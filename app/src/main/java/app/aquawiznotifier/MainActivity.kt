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

    private lateinit var authMode: Spinner
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var accessToken: EditText
    private lateinit var connectButton: Button
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
            if (::historyContainer.isInitialized && currentSection == "history") updateHistory()
            if (::homeSection.isInitialized && currentSection == "home") homeSection.refreshFromLocal()
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
        selectInitialSection()
        UpdateChecker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
        if (::historyContainer.isInitialized) updateHistory()
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
        val scroll = ScrollView(this)
        historyContainer = verticalRoot()
        scroll.addView(historyContainer)
        return scroll
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
        authMode = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    "AquaWiz Web Login (recommended)",
                    "Existing bearer token",
                    "AquaWiz username/password (fallback)"
                )
            )
        }
        root.addView(authMode, full())

        username = field("AquaWiz username")
        password = field("Password", password = true)
        accessToken = field("Existing AquaWiz access token", password = true)
        serial = field("Device serial (for example KH1-00-00002)")
        interval = field("Measurement interval in minutes (default 60)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        root.addView(username, full())
        root.addView(password, full())
        root.addView(accessToken, full())
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
            text = "Sign in & start"
            setOnClickListener { signIn() }
        }

        authMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateAuthModeUi()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
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

        root.addView(textView("Recommended: use AquaWiz Web Login. The official AquaWiz page handles your credentials and the notifier captures only the returned bearer token. This avoids notifier-side credential login.", 12f))
        root.addView(textView("Manual bearer-token entry and username/password remain available as fallback options. Background polling never auto-logs in after a rejected token.", 12f))
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

    private fun field(hintText: String, password: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        setSingleLine(true)
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun updateAuthModeUi() {
        if (!::authMode.isInitialized || !::password.isInitialized || !::accessToken.isInitialized) return
        val mode = authMode.selectedItemPosition
        password.visibility = if (mode == 2) View.VISIBLE else View.GONE
        accessToken.visibility = if (mode == 1) View.VISIBLE else View.GONE
        if (::connectButton.isInitialized) {
            connectButton.text = when (mode) {
                0 -> "Open AquaWiz Web Login"
                1 -> "Validate token & start"
                else -> "Sign in & start"
            }
        }
    }

    private fun selectInitialSection() {
        val session = store.session()
        val configured = session != null &&
            session.username.isNotBlank() &&
            session.accessToken.isNotBlank() &&
            !store.selectedDevice().isNullOrBlank() &&
            (store.authMethod() != 2 || session.password.isNotBlank())

        showSection(if (configured) "home" else "config")
    }

    private fun signIn() {
        val mode = authMode.selectedItemPosition
        val u = username.text.toString().trim()
        val p = password.text.toString()
        val token = accessToken.text.toString().trim()
        val typedSerial = serial.text.toString().trim()

        if (u.isBlank()) {
            toast("Enter your AquaWiz username")
            showSection("config")
            return
        }
        if (typedSerial.isBlank()) {
            toast("Enter the AquaWiz device serial")
            showSection("config")
            return
        }

        saveConfig()

        when (mode) {
            0 -> {
                store.appendActivity("Opening official AquaWiz web login to obtain a bearer token")
                updateStatus()
                val loginUrl = if (store.baseUrl().contains(".cn")) {
                    "https://www.aquawiz.net/auth/cn/login"
                } else {
                    "https://www.aquawiz.net/auth/en"
                }
                AquaWizWebLogin(this, loginUrl) { capturedToken ->
                    accessToken.setText(capturedToken)
                    store.appendActivity("AquaWiz web login returned a bearer token; validating against controller")
                    connectWithBearerToken(u, capturedToken, typedSerial, "web")
                }.show()
            }
            1 -> {
                if (token.isBlank()) {
                    toast("Paste an AquaWiz access token")
                    return
                }
                store.appendActivity("Validating existing AquaWiz bearer token without calling /auth")
                updateStatus()
                connectWithBearerToken(u, token, typedSerial, "token")
            }
            else -> {
                if (p.isBlank()) {
                    toast("Enter username and password")
                    return
                }
                store.appendActivity("Signing in to AquaWiz with username/password")
                updateStatus()
                connectWithPassword(u, p, typedSerial)
            }
        }
    }

    private fun connectWithBearerToken(
        usernameValue: String,
        tokenValue: String,
        deviceSerial: String,
        source: String,
    ) {
        Thread {
            try {
                val api = AquaWizApi(store.baseUrl())
                val session = Session(
                    username = usernameValue,
                    password = "",
                    accessToken = tokenValue,
                    devices = listOf(deviceSerial),
                )
                val baseline = api.latestMeasurement(session, deviceSerial)
                finishConnection(session, deviceSerial, baseline, authMethod = if (source == "web") 0 else 1)
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
            }
        }.start()
    }

    private fun connectWithPassword(
        usernameValue: String,
        passwordValue: String,
        deviceSerial: String,
    ) {
        Thread {
            try {
                val api = AquaWizApi(store.baseUrl())
                val loggedIn = api.login(usernameValue, passwordValue)
                val session = loggedIn.copy(
                    devices = if (loggedIn.devices.isEmpty()) listOf(deviceSerial) else loggedIn.devices
                )
                val baseline = api.latestMeasurement(session, deviceSerial)
                finishConnection(session, deviceSerial, baseline, authMethod = 2)
                runOnUiThread {
                    toast("Signed in. Monitoring started.")
                    showSection("home")
                    updateStatus()
                    updateHistory()
                }
            } catch (e: Exception) {
                val message = "Sign-in failed: " + (e.message ?: e.javaClass.simpleName)
                store.appendActivity(message)
                runOnUiThread {
                    toast(message)
                    showSection("config")
                    updateStatus()
                }
            }
        }.start()
    }

    private fun finishConnection(
        session: Session,
        deviceSerial: String,
        baseline: Measurement,
        authMethod: Int,
    ) {
        store.clearMonitoringState()
        store.setAuthPaused(false)
        store.setAuthMethod(authMethod)
        store.setSharedTokenMode(authMethod != 2)
        store.saveSession(session)
        store.setSelectedDevice(deviceSerial)
        store.saveMeasurement(deviceSerial, baseline)
        store.setLastFingerprint(baseline.fingerprint)
        store.setLastMeasurementEpochMs(baseline.measuredAt.toEpochMilli())
        store.setLastKh(baseline.kh)
        store.setLastPollEpochMs(System.currentTimeMillis())
        store.setLastError(null)
        store.appendActivity(
            if (authMethod == 2) {
                "Signed in; monitoring " + deviceSerial
            } else {
                "Bearer token validated; monitoring " + deviceSerial + " without a notifier-side AquaWiz login"
            }
        )

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
        store.setAuthMethod(authMode.selectedItemPosition)
        store.setSharedTokenMode(authMode.selectedItemPosition != 2)
        serial.text.toString().trim().takeIf { it.isNotBlank() }?.let(store::setSelectedDevice)
        interval.text.toString().toLongOrNull()?.let(store::setMeasurementIntervalMinutes)
        store.setShowPhOpenAir(phOpenAirCheck.isChecked)
        store.setShowDeltaPh(deltaPhCheck.isChecked)
        store.setShowDoseMl(doseCheck.isChecked)
    }

    private fun populate() {
        val session = store.session()
        authMode.setSelection(store.authMethod())
        username.setText(session?.username.orEmpty())
        password.setText(session?.password.orEmpty())
        accessToken.setText(if (store.authMethod() == 1) session?.accessToken.orEmpty() else "")
        serial.setText(store.selectedDevice() ?: session?.devices?.firstOrNull().orEmpty())
        interval.setText(store.measurementIntervalMinutes().toString())
        region.setSelection(if (store.baseUrl().contains(".cn")) 1 else 0)
        phOpenAirCheck.isChecked = store.showPhOpenAir()
        deltaPhCheck.isChecked = store.showDeltaPh()
        doseCheck.isChecked = store.showDoseMl()
        updateAuthModeUi()
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
            store.authMethod() == 0 -> "Status: connected via AquaWiz Web Login as " + session.username
            store.authMethod() == 1 -> "Status: connected with AquaWiz bearer token as " + session.username
            else -> "Status: signed in as " + session.username
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
        if (!::historyContainer.isInitialized) return

        historyContainer.removeAllViews()
        historyContainer.addView(textView("History", 26f))
        historyContainer.addView(
            textView(
                "Locally stored AquaWiz measurements. Notification display settings do not remove fields from History.",
                13f
            )
        )

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

            primary.addView(
                textView(historyFmt.format(measurement.measuredAt), 14f),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.45f)
            )
            primary.addView(
                textView("%.2f dKH".format(measurement.kh), 14f),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f)
            )
            primary.addView(
                textView(measurement.ph?.let { "pH: %.2f".format(it) } ?: "pH: —", 14f),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f)
            )
            row.addView(primary)

            val details = mutableListOf<String>()
            details += deviceSerial
            details += measurement.phOpenAir?.let { "pH(O) %.2f".format(it) } ?: "pH(O) —"
            details += measurement.deltaPh?.let { "ΔpH %+.2f".format(it) } ?: "ΔpH —"
            details += measurement.doseMl?.let { "Dose %.2f mL".format(it) } ?: "Dose —"
            row.addView(textView(details.joinToString(" • "), 12f))

            historyContainer.addView(row, full())
            historyContainer.addView(
                View(this).apply { setBackgroundColor(0x22000000) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            )
        }
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
