package com.quotaboard.app.data

import kotlinx.serialization.Serializable

enum class AuthMethod { API_KEY, OAUTH }

enum class WindowKind(val display: String) {
    FIVE_HOUR("5 小时窗口"),
    SESSION("会话窗口"),
    WEEKLY("周额度"),
    MONTHLY("月额度"),
    ROLLING("滚动窗口"),
}

@Serializable
data class QuotaWindow(
    val kind: WindowKind,
    val usedPercent: Double? = null,
    val usedAmount: Double? = null,
    val limitAmount: Double? = null,
    val resetsAtMs: Long? = null,
    val status: String? = null,
)

@Serializable
data class PayAsYouGo(
    val balanceAmount: Double,
    val currency: String,
)

@Serializable
data class QuotaSnapshot(
    val windows: List<QuotaWindow> = emptyList(),
    val payg: PayAsYouGo? = null,
    val unsupportedNotes: List<String> = emptyList(),
    val fetchedAtMs: Long,
)

@Serializable
data class Account(
    val id: String,
    val providerId: String,
    val label: String,
    val authMethod: AuthMethod,
    val extra: Map<String, String> = emptyMap(),
    val createdAtMs: Long,
)

@Serializable
data class LedgerEntry(
    val tsMs: Long,
    val balance: Double,
    val currency: String,
)

@Serializable
data class AccountSecrets(
    val apiKey: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtMs: Long? = null,
    val accountId: String? = null,
)

data class AccountState(
    val account: Account,
    val snapshot: QuotaSnapshot? = null,
    val lastError: String? = null,
    val updatedMs: Long? = null,
    val refreshing: Boolean = false,
)

@Serializable
data class ProviderSupport(
    val capability: String,
    val supported: Boolean,
    val note: String? = null,
)
