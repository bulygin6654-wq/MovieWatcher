package com.moviewatcher.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Прямой импорт из Must по нику (профиль должен быть публичным): списки, оценки, прогресс по сериям. */
object MustImport {
    private const val BASE = "https://mustapp.com/api"

    data class Item(
        val title: String,
        val year: String?,
        val isShow: Boolean,
        val status: String, // want | watching | watched
        val rating: Int?,
        val epsWatched: Int?,
        val ts: Long?,
    )

    private fun call(method: String, url: String, token: String?, json: String?): String {
        val b = Request.Builder().url(url)
        if (token != null) {
            b.header("Bearer", token)
            b.header("Authorization", "Bearer $token")
        }
        b.header("Accept", "application/json")
        val body = json?.toRequestBody("application/json".toMediaType())
        b.method(method, body)
        Net.client.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return text
        }
    }

    private fun idSet(a: JSONArray?): Set<String> {
        val s = HashSet<String>()
        if (a != null) for (i in 0 until a.length()) s.add(a.opt(i).toString())
        return s
    }

    private fun count(v: Any?): Int? = when (v) {
        is Number -> v.toInt()
        is JSONArray -> v.length()
        else -> null
    }

    private fun parseTs(s: String?): Long? =
        s?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

    fun fetch(username: String, progress: (String) -> Unit): List<Item> {
        val token = runCatching {
            JSONObject(call("POST", "$BASE/acc/anonymous", null, "{}")).optString("token").ifBlank { null }
        }.getOrNull()

        progress("Ищу профиль…")
        val name = URLEncoder.encode(username.trim().removePrefix("@"), "UTF-8")
        val profile = try {
            JSONObject(call("GET", "$BASE/users/uri/$name", token, null))
        } catch (e: Exception) {
            throw IOException("Профиль не найден или Must не отвечает (${e.message})")
        }
        if (profile.optBoolean("is_private")) throw IOException("Профиль закрыт. Откройте его в настройках Must.")
        val pid = profile.opt("id")?.toString() ?: throw IOException("Нет id профиля")
        val lists = profile.optJSONObject("lists") ?: throw IOException("В профиле не видно списков")
        val want = idSet(lists.optJSONArray("want"))
        val watched = idSet(lists.optJSONArray("watched"))
        val shows = idSet(lists.optJSONArray("shows"))
        val all = (want + watched + shows).toList()

        val out = ArrayList<Item>()
        var done = 0
        for (chunk in all.chunked(100)) {
            progress("Загружаю данные Must: $done / ${all.size}")
            val ids = JSONArray().also { a -> chunk.forEach { id -> a.put(id.toLongOrNull() ?: id) } }
            val resp = call("POST", "$BASE/users/id/$pid/products?embed=product", token, JSONObject().put("ids", ids).toString())
            val arr = if (resp.trimStart().startsWith("[")) JSONArray(resp)
            else JSONObject(resp).let { it.optJSONArray("products") ?: it.optJSONArray("items") ?: JSONArray() }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val p = o.optJSONObject("product") ?: continue
                val type = p.optString("type").lowercase()
                if (type == "season" || type == "episode" || type == "youtube") continue
                val title = p.optString("title")
                if (title.isBlank()) continue
                val pidStr = p.opt("id")?.toString() ?: o.optJSONObject("user_product_info")?.opt("product_id")?.toString()
                val info = o.optJSONObject("user_product_info")
                val isShow = type == "show" || type == "shows" || pidStr in shows
                val rate = info?.optInt("rate", 0)?.takeIf { it in 1..10 }
                val eps = count(info?.optJSONObject("user_show_info")?.opt("episodes_watched"))
                val st = info?.optString("status").orEmpty().lowercase()
                var status = when {
                    pidStr in watched -> "watched"
                    pidStr in want -> "want"
                    st.contains("want") || st.contains("plan") -> "want"
                    st.contains("watched") || st.contains("finish") || st.contains("complete") -> "watched"
                    isShow && (eps ?: 0) > 0 -> "watching"
                    isShow -> "watching"
                    else -> "want"
                }
                if (!isShow && status == "watching") status = "watched"
                out.add(
                    Item(
                        title = title, year = p.optString("release_date").take(4).takeIf { it.length == 4 },
                        isShow = isShow, status = status, rating = rate,
                        epsWatched = eps, ts = parseTs(info?.optString("modified_at")),
                    )
                )
            }
            done += chunk.size
        }
        return out
    }

    /** Подбор в TMDB по названию и году с учётом фильм/сериал. */
    fun match(it: Item): MediaItem? {
        val found = Tmdb.search(it.title).filter { m -> m.id.contains(if (it.isShow) ":tv:" else ":movie:") }
        if (found.isEmpty()) return null
        val y = it.year?.toIntOrNull()
        if (y != null) {
            found.firstOrNull { m -> m.year?.toIntOrNull()?.let { fy -> kotlin.math.abs(fy - y) <= 1 } == true }
                ?.let { return it }
        }
        return found.first()
    }
}
