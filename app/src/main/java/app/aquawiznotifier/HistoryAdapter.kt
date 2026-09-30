package app.aquawiznotifier

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

class HistoryAdapter(private val context: Context) : BaseAdapter() {
    private var readings = emptyList<Pair<String, Measurement>>()
    private data class Row(val date: TextView, val values: List<TextView>)
    fun submit(items: List<Pair<String, Measurement>>) {
        if (readings == items) return
        readings = items
        notifyDataSetChanged()
    }
    override fun getCount() = readings.size
    override fun getItem(position: Int) = readings[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun isEnabled(position: Int) = false
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = convertView as? LinearLayout ?: createRow()
        val holder = row.tag as Row
        val (_, m) = readings[position]
        holder.date.text = AppDates.format(m.measuredAt)
        val values = listOf("%.2f".format(m.kh), format(m.ph), format(m.phOpenAir),
            m.deltaPh?.let { "%+.2f".format(it) } ?: "—", format(m.doseMl))
        holder.values.zip(values).forEach { (view, value) -> view.text = value }
        row.setBackgroundColor(if (position % 2 == 0) Color.WHITE else 0xFFEEF4FB.toInt())
        row.contentDescription = holder.date.text.toString() + ", KH " + values[0] +
            " dKH, pH " + values[1] + ", pH open air " + values[2] + ", delta pH " + values[3] + ", dose " + values[4] + " mL"
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
                    addView(AwUi.label(context, title, 10f).apply { setTextColor(0xFF65758B.toInt()) })
                    addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(25)))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        })
        tag = Row(date, values)
    }
    private fun format(value: Double?) = value?.let { "%.2f".format(it) } ?: "—"
    private fun dp(value: Int) = AwUi.dp(context, value)
}
