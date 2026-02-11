package com.somnath.representative.scheduler

import android.content.Context
import android.content.SharedPreferences

class SchedulerPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isChargingOnly(): Boolean = prefs.getBoolean(KEY_CHARGING_ONLY, true)

    fun setChargingOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHARGING_ONLY, enabled).apply()
    }

    fun isWifiOnly(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun isSchedulerEnabled(): Boolean = prefs.getBoolean(KEY_SCHEDULER_ENABLED, false)

    fun setSchedulerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCHEDULER_ENABLED, enabled).apply()
    }

    fun getLastActionAtMillis(): Long = prefs.getLong(KEY_LAST_ACTION_AT_MILLIS, 0L)

    fun setLastActionAtMillis(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_ACTION_AT_MILLIS, timestampMillis).apply()
    }

    fun getLastActionText(): String = prefs.getString(KEY_LAST_ACTION_TEXT, "—") ?: "—"

    fun setLastActionText(text: String) {
        prefs.edit().putString(KEY_LAST_ACTION_TEXT, text).apply()
    }

    fun getErrorCount(): Int = prefs.getInt(KEY_ERROR_COUNT, 0)

    fun incrementErrorCount() {
        prefs.edit().putInt(KEY_ERROR_COUNT, getErrorCount() + 1).apply()
    }

    fun getNextEligibleAtMillis(): Long = prefs.getLong(KEY_NEXT_ELIGIBLE_AT_MILLIS, 0L)

    fun setNextEligibleAtMillis(timestampMillis: Long) {
        prefs.edit().putLong(KEY_NEXT_ELIGIBLE_AT_MILLIS, timestampMillis).apply()
    }

    companion object {
        private const val PREFS_NAME = "somnath_rep_scheduler"
        private const val KEY_CHARGING_ONLY = "charging_only"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_SCHEDULER_ENABLED = "scheduler_enabled"
        private const val KEY_LAST_ACTION_AT_MILLIS = "last_action_at_millis"
        private const val KEY_LAST_ACTION_TEXT = "last_action_text"
        private const val KEY_ERROR_COUNT = "error_count"
        private const val KEY_NEXT_ELIGIBLE_AT_MILLIS = "next_eligible_at_millis"
    }
}
