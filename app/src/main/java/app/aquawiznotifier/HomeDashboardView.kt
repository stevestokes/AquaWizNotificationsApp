package app.aquawiznotifier

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.concurrent.thread

class HomeDashboardView(context: Context, private val store: SecureStore) : ScrollView(context) {
    enum class Range(val label: String, val seconds: Long) {
        DAY("1D", 86400), THREE_DAYS("3D", 259200), WEEK("1W", 604800), MONTH("1M", 2592000), YEAR("1Y", 31536000)
    }
    private val regular = resources.getFont(R.font.aw_regular)
    private val bold = resources.getFont(R.font.aw_extrabold)
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundColor(0xFFF2F2F2.toInt())
    }
    private val khTime = label("", 10f, Color.DKGRAY)
    private val khValue = label("—", 40f, Color.BLACK, true)
    private val phTitle = label("PH", 14f, Color.WHITE)
    private val phValue = label("—", 40f, Color.WHITE, true)
    private val phStatus = label("Unavailable", 12f, Color.WHITE)
    private val khTarget = label("—", 22f, Color.BLACK, true)
    private val remainingDose = label("—", 22f, Color.BLACK, true)
    private val containerTotal = label("Set full volume", 10f, Color.GRAY).apply {
        setSingleLine(true)
        setAutoSizeTextTypeUniformWithConfiguration(8, 10, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
    }
    private val dosingBeaker = DosingBeakerView(context).apply { setOnClickListener { editDosingContainer() } }
    private val dailyDose = label("—", 22f, Color.BLACK, true)
    private val dosingSparkline = DosingSparklineView(context)
    private val syncStatus = label("", 12f, Color.GRAY)
    private val selectedTime = label("", 12f, Color.GRAY)
    private val chart = HomeChartView(context)
    private val toggles = linkedMapOf<ChartSeries, CheckBox>()
    private val buttons = linkedMapOf<Range, Button>()
    private var selectedRange = runCatching { Range.valueOf(store.chartRange()) }.getOrDefault(Range.DAY)
    private var inFlight = false
    private var fetchedKey: String? = null
    private var lastFetch = 0L
    private var points = emptyList<Measurement>()
    private var pointsDevice: String? = null
    @Volatile private var generation = 0
    private var displaySignature: String? = null
    private var sessionIdentity: Triple<String, String?, String?>? = null

    init {
        addView(root, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        buildSummary(); buildChart(); refreshFromLocal()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) return
        val nonChartHeight = root.measuredHeight - chart.measuredHeight
        val available = (measuredHeight - paddingTop - paddingBottom - nonChartHeight).coerceAtLeast(dp(160))
        if (chart.layoutParams.height != available) {
            chart.layoutParams = chart.layoutParams.apply { height = available }
            // Measure once more to fill the remaining viewport; short screens can still scroll.
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
    fun onShown() { refreshFromLocal(); refreshFromApi() }
    fun refreshFromLocal() {
        val identity = Triple(store.baseUrl(), store.selectedDevice(), store.session()?.accessToken)
        if (identity != sessionIdentity) {
            generation++; inFlight = false; fetchedKey = null; points = emptyList(); pointsDevice = null
            displaySignature = null; sessionIdentity = identity; chart.resetZoom()
        }
        val serial = store.selectedDevice().orEmpty()
        val all = store.measurementHistory().filter { it.first.equals(serial, true) }.map { it.second }
        val latest = all.maxByOrNull { it.measuredAt }
        val summary = store.deviceSummary(serial)
        khTime.text = latest?.let { AppDates.format(it.measuredAt) } ?: "No reading yet"
        khValue.text = latest?.let { "%.2f".format(it.kh) } ?: "—"
        phValue.text = latest?.ph?.let { "%.2f".format(it) } ?: "—"
        val probe = summary?.phProbeStatus
        phTitle.text = if (latest?.ph != null) "PH" else "PH Probe Health"
        if (probe != null && probe >= 1000) {
            phTitle.text = "PH Probe Status"; phValue.text = "Fail"
        } else if (latest?.ph == null && probe != null) {
            phValue.text = "%.0f%%".format(ProbeHealth.percent(probe));
        }
        phStatus.text = ProbeHealth.label(probe)
        phStatus.background = rounded(if (probe != null && probe in 100.0..999.0) 0xFF60C579.toInt() else 0x33333333, 6f)
        khTarget.text = summary?.khTarget?.let { "%.2f".format(it) } ?: "—"
        remainingDose.text = summary?.dosingRemainingMl?.let { it.toLong().toString() } ?: "—"
        remainingDose.setTextColor(if (summary?.dosingRemainingMl != null && summary.dosingWarningMl != null && summary.dosingRemainingMl < summary.dosingWarningMl) 0xFFE76C00.toInt() else Color.BLACK)
        val fullVolume = store.dosingContainerMl(serial)
        containerTotal.text = fullVolume?.let { "of ${it.toLong()} mL" } ?: "Set full volume"
        dosingBeaker.setLevel(serial, summary?.dosingRemainingMl, fullVolume, summary?.dosingWarningMl)
        val today = LocalDate.now()
        val todayReadings = all.filter { it.measuredAt.atZone(ZoneId.systemDefault()).toLocalDate() == today }.sortedBy { it.measuredAt }
        val todayValues = todayReadings.mapNotNull { it.doseMl?.takeIf { n -> n.isFinite() && n >= 0 } }
        dailyDose.text = if (todayValues.isEmpty()) "—" else todayValues.sum().toLong().toString()
        dosingSparkline.setMeasurements(todayReadings)
        val since = Instant.now().minusSeconds(selectedRange.seconds)
        val displayed = HistoryMerge.merge((all + (if (pointsDevice == serial) points else emptyList()))
            .map { serial to it }).map { it.second }.filter { !it.measuredAt.isBefore(since) }.sortedBy { it.measuredAt }
        chart.setOverview(selectedRange != Range.DAY)
        chart.setMeasurements(displayed, summary?.khLow, summary?.khHigh, summary?.khTarget)
        val signature = displayed.lastOrNull()?.toString() + selectedRange.name + serial
        if (signature != displaySignature) { displayed.lastOrNull()?.let(::showSelected); displaySignature = signature }
        buttons.forEach { (range, button) -> button.isSelected = range == selectedRange; AwUi.styleButton(button, range == selectedRange) }
    }
    private fun buildSummary() {
        val hero = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; background = DiagonalHero()
            clipToOutline = true; setPadding(dp(1), dp(1), dp(1), dp(1))
        }
        val kh = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(12), dp(12))
            addView(label("KH", 14f, Color.DKGRAY).apply { gravity = Gravity.CENTER_VERTICAL }, fullHeight(26))
            addView(valueWithUnit(khValue, "dKH", Color.BLACK), fullHeight(58))
            khTime.gravity = Gravity.CENTER_VERTICAL; khTime.setSingleLine(true)
            khTime.setAutoSizeTextTypeUniformWithConfiguration(8, 10, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            addView(khTime, fullHeight(26))
        }
        val ph = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(12), dp(12))
            phTitle.gravity = Gravity.CENTER_VERTICAL; phTitle.setSingleLine(true); phTitle.setPadding(dp(12), 0, 0, 0)
            phTitle.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            addView(phTitle, fullHeight(26))
            phValue.gravity = Gravity.CENTER_VERTICAL; phValue.setSingleLine(true); phValue.setPadding(dp(12), 0, 0, 0)
            phValue.setAutoSizeTextTypeUniformWithConfiguration(22, 40, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            addView(phValue, fullHeight(58))
            phStatus.gravity = Gravity.CENTER_VERTICAL; phStatus.setPadding(dp(5), 0, dp(5), 0); phStatus.setSingleLine(true)
            phStatus.setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            addView(phStatus, fullHeight(26))
        }
        hero.addView(kh, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hero.addView(ph, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(hero, full().apply { bottomMargin = dp(8) })
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(card("KH Target", khTarget, "dKH"), weighted(true))
        row.addView(HeroCardLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(2), dp(6), dp(2))
            addView(AwUi.button(context, "Calibrate").apply {
                textSize = 18f; background = rounded(Color.WHITE, 12f); setPadding(0, 0, 0, 0)
                setAutoSizeTextTypeUniformWithConfiguration(12, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                setSingleLine(true); setOnClickListener { openCalibration() }
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_calibrate_kh); scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(5), dp(11), dp(5), dp(11)); background = rounded(0xFFF1F1F1.toInt(), 16f)
                contentDescription = "Set true tank KH"; setOnClickListener { openCalibration() }
            }, LinearLayout.LayoutParams(dp(32), dp(44)))
        }, weighted(false))
        root.addView(row, full().apply { bottomMargin = dp(8) })
        val doses = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        doses.addView(card("KH Dosing", remainingDose, "mL"), weighted(true).apply { height = dp(78) })
        doses.addView(card("Today's Dosing", dailyDose, "mL"), weighted(false).apply { height = dp(78) })
        root.addView(doses, full().apply { bottomMargin = dp(8) })
    }
    private fun buildChart() {
        val card = HeroCardLayout(context, cornerRadius = 24).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        var row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        ChartSeries.values().forEachIndexed { index, series ->
            if (index % 3 == 0 && index > 0) { card.addView(row); row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL } }
            val check = CheckBox(context).apply {
                text = series.label; textSize = 11f; setPadding(0, 0, 0, 0); minHeight = 0; minimumHeight = 0
                setSingleLine(true); setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP); typeface = regular; setTextColor(series.color); buttonTintList = android.content.res.ColorStateList.valueOf(series.color)
                isChecked = store.chartVisible(series)
                setOnCheckedChangeListener { _, checked -> store.setChartVisible(series, checked); applyChartPreferences() }
                setOnLongClickListener {
                    AlertDialog.Builder(context).setTitle(series.label + " line style")
                        .setSingleChoiceItems(arrayOf("Solid", "Dashed", "Dotted"), store.chartLineStyle(series).ordinal) { dialog, which ->
                            store.setChartLineStyle(series, ChartLineStyle.values()[which]); applyChartPreferences(); dialog.dismiss()
                        }.show(); true
                }
                contentDescription = series.label + ", toggle line; long press to change line style"
            }
            toggles[series] = check
            row.addView(check, LinearLayout.LayoutParams(0, dp(32), 1f))
        }
        card.addView(row)
        card.addView(selectedTime)
        chart.onSelected = ::showSelected
        card.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200)))
        val ranges = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(4)) }
        Range.values().forEach { range ->
            val button = AwUi.button(context, range.label).apply {
                textSize = 13f; setPadding(0, 0, 0, 0)
                setOnClickListener { selectedRange = range; store.setChartRange(range.name); generation++; inFlight = false; chart.resetZoom(); refreshFromLocal(); refreshFromApi(force = true) }
            }
            buttons[range] = button
            ranges.addView(button, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        card.addView(ranges)
        syncStatus.textSize = 10f; syncStatus.maxLines = 2
        card.addView(syncStatus, full().apply { topMargin = dp(6) })
        chart.contentDescription = "Measurement chart. Drag to inspect, pinch to zoom, double tap to reset. Left scale: dKH and pH. Right scale: delta-pH and dose in mL."
        root.addView(card, full())
        applyChartPreferences()
    }
    private fun showSelected(m: Measurement) {
        selectedTime.text = AppDates.format(m.measuredAt)
        toggles.forEach { (series, toggle) -> toggle.text = series.label + " " + (series.value(m)?.let { "%.2f".format(it) } ?: "—") }
    }
    private fun applyChartPreferences() = chart.setSeriesVisibility(ChartSeries.values().filter { store.chartVisible(it) }.toSet(), ChartSeries.values().associateWith { store.chartLineStyle(it) })
    private fun refreshFromApi(force: Boolean = false) {
        val session = store.session() ?: run { syncStatus.text = "Connect AquaWiz in Config"; return }
        val serial = store.selectedDevice()?.takeIf { it.isNotBlank() } ?: return
        if (store.authPaused()) { syncStatus.text = "Session expired. Reconnect using Web Login in Config."; return }
        val baseUrl = store.baseUrl()
        val key = serial + baseUrl + selectedRange.name
        if (inFlight || (!force && fetchedKey == key && System.currentTimeMillis() - lastFetch < 60000)) return
        val requestGeneration = ++generation
        val range = selectedRange
        inFlight = true
        syncStatus.text = "Refreshing AquaWiz…"
        fun current() = requestGeneration == generation && store.session()?.accessToken == session.accessToken && store.selectedDevice() == serial && store.baseUrl() == baseUrl
        thread(name = "AquaWizHome") {
            var summaryError: String? = null
            try {
                val api = AquaWizApi(baseUrl, store::captureIngestion, "home")
                try {
                    val latest = api.latestMeasurement(session, serial) { if (current()) store.saveDeviceSummary(serial, it) }
                    if (current()) store.saveMeasurement(serial, latest)
                } catch (e: AquaWizApi.ApiException) { if (e.status == 401 || e.status == 403) throw e; summaryError = "Current status unavailable" }
                catch (e: Exception) { summaryError = "Current status unavailable" }
                val fetched = api.graphMeasurements(session, serial, Instant.now().minusSeconds(maxOf(range.seconds, 86400)))
                if (!current()) return@thread
                store.saveMeasurements(serial, fetched)
                post {
                    if (!current()) return@post
                    points = fetched; pointsDevice = serial; fetchedKey = key; lastFetch = System.currentTimeMillis(); inFlight = false
                    refreshFromLocal()
                    syncStatus.text = summaryError ?: if (fetched.isEmpty()) "No history returned for this range" else "Updated " + AppDates.format(Instant.now())
                }
            } catch (e: Exception) {
                if (!current()) return@thread
                val authFailure = e is AquaWizApi.ApiException && (e.status == 401 || e.status == 403)
                if (authFailure) { store.setAuthPaused(true); store.clearNextPollEpochMs(); PollScheduler.cancel(context) }
                store.appendActivity("Home refresh failed: " + (e.message ?: e.javaClass.simpleName))
                post {
                    if (!current()) return@post
                    inFlight = false; refreshFromLocal()
                    syncStatus.text = if (authFailure) "Session expired. Reconnect using Web Login in Config." else "Refresh failed. Showing saved readings. Pull down to retry."
                }
            }
        }
    }
    private fun openCalibration() {
        val session = store.session()
        val serial = store.selectedDevice()
        if (session == null || serial.isNullOrBlank() || store.authPaused()) {
            Toast.makeText(context, "Reconnect using Web Login in Config", Toast.LENGTH_SHORT).show()
            return
        }
        val baseUrl = store.baseUrl()
        fun current() = store.session()?.accessToken == session.accessToken &&
            store.selectedDevice() == serial && store.baseUrl() == baseUrl
        fun pauseIfExpired(error: Throwable) {
            if (current() && error is AquaWizApi.ApiException && (error.status == 401 || error.status == 403)) {
                store.setAuthPaused(true); store.clearNextPollEpochMs(); PollScheduler.cancel(context)
            }
        }
        KhCalibrationDialog(context, store.deviceSummary(serial)?.trueTankKh,
            load = { done ->
                thread(name = "AquaWizCalibrationRead") {
                    val result = runCatching {
                        check(current() && !store.authPaused()) { "Connection changed. Reconnect in Config" }
                        val summary = DeviceSummaryJson.parse(AquaWizApi(baseUrl).rawAllFields(session, serial), serial)
                            ?: error("Unable to identify this controller's calibration setting")
                        val value = summary.trueTankKh ?: error("AquaWiz did not return the current calibration setting")
                        check(current()) { "Connection changed. Reopen calibration" }
                        store.saveDeviceSummary(serial, summary)
                        value
                    }
                    result.exceptionOrNull()?.let(::pauseIfExpired)
                    post { done(result) }
                }
            },
            submit = { value, done ->
                thread(name = "AquaWizCalibrationWrite") {
                    val result = runCatching {
                        check(current() && !store.authPaused()) { "Connection changed. Reconnect in Config" }
                        AquaWizApi(baseUrl).setTrueTankKh(session, serial, value)
                        if (current()) {
                            store.deviceSummary(serial)?.let { store.saveDeviceSummary(serial, it.copy(trueTankKh = value.toDouble())) }
                            store.appendActivity("True tank KH calibration saved: " + value.stripTrailingZeros().toPlainString() + " dKH")
                        }
                    }
                    result.exceptionOrNull()?.let(::pauseIfExpired)
                    post {
                        done(result)
                        if (result.isSuccess) {
                            val message = if (value.signum() == 0) "Calibration disable setting saved. Select SYNC on the KHA to apply now."
                                else "Calibration saved. Select SYNC on the KHA to apply now."
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            refreshFromLocal()
                        }
                    }
                }
            }).show()
    }
    private fun openSettings(section: KhSettingsSection) {
        val session = store.session()
        val serial = store.selectedDevice()
        if (session == null || serial.isNullOrBlank() || store.authPaused()) {
            Toast.makeText(context, "Reconnect using Web Login in Config", Toast.LENGTH_SHORT).show()
            return
        }
        val baseUrl = store.baseUrl()
        fun current() = store.session()?.accessToken == session.accessToken && store.session()?.username == session.username &&
            store.selectedDevice() == serial && store.baseUrl() == baseUrl
        fun read(): DeviceSummary {
            check(current() && !store.authPaused()) { "Connection changed. Reconnect in Config" }
            val summary = DeviceSummaryJson.parse(AquaWizApi(baseUrl).rawAllFields(session, serial), serial)
                ?: error("Unable to identify this controller's settings")
            check(current() && !store.authPaused()) { "Connection changed. Reopen settings" }
            return summary
        }
        fun pauseIfExpired(error: Throwable) {
            if (current() && error is AquaWizApi.ApiException && (error.status == 401 || error.status == 403)) {
                store.setAuthPaused(true); store.clearNextPollEpochMs(); PollScheduler.cancel(context)
            }
        }
        KhSettingsDialog(context, section, serial,
            load = { done -> thread(name = "AquaWizSettingsRead") {
                val result = runCatching { read().also {
                    store.saveDeviceSummary(serial, it)
                } }
                result.exceptionOrNull()?.let(::pauseIfExpired)
                post { done(result) }
            } },
            submit = { target, dosing, done -> thread(name = "AquaWizSettingsWrite") {
                var saveMessage = "Settings saved. Select SYNC on the KHA to apply now."
                val result = runCatching {
                    // Read again before saving so packed dosing calibration digits are current.
                    val summary = read()
                    val api = AquaWizApi(baseUrl)
                    val updated = if (target != null) {
                        val receipt = api.setKhTargetAndReadBack(session, serial, target)
                        receipt.error?.let(::pauseIfExpired)
                        saveMessage = when {
                            receipt.matches -> "AquaWiz returned the saved settings. The KHA applies them at its next measurement or when you select SYNC."
                            receipt.summary != null -> "Request accepted. AquaWiz still returns different settings. Reopen KH Target after the next measurement or KHA SYNC to check again."
                            else -> "Request accepted, but settings could not be read back. Reopen KH Target to check before applying again."
                        }
                        // Keep actual server values, never replace them with the submitted form.
                        receipt.summary ?: summary
                    } else {
                        val value = requireNotNull(dosing)
                        val fields = KhDeviceSettings.dosingBody(session, serial, value, summary)
                        api.setKhDosing(session, serial, value, summary)
                        summary.copy(dosingRemainingMl = value.remaining.toDouble(), dosingWarningMl = value.emailThreshold.toDouble(),
                            dosingField5 = fields.getString("field5"), dosingField6 = fields.getString("field6"))
                    }
                    if (current()) {
                        store.saveDeviceSummary(serial, updated)
                        store.appendActivity(if (target != null) "KH target request accepted" else "KH dosing settings saved")
                    }
                }
                result.exceptionOrNull()?.let(::pauseIfExpired)
                post {
                    done(result)
                    if (result.isSuccess && current()) {
                        refreshFromLocal()
                        Toast.makeText(context, saveMessage, Toast.LENGTH_LONG).show()
                    }
                }
            } }).show()
    }
    private fun editDosingContainer() {
        val serial = store.selectedDevice() ?: return
        val input = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true); hint = "Full container volume in mL"
            contentDescription = "Full dosing container volume in mL"
            store.dosingContainerMl(serial)?.let { setText(java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()) }
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0)
            addView(label("Enter the volume when your dosing container is full. The beaker shows remaining mL as a percentage of this volume.", 14f, Color.DKGRAY), full())
            addView(input, full())
        }
        val dialog = AlertDialog.Builder(context).setTitle("Full container volume")
            .setView(panel).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = KhCalibration.parse(input.text.toString())?.toDouble()
                if (amount == null || !amount.isFinite() || amount <= 0) {
                    input.error = "Enter a volume greater than zero"; return@setOnClickListener
                }
                store.setDosingContainerMl(serial, amount)
                refreshFromLocal(); dialog.dismiss()
            }
        }
        dialog.show()
    }
    private fun card(title: String, value: TextView, unit: String) = HeroCardLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(4), dp(6), dp(4))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 11f, Color.DKGRAY).apply {
                setSingleLine(true)
                setAutoSizeTextTypeUniformWithConfiguration(9, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }, fullHeight(16))
            addView(valueWithUnit(value, unit, Color.BLACK), fullHeight(30))
            if (title == "KH Dosing") addView(containerTotal, fullHeight(14))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (title == "Today's Dosing") {
            addView(dosingSparkline, LinearLayout.LayoutParams(dp(56), dp(44)).apply { leftMargin = dp(4) })
        } else {
            val section = if (title == "KH Target") KhSettingsSection.TARGET else KhSettingsSection.DOSING
            setOnClickListener { openSettings(section) }
            if (section == KhSettingsSection.DOSING) {
                addView(dosingBeaker, LinearLayout.LayoutParams(dp(52), dp(66)).apply { leftMargin = dp(4) })
            } else addView(ImageButton(context).apply {
                setImageResource(if (section == KhSettingsSection.TARGET) R.drawable.ic_kh_target else R.drawable.ic_kh_dose)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(5), dp(11), dp(5), dp(11)); background = rounded(0xFFF1F1F1.toInt(), 16f)
                contentDescription = "Edit $title settings"; setOnClickListener { openSettings(section) }
            }, LinearLayout.LayoutParams(dp(32), dp(44)))
        }
    }
    private fun valueWithUnit(value: TextView, unit: String, color: Int): MeasurementValueView {
        value.gravity = Gravity.CENTER_VERTICAL; value.setSingleLine(true)
        value.setAutoSizeTextTypeUniformWithConfiguration(18, value.textSize.div(resources.displayMetrics.scaledDensity).toInt(), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        return MeasurementValueView(context, value, label(unit, 12f, color))
    }
    private fun label(value: String, size: Float, color: Int, heavy: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); typeface = if (heavy) bold else regular; includeFontPadding = false; setPadding(0, 0, 0, 0)
    }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat() }
    private fun weighted(left: Boolean) = LinearLayout.LayoutParams(0, dp(58), 1f).apply { if (left) rightMargin = dp(6) else leftMargin = dp(6) }
    private fun fullHeight(height: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height))
    private fun full() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private inner class DiagonalHero : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val box = RectF(bounds)
            val round = Path().apply { addRoundRect(box, dp(24).toFloat(), dp(24).toFloat(), Path.Direction.CW) }
            canvas.save(); canvas.clipPath(round); paint.shader = null; paint.color = Color.WHITE; canvas.drawRect(box, paint)
            paint.shader = LinearGradient(box.width() * 0.45f, 0f, box.width(), box.height(), 0xFF2386F8.toInt(), 0xFF397CF0.toInt(), Shader.TileMode.CLAMP)
            val diagonal = Path().apply { moveTo(box.width() * 0.57f, 0f); lineTo(box.width(), 0f); lineTo(box.width(), box.height()); lineTo(box.width() * 0.43f, box.height()); close() }
            canvas.drawPath(diagonal, paint); canvas.restore()
            paint.shader = null
            AwUi.drawBorder(canvas, box, dp(24).toFloat(), resources.displayMetrics.density)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
        override fun getOutline(outline: android.graphics.Outline) { outline.setRoundRect(bounds, dp(24).toFloat()) }
    }
}
