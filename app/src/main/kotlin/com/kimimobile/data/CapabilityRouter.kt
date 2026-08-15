package com.kimimobile.data

/**
 * Cross-model capability routing.
 *
 * When the model you've chosen can't do something another provider can, the
 * work is handed off for that turn and the result folded back into the
 * conversation — so picking a text-only model doesn't mean losing vision.
 *
 * Image *generation* is deliberately absent: Kimi's web API has no image
 * generation ("I can't draw, generate, or edit images" — verified directly),
 * and inventing a button that can't work would be worse than not having one.
 */
object CapabilityRouter {

    data class VisionRoute(
        val modelId: String,
        val baseUrl: String,
        val token: String,
        /** True when we had to borrow a different model than the user picked. */
        val borrowed: Boolean,
    )

    /**
     * Decides which model should read the attached images.
     *
     * - Model already sees images → use it.
     * - Kimi account present → borrow a Kimi vision model.
     * - Otherwise → no route; the caller explains why.
     */
    fun routeVision(
        selectedModelId: String,
        settings: AppSettings,
    ): VisionRoute? {
        val selected = Models.byId(selectedModelId)
        if (selected?.vision == true) {
            return VisionRoute(
                modelId = selectedModelId,
                baseUrl = if (selected.provider == Provider.ZEN) Models.ZEN_BASE_URL else settings.baseUrl,
                token = if (selected.provider == Provider.ZEN) settings.zenApiKey else settings.token,
                borrowed = false,
            )
        }

        // Kimi's vision models are the fallback, and they need an account.
        if (settings.token.isNotBlank() && settings.baseUrl.isNotBlank()) {
            val vision = Models.visionModelFor(settings.maxContextTokens)
            return VisionRoute(
                modelId = vision.id,
                baseUrl = settings.baseUrl,
                token = settings.token,
                borrowed = true,
            )
        }

        return null
    }

    /**
     * The instruction given to the borrowed vision model. We ask for a
     * description rather than an answer, because the answer should come from
     * the model the user actually chose.
     */
    const val DESCRIBE_PROMPT: String =
        "Describe this image in thorough detail: what it shows, any text visible " +
            "(transcribe it exactly), layout, colours, and anything unusual. " +
            "Be factual and complete — another model will answer the user's " +
            "question using only your description."

    /** How the borrowed description is folded back into the conversation. */
    fun describedContext(description: String): String =
        "[Image description, via Kimi vision]\n$description"

    /** Shown when there's no way to read the image. */
    const val NO_VISION_HELP: String =
        "This model can't read images, and there's no Kimi account connected to " +
            "borrow vision from. Sign in to Kimi in Settings, or pick a model that " +
            "supports images."
}
