package com.example.focusesp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val goalId = intent.getStringExtra(EXTRA_GOAL_ID) ?: return
        AppInitializer.init(context)
        val goal = GoalStore.goals.firstOrNull { it.id == goalId }
        if (goal != null && (goal.done || !goal.reminderEnabled)) return
        val text = goal?.text ?: intent.getStringExtra(EXTRA_GOAL_TEXT) ?: "Goal deadline reached"
        Notifications.show(context, goalId.hashCode(), "FocusESP goal reminder", text)
        FocusState.addEvent("Reminder: $text")
    }

    companion object {
        const val EXTRA_GOAL_ID = "goal_id"
        const val EXTRA_GOAL_TEXT = "goal_text"
    }
}
