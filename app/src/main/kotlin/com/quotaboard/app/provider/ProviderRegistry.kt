package com.quotaboard.app.provider

import com.quotaboard.app.provider.AnthropicProvider
import com.quotaboard.app.provider.CodexProvider
import com.quotaboard.app.provider.DeepSeekProvider
import com.quotaboard.app.provider.MistralProvider
import com.quotaboard.app.provider.OpenCodeGoProvider
import com.quotaboard.app.provider.QuotaProvider
import com.quotaboard.app.provider.XaiProvider

object ProviderRegistry {
    val all: List<QuotaProvider> = listOf(
        AnthropicProvider(),
        CodexProvider(),
        XaiProvider(),
        DeepSeekProvider(),
        MistralProvider(),
        OpenCodeGoProvider(),
    )

    fun byId(id: String): QuotaProvider? = all.firstOrNull { it.id == id }
}
