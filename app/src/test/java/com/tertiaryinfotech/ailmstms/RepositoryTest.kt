package com.tertiaryinfotech.ailmstms

import com.tertiaryinfotech.ailmstms.data.ApiException
import com.tertiaryinfotech.ailmstms.data.AttendanceLearner
import com.tertiaryinfotech.ailmstms.data.FeedbackForm
import com.tertiaryinfotech.ailmstms.data.ProfilePatch
import com.tertiaryinfotech.ailmstms.data.Repository
import com.tertiaryinfotech.ailmstms.data.SessionState
import com.tertiaryinfotech.ailmstms.data.ThemeMode
import com.tertiaryinfotech.ailmstms.data.UploadSource
import com.tertiaryinfotech.ailmstms.data.UploadedFile
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

class MemoryState : SessionState {
    override var token = ""
    override var profile = ""
    override var role = "learner"
    override var pushEnabled = false
    override var pushToken = ""
    override var theme = "system"
    override var brandColor = ""
    override var userProfile = ""
    override var mustChangePassword = false
    override fun clear() {
        token = ""; profile = ""; role = "learner"; pushEnabled = false; theme = "system"; brandColor = ""; userProfile = ""; mustChangePassword = false
    }
}

class RepositoryTest {
    private val server = MockWebServer()
    private val store = MemoryState()
    private lateinit var repo: Repository

    @Before
    fun start() {
        server.start()
        repo = Repository(store, server.url("/").toString().trimEnd('/'))
    }

    @After
    fun stop() = server.shutdown()

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private inline fun expectApiError(block: () -> Unit): ApiException {
        try {
            block()
        } catch (e: ApiException) {
            return e
        }
        fail("Expected an ApiException")
        throw IllegalStateException()
    }

    @Test
    fun passwordSignInStoresTheSessionAndTheForcedChange() = runBlocking {
        server.enqueue(json("""{"data":{"user":{"id":"u1","email":"a@b.c","fullName":"Alex Tan","role":"learner","roles":["learner","trainer"]},"token":"lms_abc","forcePasswordChange":true}}"""))
        val result = repo.passwordLogin("a@b.c", "secret")
        val request = server.takeRequest()
        assertEquals("/api/mobile/login", request.path)
        assertEquals("POST", request.method)
        assertNull("the public sign-in route never carries a token", request.getHeader("Authorization"))
        val sent = JSONObject(request.body.readUtf8())
        assertEquals("a@b.c", sent.getString("email"))
        assertEquals("secret", sent.getString("password"))
        assertTrue(result.forcePasswordChange)
        assertEquals(listOf("learner", "trainer"), result.profile.roles)
        assertEquals("lms_abc", store.token)
        assertEquals("learner", store.role)
        assertTrue(store.mustChangePassword)
    }

    @Test
    fun wrongPasswordAtSignInIsAnErrorNotAnEndedSession() = runBlocking {
        server.enqueue(json("""{"error":"Incorrect email or password."}""", 401))
        val e = expectApiError { repo.passwordLogin("a@b.c", "nope") }
        assertEquals("Incorrect email or password.", e.message)
        assertFalse(e.sessionEnded)
        assertEquals("", store.token)
    }

    @Test
    fun accountsWithoutALearnerOrTrainerRoleAreRefused() = runBlocking {
        server.enqueue(json("""{"data":{"user":{"id":"u1","roles":["admin"]},"token":"lms_abc"}}"""))
        try {
            repo.passwordLogin("a@b.c", "secret")
            fail("Expected a refusal")
        } catch (e: IllegalArgumentException) {
            assertEquals("This app is for learners and trainers only.", e.message)
        }
        assertEquals("", store.token)
    }

    @Test
    fun otpSignInStillUsesTheAndroidRoute() = runBlocking {
        server.enqueue(json("""{"success":true,"data":{"user":{"id":"u1","email":"a@b.c","fullName":"Alex","roles":["trainer"]},"token":"lms_otp"}}"""))
        repo.login("a@b.c", "123456")
        val request = server.takeRequest()
        assertEquals("/api/android/login", request.path)
        assertEquals("otp", JSONObject(request.body.readUtf8()).getString("loginType"))
        assertEquals("trainer", store.role)
        assertFalse(store.mustChangePassword)
    }

    @Test
    fun aRejectedTokenEndsTheSessionOnAnyRoute() = runBlocking {
        store.token = "lms_abc"
        server.enqueue(json("""{"success":false,"error":"Not authenticated"}""", 401))
        val e = expectApiError { repo.classDetail("run-1", "learner") }
        assertTrue(e.sessionEnded)
        val request = server.takeRequest()
        assertEquals("/api/mobile/class?id=run-1&role=learner", request.path)
        assertEquals("Bearer lms_abc", request.getHeader("Authorization"))
    }

    @Test
    fun aRouteThatIsNotDeployedIsReportedAsUnavailable() = runBlocking {
        store.token = "lms_abc"
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>Not found</html>"))
        val e = expectApiError { repo.classDetail("run-1", "learner") }
        assertFalse(e.fromApi)
        assertFalse(e.sessionEnded)
        assertEquals(404, e.status)
    }

    @Test
    fun dashboardKeepsTheAndroidRouteAndReadsRunCodes() = runBlocking {
        store.token = "lms_abc"
        server.enqueue(json("""{"courses":[{"id":"c1","title":"AI","code":"TGS","runCode":"RUN-9","classStatus":"Confirmed","slidesURL":"https://example.com/a.pdf"}],"sessions":[{"id":"s1","courseID":"c1","title":"AI","startDate":"20261116","startTime":"09:00","endTime":"17:00"}]}"""))
        val d = repo.dashboard("trainer")
        assertEquals("/api/android/dashboard?role=trainer", server.takeRequest().path)
        assertEquals("RUN-9", d.courses[0].runCode)
        assertEquals("2026-11-16", d.sessions[0].date.toString())
    }

    @Test
    fun profileIsCachedForTheNextLaunchAndPatchedWithOnlyTheChange() = runBlocking {
        store.token = "lms_abc"
        store.profile = """{"id":"u1","fullName":"Old Name","roles":["learner"]}"""
        val body = """{"data":{"id":"u1","email":"a@b.c","fullName":"Alex Tan","pictureURL":null,"theme":"dark","phone":"+65 6000 0000","company":null,"employmentStatus":"Employed","linkedinURL":null,"brandColor":"#0A3D8F","academy":"TIA"}}"""
        server.enqueue(json(body))
        val p = repo.profile()
        assertEquals("/api/mobile/profile", server.takeRequest().path)
        assertEquals(ThemeMode.DARK, p.theme)
        assertEquals("dark", store.theme)
        assertEquals("#0A3D8F", store.brandColor)
        assertEquals("Alex Tan", JSONObject(store.profile).getString("fullName"))
        assertEquals("Alex Tan", JSONObject(store.userProfile).getString("fullName"))

        server.enqueue(json(body.replace("dark", "light")))
        repo.updateProfile(ProfilePatch(theme = ThemeMode.LIGHT))
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertEquals("""{"theme":"light"}""", patch.body.readUtf8())
        assertEquals("light", store.theme)
    }

    @Test
    fun aWrongCurrentPasswordDoesNotSignTheUserOut() = runBlocking {
        store.token = "lms_abc"
        store.mustChangePassword = true
        server.enqueue(json("""{"error":"Your current password is incorrect."}""", 401))
        server.enqueue(json("""{"data":{"id":"u1","theme":"system"}}""")) // the session check still passes
        val e = expectApiError { repo.changePassword("wrong", "new-password") }
        assertFalse(e.sessionEnded)
        assertEquals("Your current password is incorrect.", e.message)
        assertEquals("/api/mobile/password", server.takeRequest().path)
        assertEquals("/api/mobile/profile", server.takeRequest().path)
        assertTrue(store.mustChangePassword)
    }

    @Test
    fun anEndedSessionDuringAPasswordChangeStillSignsOut() = runBlocking {
        store.token = "lms_abc"
        server.enqueue(json("""{"success":false,"error":"Not authenticated"}""", 401))
        server.enqueue(json("""{"success":false,"error":"Not authenticated"}""", 401))
        assertTrue(expectApiError { repo.changePassword("old", "new-password") }.sessionEnded)
    }

    @Test
    fun passwordChangeClearsTheForcedFlag() = runBlocking {
        store.token = "lms_abc"
        store.mustChangePassword = true
        server.enqueue(json("""{"success":true}"""))
        repo.changePassword("old-password", "new-password")
        val sent = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("old-password", sent.getString("currentPassword"))
        assertEquals("new-password", sent.getString("newPassword"))
        assertFalse(store.mustChangePassword)
    }

    @Test
    fun trainerAttendanceIsReadAndMarked() = runBlocking {
        store.token = "lms_abc"
        server.enqueue(json("""{"data":{"checkinURL":"https://lms-tms.tertiaryinfotech.com/checkin/0123456789abcdef0123456789abcdef","learners":[{"enrolmentId":"e1","name":"Bala","isPresent":null,"reason":""},{"enrolmentId":"e2","name":"Mei","isPresent":false,"reason":"MC"}]}}"""))
        val sheet = repo.attendance("sess-1")
        assertEquals("/api/mobile/attendance?sessionId=sess-1", server.takeRequest().path)
        assertTrue(sheet.checkinUrl!!.endsWith("0123456789abcdef0123456789abcdef"))
        assertNull(sheet.learners[0].isPresent)
        assertEquals("MC", sheet.learners[1].reason)

        server.enqueue(json("""{"success":true,"saved":2,"data":{"learners":[{"enrolmentId":"e1","name":"Bala","isPresent":true,"reason":""},{"enrolmentId":"e2","name":"Mei","isPresent":false,"reason":"MC"}]}}"""))
        val saved = repo.markAttendance("sess-1", listOf(AttendanceLearner("e1", "Bala", true, ""), AttendanceLearner("e2", "Mei", false, "MC"), AttendanceLearner("e3", "Zed", null, "")))
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("mark", body.getString("action"))
        assertEquals("sess-1", body.getString("sessionId"))
        assertEquals(2, body.getJSONArray("records").length())
        assertEquals(true, saved.learners[0].isPresent)
    }

    @Test
    fun learnerCheckInSendsTheScannedTextAndShowsTheServerMessage() = runBlocking {
        store.token = "lms_abc"
        server.enqueue(json("""{"success":true,"message":"You are checked in.","already":false,"course":"AI","session":"Day 1 · AM"}"""))
        val result = repo.checkIn(" https://lms-tms.tertiaryinfotech.com/checkin/0123456789abcdef0123456789abcdef ")
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("checkin", body.getString("action"))
        assertEquals("https://lms-tms.tertiaryinfotech.com/checkin/0123456789abcdef0123456789abcdef", body.getString("code"))
        assertEquals("You are checked in.", result.message)
        assertEquals("Day 1 · AM", result.session)

        server.enqueue(json("""{"error":"Check-in for this class has closed. Please speak to your trainer."}""", 409))
        assertEquals("Check-in for this class has closed. Please speak to your trainer.", expectApiError { repo.checkIn("x") }.message)
    }

    @Test
    fun assessmentFileIsUploadedAsMultipartThenRecordedWithTheStudentFolder() = runBlocking {
        store.token = "lms_abc"
        server.enqueue(json("""{"success":true,"data":{"fileId":"f1","fileUrl":"https://drive.google.com/file/d/1AbCdEfGhIjKlMnOp/view?usp=drivesdk","studentFolderId":"1FolderIdAbCdEfGh","studentFolder":"Alex Tan"}}"""))
        val bytes = "%PDF-1.4 answer".toByteArray()
        val progress = mutableListOf<Float>()
        val file = repo.uploadAssessment("Alex Tan", "RUN 9/A", UploadSource("My answer.pdf", "application/pdf", bytes.size.toLong()) { bytes.inputStream() }) { progress.add(it) }
        val upload = server.takeRequest()
        assertEquals("/api/upload/google-drive?studentName=Alex%20Tan&courseRunId=RUN%209%2FA", upload.path)
        assertEquals("Bearer lms_abc", upload.getHeader("Authorization"))
        assertTrue(upload.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = upload.body.readUtf8()
        assertTrue(body.contains("""name="file"; filename="My answer.pdf""""))
        assertTrue(body.contains("%PDF-1.4 answer"))
        assertEquals(1f, progress.last())
        assertEquals(UploadedFile("https://drive.google.com/file/d/1AbCdEfGhIjKlMnOp/view?usp=drivesdk", "1FolderIdAbCdEfGh"), file)

        server.enqueue(json("""{"success":true,"id":"sub-1"}""", 201))
        repo.submitAssessment("run-1", "writtenAssessment", "My answer.pdf", file)
        val submit = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("submit", submit.getString("action"))
        assertEquals("run-1", submit.getString("runId"))
        assertEquals("writtenAssessment", submit.getString("type"))
        assertEquals("My answer.pdf", submit.getString("fileName"))
        assertEquals(file.fileUrl, submit.getString("fileURL"))
        assertEquals("1FolderIdAbCdEfGh", submit.getString("folderId"))
    }

    @Test
    fun anUploadThatDoesNotReturnADriveLinkIsAFailure() = runBlocking {
        store.token = "lms_abc"
        server.enqueue(json("""{"success":true,"data":{"fileUrl":"https://evil.example/file"}}"""))
        val bytes = ByteArray(8)
        expectApiError { repo.uploadAssessment("A", "R", UploadSource("a.bin", "", 8) { bytes.inputStream() }) {} }
        server.enqueue(json("""{"success":false,"error":"Google Drive Root Folder ID is not configured."}""", 500))
        assertEquals("Google Drive Root Folder ID is not configured.", expectApiError { repo.uploadAssessment("A", "R", UploadSource("a.bin", "", 8) { bytes.inputStream() }) {} }.message)
    }

    @Test
    fun submissionsAreDeletedAndLearnersGraded() = runBlocking {
        store.token = "lms_abc"
        server.enqueue(json("""{"success":true}"""))
        repo.deleteSubmission("sub-1")
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("""{"submissionId":"sub-1"}""", delete.body.readUtf8())

        server.enqueue(json("""{"success":true,"status":"Not Yet Competent"}"""))
        assertEquals("Not Yet Competent", repo.grade(listOf("e1"), false))
        val grade = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("grade", grade.getString("action"))
        assertEquals("e1", grade.getJSONArray("enrolmentIds").getString(0))
        assertFalse(grade.getBoolean("isCompetent"))
    }

    @Test
    fun feedbackFormIsFetchedAndAnswersPosted() = runBlocking {
        store.token = "lms_abc"
        server.enqueue(json("""{"data":{"title":"Course Feedback","submitted":false,"autofill":{"course_title":"AI"},"sections":[{"id":"s","title":"Evaluation","fields":[{"id":"rate","label":"Rate","type":"rating1to5","required":true},{"id":"course_title","label":"Course","type":"text","autofill":"course_title","readonly":true}]}]}}"""))
        val form: FeedbackForm = repo.feedbackForm("run-1")
        assertEquals("/api/mobile/feedback?runId=run-1", server.takeRequest().path)
        assertEquals(2, form.fields.size)

        server.enqueue(json("""{"success":true}""", 201))
        repo.submitFeedback("run-1", form, mapOf("rate" to "4"))
        val sent = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("run-1", sent.getString("runId"))
        assertEquals(4, sent.getJSONObject("answers").getInt("rate"))

        server.enqueue(json("""{"error":"You have already sent feedback for this class. Thank you."}""", 409))
        assertEquals(409, expectApiError { repo.submitFeedback("run-1", form, mapOf("rate" to "4")) }.status)
    }
}
