package app.aquawiznotifier

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import java.math.BigDecimal

class KhCalibrationDialog(context: Context, initialValue: Double?,
    private val load: ((Result<Double>) -> Unit) -> Unit,
    private val submit: (BigDecimal, (Result<Unit>) -> Unit) -> Unit) : BottomEditorDialog(context) {
    private var loadedValue: BigDecimal? = null
    private var busy = false
    private val input = EditText(context).apply {
        tag = "calibration_input"; contentDescription = "True tank KH in dKH"
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setSingleLine(true); gravity = Gravity.END or Gravity.CENTER_VERTICAL
        textSize = 24f; typeface = context.resources.getFont(R.font.aw_extrabold)
        setTextColor(AwUi.INK); background = null
        setText(initialValue?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() } ?: "0")
    }
    private val status = AwUi.label(context, "Loading current calibration…", 12f).apply { tag = "calibration_status" }
    private val calibrate = AwUi.button(context, "Calibrate", true).apply { tag = "calibration_save" }
    private val retry = AwUi.button(context, "Retry loading").apply { visibility = View.GONE }
    private val cancel = AwUi.button(context, "Cancel").apply { tag = "calibration_cancel"; setOnClickListener { dismiss() } }

    init {
        body.addView(AwUi.label(context, "True Tank KH", 20f, true))
        body.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = AwUi.surface(context, 0xFFF5F7FA.toInt(), 28)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(ImageView(context).apply { setImageResource(R.drawable.ic_calibrate_kh); imageTintList = android.content.res.ColorStateList.valueOf(AwUi.INK) },
                LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(AwUi.label(context, "dKH", 18f).apply { setTextColor(Color.GRAY) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(12) })
        }, full().apply { topMargin = dp(18) })
        body.addView(AwUi.label(context, "Enter 0 to disable calibration", 12f), full().apply { topMargin = dp(8) })
        body.addView(status, full().apply { topMargin = dp(8) })
        body.addView(retry, full().apply { topMargin = dp(8) })
        body.addView(calibrate, full().apply { height = dp(48); topMargin = dp(18) })
        body.addView(AwUi.label(context,
            "Use a fresh tank-water sample and a test kit you trust to measure its actual KH. Enter that value above and tap Calibrate.\n\nSelect [SYNC] on the KHA LCD to apply it immediately. AquaWiz says calibration finishes in about 30 minutes. Keeping the reference water close to the tank’s KH helps reduce the correction.", 13f).apply {
                background = AwUi.surface(context, 0xFFF5F7FA.toInt(), 12)
                setPadding(dp(12), dp(12), dp(12), dp(12)); setLineSpacing(dp(3).toFloat(), 1f)
            }, full().apply { topMargin = dp(16) })
        body.addView(cancel, full().apply { height = dp(44); topMargin = dp(12) })
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateButton() }
            override fun afterTextChanged(s: Editable?) {}
        })
        calibrate.setOnClickListener {
            val value = KhCalibration.parse(input.text.toString()) ?: return@setOnClickListener
            val original = loadedValue ?: return@setOnClickListener
            if (busy || value.compareTo(original) == 0) return@setOnClickListener
            busy = true; updateButton()
            status.text = "Saving calibration…"
            // Keep the sheet open during the write; there is no automatic retry of a hardware setting.
            setCancelable(false); setCanceledOnTouchOutside(false)
            cancel.isEnabled = false
            submit(value) { result ->
                if (!isShowing) return@submit
                busy = false; setCancelable(true); setCanceledOnTouchOutside(true)
                cancel.isEnabled = true
                result.fold(onSuccess = { dismiss() }, onFailure = {
                    status.text = it.message ?: "Unable to save calibration. Try again."
                    updateButton()
                })
            }
        }
        retry.setOnClickListener { loadCurrent() }
        updateButton()
        setOnShowListener { loadCurrent() }
    }

    private fun loadCurrent() {
        busy = true; retry.visibility = View.GONE; input.isEnabled = false; updateButton()
        status.text = "Loading current calibration…"
        load { result ->
            if (!isShowing) return@load
            busy = false; input.isEnabled = true
            result.fold(onSuccess = {
                loadedValue = BigDecimal.valueOf(it)
                input.setText(loadedValue!!.stripTrailingZeros().toPlainString())
                status.text = "Enter the KH measured with your test kit"
            }, onFailure = {
                loadedValue = null; status.text = it.message ?: "Unable to load current calibration"
                retry.visibility = View.VISIBLE
            })
            updateButton()
        }
    }
    private fun updateButton() {
        val value = KhCalibration.parse(input.text.toString())
        val original = loadedValue
        calibrate.isEnabled = !busy && original != null && value != null && value.compareTo(original) != 0
        calibrate.alpha = if (calibrate.isEnabled) 1f else 0.45f
        input.isEnabled = !busy
    }
    private fun full() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
