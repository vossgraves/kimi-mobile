package com.kimimobile.data

/**
 * Primary agents, following the model opencode and Claude both use: you pick
 * a *mode*, not a pile of tool checkboxes. Each mode decides which tools are
 * available and how autonomously the model may act.
 *
 * Subagents (Subagents.kt) are separate — primary agents delegate to them.
 */
enum class AgentMode(
    val id: String,
    val label: String,
    val tagline: String,
    /** null = every installed tool. */
    val allowedTools: Set<String>?,
    val canDelegate: Boolean,
    val prompt: String,
) {
    CHAT(
        id = "chat",
        label = "Chat",
        tagline = "Plain conversation, no tools",
        allowedTools = emptySet(),
        canDelegate = false,
        prompt = "",
    ),

    PLAN(
        id = "plan",
        label = "Plan",
        tagline = "Research and outline first — never acts on its own",
        // Read-only tools only: it may look things up, not change anything.
        allowedTools = setOf("web_search", "fetch_url", "wikipedia", "datetime", "calculator"),
        canDelegate = true,
        prompt = "You are in PLAN mode. Investigate thoroughly, then produce a concrete plan " +
            "the user can approve. Read and research freely, but do not take irreversible " +
            "actions or claim work is done. End with numbered steps and call out anything " +
            "ambiguous that needs a decision.",
    ),

    BUILD(
        id = "build",
        label = "Build",
        tagline = "Full tool access, carries the task through",
        allowedTools = null,
        canDelegate = true,
        prompt = "You are in BUILD mode with every tool available. Work the task end to end: " +
            "gather what you need, use tools rather than guessing, and delegate self-contained " +
            "chunks to subagents. Report what you actually did, not what you intended.",
    ),

    AUTO(
        id = "auto",
        label = "Auto",
        tagline = "Picks the approach for you",
        allowedTools = null,
        canDelegate = true,
        prompt = "You are in AUTO mode. Judge what the request needs: answer directly when it's " +
            "simple, research first when facts are involved, and delegate when a chunk is " +
            "self-contained. Don't over-engineer a short question, and don't under-serve a " +
            "complex one.",
    );

    val usesTools: Boolean get() = this != CHAT

    companion object {
        fun byId(id: String): AgentMode = entries.firstOrNull { it.id == id } ?: CHAT

        /** Tools this mode may use, intersected with what's installed. */
        fun toolsFor(mode: AgentMode, installed: Set<String>): Set<String> =
            when (val allowed = mode.allowedTools) {
                null -> installed
                else -> installed intersect allowed
            }
    }
}

/**
 * Reasoning effort, for models that expose it. Mapped onto the provider's own
 * parameter at request time; ignored by models that don't support it.
 */
enum class ReasoningEffort(val id: String, val label: String, val description: String) {
    LOW("low", "Low", "Fastest, least deliberation"),
    MEDIUM("medium", "Medium", "Balanced"),
    HIGH("high", "High", "Slowest, most thorough");

    companion object {
        fun byId(id: String): ReasoningEffort =
            entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}
