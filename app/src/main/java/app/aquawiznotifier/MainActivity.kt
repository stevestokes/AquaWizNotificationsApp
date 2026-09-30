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
import android.view.WindowInsets
import android.widget.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var store: SecureStore

    private lateinit var homeSection: HomeDashboardView
    private lateinit var statusSection: View
    private lateinit var historySection: View
    private lateinit var configSection: View
    private lateinit var homeTabButton: Button
    private lateinit var statusTabButton: Button
    private lateinit var historyTabButton: Button
    private lateinit var configTabButton: Button
    private var currentSection = "config"

    private lateinit var username: EditText
    private lateinit var connectButton: Button
    private lateinit var serial: EditText
    private lateinit var interval: EditText
    private lateinit var region: Spinner
    private lateinit var phOpenAirCheck: CheckBox
    private lateinit var deltaPhCheck: CheckBox
    private lateinit var doseCheck: CheckBox

    private lateinit var status: TextView
    private lateinit var historyList: ListView
    private lateinit var historyAdapter: HistoryAdapter
    private var connecting = false
    private var webLoginOpen = false

    private val statusFmt get() = AppDates.formatter

    private val uiHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (::status.isInitialized) updateStatus()
            if (::historyList.isInitialized && currentSection == "history") updateHistory()
            if (::homeSection.isInitialized && currentSection == "home") homeSection.refreshFromLocal()
            uiHandler.postDelayed(this, 3000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        store.migrateWebLogin()
        Notifier.ensureChannel(this)
        requestNotifications()
        setContentView(buildUi())
        populate()
        selectInitialSection()
        if ((store.session()?.accessToken.isNullOrBlank() || store.authPaused()) && savedInstanceState == null) rootLogin()
        UpdateChecker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
        if (::historyList.isInitialized) updateHistory()
        if (::homeSection.isInitialized) homeSection.refreshFromLocal()
        uiHandler.removeCallbacks(refreshRunnable)
        uiHandler.postDelayed(refreshRunnable, 3000L)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars()) else null
                @Suppress("DEPRECATION")
                view.setPadding(bars?.left ?: insets.systemWindowInsetLeft, 0,
                    bars?.right ?: insets.systemWindowInsetRight, bars?.bottom ?: insets.systemWindowInsetBottom)
                insets
            }
        }

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
            setOnApplyWindowInsetsListener { view, insets ->
                val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.statusBars()).top
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetTop
                }
                view.setPadding(dp(8), topInset + dp(8), dp(8), dp(4))
                insets
            }
            post { requestApplyInsets() }
        }

        homeTabButton = tabButton("Home") { showSection("home") }
        statusTabButton = tabButton("Status") { showSection("status") }
        historyTabButton = tabButton("History") { showSection("history") }
        configTabButton = tabButton("Config") { showSection("config") }

        tabBar.addView(homeTabButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tabBar.addView(statusTabButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tabBar.addView(historyTabButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tabBar.addView(configTabButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val content = FrameLayout(this)
        homeSection = HomeDashboardView(this, store)
        statusSection = buildStatusSection()
        historySection = buildHistorySection()
        configSection = buildConfigSection()

        content.addView(homeSection, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(statusSection, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(historySection, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(configSection, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun tabButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun showSection(section: String) {
        currentSection = section
        homeSection.visibility = if (section == "home") View.VISIBLE else View.GONE
        statusSection.visibility = if (section == "status") View.VISIBLE else View.GONE
        historySection.visibility = if (section == "history") View.VISIBLE else View.GONE
        configSection.visibility = if (section == "config") View.VISIBLE else View.GONE

        homeTabButton.isEnabled = section != "home"
        statusTabButton.isEnabled = section != "status"
        historyTabButton.isEnabled = section != "history"
        configTabButton.isEnabled = section != "config"

        if (section == "home") homeSection.onShown()
        if (section == "status") updateStatus()
        if (section == "history") updateHistory()
    }

    private fun buildStatusSection(): View {
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
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340)).apply {
            topMargin = dp(8)
        })

        val checkNow = Button(this).apply {
            text = "Check now"
            setOnClickListener {
                if (store.authPaused()) {
                    toast("Monitoring is paused after an AquaWiz session conflict")
                    showSection("config")
                    return@setOnClickListener
                }
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

    private fun buildHistorySection(): View {
        val root = verticalRoot()
        root.addView(textView("History", 24f))
        root.addView(textView("Tap a reading for all values", 13f))
        historyAdapter = HistoryAdapter(this)
        historyList = ListView(this).apply {
            adapter = historyAdapter
            dividerHeight = dp(1)
            setOnItemClickListener { _, _, position, _ ->
                val (device, m) = historyAdapter.reading(position)
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle(AppDates.format(m.measuredAt))
                    .setMessage(buildString {
                        append(device + "\nKH: %.3f dKH".format(m.kh))
                        append("\npH: " + (m.ph?.let { "%.3f".format(it) } ?: "—"))
                        append("\npH(O): " + (m.phOpenAir?.let { "%.3f".format(it) } ?: "—"))
                        append("\nΔpH: " + (m.deltaPh?.let { "%+.3f".format(it) } ?: "—"))
                        append("\nDose: " + (m.doseMl?.let { "%.2f mL".format(it) } ?: "—"))
                    }).setPositiveButton("Close", null).show()
            }
        }
        val empty = textView("No measurements stored yet.", 15f)
        root.addView(empty)
        historyList.emptyView = empty
        root.addView(historyList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun buildConfigSection(): View {
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

        root.addView(textView("Authentication", 15f))
        username = field("AquaWiz username (if not returned by Web Login)")
        serial = field("Device serial (for example KH1-00-00002)")
        interval = field("Measurement interval in minutes (default 60)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        root.addView(username, full())
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

        connectButton = Button(this).apply {
            text = "Open AquaWiz Web Login"
            setOnClickListener { signIn() }
        }

        val test = Button(this).apply {
            text = "Test notification"
            setOnClickListener { Notifier.test(this@MainActivity) }
        }
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
            text = "Stop & disconnect"
            setOnClickListener {
                store.appendActivity("Monitoring stopped and notifier session cleared")
                PollScheduler.cancel(this@MainActivity)
                store.clearSession()
                store.clearMonitoringState()
                toast("Stopped")
                showSection("config")
                updateStatus()
            }
        }

        root.addView(textView("Use AquaWiz Web Login. The official AquaWiz page handles your credentials and the notifier captures only the returned bearer token. This avoids notifier-side credential login.", 12f))

        root.addView(connectButton, full())
        root.addView(test, full())
        root.addView(updates, full())
        root.addView(stop, full())
        root.addView(
            textView(
                "Test notification uses the most recent stored AquaWiz measurement when available. Before the first real reading, it uses a normal sample value.",
                12f
            )
        )
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

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        setSingleLine(true)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun selectInitialSection() {
        val configured = store.session()?.accessToken?.isNotBlank() == true && !store.selectedDevice().isNullOrBlank()
        showSection(if (configured && !store.authPaused()) "home" else "config")
    }

    private fun rootLogin() { window.decorView.post { signIn() } }

    private fun signIn() {
        if (connecting || webLoginOpen) return
        saveConfig()
        webLoginOpen = true
        val loginUrl = if (store.baseUrl().contains(".cn")) "https://www.aquawiz.net/auth/cn/login" else "https://www.aquawiz.net/auth/en"
        AquaWizWebLogin(this, loginUrl, onDismiss = { webLoginOpen = false }) { token, account, devices ->
            val u = account?.takeIf { it.isNotBlank() } ?: username.text.toString().trim()
            val device = serial.text.toString().trim().takeIf { it.isNotBlank() } ?: devices.singleOrNull().orEmpty()
            if (u.isNotBlank() && device.isNotBlank()) {
                username.setText(u)
                serial.setText(device)
                connectWithBearerToken(u, token, device)
            } else {
                val form = verticalRoot()
                val accountInput = field("AquaWiz username").apply { setText(u) }
                val deviceInput = field("Device serial").apply { setText(device) }
                form.addView(accountInput)
                form.addView(deviceInput)
                val dialog = android.app.AlertDialog.Builder(this)
                    .setTitle("Connect your controller")
                    .setMessage("Web Login succeeded. Enter the account and controller to monitor.")
                    .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Connect", null).create()
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val user = accountInput.text.toString().trim()
                        val sn = deviceInput.text.toString().trim()
                        if (user.isBlank()) accountInput.error = "Enter your username"
                        else if (sn.isBlank()) deviceInput.error = "Enter the device serial"
                        else {
                            username.setText(user)
                            serial.setText(sn)
                            dialog.dismiss()
                            connectWithBearerToken(user, token, sn)
                        }
                    }
                }
                dialog.show()
            }
        }.show()
    }

    private fun connectWithBearerToken(
        usernameValue: String,
        tokenValue: String,
        deviceSerial: String,
    ) {
        connecting = true
        connectButton.isEnabled = false
        Thread {
            try {
                val api = AquaWizApi(store.baseUrl())
                val session = Session(
                    username = usernameValue,
                    accessToken = tokenValue,
                    devices = listOf(deviceSerial),
                )
                val baseline = api.latestMeasurement(session, deviceSerial) { store.saveDeviceSummary(deviceSerial, it) }
                finishConnection(session, deviceSerial, baseline)
                runOnUiThread {
                    toast("AquaWiz token validated. Monitoring started.")
                    showSection("home")
                    updateStatus()
                    updateHistory()
                }
            } catch (e: Exception) {
                val message = "Token validation failed: " + (e.message ?: e.javaClass.simpleName)
                store.appendActivity(message)
                runOnUiThread {
                    toast(message)
                    showSection("config")
                    updateStatus()
                }
            } finally {
                runOnUiThread {
                    connecting = false
                    connectButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun finishConnection(
        session: Session,
        deviceSerial: String,
        baseline: Measurement,
    ) {
        store.clearMonitoringState()
        store.setAuthPaused(false)
        store.setAuthMethod(0)
        store.setSharedTokenMode(true)
        store.saveSession(session)
        store.setSelectedDevice(deviceSerial)
        store.saveMeasurement(deviceSerial, baseline)
        store.setLastFingerprint(baseline.fingerprint)
        store.setLastMeasurementEpochMs(baseline.measuredAt.toEpochMilli())
        store.setLastKh(baseline.kh)
        store.setLastPollEpochMs(System.currentTimeMillis())
        store.setLastError(null)
        store.appendActivity("Web Login connected; monitoring " + deviceSerial)

        PollScheduler.schedule(
            this,
            PollCadence.nextRun(
                Instant.now(),
                baseline.measuredAt,
                store.measurementIntervalMinutes()
            ),
        )
    }

    private fun saveConfig() {
        store.setBaseUrl(
            if (region.selectedItemPosition == 1) AquaWizApi.CHINA_BASE
            else AquaWizApi.GLOBAL_BASE
        )
        store.setAuthMethod(0)
        store.setSharedTokenMode(true)
        serial.text.toString().trim().takeIf { it.isNotBlank() }?.let(store::setSelectedDevice)
        interval.text.toString().toLongOrNull()?.let(store::setMeasurementIntervalMinutes)
        store.setShowPhOpenAir(phOpenAirCheck.isChecked)
        store.setShowDeltaPh(deltaPhCheck.isChecked)
        store.setShowDoseMl(doseCheck.isChecked)
    }

    private fun populate() {
        val session = store.session()
        username.setText(session?.username.orEmpty())
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
        lines += when {
            store.authPaused() -> "Status: monitoring paused (AquaWiz session conflict)"
            session == null -> "Status: not connected"
            else -> "Status: connected as " + session.username
        }
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

        store.lastPollEpochMs()?.let {
            lines += "Last check: " + statusFmt.format(Instant.ofEpochMilli(it))
        }
        store.lastError()?.let { lines += "Last error: " + it }
        store.nextPollEpochMs()?.let {
            lines += "Next eligible check: " + statusFmt.format(Instant.ofEpochMilli(it))
        }
        lines += "Cadence: " + PollCadence.probeOffsetsMinutes(store.measurementIntervalMinutes())
            .joinToString { value -> "+" + value + "m" }
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
        if (::historyAdapter.isInitialized) historyAdapter.submit(store.measurementHistory())
    }

    private fun requestNotifications() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        }
    }

    private fun full() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(6)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
