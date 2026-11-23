package com.tertiaryinfotech.ailmstms

import com.tertiaryinfotech.ailmstms.data.DemoApi
import com.tertiaryinfotech.ailmstms.data.ProfilePatch
import com.tertiaryinfotech.ailmstms.data.ThemeMode
import com.tertiaryinfotech.ailmstms.data.UploadSource
import com.tertiaryinfotech.ailmstms.data.isSafeLink
import com.tertiaryinfotech.ailmstms.notify.AlertPlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The demo that Google Play reviewers use: every screen has data, and nothing needs the network. */
class DemoApiTest {
    private val api = DemoApi(today = { LocalDate.parse("2026-11-10") }, pause = 0)

    @Test
    fun trainerSeesSamplePayouts() = runBlocking {
        val payouts = api.payouts()
        assertEquals(3, payouts.size)
        assertEquals(3350.0, payouts.sumOf { it.amount }, 0.001)
        assertTrue(payouts.all { it.title.isNotBlank() && it.paymentDate > it.endDate && it.endDate >= it.startDate })
        assertEquals(payouts.sortedByDescending { it.paymentDate }, payouts)
    }

    @Test
    fun learnerSeesClassesWithMaterialsAssessmentsAndFeedback() = runBlocking {
        val dashboard = api.dashboard("learner")
        assertEquals(3, dashboard.courses.size)
        assertEquals(listOf("Confirmed", "Pending", "Cancelled"), dashboard.courses.map { it.classStatus })
        assertTrue(dashboard.sessions.all { s -> dashboard.courses.any { it.id == s.courseId } })
        val detail = api.classDetail("demo-ai", "learner")
        assertEquals(3, detail.materials.size)
        assertTrue(detail.materials.all { isSafeLink(it.url) })
        assertEquals(2, detail.sessions.size)
        assertEquals(2, detail.assessments.size)
        assertTrue(detail.assessments.all { it.published })
        assertTrue(detail.feedback!!.enabled)
        assertTrue(detail.roster.isEmpty())
        assertEquals("demo-data", api.classDetail("demo-data", "learner").id)
    }

    @Test
    fun checkInMarksTheLearnerPresent() = runBlocking {
        assertNull(api.classDetail("demo-ai", "learner").sessions[0].attendance)
        assertFalse(api.checkIn(DemoApi.DEMO_CODE).already)
        assertEquals("present", api.classDetail("demo-ai", "learner").sessions[0].attendance)
        assertTrue(api.checkIn(DemoApi.DEMO_CODE).already)
    }

    @Test
    fun aSubmittedFileAppearsAndCanBeRemoved() = runBlocking {
        val steps = mutableListOf<Float>()
        val source = UploadSource("answers.pdf", "application/pdf", 10) { throw AssertionError("the demo must never read the picked file") }
        val file = api.uploadAssessment("Alex Tan", "DEMO-RUN-1001", source) { steps.add(it) }
        assertEquals(1f, steps.last())
        api.submitAssessment("demo-ai", "writtenAssessment", "answers.pdf", file)
        val submissions = api.classDetail("demo-ai", "learner").assessments.first { it.type == "writtenAssessment" }.submissions
        assertEquals("answers.pdf", submissions.single().fileName)
        api.deleteSubmission(submissions.single().id)
        assertTrue(api.classDetail("demo-ai", "learner").assessments.all { it.submissions.isEmpty() })
    }

    @Test
    fun feedbackCanBeSentOnce() = runBlocking {
        val form = api.feedbackForm("demo-ai")
        assertEquals(setOf("text", "date", "rating1to5", "select", "textarea"), form.fields.map { it.type }.toSet())
        assertEquals("rate_learning_objectives", form.firstMissing(mapOf("learner_name" to "Alex Tan"))!!.id)
        api.submitFeedback("demo-ai", form, mapOf("learner_name" to "Alex Tan", "rate_learning_objectives" to "5", "rate_trainer_knowledge" to "5"))
        assertTrue(api.feedbackForm("demo-ai").submitted)
        assertTrue(api.classDetail("demo-ai", "learner").feedback!!.submitted)
    }

    @Test
    fun trainerTakesAttendanceAndGrades() = runBlocking {
        val detail = api.classDetail("demo-ai", "trainer")
        assertEquals(5, detail.materials.size)
        assertEquals(4, detail.roster.size)
        assertEquals(2, detail.sessions[0].presentCount)
        val sheet = api.attendance("demo-1")
        assertTrue(sheet.checkinUrl!!.endsWith(DemoApi.DEMO_CODE))
        val saved = api.markAttendance("demo-1", sheet.learners.map { it.copy(isPresent = true) })
        assertTrue(saved.learners.all { it.isPresent == true && it.reason.isEmpty() })
        assertEquals(4, api.classDetail("demo-ai", "trainer").sessions[0].presentCount)
        assertEquals("Not Yet Competent", api.grade(listOf("demo-e2"), false))
        assertEquals("Not Yet Competent", api.classDetail("demo-ai", "trainer").roster.first { it.enrolmentId == "demo-e2" }.status)
    }

    @Test
    fun profileEditsStayInMemory() = runBlocking {
        val updated = api.updateProfile(ProfilePatch(fullName = "Alex T.", theme = ThemeMode.DARK))
        assertEquals("Alex T.", api.profile().fullName)
        assertEquals(ThemeMode.DARK, updated.theme)
        assertEquals("alex@example.com", updated.email)
    }

    @Test
    fun demoSessionsWouldProduceAlertsButTheDemoNeverSchedulesThem() = runBlocking {
        // The planner works on the demo data (useful as a sanity check); LmsViewModel skips scheduling in demo mode.
        val plan = AlertPlanner.plan(api.dashboard("learner"), Instant.parse("2026-11-10T04:00:00Z"))
        assertEquals(6, plan.size)
    }
}
