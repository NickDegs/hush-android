package com.nickdegs.hush.core.store

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nickdegs.hush.core.auth.Network
import com.nickdegs.hush.core.auth.StartReq
import com.nickdegs.hush.core.auth.VerifyReq
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * iOS AppState'in Android karşılığı — auth state + business mode + Matrix session.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    data class UiState(
        val isAuthenticated: Boolean = false,
        val userId: String? = null,
        val accessToken: String? = null,
        val homeserver: String? = null,
        val displayName: String? = null,
        val isBusinessMode: Boolean = false,
        val businessSlug: String? = null,
        val errorMessage: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _state.asStateFlow()

    private val Application.dataStore by preferencesDataStore("hush_secure")
    private val keyUserId = stringPreferencesKey("uid")
    private val keyToken = stringPreferencesKey("at")
    private val keyHomeserver = stringPreferencesKey("hs")
    private val keyName = stringPreferencesKey("name")

    init {
        viewModelScope.launch {
            val prefs = getApplication<Application>().dataStore.data.first()
            val uid = prefs[keyUserId]
            val token = prefs[keyToken]
            val hs = prefs[keyHomeserver]
            if (!uid.isNullOrEmpty() && !token.isNullOrEmpty() && !hs.isNullOrEmpty()) {
                _state.value = UiState(
                    isAuthenticated = true,
                    userId = uid, accessToken = token, homeserver = hs,
                    displayName = prefs[keyName]
                )
            }
        }
    }

    // MARK: - Phone Auth

    suspend fun startPhoneVerification(phone: String): Boolean {
        return try {
            Network.phoneAuth.start(StartReq(phone))
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.localizedMessage)
            false
        }
    }

    suspend fun verifyPhoneCode(phone: String, code: String, displayName: String): Boolean {
        // Apple App Review fallback bypass (iOS karşılığı)
        if (phone == "+905551111111" && code == "424242") {
            persistSession(
                uid = "@apple-review:nickdegs.duckdns.org",
                token = "demo_review_token",
                hs = "https://nickdegs.duckdns.org",
                name = "Apple Review"
            )
            return true
        }
        return try {
            val resp = Network.phoneAuth.verify(VerifyReq(phone, code, displayName.ifEmpty { null }))
            persistSession(resp.user_id, resp.access_token, resp.homeserver, resp.display_name ?: displayName)
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.localizedMessage)
            false
        }
    }

    private suspend fun persistSession(uid: String, token: String, hs: String, name: String?) {
        getApplication<Application>().dataStore.edit { prefs ->
            prefs[keyUserId] = uid
            prefs[keyToken] = token
            prefs[keyHomeserver] = hs
            if (!name.isNullOrEmpty()) prefs[keyName] = name
        }
        _state.value = UiState(
            isAuthenticated = true,
            userId = uid, accessToken = token, homeserver = hs, displayName = name
        )
    }

    fun logout() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it.clear() }
            _state.value = UiState()
        }
    }

    // MARK: - Business mode (deeplink JWT)

    fun enterBusinessMode(jwtToken: String) {
        // JWT payload decode (no signature verification needed — Matrix access token is the actual proof)
        val parts = jwtToken.split(".")
        if (parts.size < 2) return
        val payload = parts[1]
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val decoded = try {
            android.util.Base64.decode(padded.replace('-', '+').replace('_', '/'), android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            return
        }
        val text = decoded.toString(Charsets.UTF_8)
        // Minimal JSON parse (kullanılan claim'ler: uid, at, hs, biz, bn)
        val json = try {
            kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return
        }
        val uid = json["uid"]?.toString()?.trim('"') ?: return
        val at  = json["at"]?.toString()?.trim('"') ?: return
        val hs  = json["hs"]?.toString()?.trim('"') ?: return
        val biz = json["biz"]?.toString()?.trim('"')
        val bn  = json["bn"]?.toString()?.trim('"')
        viewModelScope.launch {
            persistSession(uid, at, hs, bn)
            _state.value = _state.value.copy(isBusinessMode = true, businessSlug = biz)
        }
    }
}

private val kotlinx.serialization.json.JsonElement.jsonObject get() = this.let {
    (it as kotlinx.serialization.json.JsonObject)
}
