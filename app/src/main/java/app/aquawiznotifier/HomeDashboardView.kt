package app.aquawiznotifier

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.Gravity
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
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)); setBackgroundColor(0xFFF2F2F2.toInt())
    }
    private val khTime = label("KH", 12f, Color.DKGRAY)
    private val khValue = label("—", 40f, Color.BLACK, true)
    private val phTitle = label("PH", 14f, Color.WHITE)
    private val phValue = label("—", 40f, Color.WHITE, true)
    private val phStatus = label("Unavailable", 12f, Color.WHITE)
    private val khTarget = label("—", 30f, Color.BLACK, true)
    private val remainingDose = label("—", 30f, Color.BLACK, true)
    private val dailyDose = label("—", 30f, Color.BLACK, true)
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
        khTime.text = "KH" + (latest?.let { "  (" + AppDates.format(it.measuredAt) + ")" } ?: "")
        khValue.text = latest?.let { "%.2f".format(it.kh) } ?: "—"
        phValue.text = latest?.ph?.let { "%.2f".format(it) } ?: "—"
        val probe = summary?.phProbeStatus
        phTitle.text = if (latest?.ph != null) "PH" else "PH Probe Health"
        if (probe != null && probe >= 1000) {
            phTitle.text = "PH Probe Status"; phValue.text = "Fail"; phStatus.text = "PH Probe: Check probe"
        } else if (latest?.ph == null && probe != null) {
            phValue.text = "%.0f%%".format(probe.coerceAtMost(100.0)); phStatus.text = "PH Probe Health"
        } else phStatus.text = if (probe == null) "Probe status unavailable" else if (probe >= 100) "PH Probe: Healthy" else "PH Probe: %.0f%%".format(probe)
        phStatus.background = rounded(if (probe != null && probe in 100.0..999.0) 0xFF60C579.toInt() else 0x33333333, 6f)
        khTarget.text = summary?.khTarget?.let { "%.2f".format(it) } ?: "—"
        remainingDose.text = summary?.dosingRemainingMl?.let { "%.0f".format(it) } ?: "—"
        remainingDose.setTextColor(if (summary?.dosingRemainingMl != null && summary.dosingWarningMl != null && summary.dosingRemainingMl < summary.dosingWarningMl) 0xFFE76C00.toInt() else Color.BLACK)
        val today = LocalDate.now()
        val todayValues = all.filter { it.measuredAt.atZone(ZoneId.systemDefault()).toLocalDate() == today }.mapNotNull { it.doseMl }
        dailyDose.text = if (todayValues.isEmpty()) "—" else "%.2f".format(todayValues.sum())
        val since = Instant.now().minusSeconds(selectedRange.seconds)
        val displayed = ((if (pointsDevice == serial) points else emptyList()) + all)
            .filter { !it.measuredAt.isBefore(since) }.distinctBy { it.measuredAt }.sortedBy { it.measuredAt }
        chart.setMeasurements(displayed, summary?.khLow, summary?.khHigh)
        val signature = displayed.lastOrNull()?.toString() + selectedRange.name + serial
        if (signature != displaySignature) { displayed.lastOrNull()?.let(::showSelected); displaySignature = signature }
        buttons.forEach { (range, button) -> button.isSelected = range == selectedRange; button.setTextColor(if (range == selectedRange) Color.WHITE else Color.DKGRAY); button.background = rounded(if (range == selectedRange) 0xFF3B82F6.toInt() else 0xFFF3F3F3.toInt(), 12f) }
    }
    private fun buildSummary() {
        val hero = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; background = DiagonalHero(); elevation = dp(4).toFloat(); clipToOutline = true }
        val kh = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(14), dp(4), dp(14)); addView(khTime)
            addView(valueWithUnit(khValue, "dKH", Color.BLACK))
        }
        val ph = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(14), dp(12), dp(14)); addView(phTitle); addView(phValue); addView(phStatus)
        }
        hero.addView(kh, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hero.addView(ph, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(hero, full().apply { bottomMargin = dp(12) })
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(card("KH Target", khTarget, "dKH"), weighted(true))
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = rounded(Color.WHITE, 24f); elevation = dp(3).toFloat(); setPadding(dp(10), dp(14), dp(10), dp(14))
            addView(Button(context).apply {
                text = "Take me to the AW app"; isAllCaps = false; textSize = 17f; typeface = bold; setTextColor(Color.BLACK); background = rounded(Color.WHITE, 18f)
                setOnClickListener { openOfficialAquaWiz() }
            }, full())
        }, weighted(false))
        root.addView(row, full().apply { bottomMargin = dp(12) })
        val doses = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        doses.addView(card("KH Dosing", remainingDose, "mL"), weighted(true))
        doses.addView(card("Today's Dosing", dailyDose, "mL"), weighted(false))
        root.addView(doses, full().apply { bottomMargin = dp(14) })
    }
    private fun buildChart() {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(Color.WHITE, 24f); elevation = dp(3).toFloat()
        }
        var row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        ChartSeries.values().forEachIndexed { index, series ->
            if (index % 2 == 0 && index > 0) { card.addView(row); row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL } }
            val check = CheckBox(context).apply {
                text = series.label; textSize = 12f; typeface = regular; setTextColor(series.color); buttonTintList = android.content.res.ColorStateList.valueOf(series.color)
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
            row.addView(check, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addView(row)
        card.addView(selectedTime)
        chart.onSelected = ::showSelected
        card.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)))
        val ranges = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        Range.values().forEach { range ->
            val button = Button(context).apply {
                text = range.label; textSize = 13f; isAllCaps = false; minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
                setOnClickListener { selectedRange = range; store.setChartRange(range.name); generation++; inFlight = false; chart.resetZoom(); refreshFromLocal(); refreshFromApi(force = true) }
            }
            buttons[range] = button
            ranges.addView(button, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        card.addView(ranges)
        card.addView(label("Drag to inspect · Pinch to zoom · Double tap to reset", 12f, Color.GRAY))
        card.addView(syncStatus)
        card.addView(Button(context).apply { text = "Refresh"; isAllCaps = false; setOnClickListener { refreshFromApi(force = true) } }, full())
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
                val api = AquaWizApi(baseUrl)
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
                    syncStatus.text = if (authFailure) "Session expired. Reconnect using Web Login in Config." else "Refresh failed. Showing saved readings. Tap Refresh to retry."
                }
            }
        }
    }
    private fun openOfficialAquaWiz() {
        val launch = context.packageManager.getLaunchIntentForPackage("com.kuannnn.aquawiz")
        try { context.startActivity((launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.aquawiz.net"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Toast.makeText(context, "Unable to open AquaWiz", Toast.LENGTH_SHORT).show() }
    }
    private fun card(title: String, value: TextView, unit: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(Color.WHITE, 24f); elevation = dp(3).toFloat(); minimumHeight = dp(100)
        val heading = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label(title, 14f, Color.DKGRAY), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (title != "Today's Dosing") heading.addView(TextView(context).apply {
            text = "⚙"; textSize = 18f; gravity = Gravity.CENTER; background = rounded(0xFFF1F1F1.toInt(), 16f)
            contentDescription = "Open " + title + " settings in AquaWiz"; setOnClickListener { openOfficialAquaWiz() }
        }, LinearLayout.LayoutParams(dp(30), dp(30)))
        addView(heading); addView(valueWithUnit(value, unit, Color.BLACK))
    }
    private fun valueWithUnit(value: TextView, unit: String, color: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
        value.setAutoSizeTextTypeUniformWithConfiguration(18, value.textSize.div(resources.displayMetrics.scaledDensity).toInt(), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        addView(value, LinearLayout.LayoutParams(0, dp(58), 1f))
        addView(label(unit, 12f, color).apply { setPadding(dp(3), 0, 0, dp(9)) })
    }
    private fun label(value: String, size: Float, color: Int, heavy: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); typeface = if (heavy) bold else regular; setPadding(0, dp(2), 0, dp(2))
    }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat() }
    private fun weighted(left: Boolean) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (left) rightMargin = dp(6) else leftMargin = dp(6) }
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
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
        override fun getOutline(outline: android.graphics.Outline) { outline.setRoundRect(bounds, dp(24).toFloat()) }
    }
}
