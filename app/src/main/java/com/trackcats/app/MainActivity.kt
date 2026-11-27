package com.trackcats.app

import com.trackcats.app.ui.CatDetailDialog
import com.trackcats.app.ui.LocalCatDetails
import com.trackcats.app.ui.resolveDark
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.activity.SystemBarStyle
import com.trackcats.app.data.Store
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.trackcats.app.ui.AppBackground
import com.trackcats.app.ui.Art
import com.trackcats.app.ui.Changelog
import com.trackcats.app.ui.WhatsNewScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trackcats.app.ui.AppVm
import com.trackcats.app.ui.BannersScreen
import com.trackcats.app.ui.CatsScreen
import com.trackcats.app.ui.GlossaryScreen
import com.trackcats.app.ui.HistoryScreen
import com.trackcats.app.ui.HomeScreen
import com.trackcats.app.ui.MoreScreen
import com.trackcats.app.ui.PlanScreen
import com.trackcats.app.ui.ResyncScreen
import com.trackcats.app.ui.SeedWizard
import com.trackcats.app.ui.TrackCatsTheme
import com.trackcats.app.ui.TutorialScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val user by Store.user.collectAsStateWithLifecycle()
            // Status and navigation bar icons follow the app's own light/dark choice.
            val dark = resolveDark(user.darkMode)
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            TrackCatsTheme(user.themeColor, user.darkMode) { TrackCatsRoot() }
        }
    }
}

private class Tab(val id: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("rolls", "Rolls", Icons.AutoMirrored.Filled.List),
    Tab("banners", "Banners", Icons.Filled.DateRange),
    Tab("plan", "Plan", Icons.Filled.Star),
    Tab("cats", "My cats", Icons.Filled.Favorite),
    Tab("more", "More", Icons.Filled.Menu),
)

@Composable
fun TrackCatsRoot(vm: AppVm = viewModel()) {
    val user by vm.user.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf("rolls") }
    /** A full-screen page shown over the tabs: wizard, resync, history, glossary, tutorial, update notes. */
    var page by rememberSaveable { mutableStateOf(if (user.tutorialDone) "" else "tutorial") }
    var resyncEvent by rememberSaveable { mutableStateOf<String?>(null) }
    var detailCat by rememberSaveable { mutableStateOf(0) }

    // The date can change while the app sits in the background.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.today = LocalDate.now()
        onPauseOrDispose { }
    }
    // Download cat and banner pictures once, in the background.
    LaunchedEffect(game) { runCatching { Art.prefetch(context.applicationContext, context.imageLoader, game, vm.today) } }

    fun closePage() {
        // A new player has just read the introduction and does not need the update notes as well.
        if (page == "tutorial") vm.update { it.copy(tutorialDone = true, lastSeenVersion = maxOf(it.lastSeenVersion, Changelog.latest.code)) }
        page = ""
    }
    BackHandler(enabled = page.isNotEmpty() || tab != "rolls") {
        if (page.isNotEmpty()) closePage() else tab = "rolls"
    }

    val tabStates = rememberSaveableStateHolder()
    CompositionLocalProvider(LocalCatDetails provides { id -> if (id > 0) detailCat = id }) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (page.isEmpty()) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    for (t in tabs) NavigationBarItem(
                        selected = tab == t.id, onClick = { tab = t.id },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            },
        ) { padding ->
            // Tabs slide sideways in the direction of travel; full-screen pages rise over the tabs.
            AnimatedContent(
                targetState = if (page.isNotEmpty()) "page:$page" else "tab:$tab",
                modifier = Modifier.fillMaxSize().padding(padding),
                transitionSpec = {
                    val spec = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                    val fade = tween<Float>(220)
                    val from = tabs.indexOfFirst { "tab:${it.id}" == initialState }
                    val to = tabs.indexOfFirst { "tab:${it.id}" == targetState }
                    when {
                        from >= 0 && to >= 0 -> {
                            val dir = if (to > from) 1 else -1
                            (slideInHorizontally(spec) { dir * it / 4 } + fadeIn(fade)) togetherWith (slideOutHorizontally(spec) { -dir * it / 4 } + fadeOut(fade))
                        }
                        to < 0 -> (slideInVertically(spec) { it / 10 } + fadeIn(fade)) togetherWith fadeOut(fade)
                        else -> fadeIn(fade) togetherWith (slideOutVertically(spec) { it / 10 } + fadeOut(fade))
                    }
                },
                label = "screen",
            ) { target ->
                // Each tab keeps its own scroll position while you visit the others; pages always open fresh.
                val screen: @Composable () -> Unit = {
                    when (target) {
                        "page:tutorial" -> TutorialScreen(onDone = { closePage() })
                        "page:wizard" -> SeedWizard(vm, onDone = { page = ""; tab = "rolls" })
                        "page:resync" -> ResyncScreen(vm, resyncEvent, onDone = { page = ""; tab = "rolls" }, onFullWizard = { page = "wizard" })
                        "page:history" -> HistoryScreen(vm, onDone = { page = "" })
                        "page:glossary" -> GlossaryScreen(onDone = { page = "" })
                        "page:whatsnew" -> WhatsNewScreen(onDone = { page = "" })
                        "tab:banners" -> BannersScreen(vm, onOpenBanner = { e -> vm.update { it.copy(selectedEvent = e.k) }; tab = "rolls" })
                        "tab:plan" -> PlanScreen(vm)
                        "tab:cats" -> CatsScreen(vm)
                        "tab:more" -> MoreScreen(
                            vm,
                            onFindSeed = { page = "wizard" },
                            onResync = { resyncEvent = null; page = "resync" },
                            onHistory = { page = "history" },
                            onGlossary = { page = "glossary" },
                            onTutorial = { page = "tutorial" },
                            onWhatsNew = { page = "whatsnew" },
                        )
                        else -> HomeScreen(vm, onFindSeed = { page = "wizard" }, onMismatch = { e -> resyncEvent = e.k; page = "resync" })
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    if (target.startsWith("tab:")) tabStates.SaveableStateProvider(target, screen) else screen()
                }
            }
        }
    }

    if (detailCat > 0) CatDetailDialog(vm, detailCat, onDismiss = { detailCat = 0 })
    }

    // After an update, show what changed once.
    if (user.tutorialDone && page.isEmpty() && user.lastSeenVersion < Changelog.latest.code) {
        val seen = { vm.update { it.copy(lastSeenVersion = Changelog.latest.code) } }
        AlertDialog(
            onDismissRequest = seen,
            title = { Text("What's new in ${Changelog.latest.name}") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (change in Changelog.latest.changes) Text("• $change")
                }
            },
            confirmButton = { Button(onClick = seen) { Text("Got it") } },
        )
    }
}
