package app.aquawiznotifier

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.text.method.ScrollingMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var store: SecureStore
    private lateinit var tabs: TabHost
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var serial: EditText
    private lateinit var interval: EditText
    private lateinit var region: Spinner
    private lateinit var phOpenAirCheck: CheckBox
    private lateinit var deltaPhCheck: CheckBox
    private lateinit var doseCheck: CheckBox
    private lateinit var status: TextView
    private lateinit var historyContainer: LinearLayout

    private val statusFmt = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())
    private val historyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    private val uiHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (::status.isInitialized) updateStatus()
            if (::historyContainer.isInitialized && ::tabs.isInitialized && tabs.currentTabTag == "history") updateHistory()
            uiHandler.postDelayed(this, 3000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        Notifier.ensureChannel(this)
        requestNotifications()
        setContentView(buildUi())
        populate()
        selectInitialTab()
        UpdateChecker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
        if (::historyContainer.isInitialized) updateHistory()
        uiHandler.removeCallbacks(refreshRunnable)
        uiHandler.postDelayed(refreshRunnable, 3000L)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun buildUi(): View {
        tabs = TabHost(this)
        val tabWidget = TabWidget(this).apply { id = android.R.id.tabs }
        val content = FrameLayout(this).apply { id = android.R.id.tabcontent }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabWidget, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        tabs.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tabs.setup()

        val statusView = buildStatusTab().apply { id = View.generateViewId() }
        val historyView = buildHistoryTab().apply { id = View.generateViewId() }
        val configView = buildConfigTab().apply { id = View.generateViewId() }

        content.addView(statusView)
        content.addView(historyView)
        content.addView(configView)

        tabs.addTab(tabs.newTabSpec("status").setIndicator("Status").setContent(statusView.id))
        tabs.addTab(tabs.newTabSpec("history").setIndicator("History").setContent(historyView.id))
        tabs.addTab(tabs.newTabSpec("config").setIndicator("Config").setContent(configView.id))
        tabs.setOnTabChangedListener {
            if (it == "status") updateStatus()
            if (it == "history") updateHistory()
        }
        return tabs
    }

    private fun buildStatusTab(): View {
        val scroll = ScrollView(this)
        val root = verticalRoot()
        scroll.addView(root)
        root.addView(textView("AquaWiz Notifier", 26f))
        root.addView(textView("Status", 20f))
        root.addView(textView("Live monitoring state and recent activity.", 14f))

        status = textView("", 13f).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod.getInstance()
            isVerticalScrollBarEnabled = true
        }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).apply {
            topMargin = dp(8)
        })

        val checkNow = Button(this).apply {
            text = "Check now"
            setOnClickListener {
                saveConfig()
                store.appendActivity("Manual AquaWiz check queued")
                PollScheduler.start(this@MainActivity, true)
                toast("Check queued")
                updateStatus()
            }
        }
        root.addView(checkNow, full())
        return scroll
    }

    private fun buildHistoryTab(): View {
        val scroll = ScrollView(this)
        historyContainer = verticalRoot()
        scroll.addView(historyContainer)
        return scroll
    }

    private fun buildConfigTab(): View {
        val scroll = ScrollView(this)
        val root = verticalRoot()
        scroll.addView(root)

        root.addView(textView("AquaWiz Notifier", 26f))
        val github = textView("GitHub: https://github.com/stevestokes/AquaWizNotificationsApp", 13f).apply {
            autoLinkMask = Linkify.WEB_URLS
            movementMethod = LinkMovementMethod.getInstance()
            linksClickable = true
        }
        root.addView(github)
        val author = textView("Made by Biff0rz • Reef2Reef: https://www.reef2reef.com/members/biff0rz.154703/", 13f).apply {
            autoLinkMask = Linkify.WEB_URLS
            movementMethod = LinkMovementMethod.getInstance()
            linksClickable = true
        }
        root.addView(author)

        root.addView(textView("Configuration", 20f))
        root.addView(textView("AquaWiz account, controller, polling and notification settings.", 14f))

        region = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Global (server.aquawiz.net)", "China (server.aquawiz.cn)")
            )
        }
        root.addView(textView("Server"))
        root.addView(region, full())

        username = field("AquaWiz username")
        password = field("Password", password = true)
        serial = field("Device serial (for example KH1-00-00002)")
        interval = field("Measurement interval in minutes (default 60)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(username, full())
        root.addView(password, full())
        root.addView(serial, full())
        root.addView(interval, full())

        root.addView(textView("Notification details", 15f))
        phOpenAirCheck = CheckBox(this).apply {
            text = "Show pH(O)"
            setOnCheckedChangeListener { _, checked -> store.setShowPhOpenAir(checked) }
        }
        deltaPhCheck = CheckBox(this).apply {
            text = "Show ΔpH"
            setOnCheckedChangeListener { _, checked -> store.setShowDeltaPh(checked) }
        }
        doseCheck = CheckBox(this).apply {
            text = "Show Dose (mL)"
            setOnCheckedChangeListener { _, checked -> store.setShowDoseMl(checked) }
        }
        root.addView(phOpenAirCheck, full())
        root.addView(deltaPhCheck, full())
        root.addView(doseCheck, full())

        val signIn = Button(this).apply { text = "Sign in & start"; setOnClickListener { signIn() } }
        val test = Button(this).apply { text = "Test notification"; setOnClickListener { Notifier.test(this@MainActivity) } }
        val updates = Button(this).apply {
            text = "Check for updates"
            setOnClickListener {
                store.appendActivity("Manual update check queued")
                UpdateChecker.checkNow(this@MainActivity)
                toast("Update check queued")
                updateStatus()
            }
        }
        val stop = Button(this).apply {
            text = "Stop & sign out"
            setOnClickListener {
                store.appendActivity("Monitoring stopped and AquaWiz session cleared")
                PollScheduler.cancel(this@MainActivity)
                store.clearSession()
                store.clearMonitoringState()
                toast("Stopped")
                tabs.currentTabTag = "config"
                updateStatus()
            }
        }

        root.addView(signIn, full())
        root.addView(test, full())
        root.addView(updates, full())
        root.addView(stop, full())
        root.addView(textView("Test notification uses the most recent stored AquaWiz measurement when available. Before the first real reading, it uses a normal sample value.", 12f))
        return scroll
    }

    private fun verticalRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(24))
    }

    private fun textView(label: String, size: Float = 16f) = TextView(this).apply {
        text = label
        textSize = size
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun field(hintText: String, password: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        setSingleLine(true)
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun selectInitialTab() {
        val session = store.session()
        val configured = session != null &&
            session.username.isNotBlank() &&
            session.password.isNotBlank() &&
            !store.selectedDevice().isNullOrBlank()
        tabs.currentTabTag = if (configured) "status" else "config"
    }

    private fun signIn() {
        val u = username.text.toString().trim()
        val p = password.text.toString()
        val typedSerial = serial.text.toString().trim()
        if (u.isBlank() || p.isBlank()) {
            toast("Enter username and password")
            tabs.currentTabTag = "config"
            return
        }
        saveConfig()
        store.appendActivity("Signing in to AquaWiz")
        updateStatus()

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
                        val baseline = api.latestMeasurement(session, chosenSerial)
                        store.saveMeasurement(chosenSerial, baseline)
                        store.setLastFingerprint(baseline.fingerprint)
                        store.setLastMeasurementEpochMs(baseline.measuredAt.toEpochMilli())
                        store.setLastKh(baseline.kh)
                        store.setLastPollEpochMs(System.currentTimeMillis())
                        store.setLastError(null)
                        store.appendActivity("Baseline stored in History for " + chosenSerial)
                        PollScheduler.schedule(
                            this,
                            PollCadence.nextRun(Instant.now(), baseline.measuredAt, store.measurementIntervalMinutes()),
                        )
                    } catch (e: Exception) {
                        store.setLastError("Initial data check: " + (e.message ?: e.javaClass.simpleName))
                        PollScheduler.start(this, true)
                    }
                }

                runOnUiThread {
                    if (typedSerial.isBlank() && chosenSerial.isNotBlank()) serial.setText(chosenSerial)
                    store.appendActivity(
                        if (chosenSerial.isBlank()) "Signed in; device serial still required"
                        else "Signed in; monitoring " + chosenSerial
                    )
                    toast(
                        if (chosenSerial.isBlank()) "Signed in. Enter your device serial."
                        else "Signed in. Monitoring started."
                    )
                    if (chosenSerial.isNotBlank()) tabs.currentTabTag = "status"
                    updateStatus()
                    updateHistory()
                }
            } catch (e: Exception) {
                store.appendActivity("Sign-in failed: " + (e.message ?: e.javaClass.simpleName))
                runOnUiThread {
                    tabs.currentTabTag = "config"
                    updateStatus()
                }
            }
        }.start()
    }

    private fun saveConfig() {
        store.setBaseUrl(if (region.selectedItemPosition == 1) AquaWizApi.CHINA_BASE else AquaWizApi.GLOBAL_BASE)
        serial.text.toString().trim().takeIf { it.isNotBlank() }?.let(store::setSelectedDevice)
        interval.text.toString().toLongOrNull()?.let(store::setMeasurementIntervalMinutes)
        store.setShowPhOpenAir(phOpenAirCheck.isChecked)
        store.setShowDeltaPh(deltaPhCheck.isChecked)
        store.setShowDoseMl(doseCheck.isChecked)
    }

    private fun populate() {
        val session = store.session()
        username.setText(session?.username.orEmpty())
        password.setText(session?.password.orEmpty())
        serial.setText(store.selectedDevice() ?: session?.devices?.firstOrNull().orEmpty())
        interval.setText(store.measurementIntervalMinutes().toString())
        region.setSelection(if (store.baseUrl().contains(".cn")) 1 else 0)
        phOpenAirCheck.isChecked = store.showPhOpenAir()
        deltaPhCheck.isChecked = store.showDeltaPh()
        doseCheck.isChecked = store.showDoseMl()
        updateStatus()
        updateHistory()
    }

    private fun updateStatus() {
        if (!::status.isInitialized) return
        val session = store.session()
        val lines = mutableListOf<String>()
        lines += "App version: " + BuildConfig.VERSION_NAME
        store.latestReleaseVersion()?.let { latest ->
            val update = if (UpdateChecker.isNewer(latest, BuildConfig.VERSION_NAME)) " (update available)" else ""
            lines += "Latest GitHub release: " + latest + update
        }
        store.lastUpdateError()?.let { lines += "Update check: " + it }
        lines += if (session == null) "Status: not signed in" else "Status: signed in as " + session.username
        store.selectedDevice()?.let { lines += "Device: " + it }

        store.lastStoredMeasurement()?.let { (_, measurement) ->
            val measurementLine = buildString {
                append("Latest: ")
                append("%.2f".format(measurement.kh))
                append(" dKH")
                measurement.ph?.let { append(", " + "%.2f".format(it) + " pH") }
                append(" at " + statusFmt.format(measurement.measuredAt))
            }
            lines += measurementLine
        }

        store.lastPollEpochMs()?.let { lines += "Last check: " + statusFmt.format(Instant.ofEpochMilli(it)) }
        store.lastError()?.let { lines += "Last error: " + it }
        store.nextPollEpochMs()?.let { lines += "Next eligible check: " + statusFmt.format(Instant.ofEpochMilli(it)) }
        lines += "Cadence: " + PollCadence.probeOffsetsMinutes(store.measurementIntervalMinutes()).joinToString { value -> "+" + value + "m" }
        lines += "Android may defer background work during Doze/battery optimization."

        val activity = store.activityLog()
        status.text = buildString {
            append(lines.joinToString("\n"))
            append("\n\n--- Activity ---\n")
            if (activity.isBlank()) append("No activity logged yet.") else append(activity)
        }
        status.post {
            val layout = status.layout
            if (layout != null) {
                val y = (layout.height - status.height + status.compoundPaddingBottom).coerceAtLeast(0)
                status.scrollTo(0, y)
            }
        }
    }

    private fun updateHistory() {
        if (!::historyContainer.isInitialized) return
        historyContainer.removeAllViews()
        historyContainer.addView(textView("History", 26f))
        historyContainer.addView(textView("Locally stored AquaWiz measurements. Notification display settings do not remove fields from History.", 13f))

        val history = store.measurementHistory()
        if (history.isEmpty()) {
            historyContainer.addView(textView("No measurements stored yet.", 15f))
            return
        }

        history.forEach { (deviceSerial, measurement) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            val primary = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            primary.addView(textView(historyFmt.format(measurement.measuredAt), 14f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.45f))
            primary.addView(textView("%.2f dKH".format(measurement.kh), 14f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
            primary.addView(textView(measurement.ph?.let { "pH: %.2f".format(it) } ?: "pH: —", 14f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f))
            row.addView(primary)

            val details = mutableListOf<String>()
            details += deviceSerial
            details += measurement.phOpenAir?.let { "pH(O) %.2f".format(it) } ?: "pH(O) —"
            details += measurement.deltaPh?.let { "ΔpH %+.2f".format(it) } ?: "ΔpH —"
            details += measurement.doseMl?.let { "Dose %.2f mL".format(it) } ?: "Dose —"
            row.addView(textView(details.joinToString(" • "), 12f))

            historyContainer.addView(row, full())
            historyContainer.addView(View(this).apply {
                setBackgroundColor(0x22000000)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        }
    }

    private fun requestNotifications() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
    }

    private fun full() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(6) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
