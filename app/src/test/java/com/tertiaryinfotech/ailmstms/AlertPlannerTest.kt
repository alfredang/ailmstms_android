package com.tertiaryinfotech.ailmstms

import com.tertiaryinfotech.ailmstms.data.ClassSession
import com.tertiaryinfotech.ailmstms.data.Course
import com.tertiaryinfotech.ailmstms.data.Dashboard
import com.tertiaryinfotech.ailmstms.notify.AlertPlanner
import com.tertiaryinfotech.ailmstms.notify.PlannedAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AlertPlannerTest {
    private fun course(id: String, status: String = "Confirmed") = Course(id, "Course $id", "C", "", "", "Classroom", "Room 1", emptyList(), "R", status)
    private fun session(id: String, course: String, date: String, time: String = "09:00") =
        ClassSession(id, course, "Course $course", LocalDate.parse(date), LocalDate.parse(date), time, "17:00", "Session", "Room 1", "Classroom", "")

    // 10 Nov 2026 12:00 SGT
    private val now = Instant.parse("2026-11-10T04:00:00Z")

    @Test
    fun eachUpcomingSessionGetsADayBeforeAndAMorningAlert() {
        val plan = AlertPlanner.plan(Dashboard(listOf(course("a")), listOf(session("s1", "a", "2026-11-16"))), now)
        assertEquals(listOf("s1:day-before", "s1:morning"), plan.map { it.key })
        // 24 hours before a 09:00 SGT start, and 08:00 SGT on the day.
        assertEquals(Instant.parse("2026-11-15T01:00:00Z"), plan[0].at)
        assertEquals(Instant.parse("2026-11-16T00:00:00Z"), plan[1].at)
        assertTrue(plan[0].body.contains("tomorrow at 09:00 SGT"))
        assertTrue(plan[1].body.contains("today at 09:00 SGT"))
    }

    @Test
    fun alertsAlreadyInThePastAreDropped() {
        // Class tomorrow 09:00: "24 hours before" was three hours ago, the morning alert is still ahead.
        val plan = AlertPlanner.plan(Dashboard(listOf(course("a")), listOf(session("s1", "a", "2026-11-11"))), now)
        assertEquals(listOf("s1:morning"), plan.map { it.key })
        // A session that has started gets nothing.
        assertTrue(AlertPlanner.plan(Dashboard(listOf(course("a")), listOf(session("s0", "a", "2026-11-10"))), now).isEmpty())
    }

    @Test
    fun cancelledClassesAndUnpublishedTimesGetNoAlert() {
        val dashboard = Dashboard(
            listOf(course("a", "Cancelled"), course("b")),
            listOf(session("s1", "a", "2026-11-16"), session("run-b", "b", "2026-11-16", time = "")),
        )
        assertTrue(AlertPlanner.plan(dashboard, now).isEmpty())
    }

    @Test
    fun anEarlyClassIsAlertedOneHourBeforeItStarts() {
        val plan = AlertPlanner.plan(Dashboard(listOf(course("a")), listOf(session("s1", "a", "2026-11-16", "07:30"))), now)
        assertEquals(Instant.parse("2026-11-15T22:30:00Z"), plan.first { it.kind == PlannedAlert.MORNING }.at)
    }

    @Test
    fun duplicatesAreRemovedAndThePlanIsCappedToTheNearest() {
        val sessions = (1..30).map { session("s$it", "a", LocalDate.parse("2026-11-12").plusDays(it.toLong()).toString()) }
        val plan = AlertPlanner.plan(Dashboard(listOf(course("a")), sessions + sessions), now, limit = 10)
        assertEquals(10, plan.size)
        assertEquals(plan.map { it.key }.distinct(), plan.map { it.key })
        assertEquals(plan.sortedBy { it.at }, plan)
        // Beyond the horizon nothing is scheduled; the next refresh picks it up.
        assertTrue(AlertPlanner.plan(Dashboard(listOf(course("a")), listOf(session("far", "a", "2027-06-01"))), now).isEmpty())
    }

    @Test
    fun theDayBeforeAlertSharesItsNotificationSlotWithTheServerReminder() {
        val plan = AlertPlanner.plan(Dashboard(listOf(course("a")), listOf(session("s1", "a", "2026-11-16"))), now)
        assertEquals(PlannedAlert.notificationId("s1", 1), plan[0].notificationId)
        assertNotEquals(plan[0].notificationId, plan[1].notificationId)
    }
}
