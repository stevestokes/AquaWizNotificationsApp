package app.aquawiznotifier

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("aquawiz_notifier", Context.MODE_PRIVATE)
    private val alias = "aquawiz_notifier_key_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + data, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String? = runCatching {
        val all = Base64.decode(blob, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, 12)
        val data = all.copyOfRange(12, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    fun saveSession(session: Session) {
        val json = JSONObject()
            .put("username", session.username)
            .put("password", session.password)
            .put("accessToken", session.accessToken)
            .put("devices", session.devices.joinToString("\u001f"))
        prefs.edit().putString("session", encrypt(json.toString())).apply()
    }

    fun session(): Session? {
        val raw = prefs.getString("session", null)?.let(::decrypt) ?: return null
        return runCatching {
            val j = JSONObject(raw)
            Session(
                j.getString("username"),
                j.getString("password"),
                j.getString("accessToken"),
                j.optString("devices").split("\u001f").filter { it.isNotBlank() },
            )
        }.getOrNull()
    }

    fun clearSession() = prefs.edit().remove("session").apply()

    fun clearMonitoringState() {
        prefs.edit()
            .remove("last_fingerprint")
            .remove("last_measurement_ms")
            .remove("last_kh")
            .remove("last_poll_ms")
            .remove("next_poll_ms")
            .remove("last_error")
            .apply()
    }
    fun selectedDevice(): String? = prefs.getString("device", null)
    fun setSelectedDevice(serial: String) = prefs.edit().putString("device", serial.trim()).apply()
    fun lastFingerprint(): String? = prefs.getString("last_fingerprint", null)
    fun setLastFingerprint(v: String) = prefs.edit().putString("last_fingerprint", v).apply()
    fun lastMeasurementEpochMs(): Long? = if (prefs.contains("last_measurement_ms")) prefs.getLong("last_measurement_ms", 0) else null
    fun setLastMeasurementEpochMs(v: Long) = prefs.edit().putLong("last_measurement_ms", v).apply()
    fun lastKh(): Double? = if (prefs.contains("last_kh")) java.lang.Double.longBitsToDouble(prefs.getLong("last_kh", 0)) else null
    fun setLastKh(v: Double) = prefs.edit().putLong("last_kh", java.lang.Double.doubleToRawLongBits(v)).apply()
    fun lastPollEpochMs(): Long? = if (prefs.contains("last_poll_ms")) prefs.getLong("last_poll_ms", 0) else null
    fun setLastPollEpochMs(v: Long) = prefs.edit().putLong("last_poll_ms", v).apply()
    fun lastError(): String? = prefs.getString("last_error", null)
    fun setLastError(v: String?) = prefs.edit().apply { if (v.isNullOrBlank()) remove("last_error") else putString("last_error", v.take(500)) }.apply()
    fun nextPollEpochMs(): Long? = if (prefs.contains("next_poll_ms")) prefs.getLong("next_poll_ms", 0) else null
    fun setNextPollEpochMs(v: Long) = prefs.edit().putLong("next_poll_ms", v).apply()
    fun measurementIntervalMinutes(): Long = prefs.getLong("measurement_interval", 60L).coerceIn(15L, 24L * 60L)
    fun setMeasurementIntervalMinutes(v: Long) = prefs.edit().putLong("measurement_interval", v.coerceIn(15L, 24L * 60L)).apply()
    fun baseUrl(): String = prefs.getString("base_url", AquaWizApi.GLOBAL_BASE) ?: AquaWizApi.GLOBAL_BASE
    fun setBaseUrl(v: String) = prefs.edit().putString("base_url", v.trimEnd('/')).apply()

    fun lastUpdateCheckEpochMs(): Long? = if (prefs.contains("last_update_check_ms")) prefs.getLong("last_update_check_ms", 0L) else null
    fun setLastUpdateCheckEpochMs(v: Long) = prefs.edit().putLong("last_update_check_ms", v).apply()
    fun latestReleaseVersion(): String? = prefs.getString("latest_release_version", null)
    fun setLatestReleaseVersion(v: String) = prefs.edit().putString("latest_release_version", v).apply()
    fun latestReleaseUrl(): String? = prefs.getString("latest_release_url", null)
    fun setLatestReleaseUrl(v: String) = prefs.edit().putString("latest_release_url", v).apply()
    fun lastUpdateNotifiedVersion(): String? = prefs.getString("last_update_notified_version", null)
    fun setLastUpdateNotifiedVersion(v: String) = prefs.edit().putString("last_update_notified_version", v).apply()
    fun lastUpdateError(): String? = prefs.getString("last_update_error", null)
    fun setLastUpdateError(v: String?) = prefs.edit().apply {
        if (v.isNullOrBlank()) remove("last_update_error") else putString("last_update_error", v.take(500))
    }.apply()


    fun showPhOpenAir(): Boolean = prefs.getBoolean("notify_ph_open_air", true)
    fun setShowPhOpenAir(v: Boolean) = prefs.edit().putBoolean("notify_ph_open_air", v).apply()
    fun showDeltaPh(): Boolean = prefs.getBoolean("notify_delta_ph", true)
    fun setShowDeltaPh(v: Boolean) = prefs.edit().putBoolean("notify_delta_ph", v).apply()
    fun showDoseMl(): Boolean = prefs.getBoolean("notify_dose_ml", true)
    fun setShowDoseMl(v: Boolean) = prefs.edit().putBoolean("notify_dose_ml", v).apply()

    fun appendActivity(message: String) {
        val timestamp = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm:ss a")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.now())
        val line = "[$timestamp] $message"
        val current = prefs.getString("activity_log", "").orEmpty()
        val updated = if (current.isBlank()) line else current + "\n" + line
        // Keep a generous rolling history so the on-screen log remains responsive for long-running installs.
        val lines = updated.lineSequence().toList()
        val retained = if (lines.size > 5000) lines.takeLast(5000) else lines
        prefs.edit().putString("activity_log", retained.joinToString("\n")).apply()
    }

    fun activityLog(): String = prefs.getString("activity_log", "").orEmpty()

}
