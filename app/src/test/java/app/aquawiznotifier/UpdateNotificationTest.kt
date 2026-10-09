package app.aquawiznotifier

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdateNotificationTest {
    @Test fun manualCheckRepostsDismissedUpdateButAutomaticChecksDoNotRepeat() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("aquawiz_notifier", Context.MODE_PRIVATE).edit().clear().commit()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val release = ReleaseInfo("v99.0.0", "99.0.0", "https://github.com/stevestokes/AquaWizNotificationsApp/releases/tag/v99.0.0")
        UpdateChecker.notifyIfNewer(context, release, manual = false)
        assertEquals(1, manager.activeNotifications.size)
        manager.cancelAll()
        UpdateChecker.notifyIfNewer(context, release, manual = false)
        assertEquals(0, manager.activeNotifications.size)
        repeat(2) {
            UpdateChecker.notifyIfNewer(context, release, manual = true)
            assertEquals(1, manager.activeNotifications.size)
            manager.cancelAll()
        }
        assertEquals("99.0.0", SecureStore(context).lastUpdateNotifiedVersion())
        UpdateChecker.notifyIfNewer(context, ReleaseInfo("v${BuildConfig.VERSION_NAME}", BuildConfig.VERSION_NAME, release.htmlUrl), manual = true)
        assertEquals(0, manager.activeNotifications.size)
    }
}
