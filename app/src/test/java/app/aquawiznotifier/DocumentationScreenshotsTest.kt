package app.aquawiznotifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import java.io.File
import java.time.Instant
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Opt-in documentation capture: real Android views, isolated sample data, no login or network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h850dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentationScreenshotsTest {
    @Test fun captureDocumentationViews() {
        assumeTrue(System.getenv("AQUAWIZ_SCREENSHOTS") == "true")
        val output = File("../docs/screenshots").apply { mkdirs() }
        for (mode in listOf(AppearanceMode.LIGHT, AppearanceMode.DARK)) {
            // Deliberately skip onCreate/onResume: documentation rendering must never log in,
            // schedule background work, or fetch any account/controller data.
            val activity = Robolectric.buildActivity(MainActivity::class.java).get()
            activity.getSharedPreferences("aquawiz_notifier", Context.MODE_PRIVATE).edit().clear().commit()
            AppAppearance.setMode(activity, mode)
            AppAppearance.apply(activity)
            val store = SecureStore(activity)
            val serial = "KH1-DEMO"
            store.setSelectedDevice(serial)
            store.setDosingContainerMl(serial, 500.0)
            store.saveDeviceSummary(serial, DeviceSummary(khTarget = 8.0, khDeviation = .2,
                phProbeStatus = 950.0, dosingRemainingMl = 325.0, dosingWarningMl = 50.0))
            val now = Instant.now().minusSeconds(60)
            for (i in 23 downTo 0) {
                store.saveMeasurement(serial, Measurement(kh = 8.0 + .09 * kotlin.math.sin(i * .8),
                    measuredAt = now.minusSeconds(i * 3600L), ph = 8.25 + .05 * kotlin.math.sin(i * .4),
                    phOpenAir = 8.3, deltaPh = -.05 + .05 * kotlin.math.sin(i * .4),
                    doseMl = if (i % 4 == 0) 2.5 else 1.0))
            }
            store.recordKhCalibration(serial, 8.25, now.minusSeconds(5400))
            fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
            fun invoke(name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
            field("store").set(activity, store)
            val root = invoke("buildUi") as View
            invoke("populate")
            val suffix = if (mode == AppearanceMode.DARK) "-dark" else ""
            val sections = mapOf("home" to "homeRefresh", "history" to "historySection", "config" to "configSection")
            for ((section, selectedField) in sections) {
                listOf("homeRefresh", "historySection", "configSection", "statusSection").forEach {
                    (field(it).get(activity) as View).visibility = if (it == selectedField) View.VISIBLE else View.GONE
                }
                (field("bottomNavigation").get(activity) as BottomNavigationView).select(section)
                val width = View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY)
                val height = View.MeasureSpec.makeMeasureSpec(850, View.MeasureSpec.EXACTLY)
                root.measure(width, height); root.layout(0, 0, 412, 850)
                val bitmap = Bitmap.createBitmap(412, 850, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                File(output, "$section$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
