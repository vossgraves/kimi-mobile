package com.kimimobile.data

/**
 * Models exposed by the kimi.com web API through the proxy, plus the
 * capability flags Kimi actually supports. The proxy switches features on by
 * *model-name suffix* (see chat.ts): `search` → web search, `research` →
 * deep research, `k1` → reasoning model, `math` → math mode. So the app picks
 * a base model and appends suffixes for the capabilities you turn on.
 */
data class KimiModel(
    val id: String,
    val name: String,
    val description: String,
    val contextTokens: Long,
    val vision: Boolean = false,
    val reasoning: Boolean = false,
)

object Models {

    val all: List<KimiModel> = listOf(
        KimiModel(
            id = "kimi-k2-0905-preview",
            name = "K2 · 0905",
            description = "Latest K2 — strongest agentic coding, prettier code output",
            contextTokens = 262_144,
        ),
        KimiModel(
            id = "kimi-k2-turbo-preview",
            name = "K2 Turbo",
            description = "High-speed K2 — 60–100 tokens/s",
            contextTokens = 262_144,
        ),
        KimiModel(
            id = "kimi-k2-thinking",
            name = "K2 Thinking",
            description = "Long-thinking K2 — multi-step tool use and deep reasoning",
            contextTokens = 262_144,
            reasoning = true,
        ),
        KimiModel(
            id = "kimi-k2-thinking-turbo",
            name = "K2 Thinking Turbo",
            description = "Deep reasoning at 60–100 tokens/s",
            contextTokens = 262_144,
            reasoning = true,
        ),
        KimiModel(
            id = "kimi-k2-0711-preview",
            name = "K2 · 0711",
            description = "1T-parameter MoE — strong code and agent ability",
            contextTokens = 131_072,
        ),
        KimiModel(
            id = "kimi-latest",
            name = "Kimi Latest",
            description = "Newest vision model — image understanding",
            contextTokens = 131_072,
            vision = true,
        ),
        KimiModel(
            id = "moonshot-v1-128k-vision-preview",
            name = "Vision 128K",
            description = "Image + text analysis, 128k context",
            contextTokens = 131_072,
            vision = true,
        ),
        KimiModel(
            id = "moonshot-v1-32k-vision-preview",
            name = "Vision 32K",
            description = "Image + text analysis, 32k context",
            contextTokens = 32_768,
            vision = true,
        ),
        KimiModel(
            id = "moonshot-v1-8k-vision-preview",
            name = "Vision 8K",
            description = "Image + text analysis, 8k context",
            contextTokens = 8_192,
            vision = true,
        ),
        KimiModel(
            id = "moonshot-v1-128k",
            name = "Moonshot 128K",
            description = "Very long text generation",
            contextTokens = 131_072,
        ),
        KimiModel(
            id = "moonshot-v1-32k",
            name = "Moonshot 32K",
            description = "Long text generation",
            contextTokens = 32_768,
        ),
        KimiModel(
            id = "moonshot-v1-8k",
            name = "Moonshot 8K",
            description = "Short text generation",
            contextTokens = 8_192,
        ),
    )

    val default: KimiModel = all.first()

    /**
     * Exact match first, then the *longest* matching prefix — otherwise
     * "kimi-k2-thinking-turbo" would resolve to "kimi-k2-thinking".
     */
    fun byId(id: String): KimiModel? =
        all.firstOrNull { it.id == id }
            ?: all.filter { id.startsWith("${it.id}-") }.maxByOrNull { it.id.length }

    /**
     * Builds the model string the proxy expects. Capabilities are suffixes,
     * and they compose: `kimi-k2-0905-preview-search-math`.
     */
    fun resolve(
        baseId: String,
        search: Boolean = false,
        research: Boolean = false,
        math: Boolean = false,
    ): String = buildString {
        append(baseId)
        // Deep research subsumes plain search on the web API.
        if (research) append("-research")
        else if (search) append("-search")
        if (math) append("-math")
    }

    /** Context window for a resolved model id, for the usage ring. */
    fun contextTokensFor(modelId: String): Long =
        byId(modelId)?.contextTokens ?: 131_072L
}
