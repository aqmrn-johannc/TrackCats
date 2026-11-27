package com.trackcats.app.data

import android.content.Context
import com.trackcats.app.core.Game
import com.trackcats.app.core.GameData
import com.trackcats.app.core.YamlLite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

@Serializable
data class HistoryEntry(
    val time: Long,
    val eventKey: String = "",
    val banner: String = "",
    val cats: List<Int> = emptyList(),
    val seedBefore: Long? = null,
    val lastBefore: Int = -1,
    val seedAfter: Long = 0,
    /** rolled, guaranteed, setup, manual, resync */
    val kind: String = "rolled",
    val newlyOwned: List<Int> = emptyList(),
)

@Serializable
data class UserState(
    val seed: Long? = null,
    val lastCat: Int = -1,
    val seedConfirmed: Boolean = false,
    val owned: Set<Int> = emptySet(),
    val wishlist: Set<Int> = emptySet(),
    val history: List<HistoryEntry> = emptyList(),
    val tickets: Int = 0,
    val catFood: Int = 0,
    val platTickets: Int = 0,
    val legendTickets: Int = 0,
    val selectedEvent: String? = null,
    val advanced: Boolean = false,
    val alerts: Boolean = true,
    val tutorialDone: Boolean = false,
    val seenEvents: Set<String> = emptySet(),
    val notified: Set<String> = emptySet(),
    /** versionCode whose "What's new" page this player has already seen. */
    val lastSeenVersion: Int = 0,
) {
    val seedInt: Int? get() = seed?.toInt()
}

/** Single place that holds the saved state and game data for the whole process (screens and background check). */
object Store {
    const val DATA_URL = "https://gitlab.com/godfat/battle-cats-rolls/-/raw/master/build/bc-en.yaml"
    private const val USER_FILE = "user.json"
    private const val GAME_FILE = "gamedata.json"
    private const val MAX_HISTORY = 500

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val userFlow = MutableStateFlow(UserState())
    private val gameFlow = MutableStateFlow(Game(GameData("", emptyMap(), emptyMap(), emptyList())))
    private var loaded = false

    val user: StateFlow<UserState> get() = userFlow
    val game: StateFlow<Game> get() = gameFlow

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        val ctx = context.applicationContext
        userFlow.value = runCatching {
            json.decodeFromString<UserState>(File(ctx.filesDir, USER_FILE).readText())
        }.getOrDefault(UserState())
        val bundled = runCatching {
            json.decodeFromString<GameData>(ctx.assets.open(GAME_FILE).bufferedReader().use { it.readText() })
        }.getOrNull()
        val cached = runCatching {
            json.decodeFromString<GameData>(File(ctx.filesDir, GAME_FILE).readText())
        }.getOrNull()
        val best = listOfNotNull(cached, bundled).maxByOrNull { it.fetched }
        if (best != null) gameFlow.value = Game(best)
        loaded = true
    }

    @Synchronized
    fun update(context: Context, change: (UserState) -> UserState) {
        val next = change(userFlow.value).let { s ->
            if (s.history.size > MAX_HISTORY) s.copy(history = s.history.takeLast(MAX_HISTORY)) else s
        }
        if (next == userFlow.value) return
        userFlow.value = next
        writeAtomic(File(context.applicationContext.filesDir, USER_FILE), json.encodeToString(UserState.serializer(), next))
    }

    /** Downloads the latest banner and cat data. Throws when offline or when the file looks wrong. */
    fun refreshGame(context: Context, today: LocalDate = LocalDate.now()): Game {
        val conn = URL(DATA_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "TrackCats")
        val text = try {
            if (conn.responseCode != 200) error("Server answered ${conn.responseCode}")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val data = YamlLite.parse(text, today)
        if (data.events.size < 20 || data.cats.size < 100 || data.gacha.size < 10) error("Downloaded data looks incomplete")
        writeAtomic(File(context.applicationContext.filesDir, GAME_FILE), json.encodeToString(GameData.serializer(), data))
        val game = Game(data)
        gameFlow.value = game
        return game
    }

    fun exportUser(): String = json.encodeToString(UserState.serializer(), userFlow.value)

    /** Replaces everything with a backup. Returns false when the text is not a TrackCats backup. */
    fun importUser(context: Context, text: String): Boolean {
        val parsed = runCatching { json.decodeFromString<UserState>(text) }.getOrNull() ?: return false
        if (!text.contains("\"owned\"") && !text.contains("\"seed\"")) return false
        update(context) { parsed }
        return true
    }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}
