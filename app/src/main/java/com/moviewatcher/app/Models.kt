package com.moviewatcher.app

import org.json.JSONArray
import org.json.JSONObject

/** kind: "movie" | "tv" | "anime" */
data class MediaItem(
    val id: String, // tmdb:movie:550 | tmdb:tv:1396 | shiki:5114
    val title: String,
    val original: String? = null,
    val year: String? = null,
    val poster: String? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val kind: String,
    val animeHint: Boolean = false,
)

data class Detail(
    val item: MediaItem,
    val imdb: String? = null,
    val imdbVotes: String? = null,
    val tmdbRating: Double? = null,
    val shikiRating: Double? = null,
    val genres: String? = null,
    val extra: String? = null,
    val similar: List<MediaItem> = emptyList(),
    val runtimeMin: Int? = null,
    val seasons: List<SeasonInfo> = emptyList(),
    val ended: Boolean = false,
    val totalEps: Int? = null,
    val trailerUrl: String? = null,
)

data class Trailer(val item: MediaItem, val url: String)

data class Genre(val id: String, val name: String)

data class Person(val id: Int, val name: String, val photo: String?)

data class CreditInfo(val actors: List<Person>, val dirs: List<Person>)

fun creditsToJson(m: Map<String, CreditInfo>): String {
    fun arr(l: List<Person>) = JSONArray().also { a ->
        l.forEach { a.put(JSONArray().put(it.id).put(it.name).put(it.photo ?: "")) }
    }
    val o = JSONObject()
    m.forEach { (k, v) -> o.put(k, JSONObject().put("a", arr(v.actors)).put("d", arr(v.dirs))) }
    return o.toString()
}

fun creditsFromJson(s: String?): Map<String, CreditInfo> {
    if (s.isNullOrBlank()) return emptyMap()
    return try {
        val o = JSONObject(s)
        fun list(a: JSONArray?): List<Person> {
            if (a == null) return emptyList()
            val out = ArrayList<Person>()
            for (i in 0 until a.length()) {
                val p = a.optJSONArray(i) ?: continue
                out.add(Person(p.getInt(0), p.optString(1), p.optString(2).ifBlank { null }))
            }
            return out
        }
        val res = LinkedHashMap<String, CreditInfo>()
        for (k in o.keys()) {
            val c = o.getJSONObject(k)
            res[k] = CreditInfo(list(c.optJSONArray("a")), list(c.optJSONArray("d")))
        }
        res
    } catch (_: Exception) {
        emptyMap()
    }
}

data class SeasonInfo(val number: Int, val count: Int)

/** date — ISO yyyy-MM-dd или null */
data class Episode(val num: Int, val title: String, val date: String?)

/** status: "want" | "watching" | "watched" */
data class Entry(
    val id: String,
    val title: String,
    val year: String?,
    val poster: String?,
    val kind: String,
    val status: String,
    val rating: Int?,
    val ts: Long,
    val runtime: Int? = null, // минуты, нужны для «часов просмотра»
    val eps: String? = null, // просмотренные серии: "1x1,1x2,2x1"
    val total: Int? = null, // всего серий
) {
    fun epSet(): Set<String> = eps?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun toItem() = MediaItem(id = id, title = title, year = year, poster = poster, kind = kind)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("t", title)
        put("y", year ?: "")
        put("p", poster ?: "")
        put("k", kind)
        put("s", status)
        put("r", rating ?: 0)
        put("ts", ts)
        put("rt", runtime ?: 0)
        put("e", eps ?: "")
        put("tt", total ?: 0)
    }

    companion object {
        fun fromJson(o: JSONObject) = Entry(
            id = o.getString("id"),
            title = o.optString("t"),
            year = o.optString("y").ifBlank { null },
            poster = o.optString("p").ifBlank { null },
            kind = o.optString("k", "movie"),
            status = o.optString("s", "want"),
            rating = o.optInt("r", 0).takeIf { it > 0 },
            ts = o.optLong("ts", 0L),
            runtime = o.optInt("rt", 0).takeIf { it > 0 },
            eps = o.optString("e").ifBlank { null },
            total = o.optInt("tt", 0).takeIf { it > 0 },
        )

        fun listToJson(m: Map<String, Entry>): String {
            val a = JSONArray()
            m.values.forEach { a.put(it.toJson()) }
            return a.toString()
        }

        fun listFromJson(s: String?): Map<String, Entry> {
            if (s.isNullOrBlank()) return emptyMap()
            return try {
                val a = JSONArray(s)
                val out = LinkedHashMap<String, Entry>()
                for (i in 0 until a.length()) {
                    val e = fromJson(a.getJSONObject(i))
                    out[e.id] = e
                }
                out
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }
}

fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

fun JSONObject.dbl(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() && it > 0 }

fun JSONArray?.objs(): List<JSONObject> {
    if (this == null) return emptyList()
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(it) }
    return out
}

fun kindLabel(kind: String) = when (kind) {
    "movie" -> "Фильм"
    "tv" -> "Сериал"
    "anime" -> "Аниме"
    else -> ""
}
