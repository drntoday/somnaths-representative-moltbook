package com.somnath.representative.data

import android.content.Context
import com.somnath.representative.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

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
    private const val KEY_EMERGENCY_STOP_ENABLED = "emergencyStopEnabled"
    private const val KEY_CONSECUTIVE_POST_FAILURES = "consecutivePostFailures"
    private const val KEY_NEXT_AUTO_POST_ALLOWED_AT = "nextAutoPostAllowedAt"
    private const val KEY_AUDIT_EVENTS_JSON = "auditEventsJson"
    private const val KEY_LAST_GENERATION_AT = "lastGenerationAt"
    private const val KEY_GENERATION_INTERVAL_MULTIPLIER = "generationIntervalMultiplier"
    private const val KEY_RECENT_CYCLE_OUTCOMES = "recentCycleOutcomes"
    private const val KEY_SELF_CHECK_STATUS = "selfCheckStatus"
    private const val KEY_SELF_CHECK_ISSUES = "selfCheckIssues"
    private const val KEY_M12_TOTAL_GENERATIONS = "m12_total_generations"
    private const val KEY_M12_OK_WITHOUT_REWRITE = "m12_ok_without_rewrite"
    private const val KEY_M12_REWRITES_ATTEMPTED = "m12_rewrites_attempted"
    private const val KEY_M12_REWRITES_USED = "m12_rewrites_used"
    private const val KEY_M12_SKIPPED_AFTER_SELFCHECK = "m12_skipped_after_selfcheck"
    private const val KEY_AUTO_POST_ELIGIBLE = "autoPostEligible"
    private const val KEY_AUTO_POST_BLOCK_REASON = "autoPostBlockReason"
    private const val MAX_AUDIT_EVENTS = 10
    private const val MAX_CYCLE_OUTCOMES = 3

    private const val BASE_INTERVAL_MULTIPLIER = 1.0f
    private const val MAX_INTERVAL_MULTIPLIER = 8.0f
    private const val BASE_INTERVAL_MINUTES = 45L
    private const val MAX_ADAPTIVE_DELAY_MINUTES = 360L

    const val SCHEDULE_MODE_PERIODIC = "periodic"
    const val SCHEDULE_MODE_ADAPTIVE_ONE_TIME_CHAIN = "adaptive_one_time_chain"

    enum class CycleOutcome {
        NO_CANDIDATE,
        SKIPPED_RATE_LIMIT,
        SKIPPED_SAFETY,
        SKIPPED_COOLDOWN,
        SKIPPED_LOW_BATTERY,
        SKIPPED_LOW_MEMORY,
        GENERATED,
        POST_SUCCESS
    }

    data class AuditEvent(
        val timestamp: Long,
        val eventType: String,
        val shortMessage: String
    )

    data class HomeStatus(
        val schedulerEnabled: Boolean,
        val lastActionTime: Long,
        val lastActionMessage: String,
        val actionsTodayCount: Int,
        val errorsCount: Int
    )

    data class M12Stats(
        val totalGenerations: Int,
        val okWithoutRewrite: Int,
        val rewritesAttempted: Int,
        val rewritesUsed: Int,
        val skippedAfterSelfCheck: Int
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

    fun isSchedulerEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCHEDULER_ENABLED, false)

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

    fun isEmergencyStopEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EMERGENCY_STOP_ENABLED, false)

    fun setEmergencyStopEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EMERGENCY_STOP_ENABLED, enabled).apply()
    }

    fun getConsecutivePostFailures(context: Context): Int =
        prefs(context).getInt(KEY_CONSECUTIVE_POST_FAILURES, 0)

    fun getLastGenerationAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_GENERATION_AT, 0L)

    fun recordGenerationCompleted(context: Context, generatedAt: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_GENERATION_AT, generatedAt).apply()
    }

    fun getGenerationIntervalMultiplier(context: Context): Float {
        val stored = prefs(context).getFloat(KEY_GENERATION_INTERVAL_MULTIPLIER, BASE_INTERVAL_MULTIPLIER)
        return stored.coerceIn(BASE_INTERVAL_MULTIPLIER, MAX_INTERVAL_MULTIPLIER)
    }

    fun getEffectiveIntervalMinutes(context: Context, baseMinutes: Long): Long {
        val multiplier = getGenerationIntervalMultiplier(context)
        return max(15L, (baseMinutes.toFloat() * multiplier).toLong())
    }

    fun getScheduleMode(context: Context): String =
        if (isAutonomousModeEnabled(context)) SCHEDULE_MODE_ADAPTIVE_ONE_TIME_CHAIN else SCHEDULE_MODE_PERIODIC

    fun getAdaptiveDelayMinutes(context: Context, baseMinutes: Long = BASE_INTERVAL_MINUTES): Long {
        val raw = (baseMinutes.toFloat() * getGenerationIntervalMultiplier(context)).toLong()
        return raw.coerceIn(baseMinutes, MAX_ADAPTIVE_DELAY_MINUTES)
    }

    fun getDisplayedNextRunDelayMinutes(context: Context): Long =
        when (getScheduleMode(context)) {
            SCHEDULE_MODE_ADAPTIVE_ONE_TIME_CHAIN -> getAdaptiveDelayMinutes(context)
            else -> getEffectiveIntervalMinutes(context, BASE_INTERVAL_MINUTES)
        }

    fun updateAdaptiveInterval(context: Context, cycleOutcome: CycleOutcome): Float {
        val pref = prefs(context)
        val existingRaw = pref.getString(KEY_RECENT_CYCLE_OUTCOMES, null)
        val outcomes = (existingRaw ?: "").split(',').filter { it.isNotBlank() }.toMutableList()
        outcomes.add(cycleOutcome.name)
        while (outcomes.size > MAX_CYCLE_OUTCOMES) {
            outcomes.removeAt(0)
        }

        var nextMultiplier = getGenerationIntervalMultiplier(context)
        val shouldReset = cycleOutcome == CycleOutcome.GENERATED || cycleOutcome == CycleOutcome.POST_SUCCESS
        val negativeSignals = setOf(
            CycleOutcome.NO_CANDIDATE.name,
            CycleOutcome.SKIPPED_RATE_LIMIT.name,
            CycleOutcome.SKIPPED_SAFETY.name
        )

        if (shouldReset) {
            nextMultiplier = BASE_INTERVAL_MULTIPLIER
            outcomes.clear()
        } else if (outcomes.size == MAX_CYCLE_OUTCOMES && outcomes.all { it in negativeSignals }) {
            nextMultiplier = (nextMultiplier * 1.5f).coerceAtMost(MAX_INTERVAL_MULTIPLIER)
        }

        pref.edit()
            .putString(KEY_RECENT_CYCLE_OUTCOMES, outcomes.joinToString(","))
            .putFloat(KEY_GENERATION_INTERVAL_MULTIPLIER, nextMultiplier)
            .apply()

        return nextMultiplier
    }

    fun getNextAutoPostAllowedAt(context: Context): Long =
        prefs(context).getLong(KEY_NEXT_AUTO_POST_ALLOWED_AT, 0L)


    fun setSelfCheckSummary(context: Context, status: String, issues: List<String>) {
        prefs(context).edit()
            .putString(KEY_SELF_CHECK_STATUS, status.take(24))
            .putString(KEY_SELF_CHECK_ISSUES, issues.take(2).joinToString(" | ").take(120))
            .apply()
    }

    fun getSelfCheckStatus(context: Context): String =
        prefs(context).getString(KEY_SELF_CHECK_STATUS, "—") ?: "—"

    fun getSelfCheckIssues(context: Context): String =
        prefs(context).getString(KEY_SELF_CHECK_ISSUES, "none")?.ifBlank { "none" } ?: "none"

    fun setAutoPostEligibility(context: Context, eligible: Boolean, blockedBy: String = "") {
        prefs(context).edit()
            .putBoolean(KEY_AUTO_POST_ELIGIBLE, eligible)
            .putString(KEY_AUTO_POST_BLOCK_REASON, blockedBy.take(80))
            .apply()
    }

    fun getAutoPostEligibilitySummary(context: Context): String {
        val eligible = prefs(context).getBoolean(KEY_AUTO_POST_ELIGIBLE, false)
        if (eligible) return "YES"
        val reason = prefs(context).getString(KEY_AUTO_POST_BLOCK_REASON, "")?.trim().orEmpty()
        return if (reason.isBlank()) "NO" else "NO ($reason)"
    }

    fun incrementM12TotalGenerations(context: Context) {
        incrementCounter(context, KEY_M12_TOTAL_GENERATIONS)
    }

    fun incrementM12OkWithoutRewrite(context: Context) {
        incrementCounter(context, KEY_M12_OK_WITHOUT_REWRITE)
    }

    fun incrementM12RewritesAttempted(context: Context) {
        incrementCounter(context, KEY_M12_REWRITES_ATTEMPTED)
    }

    fun incrementM12RewritesUsed(context: Context) {
        incrementCounter(context, KEY_M12_REWRITES_USED)
    }

    fun incrementM12SkippedAfterSelfCheck(context: Context) {
        incrementCounter(context, KEY_M12_SKIPPED_AFTER_SELFCHECK)
    }

    fun getM12Stats(context: Context): M12Stats {
        val pref = prefs(context)
        return M12Stats(
            totalGenerations = pref.getInt(KEY_M12_TOTAL_GENERATIONS, 0),
            okWithoutRewrite = pref.getInt(KEY_M12_OK_WITHOUT_REWRITE, 0),
            rewritesAttempted = pref.getInt(KEY_M12_REWRITES_ATTEMPTED, 0),
            rewritesUsed = pref.getInt(KEY_M12_REWRITES_USED, 0),
            skippedAfterSelfCheck = pref.getInt(KEY_M12_SKIPPED_AFTER_SELFCHECK, 0)
        )
    }

    fun resetM12Stats(context: Context) {
        prefs(context).edit()
            .putInt(KEY_M12_TOTAL_GENERATIONS, 0)
            .putInt(KEY_M12_OK_WITHOUT_REWRITE, 0)
            .putInt(KEY_M12_REWRITES_ATTEMPTED, 0)
            .putInt(KEY_M12_REWRITES_USED, 0)
            .putInt(KEY_M12_SKIPPED_AFTER_SELFCHECK, 0)
            .apply()
    }

    fun isAutoPostInCooldown(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val nextAllowedAt = getNextAutoPostAllowedAt(context)
        return nextAllowedAt > 0L && now < nextAllowedAt
    }

    fun recordAutoPostFailure(context: Context, now: Long = System.currentTimeMillis()) {
        val nextFailures = getConsecutivePostFailures(context) + 1
        val delayMillis = min(6L * 60L * 60L * 1000L, (30L * 60L * 1000L) * (1L shl nextFailures))
        prefs(context).edit()
            .putInt(KEY_CONSECUTIVE_POST_FAILURES, nextFailures)
            .putLong(KEY_NEXT_AUTO_POST_ALLOWED_AT, now + delayMillis)
            .apply()
    }

    fun recordAutoPostSuccess(context: Context) {
        prefs(context).edit()
            .putInt(KEY_CONSECUTIVE_POST_FAILURES, 0)
            .remove(KEY_NEXT_AUTO_POST_ALLOWED_AT)
            .apply()
    }

    fun recordAuditEvent(
        context: Context,
        eventType: String,
        shortMessage: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val pref = prefs(context)
        val existing = pref.getString(KEY_AUDIT_EVENTS_JSON, null)
        val jsonArray = runCatching { JSONArray(existing ?: "[]") }.getOrDefault(JSONArray())

        val eventJson = JSONObject()
            .put("timestamp", timestamp)
            .put("eventType", eventType)
            .put("shortMessage", shortMessage.take(120))

        jsonArray.put(eventJson)

        val trimmed = JSONArray()
        val start = maxOf(0, jsonArray.length() - MAX_AUDIT_EVENTS)
        for (index in start until jsonArray.length()) {
            trimmed.put(jsonArray.getJSONObject(index))
        }

        pref.edit().putString(KEY_AUDIT_EVENTS_JSON, trimmed.toString()).apply()
    }

    fun getRecentAuditEvents(context: Context, limit: Int = 3): List<AuditEvent> {
        val existing = prefs(context).getString(KEY_AUDIT_EVENTS_JSON, null)
        val jsonArray = runCatching { JSONArray(existing ?: "[]") }.getOrDefault(JSONArray())
        val events = mutableListOf<AuditEvent>()

        for (index in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(index)
            events.add(
                AuditEvent(
                    timestamp = item.optLong("timestamp", 0L),
                    eventType = item.optString("eventType", "UNKNOWN"),
                    shortMessage = item.optString("shortMessage", "")
                )
            )
        }

        return events.takeLast(limit).reversed()
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

    private fun incrementCounter(context: Context, key: String) {
        val pref = prefs(context)
        pref.edit().putInt(key, pref.getInt(key, 0) + 1).apply()
    }
}
