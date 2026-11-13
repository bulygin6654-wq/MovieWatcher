package com.moviewatcher.app

import kotlin.math.roundToInt

/** Импорт CSV: выгрузка из Must (формат Letterboxd) и экспорт IMDb. */
object CsvImport {

    data class Row(
        val tmdbId: String?,
        val imdbId: String?,
        val title: String?,
        val year: String?,
        val rating: Int?,
    )

    fun parse(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQuotes = false
                } else sb.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { cur.add(sb.toString()); sb.setLength(0) }
                '\r' -> {}
                '\n' -> {
                    cur.add(sb.toString()); sb.setLength(0)
                    if (cur.any { it.isNotBlank() }) rows.add(cur)
                    cur = ArrayList()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || cur.isNotEmpty()) {
            cur.add(sb.toString())
            if (cur.any { it.isNotBlank() }) rows.add(cur)
        }
        return rows
    }

    fun rows(text: String): List<Row> {
        val all = parse(text)
        if (all.size < 2) return emptyList()
        val header = all[0].map { it.trim().lowercase() }
        fun idx(vararg names: String): Int = names.map { header.indexOf(it) }.firstOrNull { it >= 0 } ?: -1
        val iTmdb = idx("tmdbid", "tmdb id")
        val iImdb = idx("imdbid", "imdb id", "const")
        val iTitle = idx("title", "name", "original title")
        val iYear = idx("year")
        val iRating10 = idx("rating10", "your rating")
        val iRating = idx("rating")

        fun cell(r: List<String>, i: Int): String? = if (i >= 0 && i < r.size) r[i].trim().ifBlank { null } else null

        return all.drop(1).map { r ->
            var rating: Int? = null
            cell(r, iRating10)?.toDoubleOrNull()?.let { rating = it.roundToInt() }
            if (rating == null) {
                cell(r, iRating)?.toDoubleOrNull()?.let { v ->
                    rating = if (v <= 5.0) (v * 2).roundToInt() else v.roundToInt()
                }
            }
            Row(
                tmdbId = cell(r, iTmdb)?.takeIf { it.all(Char::isDigit) },
                imdbId = cell(r, iImdb)?.takeIf { it.startsWith("tt") },
                title = cell(r, iTitle),
                year = cell(r, iYear),
                rating = rating?.coerceIn(1, 10),
            )
        }.filter { it.tmdbId != null || it.imdbId != null || it.title != null }
    }

    /** Ищет фильм/сериал в TMDB (блокирующий вызов). */
    fun match(row: Row): MediaItem? {
        row.tmdbId?.toIntOrNull()?.let { id ->
            runCatching { return Tmdb.basic("movie", id) }
        }
        row.imdbId?.let { imdb ->
            runCatching { Tmdb.findByImdb(imdb) }.getOrNull()?.let { return it }
        }
        val title = row.title ?: return null
        val found = Tmdb.search(title)
        if (found.isEmpty()) return null
        val y = row.year?.toIntOrNull()
        if (y != null) {
            found.firstOrNull { it.year?.toIntOrNull()?.let { fy -> kotlin.math.abs(fy - y) <= 1 } == true }
                ?.let { return it }
        }
        return found.first()
    }
}
