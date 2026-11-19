package com.quotaboard.app.oauth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Pkce {
    fun newVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return b64(bytes)
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return b64(digest)
    }

    fun newState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return b64(bytes)
    }

    private fun b64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
