package com.quotaboard.app.provider

import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.PayAsYouGo
import com.quotaboard.app.data.ProviderSupport
import com.quotaboard.app.data.QuotaSnapshot

class OpenRouterProvider : QuotaProvider {
    override val id = "openrouter"
    override val displayName = "OpenRouter"
    override val authMethods = listOf(AuthMethod.API_KEY)
    override val supportDescription = listOf(
        ProviderSupport("按量余额（Credits 剩余）", supported = true, note = "官方 GET /api/v1/credits 接口"),
    )

    override suspend fun fetch(account: Account, secrets: AccountSecrets): QuotaSnapshot {
        val key = secrets.apiKey?.takeIf { it.isNotBlank() } ?: throw ProviderException("请填写 OpenRouter API Key")
        val body = Http.get(
            "https://openrouter.ai/api/v1/credits",
            mapOf("Authorization" to "Bearer $key"),
        )
        val root = Http.parseObject(body)
        val data = root.obj("data")
            ?: throw ProviderException("余额响应格式无法解析：${body.take(200)}")
        val total = data.doubleValue("total_credits")
            ?: throw ProviderException("缺少 total_credits 字段：${body.take(200)}")
        val used = data.doubleValue("total_usage") ?: 0.0
        return QuotaSnapshot(
            payg = PayAsYouGo(balanceAmount = total - used, currency = "USD"),
            fetchedAtMs = System.currentTimeMillis(),
        )
    }
}
