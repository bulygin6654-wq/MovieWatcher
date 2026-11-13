@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.moviewatcher.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

val Accent = Color(0xFF3D8BFF)
val Star = Color(0xFFFFC857)
val Danger = Color(0xFFFF6B6B)

@Composable
fun MWTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondary = Star,
            background = Color(0xFF121212),
            onBackground = Color(0xFFECECEC),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFECECEC),
            surfaceVariant = Color(0xFF2B2B2B),
            onSurfaceVariant = Color(0xFFA8A8A8),
            secondaryContainer = Color(0xFF2B2B2B),
            onSecondaryContainer = Accent,
        ),
        content = content,
    )
}

fun f1(d: Double): String = String.format("%.1f", d)

// ============================================================ ROOT

@Composable
fun App(vm: AppVM) {
    var tab by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Пока приложение на экране — раз в 15 секунд подтягиваем список партнёра
    LaunchedEffect(vm.pair) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.pull()
                delay(15_000)
            }
        }
    }

    BackHandler(enabled = vm.stack.isNotEmpty()) { vm.back() }

    if (vm.stack.isNotEmpty()) {
        DetailScreen(vm, vm.stack.last())
        return
    }

    val icons = listOf(Icons.Default.Person, Icons.Default.Search, Icons.Default.Favorite, Icons.Default.Settings)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF181818)) {
                icons.forEachIndexed { i, icon ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, null) },
                        alwaysShowLabel = false,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> MyTab(vm)
                1 -> SearchTab(vm)
                2 -> TogetherTab(vm)
                else -> SettingsTab(vm)
            }
        }
    }
}

// ============================================================ COMMON

@Composable
fun Poster(url: String?, title: String, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(title.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** Тёмная плашка на постере. star = true — перед текстом жёлтая звезда. */
@Composable
fun PosterBadge(text: String, star: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (star) Text("★ ", color = Star, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PosterCard(
    title: String,
    poster: String?,
    year: String?,
    rating: String?,
    mark: String?,
    showTitle: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box {
            Poster(poster, title, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            if (rating != null) PosterBadge(rating, true, Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (mark != null) PosterBadge(mark, false, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        if (showTitle) {
            Text(
                title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (year != null) {
                Text(year, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(
            text, fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun markOf(e: Entry?): String? = when (e?.status) {
    "watched" -> "✓"
    "want" -> "Хочу"
    "watching" -> e.total?.let { "${e.epSet().size}/$it" } ?: "Смотрю"
    else -> null
}

val RU_MONTHS = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

fun ruDate(iso: String?): String? = try {
    val p = iso!!.split("-")
    "${p[2].toInt()} ${RU_MONTHS[p[1].toInt() - 1]}, ${p[0]}"
} catch (_: Exception) {
    null
}

@Composable
fun EpisodeRow(ep: Episode, watched: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val aired = ep.date == null || ep.date <= java.time.LocalDate.now().toString()
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${ep.num}", fontSize = 12.sp, color = dim, modifier = Modifier.width(24.dp))
            Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
                if (watched) {
                    Text("✓", fontWeight = FontWeight.Bold)
                } else if (aired) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Accent))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    ep.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (aired) MaterialTheme.colorScheme.onBackground else dim,
                )
                ruDate(ep.date)?.let { Text(it, fontSize = 12.sp, color = dim) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

// ============================================================ SEARCH

@Composable
fun SearchTab(vm: AppVM) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.onQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text("Фильм, сериал или аниме") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Default.Close, null) }
                }
            },
        )
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("all" to "Всё", "movie" to "Фильмы", "tv" to "Сериалы", "anime" to "Аниме").forEach { (k, l) ->
                FilterChip(selected = vm.filter == k, onClick = { vm.onFilter(k) }, label = { Text(l) })
            }
        }

        if (vm.query.isBlank()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Подборка для вас", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.loadRecs() }) { Icon(Icons.Default.Refresh, null) }
            }
            if (vm.recsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.recs.isEmpty() && !vm.recsLoading) {
                Hint("Отметьте несколько просмотренных фильмов с оценкой — здесь появятся похожие рекомендации для вас обоих.")
            } else {
                ItemGrid(vm, vm.recs)
            }
        } else {
            if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            val err = vm.searchError
            if (vm.results.isEmpty() && err != null && !vm.searching) {
                Hint(err)
            } else {
                ItemGrid(vm, vm.results)
            }
        }
    }
}

@Composable
fun ItemGrid(vm: AppVM, list: List<MediaItem>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(list, key = { it.id }) { item ->
            PosterCard(
                title = item.title, poster = item.poster, year = item.year,
                rating = item.rating?.let { f1(it) },
                mark = markOf(vm.entries[item.id]),
                showTitle = true,
            ) { vm.open(item) }
        }
    }
}

// ============================================================ MY PROFILE

@Composable
fun StatCard(value: String, label: String) {
    Box(
        Modifier
            .width(130.dp).height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CountBlock(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MyTab(vm: AppVM) {
    var sub by remember { mutableIntStateOf(0) }
    val all = vm.entries.values
    val want = all.filter { it.status == "want" }.sortedByDescending { it.ts }
    val watched = all.filter { it.status == "watched" }.sortedByDescending { it.ts }
    val series = all.filter { it.kind == "tv" || it.kind == "anime" }
        .sortedWith(compareByDescending<Entry> { it.status == "watching" }.thenByDescending { it.ts })

    Column(Modifier.fillMaxSize()) {
        Text(
            vm.myName.ifBlank { "Мой профиль" },
            fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CountBlock(want.size, "Посмотрю")
            CountBlock(watched.size, "Просмотрены")
            CountBlock(series.size, "Сериалы")
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Pill("Посмотрю", sub == 0) { sub = 0 }
            Pill("Просмотрен", sub == 1) { sub = 1 }
            Pill("Сериалы", sub == 2) { sub = 2 }
            Pill("Информация", sub == 3) { sub = 3 }
        }

        if (sub == 3) {
            InfoTab(vm)
        } else {
            val list = when (sub) {
                0 -> want
                1 -> watched
                else -> series
            }
            if (list.isEmpty()) {
                Hint(
                    when (sub) {
                        0 -> "Пока пусто. Найдите фильм и нажмите «Хочу посмотреть»."
                        1 -> "Пока пусто. Отметьте просмотренное или импортируйте из Must в разделе «Ещё»."
                        else -> "Сериалов и аниме в ваших списках пока нет."
                    },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { e ->
                        PosterCard(
                            title = e.title, poster = e.poster, year = e.year,
                            rating = e.rating?.toString(),
                            mark = if (sub == 2) markOf(e) else null,
                            showTitle = false,
                        ) { vm.open(e.toItem()) }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoTab(vm: AppVM) {
    val all = vm.entries.values
    val watched = all.filter { it.status == "watched" }
    val rated = watched.mapNotNull { it.rating }
    val minutes = all.sumOf { e ->
        val rt = e.runtime ?: 0
        val tot = e.total ?: 0
        when (e.status) {
            "watched" -> rt
            "watching" -> if (tot > 0) rt * e.epSet().size / tot else 0
            else -> 0
        }
    }
    val hours = minutes / 60.0
    val avg = if (rated.isEmpty()) "–" else f1(rated.average())
    val counts = (1..10).map { n -> rated.count { it == n } }
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard("${watched.size}", "Просмотрено")
            StatCard("${all.count { it.status == "want" }}", "Хочу посмотреть")
            StatCard(f1(hours), "Часов просмотра")
            StatCard(avg, "Средняя оценка")
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Ваши оценки", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                counts.forEachIndexed { i, c ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text("$c", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val barHeight = (70f * c / maxCount).coerceAtLeast(2f)
                        Box(
                            Modifier.fillMaxWidth().height(barHeight.dp)
                                .clip(RoundedCornerShape(4.dp)).background(Accent),
                        )
                        Text("${i + 1}", fontSize = 12.sp)
                    }
                }
            }
        }

        if (vm.pair.isNotBlank() && vm.partnerName != null) {
            val pw = vm.partner.values.filter { it.status == "watched" }
            val pr = pw.mapNotNull { it.rating }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vm.partnerName ?: "Партнёр", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("${pw.size}", "Просмотрено")
                    StatCard(if (pr.isEmpty()) "–" else f1(pr.average()), "Средняя оценка")
                }
            }
        }
    }
}

// ============================================================ TOGETHER

@Composable
fun TogetherTab(vm: AppVM) {
    if (vm.pair.isBlank()) {
        Hint("Сначала подключите пару: раздел «Ещё» → «Создать пару» (или введите код партнёра).")
        return
    }
    val mine = vm.entries
    val theirs = vm.partner
    val pname = vm.partnerName ?: "Партнёр"
    val bothWant = mine.values.filter { it.status == "want" && theirs[it.id]?.status == "want" }
    val theirWant = theirs.values.filter { it.status == "want" && mine[it.id] == null }
    val bothWatched = mine.values.filter { it.status == "watched" && theirs[it.id]?.status == "watched" }
    var pick by remember { mutableStateOf<Entry?>(null) }
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (vm.partnerName == null) {
            item(span = full) {
                Text(
                    "Партнёр ещё не подключился. Код пары: ${vm.pair}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(span = full) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Хотите оба (${bothWant.size})",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                if (bothWant.isNotEmpty()) {
                    Button(onClick = { pick = bothWant.random() }) { Text("Случайный") }
                }
            }
        }
        items(bothWant, key = { "bw" + it.id }) { e ->
            PosterCard(e.title, e.poster, e.year, null, null, true) { vm.open(e.toItem()) }
        }

        item(span = full) {
            Text("$pname хочет, вы ещё нет (${theirWant.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(theirWant, key = { "tw" + it.id }) { e ->
            PosterCard(e.title, e.poster, e.year, null, null, true) { vm.open(e.toItem()) }
        }

        item(span = full) {
            Text("Смотрели оба (${bothWatched.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(bothWatched, key = { "ww" + it.id }) { e ->
            val a = e.rating?.toString() ?: "–"
            val b = theirs[e.id]?.rating?.toString() ?: "–"
            PosterCard(e.title, e.poster, e.year, "$a / $b", null, true) { vm.open(e.toItem()) }
        }
    }

    pick?.let { e ->
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text("Смотрим сегодня?") },
            text = { Text(e.title + (e.year?.let { " ($it)" } ?: "")) },
            confirmButton = { TextButton(onClick = { pick = null; vm.open(e.toItem()) }) { Text("Открыть") } },
            dismissButton = { TextButton(onClick = { pick = bothWant.random() }) { Text("Другой") } },
        )
    }
}

// ============================================================ SETTINGS

@Composable
fun SettingsTab(vm: AppVM) {
    val clipboard = LocalClipboardManager.current
    var joinCode by remember { mutableStateOf("") }
    var pendingStatus by remember { mutableStateOf("watched") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(uri, pendingStatus)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Пара", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = vm.myName, onValueChange = { vm.setName(it) },
            label = { Text("Ваше имя") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        if (vm.pair.isBlank()) {
            Button(onClick = { vm.createPair() }, modifier = Modifier.fillMaxWidth()) { Text("Создать пару") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = joinCode, onValueChange = { joinCode = it },
                    label = { Text("Код партнёра") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { vm.joinPair(joinCode) }) { Text("Войти") }
            }
        } else {
            Text("Код пары: ${vm.pair}", style = MaterialTheme.typography.titleMedium, color = Accent)
            Text(
                if (vm.partnerName != null) "Подключён: ${vm.partnerName}" else "Ждём партнёра: отправьте ему этот код",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(vm.pair)) }) { Text("Скопировать код") }
                TextButton(onClick = { vm.leavePair() }) { Text("Выйти из пары") }
            }
        }
        vm.syncMsg?.let { Text(it, color = Danger) }

        Spacer(Modifier.height(8.dp))
        Text("Импорт из Must / IMDb", style = MaterialTheme.typography.titleLarge)
        Text(
            "Выгрузите свои списки из Must в CSV (см. README) или скачайте CSV из IMDb, " +
                "затем выберите файл. Фильмы подберутся по названию и году.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { pendingStatus = "watched"; launcher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Импорт CSV: просмотрено") }
        OutlinedButton(
            onClick = { pendingStatus = "want"; launcher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Импорт CSV: хочу посмотреть") }
        vm.importMsg?.let { Text(it, color = Accent) }

        val warnings = listOfNotNull(
            if (!Tmdb.configured) "Не задан ключ TMDB — поиск не заработает." else null,
            if (!Omdb.configured) "Не задан ключ OMDb — рейтинг IMDb не будет показываться." else null,
            if (!Sync.configured) "Не настроен Firebase — пара не будет синхронизироваться." else null,
        )
        if (warnings.isNotEmpty() && vm.keysVersion >= 0) { // keysVersion — чтобы список обновился после загрузки ключей
            Spacer(Modifier.height(8.dp))
            Text("Настройка", style = MaterialTheme.typography.titleLarge)
            warnings.forEach { Text(it, color = Danger) }
            Text("Подробности — в README репозитория.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================ DETAIL

@Composable
fun RatingLine(source: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("★ ", color = Star, fontWeight = FontWeight.Bold)
        Text(value, fontWeight = FontWeight.Bold)
        Text("  $source", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ReviewCard(r: Review) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { expanded = !expanded }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                r.author, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            r.rating?.let { PosterBadge(f1(it), true) }
        }
        r.date?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            r.text,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp,
        )
        Text(if (expanded) "Свернуть" else "Читать полностью", fontSize = 12.sp, color = Accent)
    }
}

@Composable
fun DetailScreen(vm: AppVM, item: MediaItem) {
    val d = vm.detail?.takeIf { it.item.id == item.id }
    val shown = d?.item ?: item
    val entry = vm.entries[item.id]
    val partnerEntry = vm.partner[item.id]
    var showRate by remember { mutableStateOf(false) }
    val want = entry?.status == "want"
    val watched = entry?.status == "watched"
    val watching = entry?.status == "watching"
    val isSeries = shown.kind != "movie"
    val watchedEps = entry?.epSet() ?: emptySet()
    var season by remember(item.id) { mutableIntStateOf(1) }
    val curSeason = d?.seasons?.let { l -> l.firstOrNull { it.number == season } ?: l.firstOrNull() }
    LaunchedEffect(item.id, d?.item?.id, curSeason?.number) {
        if (d != null && curSeason != null && shown.kind == "tv") vm.loadSeason(shown, curSeason.number, curSeason.count)
    }

    Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(shown.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Row {
                        Poster(shown.poster, shown.title, Modifier.width(130.dp).aspectRatio(2f / 3f))
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(shown.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            shown.original?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(
                                listOfNotNull(shown.year, kindLabel(shown.kind).ifBlank { null }).joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            d?.extra?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            d?.genres?.let { Text(it, fontSize = 13.sp) }
                            Spacer(Modifier.height(4.dp))
                            d?.imdb?.let { RatingLine("IMDb", it + (d.imdbVotes?.let { v -> "  ($v)" } ?: "")) }
                            d?.tmdbRating?.let { RatingLine("TMDB", f1(it)) }
                            d?.shikiRating?.let { RatingLine("Shikimori", f1(it)) }
                        }
                    }
                }
                item {
                    if (isSeries) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { vm.setSeriesStatus(shown, d, if (want) null else "want", null) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (want) Accent else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = Color.White,
                                ),
                            ) { Text("Хочу", maxLines = 1, fontSize = 14.sp) }
                            Button(
                                onClick = { vm.setSeriesStatus(shown, d, if (watching) null else "watching", null) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (watching) Accent else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = Color.White,
                                ),
                            ) { Text("Смотрю", maxLines = 1, fontSize = 14.sp) }
                            Button(
                                onClick = { showRate = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (watched) Accent else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text(
                                    if (watched) "Посмотрел" + (entry?.rating?.let { " ★$it" } ?: "") else "Посмотрел",
                                    maxLines = 1, fontSize = 14.sp,
                                )
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.setStatus(shown, if (want) null else "want", null, d?.runtimeMin) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (want) Accent else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = Color.White,
                                ),
                            ) { Text(if (want) "В списке «Хочу»" else "Хочу посмотреть") }
                            Button(
                                onClick = { showRate = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (watched) Accent else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = Color.White,
                                ),
                            ) { Text(if (watched) "Посмотрел" + (entry?.rating?.let { " ★ $it" } ?: "") else "Посмотрел") }
                        }
                    }
                    if (entry != null) {
                        TextButton(onClick = { vm.setStatus(shown, null) }) { Text("Убрать из моих списков") }
                    }
                }
                if (partnerEntry != null) {
                    item {
                        val st = when (partnerEntry.status) {
                            "want" -> "хочет посмотреть"
                            "watching" -> "смотрит" + (partnerEntry.total?.let { " (${partnerEntry.epSet().size} из $it)" } ?: "")
                            else -> "посмотрел" + (partnerEntry.rating?.let { ", оценка $it" } ?: "")
                        }
                        Text("${vm.partnerName ?: "Партнёр"}: $st", color = Star)
                    }
                }
                if (isSeries && d != null && curSeason != null) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            d.seasons.forEach { sn ->
                                Pill(
                                    if (shown.kind == "anime") "Серии" else "Сезон ${sn.number}",
                                    sn.number == curSeason.number,
                                ) { season = sn.number }
                            }
                        }
                    }
                    val epList = if (shown.kind == "anime") {
                        (1..curSeason.count).map { Episode(it, "Серия $it", null) }
                    } else vm.seasonCache["${shown.id}#${curSeason.number}"]
                    if (epList == null) {
                        item { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    } else {
                        items(epList.chunked(25)) { chunk ->
                            Column {
                                chunk.forEach { ep ->
                                    val key = "${curSeason.number}x${ep.num}"
                                    EpisodeRow(
                                        ep, key in watchedEps,
                                        onClick = { vm.markEpisodes(shown, d, listOf(key), key !in watchedEps) },
                                        onLong = {
                                            val upTo = d.seasons.filter { it.number < curSeason.number }
                                                .flatMap { sn -> (1..sn.count).map { "${sn.number}x$it" } } +
                                                (1..ep.num).map { "${curSeason.number}x$it" }
                                            vm.markEpisodes(shown, d, upTo, true)
                                        },
                                    )
                                }
                            }
                        }
                        item {
                            val seasonKeys = (1..curSeason.count).map { "${curSeason.number}x$it" }
                            val allDone = seasonKeys.all { it in watchedEps }
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Pill(
                                    if (allDone) "Просмотрены все эпизоды" else "Отметить всё просмотренным",
                                    true,
                                ) { vm.markEpisodes(shown, d, seasonKeys, !allDone) }
                                Text(
                                    "Нажмите на серию, чтобы отметить. Долгое нажатие — отметить всё до этой серии.",
                                    fontSize = 11.sp, textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    when {
                        vm.detailLoading && d == null -> Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                        vm.detailError != null && d == null -> Text(vm.detailError ?: "", color = Danger)
                        else -> Text(shown.overview ?: "Описание отсутствует.", fontSize = 15.sp)
                    }
                }
                if (d != null && d.similar.isNotEmpty()) {
                    item { Text("Похожее", style = MaterialTheme.typography.titleMedium) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(d.similar, key = { it.id }) { s ->
                                Column(Modifier.width(110.dp).clickable { vm.open(s) }) {
                                    Box {
                                        Poster(s.poster, s.title, Modifier.width(110.dp).aspectRatio(2f / 3f))
                                        s.rating?.let {
                                            PosterBadge(f1(it), true, Modifier.align(Alignment.TopEnd).padding(5.dp))
                                        }
                                    }
                                    Text(
                                        s.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (d != null && d.reviews.isNotEmpty()) {
                    item { Text("Отзывы", style = MaterialTheme.typography.titleMedium) }
                    items(d.reviews) { r -> ReviewCard(r) }
                }
            }
        }
    }

    if (showRate) {
        AlertDialog(
            onDismissRequest = { showRate = false },
            title = { Text("Ваша оценка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..10).chunked(5).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { n ->
                                OutlinedButton(
                                    onClick = {
                                        if (isSeries) vm.setSeriesStatus(shown, d, "watched", n)
                                        else vm.setStatus(shown, "watched", n, d?.runtimeMin)
                                        showRate = false
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(40.dp),
                                ) { Text("$n") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isSeries) vm.setSeriesStatus(shown, d, "watched", null)
                    else vm.setStatus(shown, "watched", null, d?.runtimeMin)
                    showRate = false
                }) {
                    Text("Без оценки")
                }
            },
            dismissButton = { TextButton(onClick = { showRate = false }) { Text("Отмена") } },
        )
    }
}
