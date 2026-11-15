package com.shonkware.droidmodloader

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The native window frame is only visible before Compose draws. It must still
 * match the Material You surface for the current system mode so cold starts do
 * not flash the wrong colour.
 */
class NativeWindowThemeTest {
    @Test
    fun nativeWindowMatchesComposeBackgroundInSystemLightMode() {
        assertWindowTheme(Configuration.UI_MODE_NIGHT_NO, light = true)
    }

    @Test
    fun nativeWindowMatchesComposeBackgroundInSystemDarkMode() {
        assertWindowTheme(Configuration.UI_MODE_NIGHT_YES, light = false)
    }

    private fun assertWindowTheme(nightMode: Int, light: Boolean) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val context = ContextThemeWrapper(
            app.createConfigurationContext(configuration),
            R.style.Theme_DroidModLoader
        )
        val attrs = context.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.windowBackground,
                android.R.attr.colorBackground,
                android.R.attr.statusBarColor,
                android.R.attr.navigationBarColor,
                android.R.attr.windowLightStatusBar,
                android.R.attr.windowLightNavigationBar,
                android.R.attr.windowIsTranslucent
            )
        )
        try {
            val background = context.getColor(R.color.dml_window_background)
            assertEquals(background, (attrs.getDrawable(0) as ColorDrawable).color)
            for (index in 1..3) assertEquals(background, attrs.getColor(index, 0))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                assertEquals("Light status-bar icons flag", light, attrs.getBoolean(4, !light))
                assertEquals("Light navigation-bar icons flag", light, attrs.getBoolean(5, !light))
            }
            // Unspecified translucency is opaque, as in the platform window default.
            assertFalse("Translucent window", attrs.getBoolean(6, false))
        } finally {
            attrs.recycle()
        }
    }
}
