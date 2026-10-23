package com.zoomy.wallpaper.util

import org.json.JSONArray
import org.json.JSONObject

object PresetManager {
    private const val KEY = "userPresets"

    fun loadAll(): MutableList<JSONObject> {
        val raw = PrefsStore.getInstance().getString(KEY, "")
        val out = mutableListOf<JSONObject>()
        if (raw.isBlank()) return out
        return try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("name").isNotBlank()) out.add(o)
            }
            out
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun persist(list: List<JSONObject>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        PrefsStore.getInstance().put(KEY, arr.toString())
    }

    fun save(name: String, data: JSONObject): Boolean {
        val list = loadAll()
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        // Override if name already exists
        val idx = list.indexOfFirst { it.optString("name").equals(trimmed, ignoreCase = true) }
        val obj = JSONObject(data.toString()).apply { put("name", trimmed) }
        if (idx >= 0) {
            list[idx] = obj
        } else {
            list.add(obj)
        }
        persist(list)
        return true
    }

    fun rename(oldName: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return false
        val list = loadAll()
        val idx = list.indexOfFirst { it.optString("name") == oldName }
        if (idx < 0) return false
        if (list.any { it.optString("name").equals(trimmed, ignoreCase = true) && it.optString("name") != oldName }) {
            return false // name taken
        }
        list[idx] = JSONObject(list[idx].toString()).apply { put("name", trimmed) }
        persist(list)
        return true
    }

    fun delete(name: String) {
        val list = loadAll().filterNot { it.optString("name") == name }
        persist(list)
    }

    fun find(name: String): JSONObject? =
        loadAll().firstOrNull { it.optString("name") == name }
}
