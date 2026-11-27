package com.trackcats.app.ui

/** One released version and what changed in it, newest first. Mirrors CHANGELOG.md in the repository. */
class Release(val code: Int, val name: String, val date: String, val changes: List<String>)

object Changelog {
    val releases = listOf(
        Release(
            5, "1.0.4", "2026-11-27",
            listOf(
                "Import from Cat Guide: on My cats, pick screenshots of the game's Cat Guide and TrackCats recognises the icons and ticks those cats for you. You review the list before anything changes.",
                "Ultra forms: cat details now show the fourth form of cats that have one.",
            ),
        ),
        Release(
            4, "1.0.3", "2026-11-27",
            listOf(
                "Banner calendar: on the Banners tab, switch to Calendar to see open and upcoming banners week by week, with guaranteed draws, your wishlist cats and how many ubers are new for you.",
                "Wishlist routes: the Plan tab now lists every wishlist cat with the cheapest way to reach it and whether you can afford it.",
                "Cat details: tap any cat to see its forms, where to get it, your cheapest route, and to mark it owned or wishlisted.",
                "Mark many cats at once: on My cats, narrow the list with search, rarity or a set, then mark or unmark everything shown.",
                "Set progress: My cats has a Sets view showing how many ubers of each set you own and which are missing.",
                "Light or dark: choose it under More, then Display, or keep following the phone.",
                "When confirming rolls you can now choose whether the cats are marked as owned.",
            ),
        ),
        Release(
            3, "1.0.2", "2026-11-27",
            listOf(
                "Rolls tab: the guaranteed-draw card now sits right under the banner, above your next rolls. Banners without one say so.",
                "Track table: it now lives in a pull-up tab at the bottom of the Rolls tab. Swipe it up (or tap it) to see the full table for your next 100 positions; swipe down to put it away.",
                "Theme colour: pick any colour for the app with a colour wheel under More, then Display. Buttons, titles and the background tint follow it.",
                "The TrackCats header bar is gone from every screen, leaving more room for content.",
            ),
        ),
        Release(
            2, "1.0.1", "2026-11-27",
            listOf(
                "Cat pictures: every cat now shows its icon in roll lists, plans, pickers, history and My cats.",
                "Banner art: banners show their artwork on the Rolls tab, the Banners tab and when choosing a banner.",
                "New look: a soft gradient background, every section in its own card, and a warmer orange accent in place of the flat yellow.",
                "Game-style lettering for titles, buttons and big results; normal lettering stays for explanations so they remain easy to read.",
                "Smooth motion: pages and tabs slide and fade, lists glide when they change, and cards open and close with animation.",
                "Faster: the app is now built in optimised mode, so scrolling and navigation are noticeably smoother.",
                "Tabs remember where you were: switching tabs and coming back keeps your scroll position.",
                "Pictures download once (about 3 MB) and then work offline. Without internet you see names and rarity colours, as before.",
                "This \"What's new\" page, also under More.",
            ),
        ),
        Release(
            1, "1.0.0", "2026-11-26",
            listOf(
                "First version: guided seed search, next-rolls list, banner comparison, planner, \"I rolled\" sync with history and undo, My cats and wishlist, alerts, advanced track table, backup.",
            ),
        ),
    )
    val latest get() = releases.first()
}
