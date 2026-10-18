package app.aquawiznotifier

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController

object SystemNavigation {
    @Suppress("DEPRECATION")
    fun configure(window: Window) {
        window.navigationBarColor = Color.BLACK
        window.statusBarColor = if (AppAppearance.dark) AwUi.BACKGROUND else Color.WHITE
        window.decorView.systemUiVisibility = if (AppAppearance.dark)
            window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        else window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.setSystemBarsAppearance(
                if (AppAppearance.dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS or WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        }
    }
}
