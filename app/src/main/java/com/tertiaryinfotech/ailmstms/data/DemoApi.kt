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

    // Same names and order as the website's Courseware section for each role.
    private fun materials(trainer: Boolean): List<Material> =
        if (trainer) listOf(
            Material("courseware-link", "Courseware Link", "$SAMPLE/support.html"),
            Material("lesson-plan", "Lesson Plan", "$SAMPLE/sample-guide.html"),
            Material("guide", "Learner Guide", "$SAMPLE/sample-guide.html"),
            Material("trainer-slides", "Trainer Slides", "$SAMPLE/sample-trainer.html"),
            Material("activities", "Activities/Lab", "$SAMPLE/sample-activity.html"),
        ) else listOf(
            Material("guide", "Learner Guide", "$SAMPLE/sample-guide.html"),
            Material("slides", "Learner Slides", "$SAMPLE/sample-slides.html"),
            Material("activities", "Activities/Lab", "$SAMPLE/sample-activity.html"),
        )

    // Assessment Summary Record, signatures and the Publish switches: kept in memory.
    private val published = mutableMapOf("writtenAssessment" to true, "practicalExam" to true)
    private val signers = mutableMapOf<SignerKind, SignerRecord>()
    private val candidateSigned = mutableMapOf("demo-e1" to true)
    private val assessorSigned = mutableMapOf<String, Boolean>()
    private var mySignature = false

    // Announcements posted and deleted in the demo, newest first.
    private val announcements = mutableListOf(
        Announcement("demo-a1", "Welcome to class", "Bring your laptop with the lab software installed.", null, "", null, "Alex Tan", today().plusDays(1).toString()),
    )

    private var roster = listOf(
        RosterStudent("d1", "u1", "Jamie Lim (sample)", "", "manual", false, listOf("writtenAssessment", "practicalExam"), false, 2, false, null),
        RosterStudent("d2", "u2", "Priya Nair (sample)", "Competent", "manual", true, listOf("writtenAssessment", "practicalExam"), true, 2, true, null),
        RosterStudent("d3", "u3", "Wei Ming (sample)", "", "manual", false, listOf("writtenAssessment"), false, 1, false, null),
    )
    private var credentials = TrainerCredentials(aclp = true, dddpl = false, taepp = false, areasOfExpertise = listOf("AI"), skillsTags = listOf("Python"))

    private fun signed(name: String) = SummaryParty(name, today().toString(), Instant.now().toString())

    override suspend fun dashboard(role: String): Dashboard {
        val day = firstDay()
        val trainer = role == "trainer"
        val venue = VENUE
        val courses = listOf(
            Course("demo-ai", "Building AI Applications", "DEMO · AI-101", day.toString(), day.plusDays(1).toString(), "Classroom", venue, materials(trainer), "DEMO-RUN-1001", "Confirmed"),
            Course("demo-data", "Data Analytics Essentials", "DEMO · DATA-101", day.plusDays(7).toString(), day.plusDays(7).toString(), "Online", "", emptyList(), "DEMO-RUN-1002", "Pending"),
            // A cancelled class shows in the list with its status, but has no sessions, calendar entries or alerts.
            Course("demo-cloud", "Cloud Fundamentals", "DEMO · CL-110", day.plusDays(14).toString(), day.plusDays(15).toString(), "Classroom", venue, emptyList(), "DEMO-RUN-1003", "Cancelled"),
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
                trainerNames = listOf("Alex Tan"),
                briefingOnAssessment = BRIEFING,
                showCourseFeedback = true,
                swdaEnrolmentUrl = if (trainer) "/enrol/$id" else "",
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
            Assessment(type, label, "$SAMPLE/sample-activity.html", published.getValue(type), if (trainer) emptyList() else submissions.getValue(type).toList())
        }.filter { trainer || it.published } // as on the server, a learner never sees an unpublished paper
        return ClassDetail(
            id = "demo-ai", runCode = "DEMO-RUN-1001", title = "Building AI Applications", code = "DEMO · AI-101",
            startDate = day.toString(), endDate = day.plusDays(1).toString(), format = "Classroom", classStatus = "Confirmed",
            venue = VENUE, meetingUrl = "https://meet.google.com/abc-defg-hij", materials = materials(trainer), sessions = sessions,
            ssgAttendanceUrl = "$SAMPLE/support.html", assessments = assessments, traqomUrl = TRAQOM,
            assessmentStatus = "", traqomCompleted = false,
            feedback = if (trainer) null else FeedbackInfo(true, null, feedbackSent),
            roster = if (trainer) roster() else emptyList(),
            feedbackCount = if (trainer) 3 else 0,
            courseDetails = CourseDetails(
                "Artificial Intelligence Application", "DEMO-TSC-3026-1.1", "", today().minusYears(1).toString(), today().plusYears(1).toString(), 14.0, 2.0,
            ),
            trainerNames = listOf("Alex Tan"),
            digitalAttendanceId = "RA000000",
            announcements = announcements.toList(),
            briefingOnAssessment = BRIEFING,
            showCourseFeedback = true,
            courseFeedbackUrl = if (trainer) "/feedback/demo-ai" else "",
            swdaEnrolmentUrl = if (trainer) "/enrol/demo-ai" else "",
            feedbackSummary = if (trainer) FeedbackSummary(
                3, 4.5,
                listOf(
                    FeedbackSummary.Question("Overall, how would you rate the course meeting the learning objectives?", 4.7),
                    FeedbackSummary.Question("Overall, how would you rate the trainer's knowledge of the subject?", 5.0),
                    FeedbackSummary.Question("Overall, how would you rate the training environment?", 3.8),
                ),
                listOf("Very practical, loved the hands-on labs.", "Clear explanations and good pace.", "More time for the final project, please.", "The examples were relevant to my work."),
            ) else null,
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

    private var quizAttempt: QuizAttempt? = null

    /** Any quiz link opens the same sample quiz in the demo. */
    override suspend fun quiz(courseId: String, quizId: String): QuizLoad {
        delay(pause)
        return QuizLoad(QUIZ.copy(id = quizId, courseId = courseId), quizAttempt)
    }

    override suspend fun submitQuiz(courseId: String, quizId: String, answers: Map<String, Int>): QuizResult {
        delay(pause)
        val marks = QUIZ.questions.map { q -> QuizMark(q.id, answers[q.id] == q.correctIndex, q.correctIndex, answers[q.id]) }
        val done = Instant.now().toString()
        quizAttempt = QuizAttempt(marks.count { it.correct }, marks.size, done)
        return QuizResult(marks.count { it.correct }, marks.size, done, marks)
    }

    override suspend fun payouts(): List<Payout> {
        delay(pause)
        val day = today()
        fun paid(id: String, title: String, code: String, learners: Int, amount: Double, weeksAgo: Long, days: Long, source: String): Payout {
            // Classes start on a Monday and are paid on the Friday of the following week.
            val start = day.minusWeeks(weeksAgo).with(java.time.DayOfWeek.MONDAY)
            return Payout(id, title, code, learners, amount, start.plusDays(11).toString(), start.toString(), start.plusDays(days - 1).toString(), source)
        }
        return listOf(
            paid("demo-pay-3", "Building AI Applications", "DEMO · AI-101", 12, 1600.0, 4, 2, "wsq"),
            paid("demo-pay-2", "Data Analytics Essentials", "DEMO · DATA-101", 9, 800.0, 9, 1, "wsq"),
            paid("demo-pay-1", "Prompt Engineering Workshop", "DEMO · PE-201", 15, 950.0, 15, 1, "manual"),
        )
    }

    override suspend fun publishAssessment(runId: String, type: String, published: Boolean) {
        delay(pause)
        this.published[type] = published
    }

    override suspend fun summaryRecord(runId: String, learnerView: Boolean): SummaryReply {
        delay(pause)
        if (learnerView) {
            val record = if (mySignature) SummaryRecord(signed(PROFILE.name), null, "$SAMPLE/sample-guide.html") else null
            return SummaryReply(record, emptyList(), signers[SignerKind.LEARNER]?.hasSignature == true)
        }
        val learners = LEARNERS.map { (id, name) ->
            val candidate = candidateSigned[id] == true
            val assessor = assessorSigned[id] == true
            val record = if (candidate || assessor) SummaryRecord(if (candidate) signed(name) else null, if (assessor) signed(PROFILE.name) else null, "$SAMPLE/sample-guide.html") else null
            LearnerSummaryStatus(id, name, record)
        }
        return SummaryReply(null, learners, signers[SignerKind.ASSESSOR]?.hasSignature == true)
    }

    override suspend fun signSummaryRecord(runId: String, learnerUserId: String?): String? {
        delay(pause)
        val kind = if (learnerUserId == null) SignerKind.LEARNER else SignerKind.ASSESSOR
        if (signers[kind]?.hasSignature != true) {
            throw ApiException(409, if (kind == SignerKind.LEARNER) "Set up your name, NRIC and signature first." else "Set up your assessor name, NRIC, date and signature first.")
        }
        if (learnerUserId == null) mySignature = true else assessorSigned[learnerUserId] = true
        return null
    }

    override suspend fun signer(kind: SignerKind): SignerRecord {
        delay(pause)
        return signers[kind] ?: SignerRecord(if (kind == SignerKind.LEARNER) "Jamie Lim (sample)" else "Alex Tan (sample)", "", false)
    }

    override suspend fun saveSigner(kind: SignerKind, name: String, nric: String, signaturePng: String?) {
        delay(pause)
        if (name.isBlank()) throw ApiException(400, "Name is required")
        val had = signers[kind]?.hasSignature == true
        signers[kind] = SignerRecord(name.trim(), nric.trim().uppercase(), had || signaturePng != null)
    }

    override suspend fun staffClasses(from: LocalDate, to: LocalDate): List<StaffClass> {
        delay(pause)
        val day = today()
        fun d(n: Long) = day.plusDays(n).toString()
        return listOf(
            StaffClass("s1", "100001", "Building AI Applications", "DEMO · AI-101", d(0), d(1), "Hybrid", "Confirmed", true, listOf("Alex Tan"), 18, VENUE),
            StaffClass("s2", "100002", "Data Analytics Essentials", "DEMO · DATA-101", d(0), d(0), "Online", "Confirmed", true, listOf("Priya Nair"), 9, ""),
            StaffClass("s3", "100003", "Prompt Engineering Workshop", "DEMO · PE-201", d(3), d(3), "Physical", "Pending", false, emptyList(), 6, VENUE),
            StaffClass("s4", "100004", "Cloud Fundamentals", "DEMO · CL-110", d(5), d(6), "Physical", "Cancelled", false, listOf("Wei Ming"), 0, VENUE),
            StaffClass("s5", "100005", "Building AI Applications", "DEMO · AI-101", d(12), d(13), "Physical", "Confirmed", false, listOf("Alex Tan"), 14, VENUE),
        ).filter { c -> c.start?.let { !it.isAfter(to) } == true && (c.end ?: c.start)?.let { !it.isBefore(from) } == true }
    }

    override suspend fun staffCourses(): List<StaffCourse> {
        delay(pause)
        val day = today()
        return listOf(
            StaffCourse("c1", "Building AI Applications", "DEMO · AI-101", "WSQ", "", "Artificial Intelligence Application", "DEMO-TSC-3026-1.1", 14.0, 2.0, day.plusYears(1).toString(), 2, day.toString(), day.minusYears(1).toString(), 4, 2, 1, 1),
            StaffCourse("c2", "Data Analytics Essentials", "DEMO · DATA-101", "WSQ", "", "Data Analytics", "DEMO-TSC-2011-1.1", 7.0, 1.0, day.plusDays(45).toString(), 1, day.toString(), day.minusYears(2).toString()),
            StaffCourse("c3", "Prompt Engineering Workshop", "DEMO · PE-201", "Non-WSQ", "", "", "", 7.0, 0.0, "", 1, day.plusDays(3).toString()),
            StaffCourse("c4", "Legacy Office Skills (Expired)", "DEMO · OF-001", "WSQ", "", "Office Productivity", "DEMO-TSC-1001-1.1", 7.0, 1.0, day.minusMonths(3).toString(), 0, "", day.minusYears(2).toString()),
        )
    }

    override suspend fun staffRuns(courseId: String): List<StaffClass> {
        delay(pause)
        val day = today()
        fun d(n: Long) = day.plusDays(n).toString()
        val title = "Building AI Applications"
        val code = "DEMO · AI-101"
        return listOf(
            StaffClass("r4", "100006", title, code, d(20), d(21), "Physical", "Pending", false, emptyList(), 3, VENUE),
            StaffClass("r3", "100005", title, code, d(12), d(13), "Physical", "Confirmed", false, listOf("Alex Tan"), 14, VENUE),
            StaffClass("r2", "100001", title, code, d(0), d(1), "Hybrid", "Confirmed", true, emptyList(), 18, VENUE),
            StaffClass("r1", "099990", title, code, d(-30), d(-29), "Online", "Cancelled", false, emptyList(), 0, ""),
        ).filter { courseId == "c1" }
    }

    override suspend fun staffTrainers(): List<StaffTrainer> {
        delay(pause)
        return listOf(StaffTrainer("Alex Tan (sample)", "", "+6580000000"), StaffTrainer("Priya Nair (sample)", "", null))
    }

    override suspend fun postAnnouncement(runId: String, postedBy: String, message: String, file: UploadSource?) {
        delay(pause)
        // The picked file is never read or sent in demo mode: only its name is shown.
        announcements.add(
            0,
            Announcement(
                "demo-post-${nextId++}", "", message.trim(), null, file?.fileName.orEmpty(),
                file?.let { "$SAMPLE/sample-guide.html" }, postedBy, today().toString(),
            ),
        )
    }

    override suspend fun deleteAnnouncement(id: String) {
        delay(pause)
        announcements.removeAll { it.id == id }
    }

    override suspend fun roster(runId: String): Roster {
        delay(pause)
        return Roster(roster, AssessmentMethod.DEFAULT)
    }

    private fun updateRoster(student: RosterStudent, change: (RosterStudent) -> RosterStudent) {
        roster = roster.map { if (it.enrolmentId == student.enrolmentId) change(it) else it }
    }

    override suspend fun gradeStudent(student: RosterStudent, competent: Boolean) {
        delay(pause)
        updateRoster(student) { it.copy(isCompetent = competent, competentStatus = if (competent) "Competent" else "Not Yet Competent") }
    }

    override suspend fun setTraqom(student: RosterStudent, completed: Boolean) {
        delay(pause)
        updateRoster(student) { it.copy(traqomCompleted = completed) }
    }

    override suspend fun signAssessments(runId: String, learnerUserIds: List<String>, signed: Boolean) {
        delay(pause)
        roster = roster.map { if (it.userIds.any { id -> id in learnerUserIds }) it.copy(assessorSigned = signed) else it }
    }

    override suspend fun certificates(): List<Certificate> {
        delay(pause)
        val day = today()
        return listOf(
            Certificate("cert-1", "demo-done", "Responsible Generative AI Basics (sample)", "DEMO · AI-090", "100000", day.minusDays(40).toString(), day.minusDays(39).toString(), "Competent", "$SAMPLE/sample-guide.html"),
            Certificate("cert-2", "demo-ai", "Building AI Applications", "DEMO · AI-101", "DEMO-RUN-1001", day.plusDays(3).toString(), day.plusDays(4).toString(), "", null),
        )
    }

    override suspend fun support(): SupportInfo {
        delay(pause)
        return SupportInfo.SAMPLE
    }

    /** Never sent: the view model tells the user the feedback went nowhere in the sample. */
    override suspend fun sendSupport(subject: String, message: String) {
        delay(pause)
    }

    override suspend fun trainerCredentials(): TrainerCredentials {
        delay(pause)
        return credentials
    }

    override suspend fun saveTrainerCredentials(changes: org.json.JSONObject): TrainerCredentials {
        delay(pause)
        credentials = credentials.copy(
            aclp = changes.optBoolean("aclp", credentials.aclp),
            dddpl = changes.optBoolean("dddpl", credentials.dddpl),
            taepp = changes.optBoolean("taepp", credentials.taepp),
            areasOfExpertise = if (changes.has("areasOfExpertise")) changes.strings("areasOfExpertise") else credentials.areasOfExpertise,
            skillsTags = if (changes.has("skillsTags")) changes.strings("skillsTags") else credentials.skillsTags,
        )
        return credentials
    }

    companion object {
        private const val VENUE = "12 Woodlands Square, Singapore 737715"
        private val BRIEFING = listOf("Place phones & other materials under the table or on the floor", "No discussion during assessment")

        /** The link a quiz QR code would carry; used to open the sample quiz from the demo class. */
        const val QUIZ_COURSE_ID = "00000000-0000-4000-8000-000000000001"
        const val QUIZ_ID = "demo-quiz"

        private val QUIZ = Quiz(
            QUIZ_ID, QUIZ_COURSE_ID, "Day 1 recap quiz", "Building AI Applications", "DEMO · AI-101",
            listOf(
                QuizQuestion("q1", "What does a large language model predict when it writes an answer?", listOf("The next token", "The user's password", "The size of the database", "The network speed"), 0),
                QuizQuestion("q2", "Which practice most improves the quality of a model's answer?", listOf("Typing in capital letters", "Giving clear context and an example", "Asking the same question twice", "Using a shorter username"), 1),
                QuizQuestion("q3", "What is retrieval-augmented generation (RAG) used for?", listOf("Making the screen brighter", "Compressing images", "Grounding answers in your own documents", "Encrypting the device"), 2),
            ),
        )

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
