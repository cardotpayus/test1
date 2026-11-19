package com.quotaboard.app.provider

object ProviderRegistry {
    val all: List<QuotaProvider> = listOf(
        AnthropicProvider(),
        CodexProvider(),
        XaiProvider(),
        DeepSeekProvider(),
        MistralProvider(),
        OpenCodeGoProvider(),
        OpenRouterProvider(),
        ZaiProvider(),
        KimiProvider(),
        SiliconFlowProvider(),
    )

    fun byId(id: String): QuotaProvider? = all.firstOrNull { it.id == id }
}
