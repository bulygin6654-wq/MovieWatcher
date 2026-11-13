package com.moviewatcher.app

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object Net {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return body
        }
    }

    fun send(method: String, url: String, json: String): String {
        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).method(method, body).build()
        client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${text.take(200)}")
            return text
        }
    }
}

/** TMDB: названия, описания и постеры на русском, «похожее». */
object Tmdb {
    private const val BASE = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p/w342"
    private const val IMG_BIG = "https://image.tmdb.org/t/p/w500"
    private val key get() = BuildConfig.TMDB_KEY
    val configured get() = key.isNotBlank()

    private fun call(path: String, vararg params: Pair<String, String>): JSONObject {
        if (!configured) throw IOException("Не задан ключ TMDB")
        val b = "$BASE$path".toHttpUrl().newBuilder()
        params.forEach { b.addQueryParameter(it.first, it.second) }
        val headers = HashMap<String, String>()
        if (key.length > 60) headers["Authorization"] = "Bearer $key" else b.addQueryParameter("api_key", key)
        return JSONObject(Net.get(b.build().toString(), headers))
    }

    private fun parse(o: JSONObject, kind: String): MediaItem {
        val movie = kind == "movie"
        val title = o.str(if (movie) "title" else "name")
        val orig = o.str(if (movie) "original_title" else "original_name")
        val date = o.str(if (movie) "release_date" else "first_air_date")
        val genres = o.optJSONArray("genre_ids")
        var isAnimation = false
        if (genres != null) for (i in 0 until genres.length()) if (genres.optInt(i) == 16) isAnimation = true
        return MediaItem(
            id = "tmdb:$kind:${o.getInt("id")}",
            title = title ?: orig ?: "Без названия",
            original = orig?.takeIf { it != title },
            year = date?.take(4)?.takeIf { it.length == 4 },
            poster = o.str("poster_path")?.let { IMG + it },
            overview = o.str("overview"),
            rating = o.dbl("vote_average"),
            kind = kind,
            animeHint = isAnimation && o.str("original_language") == "ja",
        )
    }

    fun search(q: String): List<MediaItem> {
        val r = call(
            "/search/multi",
            "query" to q, "language" to "ru-RU", "include_adult" to "false",
        )
        return r.optJSONArray("results").objs()
            .filter { it.str("media_type") == "movie" || it.str("media_type") == "tv" }
            .map { parse(it, it.getString("media_type")) }
    }

    fun basic(kind: String, id: Int): MediaItem =
        parse(call("/$kind/$id", "language" to "ru-RU"), kind)

    fun findByImdb(imdb: String): MediaItem? {
        val r = call("/find/$imdb", "external_source" to "imdb_id", "language" to "ru-RU")
        r.optJSONArray("movie_results").objs().firstOrNull()?.let { return parse(it, "movie") }
        r.optJSONArray("tv_results").objs().firstOrNull()?.let { return parse(it, "tv") }
        return null
    }

    fun similar(kind: String, id: Int): List<MediaItem> {
        val r = call("/$kind/$id/recommendations", "language" to "ru-RU")
        var list = r.optJSONArray("results").objs()
        if (list.isEmpty()) list = call("/$kind/$id/similar", "language" to "ru-RU").optJSONArray("results").objs()
        return list.map { parse(it, kind) }.take(20)
    }

    fun detail(kind: String, id: Int): Detail {
        val r = call(
            "/$kind/$id",
            "language" to "ru-RU",
            "append_to_response" to "external_ids,images,recommendations,similar",
            "include_image_language" to "ru,en,null",
        )
        val base = parse(r, kind)

        // Русский постер, если есть
        val ruPoster = r.optJSONObject("images")?.optJSONArray("posters").objs()
            .firstOrNull { it.str("iso_639_1") == "ru" }?.str("file_path")
        val poster = ruPoster?.let { IMG_BIG + it } ?: r.str("poster_path")?.let { IMG_BIG + it }

        // Описание: если на русском нет — берём английское
        var overview = base.overview
        if (overview == null) {
            overview = runCatching { call("/$kind/$id", "language" to "en-US").str("overview") }.getOrNull()
        }

        val imdb = r.optJSONObject("external_ids")?.str("imdb_id") ?: r.str("imdb_id")
        val genres = r.optJSONArray("genres").objs().mapNotNull { it.str("name") }.joinToString(", ")
        val extra = if (kind == "movie") {
            r.optInt("runtime", 0).takeIf { it > 0 }?.let { "$it мин" }
        } else {
            val s = r.optInt("number_of_seasons", 0)
            val e = r.optInt("number_of_episodes", 0)
            if (s > 0) "Сезонов: $s, серий: $e" else null
        }

        var sim = r.optJSONObject("recommendations")?.optJSONArray("results").objs()
        if (sim.isEmpty()) sim = r.optJSONObject("similar")?.optJSONArray("results").objs()

        return Detail(
            item = base.copy(poster = poster ?: base.poster, overview = overview),
            imdb = imdb,
            tmdbRating = base.rating,
            genres = genres.ifBlank { null },
            extra = extra,
            similar = sim.map { parse(it, kind) }.take(20),
        )
    }
}

/** Рейтинг IMDb через OMDb. */
object Omdb {
    private val key get() = BuildConfig.OMDB_KEY
    val configured get() = key.isNotBlank()

    /** (рейтинг, число голосов) */
    fun rating(imdbId: String): Pair<String?, String?> {
        if (!configured) return null to null
        val o = JSONObject(Net.get("https://www.omdbapi.com/?apikey=$key&i=$imdbId"))
        val r = o.str("imdbRating")?.takeIf { it != "N/A" }
        val v = o.str("imdbVotes")?.takeIf { it != "N/A" }
        return r to v
    }
}

/** Shikimori: аниме с русскими названиями. */
object Shiki {
    const val BASE = "https://shikimori.one"
    private val headers = mapOf("User-Agent" to "MovieWatcher")

    private fun parse(o: JSONObject): MediaItem {
        val ru = o.str("russian")
        val name = o.str("name")
        return MediaItem(
            id = "shiki:${o.getInt("id")}",
            title = ru ?: name ?: "Без названия",
            original = name?.takeIf { it != ru },
            year = o.str("aired_on")?.take(4)?.takeIf { it.length == 4 },
            poster = o.optJSONObject("image")?.str("original")?.let { BASE + it },
            rating = o.str("score")?.toDoubleOrNull()?.takeIf { it > 0 },
            kind = "anime",
        )
    }

    fun search(q: String): List<MediaItem> {
        val url = "$BASE/api/animes".toHttpUrl().newBuilder()
            .addQueryParameter("search", q)
            .addQueryParameter("limit", "20")
            .addQueryParameter("order", "popularity")
            .build().toString()
        val arr = org.json.JSONArray(Net.get(url, headers))
        return arr.objs().map { parse(it) }
    }

    fun similar(id: Int): List<MediaItem> =
        org.json.JSONArray(Net.get("$BASE/api/animes/$id/similar", headers)).objs().map { parse(it) }.take(20)

    fun detail(id: Int): Detail {
        val o = JSONObject(Net.get("$BASE/api/animes/$id", headers))
        val base = parse(o)
        val desc = o.str("description")
            ?.replace(Regex("\\[\\[|\\]\\]"), "")
            ?.replace(Regex("\\[/?[a-zA-Z_]+(=[^\\]]*)?\\]"), "")
            ?.trim()
        val genres = o.optJSONArray("genres").objs().mapNotNull { it.str("russian") ?: it.str("name") }
            .joinToString(", ")
        val status = when (o.str("status")) {
            "anons" -> "анонс"
            "ongoing" -> "выходит"
            "released" -> "вышло"
            else -> null
        }
        val eps = o.optInt("episodes", 0)
        val extra = listOfNotNull(
            status,
            if (eps > 0) "серий: $eps" else null,
            o.optInt("duration", 0).takeIf { it > 0 }?.let { "$it мин/серия" },
        ).joinToString(", ").ifBlank { null }
        val sim = runCatching { similar(id) }.getOrDefault(emptyList())
        return Detail(
            item = base.copy(overview = desc),
            shikiRating = base.rating,
            genres = genres.ifBlank { null },
            extra = extra,
            similar = sim,
        )
    }
}
