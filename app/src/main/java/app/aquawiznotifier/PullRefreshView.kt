package app.aquawiznotifier

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class PullRefreshView(context: Context, canScrollUp: () -> Boolean, refresh: () -> Unit) : SwipeRefreshLayout(context) {
    init {
        setColorSchemeColors(AwUi.BLUE)
        setOnChildScrollUpCallback { _, _ -> canScrollUp() }
        setOnRefreshListener { refresh() }
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.action_refresh_measurements, "Refresh measurements"))
            }
            override fun performAccessibilityAction(host: View, action: Int, arguments: Bundle?): Boolean {
                if (action == R.id.action_refresh_measurements) {
                    if (!isRefreshing) { isRefreshing = true; refresh() }
                    return true
                }
                return super.performAccessibilityAction(host, action, arguments)
            }
        }
    }
}
