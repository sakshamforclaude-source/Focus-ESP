package com.example.focusesp

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Persists the user's daily screen-time goal so the Screen Time dashboard can
 * compare today's measured usage against it and show the remaining allowance.
 *
 * A value of 0 means "no goal set" — the UI presents an explicit empty goal
 * state rather than assuming a default allowance.
 */
object ScreenTimeStore {

    private const val KEY_DAILY_GOAL_MINUTES = "screen_time_daily_goal_minutes"

    val dailyGoalMinutes = mutableStateOf(0)

    fun init(context: Context) {
        LocalStore.init(context)
        dailyGoalMinutes.value = LocalStore.getInt(KEY_DAILY_GOAL_MINUTES, 0)
    }

    fun setDailyGoalMinutes(minutes: Int) {
        dailyGoalMinutes.value = minutes.coerceAtLeast(0)
        LocalStore.putInt(KEY_DAILY_GOAL_MINUTES, dailyGoalMinutes.value)
    }

    fun hasGoal(): Boolean = dailyGoalMinutes.value > 0
}
