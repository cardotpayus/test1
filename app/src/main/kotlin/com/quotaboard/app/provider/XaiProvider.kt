package com.quotaboard.app.provider

import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.PayAsYouGo
import com.quotaboard.app.data.ProviderSupport
import com.quotaboard.app.data.QuotaSnapshot

class XaiProvider : QuotaProvider {
    override val id = "grok"
    override val displayName = "Grok / xAI"
    override val authMethods = listOf(AuthMethod.API_KEY)
    override val supportDescription = listOf(
        ProviderSupport("按量预付余额（充值余额）", supported = true, note = "使用 xAI Management Key + 团队 ID 查询官方管理 API"),
        ProviderSupport("订阅额度（SuperGrok）", supported = false, note = "Grok 网页订阅无公开接口，暂不支持查询"),
    )

    override suspend fun fetch(account: Account, secrets: AccountSecrets): QuotaSnapshot {
        val key = secrets.apiKey?.takeIf { it.isNotBlank() }
            ?: throw ProviderException("请填写 xAI Management Key（Console → Settings → Management Keys）")
        val teamId = account.extra["teamId"]?.takeIf { it.isNotBlank() }
            ?: throw ProviderException("请填写 xAI 团队 ID（Console 页面 URL 中 teams/ 后的 ID）")
        val headers = mapOf("Authorization" to "Bearer $key")

        val balanceText = Http.get(
            "https://management-api.x.ai/v1/billing/teams/$teamId/prepaid/balance",
            headers,
        )
        val balanceRoot = Http.parseObject(balanceText)
        val centsStr = balanceRoot.stringValue("total")
            ?: balanceRoot.stringValue("balance")
            ?: throw ProviderException("余额响应格式无法解析：${balanceText.take(200)}")
        val cents = centsStr.toDoubleOrNull()
            ?: throw ProviderException("余额数值无法解析：$centsStr")
        return QuotaSnapshot(
            payg = PayAsYouGo(balanceAmount = cents / -100.0, currency = "USD"),
            fetchedAtMs = System.currentTimeMillis(),
        )
    }
}
