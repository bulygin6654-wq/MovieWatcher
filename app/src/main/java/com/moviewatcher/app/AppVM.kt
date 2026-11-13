package com.moviewatcher.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
            )
        }
        entries = m
        store.saveEntries(m)
        schedulePush()
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
        }
    }
}
