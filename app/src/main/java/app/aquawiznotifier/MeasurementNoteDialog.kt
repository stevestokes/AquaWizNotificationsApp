package app.aquawiznotifier

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** An editor anchored to the bottom, with an explicit save and a dismissible scrim. */
class MeasurementNoteDialog(context: Context, serial: String, measurement: Measurement, store: SecureStore,
    onSaved: () -> Unit) : Dialog(context) {
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        body.addView(View(context).apply { background = AwUi.surface(context, 0xFFCCD4DF.toInt(), 2, false) },
            LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(18) })
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
        setContentView(ScrollView(context).apply { isFillViewport = false; addView(body) })
        setCanceledOnTouchOutside(true)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.35f)
            setGravity(Gravity.BOTTOM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setWindowAnimations(R.style.NoteSheetAnimation)
            SystemNavigation.configure(this)
        }
    }
    override fun onStart() {
        super.onStart()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    private fun dp(value: Int) = AwUi.dp(context, value)
}
