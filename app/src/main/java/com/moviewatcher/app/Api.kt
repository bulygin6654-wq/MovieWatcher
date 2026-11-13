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
    private val key get() = Keys.tmdb
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

    /** Длительность в минутах: фильм — runtime, сериал — серии × длительность серии. */
    fun runtime(kind: String, id: Int): Int? {
        val r = call("/$kind/$id")
        if (kind == "movie") return r.optInt("runtime", 0).takeIf { it > 0 }
        val eps = r.optInt("number_of_episodes", 0)
        val per = r.optJSONArray("episode_run_time")?.optInt(0, 0)?.takeIf { it > 0 }
            ?: r.optJSONObject("last_episode_to_air")?.optInt("runtime", 0)?.takeIf { it > 0 }
            ?: 0
        return (eps * per).takeIf { it > 0 }
    }

    fun list(path: String, kind: String, vararg params: Pair<String, String>): List<MediaItem> =
        call(path, "language" to "ru-RU", *params).optJSONArray("results").objs().map { parse(it, kind) }

    fun genres(kind: String): List<Genre> =
        call("/genre/$kind/list", "language" to "ru").optJSONArray("genres").objs().map {
            Genre(it.optInt("id").toString(), it.optString("name").replaceFirstChar { c -> c.uppercase() })
        }

    fun trailer(kind: String, id: Int): String? {
        for (lang in listOf("ru-RU", "en-US")) {
            val v = runCatching { call("/$kind/$id/videos", "language" to lang).optJSONArray("results").objs() }
                .getOrDefault(emptyList())
            v.firstOrNull { it.str("site") == "YouTube" && it.str("type") == "Trailer" }?.str("key")
                ?.let { return "https://www.youtube.com/watch?v=$it" }
        }
        return null
    }

    fun credits(kind: String, id: Int): CreditInfo {
        val r = call("/$kind/$id/credits", "language" to "ru-RU")
        fun person(o: JSONObject) = Person(
            o.getInt("id"), o.str("name") ?: "",
            o.str("profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" },
        )
        val actors = r.optJSONArray("cast").objs().take(8).map { person(it) }
        val dirs = r.optJSONArray("crew").objs().filter { it.str("job") == "Director" }.take(2).map { person(it) }
        return CreditInfo(actors, dirs)
    }

    fun season(tvId: Int, n: Int): List<Episode> =
        call("/tv/$tvId/season/$n", "language" to "ru-RU").optJSONArray("episodes").objs().map {
            val num = it.optInt("episode_number")
            Episode(num, it.str("name") ?: "Серия $num", it.str("air_date"))
        }

    fun detail(kind: String, id: Int): Detail {
        val r = call(
            "/$kind/$id",
            "language" to "ru-RU",
            "append_to_response" to "external_ids,images,recommendations,similar,videos",
            "include_image_language" to "ru,en,null",
            "include_video_language" to "ru,en",
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

        val runtimeMin = if (kind == "movie") {
            r.optInt("runtime", 0).takeIf { it > 0 }
        } else {
            val eps = r.optInt("number_of_episodes", 0)
            val per = r.optJSONArray("episode_run_time")?.optInt(0, 0)?.takeIf { it > 0 }
                ?: r.optJSONObject("last_episode_to_air")?.optInt("runtime", 0)?.takeIf { it > 0 }
                ?: 0
            (eps * per).takeIf { it > 0 }
        }

        val seasons = if (kind == "tv") {
            r.optJSONArray("seasons").objs()
                .filter { it.optInt("season_number", 0) > 0 && it.optInt("episode_count", 0) > 0 }
                .map { SeasonInfo(it.optInt("season_number"), it.optInt("episode_count")) }
        } else emptyList()

        val vids = r.optJSONObject("videos")?.optJSONArray("results").objs()
            .filter { it.str("site") == "YouTube" && it.str("type") == "Trailer" }
        val trailerKey = (vids.firstOrNull { it.str("iso_639_1") == "ru" } ?: vids.firstOrNull())?.str("key")

        return Detail(
            trailerUrl = trailerKey?.let { "https://www.youtube.com/watch?v=$it" },
            seasons = seasons,
            ended = kind == "tv" && (r.str("status") == "Ended" || r.str("status") == "Canceled"),
            totalEps = if (kind == "tv") r.optInt("number_of_episodes", 0).takeIf { it > 0 } else null,
            item = base.copy(poster = poster ?: base.poster, overview = overview),
            imdb = imdb,
            tmdbRating = base.rating,
            genres = genres.ifBlank { null },
            extra = extra,
            similar = sim.map { parse(it, kind) }.take(20),
            runtimeMin = runtimeMin,
        )
    }
}

/** Рейтинг IMDb через OMDb. */
object Omdb {
    private val key get() = Keys.omdb
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

    fun list(params: Map<String, String>, limit: Int = 20): List<MediaItem> {
        val b = "$BASE/api/animes".toHttpUrl().newBuilder().addQueryParameter("limit", limit.toString())
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return org.json.JSONArray(Net.get(b.build().toString(), headers)).objs().map { parse(it) }
    }

    fun genres(): List<Genre> {
        val skip = setOf("Hentai", "Yaoi", "Yuri", "Erotica")
        return org.json.JSONArray(Net.get("$BASE/api/genres", headers)).objs()
            .filter { it.str("kind") == "anime" && it.str("name") !in skip }
            .mapNotNull { o ->
                val n = o.str("russian") ?: o.str("name") ?: return@mapNotNull null
                Genre(o.getInt("id").toString(), n)
            }
    }

    fun runtime(id: Int): Int? {
        val o = JSONObject(Net.get("$BASE/api/animes/$id", headers))
        return (o.optInt("episodes", 0) * o.optInt("duration", 0)).takeIf { it > 0 }
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
        val dur = o.optInt("duration", 0)
        val total = if (eps > 0) eps else o.optInt("episodes_aired", 0)
        val trailer = runCatching {
            val v = org.json.JSONArray(Net.get("$BASE/api/animes/$id/videos", headers)).objs()
                .filter { it.str("hosting") == "youtube" }
            (v.firstOrNull { it.str("kind") == "pv" } ?: v.firstOrNull())?.str("url")
        }.getOrNull()
        return Detail(
            trailerUrl = trailer,
            seasons = if (total > 0) listOf(SeasonInfo(1, total)) else emptyList(),
            ended = o.str("status") == "released",
            totalEps = total.takeIf { it > 0 },
            runtimeMin = (eps * dur).takeIf { it > 0 },
            item = base.copy(overview = desc),
            shikiRating = base.rating,
            genres = genres.ifBlank { null },
            extra = extra,
            similar = sim,
        )
    }
}

/** Помощник: описание → список подходящих фильмов (Gemini) → поиск в TMDB / Shikimori. */
object Gemini {
    val configured get() = Keys.gemini.isNotBlank()

    data class Cand(val title: String, val titleRu: String?, val year: Int?, val type: String)

    fun suggest(description: String): List<Cand> {
        if (!configured) throw IOException("Не задан ключ Gemini")
        val prompt = "Пользователь описывает фильм, сериал или аниме, которое хочет найти или вспомнить. " +
            "Предложи от 5 до 8 наиболее вероятных вариантов, самые подходящие первыми. " +
            "Ответь ТОЛЬКО JSON-массивом объектов вида " +
            "{\"title\":\"оригинальное название\",\"title_ru\":\"название по-русски\",\"year\":2010,\"type\":\"movie\"}. " +
            "Поле type: movie, tv или anime. Описание пользователя: " + description
        val models = listOf(Keys.geminiModel.ifBlank { "gemini-flash-latest" }, "gemini-flash-lite-latest", "gemini-2.5-flash")
            .distinct()
        val body = JSONObject()
            .put(
                "contents",
                org.json.JSONArray().put(
                    JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt))),
                ),
            )
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.3))
        var raw: String? = null
        var lastErr: Exception? = null
        loop@ for (attempt in 0 until 2) {
            for (model in models) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${Keys.gemini}"
                try {
                    raw = Net.send("POST", url, body.toString())
                    break@loop
                } catch (e: Exception) {
                    lastErr = e
                    val m = e.message.orEmpty()
                    // 400/401/403 — проблема с ключом или запросом, повторять бессмысленно
                    if (m.startsWith("HTTP 400") || m.startsWith("HTTP 401") || m.startsWith("HTTP 403")) throw e
                }
            }
            Thread.sleep(2000)
        }
        if (raw == null) {
            val m = lastErr?.message.orEmpty()
            throw IOException(
                if (m.startsWith("HTTP 503") || m.startsWith("HTTP 429")) "Gemini сейчас перегружен. Попробуйте через минуту."
                else m.take(120).ifBlank { "нет ответа" },
            )
        }
        val resp = JSONObject(raw)
        val parts = resp.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        val text = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false)) text.append(p.optString("text"))
        }
        val clean = text.toString().trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = org.json.JSONArray(clean)
        return arr.objs().mapNotNull { o ->
            val t = o.str("title") ?: o.str("title_ru") ?: return@mapNotNull null
            Cand(t, o.str("title_ru"), o.optInt("year", 0).takeIf { it > 0 }, o.str("type") ?: "movie")
        }.take(8)
    }

    fun resolve(c: Cand): MediaItem? {
        if (c.type == "anime") {
            (Shiki.search(c.titleRu ?: c.title).firstOrNull() ?: Shiki.search(c.title).firstOrNull())?.let { return it }
        }
        fun pick(list: List<MediaItem>): MediaItem? {
            val byKind = list.filter { c.type == "anime" || it.kind == c.type }.ifEmpty { list }
            val y = c.year
            val byYear = if (y != null) {
                byKind.firstOrNull { it.year?.toIntOrNull()?.let { v -> kotlin.math.abs(v - y) <= 1 } == true }
            } else null
            return byYear ?: byKind.firstOrNull()
        }
        pick(Tmdb.search(c.title))?.let { return it }
        c.titleRu?.let { ru -> pick(Tmdb.search(ru))?.let { return it } }
        return null
    }
}
