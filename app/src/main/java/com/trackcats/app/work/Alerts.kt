package com.trackcats.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trackcats.app.MainActivity
import com.trackcats.app.R
import com.trackcats.app.core.Game
import com.trackcats.app.core.Planner
import com.trackcats.app.core.Valuer
import com.trackcats.app.data.Store
import com.trackcats.app.data.UserState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object Alerts {
    private const val CHANNEL = "banners"
    private const val WORK = "daily-check"

    class Note(val id: Int, val title: String, val text: String)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Banner forecasts", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "New banners and wishlist cats that are close on your roll path"
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Works out what is worth telling the player about. Pure: returns the notes and the updated state,
     * so the same logic serves the background check, the "check now" button and the tests.
     */
    fun evaluate(game: Game, user: UserState, today: LocalDate): Pair<List<Note>, UserState> {
        val banners = game.ticketBanners(today) + game.upcomingBanners(today)
        val known = game.data.events.map { it.k }.toHashSet()
        val seed = user.seedInt
        if (seed == null) return emptyList<Note>() to user.copy(seenEvents = user.seenEvents.filter { it in known }.toSet() + banners.map { it.k })
        val v = Valuer(user.owned, user.wishlist)
        val firstRun = user.seenEvents.isEmpty()
        val seen = user.seenEvents.filter { it in known }.toMutableSet()
        val prefix = "${user.seed}|"
        val notified = user.notified.filter { it.startsWith(prefix) }.toMutableSet()
        val notes = ArrayList<Note>()
        val reach = user.tickets.coerceIn(5, 30)
        for (event in banners) {
            val pool = game.pool(event)
            val upcoming = event.isUpcoming(today)
            val score = Planner.score(pool, seed, user.lastCat, reach, v)
            if (seen.add(event.k) && !firstRun) {
                val first = score.rolls.take(5).joinToString(", ") { game.name(it.catId) }
                val good = score.good.size
                val headline = if (good > 0) "$good cat${if (good == 1) "" else "s"} worth rolling for in your next $reach rolls." else "Nothing special in your next $reach rolls."
                val whenText = if (upcoming) "Starts ${event.s}. " else ""
                notes.add(Note(event.k.hashCode(), "New banner: ${shortName(event.n)}", "$whenText$headline Next rolls: $first"))
            }
            score.rolls.forEachIndexed { i, roll ->
                if (v.isWanted(roll.catId)) {
                    val key = "$prefix${event.k}|${roll.catId}"
                    if (notified.add(key)) {
                        val place = if (i == 0) "your very next roll" else "${i + 1} rolls away"
                        val whenText = if (upcoming) " (starts ${event.s})" else ""
                        notes.add(Note(key.hashCode(), "Wishlist: ${game.name(roll.catId)} is $place", "On ${shortName(event.n)}$whenText."))
                    }
                }
            }
        }
        return notes to user.copy(seenEvents = seen, notified = notified)
    }

    /** Runs [evaluate], saves what was seen and posts the notifications. Returns how many were posted. */
    fun runCheck(context: Context, today: LocalDate = LocalDate.now()): Int {
        var notes: List<Note> = emptyList()
        Store.update(context) { user ->
            val (n, next) = evaluate(Store.game.value, user, today)
            notes = n
            next
        }
        if (!Store.user.value.alerts || !canNotify(context)) return 0
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val manager = NotificationManagerCompat.from(context)
        var posted = 0
        for (note in notes.take(8)) {
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(note.title)
                .setContentText(note.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(note.text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(note.id, n)
                posted++
            } catch (_: SecurityException) {
                return posted
            }
        }
        return posted
    }

    /** Banner names from the game are long marketing lines; keep the part before the first separator. */
    fun shortName(name: String): String {
        val cleaned = name.replace("★", " ").replace(Regex("\\s+"), " ").trim()
        val cut = cleaned.indexOfAny(charArrayOf('!', '(')).let { if (it in 12..60) it + 1 else -1 }
        val text = if (cut > 0) cleaned.substring(0, cut) else cleaned
        return if (text.length > 64) text.take(61).trimEnd() + "…" else text
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Store.init(applicationContext)
        val refreshed = runCatching { Store.refreshGame(applicationContext) }.isSuccess
        runCatching { Alerts.runCheck(applicationContext) }
        if (refreshed) Result.success() else Result.retry()
    }
}
