package com.negusoft.localauth.core

import android.content.SharedPreferences
import androidx.core.content.edit
import com.negusoft.localauth.coding.ByteCoding
import com.negusoft.localauth.coding.writePropertyMap
import com.negusoft.localauth.preferences.getByteArray
import com.negusoft.localauth.preferences.putByteArray
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed class AuthResult {
    class Success(val accessToken: AccessToken, val refreshToken: RefreshToken): AuthResult()
    object InvalidUsernameOrPassword: AuthResult()
}

sealed class RefreshAccessResult {
    class Success(val accessToken: AccessToken, val refreshToken: RefreshToken?): RefreshAccessResult()
    object InvalidRefreshToken: RefreshAccessResult()
}

fun SharedPreferences.restoreTokens(): MutableMap<RefreshToken, String> {
    val bytes = getByteArray("refresh_tokens") ?: return mutableMapOf()
    return ByteCoding.decode(bytes)
        .readPropertyMap()
        .mapKeys { RefreshToken(it.key) }
        .mapValues { it.value.decodeToString() }
        .toMutableMap()
}
fun SharedPreferences.saveTokens(tokens: Map<RefreshToken, String>) {
    val map = tokens
        .mapKeys { it.key.value }
        .mapValues { it.value.encodeToByteArray() }
    val bytes = ByteCoding.encode { writePropertyMap(map) }
    edit { putByteArray("refresh_tokens", bytes) }
}

class MockAuthenticator(
    private val sharedPreferences: SharedPreferences
) {

//    private val refreshTokenRegistry = mutableMapOf<RefreshToken, String>()
//    private val refreshTokenRegistry: MutableMap<RefreshToken, String> = sharedPreferences
//        .getByteArray("asdf")
//        ?.let { ByteCoding.decode(it).readPropertyMap() }
//        ?.mapKeys { RefreshToken(it.key) }
//        ?.mapValues { it.value.toString() }
//        ?.toMutableMap()
//        ?: mutableMapOf ()
    private val refreshTokenRegistry = sharedPreferences.restoreTokens().toMutableMap()


    /**
     * Any non-empty username is accepted. The password must be the same as the username.
     * On success, it will register a new refresh token for this user. It will return the refresh
     * token along an access token in JWT format.
     */
    fun autheticate(username: String, password: String): AuthResult {
        if (username.isBlank())
            return AuthResult.InvalidUsernameOrPassword
        if (username != password)
            return AuthResult.InvalidUsernameOrPassword

        val refreshToken = registerRefreshToken(username)
        return AuthResult.Success(AccessToken(username), refreshToken)
    }

    /**
     * If the refresh token is valid, it will return a valida access token in JWT format. In case
     * refresh token rotation is enabled, the current one will be replaced by the new one, and
     * returned in the response.
     * I will fail if the refresh token is not found or is no longer valid.
     */
    fun refreshAccess(refreshToken: RefreshToken): RefreshAccessResult {
        val username = refreshTokenRegistry[refreshToken] ?: return RefreshAccessResult.InvalidRefreshToken
        invalidateRefreshToken(refreshToken)
        val newRefreshToken = registerRefreshToken(username)
        return RefreshAccessResult.Success(AccessToken(username), newRefreshToken)
    }

    private fun invalidateRefreshToken(refreshToken: RefreshToken) {
        refreshTokenRegistry.remove(refreshToken)
        sharedPreferences.saveTokens(refreshTokenRegistry)
    }

    private fun registerRefreshToken(username: String): RefreshToken {
        val refreshToken = generateNewRefreshToken()
        refreshTokenRegistry[refreshToken] = username
        sharedPreferences.saveTokens(refreshTokenRegistry)
        return refreshToken
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun generateNewRefreshToken() = RefreshToken(
        Uuid.random().toString()
    )
}