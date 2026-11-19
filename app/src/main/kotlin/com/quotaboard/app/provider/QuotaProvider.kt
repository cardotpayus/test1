package com.quotaboard.app.provider

import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.QuotaSnapshot
import com.quotaboard.app.data.ProviderSupport

data class OAuthConfig(
    val authorizeUrl: String,
    val tokenUrl: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val tokenRequestUseJson: Boolean = false,
    val extraAuthParams: Map<String, String> = emptyMap(),
)

class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface QuotaProvider {
    val id: String
    val displayName: String
    val authMethods: List<AuthMethod>
    val supportDescription: List<ProviderSupport>
    val oauthConfig: OAuthConfig? get() = null

    suspend fun fetch(account: Account, secrets: AccountSecrets): QuotaSnapshot
}

fun QuotaProvider.supports(method: AuthMethod): Boolean = authMethods.contains(method)
