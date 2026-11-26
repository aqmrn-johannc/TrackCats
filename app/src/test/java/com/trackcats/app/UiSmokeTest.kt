package com.trackcats.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.trackcats.app.core.Roller
import com.trackcats.app.data.Store
import com.trackcats.app.data.UserState
import com.trackcats.app.work.Alerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Opens every screen with real bundled data to catch crashes; it does not judge how things look. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun everyScreenOpens() {
        // First launch shows the introduction.
        rule.onNodeWithText("Welcome to TrackCats").assertIsDisplayed()
        rule.onNodeWithText("Skip").performClick()
        rule.onNodeWithText("Let's find your seed").assertIsDisplayed()
        assertTrue(Store.user.value.tutorialDone)

        // Wizard: choose the first open banner, then the roll entry step appears.
        rule.onNodeWithText("Find my seed").performClick()
        rule.onNodeWithText("Which banner did you roll on?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performScrollTo().performClick()

        // With a seed saved, the roll list appears and "I rolled" advances it.
        val today = LocalDate.now()
        val game = Store.game.value
        Store.update(ctx) { UserState(seed = 123456789, tutorialDone = true, tickets = 20, catFood = 3000, platTickets = 1, legendTickets = 1) }
        rule.waitForIdle()
        rule.onNodeWithText("Your next rolls").assertIsDisplayed()
        val banner = com.trackcats.app.ui.selectedBanner(game, Store.user.value, today)
        if (banner != null) {
            val expected = Roller.rollOne(game.pool(banner), 123456789, -1)
            rule.onNodeWithText("I rolled").performClick()
            rule.onNodeWithText("What did you roll?").assertIsDisplayed()
            rule.onNodeWithText("Yes, that's what I got").performClick()
            rule.waitForIdle()
            assertEquals(expected.seedAfter.toLong() and 0xFFFFFFFFL, Store.user.value.seed)
            assertEquals(expected.catId, Store.user.value.lastCat)
            assertTrue(Store.user.value.seedConfirmed)
            assertEquals(19, Store.user.value.tickets)
        }

        // Advanced view adds the track table to the same screen.
        Store.update(ctx) { it.copy(advanced = true) }
        rule.waitForIdle()

        for (tab in listOf("Banners" to "Compare banners", "Plan" to "What you have", "My cats" to "Any rarity", "More" to "My seed")) {
            rule.onAllNodesWithText(tab.first).onFirst().performClick()
            rule.waitForIdle()
            rule.onNodeWithText(tab.second).assertIsDisplayed()
        }

        // Pages reached from More.
        rule.onNodeWithText("Open history").performScrollTo().performClick()
        rule.onNodeWithText("Undo latest entry").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(123456789L, Store.user.value.seed)
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("What do these words mean?").performScrollTo().performClick()
        rule.onNodeWithText("Seed").assertIsDisplayed()
        rule.onNodeWithText("Back").performScrollTo().performClick()
        rule.onNodeWithText("My rolls stopped matching").performScrollTo().performClick()
        rule.onNodeWithText("Add at least 2 cats").assertIsDisplayed()
    }

    @Test
    fun alertsOnlyFireForNewBannersAndWishlistCats() {
        val game = Store.game.value
        val today = LocalDate.parse(game.data.fetched)
        val banner = game.ticketBanners(today).first()
        val wanted = Roller.simulate(game.pool(banner), 42, -1, 3).last().catId
        val user = UserState(seed = 42, wishlist = setOf(wanted))
        val (first, afterFirst) = Alerts.evaluate(game, user, today)
        // First run: banners are recorded silently, but the wishlist cat three rolls away is reported.
        assertTrue(first.none { it.title.startsWith("New banner") })
        assertTrue(first.any { it.title.contains(game.name(wanted)) })
        // Second run with nothing changed stays quiet.
        val (second, _) = Alerts.evaluate(game, afterFirst, today)
        assertTrue(second.isEmpty())
        // A banner the app has not seen before produces one note.
        val (third, _) = Alerts.evaluate(game, afterFirst.copy(seenEvents = afterFirst.seenEvents - banner.k), today)
        assertEquals(1, third.count { it.title.startsWith("New banner") })
    }
}
