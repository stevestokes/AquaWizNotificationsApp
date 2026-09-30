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
    fun submit(items: List<Pair<String, Measurement>>) {
        if (readings == items) return
        readings = items
        notifyDataSetChanged()
    }
    fun reading(position: Int) = readings[position]
    override fun getCount() = readings.size
    override fun getItem(position: Int) = readings[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = convertView as? LinearLayout ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val density = resources.displayMetrics.density
            setPadding((4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt())
            addView(TextView(context).apply { textSize = 13f; setTextColor(Color.DKGRAY) })
            addView(TextView(context).apply { textSize = 16f; gravity = Gravity.START; setTextColor(Color.BLACK) })
        }
        val (device, m) = readings[position]
        (row.getChildAt(0) as TextView).text = AppDates.format(m.measuredAt)
        (row.getChildAt(1) as TextView).text = "%.2f dKH".format(m.kh) + "    pH " + (m.ph?.let { "%.2f".format(it) } ?: "—")
        row.contentDescription = AppDates.format(m.measuredAt) + ", " + device + ", " + (row.getChildAt(1) as TextView).text + ", tap for details"
        return row
    }
}
