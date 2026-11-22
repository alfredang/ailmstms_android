package com.tertiaryinfotech.ailmstms.data

import java.io.IOException
import java.io.InputStream

/**
 * A failed API call. [sessionEnded] is true only when the server rejected the session token
 * itself; the app then signs the user out. [fromApi] is false when the answer was not the
 * API's JSON at all (for example a gateway page), which the UI treats as "try again later".
 */
class ApiException(
    val status: Int,
    message: String,
    val sessionEnded: Boolean = false,
    val fromApi: Boolean = true,
) : IOException(message)

/** A file the learner picked, read lazily so it is never held in memory. */
class UploadSource(val fileName: String, val mimeType: String, val length: Long, val open: () -> InputStream)

/**
 * Everything the signed-in screens need. [Repository] talks to the server; [DemoApi] answers
 * from fictional in-memory data and never touches the network.
 */
interface LmsApi {
    suspend fun dashboard(role: String): Dashboard
    suspend fun profile(): UserProfile
    suspend fun updateProfile(patch: ProfilePatch): UserProfile
    suspend fun changePassword(current: String, new: String)
    suspend fun classDetail(id: String, role: String): ClassDetail

    suspend fun attendance(sessionId: String): AttendanceSheet
    suspend fun markAttendance(sessionId: String, learners: Collection<AttendanceLearner>): AttendanceSheet
    suspend fun checkIn(code: String): CheckinResult

    suspend fun uploadAssessment(studentName: String, runCode: String, source: UploadSource, progress: (Float) -> Unit): UploadedFile
    suspend fun submitAssessment(runId: String, type: String, fileName: String, file: UploadedFile)
    suspend fun deleteSubmission(submissionId: String)
    suspend fun grade(enrolmentIds: List<String>, competent: Boolean): String

    suspend fun feedbackForm(runId: String): FeedbackForm
    suspend fun submitFeedback(runId: String, form: FeedbackForm, answers: Map<String, String>)

    /** The quiz behind a quiz QR code, with the signed-in user's latest attempt. */
    suspend fun quiz(courseId: String, quizId: String): QuizLoad

    /** Sends the picked option of each question (by question id); the server marks and records the attempt. */
    suspend fun submitQuiz(courseId: String, quizId: String, answers: Map<String, Int>): QuizResult

    /** Trainer: every completed payout for the signed-in user, newest first. */
    suspend fun payouts(): List<Payout>
}

const val MAX_UPLOAD_BYTES = 100L * 1024 * 1024
