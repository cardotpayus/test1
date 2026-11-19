package com.quotaboard.app.provider

import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.PayAsYouGo
import com.quotaboard.app.data.ProviderSupport
import com.quotaboard.app.data.QuotaSnapshot
import com.quotaboard.app.data.QuotaWindow
import com.quotaboard.app.data.WindowKind

class MistralProvider : QuotaProvider {
    override val id = "mistral"
    override val displayName = "Mistral API"
    override val authMethods = listOf(AuthMethod.API_KEY)
    override val supportDescription = listOf(
        ProviderSupport("按量余额（La Plateforme 积分）", supported = true, note = "官方 CLI 同款 GET /v1/billing/subscription 接口"),
    )

    override suspend fun fetch(account: Account, secrets: AccountSecrets): QuotaSnapshot {
        val key = secrets.apiKey?.takeIf { it.isNotBlank() } ?: throw ProviderException("请填写 Mistral API Key")
        val headers = mapOf("Authorization" to "Bearer $key", "Accept" to "application/json")
        val body = try {
            Http.get("https://api.mistral.ai/v1/billing/subscription", headers)
        } catch (e: ProviderException) {
            throw ProviderException("查询 /v1/billing/subscription 失败：${e.message}")
        }
        val root = Http.parseObject(body)
        val balance = root.doubleValue("credit_balance")
            ?: throw ProviderException("未能解析 credit_balance：${body.take(200)}")
        val unsupported = mutableListOf<String>()
        val windows = mutableListOf<QuotaWindow>()
        val budget = root.stringValue("monthly_budget")
        budget?.toDoubleOrNull()?.takeIf { it > 0 }?.let { b ->
            root.stringValue("month_started_at")?.let { start ->
                windows.add(
                    QuotaWindow(
                        kind = WindowKind.MONTHLY,
                        usedAmount = (b - balance),
                        limitAmount = b,
                        resetsAtMs = null,
                    )
                )
            }
        }
        return QuotaSnapshot(
            windows = windows,
            payg = PayAsYouGo(balanceAmount = balance, currency = "EUR"),
            fetchedAtMs = System.currentTimeMillis(),
        )
    }
}
