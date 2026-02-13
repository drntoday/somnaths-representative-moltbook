package com.somnath.representative.data

import android.content.Context

private const val PREFS_NAME = "somnath_rep_prefs"
private const val KEY_LAST_POST_TIMESTAMP = "autonomous_lastPostTimestamp"
private const val KEY_POSTS_LAST_24H = "autonomous_postsLast24h"
private const val KEY_DAILY_WINDOW_START = "autonomous_dailyWindowStart"

private const val TWENTY_FOUR_HOURS_MS = 24L * 60L * 60L * 1000L
private const val TWO_HOURS_MS = 2L * 60L * 60L * 1000L
private const val MAX_POSTS_PER_24H = 3

data class AutonomousPostRateLimitStatus(
    val lastPostTimestamp: Long,
    val postsLast24h: Int,
    val dailyWindowStart: Long,
    val nextAllowedPostAt: Long,
    val canPostNow: Boolean
)

object AutonomousPostRateLimiter {
    private data class RateState(
        val lastPostTimestamp: Long,
        val postsLast24h: Int,
        val dailyWindowStart: Long
    )

    private var inMemoryState: RateState? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun canPostNow(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val state = normalizedState(context, nowMs)
        if (state.postsLast24h >= MAX_POSTS_PER_24H) return false
        if (state.lastPostTimestamp > 0L && nowMs - state.lastPostTimestamp < TWO_HOURS_MS) return false
        return true
    }

    @Synchronized
    fun getStatus(context: Context, nowMs: Long = System.currentTimeMillis()): AutonomousPostRateLimitStatus {
        val state = normalizedState(context, nowMs)
        val nextByGap = if (state.lastPostTimestamp > 0L) state.lastPostTimestamp + TWO_HOURS_MS else 0L
        val nextByDailyCap = if (state.postsLast24h >= MAX_POSTS_PER_24H) state.dailyWindowStart + TWENTY_FOUR_HOURS_MS else 0L
        val nextAllowed = maxOf(nextByGap, nextByDailyCap)
        return AutonomousPostRateLimitStatus(
            lastPostTimestamp = state.lastPostTimestamp,
            postsLast24h = state.postsLast24h,
            dailyWindowStart = state.dailyWindowStart,
            nextAllowedPostAt = nextAllowed,
            canPostNow = nextAllowed == 0L || nowMs >= nextAllowed
        )
    }

    @Synchronized
    fun recordSuccessfulPost(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val state = normalizedState(context, nowMs)
        val updated = state.copy(
            lastPostTimestamp = nowMs,
            postsLast24h = (state.postsLast24h + 1).coerceAtMost(MAX_POSTS_PER_24H),
            dailyWindowStart = if (state.dailyWindowStart <= 0L) nowMs else state.dailyWindowStart
        )
        saveState(context, updated)
    }

    @Synchronized
    private fun normalizedState(context: Context, nowMs: Long): RateState {
        val base = inMemoryState ?: loadState(context)
        val normalized = if (base.dailyWindowStart <= 0L || nowMs - base.dailyWindowStart >= TWENTY_FOUR_HOURS_MS) {
            RateState(
                lastPostTimestamp = if (base.lastPostTimestamp > 0L && nowMs - base.lastPostTimestamp < TWENTY_FOUR_HOURS_MS) base.lastPostTimestamp else 0L,
                postsLast24h = 0,
                dailyWindowStart = nowMs
            )
        } else {
            base
        }
        if (normalized != base) {
            saveState(context, normalized)
        } else {
            inMemoryState = normalized
        }
        return normalized
    }

    private fun loadState(context: Context): RateState {
        val pref = prefs(context)
        return RateState(
            lastPostTimestamp = pref.getLong(KEY_LAST_POST_TIMESTAMP, 0L),
            postsLast24h = pref.getInt(KEY_POSTS_LAST_24H, 0),
            dailyWindowStart = pref.getLong(KEY_DAILY_WINDOW_START, 0L)
        ).also { inMemoryState = it }
    }

    private fun saveState(context: Context, state: RateState) {
        prefs(context).edit()
            .putLong(KEY_LAST_POST_TIMESTAMP, state.lastPostTimestamp)
            .putInt(KEY_POSTS_LAST_24H, state.postsLast24h)
            .putLong(KEY_DAILY_WINDOW_START, state.dailyWindowStart)
            .apply()
        inMemoryState = state
    }
}
