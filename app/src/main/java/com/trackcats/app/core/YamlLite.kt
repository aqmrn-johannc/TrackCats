package com.trackcats.app.core

import java.time.LocalDate

/**
 * Reads the community data file (bc-en.yaml from the battle-cats-rolls project) without a YAML library.
 * The file is machine-written with a fixed shape, so a small line parser is enough:
 *
 *   cats:    id -> name (list of form names), rarity
 *   gacha:   id -> cats (list of cat ids)
 *   events:  key -> start_on, end_on, name, id, rare, supa, uber, legend, guaranteed, step_up, platinum
 *
 * Mirrors tools/convert_data.py, which produces the snapshot bundled in the APK.
 */
object YamlLite {
    private const val KEEP_PAST_DAYS = 120L

    fun parse(text: String, today: LocalDate): GameData {
        val catNames = HashMap<Int, List<String>>()
        val catRarity = HashMap<Int, Int>()
        val gachaCats = HashMap<Int, List<Int>>()
        val rawEvents = ArrayList<Pair<String, HashMap<String, String>>>()

        var section = ""
        var key = ""
        var fields: HashMap<String, String>? = null
        for (line in text.lineSequence()) {
            if (line.isEmpty() || line.startsWith("---") || line.startsWith("#")) continue
            if (line[0] != ' ') {
                section = line.trimEnd().removeSuffix(":")
                continue
            }
            if (line.startsWith("  ") && line.length > 2 && line[2] != ' ') {
                key = unquote(line.trim().removeSuffix(":"))
                if (section == "events") {
                    fields = HashMap()
                    rawEvents.add(key to fields)
                }
                continue
            }
            if (!line.startsWith("    ") || line.length < 5 || line[4] == ' ') continue
            val colon = line.indexOf(':', 4)
            if (colon < 0) continue
            val field = line.substring(4, colon)
            val value = line.substring(colon + 1).trim()
            when (section) {
                "cats" -> {
                    val id = key.toIntOrNull() ?: continue
                    if (field == "name") catNames[id] = flowList(value)
                    if (field == "rarity") value.toIntOrNull()?.let { catRarity[id] = it }
                }
                "gacha" -> {
                    val id = key.toIntOrNull() ?: continue
                    if (field == "cats") gachaCats[id] = flowList(value).mapNotNull { it.toIntOrNull() }
                }
                "events" -> fields?.put(field, unquote(value))
            }
        }

        val cutoff = today.minusDays(KEEP_PAST_DAYS)
        val events = ArrayList<EventInfo>()
        for ((k, f) in rawEvents) {
            val end = f["end_on"] ?: continue
            val start = f["start_on"] ?: continue
            if (runCatching { LocalDate.parse(end).isBefore(cutoff) }.getOrDefault(true)) continue
            val id = f["id"]?.toIntOrNull() ?: continue
            events.add(
                EventInfo(
                    k = k, s = start, e = end, n = (f["name"] ?: "").trim(), id = id,
                    ra = f["rare"]?.toIntOrNull() ?: 0,
                    su = f["supa"]?.toIntOrNull() ?: 0,
                    ub = f["uber"]?.toIntOrNull() ?: 0,
                    le = f["legend"]?.toIntOrNull() ?: 0,
                    g = f["guaranteed"] == "true",
                    st = f["step_up"] == "true",
                    p = f["platinum"],
                )
            )
        }
        val gacha = LinkedHashMap<Int, List<Int>>()
        val cats = LinkedHashMap<Int, CatInfo>()
        for (gid in events.map { it.id }.toSortedSet()) {
            val ids = gachaCats[gid] ?: continue
            if (ids.isEmpty()) continue
            gacha[gid] = ids
        }
        for (cid in gacha.values.flatten().toSortedSet()) {
            val names = catNames[cid] ?: continue
            cats[cid] = CatInfo(names, catRarity[cid] ?: continue)
        }
        return GameData(today.toString(), cats, gacha, events)
    }

    /** Parses an inline list such as `[Kai, 'Kai, type μ', "A \"b\""]`. */
    fun flowList(value: String): List<String> {
        val s = value.trim()
        if (!s.startsWith("[") || !s.endsWith("]")) return emptyList()
        val out = ArrayList<String>()
        var i = 1
        val end = s.length - 1
        while (i < end) {
            while (i < end && (s[i] == ' ' || s[i] == ',')) i++
            if (i >= end) break
            val sb = StringBuilder()
            when (s[i]) {
                '"' -> {
                    i++
                    while (i < end && s[i] != '"') {
                        if (s[i] == '\\' && i + 1 < end) { i = escape(s, i, sb) } else { sb.append(s[i]); i++ }
                    }
                    i++
                }
                '\'' -> {
                    i++
                    while (i < end) {
                        if (s[i] == '\'') {
                            if (i + 1 < end && s[i + 1] == '\'') { sb.append('\''); i += 2 } else break
                        } else { sb.append(s[i]); i++ }
                    }
                    i++
                }
                else -> {
                    while (i < end && s[i] != ',') { sb.append(s[i]); i++ }
                    val t = sb.toString().trim()
                    sb.setLength(0)
                    sb.append(t)
                }
            }
            out.add(sb.toString())
        }
        return out
    }

    fun unquote(value: String): String {
        val s = value.trim()
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
            val sb = StringBuilder()
            var i = 1
            val end = s.length - 1
            while (i < end) {
                if (s[i] == '\\' && i + 1 < end) i = escape(s, i, sb) else { sb.append(s[i]); i++ }
            }
            return sb.toString()
        }
        if (s.length >= 2 && s.first() == '\'' && s.last() == '\'') return s.substring(1, s.length - 1).replace("''", "'")
        return s
    }

    /** Handles one backslash escape starting at [i]; returns the index after it. */
    private fun escape(s: String, i: Int, sb: StringBuilder): Int {
        when (val c = s[i + 1]) {
            'n' -> sb.append('\n')
            't' -> sb.append('\t')
            'r' -> sb.append('\r')
            '0' -> sb.append('\u0000')
            '_' -> sb.append(' ')
            'x' -> { sb.append(s.substring(i + 2, i + 4).toInt(16).toChar()); return i + 4 }
            'u' -> { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); return i + 6 }
            else -> sb.append(c)
        }
        return i + 2
    }
}
