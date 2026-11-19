package com.quotaboard.app.provider

import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.ProviderSupport
import com.quotaboard.app.data.QuotaSnapshot
import com.quotaboard.app.data.QuotaWindow
import com.quotaboard.app.data.WindowKind
import kotlinx.serialization.json.jsonObject

class OpenCodeGoProvider : QuotaProvider {
    override val id = "opencodego"
    override val displayName = "opencode go"
    override val authMethods = listOf(AuthMethod.API_KEY)
    override val supportDescription = listOf(
        ProviderSupport("订阅额度（go 订阅滚动/周/月窗口）", supported = true, note = "官方 GET /zen/go/v1/usage 接口（按公开文档实现）"),
        ProviderSupport("Zen 按量余额", supported = false, note = "Zen 无带 Key 的余额接口，暂不支持查询"),
    )

    override suspend fun fetch(account: Account, secrets: AccountSecrets): QuotaSnapshot {
        val key = secrets.apiKey?.takeIf { it.isNotBlank() } ?: throw ProviderException("请填写 opencode API Key")
        val unsupported = mutableListOf(
            "Zen 按量余额无公开查询接口，暂不支持查询"
        )
        val body = Http.get(
            "https://opencode.ai/zen/go/v1/usage",
            mapOf(
                "Authorization" to "Bearer $key",
                "User-Agent" to "QuotaBoard/0.1 (Android)",
            ),
        )
        val root = Http.parseObject(body)
        val windows = mutableListOf<QuotaWindow>()
        root.obj("rolling")?.obj("usage")?.let { w ->
            parseWindow(w, WindowKind.ROLLING)?.let { windows.add(it) }
        }
        root.obj("rolling")?.let { w -> parseWindow(w, WindowKind.ROLLING)?.let { windows.add(it) } }
        root.obj("weekly")?.let { w ->
            parseWindow(w, WindowKind.WEEKLY)?.let { windows.add(it) }
        }
        root.obj("monthly")?.let { w ->
            parseWindow(w, WindowKind.MONTHLY)?.let { windows.add(it) }
        }
        if (windows.isEmpty()) {
            throw ProviderException("无法从响应解析用量窗口：${body.take(200)}")
        }
        return QuotaSnapshot(
            windows = windows.distinctBy { it.kind },
            unsupportedNotes = unsupported,
            fetchedAtMs = System.currentTimeMillis(),
        )
    }
}
