package com.kj2431.pielauncher.prefs

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import com.kj2431.pielauncher.ui.LanguageHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports every setting (edges, look, timing, ring info, language and the
 * slice list) to a JSON file the user picks, and imports it back. Whether the
 * launcher is switched on is left out, so an import never starts the overlay
 * by itself; permissions can't travel in a file at all.
 *
 * File layout:
 * ```
 * { "format": "pie-launcher-config", "version": 1, "appVersion": "0.7.5",
 *   "exportedAt": 1760945000000,
 *   "prefs": { "<key>": { "type": "int|long|float|bool|string|set", "value": ... } } }
 * ```
 */
object ConfigBackup {

    private const val FORMAT = "pie-launcher-config"
    private const val VERSION = 1

    /** Keys that describe this device's state rather than the user's configuration. */
    private val excluded = setOf(Prefs.KEY_ENABLED)

    class InvalidFileException : Exception()
    class TooNewException : Exception()

    /** Writes the configuration to [uri]; returns how many settings were saved. */
    fun export(ctx: Context, uri: Uri): Int {
        val all = PreferenceManager.getDefaultSharedPreferences(ctx).all.toMutableMap()
        // On Android 13+ the system can change the language behind our back; save what is in effect.
        all[Prefs.KEY_LANGUAGE] = LanguageHelper.current(ctx)

        val prefs = JSONObject()
        all.forEach { (key, value) ->
            if (key in excluded || value == null) return@forEach
            val (type, json) = when (value) {
                is Boolean -> "bool" to value
                is Int -> "int" to value
                is Long -> "long" to value
                is Float -> "float" to value.toDouble()
                is String -> "string" to value
                is Set<*> -> "set" to JSONArray(value.filterIsInstance<String>().sorted())
                else -> return@forEach
            }
            prefs.put(key, JSONObject().put("type", type).put("value", json))
        }
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("appVersion", runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull())
            .put("exportedAt", System.currentTimeMillis())
            .put("prefs", prefs)

        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) }
            ?: throw java.io.IOException("cannot open $uri")
        return prefs.length()
    }

    /**
     * Replaces the configuration with the one in [uri]. Everything is parsed
     * and checked before anything is written, so a bad file changes nothing.
     * Returns how many settings were restored.
     */
    fun import(ctx: Context, uri: Uri): Int {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw java.io.IOException("cannot open $uri")
        val root = runCatching { JSONObject(text) }.getOrNull() ?: throw InvalidFileException()
        if (root.optString("format") != FORMAT) throw InvalidFileException()
        if (root.optInt("version", 0) > VERSION) throw TooNewException()
        val prefs = root.optJSONObject("prefs") ?: throw InvalidFileException()

        val values = mutableMapOf<String, Any>()
        prefs.keys().forEach { key ->
            if (!key.startsWith("pref_") || key in excluded) return@forEach
            val entry = prefs.optJSONObject(key) ?: return@forEach
            val v = when (entry.optString("type")) {
                "bool" -> entry.optBoolean("value")
                "int" -> entry.optInt("value")
                "long" -> entry.optLong("value")
                "float" -> entry.optDouble("value").toFloat()
                "string" -> entry.optString("value")
                "set" -> entry.optJSONArray("value")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.toSet()
                }
                else -> null
            } ?: return@forEach
            values[key] = v
        }
        if (values.isEmpty()) throw InvalidFileException()

        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        val edit = sp.edit()
        // Settings missing from the file go back to their defaults.
        sp.all.keys.filter { it !in excluded }.forEach { edit.remove(it) }
        values.forEach { (key, v) ->
            @Suppress("UNCHECKED_CAST")
            when (v) {
                is Boolean -> edit.putBoolean(key, v)
                is Int -> edit.putInt(key, v)
                is Long -> edit.putLong(key, v)
                is Float -> edit.putFloat(key, v)
                is String -> edit.putString(key, v)
                is Set<*> -> edit.putStringSet(key, v as Set<String>)
            }
        }
        edit.commit()
        return values.size
    }
}
