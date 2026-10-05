package app.aquawiznotifier

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.math.BigDecimal

enum class KhSettingsSection { TARGET, DOSING }

class KhSettingsDialog(context: Context, private val section: KhSettingsSection, private val serial: String,
    private val load: ((Result<DeviceSummary>) -> Unit) -> Unit,
    private val submit: (KhTargetSettings?, KhDosingSettings?, (Result<Unit>) -> Unit) -> Unit) : BottomEditorDialog(context) {
    private val inputs = linkedMapOf<String, EditText>()
    private val selectors = linkedMapOf<String, Button>()
    private val choices = mutableMapOf<String, Int>()
    private var original: List<String>? = null
    private var busy = true
    private val status = AwUi.label(context, "Loading current settings…", 12f).apply { tag = "settings_status" }
    private val apply = AwUi.button(context, "Apply", true).apply {
        tag = "settings_apply"; background = AwUi.surface(context, AwUi.BLUE, 28, false)
    }
    private val retry = AwUi.button(context, "Retry loading").apply { visibility = View.GONE; setOnClickListener { loadCurrent() } }
    private val cancel = AwUi.button(context, "Cancel").apply { tag = "settings_cancel"; setOnClickListener { dismiss() } }

    init {
        if (section == KhSettingsSection.TARGET) {
            decimal("target", "Target KH", "dKH", "If lower, then dose KH rising solution", R.drawable.ic_kh_target)
            decimal("deviation", "Notify If Out of Spec Through Email", "dKH", null, R.drawable.ic_kh_email)
            selector(body, "interval", "Measurement Interval", 1..6, true, "Controller measurement interval in hours. An already scheduled measurement may still run before the new setting takes effect.")
            body.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                for ((key, title) in listOf("from" to "Sleep Time Period From", "to" to "Sleep Time Period To")) {
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        selector(this, key, title, 0..23, false, null)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (key == "from") rightMargin = dp(6) else leftMargin = dp(6)
                    })
                }
            }, full())
            body.addView(AwUi.label(context, "Sleep hours use the KHA controller's clock. Changing your phone's timezone does not change these hours.", 12f), full().apply { topMargin = dp(8) })
        } else {
            decimal("remaining", "KH Dosing Solution Remaining", "mL", "It is reduced after dosing", R.drawable.ic_kh_dose)
            decimal("amount", "Amount to Increase 1 dKH", "mL", "Tank size and concentration of KH rising solution determine this value", R.drawable.ic_kh_drop)
            decimal("maximum", "Maximum Dosing Per Hour", "mL", "Enter 0 to stop dosing", R.drawable.ic_kh_drop)
            decimal("threshold", "Email Notification Threshold", "mL", "Send email when KH rising solution is less than this value", R.drawable.ic_kh_email)
        }
        body.addView(status, full().apply { topMargin = dp(12) })
        body.addView(retry, full().apply { topMargin = dp(8) })
        body.addView(apply, full().apply { height = dp(52); topMargin = dp(16) })
        body.addView(AwUi.label(context, "New settings take effect at the next measurement. Select [SYNC] on the KHA LCD to update immediately.", 13f).apply {
            background = AwUi.surface(context, 0xFFF5F7FA.toInt(), 12)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }, full().apply { topMargin = dp(12) })
        body.addView(cancel, full().apply { height = dp(44); topMargin = dp(8) })
        apply.setOnClickListener {
            if (!apply.isEnabled || busy) return@setOnClickListener
            val target = if (section == KhSettingsSection.TARGET) targetValue() else null
            val dosing = if (section == KhSettingsSection.DOSING) dosingValue() else null
            busy = true; updateControls(); status.text = "Saving settings…"
            setCancelable(false); setCanceledOnTouchOutside(false); cancel.isEnabled = false
            submit(target, dosing) { result ->
                if (!isShowing) return@submit
                busy = false; setCancelable(true); setCanceledOnTouchOutside(true); cancel.isEnabled = true
                result.fold(onSuccess = { dismiss() }, onFailure = {
                    updateControls(); status.text = it.message ?: "Unable to save settings. Try again."
                })
            }
        }
        updateControls()
        setOnShowListener { loadCurrent() }
    }

    private fun decimal(key: String, title: String, unit: String, hint: String?, icon: Int) {
        body.addView(AwUi.label(context, title, 17f, true), full().apply { topMargin = dp(16) })
        val input = EditText(context).apply {
            tag = "settings_$key"; contentDescription = "$title in $unit"
            inputType = InputType.TYPE_CLASS_NUMBER or if (section == KhSettingsSection.TARGET) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
            setSingleLine(true); gravity = Gravity.END; textSize = 22f
            typeface = resources.getFont(R.font.aw_extrabold); setTextColor(AwUi.INK); background = null
            this.hint = "—"; setPadding(0, 0, 0, 0)
        }
        inputs[key] = input
        body.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = AwUi.surface(context, 0xFFF7F8FA.toInt(), 28)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(ImageView(context).apply { setImageResource(icon) }, LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(input, LinearLayout.LayoutParams(0, dp(42), 1f))
            addView(AwUi.label(context, unit, 16f).apply { setTextColor(android.graphics.Color.GRAY) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(12) })
        }, full().apply { topMargin = dp(8) })
        hint?.let { body.addView(AwUi.label(context, it, 12f).apply { setLineSpacing(dp(2).toFloat(), 1f) }, full().apply { topMargin = dp(7) }) }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateControls() }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    private fun selector(parent: LinearLayout, key: String, title: String, range: IntRange, interval: Boolean, hint: String?) {
        parent.addView(AwUi.label(context, title, 13f), full().apply { topMargin = dp(16) })
        val button = AwUi.button(context, "—").apply {
            tag = "settings_$key"; contentDescription = title
            background = AwUi.surface(context, 0xFFF7F8FA.toInt(), 28)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_kh_clock, 0, 0, 0)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setOnClickListener {
                if (busy || original == null) return@setOnClickListener
                val items = range.map { if (interval) "$it ${if (it == 1) "hour" else "hours"}" else "%02d:00".format(it) }.toTypedArray()
                AlertDialog.Builder(context).setTitle(title).setSingleChoiceItems(items, (choices[key] ?: range.first) - range.first) { dialog, index ->
                    choices[key] = range.first + index; updateSelector(key); updateControls(); dialog.dismiss()
                }.setNegativeButton("Cancel", null).show()
            }
        }
        selectors[key] = button
        parent.addView(button, full().apply { height = dp(54); topMargin = dp(8) })
        hint?.let { parent.addView(AwUi.label(context, it, 12f), full().apply { topMargin = dp(7) }) }
    }
    private fun updateSelector(key: String) {
        val value = choices[key] ?: return
        selectors[key]?.text = if (key == "interval") "$value ${if (value == 1) "hour" else "hours"}" else "%02d:00".format(value)
    }
    private fun number(key: String) = KhCalibration.parse(inputs.getValue(key).text.toString()) ?: error("Enter a valid value in every field")
    private fun targetValue() = KhTargetSettings(number("target"), number("deviation"), choices.getValue("interval"), choices.getValue("from"), choices.getValue("to")).also(KhDeviceSettings::validate)
    private fun dosingValue() = KhDosingSettings(number("remaining"), number("amount"), number("maximum"), number("threshold")).also { KhDeviceSettings.validate(it, serial) }
    private fun snapshot(): List<String> = if (section == KhSettingsSection.TARGET) targetValue().let {
        listOf(it.target.stripTrailingZeros().toPlainString(), it.deviation.stripTrailingZeros().toPlainString(), it.interval.toString(), it.sleepFrom.toString(), it.sleepTo.toString())
    } else dosingValue().let { listOf(it.remaining, it.amountPerDkh, it.maxPerHour, it.emailThreshold).map { n -> n.stripTrailingZeros().toPlainString() } }
    private fun updateControls() {
        inputs.values.forEach { it.isEnabled = !busy && original != null }
        selectors.values.forEach { it.isEnabled = !busy && original != null }
        val values = runCatching { snapshot() }
        apply.isEnabled = !busy && original != null && values.getOrNull()?.let { it != original } == true
        apply.alpha = if (apply.isEnabled) 1f else 0.45f
        if (!busy && original != null) status.text = values.exceptionOrNull()?.message ?: "Changes are saved when you tap Apply"
    }
    private fun loadCurrent() {
        busy = true; original = null; retry.visibility = View.GONE; updateControls(); status.text = "Loading current settings…"
        load { result ->
            if (!isShowing) return@load
            val populated = result.mapCatching { summary ->
                fun put(key: String, value: BigDecimal) { inputs.getValue(key).setText(value.stripTrailingZeros().toPlainString()) }
                if (section == KhSettingsSection.TARGET) KhDeviceSettings.target(summary).let {
                    put("target", it.target); put("deviation", it.deviation)
                    choices["interval"] = it.interval; choices["from"] = it.sleepFrom; choices["to"] = it.sleepTo
                    selectors.keys.forEach(::updateSelector)
                } else KhDeviceSettings.dosing(summary, serial).let {
                    put("remaining", it.remaining); put("amount", it.amountPerDkh); put("maximum", it.maxPerHour); put("threshold", it.emailThreshold)
                }
                original = snapshot()
            }
            busy = false; updateControls()
            populated.exceptionOrNull()?.let { original = null; updateControls(); status.text = it.message ?: "Unable to load settings"; retry.visibility = View.VISIBLE }
        }
    }
    private fun full() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
