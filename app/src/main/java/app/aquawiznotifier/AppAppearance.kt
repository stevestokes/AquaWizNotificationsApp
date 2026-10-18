package app.aquawiznotifier

import android.content.Context
import android.content.res.Configuration

enum class AppearanceMode(val title: String) {
    LIGHT("Light · dark mode off"), DARK("Dark · always on"), SYSTEM("Follow Android system");
    companion object {
        fun from(value: String?): AppearanceMode = values().firstOrNull { it.name == value } ?: LIGHT
    }
}

object AppAppearance {
    @Volatile var dark: Boolean = false
        private set
    fun usesDark(mode: AppearanceMode, uiMode: Int): Boolean = when (mode) {
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
        AppearanceMode.SYSTEM -> (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    fun mode(context: Context): AppearanceMode = AppearanceMode.from(context.getSharedPreferences("aquawiz_notifier", Context.MODE_PRIVATE).getString("appearance_mode", null))
    fun setMode(context: Context, mode: AppearanceMode) {
        context.getSharedPreferences("aquawiz_notifier", Context.MODE_PRIVATE).edit().putString("appearance_mode", mode.name).apply()
    }
    fun apply(context: Context, uiMode: Int = context.resources.configuration.uiMode) {
        dark = usesDark(mode(context), uiMode)
        context.setTheme(if (dark) R.style.AppThemeDark else R.style.AppTheme)
    }
}
