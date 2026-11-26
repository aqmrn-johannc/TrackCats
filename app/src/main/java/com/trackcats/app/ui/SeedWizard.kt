package com.trackcats.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackcats.app.core.EventInfo
import com.trackcats.app.core.Resync
import com.trackcats.app.core.Roller
import com.trackcats.app.core.SeedFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Guided seed search: pick the banner, tap the cats you rolled, wait, confirm. */
@Composable
fun SeedWizard(vm: AppVm, onDone: () -> Unit) {
    val game by vm.game.collectAsStateWithLifecycle()
    val today = vm.today
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var eventKey by rememberSaveable { mutableStateOf<String?>(null) }
    var ids by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var seeds by remember { mutableStateOf<List<Int>?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var finder by remember { mutableStateOf<SeedFinder?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val event = game.event(eventKey)
    val pool = event?.let { game.pool(it) }

    DisposableEffect(Unit) { onDispose { finder?.cancelled = true } }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty() && pool != null) scope.launch {
            note = "Reading screenshots…"
            val found = runCatching { ScreenshotImport.read(context, uris, pool, game) }.getOrDefault(emptyList())
            ids = ids + found
            note = if (found.isEmpty()) "No cat names from this banner were found in those screenshots. Add the cats by tapping them instead."
            else "Added ${plural(found.size, "cat")} from ${plural(uris.size, "screenshot")}. Check the order before searching."
        }
    }

    fun startSearch() {
        val p = pool ?: return
        val f = SeedFinder(p, ids.toIntArray())
        val problem = f.problem()
        if (problem != null) { note = problem; return }
        finder = f
        seeds = null
        progress = 0f
        step = 2
        scope.launch {
            val poll = launch { while (isActive) { progress = f.progress; delay(150) } }
            val result = withContext(Dispatchers.Default) { f.find() }
            poll.cancel()
            if (!f.cancelled) { seeds = result.seeds; step = 3 }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Find my seed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Step ${step.coerceAtMost(2) + 1} of 3", color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (step) {
            0 -> {
                Text("Which banner did you roll on?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("All your rolls must come from the same Rare Capsule banner. If you haven't rolled recently, do 10 rolls now on any banner below and note each cat.")
                for ((label, events) in listOf("Open now" to game.ticketBanners(today), "Ended recently" to game.recentBanners(today))) {
                    if (events.isEmpty()) continue
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    for (e in events) BannerChoice(e, vm, onPick = { eventKey = e.k; ids = emptyList(); note = null; step = 1 })
                }
                TextButton(onClick = onDone) { Text("Cancel") }
            }
            1 -> if (pool != null) {
                Text(bannerTitle(pool.event), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SectionCard {
                    Text("Add your most recent rolls on this banner, oldest first. ${SeedFinder.RECOMMENDED_ROLLS} rolls give a reliable answer; ${SeedFinder.MIN_ROLLS} is the minimum.")
                    Text("Leave out the guaranteed uber at the end of a guaranteed 11-draw, and don't skip any roll in between.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                note?.let { SectionCard(highlight = true) { Text(it) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startSearch() }, enabled = ids.size >= SeedFinder.MIN_ROLLS, modifier = Modifier.weight(1f)) {
                        Text(if (ids.size >= SeedFinder.MIN_ROLLS) "Find my seed" else "${ids.size} of ${SeedFinder.MIN_ROLLS} added")
                    }
                    OutlinedButton(onClick = { step = 0 }) { Text("Back") }
                }
                OutlinedButton(
                    onClick = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import from screenshots (beta)") }
                Text("Screenshot import reads the cat's name from the screen shown after each roll, one screenshot per cat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RollSequenceEditor(game, pool, ids, { ids = it; note = null })
            }
            2 -> {
                Text("Searching…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Checking every possible seed against your rolls. This usually takes under a minute.")
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("${(progress * 100).toInt()}%")
                OutlinedButton(onClick = { finder?.cancelled = true; step = 1 }) { Text("Cancel") }
            }
            else -> {
                val found = seeds.orEmpty()
                when {
                    found.size == 1 && pool != null -> {
                        val seed = found[0]
                        val next = remember(seed) { Roller.rollOne(pool, seed, ids.last()) }
                        SectionCard(title = "Found your seed", highlight = true) {
                            Text("Your next roll on ${bannerTitle(pool.event)} should be:")
                            Text(game.name(next.catId), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Roll once to check. If it matches, tap \"I rolled\" and confirm, and your seed is locked in.")
                        }
                        Button(onClick = { vm.setSeed(seed, ids.last(), event, "setup"); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Save and show my rolls") }
                    }
                    found.size > 1 -> {
                        SectionCard(title = "${found.size} seeds fit those rolls", highlight = true) {
                            Text("That is too few rolls to tell them apart. Go back and add more rolls (older ones at the start, or newer ones at the end).")
                        }
                        Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Add more rolls") }
                    }
                    else -> {
                        SectionCard(title = "No seed matches", highlight = true) {
                            Text("Something in the list doesn't line up. The usual causes:")
                            Text("• A cat is missing, or two are in the wrong order.")
                            Text("• The rolls were on a different banner than the one chosen.")
                            Text("• The guaranteed uber from an 11-draw is in the list.")
                            Text("• A Platinum or Legend Ticket, or a roll on another banner, happened in between.")
                        }
                        Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Check my rolls") }
                    }
                }
                TextButton(onClick = onDone) { Text("Close") }
            }
        }
    }
}

@Composable
private fun BannerChoice(event: EventInfo, vm: AppVm, onPick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onPick).padding(vertical = 10.dp, horizontal = 8.dp)) {
        Text(bannerTitle(event), fontWeight = FontWeight.SemiBold)
        Text(
            listOfNotNull(bannerWhen(event, vm.today), bannerPerk(event)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "My rolls stopped matching": enter what you actually got and TrackCats finds where you are now. */
@Composable
fun ResyncScreen(vm: AppVm, startEventKey: String?, onDone: () -> Unit, onFullWizard: () -> Unit) {
    val game by vm.game.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val today = vm.today
    var eventKey by rememberSaveable { mutableStateOf(startEventKey ?: selectedBanner(game, user, today)?.k) }
    var ids by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var result by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val event = game.event(eventKey)
    val seed = user.seedInt
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("My rolls stopped matching", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("This happens when a roll was made without telling TrackCats. Add the last few cats you really got, oldest first, and it will find your place again. Three or more works best.")
        if (event == null || seed == null) {
            SectionCard(highlight = true) { Text("There is no saved seed yet. Use the guided seed search instead.") }
            Button(onClick = onFullWizard) { Text("Find my seed") }
            TextButton(onClick = onDone) { Text("Close") }
            return@Column
        }
        val pool = game.pool(event)
        BannerHeader(event, today) { picking = true }
        result?.let { SectionCard(highlight = true) { Text(it) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = ids.size >= 2, modifier = Modifier.weight(1f),
                onClick = {
                    val match = Resync.find(pool, seed, ids)
                    if (match != null) {
                        vm.setSeed(match.seedAfter, ids.last(), event, "resync", confirmed = true)
                        onDone()
                    } else {
                        result = "Couldn't find those cats anywhere ahead of your saved position. Check the order and the banner, or run the full seed search with your last 10 rolls."
                    }
                },
            ) { Text(if (ids.size >= 2) "Find my place" else "Add at least 2 cats") }
            OutlinedButton(onClick = onDone) { Text("Cancel") }
        }
        TextButton(onClick = onFullWizard) { Text("Start the full seed search instead") }
        RollSequenceEditor(game, pool, ids, { ids = it; result = null })
    }
    if (picking) {
        BannerPickerDialog("Which banner did you roll on?", bannerGroups(game, today), today, onPick = { eventKey = it.k; ids = emptyList(); picking = false }, onDismiss = { picking = false })
    }
}

/** For players who already know their seed from another tracker. */
@Composable
fun ManualSeedDialog(current: Long?, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current?.toString() ?: "") }
    val value = text.toLongOrNull()
    val valid = value != null && value in 1..0xFFFFFFFFL
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter seed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("If another seed tracker already gave you your seed, type it here. It is a number between 1 and 4294967295.")
                OutlinedTextField(
                    value = text, onValueChange = { v -> text = v.filter { it.isDigit() }.take(10) }, singleLine = true,
                    label = { Text("Seed") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = text.isNotEmpty() && !valid,
                )
            }
        },
        confirmButton = { Button(enabled = valid, onClick = { onSave(value!!.toInt()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
