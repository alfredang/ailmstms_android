package com.tertiaryinfotech.ailmstms

import com.tertiaryinfotech.ailmstms.data.ApiException
import com.tertiaryinfotech.ailmstms.data.ClassDetail
import com.tertiaryinfotech.ailmstms.data.DemoApi
import com.tertiaryinfotech.ailmstms.data.Material
import com.tertiaryinfotech.ailmstms.data.Repository
import com.tertiaryinfotech.ailmstms.data.Roles
import com.tertiaryinfotech.ailmstms.data.SignerKind
import com.tertiaryinfotech.ailmstms.data.SignerRecord
import com.tertiaryinfotech.ailmstms.data.StaffClass
import com.tertiaryinfotech.ailmstms.data.StaffCourse
import com.tertiaryinfotech.ailmstms.data.StaffTrainer
import com.tertiaryinfotech.ailmstms.data.trainerMessage
import com.tertiaryinfotech.ailmstms.data.whatsAppUrl
import com.tertiaryinfotech.ailmstms.data.SummaryReply
import com.tertiaryinfotech.ailmstms.data.asOnWebsite
import com.tertiaryinfotech.ailmstms.data.hoursText
import com.tertiaryinfotech.ailmstms.data.longRange
import com.tertiaryinfotech.ailmstms.data.mapsUrl
import com.tertiaryinfotech.ailmstms.data.meetingProvider
import com.tertiaryinfotech.ailmstms.data.needsSignatureSetup
import com.tertiaryinfotech.ailmstms.data.portalUrl
import com.tertiaryinfotech.ailmstms.data.scoreText
import com.tertiaryinfotech.ailmstms.data.signerBody
import com.tertiaryinfotech.ailmstms.data.ssgAttendanceListUrl
import com.tertiaryinfotech.ailmstms.data.ssgQrAttendanceUrl
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** The class page panels, Assessment Summary Record signing, the Publish switch, roles and the staff pages. */
class ClassPageTest {
    private val server = MockWebServer()
    private val store = MemoryState()
    private lateinit var repo: Repository

    @Before
    fun start() {
        server.start()
        store.token = "lms_abc"
        repo = Repository(store, server.url("/").toString().trimEnd('/'))
    }

    @After
    fun stop() = server.shutdown()

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    // ---------------------------------------------------------------- models

    @Test
    fun classDetailReadsTheWebsitePanels() {
        val d = ClassDetail.parse(JSONObject("""{"id":"run","title":"AI","materials":[],"sessions":[],"assessments":[],
          "courseDetails":{"tscTitle":"AI Application","tscCode":"EGS-1","caslSkills":null,"fundingValidityStart":"2025-01-30","fundingValidityEnd":"2027-01-29","trainingHours":"7","assessmentHours":1},
          "trainerNames":["Alex Tan"," ",""],"digitalAttendanceId":" RA123456 ",
          "announcements":[{"id":"a1","title":"Welcome","message":"Hi","linkURL":"javascript:x","fileURL":"https://drive.google.com/file/d/abcdefghijkl/view","fileName":"Notes.pdf","postedBy":"Alex","createdAt":"2026-11-10T02:00:00.000Z"}],
          "briefingOnAssessment":["No phones",""],"showCourseFeedback":false,"courseFeedbackURL":"/feedback/run","swdaEnrolmentURL":"/enrol/run",
          "feedbackSummary":{"count":2,"overall":4.5,"questions":[{"label":"Q1","average":4.5},{"label":"Q2","average":null}],"comments":["Great",""]}}"""))
        val info = d.courseDetails!!
        assertEquals("AI Application", info.tscTitle)
        assertEquals("", info.caslSkills)
        assertEquals(7.0, info.trainingHours!!, 0.0)
        assertEquals(false, info.expired(LocalDate.parse("2026-11-23")))
        assertEquals(true, info.expired(LocalDate.parse("2027-01-30")))
        assertEquals(listOf("Alex Tan"), d.trainerNames)
        assertEquals("RA123456", d.digitalAttendanceId)
        assertNull("only https links are kept", d.announcements[0].linkUrl)
        assertEquals("Notes.pdf", d.announcements[0].fileName)
        assertEquals(listOf("No phones"), d.briefingOnAssessment)
        assertEquals(false, d.showCourseFeedback)
        assertEquals("/enrol/run", d.swdaEnrolmentUrl)
        assertEquals(2, d.feedbackSummary!!.count)
        assertNull(d.feedbackSummary!!.questions[1].average)
        assertEquals(listOf("Great"), d.feedbackSummary!!.comments)
    }

    @Test
    fun anOlderServerReplyStillReads() {
        val d = ClassDetail.parse(JSONObject("""{"id":"run","title":"AI"}"""))
        assertNull(d.courseDetails)
        assertNull(d.showCourseFeedback)
        assertTrue(d.trainerNames.isEmpty() && d.announcements.isEmpty())
        assertNull(d.feedbackSummary)
    }

    @Test
    fun coursewareFollowsTheWebsiteNamesAndOrder() {
        val old = listOf(
            Material("slides", "Learner slides", "https://e.com/1"),
            Material("trainer-slides", "Trainer slides", "https://e.com/2"),
            Material("custom", "Extra reading", "https://e.com/3"),
            Material("activities", "Labs & activities", "https://e.com/4"),
            Material("guide", "Learner Guide", "https://e.com/5"),
            Material("courseware-link", "Courseware Link", "https://e.com/6"),
        )
        assertEquals(
            listOf("Courseware Link", "Learner Guide", "Trainer Slides", "Activities/Lab", "Extra reading"),
            old.asOnWebsite(trainer = true).map { it.title },
        )
        assertEquals(
            listOf("Learner Guide", "Learner Slides", "Activities/Lab", "Extra reading"),
            old.asOnWebsite(trainer = false).map { it.title },
        )
    }

    @Test
    fun hoursScoresAndDatesReadLikeTheWebsite() {
        assertEquals("1 hr", hoursText(1.0))
        assertEquals("7 hrs", hoursText(7.0))
        assertEquals("1.5 hrs", hoursText(1.5))
        assertEquals("N/A", hoursText(null))
        assertEquals("4.5", scoreText(4.5))
        assertEquals("5", scoreText(5.0))
        assertEquals("—", scoreText(null))
        assertEquals("16 Nov 2026 – 17 Nov 2026", longRange("2026-11-16", "2026-11-17"))
        assertEquals("16 Nov 2026", longRange("2026-11-16", "2026-11-16"))
    }

    @Test
    fun linksForMapsMeetingsSsgAndThePortal() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=12%20Woodlands%20Square%2C%20Singapore%20737715", mapsUrl("12 Woodlands Square, Singapore 737715"))
        assertEquals("Google Meet", meetingProvider("https://meet.google.com/abc-defg-hij"))
        assertEquals("Zoom", meetingProvider("https://us02web.zoom.us/j/123"))
        assertEquals("Microsoft Teams", meetingProvider("https://teams.microsoft.com/l/meetup-join/x"))
        assertEquals("online class", meetingProvider("https://example.com/room"))
        assertEquals("https://www.myskillsfuture.gov.sg/spface/splogin/select-session?course-run-code=RA1", ssgQrAttendanceUrl("RA1"))
        assertEquals("https://www.myskillsfuture.gov.sg/api/take-attendance/RA1", ssgAttendanceListUrl("RA1"))
        assertEquals("https://lms.example.com/enrol/run-1", portalUrl("/enrol/run-1", "https://lms.example.com/"))
        assertEquals("https://forms.example.com/x", portalUrl("https://forms.example.com/x", "https://lms.example.com"))
        assertNull(portalUrl("//evil.example.com/x", "https://lms.example.com"))
        assertNull(portalUrl("javascript:alert(1)", "https://lms.example.com"))
        assertNull(portalUrl("", "https://lms.example.com"))
    }

    @Test
    fun rolesForTheOwnerAdminAndEveryoneElse() {
        assertEquals(listOf("learner", "trainer", "developer", "admin"), Roles.available(Roles.OWNER_EMAIL, listOf("learner"), "learner"))
        // An email with several roles switches between them; Admin only after an admin sign-in.
        assertEquals(listOf("learner", "trainer"), Roles.available("someone@example.com", listOf("learner", "trainer"), "trainer"))
        assertEquals(listOf("learner", "trainer", "developer", "admin"),
            Roles.available("someone@example.com", listOf("learner", "trainer", "developer"), "admin", adminSession = true))
        assertEquals(listOf("learner"), Roles.available("someone@example.com", listOf("learner"), "learner"))
        assertEquals("developer", Roles.restore("a@b.c", "developer", "learner", false, listOf("learner", "developer")))
        assertEquals("Admin", Roles.label("admin"))
        assertTrue(Roles.isStaff("developer") && Roles.isStaff("admin") && !Roles.isStaff("trainer"))
        // The owner keeps the role last viewed as; an admin sign-in keeps Admin; others fall back to what they hold.
        assertEquals("developer", Roles.restore(Roles.OWNER_EMAIL, "developer", "learner", false, listOf("learner")))
        assertEquals("admin", Roles.restore("a@b.c", "admin", "admin", true, emptyList()))
        assertEquals("learner", Roles.restore("a@b.c", "admin", "admin", false, listOf("learner")))
        assertEquals("trainer", Roles.restore("a@b.c", "trainer", "", false, listOf("learner", "trainer")))
    }

    @Test
    fun staffListsReadAndFilter() {
        val classes = StaffClass.parseAll(JSONObject("""{"classes":[
          {"id":"1","runCode":"100001","title":"AI","code":"TGS-1","startDate":"2026-11-23","endDate":"2026-11-24","format":"Hybrid","classStatus":"Confirmed","hasMeetingLink":true,"trainers":["Alex Tan"],"learners":18,"venue":"12 Woodlands Square"},
          {"id":"2","runCode":"100002","title":"Data","code":"TGS-2","startDate":"2026-11-26","endDate":null,"format":"Physical","classStatus":"Cancelled","hasMeetingLink":false,"trainers":[],"learners":0,"venue":null}]}"""))
        assertTrue(classes[0].covers(LocalDate.parse("2026-11-24")))
        assertFalse(classes[0].covers(LocalDate.parse("2026-11-25")))
        assertTrue(classes[1].covers(LocalDate.parse("2026-11-26")))
        assertTrue(classes[1].cancelled)
        assertEquals("", classes[1].venue)
        assertTrue(classes[0].matches("alex") && classes[0].matches("100001") && classes[0].matches("tgs-1") && !classes[0].matches("data"))

        val courses = StaffCourse.parseAll(JSONObject("""{"courses":[
          {"id":"c1","title":"AI","code":"TGS-1","courseType":"WSQ","tscCode":"EGS-1","trainingHours":7,"assessmentHours":1,"fundingValidityEnd":"2027-01-29","upcomingRuns":4,"nextRun":"2026-12-01"},
          {"id":"c2","title":"Old (Expired)","code":"TGS-2","fundingValidityEnd":null,"upcomingRuns":0,"nextRun":null}]}"""))
        val today = LocalDate.parse("2026-11-23")
        assertFalse(courses[0].expired(today))
        assertTrue(courses[0].expired(LocalDate.parse("2027-02-01")))
        assertTrue("a title marked (Expired) counts as expired", courses[1].expired(today))
        assertTrue(courses[0].matches("egs-1"))
    }

    @Test
    fun summaryRecordsAndSignerDetailsRead() {
        val mine = SummaryReply.parse(JSONObject("""{"success":true,"run":{"hasTemplate":true},"record":{"learner":{"name":"Jamie","signDate":"2026-11-20","signedAt":"2026-11-20T03:00:00Z"},"trainer":null,"file":{"url":"https://drive.google.com/file/d/abcdefghijkl/view"}},"hasSignature":true}"""))
        assertEquals("2026-11-20", mine.record!!.learner!!.day)
        assertNull(mine.record!!.trainer)
        assertTrue(mine.hasSignature)
        assertTrue(mine.record!!.fileUrl!!.startsWith("https://drive.google.com"))
        val all = SummaryReply.parse(JSONObject("""{"success":true,"learners":[{"learnerUserId":"u1","learnerName":"Bala","record":null},{"learnerUserId":"u2","learnerName":"","record":{"learner":{"name":"X","signDate":null,"signedAt":"2026-11-20T03:00:00Z"},"trainer":null,"file":null}}]}"""))
        assertEquals(2, all.learners.size)
        assertEquals("Learner", all.learners[1].learnerName)
        assertEquals("2026-11-20T03:00:00Z", all.learners[1].record!!.learner!!.day)

        assertEquals(SignerRecord("Jamie Lim", "S1234567A", true), SignerRecord.parse(JSONObject("""{"exists":true,"data":{"learner_name":"Jamie Lim","nric":"S1234567A","signature_png":"data:image/png;base64,AAA"}}""")))
        assertEquals(SignerRecord("Alex Tan", "", false), SignerRecord.parse(JSONObject("""{"exists":false,"data":{"assessor_name":"Alex Tan","nric":null,"signature_png":null}}""")))

        val learner = signerBody(SignerKind.LEARNER, " Jamie ", "s1234567a", null)
        assertEquals("Jamie", learner.getString("name"))
        assertEquals("S1234567A", learner.getString("nric"))
        assertFalse("no new drawing keeps the saved signature", learner.has("signaturePng"))
        assertFalse(learner.has("signDate"))
        val assessor = signerBody(SignerKind.ASSESSOR, "Alex", "", "data:image/png;base64,AAA", LocalDate.parse("2026-11-23"))
        assertEquals("2026-11-23", assessor.getString("signDate"))
        assertEquals("data:image/png;base64,AAA", assessor.getString("signaturePng"))

        assertTrue(needsSignatureSetup(ApiException(409, "Set up your name, NRIC and signature first.")))
        assertTrue(needsSignatureSetup(ApiException(400, "Set up your assessor name first")))
        assertFalse(needsSignatureSetup(ApiException(500, "Internal server error")))
    }

    @Test
    fun whatsAppTrainerMessageAndLink() {
        val item = StaffClass(
            "x", "1361797", "Agentic AI Applications with Claude Code", "TGS-2025052468", "2026-11-21", "2026-11-22",
            "Hybrid", "Confirmed", true, emptyList(), 3, "12 Woodland Square, Singapore 737715",
        )
        val t = StaffTrainer("Alex Tan", "", "+6591234567")
        val text = trainerMessage(t, item)
        assertTrue(text.startsWith("Hi Alex,"))
        assertEquals(
            listOf(
                "Hi Alex, this is Tertiary Infotech Academy.", "Are you available to train this class?", "",
                "Agentic AI Applications with Claude Code (TGS-2025052468)", "Course Run: 1361797", "Dates: 21 Nov 2026 – 22 Nov 2026",
                "Class type: Hybrid", "Venue: 12 Woodland Square, Singapore 737715", "", "Please reply to confirm. Thank you!",
            ),
            text.split("\n"),
        )
        val url = java.net.URI(whatsAppUrl(t.phone!!, text)!!)
        assertEquals("wa.me", url.host)
        assertEquals("/6591234567", url.path)
        assertTrue(url.rawQuery.startsWith("text=Hi%20Alex"))
        assertEquals(text, url.query.removePrefix("text="))
        assertNull(whatsAppUrl("", "x"))

        // Assigned trainers are matched by name; blank fields are left out of the message.
        assertTrue(StaffTrainer("alex tan", "", null).assignedTo(item.copy(trainers = listOf("Alex Tan"))))
        val bare = trainerMessage(StaffTrainer("Priya", "", null), item.copy(code = "", runCode = "", format = "", venue = ""))
        assertFalse(bare.contains("Course Run") || bare.contains("Venue") || bare.contains("("))
        val parsed = StaffTrainer.parseAll(JSONObject("""{"trainers":[{"name":"Alex Tan","email":"a@x.com","phone":"+6591234567"},{"name":"Priya","email":null,"phone":null},{"name":"","phone":"+65"}]}"""))
        assertEquals(2, parsed.size)
        assertNull(parsed[1].phone)
    }

    // ---------------------------------------------------------------- requests

    @Test
    fun publishSwitchAlsoKeepsTheOlderFlagInStep() = runBlocking {
        server.enqueue(json("""{"success":true}"""))
        server.enqueue(json("""{"success":true}"""))
        repo.publishAssessment("run-1", "writtenAssessment", true)
        val first = server.takeRequest()
        assertEquals("/api/assessments/publish-link", first.path)
        assertEquals("PUT", first.method)
        assertEquals("Bearer lms_abc", first.getHeader("Authorization"))
        val a = JSONObject(first.body.readUtf8())
        assertEquals("run-1", a.getString("courseRunId"))
        assertEquals("writtenAssessment", a.getString("field"))
        assertTrue(a.getBoolean("published"))
        val b = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("written", b.getString("field"))

        server.enqueue(json("""{"success":true}"""))
        repo.publishAssessment("run-1", "caseStudy", false)
        assertEquals("caseStudy", JSONObject(server.takeRequest().body.readUtf8()).getString("field"))
        assertEquals("only one call for an assessment with no older flag", 3, server.requestCount)
    }

    @Test
    fun summaryRecordRoutes() = runBlocking {
        server.enqueue(json("""{"success":true,"record":null,"hasSignature":false}"""))
        repo.summaryRecord("run-1", learnerView = true)
        assertEquals("/api/assessments/summary-record?courseRunId=run-1&view=learner", server.takeRequest().path)

        server.enqueue(json("""{"success":true,"learners":[]}"""))
        repo.summaryRecord("run-1", learnerView = false)
        assertEquals("/api/assessments/summary-record?courseRunId=run-1", server.takeRequest().path)

        server.enqueue(json("""{"success":true,"signed":true,"party":"trainer","generationError":"Template missing"}"""))
        assertEquals("Template missing", repo.signSummaryRecord("run-1", "u9"))
        val sign = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("trainer", sign.getString("party"))
        assertEquals("u9", sign.getString("learnerUserId"))

        server.enqueue(json("""{"success":false,"code":"NO_LEARNER_SIGNATURE","error":"Set up your name, NRIC and signature first."}""", 409))
        try {
            repo.signSummaryRecord("run-1", null)
            fail("Expected a 409")
        } catch (e: ApiException) {
            assertEquals(409, e.status)
            assertTrue(needsSignatureSetup(e))
            assertFalse(e.sessionEnded)
        }
        val mine = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("learner", mine.getString("party"))
        assertFalse(mine.has("learnerUserId"))

        server.enqueue(json("""{"success":true,"exists":true,"data":{}}"""))
        repo.saveSigner(SignerKind.ASSESSOR, "Alex", "S1", "data:image/png;base64,AAA")
        val save = server.takeRequest()
        assertEquals("/api/trainer/assessor-signature", save.path)
        assertEquals("POST", save.method)
    }

    @Test
    fun staffRoutes() = runBlocking {
        server.enqueue(json("""{"classes":[]}"""))
        repo.staffClasses(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-07"))
        assertEquals("/api/mobile/staff?view=classes&from=2026-11-01&to=2026-12-07", server.takeRequest().path)
        server.enqueue(json("""{"courses":[]}"""))
        repo.staffCourses()
        assertEquals("/api/mobile/staff?view=courses", server.takeRequest().path)
        server.enqueue(json("""{"trainers":[]}"""))
        repo.staffTrainers()
        assertEquals("/api/mobile/staff?view=trainers", server.takeRequest().path)
    }

    @Test
    fun anAdminGoogleSignInShowsAdmin() = runBlocking {
        store.token = ""
        server.enqueue(json("""{"success":true,"data":{"user":{"id":"u1","email":"ops@example.com","fullName":"Ops","roles":["admin"]},"token":"lms_g"}}"""))
        repo.googleLogin("code", "verifier")
        assertEquals("/api/mobile/google-exchange", server.takeRequest().path)
        assertEquals("admin", store.role)
        assertEquals("admin", store.loginRole)
        assertTrue(store.adminSession)

        server.enqueue(json("""{"success":true,"data":{"user":{"id":"u2","email":"l@b.c","fullName":"Lee","roles":["learner"]},"token":"lms_o"}}"""))
        repo.login("l@b.c", "123456")
        assertFalse("any other sign-in ends the admin session", store.adminSession)
        assertEquals("learner", store.role)
    }

    // ---------------------------------------------------------------- demo

    @Test
    fun demoSigningNeedsASavedSignatureFirst() = runBlocking {
        val api = DemoApi(today = { LocalDate.parse("2026-11-10") }, pause = 0)
        assertFalse(api.summaryRecord("demo-ai", true).hasSignature)
        try {
            api.signSummaryRecord("demo-ai", null)
            fail("Expected a 409")
        } catch (e: ApiException) {
            assertTrue(needsSignatureSetup(e))
        }
        api.saveSigner(SignerKind.LEARNER, "Alex Tan", "S0000000A", "data:image/png;base64,AAA")
        api.signSummaryRecord("demo-ai", null)
        assertEquals("2026-11-10", api.summaryRecord("demo-ai", true).record!!.learner!!.signDate)

        val before = api.summaryRecord("demo-ai", false).learners
        assertEquals(4, before.size)
        assertTrue(before.none { it.record?.trainer != null })
        api.saveSigner(SignerKind.ASSESSOR, "Alex Tan", "", "data:image/png;base64,AAA")
        before.forEach { api.signSummaryRecord("demo-ai", it.learnerUserId) }
        assertTrue(api.summaryRecord("demo-ai", false).learners.all { it.record?.trainer != null })
    }

    @Test
    fun demoPublishSwitchHidesThePaperFromLearners() = runBlocking {
        val api = DemoApi(today = { LocalDate.parse("2026-11-10") }, pause = 0)
        api.publishAssessment("demo-ai", "practicalExam", false)
        assertFalse(api.classDetail("demo-ai", "trainer").assessments.first { it.type == "practicalExam" }.published)
        assertEquals(listOf("writtenAssessment"), api.classDetail("demo-ai", "learner").assessments.map { it.type })
    }

    @Test
    fun demoHasThePanelsAndStaffData() = runBlocking {
        val api = DemoApi(today = { LocalDate.parse("2026-11-10") }, pause = 0)
        val trainer = api.classDetail("demo-ai", "trainer")
        assertEquals("RA000000", trainer.digitalAttendanceId)
        assertTrue(trainer.feedbackSummary!!.comments.size > 3)
        assertEquals("/enrol/demo-ai", trainer.swdaEnrolmentUrl)
        assertEquals(listOf("courseware-link", "lesson-plan", "guide", "trainer-slides", "activities"), trainer.materials.map { it.id })
        val learner = api.classDetail("demo-ai", "learner")
        assertNull(learner.feedbackSummary)
        assertEquals("", learner.swdaEnrolmentUrl)

        val today = LocalDate.parse("2026-11-10")
        val classes = api.staffClasses(today, today.plusDays(30))
        assertTrue(classes.any { it.covers(today) } && classes.any { it.cancelled })
        assertTrue(api.staffClasses(today.plusDays(40), today.plusDays(50)).isEmpty())
        assertTrue(api.staffCourses().any { it.expired(today) })
        assertEquals(listOf("+6580000000", null), api.staffTrainers().map { it.phone })
    }
}
