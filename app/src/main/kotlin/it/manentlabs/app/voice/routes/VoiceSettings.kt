// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.routes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manentlabs.app.security.SecretBox
import it.manentlabs.app.voice.phone.PhoneActionKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the voice-command settings hold, secrets aside. */
data class VoiceConfig(
    val enabled: Boolean,
    val homeAssistantUrl: String,
    /** A token is stored, whether or not it still opens. */
    val hasToken: Boolean,
    /** The stored token opens with this phone's key. */
    val tokenUsable: Boolean,
    val pipeline: String?,
    val routes: List<Route>,
    /** Ids of the routes with a webhook secret saved. */
    val webhookSecrets: Set<String>,
    val phoneApps: Map<PhoneActionKind, String>,
    val hermesUrl: String = "",
    /** A Hermes API key is stored, whether or not it still opens. */
    val hasHermesKey: Boolean = false,
    val hermesKeyUsable: Boolean = false,
) {
    val homeAssistantReady: Boolean get() = homeAssistantUrl.isNotBlank() && tokenUsable
    val hermesReady: Boolean get() = hermesUrl.isNotBlank() && hermesKeyUsable
}

/**
 * The voice-command settings, in the preferences file earlier releases used
 * (`omnesis_assist`), so an update keeps them. The Home Assistant token, the
 * Hermes API key and webhook secrets are sealed with [SecretBox].
 */
@Singleton
class VoiceSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<VoiceConfig> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) = edit { putBoolean(KEY_ENABLED, enabled) }

    /** Saves the address, trimmed, and the token when one is given; a null token keeps the stored one. */
    fun saveHomeAssistant(url: String, token: String?) = edit {
        putString(KEY_BASE_URL, url.trim())
        if (!token.isNullOrBlank()) putString(KEY_TOKEN, SecretBox.seal(token.trim()))
    }

    /** Saves Hermes Agent's address, trimmed, and its API key when one is given; a null key keeps the stored one. */
    fun saveHermes(url: String, key: String?) = edit {
        putString(KEY_HERMES_URL, url.trim())
        if (!key.isNullOrBlank()) putString(KEY_HERMES_KEY, SecretBox.seal(key.trim()))
    }

    /** The assistant to run; null or blank for the one Home Assistant prefers. */
    fun setPipeline(id: String?) = edit {
        if (id.isNullOrBlank()) remove(KEY_PIPELINE) else putString(KEY_PIPELINE, id)
    }

    /** Saves [routes]; false, saving nothing, when two share a word. Secrets of routes no longer present go. */
    fun saveRoutes(routes: List<Route>): Boolean {
        if (routes.map { it.wakeWord }.toSet().size != routes.size) return false
        val kept = routes.map { it.id }.toSet()
        edit {
            putString(KEY_ROUTES, RouteCodec.encode(routes))
            prefs.all.keys.filter { it.startsWith(SECRET_PREFIX) && it.removePrefix(SECRET_PREFIX) !in kept }.forEach(::remove)
        }
        return true
    }

    /** Saves the bearer secret for a webhook route; blank removes it. */
    fun saveWebhookSecret(routeId: String, secret: String) = edit {
        if (secret.isBlank()) remove(SECRET_PREFIX + routeId) else putString(SECRET_PREFIX + routeId, SecretBox.seal(secret.trim()))
    }

    fun setPhoneApp(kind: PhoneActionKind, packageName: String?) = edit {
        if (packageName == null) remove(APP_PREFIX + kind.name) else putString(APP_PREFIX + kind.name, packageName)
    }

    fun homeAssistantToken(): String? = SecretBox.open(prefs.getString(KEY_TOKEN, null))

    fun hermesKey(): String? = SecretBox.open(prefs.getString(KEY_HERMES_KEY, null))

    fun webhookSecret(routeId: String): String? = SecretBox.open(prefs.getString(SECRET_PREFIX + routeId, null))

    /** Clears every voice-command setting, which also stops listening. */
    fun forget() = edit { clear() }

    private fun edit(change: android.content.SharedPreferences.Editor.() -> Unit) {
        synchronized(this) {
            prefs.edit().apply(change).apply()
            _state.value = read()
        }
    }

    private fun read(): VoiceConfig {
        val token = prefs.getString(KEY_TOKEN, null)
        val hermesKey = prefs.getString(KEY_HERMES_KEY, null)
        return VoiceConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            homeAssistantUrl = prefs.getString(KEY_BASE_URL, null).orEmpty(),
            hasToken = token != null,
            tokenUsable = SecretBox.open(token) != null,
            pipeline = prefs.getString(KEY_PIPELINE, null)?.takeIf { it.isNotBlank() },
            routes = RouteCodec.decode(prefs.getString(KEY_ROUTES, null)),
            webhookSecrets = prefs.all.keys.filter { it.startsWith(SECRET_PREFIX) }.map { it.removePrefix(SECRET_PREFIX) }.toSet(),
            phoneApps = PhoneActionKind.entries.mapNotNull { kind ->
                prefs.getString(APP_PREFIX + kind.name, null)?.let { kind to it }
            }.toMap(),
            hermesUrl = prefs.getString(KEY_HERMES_URL, null).orEmpty(),
            hasHermesKey = hermesKey != null,
            hermesKeyUsable = SecretBox.open(hermesKey) != null,
        )
    }

    private companion object {
        const val PREFS_NAME = "omnesis_assist"
        const val KEY_ENABLED = "enabled"
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "token"
        const val KEY_PIPELINE = "pipeline"
        const val KEY_ROUTES = "routes"
        const val SECRET_PREFIX = "webhook_secret:"
        const val APP_PREFIX = "phone_app:"
        const val KEY_HERMES_URL = "hermes_url"
        const val KEY_HERMES_KEY = "hermes_key"
    }
}
