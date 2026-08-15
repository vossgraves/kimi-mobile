package com.kimimobile.data

/**
 * Where a model runs. Kimi goes through the self-hosted proxy with your
 * refresh token; Zen is OpenCode's gateway, whose `-free` models answer
 * without any API key (verified: HTTP 200, cost "0").
 */
enum class Provider(val id: String, val label: String) {
    KIMI("kimi", "Kimi"),
    ZEN("zen", "OpenCode Zen"),
}

data class KimiModel(
    val id: String,
    val name: String,
    val description: String,
    val contextTokens: Long,
    val provider: Provider = Provider.KIMI,
    val vision: Boolean = false,
    val reasoning: Boolean = false,
    /** Kimi-only: capability suffixes the proxy understands. */
    val supportsSuffixes: Boolean = provider == Provider.KIMI,
)

object Models {

    const val ZEN_BASE_URL = "https://opencode.ai/zen/v1"

    private val kimiModels = listOf(
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
    )

    /**
     * Free tier on OpenCode Zen — no key, no account. Rate limits apply per
     * model, so the picker offers several.
     */
    private val zenModels = listOf(
        KimiModel(
            id = "nemotron-3-ultra-free",
            name = "Nemotron 3 Ultra",
            description = "Free · 1M context, NVIDIA's largest open model",
            contextTokens = 1_000_000,
            provider = Provider.ZEN,
            reasoning = true,
        ),
        KimiModel(
            id = "nemotron-3.5-lightning-free",
            name = "Nemotron 3.5 Lightning",
            description = "Free · fast responses, 262k context",
            contextTokens = 262_144,
            provider = Provider.ZEN,
            reasoning = true,
        ),
        KimiModel(
            id = "deepseek-v4-flash-free",
            name = "DeepSeek V4 Flash",
            description = "Free · quick reasoning, 200k context",
            contextTokens = 200_000,
            provider = Provider.ZEN,
            reasoning = true,
        ),
        KimiModel(
            id = "hy3-free",
            name = "Hy3",
            description = "Free · Tencent Hunyuan 3, 190k context",
            contextTokens = 190_000,
            provider = Provider.ZEN,
            reasoning = true,
        ),
        KimiModel(
            id = "mimo-v2.5-free",
            name = "MiMo V2.5",
            description = "Free · Xiaomi MiMo, strong at code",
            contextTokens = 262_144,
            provider = Provider.ZEN,
        ),
        KimiModel(
            id = "laguna-s-2.1-free",
            name = "Laguna S 2.1",
            description = "Free · general purpose, 256k context",
            contextTokens = 256_000,
            provider = Provider.ZEN,
        ),
    )

    val all: List<KimiModel> = kimiModels + zenModels

    fun forProvider(provider: Provider): List<KimiModel> = all.filter { it.provider == provider }

    val default: KimiModel = kimiModels.first()

    /**
     * Exact match first, then the *longest* matching prefix — otherwise
     * "kimi-k2-thinking-turbo" would resolve to "kimi-k2-thinking".
     */
    fun byId(id: String): KimiModel? =
        all.firstOrNull { it.id == id }
            ?: all.filter { id.startsWith("${it.id}-") }.maxByOrNull { it.id.length }

    fun providerOf(id: String): Provider = byId(id)?.provider ?: Provider.KIMI

    /**
     * Builds the model string the backend expects. Kimi capabilities are
     * suffixes and they compose; Zen models take their id verbatim.
     */
    fun resolve(
        baseId: String,
        search: Boolean = false,
        research: Boolean = false,
        math: Boolean = false,
    ): String {
        val model = byId(baseId)
        if (model != null && !model.supportsSuffixes) return baseId
        return buildString {
            append(baseId)
            // Deep research subsumes plain search on the web API.
            if (research) append("-research")
            else if (search) append("-search")
            if (math) append("-math")
        }
    }

    /** Context window for a resolved model id, for the usage ring. */
    fun contextTokensFor(modelId: String): Long =
        byId(modelId)?.contextTokens ?: 131_072L
}
