package com.trackcats.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trackcats.app.core.EventInfo
import com.trackcats.app.core.Game
import com.trackcats.app.core.Rng
import com.trackcats.app.core.Roll
import com.trackcats.app.core.Valuer
import com.trackcats.app.data.HistoryEntry
import com.trackcats.app.data.Store
import com.trackcats.app.data.UserState
import com.trackcats.app.work.Alerts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AppVm(app: Application) : AndroidViewModel(app) {
    val user: StateFlow<UserState> = Store.user
    val game: StateFlow<Game> = Store.game

    var today: LocalDate by mutableStateOf(LocalDate.now())
    var refreshing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    init {
        Store.init(app)
    }

    fun update(change: (UserState) -> UserState) = Store.update(getApplication(), change)

    fun valuer(u: UserState) = Valuer(u.owned, u.wishlist)

    /** Records a seed found by the wizard, typed in by hand, or recovered after a mismatch. */
    fun setSeed(seed: Int, lastCat: Int, event: EventInfo?, kind: String, confirmed: Boolean = false) = update { u ->
        u.copy(
            seed = Rng.toLong(seed), lastCat = lastCat, seedConfirmed = confirmed,
            selectedEvent = event?.k ?: u.selectedEvent,
            history = u.history + HistoryEntry(
                time = System.currentTimeMillis(), eventKey = event?.k ?: "", banner = event?.n ?: "",
                seedBefore = u.seed, lastBefore = u.lastCat, seedAfter = Rng.toLong(seed), kind = kind,
            ),
        )
    }

    /** The player rolled and got exactly what was predicted: advance the seed and mark the cats as owned. */
    fun applyRolls(event: EventInfo, rolls: List<Roll>, guaranteed: Boolean) {
        if (rolls.isEmpty()) return
        update { u ->
            val ids = rolls.map { it.catId }.filter { it > 0 }
            val newly = ids.filter { it !in u.owned }.distinct()
            u.copy(
                seed = Rng.toLong(rolls.last().seedAfter), lastCat = rolls.last().catId, seedConfirmed = true,
                owned = u.owned + newly,
                tickets = if (!guaranteed && event.p == null) (u.tickets - rolls.size).coerceAtLeast(0) else u.tickets,
                platTickets = if (event.p == "platinum") (u.platTickets - rolls.size).coerceAtLeast(0) else u.platTickets,
                legendTickets = if (event.p == "legend") (u.legendTickets - rolls.size).coerceAtLeast(0) else u.legendTickets,
                history = u.history + HistoryEntry(
                    time = System.currentTimeMillis(), eventKey = event.k, banner = event.n, cats = rolls.map { it.catId },
                    seedBefore = u.seed, lastBefore = u.lastCat, seedAfter = Rng.toLong(rolls.last().seedAfter),
                    kind = if (guaranteed) "guaranteed" else "rolled", newlyOwned = newly,
                ),
            )
        }
    }

    /** Takes back the most recent history entry: the seed and position go back to what they were. */
    fun undoLast() = update { u ->
        val last = u.history.lastOrNull() ?: return@update u
        u.copy(
            seed = last.seedBefore, lastCat = last.lastBefore,
            owned = u.owned - last.newlyOwned.toSet(),
            history = u.history.dropLast(1),
        )
    }

    fun refreshData(thenCheckAlerts: Boolean = false) {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Store.refreshGame(getApplication()) } }
            today = LocalDate.now()
            message = if (result.isSuccess) {
                val posted = if (thenCheckAlerts) withContext(Dispatchers.IO) { Alerts.runCheck(getApplication()) } else -1
                when {
                    posted > 0 -> "Banner data updated. $posted alert${if (posted == 1) "" else "s"} sent."
                    posted == 0 -> "Banner data updated. Nothing new to alert you about."
                    else -> "Banner data updated."
                }
            } else {
                "Couldn't update: ${result.exceptionOrNull()?.message ?: "no connection"}. Using saved data."
            }
            refreshing = false
        }
    }
}
