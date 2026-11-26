package com.trackcats.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
        setContent { TrackCatsTheme { TrackCatsRoot() } }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackCatsRoot(vm: AppVm = viewModel()) {
    val user by vm.user.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("rolls") }
    /** A full-screen page shown over the tabs: wizard, resync, history, glossary, tutorial. */
    var page by rememberSaveable { mutableStateOf(if (user.tutorialDone) "" else "tutorial") }
    var resyncEvent by rememberSaveable { mutableStateOf<String?>(null) }

    // The date can change while the app sits in the background.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.today = LocalDate.now()
        onPauseOrDispose { }
    }

    fun closePage() {
        if (page == "tutorial") vm.update { it.copy(tutorialDone = true) }
        page = ""
    }
    BackHandler(enabled = page.isNotEmpty() || tab != "rolls") {
        if (page.isNotEmpty()) closePage() else tab = "rolls"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("TrackCats", fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        bottomBar = {
            if (page.isEmpty()) NavigationBar {
                for (t in tabs) NavigationBarItem(
                    selected = tab == t.id, onClick = { tab = t.id },
                    icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                "tutorial" -> TutorialScreen(onDone = { closePage() })
                "wizard" -> SeedWizard(vm, onDone = { page = ""; tab = "rolls" })
                "resync" -> ResyncScreen(vm, resyncEvent, onDone = { page = ""; tab = "rolls" }, onFullWizard = { page = "wizard" })
                "history" -> HistoryScreen(vm, onDone = { page = "" })
                "glossary" -> GlossaryScreen(onDone = { page = "" })
                else -> when (tab) {
                    "banners" -> BannersScreen(vm, onOpenBanner = { e -> vm.update { it.copy(selectedEvent = e.k) }; tab = "rolls" })
                    "plan" -> PlanScreen(vm)
                    "cats" -> CatsScreen(vm)
                    "more" -> MoreScreen(
                        vm,
                        onFindSeed = { page = "wizard" },
                        onResync = { resyncEvent = null; page = "resync" },
                        onHistory = { page = "history" },
                        onGlossary = { page = "glossary" },
                        onTutorial = { page = "tutorial" },
                    )
                    else -> HomeScreen(vm, onFindSeed = { page = "wizard" }, onMismatch = { e -> resyncEvent = e.k; page = "resync" })
                }
            }
        }
    }
}
