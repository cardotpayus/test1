package com.quotaboard.app.provider

import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.PayAsYouGo
import com.quotaboard.app.data.ProviderSupport
import com.quotaboard.app.data.QuotaSnapshot

class SiliconFlowProvider : QuotaProvider {
    override val id = "siliconflow"
    override val displayName = "SiliconFlow 硅基流动"
    override val authMethods = listOf(AuthMethod.API_KEY)
    override val supportDescription = listOf(
        ProviderSupport("按量余额（国际站 api.siliconflow.com）", supported = true, note = "官方 GET /v1/user/info 接口"),
        ProviderSupport("中国大陆站余额", supported = false, note = "官方已于 2026-08 停用国内站 Key 余额查询，暂不支持查询"),
    )

    override suspend fun fetch(account: Account, secrets: AccountSecrets): QuotaSnapshot {
        val key = secrets.apiKey?.takeIf { it.isNotBlank() } ?: throw ProviderException("请填写 SiliconFlow API Key（国际站）")
        val body = Http.get(
            "https://api.siliconflow.com/v1/user/info",
            mapOf("Authorization" to "Bearer $key"),
        )
        val root = Http.parseObject(body)
        if (root.longValue("code") != null && root.longValue("code") != 20000L) {
            throw ProviderException("接口返回错误：${root.stringValue("message") ?: body.take(160)}")
        }
        val data = root.obj("data")
            ?: throw ProviderException("余额响应格式无法解析：${body.take(200)}")
        val balance = data.doubleValue("balance")
            ?: data.doubleValue("totalBalance")
            ?: throw ProviderException("缺少 balance 字段：${body.take(200)}")
        return QuotaSnapshot(
            payg = PayAsYouGo(balanceAmount = balance, currency = "USD"),
            fetchedAtMs = System.currentTimeMillis(),
        )
    }
}
