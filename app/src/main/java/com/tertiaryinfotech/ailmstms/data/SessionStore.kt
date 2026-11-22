package com.tertiaryinfotech.ailmstms.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** What the app remembers between launches. Kept behind an interface so the API layer can be unit tested. */
interface SessionState {
    var token: String
    var profile: String
    var role: String

    /** The role chosen on the sign-in screen. Empty for a session that predates the choice. */
    var loginRole: String
    var pushEnabled: Boolean
    var pushToken: String

    /** Last theme from the profile (system, light or dark), applied at launch before the network answers. */
    var theme: String
    var brandColor: String

    /** Full profile JSON from /api/mobile/profile, for showing the profile while offline. */
    var userProfile: String

    /** True after a password sign-in that must be followed by a password change. */
    var mustChangePassword: Boolean

    /** Ends the session. The device's push token is kept so it can be unregistered or reused. */
    fun clear()
}

class SessionStore(context: Context) : SessionState {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun string(key: String, default: String = "") = prefs.getString(key, default) ?: default
    private fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()

    override var token: String
        get() = string("token")
        set(v) = put("token", v)
    override var profile: String
        get() = string("profile")
        set(v) = put("profile", v)
    override var role: String
        get() = string("role", "learner")
        set(v) = put("role", v)
    override var loginRole: String
        get() = string("loginRole")
        set(v) = put("loginRole", v)
    override var pushEnabled: Boolean
        get() = prefs.getBoolean("pushEnabled", false)
        set(v) = prefs.edit().putBoolean("pushEnabled", v).apply()
    override var pushToken: String
        get() = string("pushToken")
        set(v) = put("pushToken", v)
    override var theme: String
        get() = string("theme", "system")
        set(v) = put("theme", v)
    override var brandColor: String
        get() = string("brandColor")
        set(v) = put("brandColor", v)
    override var userProfile: String
        get() = string("userProfile")
        set(v) = put("userProfile", v)
    override var mustChangePassword: Boolean
        get() = prefs.getBoolean("mustChangePassword", false)
        set(v) = prefs.edit().putBoolean("mustChangePassword", v).apply()

    override fun clear() {
        val device = pushToken
        prefs.edit().clear().putString("pushToken", device).commit()
    }
}
