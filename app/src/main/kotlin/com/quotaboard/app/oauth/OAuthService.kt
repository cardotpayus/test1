package com.quotaboard.app.oauth

import android.net.Uri
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.provider.Http
import com.quotaboard.app.provider.OAuthConfig
import com.quotaboard.app.provider.ProviderException
import com.quotaboard.app.provider.stringValue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class OAuthFlowParams(
    val authorizeUrl: String,
    val state: String,
    val verifier: String,
    val redirectUri: String,
)

object OAuthService {

    fun buildAuthorizeUrl(config: OAuthConfig): OAuthFlowParams {
        val verifier = Pkce.newVerifier()
        val state = Pkce.newState()
        val uri = Uri.parse(config.authorizeUrl).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("scope", config.scope)
            .appendQueryParameter("code_challenge", Pkce.challenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .apply {
                config.extraAuthParams.forEach { (k, v) -> appendQueryParameter(k, v) }
            }
            .build()
        return OAuthFlowParams(uri.toString(), state, verifier, config.redirectUri)
    }

    fun extractCode(url: String, expectedState: String): String? {
        val uri = Uri.parse(url)
        if (uri.getQueryParameter("state") != expectedState) return null
        return uri.getQueryParameter("code")
    }

    suspend fun exchangeCode(config: OAuthConfig, code: String, verifier: String): AccountSecrets {
        val form = mapOf(
            "grant_type" to "authorization_code",
            "client_id" to config.clientId,
            "code" to code,
            "redirect_uri" to config.redirectUri,
            "code_verifier" to verifier,
        )
        val body = if (config.tokenRequestUseJson) {
            val jsonObj = kotlinx.serialization.json.buildJsonObject {
                form.forEach { (k, v) -> put(k, kotlinx.serialization.json.JsonPrimitive(v)) }
            }
            Http.postJson(config.tokenUrl, emptyMap(), jsonObj.toString())
        } else {
            Http.postForm(config.tokenUrl, form)
        }
        val root = Http.parseObject(body)
        val accessToken = root.stringValue("access_token")
            ?: throw ProviderException("Token 响应缺少 access_token：${body.take(200)}")
        val expiresIn = (root as? kotlinx.serialization.json.JsonObject)
            ?.get("expires_in")?.jsonPrimitive?.content?.toLongOrNull()
        return AccountSecrets(
            accessToken = accessToken,
            refreshToken = root.stringValue("refresh_token"),
            expiresAtMs = expiresIn?.let { System.currentTimeMillis() + it * 1000 },
            accountId = root.stringValue("chatgpt_account_id") ?: root.stringValue("account_id"),
        )
    }
}
