package com.din.htmltomp4.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the "Recent conversions" list across app restarts. Deliberately
 * simple (SharedPreferences + hand-rolled JSON) rather than pulling in a
 * database dependency for what's just a small list of file references.
 */
class HistoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("conversion_history", Context.MODE_PRIVATE)

    fun load(): List<ConversionHistoryItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val type = try {
                    ConversionType.valueOf(o.optString("type", "HTML_TO_MP4"))
                } catch (t: Throwable) {
                    ConversionType.HTML_TO_MP4
                }
                ConversionHistoryItem(
                    id = o.optString("id"),
                    fileName = o.optString("fileName"),
                    thumbnailPath = o.optString("thumbnailPath").takeIf { it.isNotEmpty() },
                    outputPath = o.optString("outputPath"),
                    resolution = o.optString("resolution"),
                    durationSeconds = o.optInt("durationSeconds"),
                    createdAtMillis = o.optLong("createdAtMillis"),
                    type = type
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun save(items: List<ConversionHistoryItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val o = JSONObject()
            o.put("id", item.id)
            o.put("fileName", item.fileName)
            o.put("thumbnailPath", item.thumbnailPath ?: "")
            o.put("outputPath", item.outputPath)
            o.put("resolution", item.resolution)
            o.put("durationSeconds", item.durationSeconds)
            o.put("createdAtMillis", item.createdAtMillis)
            o.put("type", item.type.name)
            arr.put(o)
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_ITEMS = "items"
    }
}
