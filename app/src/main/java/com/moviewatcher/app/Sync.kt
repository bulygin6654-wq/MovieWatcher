package com.moviewatcher.app

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** Локальное хранилище: список фильмов, настройки пары. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("mw", Context.MODE_PRIVATE)

    val uid: String = sp.getString("uid", null) ?: UUID.randomUUID().toString().also {
        sp.edit().putString("uid", it).apply()
    }

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v).apply()

    var pair: String
        get() = sp.getString("pair", "") ?: ""
        set(v) = sp.edit().putString("pair", v).apply()

    fun loadEntries(): Map<String, Entry> = Entry.listFromJson(sp.getString("entries", null))

    fun saveEntries(m: Map<String, Entry>) {
        sp.edit().putString("entries", Entry.listToJson(m)).apply()
    }
}

class Partner(val name: String, val entries: Map<String, Entry>)

/**
 * Синхронизация пары через Cloud Firestore REST API (без SDK и google-services.json).
 * Структура: pairs/{код}/members/{uid} → поля name и entries (JSON-строка).
 */
object Sync {
    private val project get() = BuildConfig.FIREBASE_PROJECT
    private val key get() = BuildConfig.FIREBASE_KEY
    val configured get() = project.isNotBlank() && key.isNotBlank()

    private fun base(code: String) =
        "https://firestore.googleapis.com/v1/projects/$project/databases/(default)/documents/pairs/$code/members"

    fun newCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun push(code: String, uid: String, name: String, entries: Map<String, Entry>) {
        if (!configured) throw IOException("Firebase не настроен")
        val url = "${base(code)}/$uid".toHttpUrl().newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("updateMask.fieldPaths", "name")
            .addQueryParameter("updateMask.fieldPaths", "entries")
            .build().toString()
        val body = JSONObject().put(
            "fields",
            JSONObject()
                .put("name", JSONObject().put("stringValue", name))
                .put("entries", JSONObject().put("stringValue", Entry.listToJson(entries))),
        )
        Net.send("PATCH", url, body.toString())
    }

    fun pull(code: String, myUid: String): Partner? {
        if (!configured) throw IOException("Firebase не настроен")
        val url = base(code).toHttpUrl().newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("pageSize", "10")
            .build().toString()
        val r = JSONObject(Net.get(url))
        for (d in r.optJSONArray("documents").objs()) {
            val uid = d.optString("name").substringAfterLast('/')
            if (uid == myUid) continue
            val f = d.optJSONObject("fields") ?: continue
            val name = f.optJSONObject("name")?.str("stringValue") ?: "Партнёр"
            val entries = Entry.listFromJson(f.optJSONObject("entries")?.str("stringValue"))
            return Partner(name, entries)
        }
        return null
    }
}
