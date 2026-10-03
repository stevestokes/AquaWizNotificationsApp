package app.aquawiznotifier

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout

/** An editor anchored to the bottom, with an explicit save and a dismissible scrim. */
class MeasurementNoteDialog(context: Context, serial: String, measurement: Measurement, store: SecureStore,
    onSaved: () -> Unit) : BottomEditorDialog(context) {
    init {
        body.addView(AwUi.label(context, "Measurement note", 20f, true))
        body.addView(AwUi.label(context, AppDates.format(measurement.measuredAt) + " · %.2f dKH".format(measurement.kh), 12f),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8); bottomMargin = dp(16) })
        val input = EditText(context).apply {
            tag = "note_input"
            hint = "Add a note…"; contentDescription = "Measurement note"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.START
            minLines = 3; maxLines = 6
            textSize = 16f; typeface = context.resources.getFont(R.font.aw_regular)
            setTextColor(AwUi.INK); setPadding(dp(12), dp(12), dp(12), dp(12))
            background = AwUi.surface(context, 0xFFF5F7FA.toInt(), 12)
            setText(store.measurementNote(serial, measurement))
            setSelection(text.length)
        }
        body.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(AwUi.button(context, "Cancel").apply { tag = "note_cancel"; setOnClickListener { dismiss() } },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        actions.addView(AwUi.button(context, "Save note", true).apply {
            tag = "note_save"
            setOnClickListener { store.saveMeasurementNote(serial, measurement, input.text.toString()); onSaved(); dismiss() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        body.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
    }
}
