package com.kimimobile.data

/**
 * Task-graph agent execution, modelled on jcode's swarm design.
 *
 * My first attempt was agent-first: fixed roles (researcher, coder...) that
 * each ran the same model regardless of the work. That wastes the two things
 * a phone has least of — battery and tokens.
 *
 * This is DAG-first instead. You declare tasks with dependency edges; the
 * scheduler runs whatever is unblocked, in parallel, against a *pool* of
 * workers. Agents are fungible: a task says what kind of work it is, and the
 * router picks the cheapest model that can do it.
 */

/** What kind of work a node is — drives model choice and effort. */
enum class TaskKind(val label: String) {
    /** Bulk reading, fetching, summarising. Cheapest model, no reasoning. */
    GATHER("Gather"),
    /** Design, debugging, review. Strongest model available. */
    REASON("Reason"),
    /** Writing code or prose from a settled plan. Mid model, low effort. */
    PRODUCE("Produce"),
    /** Checking someone else's output. Mid model, cheap. */
    VERIFY("Verify"),
}

enum class NodeState { BLOCKED, READY, RUNNING, DONE, FAILED }

data class TaskNode(
    val id: String,
    val title: String,
    val kind: TaskKind,
    val prompt: String,
    /** Ids that must reach DONE before this becomes READY. */
    val dependsOn: Set<String> = emptySet(),
    val state: NodeState = NodeState.BLOCKED,
    /** The worker's report, fed to dependents as context. */
    val output: String = "",
)

/**
 * Two presets over one engine, as jcode frames it: light is deep with the
 * rigor turned off and a small cap. On a phone, light is the sane default.
 */
enum class SwarmMode(
    val label: String,
    val maxParallel: Int,
    val maxNodes: Int,
    /** Deep mode adds a verify node before anything closes. */
    val verifyGate: Boolean,
) {
    LIGHT("Fan-out", maxParallel = 3, maxNodes = 8, verifyGate = false),
    DEEP("Comprehensive", maxParallel = 2, maxNodes = 16, verifyGate = true),
}

/**
 * Picks the cheapest capable model per task kind, from what's actually
 * available to this user. jcode routes reads to a no-reasoning model and
 * saves the strong one for judgement; the same split matters far more here,
 * where every token is metered or rate-limited.
 */
object ModelRouter {

    data class Route(val modelId: String, val effort: ReasoningEffort)

    fun route(
        kind: TaskKind,
        available: List<KimiModel>,
        coordinatorModel: String,
    ): Route {
        val usable = available.filterNot { it.hidden }
        // Free models first: delegation shouldn't burn a paid quota.
        val free = usable.filter { it.provider == Provider.ZEN && !it.requiresKey }
        val reasoning = usable.filter { it.reasoning }

        return when (kind) {
            // Reading and summarising: smallest context-capable free model,
            // minimum effort. Never the flagship.
            TaskKind.GATHER -> Route(
                modelId = free.minByOrNull { it.contextTokens }?.id
                    ?: free.firstOrNull()?.id
                    ?: coordinatorModel,
                effort = ReasoningEffort.LOW,
            )

            // Judgement: the strongest thing on hand, thinking hard.
            TaskKind.REASON -> Route(
                modelId = reasoning.maxByOrNull { it.contextTokens }?.id
                    ?: coordinatorModel,
                effort = ReasoningEffort.HIGH,
            )

            // Producing from a settled plan needs competence, not deliberation.
            TaskKind.PRODUCE -> Route(
                modelId = free.maxByOrNull { it.contextTokens }?.id
                    ?: coordinatorModel,
                effort = ReasoningEffort.LOW,
            )

            // Verification is a second opinion; cheap is fine, but it should
            // differ from whoever produced the work.
            TaskKind.VERIFY -> Route(
                modelId = free.firstOrNull { it.id != coordinatorModel }?.id
                    ?: coordinatorModel,
                effort = ReasoningEffort.MEDIUM,
            )
        }
    }
}

/**
 * The scheduler. Holds the graph, hands out runnable nodes, and folds each
 * worker's output into its dependents' context.
 */
class TaskGraph(
    private val mode: SwarmMode,
    nodes: List<TaskNode>,
) {
    private val graph = LinkedHashMap<String, TaskNode>()

    init {
        nodes.take(mode.maxNodes).forEach { graph[it.id] = it }
        recomputeReadiness()
    }

    val all: List<TaskNode> get() = graph.values.toList()

    val isComplete: Boolean
        get() = graph.values.all { it.state == NodeState.DONE || it.state == NodeState.FAILED }

    /** Nodes whose dependencies are satisfied, capped by the mode's width. */
    fun nextBatch(): List<TaskNode> =
        graph.values
            .filter { it.state == NodeState.READY }
            .take(mode.maxParallel)

    fun markRunning(id: String) = update(id) { it.copy(state = NodeState.RUNNING) }

    fun complete(id: String, output: String) {
        update(id) { it.copy(state = NodeState.DONE, output = output) }
        recomputeReadiness()
    }

    fun fail(id: String, reason: String) {
        update(id) { it.copy(state = NodeState.FAILED, output = reason) }
        // A failed dependency shouldn't wedge the graph: dependents still run,
        // with the failure noted in their context.
        recomputeReadiness()
    }

    /** Everything this node's dependencies produced, as prompt context. */
    fun contextFor(node: TaskNode): String =
        node.dependsOn
            .mapNotNull { graph[it] }
            .filter { it.output.isNotBlank() }
            .joinToString("\n\n") { "### ${it.title}\n${it.output}" }

    private fun update(id: String, transform: (TaskNode) -> TaskNode) {
        graph[id]?.let { graph[id] = transform(it) }
    }

    private fun recomputeReadiness() {
        graph.values.forEach { node ->
            if (node.state != NodeState.BLOCKED) return@forEach
            val settled = node.dependsOn.all { dep ->
                graph[dep]?.state.let { it == NodeState.DONE || it == NodeState.FAILED }
            }
            if (settled) graph[node.id] = node.copy(state = NodeState.READY)
        }
    }

    companion object {
        /**
         * Parses the plan the coordinator emits:
         *   TASK:<id>|<kind>|<depends,comma,separated>|<title>
         * Dependencies may be empty. Unknown kinds fall back to PRODUCE.
         */
        private val TASK_RE = Regex(
            """TASK:([\w-]+)\|(\w+)\|([\w,\-]*)\|(.+)"""
        )

        fun parse(plan: String, mode: SwarmMode): TaskGraph? {
            val nodes = TASK_RE.findAll(plan).map { m ->
                val (id, kindRaw, deps, title) = m.destructured
                TaskNode(
                    id = id,
                    title = title.trim(),
                    kind = runCatching { TaskKind.valueOf(kindRaw.uppercase()) }
                        .getOrDefault(TaskKind.PRODUCE),
                    prompt = title.trim(),
                    dependsOn = deps.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                )
            }.toList()
            return if (nodes.isEmpty()) null else TaskGraph(mode, nodes)
        }

        /** Instructions given to the coordinator so it emits a usable graph. */
        fun plannerPrompt(mode: SwarmMode): String = buildString {
            append("Break the task into a dependency graph. One line per task:\n")
            append("TASK:<id>|<GATHER|REASON|PRODUCE|VERIFY>|<comma-separated ids this waits on>|<what to do>\n\n")
            append("Kinds route to different models, so choose honestly:\n")
            append("- GATHER: reading, searching, summarising (runs on a cheap model)\n")
            append("- REASON: design, debugging, judgement calls (runs on the strongest)\n")
            append("- PRODUCE: writing code or prose from a settled plan\n")
            append("- VERIFY: checking another task's output\n\n")
            append("Independent tasks run in parallel, so leave their dependency field empty. ")
            append("Keep it under ${mode.maxNodes} tasks. ")
            if (mode.verifyGate) {
                append("Every PRODUCE task must have a VERIFY task depending on it.\n")
            } else {
                append("Skip verification unless the task genuinely needs it.\n")
            }
            append("Emit nothing but TASK lines, then stop.")
        }
    }
}
