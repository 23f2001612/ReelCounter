package com.reelcounter

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StatsStore {
    private const val PREFS = "reel_counter_stats"
    private const val KEY_ENABLED = "tracking_enabled"
    private const val KEY_TOTAL = "total_count"
    private const val KEY_PREFIX = "day_"
    private const val KEY_DIAGNOSTICS = "diagnostic_events"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun isTrackingEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)
    fun setTrackingEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply() }
    fun todayCount(context: Context): Int = prefs(context).getInt(KEY_PREFIX + todayKey(), 0)
    fun totalCount(context: Context): Int = prefs(context).getInt(KEY_TOTAL, 0)

    fun resetCounts(context: Context) {
        val p = prefs(context)
        val editor = p.edit().putInt(KEY_TOTAL, 0)
        p.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun recordReel(context: Context): Int {
        val p = prefs(context)
        val key = KEY_PREFIX + todayKey()
        val today = p.getInt(key, 0) + 1
        p.edit().putInt(key, today).putInt(KEY_TOTAL, p.getInt(KEY_TOTAL, 0) + 1).apply()
        return today
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