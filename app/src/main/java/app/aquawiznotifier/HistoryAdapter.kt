package app.aquawiznotifier

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

class HistoryAdapter(private val context: Context, private val store: SecureStore) : BaseAdapter() {
    private var readings = emptyList<Pair<String, Measurement>>()
    private data class Entry(val reading: Pair<String, Measurement>? = null, val calibration: KhCalibrationEvent? = null) {
        val at get() = reading?.second?.measuredAt ?: calibration!!.recordedAt
    }
    private var entries = emptyList<Entry>()
    private var calibrations = emptyList<KhCalibrationEvent>()
    private var notes = emptyMap<String, String>()
    private data class Row(val date: TextView, val values: List<TextView>, val note: TextView)
    fun submit(items: List<Pair<String, Measurement>>) {
        val updatedNotes = items.associate { (serial, m) -> HistoryMerge.readingKey(serial, m) to store.measurementNote(serial, m) }
        val events = store.calibrationEvents()
        if (readings == items && notes == updatedNotes && calibrations == events) return
        calibrations = events
        entries = (items.map { Entry(reading = it) } + events.map { Entry(calibration = it) }).sortedByDescending { it.at }
        readings = items
        notes = updatedNotes
        notifyDataSetChanged()
    }
    override fun getCount() = entries.size
    override fun getItem(position: Int): Any = entries[position].reading ?: entries[position].calibration!!
    fun measurementAt(position: Int): Pair<String, Measurement>? = entries[position].reading
    override fun getViewTypeCount() = 2
    override fun getItemViewType(position: Int) = if (entries[position].calibration != null) 1 else 0
    override fun areAllItemsEnabled() = false
    override fun getItemId(position: Int) = position.toLong()
    override fun isEnabled(position: Int) = entries[position].reading != null
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        entries[position].calibration?.let { event ->
            return AwUi.label(context, AppDates.format(event.recordedAt) + "\n[" + event.serial + "] " + event.description() +
                "\nLocal record · applies at the next measurement or KHA SYNC", 13f, true).apply {
                tag = "calibration_event"
                setTextColor(AwUi.CALIBRATION); setPadding(dp(12), dp(14), dp(12), dp(14))
                setLineSpacing(dp(4).toFloat(), 1f)
                setBackgroundColor(if (position % 2 == 0) AwUi.SURFACE else AwUi.ALTERNATE_ROW)
                contentDescription = text
            }
        }
        val row = (convertView as? LinearLayout)?.takeIf { it.tag is Row } ?: createRow()
        val holder = row.tag as Row
        val (serial, m) = entries[position].reading!!
        val note = notes[HistoryMerge.readingKey(serial, m)].orEmpty()
        holder.note.text = note
        holder.note.visibility = if (note.isBlank()) View.GONE else View.VISIBLE
        holder.date.text = AppDates.format(m.measuredAt)
        val values = listOf("%.2f".format(m.kh), format(m.ph), format(m.phOpenAir),
            m.deltaPh?.let { "%+.2f".format(it) } ?: "—", format(m.doseMl))
        holder.values.zip(values).forEach { (view, value) -> view.text = value }
        row.setBackgroundColor(if (position % 2 == 0) AwUi.SURFACE else AwUi.ALTERNATE_ROW)
        row.contentDescription = holder.date.text.toString() + ", KH " + values[0] +
            " dKH, pH " + values[1] + ", pH open air " + values[2] + ", delta pH " + values[3] + ", dose " + values[4] + " mL" +
            if (note.isBlank()) ". Tap to add a note" else ". Note: $note. Tap to edit note"
        return row
    }
    private fun createRow() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        val date = AwUi.label(context, "", 12f, true).apply {
            setSingleLine(true)
            setAutoSizeTextTypeUniformWithConfiguration(10, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        addView(date, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)))
        val values = mutableListOf<TextView>()
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("KH · dKH", "pH", "pH(O)", "ΔpH", "Dose · mL").forEachIndexed { index, title ->
                val value = AwUi.label(context, "", 16f, true).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL; setSingleLine(true)
                    setAutoSizeTextTypeUniformWithConfiguration(10, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                    if (index == 0) setTextColor(ChartSeries.KH.color)
                }
                values += value
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(0, dp(9), dp(4), 0)
                    addView(AwUi.label(context, title, 10f).apply { setTextColor(AwUi.MUTED) })
                    addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(25)))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        })
        val note = AwUi.label(context, "", 13f).apply {
            tag = "measurement_note"
            setTextColor(AwUi.MUTED)
            visibility = View.GONE
        }
        addView(note, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(9) })
        tag = Row(date, values, note)
    }
    private fun format(value: Double?) = value?.let { "%.2f".format(it) } ?: "—"
    private fun dp(value: Int) = AwUi.dp(context, value)
}
