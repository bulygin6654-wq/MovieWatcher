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
    val reviews: List<Review> = emptyList(),
)

data class Review(
    val author: String,
    val rating: Double?,
    val text: String,
    val date: String?,
)

/** status: "want" | "watched" */
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
) {
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
