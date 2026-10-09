package app.aquawiznotifier

import android.Manifest
import android.content.Intent
import java.io.File
import kotlin.concurrent.thread
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
    private var supportExportFile: File? = null
    private var supportExportInFlight = false
    private val supportExportRequest = 701
    private lateinit var store: SecureStore

    private lateinit var homeSection: HomeDashboardView
    private lateinit var homeRefresh: PullRefreshView
    private lateinit var historyRefresh: PullRefreshView
    private var manualRefreshInFlight = false
    private lateinit var statusSection: View
    private lateinit var historySection: View
    private lateinit var configSection: View
    private lateinit var bottomNavigation: BottomNavigationView
    private var currentSection = "config"
    private var hasSelectedSection = false

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
    private lateinit var historySummary: TextView
    private lateinit var connectionState: TextView
    private lateinit var connectionDetails: TextView
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
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        SystemNavigation.configure(window)
        cacheDir.listFiles()?.filter { it.name.startsWith("graph-export-") && it.name.endsWith(".json") }?.forEach { it.delete() }
        store = SecureStore(this)
        supportExportFile = savedInstanceState?.getString("support_export_file")?.let { File(cacheDir, it) }
        val hadFutureAnchor = store.lastMeasurementEpochMs()?.let { it > Instant.now().plusSeconds(300).toEpochMilli() } == true
        store.measurementHistory()
        store.migrateWebLogin()
        Notifier.ensureChannel(this)
        requestNotifications()
        setContentView(buildUi())
        populate()
        selectInitialSection(savedInstanceState?.getString("current_section"))
        if ((store.session()?.accessToken.isNullOrBlank() || store.authPaused()) && savedInstanceState == null) rootLogin()
        UpdateChecker.schedule(this)
        if (hadFutureAnchor && store.session() != null && !store.authPaused()) PollScheduler.start(this, true)
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

    override fun onSaveInstanceState(outState: Bundle) {
        supportExportFile?.let { outState.putString("support_export_file", it.name) }
        outState.putString("current_section", currentSection)
        super.onSaveInstanceState(outState)
    }

    private fun exportSupportData() {
        if (supportExportInFlight || supportExportFile != null) return
        supportExportInFlight = true
        Toast.makeText(this, "Preparing support data…", Toast.LENGTH_SHORT).show()
        thread(name = "AquaWizSupportExport") {
            val result = runCatching {
                val raw = store.supportData()
                check(org.json.JSONObject(raw).getJSONArray("captures").length() > 0)
                // Export retained, redacted evidence without refetching the server.
                File.createTempFile("support-export-", ".json", cacheDir).apply { writeText(raw, Charsets.UTF_8) }
            }
            runOnUiThread {
                supportExportInFlight = false
                if (isFinishing || isDestroyed) { result.getOrNull()?.delete(); return@runOnUiThread }
                result.fold(onSuccess = { file ->
                    supportExportFile = file
                    try {
                        @Suppress("DEPRECATION")
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                            putExtra(Intent.EXTRA_TITLE, "AquaWiz-support-${java.time.LocalDate.now()}.json")
                        }, supportExportRequest)
                    } catch (_: Exception) {
                        file.delete(); supportExportFile = null
                        Toast.makeText(this, "Unable to open the file picker", Toast.LENGTH_LONG).show()
                    }
                }, onFailure = {
                    Toast.makeText(this, "No support data available yet. Refresh the app and try again.", Toast.LENGTH_LONG).show()
                })
            }
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != supportExportRequest) return
        val file = supportExportFile ?: return
        supportExportFile = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) { file.delete(); return }
        thread(name = "AquaWizSupportSave") {
            val saved = runCatching {
                val output = contentResolver.openOutputStream(uri, "wt") ?: error("Unable to open file")
                output.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            }.isSuccess
            file.delete()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) Toast.makeText(this,
                    if (saved) "Support data saved." else "Unable to save support data. Try again.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildUi(): View {
        bottomNavigation = BottomNavigationView(this, store.showStatusTab()) { showSection(it) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF2F2F2.toInt())
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                } else null
                @Suppress("DEPRECATION")
                val bottom = bars?.bottom ?: insets.systemWindowInsetBottom
                val keyboardBottom = if (Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(WindowInsets.Type.ime()).bottom
                } else if (bottom > dp(100)) bottom else 0
                val keyboardOpen = keyboardBottom > bottom || (bars == null && keyboardBottom > 0)
                @Suppress("DEPRECATION")
                view.setPadding(bars?.left ?: insets.systemWindowInsetLeft,
                    bars?.top ?: insets.systemWindowInsetTop,
                    bars?.right ?: insets.systemWindowInsetRight,
                    if (keyboardOpen) keyboardBottom else 0)
                bottomNavigation.setBottomInset(if (keyboardOpen) 0 else bottom)
                insets
            }
            post { requestApplyInsets() }
        }

        val content = FrameLayout(this)
        homeSection = HomeDashboardView(this, store)
        homeRefresh = PullRefreshView(this, { homeSection.canScrollVertically(-1) }, ::refreshMeasurements).apply {
            addView(homeSection, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        statusSection = buildStatusSection()
        historySection = buildHistorySection()
        configSection = buildConfigSection()

        content.addView(homeRefresh, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(statusSection, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(historySection, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(configSection, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun showSection(section: String) {
        val previous = currentSection
        val views = linkedMapOf("home" to homeRefresh, "history" to historySection, "status" to statusSection, "config" to configSection)
        val incoming = views[section] ?: return
        val outgoing = views[previous]
        val contentWidth = (incoming.parent as? View)?.width ?: 0
        val animate = hasSelectedSection && previous != section && contentWidth > 0 && android.animation.ValueAnimator.areAnimatorsEnabled()
        currentSection = section
        hasSelectedSection = true
        views.values.forEach { view ->
            view.animate().cancel(); view.animate().withEndAction(null)
            view.translationX = 0f; view.alpha = 1f
            view.visibility = if (view === incoming || (animate && view === outgoing)) View.VISIBLE else View.GONE
            view.importantForAccessibility = if (view === incoming) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        if (animate && outgoing != null) {
            val order = views.keys.toList()
            val direction = if (order.indexOf(section) > order.indexOf(previous)) 1f else -1f
            val distance = contentWidth.toFloat()
            incoming.translationX = direction * distance
            incoming.animate().translationX(0f).setDuration(240).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            outgoing.animate().translationX(-direction * distance).setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).withEndAction {
                    if (currentSection != previous) { outgoing.visibility = View.GONE; outgoing.translationX = 0f }
                }.start()
        }

        bottomNavigation.select(section)
        if (section == "config") updateConnectionState()
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
        val root = verticalRoot().apply { setBackgroundColor(0xFFF2F4F7.toInt()) }
        root.addView(AwUi.label(this, "History", 24f, true), full())
        historySummary = AwUi.label(this, "", 12f).apply { setTextColor(0xFF65758B.toInt()) }
        root.addView(historySummary, full().apply { bottomMargin = dp(12) })
        historyAdapter = HistoryAdapter(this, store)
        historyList = ListView(this).apply {
            adapter = historyAdapter; dividerHeight = 0; selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            background = AwUi.surface(this@MainActivity, radius = 14, border = false); clipToOutline = true
            setOnItemClickListener { _, _, position, _ ->
                val (serial, measurement) = historyAdapter.getItem(position)
                MeasurementNoteDialog(this@MainActivity, serial, measurement, store, ::updateHistory).show()
            }
        }
        val empty = AwUi.label(this, "No measurements stored yet.", 15f).apply { setPadding(0, dp(24), 0, 0) }
        root.addView(empty)
        historyList.emptyView = empty
        root.addView(FrameLayout(this).apply {
            background = AwUi.surface(this@MainActivity, radius = 16)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            addView(historyList, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        historyRefresh = PullRefreshView(this, { historyList.canScrollVertically(-1) }, ::refreshMeasurements).apply {
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        return historyRefresh
    }

    private fun refreshMeasurements() {
        if (manualRefreshInFlight) return
        fun finish(message: String) {
            manualRefreshInFlight = false
            homeRefresh.isRefreshing = false; historyRefresh.isRefreshing = false
            homeSection.refreshFromLocal(); updateHistory()
            toast(message)
        }
        val session = store.session()
        val serial = store.selectedDevice()
        if (session == null || serial.isNullOrBlank() || store.authPaused()) {
            finish("Reconnect using Web Login in Config")
            return
        }
        manualRefreshInFlight = true
        homeSection.refreshAsync { message ->
            if (!isFinishing && !isDestroyed) finish(message)
        }
    }

    private fun buildConfigSection(): View {
        val scroll = ScrollView(this)
        val root = verticalRoot().apply { setBackgroundColor(0xFFF2F4F7.toInt()) }
        scroll.addView(root)
        root.addView(AwUi.label(this, "Config", 24f, true), full().apply { bottomMargin = dp(12) })

        val account = sectionCard("AquaWiz connection")
        connectionState = AwUi.label(this, "", 16f, true)
        connectionDetails = AwUi.label(this, "", 12f).apply { setTextColor(0xFF65758B.toInt()); setLineSpacing(dp(4).toFloat(), 1f) }
        account.addView(connectionState, full())
        account.addView(connectionDetails, full())
        connectButton = AwUi.button(this, "Open AquaWiz Web Login", true).apply { setOnClickListener { signIn() } }
        account.addView(connectButton, full())

        region = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Global (server.aquawiz.net)", "China (server.aquawiz.cn)"))
        }
        username = field("AquaWiz username")
        serial = field("Controller serial")
        interval = field("Measurement interval in minutes").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val settings = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            addView(AwUi.label(this@MainActivity, "Server", 12f), full()); addView(region, full())
            addView(AwUi.label(this@MainActivity, "Account", 12f), full()); addView(username, full())
            addView(AwUi.label(this@MainActivity, "Controller", 12f), full()); addView(serial, full())
            addView(AwUi.label(this@MainActivity, "Measurement interval (minutes)", 12f), full()); addView(interval, full())
            addView(AwUi.button(this@MainActivity, "Save connection settings").apply {
                setOnClickListener {
                    saveConfig(); settingsVisible(false); updateConnectionState(); toast("Connection settings saved")
                }
            }, full())
        }
        // Keep connection editors available without crowding everyday configuration.
        connectionEditors = settings
        account.addView(AwUi.button(this, "Connection settings").apply {
            setOnClickListener { settingsVisible(settings.visibility != View.VISIBLE) }
        }, full())
        account.addView(settings, full())
        root.addView(account, full().apply { bottomMargin = dp(12) })

        val notifications = sectionCard("Notification details")
        notifications.addView(AwUi.label(this, "KH and pH are always included. Choose additional values.", 12f), full())
        phOpenAirCheck = notificationToggle("pH(O) · fully aerated pH") { store.setShowPhOpenAir(it) }
        deltaPhCheck = notificationToggle("ΔpH · pH difference") { store.setShowDeltaPh(it) }
        doseCheck = notificationToggle("Dose · mL") { store.setShowDoseMl(it) }
        notifications.addView(phOpenAirCheck, full()); notifications.addView(deltaPhCheck, full()); notifications.addView(doseCheck, full())
        notifications.addView(AwUi.button(this, "Test notification").apply { setOnClickListener { Notifier.test(this@MainActivity) } }, full())
        root.addView(notifications, full().apply { bottomMargin = dp(12) })

        val actions = sectionCard("App")
        actions.addView(AwUi.label(this, "Version " + BuildConfig.VERSION_NAME, 12f), full())
        actions.addView(Switch(this).apply {
            text = "Show Status in bottom menu"; textSize = 14f; typeface = resources.getFont(R.font.aw_regular)
            setTextColor(AwUi.INK); setPadding(0, dp(8), 0, dp(8))
            isChecked = store.showStatusTab()
            setOnCheckedChangeListener { _, checked ->
                store.setShowStatusTab(checked)
                bottomNavigation.setStatusVisible(checked)
                if (!checked && currentSection == "status") showSection("config")
            }
        }, full())
        actions.addView(AwUi.button(this, "Check for updates").apply {
            setOnClickListener { store.appendActivity("Manual update check queued"); UpdateChecker.checkNow(this@MainActivity); toast("Update check queued"); updateStatus() }
        }, full())
        actions.addView(AwUi.button(this, "Export support data").apply {
            setOnClickListener { exportSupportData() }
        }, full())
        actions.addView(AwUi.button(this, "Stop & disconnect").apply {
            setTextColor(0xFFB64242.toInt())
            setOnClickListener {
                store.appendActivity("Monitoring stopped and notifier session cleared")
                PollScheduler.cancel(this@MainActivity); store.clearSession(); store.clearMonitoringState()
                toast("Stopped"); showSection("config"); updateStatus()
            }
        }, full())
        root.addView(actions, full())
        root.addView(textView("Made by Biff0rz · Reef2Reef: https://www.reef2reef.com/members/biff0rz.154703/", 11f).apply {
            autoLinkMask = Linkify.WEB_URLS; movementMethod = LinkMovementMethod.getInstance(); linksClickable = true
        }, full())
        root.addView(textView("GitHub: https://github.com/stevestokes/AquaWizNotificationsApp", 11f).apply {
            autoLinkMask = Linkify.WEB_URLS; movementMethod = LinkMovementMethod.getInstance(); linksClickable = true
        }, full())
        return scroll
    }
    private lateinit var connectionEditors: LinearLayout
    private fun settingsVisible(visible: Boolean) { connectionEditors.visibility = if (visible) View.VISIBLE else View.GONE }
    private fun sectionCard(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = AwUi.surface(this@MainActivity)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(AwUi.label(this@MainActivity, title, 17f, true), full().apply { topMargin = 0; bottomMargin = dp(6) })
    }
    private fun notificationToggle(title: String, changed: (Boolean) -> Unit) = CheckBox(this).apply {
        text = title; textSize = 14f; typeface = resources.getFont(R.font.aw_regular)
        setTextColor(AwUi.INK); buttonTintList = android.content.res.ColorStateList.valueOf(AwUi.BLUE)
        setOnCheckedChangeListener { _, checked -> changed(checked) }
    }
    private fun updateConnectionState() {
        if (!::connectionState.isInitialized) return
        val session = store.session()
        connectionState.text = when { store.authPaused() -> "Session expired · reconnect"; session == null -> "Not connected"; else -> "Connected" }
        connectionState.setTextColor(if (session != null && !store.authPaused()) 0xFF26985A.toInt() else AwUi.INK)
        connectionDetails.text = listOfNotNull(session?.username, store.selectedDevice(),
            if (store.baseUrl().contains(".cn")) "China server" else "Global server").joinToString(" · ")
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

    private fun selectInitialSection(restoredSection: String? = null) {
        val configured = store.session()?.accessToken?.isNotBlank() == true && !store.selectedDevice().isNullOrBlank()
        val restored = restoredSection?.takeIf {
            it in listOf("home", "history", "config") || (it == "status" && store.showStatusTab())
        }
        showSection(if (configured && !store.authPaused()) restored ?: "home" else "config")
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
                val api = AquaWizApi(store.baseUrl(), store::captureIngestion, "connect")
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
        updateConnectionState()
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
        if (::historyAdapter.isInitialized) {
            val readings = store.measurementHistory()
            historyAdapter.submit(readings)
            historySummary.text = readings.size.toString() + " readings · Newest first"
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
