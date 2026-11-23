package com.tertiaryinfotech.ailmstms

import com.tertiaryinfotech.ailmstms.data.AssessmentMethod
import com.tertiaryinfotech.ailmstms.data.Certificate
import com.tertiaryinfotech.ailmstms.data.DemoApi
import com.tertiaryinfotech.ailmstms.data.Repository
import com.tertiaryinfotech.ailmstms.data.Roster
import com.tertiaryinfotech.ailmstms.data.StaffCourse
import com.tertiaryinfotech.ailmstms.data.SupportInfo
import com.tertiaryinfotech.ailmstms.data.TrainerCredentials
import com.tertiaryinfotech.ailmstms.data.UploadSource
import com.tertiaryinfotech.ailmstms.data.addTag
import com.tertiaryinfotech.ailmstms.data.classStatusLabel
import com.tertiaryinfotech.ailmstms.data.fundingExpiresText
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Announcements, Assessment Grading, certificates, Support, trainer credentials, class status chips and the developer catalogue. */
class TrainerLearnerPagesTest {
    private val server = MockWebServer()
    private val store = MemoryState()
    private lateinit var repo: Repository
    private val today = LocalDate.parse("2026-11-23")

    @Before
    fun start() {
        server.start()
        store.token = "lms_abc"
        repo = Repository(store, server.url("/").toString().trimEnd('/'))
    }

    @After
    fun stop() = server.shutdown()

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    // ---------------------------------------------------------------- pure logic

    @Test
    fun classStatusChipReadsLikeTheTms() {
        assertEquals("Confirmed", classStatusLabel("Confirmed"))
        assertEquals("Pending", classStatusLabel(""))
        assertEquals("Cancelled", classStatusLabel("canceled"))
        assertEquals("Reschedule", classStatusLabel(" Reschedule "))
    }

    @Test
    fun developerCatalogueFlagsFundingEndingWithinThreeMonths() {
        fun course(end: String, start: String = "", title: String = "AI") =
            StaffCourse("c", title, "TGS-1", "WSQ", "", "", "", 7.0, 1.0, end, 0, "", start)
        assertEquals(45, course("2027-01-07").expiringInDays(today))
        assertEquals(90, course("2027-02-21").expiringInDays(today))
        assertNull("beyond 90 days", course("2027-02-22").expiringInDays(today))
        assertEquals(0, course("2026-11-23").expiringInDays(today))
        assertNull("already expired", course("2026-11-22").expiringInDays(today))
        assertNull("a title marked (Expired)", course("2027-01-07", title = "Old (Expired)").expiringInDays(today))
        assertNull(course("").expiringInDays(today))
        assertEquals("Funding validity: 30 Jan 2025 – 29 Jan 2027", course("2027-01-29", "2025-01-30").fundingValidityText)
        assertEquals("Funding validity: N/A – 29 Jan 2027", course("2027-01-29").fundingValidityText)
        assertNull(course("").fundingValidityText)
        assertEquals("Funding expires today", fundingExpiresText(0))
        assertEquals("Funding expires in 1 day", fundingExpiresText(1))
        assertEquals("Funding expires in 45 days", fundingExpiresText(45))
        assertEquals("2025-01-30", StaffCourse.parseAll(JSONObject("""{"courses":[{"id":"c","title":"AI","fundingValidityStart":"2025-01-30","fundingValidityEnd":"2027-01-29","upcomingRuns":1}]}"""))[0].fundingValidityStart)
    }

    @Test
    fun certificatesKeepOnlyHttpsLinks() {
        val list = Certificate.parseAll(JSONObject("""{"certificates":[
          {"id":"e1","courseID":"r1","title":"AI","code":"TGS-1","runCode":"100","startDate":"2026-10-01","endDate":"2026-10-02","result":"Competent","certificateURL":"https://drive.google.com/file/d/abcdefghijkl/view"},
          {"id":"e2","courseID":"r2","title":"Data","code":null,"runCode":null,"startDate":"2026-11-01","endDate":null,"result":null,"certificateURL":"http://insecure"}]}"""))
        assertEquals(2, list.size)
        assertTrue(list[0].certificateUrl!!.startsWith("https://"))
        assertNull(list[1].certificateUrl)
        assertEquals("", list[1].result)
    }

    @Test
    fun supportContactsAndPhoneDigits() {
        val info = SupportInfo.parse(JSONObject("""{"email":"enquiry@example.com","phone":"6100 0613","whatsapp":null,"address":"12 Woodlands Square","feedback":true}"""))
        assertEquals("enquiry@example.com", info.email)
        assertNull(info.whatsapp)
        assertTrue(info.feedback)
        assertEquals("6561000613", SupportInfo.digits("6100 0613"))
        assertEquals("6588666375", SupportInfo.digits("+65 8866 6375"))
    }

    @Test
    fun trainerCredentialBadgesAndTags() {
        val c = TrainerCredentials.parse(JSONObject("""{"aclp":true,"dddpl":false,"taepp":true,"areasOfExpertise":["AI"],"skillsTags":[]}"""))
        assertEquals(listOf("ACLP", "TAEPP"), c.badges)
        assertEquals(listOf("AI", "Cybersecurity"), addTag(c.areasOfExpertise, "  Cybersecurity "))
        assertNull("case-insensitive duplicate", addTag(c.areasOfExpertise, "ai"))
        assertNull(addTag(c.areasOfExpertise, "   "))
    }

    @Test
    fun gradingRosterReadsTheWebsiteForm() {
        val roster = Roster.parse(JSONObject("""{"students":[
          {"enrolment_id":"e1","user_id":"u1","student_name":"Jamie","competent_status":"","source":"manual","is_competent":false,"submitted_assessments":["writtenAssessment","practicalExam"],"traqom_completed":false,"submission_count":2,"assessor_signed":false,"assessment_folder_url":"https://drive.google.com/drive/folders/abc"},
          {"enrolment_id":"e2","user_id":null,"student_name":"Priya","competent_status":"Not Yet Competent","source":"tpg","is_competent":false,"submitted_assessments":["writtenAssessment"],"traqom_completed":true,"submission_count":1,"assessor_signed":false,"assessment_folder_url":null,"enrolment_ids":["e2","e2b"],"user_ids":[]},
          {"enrolment_id":"e3","user_id":"u3","student_name":"Wei","competent_status":"Competent","is_competent":true,"submitted_assessments":[],"submission_count":0,"assessor_signed":false}],
          "assessment_methods":["writtenAssessment","practicalExam","caseStudy"]}"""))
        assertEquals(listOf("writtenAssessment", "practicalExam", "caseStudy"), roster.methods)
        val (a, b, c) = roster.students
        assertTrue(a.submitted("practicalExam") && !b.submitted("practicalExam"))
        assertEquals(listOf("e2", "e2b"), b.enrolmentIds)
        assertEquals(listOf("e1"), a.enrolmentIds)
        assertTrue("no account: cannot be signed", b.userIds.isEmpty() && !b.signable)
        assertFalse("nothing submitted: cannot be signed", c.signable)
        assertEquals(listOf("e1"), roster.unsigned.map { it.enrolmentId })
        assertTrue(b.notYetCompetent && !a.notYetCompetent)
        assertEquals("Result: not graded", a.resultText)
        assertEquals("Result: Competent", c.resultText)
        assertEquals("manual", c.source)
        assertEquals(AssessmentMethod.DEFAULT, Roster.parse(JSONObject("""{"students":[]}""")).methods)
        assertEquals("CS", AssessmentMethod.abbr("caseStudy"))
        assertEquals("custom", AssessmentMethod.abbr("custom"))
        assertEquals(
            "WA = Written Assessment · PP = Practical Performance · Signed = your assessor signature stamped on the learner's submitted files. TRAQOM is ticked by you: SSG does not report completion.",
            AssessmentMethod.legend(AssessmentMethod.DEFAULT),
        )
    }

    // ---------------------------------------------------------------- requests

    @Test
    fun announcementIsPostedAsMultipartAndDeletedById() = runBlocking {
        server.enqueue(json("""{"success":true,"announcement":{}}"""))
        val bytes = "%PDF-1.4 sample".toByteArray()
        repo.postAnnouncement("run-1", "Alex Tan", "  Bring your laptop  ", UploadSource("notes \"v2\".pdf", "application/pdf", bytes.size.toLong()) { bytes.inputStream() })
        val post = server.takeRequest()
        assertEquals("/api/trainer/create-announcement", post.path)
        assertEquals("POST", post.method)
        assertEquals("Bearer lms_abc", post.getHeader("Authorization"))
        assertTrue(post.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = post.body.readUtf8()
        assertTrue(body.contains("name=\"courseRunId\"\r\nContent-Length: 5\r\n\r\nrun-1") || body.contains("name=\"courseRunId\"") && body.contains("run-1"))
        assertTrue(body.contains("name=\"postedBy\"") && body.contains("Alex Tan"))
        assertTrue(body.contains("Bring your laptop\r\n"))
        assertTrue("the file part is named file, quotes removed from its name", body.contains("name=\"file\"; filename=\"notes v2.pdf\""))
        assertTrue(body.contains("%PDF-1.4 sample"))

        server.enqueue(json("""{"success":true}"""))
        repo.postAnnouncement("run-1", "Alex Tan", "   ", null)
        val textless = server.takeRequest().body.readUtf8()
        assertFalse("a blank message is left out", textless.contains("name=\"message\""))
        assertFalse(textless.contains("name=\"file\""))

        server.enqueue(json("""{"success":true,"message":"Announcement deleted"}"""))
        repo.deleteAnnouncement("a-9")
        val delete = server.takeRequest()
        assertEquals("/api/course/announcements?id=a-9", delete.path)
        assertEquals("DELETE", delete.method)
    }

    @Test
    fun gradingRoutes() = runBlocking {
        server.enqueue(json("""{"students":[{"enrolment_id":"e1","user_id":"u1","student_name":"Jamie","source":"tpg","enrolment_ids":["e1","e1b"],"user_ids":["u1"]}],"assessment_methods":["writtenAssessment"]}"""))
        val roster = repo.roster("run-1")
        assertEquals("/api/trainer/class-students?courseRunId=run-1&withMeta=1", server.takeRequest().path)
        val s = roster.students[0]

        server.enqueue(json("""{"message":"Competency updated successfully","status":"Competent"}"""))
        repo.gradeStudent(s, true)
        val grade = server.takeRequest()
        assertEquals("/api/trainer/grade-student", grade.path)
        val g = JSONObject(grade.body.readUtf8())
        assertEquals(2, g.getJSONArray("enrolmentIds").length())
        assertEquals("tpg", g.getString("source"))
        assertTrue(g.getBoolean("isCompetent"))

        server.enqueue(json("""{"message":"TRAQOM status updated","updated":2,"completed":true}"""))
        repo.setTraqom(s, true)
        val t = server.takeRequest()
        assertEquals("/api/trainer/traqom-status", t.path)
        assertTrue(JSONObject(t.body.readUtf8()).getBoolean("completed"))

        server.enqueue(json("""{"success":true,"signed":false,"results":[]}"""))
        repo.signAssessments("run-1", s.userIds, false)
        val sign = server.takeRequest()
        assertEquals("/api/trainer/sign-assessments", sign.path)
        val b = JSONObject(sign.body.readUtf8())
        assertEquals("run-1", b.getString("courseRunId"))
        assertEquals("u1", b.getJSONArray("learnerUserIds").getString(0))
        assertFalse(b.getBoolean("signed"))
    }

    @Test
    fun certificatesSupportAndCredentialRoutes() = runBlocking {
        server.enqueue(json("""{"certificates":[]}"""))
        repo.certificates()
        assertEquals("/api/mobile/certificates", server.takeRequest().path)

        server.enqueue(json("""{"email":null,"phone":"61000613","whatsapp":null,"address":null,"feedback":false}"""))
        assertEquals("61000613", repo.support().phone)
        assertEquals("GET", server.takeRequest().method)

        server.enqueue(json("""{"success":true}"""))
        repo.sendSupport(" Login ", " I cannot see my class ")
        val sent = server.takeRequest()
        assertEquals("/api/mobile/support", sent.path)
        assertEquals("POST", sent.method)
        val body = JSONObject(sent.body.readUtf8())
        assertEquals("Login", body.getString("subject"))
        assertEquals("I cannot see my class", body.getString("message"))

        server.enqueue(json("""{"aclp":false,"dddpl":true,"taepp":false,"areasOfExpertise":[],"skillsTags":["Python"]}"""))
        val saved = repo.saveTrainerCredentials(JSONObject().put("dddpl", true))
        val post = server.takeRequest()
        assertEquals("/api/mobile/trainer-profile", post.path)
        assertEquals("POST", post.method)
        assertEquals("""{"dddpl":true}""", post.body.readUtf8())
        assertEquals(listOf("DDDPL"), saved.badges)
    }

    // ---------------------------------------------------------------- demo

    @Test
    fun demoAnnouncementsGradingAndCredentialsStayInMemory() = runBlocking {
        val api = DemoApi(today = { today }, pause = 0)
        val never = UploadSource("photo.jpg", "image/jpeg", 10) { throw AssertionError("the demo must never read the picked file") }
        api.postAnnouncement("demo-ai", "Alex Tan", "Room changed to 3-01", never)
        val posted = api.classDetail("demo-ai", "trainer").announcements
        assertEquals("Room changed to 3-01", posted[0].message)
        assertEquals("photo.jpg", posted[0].fileName)
        api.deleteAnnouncement(posted[0].id)
        assertEquals(1, api.classDetail("demo-ai", "trainer").announcements.size)

        val roster = api.roster("demo-ai")
        assertEquals(3, roster.students.size)
        assertEquals(2, roster.unsigned.size)
        api.signAssessments("demo-ai", roster.unsigned.flatMap { it.userIds }, true)
        assertTrue(api.roster("demo-ai").students.all { it.assessorSigned })
        api.setTraqom(roster.students[0], true)
        api.gradeStudent(roster.students[0], false)
        val first = api.roster("demo-ai").students[0]
        assertTrue(first.traqomCompleted && first.notYetCompetent)

        assertEquals(listOf("ACLP"), api.trainerCredentials().badges)
        val c = api.saveTrainerCredentials(JSONObject().put("taepp", true).put("skillsTags", org.json.JSONArray(listOf("Python", "Power BI"))))
        assertEquals(listOf("ACLP", "TAEPP"), c.badges)
        assertEquals(listOf("Python", "Power BI"), c.skillsTags)

        val certs = api.certificates()
        assertTrue(certs.any { it.certificateUrl != null } && certs.any { it.certificateUrl == null })
        assertEquals(SupportInfo.SAMPLE, api.support())
        val courses = api.staffCourses()
        assertTrue(courses.any { it.expiringInDays(today) != null })
    }
}
