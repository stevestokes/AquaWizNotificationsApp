package app.aquawiznotifier

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.concurrent.thread

class HomeDashboardView(
    context: Context,
    private val store: SecureStore,
) : ScrollView(context) {

    enum class Range(val label: String, val seconds: Long) {
        DAY("1D", 24L * 60L * 60L),
        THREE_DAYS("3D", 3L * 24L * 60L * 60L),
        WEEK("1W", 7L * 24L * 60L * 60L),
        MONTH("1M", 30L * 24L * 60L * 60L),
        YEAR("1Y", 365L * 24L * 60L * 60L),
    }

    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(28))
        setBackgroundColor(Color.rgb(247, 247, 247))
    }

    private val khTimestamp = label("KH", 15f, Color.rgb(105, 105, 105))
    private val khValue = label("—", 42f, Color.BLACK, bold = true)
    private val phValue = label("—", 40f, Color.WHITE, bold = true)
    private val phProbe = label("pH", 13f, Color.WHITE)
    private val khTargetValue = label("—", 30f, Color.BLACK, bold = true)
    private val latestDoseValue = label("—", 30f, Color.rgb(230, 95, 0), bold = true)
    private val todayDoseValue = label("—", 22f, Color.rgb(210, 55, 45), bold = true)

    private val legendKh = label("KH —", 14f, Color.rgb(91, 52, 255))
    private val legendPh = label("pH —", 14f, Color.rgb(59, 191, 91))
    private val legendPhOpenAir = label("pH(O) —", 14f, Color.rgb(29, 153, 69))
    private val legendDelta = label("ΔpH —", 14f, Color.rgb(22, 174, 137))
    private val chartTimestamp = label("", 12f, Color.rgb(95, 95, 95))

    private val chart = HomeChartView(context)
    private val rangeButtons = linkedMapOf<Range, Button>()
    private var selectedRange = Range.DAY
    private var fetchedRange: Range? = null

    private val shortTime = DateTimeFormatter.ofPattern("HH:mm, MM/dd")
        .withZone(ZoneId.systemDefault())

    init {
        addView(root, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        buildSummary()
        buildChart()
        refreshFromLocal()
    }

    fun onShown() {
        refreshFromLocal()
        if (fetchedRange != selectedRange) {
            refreshFromApi(selectedRange)
        }
    }

    fun refreshFromLocal() {
        val latest = store.lastStoredMeasurement()?.second
        val all = store.measurementHistory().map { it.second }
        val since = Instant.now().minusSeconds(selectedRange.seconds)
        val ranged = all.filter { !it.measuredAt.isBefore(since) }.sortedBy { it.measuredAt }

        if (latest != null) {
            khTimestamp.text = "KH  (" + shortTime.format(latest.measuredAt) + ")"
            khValue.text = "%.2f dKH".format(latest.kh)
            phValue.text = latest.ph?.let { "%.2f".format(it) } ?: "—"
            phProbe.text = if (latest.ph != null) "pH reading available" else "pH unavailable"
            latestDoseValue.text = latest.doseMl?.let { "%.2f mL".format(it) } ?: "Unavailable"

            legendKh.text = "KH " + "%.3f".format(latest.kh)
            legendPh.text = "pH " + (latest.ph?.let { "%.3f".format(it) } ?: "—")
            legendPhOpenAir.text = "pH(O) " + (latest.phOpenAir?.let { "%.3f".format(it) } ?: "—")
            legendDelta.text = "ΔpH " + (latest.deltaPh?.let { "%+.2f".format(it) } ?: "—")
            chartTimestamp.text = shortTime.format(latest.measuredAt)
        } else {
            khTimestamp.text = "KH"
            khValue.text = "—"
            phValue.text = "—"
            phProbe.text = "pH unavailable"
            latestDoseValue.text = "Unavailable"
            legendKh.text = "KH —"
            legendPh.text = "pH —"
            legendPhOpenAir.text = "pH(O) —"
            legendDelta.text = "ΔpH —"
            chartTimestamp.text = ""
        }

        val zone = ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val todayDoseValues = all
            .filter { it.measuredAt.atZone(zone).toLocalDate() == today }
            .mapNotNull { it.doseMl }
        todayDoseValue.text = if (todayDoseValues.isNotEmpty()) {
            "%.2f mL".format(todayDoseValues.sum())
        } else {
            "Unavailable"
        }

        // KH target/high/low are not yet mapped from the AquaWiz API. The chart API accepts
        // explicit limits so the next phase can plug them in without changing rendering code.
        khTargetValue.text = "—"

        chart.setMeasurements(ranged, lowLimit = null, highLimit = null)
        updateRangeButtons()
    }

    private fun buildSummary() {
        val hero = horizontalCard().apply {
            minimumHeight = dp(154)
        }

        val khSide = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(10), dp(14))
            background = rounded(Color.WHITE, 22f)
            addView(khTimestamp)
            addView(khValue)
        }
        hero.addView(khSide, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val phSide = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(25, 139, 242), 22f)
            addView(label("PH", 15f, Color.WHITE))
            addView(phValue)
            addView(phProbe)
        }
        hero.addView(phSide, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        root.addView(hero, full().apply { bottomMargin = dp(12) })

        val secondRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        secondRow.addView(
            smallCard(
                title = "KH Target",
                valueView = khTargetValue,
                subtitle = "Target not yet mapped from AquaWiz"
            ),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
        )

        val awButtonCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(18), dp(12), dp(18))
            background = rounded(Color.WHITE, 22f)
            elevation = dp(2).toFloat()
            addView(Button(context).apply {
                text = "Take me to the AW app"
                setOnClickListener { openOfficialAquaWiz() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        secondRow.addView(
            awButtonCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = dp(6)
            }
        )
        root.addView(secondRow, full().apply { bottomMargin = dp(12) })

        val dosingRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        dosingRow.addView(
            smallCard(
                title = "⚠ KH Dosing",
                valueView = latestDoseValue,
                subtitle = "Latest graph dose"
            ).apply {
                background = rounded(Color.rgb(255, 248, 190), 22f, strokeColor = Color.rgb(255, 146, 50))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
        )
        dosingRow.addView(
            smallCard(
                title = "Today's Dosing",
                valueView = todayDoseValue,
                subtitle = "From locally cached measurements"
            ),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(6)
            }
        )
        root.addView(dosingRow, full().apply { bottomMargin = dp(14) })
    }

    private fun buildChart() {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.WHITE, 22f)
            elevation = dp(2).toFloat()
        }

        val legendRow1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(legendKh, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(legendPh, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val legendRow2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(legendPhOpenAir, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(legendDelta, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addView(legendRow1)
        card.addView(legendRow2)
        card.addView(chartTimestamp, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
        })

        card.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330)))

        val ranges = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        Range.values().forEach { range ->
            val button = Button(context).apply {
                text = range.label
                textSize = 13f
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { selectRange(range) }
            }
            rangeButtons[range] = button
            ranges.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        card.addView(ranges)

        root.addView(card, full())

        root.addView(label(
            "💡 Drag across the chart to inspect a measurement. The Y axis is shared by KH, pH, and pH(O). " +
                "Phase 1 uses KH values in the selected range with ±0.5 dKH padding; AquaWiz KH alert limits can plug into the same chart scale once mapped.",
            12f,
            Color.rgb(80, 80, 80)
        ).apply {
            setPadding(dp(8), dp(12), dp(8), dp(8))
        })
    }

    private fun selectRange(range: Range) {
        selectedRange = range
        fetchedRange = null
        refreshFromLocal()
        refreshFromApi(range)
    }

    private fun refreshFromApi(range: Range) {
        val session = store.session() ?: return
        val serial = store.selectedDevice()?.takeIf { it.isNotBlank() } ?: return
        val since = Instant.now().minusSeconds(range.seconds)

        thread(name = "AquaWizHomeGraph") {
            try {
                val api = AquaWizApi(store.baseUrl())
                val measurements = api.graphMeasurements(session, serial, since)
                if (measurements.isNotEmpty()) {
                    store.saveMeasurements(serial, measurements)
                    store.appendActivity("Home " + range.label + " chart refreshed from AquaWiz: " + measurements.size + " points")
                } else {
                    store.appendActivity("Home " + range.label + " chart returned no graph points")
                }
                fetchedRange = range
                post { refreshFromLocal() }
            } catch (e: AquaWizApi.ApiException) {
                if (e.status == 401 || e.status == 403) {
                    store.setAuthPaused(true)
                    store.clearNextPollEpochMs()
                }
                store.appendActivity("Home chart API error: " + (e.message ?: "unknown"))
                post {
                    Toast.makeText(context, "Chart refresh failed; showing local history", Toast.LENGTH_SHORT).show()
                    refreshFromLocal()
                }
            } catch (e: Exception) {
                store.appendActivity("Home chart refresh error: " + (e.message ?: e.javaClass.simpleName))
                post { refreshFromLocal() }
            }
        }
    }

    private fun openOfficialAquaWiz() {
        val pm = context.packageManager
        val direct = pm.getLaunchIntentForPackage("com.kuannnn.aquawiz")
        val fallback = if (direct == null) {
            val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcherQuery, 0)
                .firstOrNull {
                    it.activityInfo.packageName != context.packageName &&
                        it.loadLabel(pm).toString().contains("AquaWiz", ignoreCase = true)
                }
                ?.activityInfo
                ?.let {
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(it.packageName, it.name)
                }
        } else null

        val launch = direct ?: fallback
        if (launch == null) {
            Toast.makeText(context, "Official AquaWiz app is not installed", Toast.LENGTH_SHORT).show()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(launch)
    }

    private fun smallCard(title: String, valueView: TextView, subtitle: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.WHITE, 22f)
            elevation = dp(2).toFloat()
            addView(label(title, 14f, Color.rgb(105, 105, 105)))
            addView(valueView)
            addView(label(subtitle, 11f, Color.rgb(125, 125, 125)))
        }

    private fun horizontalCard() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(context).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun rounded(
        color: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        if (strokeColor != null) setStroke(dp(2), strokeColor)
    }

    private fun full() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun updateRangeButtons() {
        rangeButtons.forEach { (range, button) ->
            button.isEnabled = range != selectedRange
            button.alpha = if (range == selectedRange) 1.0f else 0.82f
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
