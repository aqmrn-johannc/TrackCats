package com.trackcats.app

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.trackcats.app.data.Store
import com.trackcats.app.data.UserState
import com.trackcats.app.ui.Changelog
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens to PNG files for a visual check on the PC. Skipped unless the
 * TRACKCATS_SHOTS environment variable names an output folder, because it downloads pictures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-420dpi")
class ScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val out = System.getenv("TRACKCATS_SHOTS")

    private fun settle(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            Thread.sleep(100)
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
        }
    }

    private fun shot(name: String, wait: Long = 4000) {
        settle(wait)
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun captureScreens() {
        assumeTrue(!out.isNullOrBlank())
        File(out!!).mkdirs()
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        shot("0-tutorial")
        rule.onNodeWithText("Skip").performClick()
        shot("1-noseed")
        val owned = Store.game.value.gachaCats.filterIndexed { i, _ -> i % 3 == 0 }.toSet()
        Store.update(ctx) {
            UserState(seed = 123456789, tutorialDone = true, seedConfirmed = true, tickets = 30, catFood = 3000, platTickets = 1,
                owned = owned, lastSeenVersion = Changelog.latest.code)
        }
        shot("2-rolls", 8000)
        for (tab in listOf("Banners", "Plan", "My cats", "More")) {
            rule.onAllNodesWithText(tab).onFirst().performClick()
            shot("3-" + tab.lowercase().replace(" ", ""), 8000)
        }
        // Freshly downloaded pictures fade in, and the test clock does not advance that fade.
        // Coming back to a screen shows them from memory, as they look on a phone.
        for (tab in listOf("Rolls", "Banners", "Plan", "My cats", "More")) {
            rule.onAllNodesWithText(tab).onFirst().performClick()
            shot("5-" + tab.lowercase().replace(" ", ""), 1500)
        }
        rule.onNodeWithText("Find my seed again").performScrollTo().performClick()
        shot("4-wizard")
    }
}
