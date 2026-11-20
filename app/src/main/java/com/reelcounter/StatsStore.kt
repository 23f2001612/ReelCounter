package com.reelcounter

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object StatsStore {
    private const val PREFS = "reel_counter_stats"
    private const val KEY_ENABLED = "tracking_enabled"
    private const val KEY_TOTAL = "total_count"
    private const val KEY_PREFIX = "day_"
    private const val KEY_DIAGNOSTICS = "diagnostic_events"
    private const val WATCH_PREFIX = "watch_"
    private const val SESSION_PREFIX = "sessions_"
    private const val KEY_TOTAL_WATCH = "total_watch_seconds"
    private const val KEY_TOTAL_SESSIONS = "total_sessions"
    private const val KEY_GOAL = "daily_goal_minutes"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun dateKey(date: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
    private fun dayKey(offset: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -offset)
        return dateKey(calendar.time)
    }

    fun isTrackingEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)
    fun setTrackingEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply() }
    fun todayCount(context: Context): Int = prefs(context).getInt(KEY_PREFIX + dateKey(), 0)
    fun totalCount(context: Context): Int = prefs(context).getInt(KEY_TOTAL, 0)
    fun todayWatchSeconds(context: Context): Long = prefs(context).getLong(WATCH_PREFIX + dateKey(), 0L)
    fun totalWatchSeconds(context: Context): Long = prefs(context).getLong(KEY_TOTAL_WATCH, 0L)
    fun todaySessions(context: Context): Int = prefs(context).getInt(SESSION_PREFIX + dateKey(), 0)
    fun totalSessions(context: Context): Int = prefs(context).getInt(KEY_TOTAL_SESSIONS, 0)
    fun dailyGoalMinutes(context: Context): Int = prefs(context).getInt(KEY_GOAL, 60)
    fun setDailyGoalMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_GOAL, minutes.coerceIn(5, 600)).apply()
    }

    fun startSession(context: Context) {
        val p = prefs(context)
        val key = SESSION_PREFIX + dateKey()
        p.edit().putInt(key, p.getInt(key, 0) + 1)
            .putInt(KEY_TOTAL_SESSIONS, p.getInt(KEY_TOTAL_SESSIONS, 0) + 1).apply()
    }

    fun recordWatchSecond(context: Context) {
        val p = prefs(context)
        val key = WATCH_PREFIX + dateKey()
        p.edit().putLong(key, p.getLong(key, 0L) + 1L)
            .putLong(KEY_TOTAL_WATCH, p.getLong(KEY_TOTAL_WATCH, 0L) + 1L).apply()
    }

    fun recordReel(context: Context): Int {
        val p = prefs(context)
        val key = KEY_PREFIX + dateKey()
        val today = p.getInt(key, 0) + 1
        p.edit().putInt(key, today).putInt(KEY_TOTAL, p.getInt(KEY_TOTAL, 0) + 1).apply()
        return today
    }

    fun weekCounts(context: Context): List<Pair<String, Int>> {
        val p = prefs(context)
        val labelFormat = SimpleDateFormat("EEE", Locale.getDefault())
        return (6 downTo 0).map { offset ->
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -offset)
            val date = calendar.time
            labelFormat.format(date) to p.getInt(KEY_PREFIX + dateKey(date), 0)
        }
    }

    fun weekWatchSeconds(context: Context): Long {
        val p = prefs(context)
        return (0..6).sumOf { p.getLong(WATCH_PREFIX + dayKey(it), 0L) }
    }

    fun history(context: Context): List<Triple<String, Int, Long>> {
        val p = prefs(context)
        val label = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        return (0..13).map { offset ->
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -offset)
            val date = calendar.time
            val key = dateKey(date)
            Triple(label.format(date), p.getInt(KEY_PREFIX + key, 0), p.getLong(WATCH_PREFIX + key, 0L))
        }
    }

    fun resetCounts(context: Context) {
        val p = prefs(context)
        val editor = p.edit().putInt(KEY_TOTAL, 0).putLong(KEY_TOTAL_WATCH, 0L)
            .putInt(KEY_TOTAL_SESSIONS, 0)
        p.all.keys.filter { it.startsWith(KEY_PREFIX) || it.startsWith(WATCH_PREFIX) || it.startsWith(SESSION_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    fun addDiagnostic(context: Context, message: String) {
        val p = prefs(context)
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val old = p.getString(KEY_DIAGNOSTICS, "") ?: ""
        val lines = (old.lines() + "$stamp $message").filter { it.isNotBlank() }.takeLast(45)
        p.edit().putString(KEY_DIAGNOSTICS, lines.joinToString("\n")).apply()
    }

    fun diagnostics(context: Context): String =
        prefs(context).getString(KEY_DIAGNOSTICS, "No Instagram events recorded yet.") ?: ""
}