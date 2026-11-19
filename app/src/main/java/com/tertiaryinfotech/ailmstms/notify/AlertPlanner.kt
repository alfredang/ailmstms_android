package com.tertiaryinfotech.ailmstms.notify

import com.tertiaryinfotech.ailmstms.data.Dashboard
import com.tertiaryinfotech.ailmstms.data.SGT
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** One local notification to show for an upcoming class session. */
data class PlannedAlert(
    /** Stable per session and kind, so the same alert is never scheduled twice. */
    val key: String,
    val sessionId: String,
    val courseId: String,
    val kind: String,
    val at: Instant,
    val startsAt: Instant,
    val title: String,
    val body: String,
) {
    /** Shared with the matching server (FCM) reminder so the two replace each other instead of stacking. */
    val notificationId: Int get() = notificationId(sessionId, if (kind == DAY_BEFORE) 1 else 0)

    companion object {
        const val DAY_BEFORE = "day-before"
        const val MORNING = "morning"

        fun notificationId(sessionId: String, daysBefore: Int): Int = "$sessionId:$daysBefore".hashCode()
    }
}

/** Pure planning of class alerts: no Android types, so the timing rules are unit tested. */
object AlertPlanner {
    private val MORNING_TIME: LocalTime = LocalTime.of(8, 0)

    /**
     * Two alerts per upcoming session: 24 hours before it starts, and at 08:00 Singapore time
     * on the day (one hour before the start for a class that begins at or before 08:00).
     * Sessions without a published time and cancelled classes get none. Alerts already in the
     * past are dropped, and only the nearest [limit] within [horizon] are kept.
     */
    fun plan(dashboard: Dashboard, now: Instant, limit: Int = 40, horizon: Duration = Duration.ofDays(60)): List<PlannedAlert> {
        val cancelled = dashboard.courses.filter { it.isCancelled }.map { it.id }.toSet()
        return dashboard.sessions.asSequence()
            .filter { it.hasTime && it.courseId !in cancelled && it.startsAt.isAfter(now) }
            .distinctBy { it.id }
            .flatMap { session ->
                val start = session.startsAt
                val time = session.time.take(5)
                val place = listOf(session.format, session.venue).filter { it.isNotBlank() }.joinToString(" · ")
                val detail = if (place.isBlank()) "" else " $place."
                val eight = session.date.atTime(MORNING_TIME).atZone(SGT).toInstant()
                val morning = if (eight.isBefore(start)) eight else start.minus(Duration.ofHours(1))
                sequenceOf(
                    PlannedAlert(
                        "${session.id}:${PlannedAlert.DAY_BEFORE}", session.id, session.courseId, PlannedAlert.DAY_BEFORE,
                        start.minus(Duration.ofDays(1)), start, "Class tomorrow", "${session.title} starts tomorrow at $time SGT.$detail",
                    ),
                    PlannedAlert(
                        "${session.id}:${PlannedAlert.MORNING}", session.id, session.courseId, PlannedAlert.MORNING,
                        morning, start, "Class today", "${session.title} starts today at $time SGT.$detail",
                    ),
                )
            }
            .filter { it.at.isAfter(now) && it.at.isBefore(now.plus(horizon)) }
            .sortedBy { it.at }
            .take(limit)
            .toList()
    }
}
