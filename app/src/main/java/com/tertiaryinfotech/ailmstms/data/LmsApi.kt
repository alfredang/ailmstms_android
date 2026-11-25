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

    /** Trainer: the website's Publish switch for one assessment of the class. */
    suspend fun publishAssessment(runId: String, type: String, published: Boolean)

    /** The Assessment Summary Record: the learner's own ([learnerView]) or every learner's for the trainer. */
    suspend fun summaryRecord(runId: String, learnerView: Boolean): SummaryReply

    /**
     * Signs the record: as the candidate (learner, [learnerUserId] null) or as the assessor for
     * [learnerUserId]. Returns the server's note when the PDF could not be made yet, else null.
     * Fails with a 409 when no signature is saved.
     */
    suspend fun signSummaryRecord(runId: String, learnerUserId: String?): String?

    /** The saved name, NRIC and whether a drawn signature is on file. */
    suspend fun signer(kind: SignerKind): SignerRecord

    /** Saves the signer details; [signaturePng] is a PNG data URL, or null to keep the saved one. */
    suspend fun saveSigner(kind: SignerKind, name: String, nric: String, signaturePng: String?)

    /** Admin: every class run overlapping the dates (at most about three months). */
    suspend fun staffClasses(from: java.time.LocalDate, to: java.time.LocalDate): List<StaffClass>

    /** Admin and Developer: the course catalogue. */
    suspend fun staffCourses(): List<StaffCourse>

    /** Admin: every run of one course, newest first. */
    suspend fun staffRuns(courseId: String): List<StaffClass>

    /** Admin: every trainer with a WhatsApp number where the profile has one. */
    suspend fun staffTrainers(): List<StaffTrainer>

    /** Trainer: posts an announcement to the class (message and/or one attachment, at most 50 MB). */
    suspend fun postAnnouncement(runId: String, postedBy: String, message: String, file: UploadSource?)

    /** Trainer: deletes one announcement of a class they train. */
    suspend fun deleteAnnouncement(id: String)

    /** Trainer: the grading roster and the class's assessment methods. */
    suspend fun roster(runId: String): Roster

    suspend fun gradeStudent(student: RosterStudent, competent: Boolean)
    suspend fun setTraqom(student: RosterStudent, completed: Boolean)

    /** Stamps (or removes) the assessor signature on every file the learners submitted. */
    suspend fun signAssessments(runId: String, learnerUserIds: List<String>, signed: Boolean)

    /** The signed-in account's course certificates. */
    suspend fun certificates(): List<Certificate>

    suspend fun support(): SupportInfo
    suspend fun sendSupport(subject: String, message: String)

    suspend fun trainerCredentials(): TrainerCredentials

    /** Saves the changed fields only (aclp, dddpl, taepp, areasOfExpertise, skillsTags) and returns the saved profile. */
    suspend fun saveTrainerCredentials(changes: org.json.JSONObject): TrainerCredentials
}

const val MAX_UPLOAD_BYTES = 100L * 1024 * 1024
