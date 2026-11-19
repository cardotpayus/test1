package com.quotaboard.app.provider

import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.PayAsYouGo
import com.quotaboard.app.data.ProviderSupport
import com.quotaboard.app.data.QuotaSnapshot
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DeepSeekProvider : QuotaProvider {
    override val id = "deepseek"
    override val displayName = "DeepSeek API"
    override val authMethods = listOf(AuthMethod.API_KEY)
    override val supportDescription = listOf(
        ProviderSupport("按量余额（API 钱包）", supported = true, note = "官方 GET /user/balance 接口"),
    )

    override suspend fun fetch(account: Account, secrets: AccountSecrets): QuotaSnapshot {
        val key = secrets.apiKey?.takeIf { it.isNotBlank() } ?: throw ProviderException("请填写 DeepSeek API Key")
        val body = Http.get(
            "https://api.deepseek.com/user/balance",
            mapOf("Authorization" to "Bearer $key"),
        )
        val root = Http.parseObject(body)
        val first = root.arr("balance_infos")?.firstOrNull()?.jsonObject
            ?: throw ProviderException("余额响应格式无法解析：${body.take(200)}")
        val total = first.doubleValue("total_balance")
            ?: throw ProviderException("余额字段缺失：${body.take(200)}")
        val currency = first.stringValue("currency") ?: "CNY"
        return QuotaSnapshot(
            payg = PayAsYouGo(balanceAmount = total, currency = currency),
            fetchedAtMs = System.currentTimeMillis(),
        )
    }
}
