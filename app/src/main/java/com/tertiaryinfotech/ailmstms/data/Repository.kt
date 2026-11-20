package com.tertiaryinfotech.ailmstms.data

import com.tertiaryinfotech.ailmstms.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** The result of a password sign-in. */
data class PasswordLogin(val profile: Profile, val forcePasswordChange: Boolean)

class Repository(val store: SessionState, private val base: String = BuildConfig.API_BASE_URL) : LmsApi {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    // Uploads go to the same origin but need time for a large file on a slow connection.
    private val uploadHttp = http.newBuilder()
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private fun url(path: String, query: List<Pair<String, String>>) =
        (base + path).toHttpUrl().newBuilder().apply { query.forEach { addQueryParameter(it.first, it.second) } }.build()

    private fun read(response: Response, hadToken: Boolean): JSONObject {
        val raw = response.body?.string().orEmpty()
        val ended = response.code == 401 && hadToken
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: throw ApiException(
            response.code,
            if (response.code == 403) "Your account does not have access to this." else "The service is temporarily unavailable. Please try again.",
            sessionEnded = ended,
            fromApi = false,
        )
        if (!response.isSuccessful || (json.has("success") && !json.optBoolean("success"))) {
            throw ApiException(response.code, json.text("error").ifEmpty { "Unable to complete the request. Please try again." }, sessionEnded = ended)
        }
        return json
    }

    /** One JSON call. [auth] is false for the public sign-in routes, which never send the token. */
    suspend fun request(
        path: String,
        body: JSONObject? = null,
        method: String = if (body == null) "GET" else "POST",
        query: List<Pair<String, String>> = emptyList(),
        auth: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val token = if (auth) store.token else ""
        val builder = Request.Builder().url(url(path, query)).header("Accept", "application/json")
        if (token.isNotEmpty()) builder.header("Authorization", "Bearer $token")
        if (method != "GET") builder.method(method, (body ?: JSONObject()).toString().toRequestBody(JSON))
        http.newCall(builder.build()).execute().use { read(it, token.isNotEmpty()) }
    }

    private fun data(json: JSONObject): JSONObject =
        json.optJSONObject("data") ?: throw ApiException(502, "The service sent an unexpected answer. Please try again.", fromApi = false)

    // ---------------------------------------------------------------- sign-in and session

    suspend fun sendOtp(email: String) {
        request("/api/android/send-otp", JSONObject().put("email", email), auth = false)
    }

    private fun startSession(data: JSONObject): Profile {
        val user = data.getJSONObject("user")
        val profile = Profile.parse(user)
        require(profile.roles.isNotEmpty()) { "This app is for learners and trainers only." }
        val token = data.text("token")
        require(token.startsWith("lms_")) { "Invalid sign-in response. Please try again." }
        store.token = token
        store.profile = user.toString()
        store.role = profile.roles.first()
        return profile
    }

    suspend fun login(email: String, otp: String): Profile {
        val body = JSONObject().put("email", email).put("otp", otp).put("loginType", "otp")
        return startSession(data(request("/api/android/login", body, auth = false)))
    }

    suspend fun passwordLogin(email: String, password: String): PasswordLogin {
        val data = data(request("/api/mobile/login", JSONObject().put("email", email).put("password", password), auth = false))
        val profile = startSession(data)
        val force = data.optBoolean("forcePasswordChange")
        store.mustChangePassword = force
        return PasswordLogin(profile, force)
    }

    suspend fun registerPush(enabled: Boolean) {
        if (store.pushToken.isNotEmpty()) request("/api/android/device", JSONObject().put("token", store.pushToken).put("enabled", enabled))
        store.pushEnabled = enabled
    }

    suspend fun logout() {
        try {
            if (store.pushToken.isNotEmpty()) request("/api/android/device", JSONObject().put("token", store.pushToken), "DELETE")
            request("/api/auth/logout", JSONObject())
        } finally {
            store.clear()
        }
    }

    // ---------------------------------------------------------------- LmsApi

    override suspend fun dashboard(role: String): Dashboard =
        Dashboard.parse(request("/api/android/dashboard", query = listOf("role" to role)))

    private fun remember(profile: UserProfile): UserProfile {
        store.userProfile = profile.toJson().toString()
        store.theme = profile.theme.key
        store.brandColor = profile.brandColor
        // Keep the signed-in identity's name in step with an edited profile.
        runCatching { store.profile = JSONObject(store.profile).put("fullName", profile.fullName).toString() }
        return profile
    }

    override suspend fun profile(): UserProfile = remember(UserProfile.parse(data(request("/api/mobile/profile"))))

    override suspend fun updateProfile(patch: ProfilePatch): UserProfile =
        remember(UserProfile.parse(data(request("/api/mobile/profile", patch.toJson(), "PATCH"))))

    override suspend fun changePassword(current: String, new: String) {
        try {
            request("/api/mobile/password", JSONObject().put("currentPassword", current).put("newPassword", new))
        } catch (e: ApiException) {
            if (e.status != 401) throw e
            // This route also answers 401 for a wrong current password. Only a session that is
            // really over may sign the user out, so ask a route that has no such second meaning.
            try {
                request("/api/mobile/profile")
            } catch (probe: ApiException) {
                if (probe.sessionEnded) throw probe
            } catch (_: java.io.IOException) {
                // Offline for the check: report the password answer the server already gave.
            }
            throw ApiException(400, e.message ?: "Your current password is incorrect.")
        }
        store.mustChangePassword = false
    }

    override suspend fun classDetail(id: String, role: String): ClassDetail =
        ClassDetail.parse(data(request("/api/mobile/class", query = listOf("id" to id, "role" to role))))

    override suspend fun attendance(sessionId: String): AttendanceSheet =
        AttendanceSheet.parse(data(request("/api/mobile/attendance", query = listOf("sessionId" to sessionId))))

    override suspend fun markAttendance(sessionId: String, learners: Collection<AttendanceLearner>): AttendanceSheet {
        val body = JSONObject().put("action", "mark").put("sessionId", sessionId).put("records", attendanceRecords(learners))
        // The save answer carries the learners but not the check-in link; the caller keeps the link it has.
        return AttendanceSheet.parse(data(request("/api/mobile/attendance", body)), checkinUrl = null)
    }

    override suspend fun checkIn(code: String): CheckinResult {
        val json = request("/api/mobile/attendance", JSONObject().put("action", "checkin").put("code", code.trim()))
        return CheckinResult(json.text("message").ifEmpty { "You are checked in." }, json.optBoolean("already"), json.text("course"), json.text("session"))
    }

    override suspend fun uploadAssessment(studentName: String, runCode: String, source: UploadSource, progress: (Float) -> Unit): UploadedFile =
        withContext(Dispatchers.IO) {
            val token = store.token
            val file = object : RequestBody() {
                override fun contentType(): MediaType = source.mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
                override fun contentLength(): Long = if (source.length > 0) source.length else -1

                override fun writeTo(sink: BufferedSink) {
                    source.open().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var sent = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            sink.write(buffer, 0, n)
                            sent += n
                            if (source.length > 0) progress((sent.toFloat() / source.length).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            val form = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", source.fileName, file).build()
            val request = Request.Builder()
                .url(url("/api/upload/google-drive", listOf("studentName" to studentName, "courseRunId" to runCode)))
                .header("Accept", "application/json")
                .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
                .post(form)
                .build()
            val call = uploadHttp.newCall(request)
            // Leaving the screen cancels the coroutine; stop the transfer with it.
            val handle = coroutineContext.job.invokeOnCompletion { if (it != null) call.cancel() }
            try {
                val data = call.execute().use { data(read(it, token.isNotEmpty())) }
                val fileUrl = data.text("fileUrl")
                if (!isDriveLink(fileUrl)) throw ApiException(502, "The upload did not finish. Please try again.", fromApi = false)
                val folder = data.text("studentFolderId").ifEmpty { data.text("folderId") }
                UploadedFile(fileUrl, folder.takeIf { Regex("^[A-Za-z0-9_-]{10,}$").matches(it) })
            } finally {
                handle.dispose()
            }
        }

    override suspend fun submitAssessment(runId: String, type: String, fileName: String, file: UploadedFile) {
        val body = JSONObject().put("action", "submit").put("runId", runId).put("type", type)
            .put("fileName", fileName).put("fileURL", file.fileUrl)
        file.folderId?.let { body.put("folderId", it) }
        request("/api/mobile/assessments", body)
    }

    override suspend fun deleteSubmission(submissionId: String) {
        request("/api/mobile/assessments", JSONObject().put("submissionId", submissionId), "DELETE")
    }

    override suspend fun grade(enrolmentIds: List<String>, competent: Boolean): String {
        val body = JSONObject().put("action", "grade").put("enrolmentIds", JSONArray(enrolmentIds)).put("isCompetent", competent)
        return request("/api/mobile/assessments", body).text("status").ifEmpty { if (competent) "Competent" else "Not Yet Competent" }
    }

    override suspend fun feedbackForm(runId: String): FeedbackForm =
        FeedbackForm.parse(data(request("/api/mobile/feedback", query = listOf("runId" to runId))))

    override suspend fun submitFeedback(runId: String, form: FeedbackForm, answers: Map<String, String>) {
        request("/api/mobile/feedback", JSONObject().put("runId", runId).put("answers", form.answersJson(answers)))
    }

    // The quiz routes are shared with the web quiz page, so their answers are not wrapped in "data".
    override suspend fun quiz(courseId: String, quizId: String): QuizLoad =
        QuizLoad.parse(request("/api/courses/quiz", query = listOf("courseId" to courseId, "quizId" to quizId)))

    override suspend fun submitQuiz(courseId: String, quizId: String, answers: Map<String, Int>): QuizResult {
        // An attempt is recorded against the signed-in user only; the server rejects any other id.
        val userId = runCatching { JSONObject(store.profile).text("id") }.getOrDefault("")
        if (userId.isEmpty()) throw ApiException(400, "Please sign in again to submit this quiz.")
        return QuizResult.parse(request("/api/courses/submit-quiz", quizSubmission(userId, courseId, quizId, answers)))
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
