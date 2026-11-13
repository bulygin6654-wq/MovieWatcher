@file:OptIn(ExperimentalMaterial3Api::class)

package com.moviewatcher.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

val Coral = Color(0xFFFF6B6B)
val Amber = Color(0xFFFFC857)

@Composable
fun MWTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Coral,
            onPrimary = Color(0xFF1A0B0B),
            secondary = Amber,
            background = Color(0xFF14101F),
            onBackground = Color(0xFFEDE9F7),
            surface = Color(0xFF1C1730),
            onSurface = Color(0xFFEDE9F7),
            surfaceVariant = Color(0xFF2A2342),
            onSurfaceVariant = Color(0xFFB9B1D0),
        ),
        content = content,
    )
}

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Search, null) }, label = { Text("Поиск") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Person, null) }, label = { Text("Мои") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Вместе") },
                )
                NavigationBarItem(
                    selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Ещё") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> SearchTab(vm)
                1 -> MyTab(vm)
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
            .clip(RoundedCornerShape(8.dp))
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

@Composable
fun ItemRow(item: MediaItem, badge: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(item.poster, item.title, Modifier.width(56.dp).height(84.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            val sub = listOfNotNull(item.year, kindLabel(item.kind).ifBlank { null }).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.original != null) {
                Text(
                    item.original, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (badge != null) {
            Text(badge, color = Coral, style = MaterialTheme.typography.titleSmall)
        } else if (item.rating != null) {
            Text("★ " + String.format("%.1f", item.rating), color = Amber, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ============================================================ SEARCH

@Composable
fun SearchTab(vm: AppVM) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.onQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            placeholder = { Text("Фильм, сериал или аниме") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Default.Close, null) }
                }
            },
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "Всё", "movie" to "Фильмы", "tv" to "Сериалы", "anime" to "Аниме").forEach { (k, l) ->
                FilterChip(selected = vm.filter == k, onClick = { vm.onFilter(k) }, label = { Text(l) })
            }
        }

        if (vm.query.isBlank()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Подборка для вас", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.loadRecs() }) { Icon(Icons.Default.Refresh, null) }
            }
            if (vm.recsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.recs.isEmpty() && !vm.recsLoading) {
                Hint("Отметьте несколько просмотренных фильмов с оценкой — здесь появятся похожие рекомендации для вас обоих.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.recs, key = { it.id }) { ItemRow(it) { vm.open(it) } }
                }
            }
        } else {
            if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            val err = vm.searchError
            if (vm.results.isEmpty() && err != null && !vm.searching) {
                Hint(err)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.results, key = { it.id }) { ItemRow(it) { vm.open(it) } }
                }
            }
        }
    }
}

// ============================================================ MY LISTS

@Composable
fun MyTab(vm: AppVM) {
    var sub by remember { mutableIntStateOf(0) }
    val want = vm.entries.values.filter { it.status == "want" }.sortedByDescending { it.ts }
    val watched = vm.entries.values.filter { it.status == "watched" }.sortedByDescending { it.ts }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = sub, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("Хочу (${want.size})") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("Просмотрено (${watched.size})") })
        }
        val list = if (sub == 0) want else watched
        if (list.isEmpty()) {
            Hint(if (sub == 0) "Пока пусто. Найдите фильм и нажмите «Хочу посмотреть»." else "Пока пусто. Отметьте просмотренное или импортируйте из Must в разделе «Ещё».")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(list, key = { it.id }) { e ->
                    ItemRow(e.toItem(), badge = e.rating?.let { "★ $it" }) { vm.open(e.toItem()) }
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

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (vm.partnerName == null) {
            item {
                Text(
                    "Партнёр ещё не подключился. Код пары: ${vm.pair}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Хотите оба (${bothWant.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (bothWant.isNotEmpty()) {
                    Button(onClick = { pick = bothWant.random() }) { Text("Случайный") }
                }
            }
        }
        items(bothWant, key = { "bw" + it.id }) { e -> ItemRow(e.toItem()) { vm.open(e.toItem()) } }

        item {
            Text(
                "$pname хочет, вы ещё нет (${theirWant.size})",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            )
        }
        items(theirWant, key = { "tw" + it.id }) { e -> ItemRow(e.toItem()) { vm.open(e.toItem()) } }

        item {
            Text(
                "Смотрели оба (${bothWatched.size})",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            )
        }
        items(bothWatched, key = { "ww" + it.id }) { e ->
            val a = e.rating?.toString() ?: "–"
            val b = theirs[e.id]?.rating?.toString() ?: "–"
            ItemRow(e.toItem(), badge = "$a / $b") { vm.open(e.toItem()) }
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
            Text("Код пары: ${vm.pair}", style = MaterialTheme.typography.titleMedium, color = Amber)
            Text(
                if (vm.partnerName != null) "Подключён: ${vm.partnerName}" else "Ждём партнёра: отправьте ему этот код",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(vm.pair)) }) { Text("Скопировать код") }
                TextButton(onClick = { vm.leavePair() }) { Text("Выйти из пары") }
            }
        }
        vm.syncMsg?.let { Text(it, color = Coral) }

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
        vm.importMsg?.let { Text(it, color = Amber) }

        val warnings = listOfNotNull(
            if (!Tmdb.configured) "Не задан ключ TMDB — поиск не заработает." else null,
            if (!Omdb.configured) "Не задан ключ OMDb — рейтинг IMDb не будет показываться." else null,
            if (!Sync.configured) "Не настроен Firebase — пара не будет синхронизироваться." else null,
        )
        if (warnings.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Настройка", style = MaterialTheme.typography.titleLarge)
            warnings.forEach { Text(it, color = Coral) }
            Text("Подробности — в README репозитория.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================ DETAIL

@Composable
fun DetailScreen(vm: AppVM, item: MediaItem) {
    val d = vm.detail?.takeIf { it.item.id == item.id }
    val shown = d?.item ?: item
    val entry = vm.entries[item.id]
    val partnerEntry = vm.partner[item.id]
    var showRate by remember { mutableStateOf(false) }
    val want = entry?.status == "want"
    val watched = entry?.status == "watched"

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(shown.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row {
                    Poster(shown.poster, shown.title, Modifier.width(130.dp).height(195.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(shown.title, style = MaterialTheme.typography.titleLarge)
                        shown.original?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            listOfNotNull(shown.year, kindLabel(shown.kind).ifBlank { null }).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        d?.extra?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        d?.genres?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(4.dp))
                        d?.imdb?.let {
                            Text(
                                "IMDb ★ $it" + (d.imdbVotes?.let { v -> "  ($v)" } ?: ""),
                                color = Amber, style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        d?.tmdbRating?.let {
                            Text("TMDB ★ " + String.format("%.1f", it), color = Amber, style = MaterialTheme.typography.titleSmall)
                        }
                        d?.shikiRating?.let {
                            Text("Shikimori ★ " + String.format("%.1f", it), color = Amber, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.setStatus(shown, if (want) null else "want") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (want) Coral else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (want) Color.Black else MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text(if (want) "В списке «Хочу»" else "Хочу посмотреть") }
                    Button(
                        onClick = { showRate = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (watched) Coral else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (watched) Color.Black else MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text(if (watched) "Посмотрел" + (entry?.rating?.let { " ★ $it" } ?: "") else "Посмотрел") }
                }
                if (entry != null) {
                    TextButton(onClick = { vm.setStatus(shown, null) }) { Text("Убрать из моих списков") }
                }
            }
            if (partnerEntry != null) {
                item {
                    val st = if (partnerEntry.status == "want") "хочет посмотреть"
                    else "посмотрел" + (partnerEntry.rating?.let { ", оценка $it" } ?: "")
                    Text("${vm.partnerName ?: "Партнёр"}: $st", color = Amber)
                }
            }
            item {
                when {
                    vm.detailLoading && d == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    vm.detailError != null && d == null -> Text(vm.detailError ?: "", color = Coral)
                    else -> Text(shown.overview ?: "Описание отсутствует.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (d != null && d.similar.isNotEmpty()) {
                item { Text("Похожее", style = MaterialTheme.typography.titleMedium) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(d.similar, key = { it.id }) { s ->
                            Column(Modifier.width(110.dp).clickable { vm.open(s) }) {
                                Poster(s.poster, s.title, Modifier.width(110.dp).height(165.dp))
                                Text(
                                    s.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
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
                                    onClick = { vm.setStatus(shown, "watched", n); showRate = false },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(40.dp),
                                ) { Text("$n") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setStatus(shown, "watched", null); showRate = false }) { Text("Без оценки") }
            },
            dismissButton = { TextButton(onClick = { showRate = false }) { Text("Отмена") } },
        )
    }
}
