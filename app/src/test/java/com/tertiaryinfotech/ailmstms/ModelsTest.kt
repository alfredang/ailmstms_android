package com.tertiaryinfotech.ailmstms

import com.tertiaryinfotech.ailmstms.data.AttendanceLearner
import com.tertiaryinfotech.ailmstms.data.ClassDetail
import com.tertiaryinfotech.ailmstms.data.ClassSession
import com.tertiaryinfotech.ailmstms.data.Course
import com.tertiaryinfotech.ailmstms.data.Dashboard
import com.tertiaryinfotech.ailmstms.data.FeedbackForm
import com.tertiaryinfotech.ailmstms.data.Profile
import com.tertiaryinfotech.ailmstms.data.ProfilePatch
import com.tertiaryinfotech.ailmstms.data.ThemeMode
import com.tertiaryinfotech.ailmstms.data.UserProfile
import com.tertiaryinfotech.ailmstms.data.attendanceRecords
import com.tertiaryinfotech.ailmstms.data.isSafeLink
import com.tertiaryinfotech.ailmstms.data.parseDay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun roleFilterRejectsAdministrativeAccounts() {
        assertEquals(emptyList<String>(), Profile.parse(JSONObject("""{"id":"1","roles":["admin","finance","developer"]}""")).roles)
        assertEquals(listOf("trainer"), Profile.parse(JSONObject("""{"roles":["admin","trainer"]}""")).roles)
        // Developer is kept beside a classroom role, so the account can switch to it.
        assertEquals(listOf("learner", "trainer", "developer"), Profile.parse(JSONObject("""{"roles":["developer","Trainer","learner","admin"]}""")).roles)
    }

    @Test
    fun courseRunsGroupByStatusAndDate() {
        val today = java.time.LocalDate.of(2026, 11, 25)
        fun run(id: String, start: String, end: String, status: String) =
            com.tertiaryinfotech.ailmstms.data.StaffClass(id, id, "T", "C", start, end, "Physical", status, false, emptyList(), 0, "")
        val runs = listOf(
            run("a", "2027-01-10", "2027-01-11", "Pending"), run("b", "2026-12-01", "2026-12-01", "Confirmed"),
            run("c", "2026-11-24", "2026-11-26", "Unconfirmed"), run("d", "2026-10-01", "2026-10-02", "Cancelled"),
        )
        assertEquals(listOf(com.tertiaryinfotech.ailmstms.data.RunStatus.PENDING, com.tertiaryinfotech.ailmstms.data.RunStatus.CONFIRMED,
            com.tertiaryinfotech.ailmstms.data.RunStatus.PENDING, com.tertiaryinfotech.ailmstms.data.RunStatus.CANCELLED), runs.map { it.statusBucket })
        val (upcoming, past) = com.tertiaryinfotech.ailmstms.data.splitRuns(runs, today)
        assertEquals(listOf("c", "b", "a"), upcoming.map { it.id })
        assertEquals(listOf("d"), past.map { it.id })
        val course = com.tertiaryinfotech.ailmstms.data.StaffCourse.parse(JSONObject("""{"id":"x","totalRuns":5,"confirmedRuns":3,"pendingRuns":1,"cancelledRuns":1}"""))
        assertEquals(5, course.totalRuns); assertEquals(3, course.confirmedRuns)
        assertEquals(null, com.tertiaryinfotech.ailmstms.data.StaffCourse.parse(JSONObject("""{"id":"y"}""")).totalRuns)
    }

    @Test
    fun resourceLinksRejectUnsafeSchemes() {
        assertFalse(isSafeLink("javascript:alert(1)"))
        assertFalse(isSafeLink("http://example.com"))
        assertFalse(isSafeLink("https://user:pass@example.com"))
        assertTrue(isSafeLink("https://drive.google.com/file/d/example/view"))
    }

    @Test
    fun singaporeTimesAreIndependentOfPhoneTimezone() {
        val s = ClassSession.parse(JSONObject("""{"id":"1","startDate":"2026-09-10","endDate":"2026-09-10","startTime":"09:00","endTime":"17:00"}"""))!!
        assertEquals("2026-09-10T01:00:00Z", s.startsAt.toString())
        assertEquals("2026-09-10T09:00:00Z", s.endsAt.toString())
        assertTrue(s.hasTime)
    }

    @Test
    fun invalidSessionIsSkipped() {
        assertNull(ClassSession.parse(JSONObject("""{"startDate":"invalid"}""")))
    }

    @Test
    fun ssgCompactDatesAreAccepted() {
        assertEquals("2026-11-16", parseDay("20261116").toString())
        assertEquals("2026-11-16", parseDay("2026-11-16T00:00:00.000Z").toString())
        val s = ClassSession.parse(JSONObject("""{"id":"1","startDate":"20261116","endDate":null,"startTime":null}"""))!!
        assertEquals("2026-11-16", s.date.toString())
        assertEquals(s.date, s.endDate)
        assertFalse(s.hasTime)
        assertTrue(s.whenText.endsWith("Time to be confirmed"))
    }

    @Test
    fun courseKeepsOnlySafeMaterialLinksAndReadsRunCode() {
        val c = Course.parse(JSONObject("""{"id":"c1","title":"T","code":"C","slidesURL":"https://example.com/s.pdf","guideURL":"http://example.com/g","activitiesURL":null,"trainerSlidesURL":"javascript:1","runCode":"RUN-1","classStatus":"Cancelled"}"""))
        assertEquals(listOf("slides"), c.resources.map { it.id })
        assertEquals("RUN-1", c.runCode)
        assertTrue(c.isCancelled)
    }

    @Test
    fun dashboardSortsSessions() {
        val d = Dashboard.parse(JSONObject("""{"courses":[],"sessions":[{"id":"b","startDate":"2026-09-11","startTime":"09:00"},{"id":"a","startDate":"2026-09-10","startTime":"09:00"},{"id":"bad","startDate":""}]}"""))
        assertEquals(listOf("a", "b"), d.sessions.map { it.id })
    }

    @Test
    fun learnerClassDetailIsParsed() {
        val json = """{"id":"run","runCode":"RC1","title":"AI","code":"TGS-1","startDate":"2026-11-16","endDate":"2026-11-17","format":"Physical","classStatus":"Confirmed","venue":null,"meetingURL":"https://meet.example.com/x",
          "materials":[{"id":"slides","title":"Learner slides","url":"https://drive.google.com/file/d/abcdefghijkl/view"},{"id":"bad","title":"Bad","url":"http://insecure"}],
          "sessions":[{"id":"s1","label":"Day 1 · Mon 16 Nov · AM","date":"2026-11-16","startTime":"09:00","endTime":"13:00","attendance":"present"},{"id":"s2","label":"Day 1 · PM","date":"2026-11-16","startTime":null,"endTime":null,"attendance":null}],
          "ssgAttendanceURL":null,"traqomURL":"https://ssgtraqom.qualtrics.com/jfe/form/x",
          "assessments":[{"type":"writtenAssessment","label":"Written Assessment","questionURL":"https://docs.google.com/document/d/abcdefghijkl/edit","published":true,"submissions":[{"id":"sub1","fileName":"a.pdf","fileURL":"https://drive.google.com/file/d/x/view","submittedAt":"2026-11-16T04:00:00.000Z","signed":true}]}],
          "assessmentStatus":"Competent","traqomCompleted":true,"feedback":{"enabled":true,"externalURL":null,"submitted":false}}"""
        val d = ClassDetail.parse(JSONObject(json))
        assertEquals("RC1", d.runCode)
        assertEquals("", d.venue)
        assertEquals(1, d.materials.size)
        assertEquals("present", d.sessions[0].attendance)
        assertNull(d.sessions[1].attendance)
        assertNull(d.sessions[0].presentCount)
        assertEquals("09:00–13:00", d.sessions[0].timeText)
        assertNull(d.ssgAttendanceUrl)
        assertTrue(d.assessments[0].submissions[0].signed)
        assertEquals("Competent", d.assessmentStatus)
        assertTrue(d.feedback!!.enabled)
        assertNull(d.feedback!!.externalUrl)
        assertNull(d.partialReason)
    }

    @Test
    fun trainerClassDetailIsParsed() {
        val json = """{"id":"run","title":"AI","sessions":[{"id":"s1","label":"Day 1","date":"2026-11-16","presentCount":3}],"materials":[],"assessments":[{"type":"project","label":"Project","questionURL":null,"published":false,"submissions":[]}],
          "ssgAttendanceURL":"https://www.myskillsfuture.gov.sg/spface/splogin/select-session?course-run-code=RA123456",
          "roster":[{"enrolmentId":"e1","name":"Bala","status":null,"traqomCompleted":false,"submissions":2}],"feedbackCount":4}"""
        val d = ClassDetail.parse(JSONObject(json))
        assertEquals(3, d.sessions[0].presentCount)
        assertEquals("", d.roster[0].status)
        assertEquals(2, d.roster[0].submissions)
        assertEquals(4, d.feedbackCount)
        assertNull(d.feedback)
        assertFalse(d.assessments[0].published)
        assertTrue(d.ssgAttendanceUrl!!.contains("RA123456"))
    }

    @Test
    fun classDetailFallsBackToTheDashboardCopy() {
        val d = Dashboard.parse(JSONObject("""{"courses":[{"id":"c1","title":"AI","code":"C","slidesURL":"https://example.com/s.pdf","runCode":"R"}],"sessions":[{"id":"s1","courseID":"c1","startDate":"2026-09-10","startTime":"09:00","subtitle":"Session 1"},{"id":"run-c1","courseID":"c1","startDate":"2026-09-10"}]}"""))
        val detail = ClassDetail.fromCourse(d.courses[0], d.sessions, "offline")
        assertEquals("offline", detail.partialReason)
        assertEquals(listOf("s1"), detail.sessions.map { it.id })
        assertEquals(1, detail.materials.size)
        assertTrue(detail.assessments.isEmpty())
    }

    @Test
    fun attendanceRecordsSendOnlyMarkedLearners() {
        val records = attendanceRecords(
            listOf(
                AttendanceLearner("e1", "A", true, "ignored"),
                AttendanceLearner("e2", "B", false, " Sick "),
                AttendanceLearner("e3", "C", null, ""),
            ),
        )
        assertEquals(2, records.length())
        assertFalse(records.getJSONObject(0).has("reason"))
        assertEquals("Sick", records.getJSONObject(1).getString("reason"))
        assertFalse(records.getJSONObject(1).getBoolean("isPresent"))
    }

    private val form = FeedbackForm.parse(
        JSONObject(
            """{"title":"Course Feedback","submitted":false,"autofill":{"course_title":"AI","start_date":"2026-11-16"},
            "sections":[{"id":"s1","title":"Details","fields":[
              {"id":"course_title","label":"Course Title","type":"text","required":true,"autofill":"course_title","readonly":true},
              {"id":"learner_email","label":"Email","type":"email","required":true},
              {"id":"learner_name","label":"Name","type":"text","required":true},
              {"id":"rate","label":"Rate it","type":"rating1to5","required":true},
              {"id":"pick","label":"Pick","type":"select","options":["Yes","No"]},
              {"id":"message","label":"Comments","type":"textarea","required":false}]}]}""",
        ),
    )

    @Test
    fun feedbackFormFindsTheFirstUnansweredRequiredQuestion() {
        assertEquals("AI", form.autofill["course_title"])
        assertEquals(listOf("Yes", "No"), form.fields.first { it.id == "pick" }.options)
        assertTrue(form.fields.first { it.id == "course_title" }.locked)
        assertEquals("learner_name", form.firstMissing(emptyMap())!!.id)
        assertEquals("rate", form.firstMissing(mapOf("learner_name" to "Alex"))!!.id)
        assertEquals("rate", form.firstMissing(mapOf("learner_name" to "Alex", "rate" to "9"))!!.id)
        assertNull(form.firstMissing(mapOf("learner_name" to "Alex", "rate" to "4")))
    }

    @Test
    fun feedbackAnswersSendRatingsAsNumbersAndSkipServerFilledFields() {
        val json = form.answersJson(mapOf("learner_name" to " Alex ", "rate" to "5", "message" to "  ", "course_title" to "tampered", "learner_email" to "x@y.z"))
        assertEquals(5, json.getInt("rate"))
        assertEquals("Alex", json.getString("learner_name"))
        assertFalse(json.has("message"))
        assertFalse(json.has("course_title"))
        assertFalse(json.has("learner_email"))
    }

    @Test
    fun profileRoundTripsAndPatchSendsOnlySetFields() {
        val p = UserProfile.parse(JSONObject("""{"id":"u","email":"a@b.c","fullName":"Alex","pictureURL":null,"theme":"dark","phone":null,"company":"Co","employmentStatus":"Employed","linkedinURL":null,"brandColor":"#0A3D8F","academy":"TIA"}"""))
        assertEquals(ThemeMode.DARK, p.theme)
        assertEquals("", p.phone)
        assertEquals(p, UserProfile.parse(p.toJson()))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("sepia"))
        val patch = ProfilePatch(theme = ThemeMode.LIGHT).toJson()
        assertEquals("""{"theme":"light"}""", patch.toString())
        assertEquals("", ProfilePatch(phone = "").toJson().getString("phone"))
    }
}
