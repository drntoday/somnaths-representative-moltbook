package com.somnath.representative.data

import android.content.Context
import com.somnath.representative.BuildConfig
import java.time.LocalDate

private const val PREFS_NAME = "somnath_rep_prefs"

object SchedulerPrefs {
    private const val KEY_CHARGING_ONLY = "chargingOnly"
    private const val KEY_WIFI_ONLY = "wifiOnly"
    private const val KEY_LAST_ACTION_TIME = "lastActionTime"
    private const val KEY_LAST_ACTION_MESSAGE = "lastActionMessage"
    private const val KEY_ERRORS_COUNT = "errorsCount"
    private const val KEY_ACTIONS_TODAY_COUNT = "actionsTodayCount"
    private const val KEY_ACTIONS_TODAY_DATE = "actionsTodayDate"
    private const val KEY_SCHEDULER_ENABLED = "schedulerEnabled"
    private const val KEY_ENABLE_DEBUG_TOOLS = "enable_debug_tools"
    private const val KEY_TOPIC_QUERY = "topicQuery"
    private const val KEY_AUTONOMOUS_MODE_ENABLED = "autonomousModeEnabled"

    data class HomeStatus(
        val schedulerEnabled: Boolean,
        val lastActionTime: Long,
        val lastActionMessage: String,
        val actionsTodayCount: Int,
        val errorsCount: Int
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isChargingOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHARGING_ONLY, true)

    fun isWifiOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIFI_ONLY, false)

    fun setSchedulerSettings(context: Context, chargingOnly: Boolean, wifiOnly: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_CHARGING_ONLY, chargingOnly)
            .putBoolean(KEY_WIFI_ONLY, wifiOnly)
            .apply()
    }

    fun setSchedulerEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCHEDULER_ENABLED, enabled).apply()
    }

    fun isDebugToolsEnabled(context: Context): Boolean =
        if (BuildConfig.DEBUG) {
            prefs(context).getBoolean(KEY_ENABLE_DEBUG_TOOLS, false)
        } else {
            prefs(context).edit().putBoolean(KEY_ENABLE_DEBUG_TOOLS, false).apply()
            false
        }

    fun setDebugToolsEnabled(context: Context, enabled: Boolean) {
        val value = if (BuildConfig.DEBUG) enabled else false
        prefs(context).edit().putBoolean(KEY_ENABLE_DEBUG_TOOLS, value).apply()
    }

    fun setTopicQuery(context: Context, topicQuery: String) {
        prefs(context).edit().putString(KEY_TOPIC_QUERY, topicQuery).apply()
    }

    fun getTopicQuery(context: Context): String =
        prefs(context).getString(KEY_TOPIC_QUERY, "") ?: ""

    fun isAutonomousModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTONOMOUS_MODE_ENABLED, false)

    fun setAutonomousModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTONOMOUS_MODE_ENABLED, enabled).apply()
    }

    fun incrementErrors(context: Context) {
        val pref = prefs(context)
        val current = pref.getInt(KEY_ERRORS_COUNT, 0)
        pref.edit().putInt(KEY_ERRORS_COUNT, current + 1).apply()
    }

    fun updateLastActionMessage(context: Context, message: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_ACTION_TIME, System.currentTimeMillis())
            .putString(KEY_LAST_ACTION_MESSAGE, message)
            .apply()
    }

    fun recordScheduledCycle(context: Context, message: String = "Scheduled cycle ran") {
        val pref = prefs(context)
        val today = LocalDate.now().toString()
        val storedDate = pref.getString(KEY_ACTIONS_TODAY_DATE, null)
        val count = if (storedDate == today) {
            pref.getInt(KEY_ACTIONS_TODAY_COUNT, 0) + 1
        } else {
            1
        }

        pref.edit()
            .putLong(KEY_LAST_ACTION_TIME, System.currentTimeMillis())
            .putString(KEY_LAST_ACTION_MESSAGE, message)
            .putString(KEY_ACTIONS_TODAY_DATE, today)
            .putInt(KEY_ACTIONS_TODAY_COUNT, count)
            .apply()
    }

    fun getHomeStatus(context: Context): HomeStatus {
        val pref = prefs(context)
        val today = LocalDate.now().toString()
        val storedDate = pref.getString(KEY_ACTIONS_TODAY_DATE, null)
        val count = if (storedDate == today) {
            pref.getInt(KEY_ACTIONS_TODAY_COUNT, 0)
        } else {
            0
        }

        return HomeStatus(
            schedulerEnabled = pref.getBoolean(KEY_SCHEDULER_ENABLED, false),
            lastActionTime = pref.getLong(KEY_LAST_ACTION_TIME, 0L),
            lastActionMessage = pref.getString(KEY_LAST_ACTION_MESSAGE, "") ?: "",
            actionsTodayCount = count,
            errorsCount = pref.getInt(KEY_ERRORS_COUNT, 0)
        )
    }
}
