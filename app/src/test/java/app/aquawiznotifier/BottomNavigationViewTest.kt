package app.aquawiznotifier

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.ImageView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BottomNavigationViewTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun item(nav: BottomNavigationView, section: String): View = nav.findViewWithTag(section)
    private fun measure(nav: BottomNavigationView, widthDp: Int = 360) {
        nav.measure(View.MeasureSpec.makeMeasureSpec(AwUi.dp(nav.context, widthDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        nav.layout(0, 0, nav.measuredWidth, nav.measuredHeight)
    }

    @Test fun defaultMenuHidesStatusAndKeepsSelectedItemAccessible() {
        val nav = BottomNavigationView(context, false) {}
        measure(nav)
        assertEquals(View.GONE, item(nav, "status").visibility)
        assertTrue(item(nav, "home").isSelected)
        assertTrue(item(nav, "home").isEnabled)
        assertTrue(item(nav, "home").isFocusable)
        assertEquals("Home", item(nav, "home").contentDescription)
    }

    @Test fun tappingHistorySelectsItAndClearsPreviousSelection() {
        var destination: String? = null
        lateinit var nav: BottomNavigationView
        nav = BottomNavigationView(context, false) { destination = it; nav.select(it) }
        item(nav, "history").performClick()
        assertEquals("history", destination)
        assertTrue(item(nav, "history").isSelected)
        assertFalse(item(nav, "home").isSelected)
        val icon = (item(nav, "history") as android.view.ViewGroup).getChildAt(0) as ImageView
        assertEquals(android.graphics.Color.WHITE, icon.imageTintList!!.defaultColor)
    }

    @Test fun hidingActiveStatusReturnsHomeAndCompactsTheDock() {
        var destination: String? = null
        lateinit var nav: BottomNavigationView
        nav = BottomNavigationView(context, true) { destination = it; nav.select(it) }
        measure(nav)
        val fourItemDockWidth = (item(nav, "home").parent as View).width
        item(nav, "status").performClick()
        nav.setStatusVisible(false)
        measure(nav)
        assertEquals("home", destination)
        assertTrue(item(nav, "home").isSelected)
        assertEquals(View.GONE, item(nav, "status").visibility)
        assertTrue((item(nav, "home").parent as View).width < fourItemDockWidth)
        nav.setStatusVisible(true)
        assertEquals(View.VISIBLE, item(nav, "status").visibility)
    }

    @Test fun largeTextOnNarrowScreenFitsDockAndBottomInsetAddsSpaceOnce() {
        val largeTextContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 1.8f })
        val nav = BottomNavigationView(largeTextContext, true) {}
        measure(nav, 320)
        val dock = item(nav, "home").parent as View
        assertTrue(dock.left >= nav.paddingLeft)
        assertTrue(dock.right <= nav.width - nav.paddingRight)
        for (section in listOf("home", "history", "status", "config")) {
            val view = item(nav, section)
            assertTrue(view.width > 0)
            assertTrue(view.right <= dock.width)
            assertTrue(view.isClickable)
        }
        val initialHeight = nav.measuredHeight
        val inset = AwUi.dp(largeTextContext, 24)
        nav.setBottomInset(inset)
        measure(nav, 320)
        assertEquals(initialHeight + inset, nav.measuredHeight)
        nav.setBottomInset(inset)
        measure(nav, 320)
        assertEquals(initialHeight + inset, nav.measuredHeight)
    }
}
