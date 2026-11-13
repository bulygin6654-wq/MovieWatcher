package com.moviewatcher.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class AppVM(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    // --- списки ---
    var entries by mutableStateOf(store.loadEntries())
        private set
    var partner by mutableStateOf<Map<String, Entry>>(emptyMap())
        private set
    var partnerName by mutableStateOf<String?>(null)
        private set

    // --- пара ---
    var pair by mutableStateOf(store.pair)
        private set
    var myName by mutableStateOf(store.name)
        private set
    var syncMsg by mutableStateOf<String?>(null)
        private set

    // --- поиск ---
    var query by mutableStateOf("")
        private set
    var filter by mutableStateOf("all") // all | movie | tv | anime
        private set
    var results by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    // --- рекомендации ---
    var recs by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var recsLoading by mutableStateOf(false)
        private set

    // --- карточка ---
    val stack = mutableStateListOf<MediaItem>()
    var detail by mutableStateOf<Detail?>(null)
        private set
    var detailLoading by mutableStateOf(false)
        private set
    var detailError by mutableStateOf<String?>(null)
        private set
    private val detailCache = HashMap<String, Detail>()

    /** Серии сезонов: ключ "id#номер сезона". */
    val seasonCache = mutableStateMapOf<String, List<Episode>>()

    // --- главная (обзор) ---
    var exploreKind by mutableStateOf("movie")
        private set
    val rails = mutableStateMapOf<String, List<MediaItem>>()
    val trailers = mutableStateMapOf<String, List<Trailer>>()
    val genres = mutableStateMapOf<String, List<Genre>>()
    var exploreLoading by mutableStateOf(false)
        private set
    private val exploreLoaded = HashSet<String>()

    // --- экран списка (жанр) ---
    var listTitle by mutableStateOf<String?>(null)
        private set
    var listItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var listLoading by mutableStateOf(false)
        private set

    // --- помощник (поиск по описанию) ---
    var aiMode by mutableStateOf(false)
        private set
    var aiQuery by mutableStateOf("")
        private set
    var aiResults by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var aiLoading by mutableStateOf(false)
        private set
    var aiError by mutableStateOf<String?>(null)
        private set

    // --- актёры и режиссёры просмотренного ---
    var credits by mutableStateOf(store.loadCredits())
        private set
    private var metaJob: Job? = null

    // --- импорт ---
    var importMsg by mutableStateOf<String?>(null)
        private set

    private var searchJob: Job? = null
    private var pushJob: Job? = null
    private var detailJob: Job? = null

    /** Растёт после загрузки ключей — чтобы экран «Ещё» обновил предупреждения. */
    var keysVersion by mutableStateOf(0)
        private set

    init {
        Keys.init(store)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Keys.fetch(store) } }
            keysVersion++
            loadRecs()
            fillMeta()
        }
    }

    // ============ ОБЗОР ============

    fun setExploreKind(k: String) {
        exploreKind = k
        loadExplore(k)
    }

    fun loadExplore(k: String) {
        if (!exploreLoaded.add(k)) return
        viewModelScope.launch {
            exploreLoading = true
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val jobs = ArrayList<kotlinx.coroutines.Deferred<Unit>>()
                    fun rail(key: String, block: () -> List<MediaItem>) {
                        jobs.add(async { rails["$k/$key"] = runCatching(block).getOrDefault(emptyList()) })
                    }
                    when (k) {
                        "movie" -> {
                            rail("trending") { Tmdb.list("/trending/movie/week", "movie") }
                            rail("now") {
                                Tmdb.list("/movie/now_playing", "movie", "region" to "KZ")
                                    .ifEmpty { Tmdb.list("/movie/now_playing", "movie") }
                            }
                            rail("soon") {
                                Tmdb.list("/movie/upcoming", "movie", "region" to "KZ")
                                    .ifEmpty { Tmdb.list("/movie/upcoming", "movie") }
                            }
                            rail("popular") { Tmdb.list("/movie/popular", "movie") }
                            rail("top") { Tmdb.list("/movie/top_rated", "movie") }
                        }
                        "tv" -> {
                            rail("trending") { Tmdb.list("/trending/tv/week", "tv") }
                            rail("popular") { Tmdb.list("/tv/popular", "tv") }
                            rail("air") { Tmdb.list("/tv/on_the_air", "tv") }
                            rail("top") { Tmdb.list("/tv/top_rated", "tv") }
                        }
                        else -> {
                            rail("popular") { Shiki.list(mapOf("order" to "popularity")) }
                            rail("ongoing") { Shiki.list(mapOf("status" to "ongoing", "order" to "popularity")) }
                            rail("anons") { Shiki.list(mapOf("status" to "anons", "order" to "popularity")) }
                            rail("ranked") { Shiki.list(mapOf("order" to "ranked")) }
                        }
                    }
                    jobs.add(
                        async {
                            genres[k] = runCatching { if (k == "anime") Shiki.genres() else Tmdb.genres(k) }
                                .getOrDefault(emptyList())
                        },
                    )
                    jobs.awaitAll()
                    exploreLoading = false

                    if (k != "anime") {
                        val src = (
                            rails["$k/now"].orEmpty() + rails["$k/soon"].orEmpty() + rails["$k/trending"].orEmpty()
                            ).distinctBy { it.id }.take(14)
                        val tr = src.map { item ->
                            async {
                                runCatching { Tmdb.trailer(item.kind, item.id.split(":")[2].toInt()) }.getOrNull()
                                    ?.let { Trailer(item, it) }
                            }
                        }.awaitAll().filterNotNull().take(8)
                        trailers[k] = tr
                    }
                }
            }
            exploreLoading = false
        }
    }

    fun openGenre(kind: String, g: Genre) {
        listTitle = g.name
        listItems = emptyList()
        listLoading = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    when (kind) {
                        "anime" -> Shiki.list(mapOf("genre" to g.id, "order" to "popularity"), 50)
                        "tv" -> Tmdb.list(
                            "/discover/tv", "tv", "with_genres" to g.id,
                            "sort_by" to "popularity.desc", "vote_count.gte" to "50",
                        )
                        else -> Tmdb.list(
                            "/discover/movie", "movie", "with_genres" to g.id,
                            "sort_by" to "popularity.desc", "vote_count.gte" to "100",
                        )
                    }
                }
            }
            listItems = r.getOrDefault(emptyList())
            listLoading = false
        }
    }

    fun closeList() {
        listTitle = null
    }

    // ============ ПОМОЩНИК ============

    fun toggleAi() {
        aiMode = !aiMode
    }

    fun onAiQuery(q: String) {
        aiQuery = q
    }

    fun aiSearch() {
        val q = aiQuery.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            aiLoading = true
            aiError = null
            aiResults = emptyList()
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val cands = Gemini.suggest(q)
                    coroutineScope {
                        cands.map { c -> async { runCatching { Gemini.resolve(c) }.getOrNull() } }.awaitAll()
                    }.filterNotNull().distinctBy { it.id }
                }
            }
            r.onSuccess {
                aiResults = it
                if (it.isEmpty()) aiError = "Ничего не нашлось. Попробуйте описать иначе."
            }.onFailure {
                aiError = if (!Gemini.configured) {
                    "Не задан ключ Gemini (поле gemini в config/keys)."
                } else {
                    "Ошибка помощника: ${it.message}"
                }
            }
            aiLoading = false
        }
    }

    // ============ ДОТЯГИВАНИЕ ДАННЫХ ============

    /** Через пару секунд после последнего изменения дотягивает длительность и состав фильмов. */
    fun fillMeta() {
        metaJob?.cancel()
        metaJob = viewModelScope.launch {
            delay(2500)
            fillRuntimes()
            fillCredits()
        }
    }

    fun fillCredits() {
        viewModelScope.launch {
            val todo = entries.values
                .filter { (it.status == "watched" || it.status == "watching") && it.id.startsWith("tmdb:") }
                .filter { !credits.containsKey(it.id) }.take(200)
            if (todo.isEmpty()) return@launch
            val sem = Semaphore(5)
            val found = withContext(Dispatchers.IO) {
                coroutineScope {
                    todo.map { e ->
                        async {
                            sem.withPermit {
                                val p = e.id.split(":")
                                e.id to runCatching { Tmdb.credits(p[1], p[2].toInt()) }.getOrNull()
                            }
                        }
                    }.awaitAll()
                }
            }.mapNotNull { (id, c) -> if (c != null) id to c else null }.toMap()
            if (found.isEmpty()) return@launch
            credits = credits + found
            store.saveCredits(credits)
        }
    }

    /** Дотягивает длительность для просмотренного (для «часов просмотра»), в фоне. */
    fun fillRuntimes() {
        viewModelScope.launch {
            val todo = entries.values.filter { (it.status == "watched" || it.status == "watching") && it.runtime == null }.take(400)
            if (todo.isEmpty()) return@launch
            val sem = Semaphore(5)
            val found = withContext(Dispatchers.IO) {
                coroutineScope {
                    todo.map { e ->
                        async {
                            sem.withPermit {
                                val p = e.id.split(":")
                                val m = runCatching {
                                    if (p[0] == "shiki") Shiki.runtime(p[1].toInt()) else Tmdb.runtime(p[1], p[2].toInt())
                                }.getOrNull()
                                e.id to m
                            }
                        }
                    }.awaitAll()
                }
            }.filter { it.second != null }.toMap()
            if (found.isEmpty()) return@launch
            val map = LinkedHashMap(entries)
            for ((id, m) in found) {
                val cur = map[id] ?: continue
                map[id] = cur.copy(runtime = m)
            }
            entries = map
            store.saveEntries(map)
            schedulePush()
        }
    }

    // ============ ПОИСК ============

    fun onQuery(q: String) {
        query = q
        scheduleSearch()
    }

    fun onFilter(f: String) {
        filter = f
        scheduleSearch()
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(450)
            doSearch()
        }
    }

    private suspend fun doSearch() {
        val q = query.trim()
        if (q.isBlank()) {
            results = emptyList(); searchError = null; searching = false
            return
        }
        searching = true
        searchError = null
        val f = filter
        val (t, s) = coroutineScope {
            val a = async(Dispatchers.IO) { runCatching { Tmdb.search(q) } }
            val b = async(Dispatchers.IO) {
                if (f == "all" || f == "anime") runCatching { Shiki.search(q) } else Result.success(emptyList())
            }
            a.await() to b.await()
        }
        val tm = t.getOrDefault(emptyList())
        val sh = s.getOrDefault(emptyList())
        results = when (f) {
            "movie" -> tm.filter { it.kind == "movie" }
            "tv" -> tm.filter { it.kind == "tv" }
            "anime" -> sh.ifEmpty { tm.filter { it.animeHint } }
            else -> (if (sh.isNotEmpty()) tm.filter { !it.animeHint } else tm) + sh
        }
        if (t.isFailure && (f != "anime" || sh.isEmpty())) {
            searchError = if (!Tmdb.configured) "Не задан ключ TMDB (см. README)" else "Не удалось загрузить: ${t.exceptionOrNull()?.message}"
        } else if (results.isEmpty()) {
            searchError = "Ничего не найдено"
        }
        searching = false
    }

    // ============ КАРТОЧКА ============

    fun open(item: MediaItem) {
        stack.add(item)
        loadDetail(item)
    }

    fun back() {
        if (stack.isEmpty()) return
        stack.removeAt(stack.lastIndex)
        if (stack.isNotEmpty()) loadDetail(stack.last()) else {
            detailJob?.cancel()
            detail = null
            detailLoading = false
            detailError = null
        }
    }

    private fun loadDetail(item: MediaItem) {
        detailJob?.cancel()
        detailError = null
        detailCache[item.id]?.let {
            detail = it; detailLoading = false
            return
        }
        detail = null
        detailLoading = true
        detailJob = viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { fetchDetail(item) } }
            r.onSuccess {
                detailCache[item.id] = it
                detail = it
            }.onFailure {
                detailError = "Не удалось загрузить: ${it.message}"
            }
            detailLoading = false
        }
    }

    private fun fetchDetail(item: MediaItem): Detail {
        val parts = item.id.split(":")
        return if (parts[0] == "shiki") {
            Shiki.detail(parts[1].toInt())
        } else {
            val d = Tmdb.detail(parts[1], parts[2].toInt())
            val imdbId = d.imdb // у TMDB это id вида tt0137523, ниже заменяем его рейтингом IMDb
            if (imdbId != null) {
                val (rate, votes) = runCatching { Omdb.rating(imdbId) }.getOrDefault(null to null)
                d.copy(imdb = rate, imdbVotes = votes)
            } else d
        }
    }

    // ============ МОЙ СПИСОК ============

    fun setStatus(item: MediaItem, status: String?, rating: Int? = null, runtime: Int? = null) {
        val m = LinkedHashMap(entries)
        if (status == null) {
            m.remove(item.id)
        } else {
            m[item.id] = Entry(
                id = item.id, title = item.title, year = item.year, poster = item.poster,
                kind = item.kind, status = status, rating = rating, ts = System.currentTimeMillis(),
                runtime = runtime ?: entries[item.id]?.runtime,
                eps = entries[item.id]?.eps, total = entries[item.id]?.total,
            )
        }
        entries = m
        store.saveEntries(m)
        schedulePush()
        fillMeta()
    }

    // ============ СЕРИАЛЫ: СЕРИИ ============

    fun allKeys(d: Detail): List<String> = d.seasons.flatMap { s -> (1..s.count).map { "${s.number}x$it" } }

    fun loadSeason(item: MediaItem, season: Int, count: Int) {
        val key = "${item.id}#$season"
        if (seasonCache.containsKey(key)) return
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { Tmdb.season(item.id.split(":")[2].toInt(), season) }.getOrNull()
            }
            seasonCache[key] = list?.takeIf { it.isNotEmpty() }
                ?: (1..count).map { Episode(it, "Серия $it", null) }
        }
    }

    /** Статус сериала/аниме: "want" | "watching" | "watched" | null (убрать). */
    fun setSeriesStatus(item: MediaItem, d: Detail?, status: String?, rating: Int?) {
        val m = LinkedHashMap(entries)
        if (status == null) {
            m.remove(item.id)
        } else {
            val old = entries[item.id]
            val eps = if (status == "watched" && d != null && d.seasons.isNotEmpty()) {
                allKeys(d).joinToString(",")
            } else old?.eps
            m[item.id] = Entry(
                id = item.id, title = item.title, year = item.year, poster = item.poster,
                kind = item.kind, status = status, rating = rating ?: old?.rating,
                ts = System.currentTimeMillis(),
                runtime = d?.runtimeMin ?: old?.runtime, eps = eps, total = d?.totalEps ?: old?.total,
            )
        }
        entries = m
        store.saveEntries(m)
        schedulePush()
        fillMeta()
    }

    /** Отметить / снять серии. Статус пересчитывается: «Смотрю», а если сериал закончился и всё просмотрено — «Посмотрел». */
    fun markEpisodes(item: MediaItem, d: Detail, keys: Collection<String>, add: Boolean) {
        val old = entries[item.id]
        val cur = old?.epSet() ?: emptySet()
        val next = if (add) cur + keys else cur - keys.toSet()
        val m = LinkedHashMap(entries)
        if (next.isEmpty()) {
            if (old == null) return
            m[item.id] = old.copy(status = "want", eps = null, ts = System.currentTimeMillis())
        } else {
            val total = d.totalEps
            val status = if (d.ended && total != null && next.size >= total) "watched" else "watching"
            m[item.id] = Entry(
                id = item.id, title = item.title, year = item.year, poster = item.poster,
                kind = item.kind, status = status, rating = old?.rating,
                ts = System.currentTimeMillis(),
                runtime = d.runtimeMin ?: old?.runtime, eps = next.joinToString(","), total = total,
            )
        }
        entries = m
        store.saveEntries(m)
        schedulePush()
        fillMeta()
    }

    // ============ ПАРА ============

    fun setName(n: String) {
        myName = n
        store.name = n
        schedulePush()
    }

    fun createPair() {
        val code = Sync.newCode()
        applyPair(code)
    }

    fun joinPair(raw: String) {
        val code = raw.trim().uppercase().filter { it.isLetterOrDigit() }
        if (code.length < 4) {
            syncMsg = "Введите код пары"
            return
        }
        applyPair(code)
    }

    private fun applyPair(code: String) {
        pair = code
        store.pair = code
        partner = emptyMap()
        partnerName = null
        syncMsg = null
        viewModelScope.launch {
            pushNow()
            pull()
        }
    }

    fun leavePair() {
        pair = ""
        store.pair = ""
        partner = emptyMap()
        partnerName = null
        syncMsg = null
    }

    private fun schedulePush() {
        if (pair.isBlank()) return
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(1200)
            pushNow()
        }
    }

    private suspend fun pushNow() {
        if (pair.isBlank()) return
        if (!Sync.configured) {
            syncMsg = "Firebase не настроен (см. README)"
            return
        }
        val code = pair
        val snapshot = entries
        val nm = myName.ifBlank { "Я" }
        val r = withContext(Dispatchers.IO) { runCatching { Sync.push(code, store.uid, nm, snapshot) } }
        syncMsg = if (r.isSuccess) null else "Ошибка синхронизации: ${r.exceptionOrNull()?.message}"
    }

    suspend fun pull() {
        if (pair.isBlank() || !Sync.configured) return
        val code = pair
        val r = withContext(Dispatchers.IO) { runCatching { Sync.pull(code, store.uid) } }
        r.onSuccess { p ->
            if (code == pair) {
                partner = p?.entries ?: emptyMap()
                partnerName = p?.name
            }
        }.onFailure {
            syncMsg = "Ошибка синхронизации: ${it.message}"
        }
    }

    // ============ РЕКОМЕНДАЦИИ ============

    fun loadRecs() {
        viewModelScope.launch {
            recsLoading = true
            val all = entries.values + partner.values
            var seeds = all.filter { it.status == "watched" && (it.rating ?: 0) >= 8 }.shuffled().take(4)
            if (seeds.isEmpty()) seeds = all.filter { it.status == "watched" }.shuffled().take(3)
            if (seeds.isEmpty()) {
                recs = emptyList(); recsLoading = false
                return@launch
            }
            val known = entries.keys + partner.keys
            val list = withContext(Dispatchers.IO) {
                coroutineScope {
                    seeds.map { e ->
                        async { runCatching { similarOf(e.id) }.getOrDefault(emptyList()) }
                    }.awaitAll()
                }
            }.flatten().filter { it.id !in known }.distinctBy { it.id }.shuffled().take(24)
            recs = list
            recsLoading = false
        }
    }

    private fun similarOf(id: String): List<MediaItem> {
        val p = id.split(":")
        return if (p[0] == "shiki") Shiki.similar(p[1].toInt()) else Tmdb.similar(p[1], p[2].toInt())
    }

    // ============ ИМПОРТ ============

    fun importCsv(uri: Uri, status: String) {
        viewModelScope.launch {
            importMsg = "Читаю файл…"
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .bufferedReader().use { it.readText() }
                }.getOrNull()
            }
            if (text == null) {
                importMsg = "Не удалось прочитать файл"
                return@launch
            }
            val rows = CsvImport.rows(text)
            if (rows.isEmpty()) {
                importMsg = "В файле не найдено строк с фильмами"
                return@launch
            }
            val done = AtomicInteger(0)
            val sem = Semaphore(5)
            importMsg = "Импорт: 0 / ${rows.size}"
            val matched = withContext(Dispatchers.IO) {
                coroutineScope {
                    rows.map { row ->
                        async {
                            sem.withPermit {
                                val m = runCatching { CsvImport.match(row) }.getOrNull()
                                importMsg = "Импорт: ${done.incrementAndGet()} / ${rows.size}"
                                row to m
                            }
                        }
                    }.awaitAll()
                }
            }
            val map = LinkedHashMap(entries)
            var added = 0
            val now = System.currentTimeMillis()
            for ((row, item) in matched) {
                if (item == null) continue
                val old = map[item.id]
                if (old != null && old.status == "watched" && status == "want") continue
                map[item.id] = Entry(
                    id = item.id, title = item.title, year = item.year, poster = item.poster,
                    kind = item.kind, status = status,
                    rating = if (status == "watched") row.rating else null, ts = now,
                )
                added++
            }
            entries = map
            store.saveEntries(map)
            schedulePush()
            val missed = rows.size - matched.count { it.second != null }
            importMsg = "Готово: добавлено $added из ${rows.size}" +
                if (missed > 0) ". Не найдено: $missed" else ""
            fillMeta()
        }
    }
}
