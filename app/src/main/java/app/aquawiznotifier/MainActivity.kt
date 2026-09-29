package app.aquawiznotifier

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var store: SecureStore
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var serial: EditText
    private lateinit var interval: EditText
    private lateinit var status: TextView
    private lateinit var region: Spinner
    private val fmt = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        Notifier.ensureChannel(this)
        requestNotifications()
        setContentView(buildUi())
        populate()
        UpdateChecker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
        }
        scroll.addView(root)
        fun text(label: String, size: Float = 16f) = TextView(this).apply { text = label; textSize = size; setPadding(0, dp(6), 0, dp(6)) }
        root.addView(text("AquaWiz Notifier", 28f))

        val github = text("GitHub: https://github.com/stevestokes/AquaWizNotificationsApp", 13f).apply {
            autoLinkMask = Linkify.WEB_URLS
            movementMethod = LinkMovementMethod.getInstance()
            linksClickable = true
        }
        root.addView(github)

        val author = text("Made by Biff0rz • Find me on Reef2Reef: https://www.reef2reef.com/members/biff0rz.154703/", 13f).apply {
            autoLinkMask = Linkify.WEB_URLS
            movementMethod = LinkMovementMethod.getInstance()
            linksClickable = true
        }
        root.addView(author)

        root.addView(text("Unofficial Android notifier. Every newly detected KH measurement produces a local notification.", 15f))

        region = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Global (server.aquawiz.net)", "China (server.aquawiz.cn)")) }
        root.addView(text("Server")); root.addView(region, full())
        username = field("AquaWiz username")
        password = field("Password", password = true)
        serial = field("Device serial (for example KH1-00-00002)")
        interval = field("Measurement interval in minutes (default 60)").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        root.addView(username, full()); root.addView(password, full()); root.addView(serial, full()); root.addView(interval, full())

        val signIn = Button(this).apply { text = "Sign in & start"; setOnClickListener { signIn() } }
        val checkNow = Button(this).apply { text = "Check now"; setOnClickListener { saveConfig(); PollScheduler.start(this@MainActivity, true); toast("Check queued") } }
        val test = Button(this).apply { text = "Test notification"; setOnClickListener { Notifier.test(this@MainActivity) } }
        val updates = Button(this).apply { text = "Check for updates"; setOnClickListener { UpdateChecker.checkNow(this@MainActivity); toast("Update check queued") } }
        val stop = Button(this).apply { text = "Stop & sign out"; setOnClickListener { PollScheduler.cancel(this@MainActivity); store.clearSession(); store.clearMonitoringState(); toast("Stopped"); updateStatus() } }
        root.addView(signIn, full()); root.addView(checkNow, full()); root.addView(test, full()); root.addView(updates, full()); root.addView(stop, full())

        status = text("", 15f).apply { setPadding(0, dp(18), 0, 0) }
        root.addView(status, full())
        return scroll
    }

    private fun field(hintText: String, password: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        setSingleLine(true)
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun signIn() {
        val u = username.text.toString().trim()
        val p = password.text.toString()
        val typedSerial = serial.text.toString().trim()
        if (u.isBlank() || p.isBlank()) { toast("Enter username and password"); return }
        saveConfig()
        status.text = "Signing in…"

        Thread {
            try {
                val api = AquaWizApi(store.baseUrl())
                val session = api.login(u, p)
                store.clearMonitoringState()
                store.saveSession(session)
                val chosenSerial = typedSerial.ifBlank { session.devices.firstOrNull().orEmpty() }

                if (chosenSerial.isNotBlank()) {
                    store.setSelectedDevice(chosenSerial)
                    try {
                        // Establish a fresh baseline immediately. We intentionally do not notify for
                        // a reading that may have completed before the notifier was configured.
                        val baseline = api.latestMeasurement(session, chosenSerial)
                        store.setLastFingerprint(baseline.fingerprint)
                        store.setLastMeasurementEpochMs(baseline.measuredAt.toEpochMilli())
                        store.setLastKh(baseline.kh)
                        store.setLastPollEpochMs(System.currentTimeMillis())
                        store.setLastError(null)
                        PollScheduler.schedule(
                            this,
                            PollCadence.nextRun(Instant.now(), baseline.measuredAt, store.measurementIntervalMinutes()),
                        )
                    } catch (e: Exception) {
                        store.setLastError("Initial data check: ${e.message ?: e.javaClass.simpleName}")
                        PollScheduler.start(this, true)
                    }
                }

                runOnUiThread {
                    if (typedSerial.isBlank() && chosenSerial.isNotBlank()) serial.setText(chosenSerial)
                    toast(if (chosenSerial.isBlank()) "Signed in. Enter your device serial." else "Signed in. Monitoring started.")
                    updateStatus()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Sign-in failed: ${e.message}" }
            }
        }.start()
    }

    private fun saveConfig() {
        store.setBaseUrl(if (region.selectedItemPosition == 1) AquaWizApi.CHINA_BASE else AquaWizApi.GLOBAL_BASE)
        serial.text.toString().trim().takeIf { it.isNotBlank() }?.let(store::setSelectedDevice)
        interval.text.toString().toLongOrNull()?.let(store::setMeasurementIntervalMinutes)
    }

    private fun populate() {
        val s = store.session()
        username.setText(s?.username.orEmpty())
        password.setText(s?.password.orEmpty())
        serial.setText(store.selectedDevice() ?: s?.devices?.firstOrNull().orEmpty())
        interval.setText(store.measurementIntervalMinutes().toString())
        region.setSelection(if (store.baseUrl().contains(".cn")) 1 else 0)
        updateStatus()
    }

    private fun updateStatus() {
        val s = store.session()
        val lines = mutableListOf<String>()
        lines += "App version: ${BuildConfig.VERSION_NAME}"
        store.latestReleaseVersion()?.let { latest ->
            val update = if (UpdateChecker.isNewer(latest, BuildConfig.VERSION_NAME)) " (update available)" else ""
            lines += "Latest GitHub release: $latest$update"
        }
        store.lastUpdateError()?.let { lines += "Update check: $it" }
        lines += if (s == null) "Status: not signed in" else "Status: signed in as ${s.username}"
        store.selectedDevice()?.let { lines += "Device: $it" }
        store.lastKh()?.let { kh ->
            val t = store.lastMeasurementEpochMs()?.let { fmt.format(Instant.ofEpochMilli(it)) }.orEmpty()
            lines += "Latest detected: ${"%.2f".format(kh)} dKH${if (t.isNotBlank()) " at $t" else ""}"
        }
        store.lastPollEpochMs()?.let { lines += "Last check: ${fmt.format(Instant.ofEpochMilli(it))}" }
        store.lastError()?.let { lines += "Last error: $it" }
        store.nextPollEpochMs()?.let { lines += "Next eligible check: ${fmt.format(Instant.ofEpochMilli(it))}" }
        lines += "Cadence: ${PollCadence.probeOffsetsMinutes(store.measurementIntervalMinutes()).joinToString { "+${it}m" }} from each measurement"
        lines += "Note: Android may defer background work during Doze/battery optimization."
        status.text = lines.joinToString("\n")
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
    }

    private fun full() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
