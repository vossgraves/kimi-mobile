package com.kimimobile.data

/**
 * Subagents are named roles the main agent (or you) can delegate to. Each runs
 * its own tool loop on its own model, with only the tools its job needs, and
 * reports one summary back — so a long research crawl doesn't flood the main
 * conversation's context.
 *
 * Delegate from the chat box by starting a message with the agent's handle:
 *     @researcher what changed in Kotlin 2.2?
 *     @coder write a retry helper with exponential backoff
 *
 * Or let agent mode do it itself with:  DELEGATE:<handle>|<task>
 */
data class Subagent(
    val handle: String,
    val name: String,
    val description: String,
    /** Tool ids from SkillEngine this agent may use. */
    val tools: Set<String>,
    /** Prefer a free Zen model so delegation doesn't burn Kimi quota. */
    val preferredModel: String?,
    val systemPrompt: String,
)

object Subagents {

    val all: List<Subagent> = listOf(
        Subagent(
            handle = "researcher",
            name = "Researcher",
            description = "Searches the web and reads sources, returns a cited summary",
            tools = setOf("web_search", "fetch_url", "wikipedia"),
            preferredModel = "nemotron-3.5-lightning-free",
            systemPrompt = "You are a research subagent. Gather facts with your tools, " +
                "prefer primary sources, and note when sources disagree. " +
                "Finish with a tight summary and a short source list. " +
                "Never speculate past what the sources say.",
        ),
        Subagent(
            handle = "coder",
            name = "Coder",
            description = "Writes and reviews code, explains trade-offs",
            tools = setOf("fetch_url", "calculator"),
            preferredModel = "mimo-v2.5-free",
            systemPrompt = "You are a coding subagent. Produce complete, runnable code with " +
                "correct imports and error handling. State assumptions explicitly. " +
                "Prefer the standard library over new dependencies. " +
                "Keep explanation short — the code is the deliverable.",
        ),
        Subagent(
            handle = "analyst",
            name = "Analyst",
            description = "Works through numbers, comparisons and trade-offs",
            tools = setOf("calculator", "web_search", "datetime"),
            preferredModel = "deepseek-v4-flash-free",
            systemPrompt = "You are an analysis subagent. Break problems into steps, compute " +
                "carefully with the calculator rather than estimating, and show the " +
                "reasoning behind each number. End with a clear recommendation.",
        ),
        Subagent(
            handle = "summarizer",
            name = "Summarizer",
            description = "Condenses long text or conversations into the essentials",
            tools = emptySet(),
            preferredModel = "laguna-s-2.1-free",
            systemPrompt = "You are a summarization subagent. Preserve decisions, numbers, " +
                "names and open questions. Drop pleasantries and repetition. " +
                "Structure the output so it can be skimmed.",
        ),
    )

    fun byHandle(handle: String): Subagent? =
        all.firstOrNull { it.handle.equals(handle.trim().removePrefix("@"), ignoreCase = true) }

    /** Matches a leading "@handle rest of the message". */
    private val MENTION = Regex("""^\s*@([a-zA-Z_]+)\s+(.+)$""", RegexOption.DOT_MATCHES_ALL)

    /** Splits "@researcher find X" into the agent and its task, if it parses. */
    fun parseMention(input: String): Pair<Subagent, String>? {
        val match = MENTION.find(input) ?: return null
        val agent = byHandle(match.groupValues[1]) ?: return null
        val task = match.groupValues[2].trim()
        return if (task.isBlank()) null else agent to task
    }

    /** Agent-initiated delegation: DELEGATE:researcher|find X */
    val DELEGATE_RE = Regex("""DELEGATE:([a-zA-Z_]+)\|([^\n]+)""")
}
