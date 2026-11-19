package com.tertiaryinfotech.ailmstms.data

import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

/**
 * Public, fictional sample data only. Demo mode never authenticates, never registers a push
 * token and never makes a network call: every answer here comes from memory, and changes
 * (a check-in, an upload, a mark) last until the demo is left.
 */
class DemoApi(private val today: () -> LocalDate = { LocalDate.now(SGT) }, private val pause: Long = 250) : LmsApi {
    private var user = PROFILE_DETAIL
    private val attendance = mutableMapOf<String, String>()
    private val submissions = mutableMapOf("writtenAssessment" to mutableListOf<Submission>(), "practicalExam" to mutableListOf())
    private var feedbackSent = false
    private var nextId = 1
    private val sheets = mutableMapOf<String, List<AttendanceLearner>>()
    private val results = mutableMapOf("demo-e1" to "Competent", "demo-e2" to "", "demo-e3" to "", "demo-e4" to "")

    private fun firstDay(): LocalDate = today().plusDays(3)

    private fun materials(trainer: Boolean): List<Material> = buildList {
        if (trainer) add(Material("trainer-slides", "Trainer slides", "$SAMPLE/sample-trainer.html"))
        add(Material("slides", "Learner slides", "$SAMPLE/sample-slides.html"))
        add(Material("guide", "Learner Guide", "$SAMPLE/sample-guide.html"))
        add(Material("activities", "Labs & activities", "$SAMPLE/sample-activity.html"))
        if (trainer) add(Material("lesson-plan", "Lesson Plan", "$SAMPLE/sample-guide.html"))
    }

    override suspend fun dashboard(role: String): Dashboard {
        val day = firstDay()
        val trainer = role == "trainer"
        val venue = "Woods Square, Singapore"
        val courses = listOf(
            Course("demo-ai", "Building AI Applications", "DEMO · AI-101", day.toString(), day.plusDays(1).toString(), "Classroom", venue, materials(trainer), "DEMO-RUN-1001", "Confirmed"),
            Course("demo-data", "Data Analytics Essentials", "DEMO · DATA-101", day.plusDays(7).toString(), day.plusDays(7).toString(), "Online", "", emptyList(), "DEMO-RUN-1002", "Confirmed"),
        )
        val sessions = listOf(
            ClassSession("demo-1", "demo-ai", "Building AI Applications", day, day, "09:00", "17:00", if (trainer) "Teaching · Session 1" else "Session 1 · Foundations", venue, "Classroom", ""),
            ClassSession("demo-2", "demo-ai", "Building AI Applications", day.plusDays(1), day.plusDays(1), "09:00", "17:00", "Session 2 · Build and apply", venue, "Classroom", ""),
            ClassSession("demo-3", "demo-data", "Data Analytics Essentials", day.plusDays(7), day.plusDays(7), "09:00", "17:00", "Explore and visualise data", "", "Online", ""),
        )
        return Dashboard(courses, sessions)
    }

    override suspend fun profile(): UserProfile = user

    override suspend fun updateProfile(patch: ProfilePatch): UserProfile {
        delay(pause)
        user = user.copy(
            fullName = patch.fullName ?: user.fullName,
            phone = patch.phone ?: user.phone,
            company = patch.company ?: user.company,
            employmentStatus = patch.employmentStatus?.takeIf { it.isNotBlank() } ?: user.employmentStatus,
            linkedinUrl = patch.linkedinUrl ?: user.linkedinUrl,
            theme = patch.theme ?: user.theme,
        )
        return user
    }

    override suspend fun changePassword(current: String, new: String) {
        delay(pause)
    }

    private fun roster(): List<RosterEntry> = LEARNERS.map { (id, name) ->
        RosterEntry(id, name, results[id].orEmpty(), id == "demo-e1", if (id == "demo-e1" || id == "demo-e2") 1 else 0)
    }

    private fun sheet(sessionId: String): List<AttendanceLearner> = sheets.getOrPut(sessionId) {
        LEARNERS.mapIndexed { i, (id, name) ->
            // The first session starts half marked so the screen shows every state.
            val present = if (sessionId == "demo-1") when (i) { 0, 1 -> true; 2 -> false; else -> null } else null
            AttendanceLearner(id, name, present, if (present == false) "Medical leave" else "")
        }
    }

    override suspend fun classDetail(id: String, role: String): ClassDetail {
        delay(pause)
        val trainer = role == "trainer"
        val day = firstDay()
        if (id == "demo-data") {
            val date = day.plusDays(7).toString()
            return ClassDetail(
                id, "DEMO-RUN-1002", "Data Analytics Essentials", "DEMO · DATA-101", date, date, "Online", "Confirmed", "",
                "$SAMPLE/support.html", emptyList(),
                listOf(DetailSession("demo-3", "Day 1 · Full day", date, "09:00", "17:00", null, if (trainer) sheet("demo-3").count { it.isPresent == true } else null)),
                null, emptyList(), TRAQOM,
                feedback = if (trainer) null else FeedbackInfo(true, null, false),
                roster = if (trainer) roster() else emptyList(),
            )
        }
        val sessions = listOf("demo-1" to day, "demo-2" to day.plusDays(1)).mapIndexed { i, (sid, date) ->
            DetailSession(
                sid, "Day ${i + 1} · ${if (i == 0) "Foundations" else "Build and apply"}", date.toString(), "09:00", "17:00",
                if (trainer) null else attendance[sid],
                if (trainer) sheet(sid).count { it.isPresent == true } else null,
            )
        }
        val assessments = listOf("writtenAssessment" to "Written Assessment", "practicalExam" to "Practical Performance").map { (type, label) ->
            Assessment(type, label, "$SAMPLE/sample-activity.html", true, if (trainer) emptyList() else submissions.getValue(type).toList())
        }
        return ClassDetail(
            id = "demo-ai", runCode = "DEMO-RUN-1001", title = "Building AI Applications", code = "DEMO · AI-101",
            startDate = day.toString(), endDate = day.plusDays(1).toString(), format = "Classroom", classStatus = "Confirmed",
            venue = "Woods Square, Singapore", meetingUrl = null, materials = materials(trainer), sessions = sessions,
            ssgAttendanceUrl = "$SAMPLE/support.html", assessments = assessments, traqomUrl = TRAQOM,
            assessmentStatus = "", traqomCompleted = false,
            feedback = if (trainer) null else FeedbackInfo(true, null, feedbackSent),
            roster = if (trainer) roster() else emptyList(),
            feedbackCount = if (trainer) 3 else 0,
        )
    }

    override suspend fun attendance(sessionId: String): AttendanceSheet {
        delay(pause)
        return AttendanceSheet("https://lms-tms.tertiaryinfotech.com/checkin/$DEMO_CODE", sheet(sessionId))
    }

    override suspend fun markAttendance(sessionId: String, learners: Collection<AttendanceLearner>): AttendanceSheet {
        delay(pause)
        val marks = learners.filter { it.isPresent != null }.associateBy { it.enrolmentId }
        sheets[sessionId] = sheet(sessionId).map { old ->
            marks[old.enrolmentId]?.let { old.copy(isPresent = it.isPresent, reason = if (it.isPresent == false) it.reason.trim() else "") } ?: old
        }
        return AttendanceSheet(null, sheets.getValue(sessionId))
    }

    override suspend fun checkIn(code: String): CheckinResult {
        delay(pause)
        val already = attendance["demo-1"] == "present"
        attendance["demo-1"] = "present"
        return CheckinResult(
            if (already) "You are already checked in for this session." else "You are checked in.",
            already, "Building AI Applications", "Day 1 · Foundations",
        )
    }

    override suspend fun uploadAssessment(studentName: String, runCode: String, source: UploadSource, progress: (Float) -> Unit): UploadedFile {
        // The picked file is never read or sent in demo mode: only the progress is simulated.
        for (step in 1..8) {
            delay(pause / 2)
            progress(step / 8f)
        }
        return UploadedFile("https://drive.google.com/file/d/demo-sample-file/view", null)
    }

    override suspend fun submitAssessment(runId: String, type: String, fileName: String, file: UploadedFile) {
        delay(pause)
        submissions.getOrPut(type) { mutableListOf() }.add(0, Submission("demo-sub-${nextId++}", fileName, "$SAMPLE/sample-activity.html", Instant.now().toString(), false))
    }

    override suspend fun deleteSubmission(submissionId: String) {
        delay(pause)
        submissions.values.forEach { list -> list.removeAll { it.id == submissionId } }
    }

    override suspend fun grade(enrolmentIds: List<String>, competent: Boolean): String {
        delay(pause)
        val status = if (competent) "Competent" else "Not Yet Competent"
        enrolmentIds.forEach { results[it] = status }
        return status
    }

    override suspend fun feedbackForm(runId: String): FeedbackForm {
        delay(pause)
        val day = firstDay()
        val demoData = runId == "demo-data"
        return FeedbackForm(
            "Course Feedback",
            listOf(
                FeedbackSection(
                    "section-1", "Your Details",
                    listOf(
                        FeedbackField("course_title", "Course Title", "text", true, emptyList(), "course_title", true),
                        FeedbackField("course_code", "Course Code", "text", true, emptyList(), "course_code", true),
                        FeedbackField("learner_name", "Student's Full Name", "text", true, emptyList(), null, false),
                        FeedbackField("start_date", "Class Start Date", "date", false, emptyList(), "start_date", false),
                    ),
                ),
                FeedbackSection(
                    "section-2", "Course Evaluation",
                    listOf(
                        FeedbackField("rate_learning_objectives", "Overall, how would you rate the course meeting the learning objectives?", "rating1to5", true, emptyList(), null, false),
                        FeedbackField("rate_trainer_knowledge", "Overall, how would you rate the trainer's knowledge of the subject?", "rating1to5", true, emptyList(), null, false),
                        FeedbackField("recommend", "Would you recommend this course?", "select", false, listOf("Yes", "Maybe", "No"), null, false),
                        FeedbackField("message", "Additional comments (optional)", "textarea", false, emptyList(), null, false),
                    ),
                ),
            ),
            feedbackSent && !demoData,
            mapOf(
                "course_title" to if (demoData) "Data Analytics Essentials" else "Building AI Applications",
                "course_code" to if (demoData) "DEMO · DATA-101" else "DEMO · AI-101",
                "start_date" to (if (demoData) day.plusDays(7) else day).toString(),
                "end_date" to (if (demoData) day.plusDays(7) else day.plusDays(1)).toString(),
            ),
        )
    }

    override suspend fun submitFeedback(runId: String, form: FeedbackForm, answers: Map<String, String>) {
        delay(pause)
        if (runId != "demo-data") feedbackSent = true
    }

    companion object {
        private const val SAMPLE = "https://lms-tms.tertiaryinfotech.com/mobile"
        private const val TRAQOM = "$SAMPLE/support.html"

        /** A sample check-in code in the real format, shown as the trainer's QR in the demo. */
        const val DEMO_CODE = "00000000000000000000000000000000"

        val PROFILE = Profile("demo", "Alex Tan", "alex@example.com", listOf("learner", "trainer"))
        val PROFILE_DETAIL = UserProfile(
            "demo", "alex@example.com", "Alex Tan", null, ThemeMode.SYSTEM, "+65 6000 0000", "Example Pte. Ltd.",
            "Employed", "https://www.linkedin.com/in/example", "", "Demo Academy",
        )
        private val LEARNERS = listOf("demo-e1" to "Bala Krishnan", "demo-e2" to "Chen Mei Ling", "demo-e3" to "Farah Aziz", "demo-e4" to "Jordan Lee")
    }
}
