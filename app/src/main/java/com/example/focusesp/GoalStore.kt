package com.example.focusesp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Goal(
    val id: String,
    val text: String,
    val deadline: Long?,
    val done: Boolean,
    val reminderEnabled: Boolean = false
)

object GoalStore {

    private const val KEY = "goals"

    val goals = mutableStateListOf<Goal>()

    fun init(context: Context) {
        LocalStore.init(context)
        load()
    }

    private fun load() {
        goals.clear()
        try {
            val array = JSONArray(LocalStore.getString(KEY, "[]"))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                goals.add(
                    Goal(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        text = o.optString("text", ""),
                        deadline = if (o.has("deadline") && !o.isNull("deadline")) o.getLong("deadline") else null,
                        done = o.optBoolean("done", false),
                        reminderEnabled = o.optBoolean("reminderEnabled", false)
                    )
                )
            }
        } catch (_: Exception) {
            goals.clear()
        }
    }

    private fun persist() {
        val array = JSONArray()
        goals.forEach { g ->
            val o = JSONObject()
            o.put("id", g.id)
            o.put("text", g.text)
            if (g.deadline != null) o.put("deadline", g.deadline) else o.put("deadline", JSONObject.NULL)
            o.put("done", g.done)
            o.put("reminderEnabled", g.reminderEnabled)
            array.put(o)
        }
        LocalStore.putString(KEY, array.toString())
    }

    fun add(goal: Goal) {
        goals.add(goal)
        persist()
    }

    fun update(goal: Goal) {
        val index = goals.indexOfFirst { it.id == goal.id }
        if (index >= 0) {
            goals[index] = goal
            persist()
        }
    }

    fun setDone(id: String, done: Boolean) {
        val index = goals.indexOfFirst { it.id == id }
        if (index >= 0) {
            goals[index] = goals[index].copy(done = done)
            persist()
        }
    }

    fun setReminderEnabled(context: Context, id: String, enabled: Boolean) {
        val index = goals.indexOfFirst { it.id == id }
        if (index < 0) return
        val updated = goals[index].copy(reminderEnabled = enabled)
        goals[index] = updated
        persist()
        if (enabled) {
            scheduleReminder(context, updated)
        } else {
            cancelReminder(context, updated)
        }
    }

    fun remove(id: String) {
        goals.removeAll { it.id == id }
        persist()
    }

    fun rescheduleAll(context: Context) {
        goals.toList().forEach { goal ->
            if (goal.reminderEnabled) scheduleReminder(context, goal)
        }
    }

    fun scheduleReminder(context: Context, goal: Goal) {
        val deadline = goal.deadline ?: return
        if (!goal.reminderEnabled || goal.done) return
        if (deadline <= System.currentTimeMillis()) return
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = reminderIntent(context, goal)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
        } else {
            @Suppress("DEPRECATION")
            manager.setExact(AlarmManager.RTC_WAKEUP, deadline, pending)
        }
    }

    fun cancelReminder(context: Context, goal: Goal) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(reminderIntent(context, goal))
    }

    private fun reminderIntent(context: Context, goal: Goal): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_GOAL_ID, goal.id)
            putExtra(ReminderReceiver.EXTRA_GOAL_TEXT, goal.text)
        }
        return PendingIntent.getBroadcast(
            context,
            goal.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
