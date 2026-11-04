package com.example.focusesp

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Schedule(
    val id: String,
    val enabled: Boolean,
    val startMinutes: Int,
    val endMinutes: Int,
    val days: Set<Int>,
    val packages: Set<String>
)

object ScheduleStore {

    private const val KEY = "schedules"

    val schedules = mutableStateListOf<Schedule>()

    fun init(context: Context) {
        LocalStore.init(context)
        load()
    }

    private fun load() {
        schedules.clear()
        try {
            val array = JSONArray(LocalStore.getString(KEY, "[]"))
            for (i in 0 until array.length()) {
                schedules.add(parse(array.getJSONObject(i)))
            }
        } catch (_: Exception) {
            schedules.clear()
        }
    }

    private fun parse(o: JSONObject): Schedule = Schedule(
        id = o.optString("id", UUID.randomUUID().toString()),
        enabled = o.optBoolean("enabled", true),
        startMinutes = o.optInt("start", 9 * 60),
        endMinutes = o.optInt("end", 17 * 60),
        days = intSet(o.optJSONArray("days")),
        packages = stringSet(o.optJSONArray("packages"))
    )

    private fun intSet(array: JSONArray?): Set<Int> {
        if (array == null) return emptySet()
        return (0 until array.length()).map { array.getInt(it) }.toSet()
    }

    private fun stringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return (0 until array.length()).map { array.getString(it) }.toSet()
    }

    private fun persist() {
        val array = JSONArray()
        schedules.forEach { s ->
            val o = JSONObject()
            o.put("id", s.id)
            o.put("enabled", s.enabled)
            o.put("start", s.startMinutes)
            o.put("end", s.endMinutes)
            o.put("days", JSONArray(s.days.toList()))
            o.put("packages", JSONArray(s.packages.toList()))
            array.put(o)
        }
        LocalStore.putString(KEY, array.toString())
    }

    fun add(schedule: Schedule) {
        schedules.add(schedule)
        persist()
    }

    fun update(schedule: Schedule) {
        val index = schedules.indexOfFirst { it.id == schedule.id }
        if (index >= 0) {
            schedules[index] = schedule
            persist()
        }
    }

    fun remove(id: String) {
        schedules.removeAll { it.id == id }
        persist()
    }
}
