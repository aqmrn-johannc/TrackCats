package com.trackcats.app.ui

/** One released version and what changed in it, newest first. Mirrors CHANGELOG.md in the repository. */
class Release(val code: Int, val name: String, val date: String, val changes: List<String>)

object Changelog {
    val releases = listOf(
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
