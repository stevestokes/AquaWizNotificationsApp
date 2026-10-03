package app.aquawiznotifier

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Compact iOS-style dock. It occupies layout space rather than covering content. */
class BottomNavigationView(context: Context, showStatus: Boolean, private val onSelect: (String) -> Unit) : FrameLayout(context) {
    private data class Item(val section: String, val view: LinearLayout, val icon: ImageView, val label: TextView)
    private val items = linkedMapOf<String, Item>()
    private val pill = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(2), dp(8), dp(2))
        background = AwUi.surface(context, Color.WHITE, radius = 36, border = false)
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
                    1, items.values.count { it.view.visibility == View.VISIBLE }, false,
                    AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE
                )
            }
        }
    }
    private var selectedSection = "home"
    private var bottomInset = 0
    private val systemBarPaint = Paint().apply { color = Color.BLACK }

    init {
        setPadding(dp(12), dp(8), dp(12), dp(8))
        addView(pill, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addItem("home", "Home", R.drawable.ic_nav_home)
        addItem("history", "History", R.drawable.ic_nav_history)
        addItem("status", "Status", R.drawable.ic_nav_status)
        addItem("config", "Config", R.drawable.ic_nav_config)
        setStatusVisible(showStatus)
        select("home")
    }

    private fun addItem(section: String, name: String, drawable: Int) {
        val icon = ImageView(context).apply {
            setImageResource(drawable)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val label = AwUi.label(context, name, 11f).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val item = LinearLayout(context).apply {
            tag = section
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = dp(56)
            setPadding(dp(3), dp(6), dp(3), dp(6))
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = name
            setOnClickListener { onSelect(section) }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Button"
                    info.isSelected = host.isSelected
                    val column = items.values.filter { it.view.visibility == View.VISIBLE }.indexOfFirst { it.section == section }
                    info.collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, column, 1, false, host.isSelected)
                }
            }
            addView(icon, LinearLayout.LayoutParams(dp(23), dp(23)))
            addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            })
        }
        pill.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(4), 0, dp(4), 0)
        })
        items[section] = Item(section, item, icon, label)
    }

    fun select(section: String) {
        selectedSection = section
        items.values.forEach { item ->
            val active = item.section == section
            item.view.isSelected = active
            val ink = if (active) Color.WHITE else 0xFF697584.toInt()
            item.icon.imageTintList = ColorStateList.valueOf(ink)
            item.label.setTextColor(ink)
            item.view.background = RippleDrawable(
                ColorStateList.valueOf(0x220089FF),
                GradientDrawable().apply {
                    cornerRadius = dp(28).toFloat()
                    setColor(if (active) 0xFF0089FF.toInt() else Color.TRANSPARENT)
                },
                GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(Color.WHITE) }
            )
        }
    }

    fun setStatusVisible(visible: Boolean) {
        items.getValue("status").view.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible && selectedSection == "status") {
            select("home")
            onSelect("home")
        }
        requestLayout()
    }

    fun setBottomInset(inset: Int) {
        if (bottomInset == inset) return
        bottomInset = inset
        setPadding(dp(12), dp(8), dp(12), dp(8) + inset)
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // Android 15/16 can make the navigation bar transparent; provide its black backing.
        if (bottomInset > 0) canvas.drawRect(0f, (height - bottomInset).toFloat(), width.toFloat(), height.toFloat(), systemBarPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = items.values.count { it.view.visibility == View.VISIBLE }
        val desired = dp((64 * resources.configuration.fontScale.coerceAtLeast(1f)).roundToInt()) * count + dp(16)
        val available = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) desired
            else (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        pill.layoutParams.width = desired.coerceAtMost(available)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun dp(value: Int) = AwUi.dp(context, value)
}
