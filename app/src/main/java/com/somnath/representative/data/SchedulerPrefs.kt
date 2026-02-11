package com.somnath.representative.data

import android.content.Context
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

    fun incrementErrors(context: Context) {
        val pref = prefs(context)
        val current = pref.getInt(KEY_ERRORS_COUNT, 0)
        pref.edit().putInt(KEY_ERRORS_COUNT, current + 1).apply()
    }

    fun recordScheduledCycle(context: Context) {
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
            .putString(KEY_LAST_ACTION_MESSAGE, "Scheduled cycle ran")
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
