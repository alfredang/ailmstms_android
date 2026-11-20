package com.tertiaryinfotech.ailmstms.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val SGT: ZoneId = ZoneId.of("Asia/Singapore")

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")
fun JSONObject.array(key: String): JSONArray = optJSONArray(key) ?: JSONArray()

/** An https link from the server, or null when it is missing or not safe to open. */
fun JSONObject.link(key: String): String? = text(key).trim().takeIf(::isSafeLink)

/** "2026-11-16", "2026-11-16T…" or the SSG text form "20261116" → a date; anything else → null. */
fun parseDay(value: String): LocalDate? {
    val text = value.trim()
    val iso = if (Regex("^\\d{8}$").matches(text)) {
        "${text.substring(0, 4)}-${text.substring(4, 6)}-${text.substring(6, 8)}"
    } else {
        text.take(10)
    }
    return runCatching { LocalDate.parse(iso) }.getOrNull()
}

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

/** A date string from the server shown as "Mon, 16 Nov 2026"; the raw text when unreadable. */
fun prettyDay(value: String): String = parseDay(value)?.format(DAY_FORMAT) ?: value.take(10)

/** An ISO timestamp shown in Singapore time; the raw text when unreadable. */
fun prettyInstant(value: String): String =
    runCatching {
        Instant.parse(value).atZone(SGT).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)) + " SGT"
    }.getOrDefault(value.take(16).replace('T', ' '))

/** The signed-in identity: only learner and trainer roles are ever kept. */
data class Profile(val id: String, val name: String, val email: String, val roles: List<String>) {
    companion object {
        fun parse(o: JSONObject): Profile {
            val roles = o.array("roles").let { a -> (0 until a.length()).map { a.optString(it) } }
                .filter { it in listOf("learner", "trainer") }
            return Profile(o.text("id"), o.text("fullName"), o.text("email"), roles)
        }
    }
}

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"), LIGHT("light", "Light"), DARK("dark", "Dark");

    companion object {
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

val EMPLOYMENT_OPTIONS = listOf("Employed", "Unemployed", "Looking for Job")

/** The editable profile from /api/mobile/profile. */
data class UserProfile(
    val id: String,
    val email: String,
    val fullName: String,
    val pictureUrl: String?,
    val theme: ThemeMode,
    val phone: String,
    val company: String,
    val employmentStatus: String,
    val linkedinUrl: String,
    val brandColor: String,
    val academy: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("email", email).put("fullName", fullName).put("pictureURL", pictureUrl ?: JSONObject.NULL)
        .put("theme", theme.key).put("phone", phone).put("company", company)
        .put("employmentStatus", employmentStatus).put("linkedinURL", linkedinUrl)
        .put("brandColor", brandColor).put("academy", academy)

    companion object {
        fun parse(o: JSONObject) = UserProfile(
            o.text("id"), o.text("email"), o.text("fullName"), o.link("pictureURL"), ThemeMode.from(o.text("theme")),
            o.text("phone"), o.text("company"), o.text("employmentStatus"), o.text("linkedinURL"),
            o.text("brandColor"), o.text("academy"),
        )
    }
}

/** Only the fields that are set are sent; the server ignores everything else. */
data class ProfilePatch(
    val fullName: String? = null,
    val phone: String? = null,
    val company: String? = null,
    val employmentStatus: String? = null,
    val linkedinUrl: String? = null,
    val theme: ThemeMode? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        fullName?.let { put("fullName", it) }
        phone?.let { put("phone", it) }
        company?.let { put("company", it) }
        employmentStatus?.let { put("employmentStatus", it) }
        linkedinUrl?.let { put("linkedinURL", it) }
        theme?.let { put("theme", it.key) }
    }
}

data class Material(val id: String, val title: String, val url: String)

data class Course(
    val id: String,
    val title: String,
    val code: String,
    val start: String,
    val end: String,
    val format: String,
    val venue: String,
    val resources: List<Material>,
    val runCode: String = "",
    val classStatus: String = "",
) {
    val isCancelled: Boolean get() = isCancelledStatus(classStatus)

    companion object {
        private val LINKS = listOf(
            Triple("trainer-slides", "Trainer slides", "trainerSlidesURL"),
            Triple("slides", "Learner slides", "slidesURL"),
            Triple("guide", "Learner Guide", "guideURL"),
            Triple("activities", "Labs & activities", "activitiesURL"),
        )

        fun parse(o: JSONObject) = Course(
            o.text("id"), o.text("title"), o.text("code"), o.text("startDate"), o.text("endDate"),
            o.text("format"), o.text("venue"),
            LINKS.mapNotNull { (id, title, key) -> o.link(key)?.let { Material(id, title, it) } },
            o.text("runCode"), o.text("classStatus"),
        )
    }
}

fun isCancelledStatus(status: String): Boolean = status.trim().lowercase() in listOf("cancelled", "canceled")

data class ClassSession(
    val id: String,
    val courseId: String,
    val title: String,
    val date: LocalDate,
    val endDate: LocalDate,
    val time: String,
    val endTime: String,
    val subtitle: String,
    val venue: String,
    val format: String,
    val meeting: String,
) {
    /** False for a class whose session times are not published yet. */
    val hasTime: Boolean get() = runCatching { LocalTime.parse(time) }.isSuccess

    val startsAt: Instant
        get() = date.atTime(runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(9, 0))).atZone(SGT).toInstant()
    val endsAt: Instant
        get() = endDate.atTime(runCatching { LocalTime.parse(endTime) }.getOrDefault(LocalTime.of(18, 0))).atZone(SGT).toInstant()
    val whenText: String
        get() = date.format(DAY_FORMAT) + " · " +
            (if (hasTime) time.take(5) + "–" + endTime.take(5) + " SGT" else "Time to be confirmed")

    companion object {
        fun parse(o: JSONObject): ClassSession? {
            val start = parseDay(o.text("startDate")) ?: return null
            val end = parseDay(o.text("endDate").ifEmpty { o.text("startDate") }) ?: start
            return ClassSession(
                o.text("id"), o.text("courseID"), o.text("title"), start, end, o.text("startTime"), o.text("endTime"),
                o.text("subtitle"), o.text("venue"), o.text("format"), o.text("meetingURL"),
            )
        }
    }
}

data class Dashboard(val courses: List<Course>, val sessions: List<ClassSession>) {
    companion object {
        val EMPTY = Dashboard(emptyList(), emptyList())

        fun parse(o: JSONObject) = Dashboard(
            o.array("courses").objects().map(Course::parse),
            o.array("sessions").objects().mapNotNull(ClassSession::parse).sortedBy { it.startsAt },
        )
    }
}

// ---------------------------------------------------------------- class detail

data class DetailSession(
    val id: String,
    val label: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    /** Learner: "present", "absent" or null when not marked. */
    val attendance: String?,
    /** Trainer: learners marked present. */
    val presentCount: Int?,
) {
    val timeText: String
        get() = listOf(startTime.take(5), endTime.take(5)).filter { it.isNotBlank() }.joinToString("–")
}

data class Submission(val id: String, val fileName: String, val fileUrl: String, val submittedAt: String, val signed: Boolean)

data class Assessment(
    val type: String,
    val label: String,
    val questionUrl: String?,
    val published: Boolean,
    val submissions: List<Submission>,
)

data class FeedbackInfo(val enabled: Boolean, val externalUrl: String?, val submitted: Boolean)

data class RosterEntry(
    val enrolmentId: String,
    val name: String,
    val status: String,
    val traqomCompleted: Boolean,
    val submissions: Int,
)

data class ClassDetail(
    val id: String,
    val runCode: String,
    val title: String,
    val code: String,
    val startDate: String,
    val endDate: String,
    val format: String,
    val classStatus: String,
    val venue: String,
    val meetingUrl: String?,
    val materials: List<Material>,
    val sessions: List<DetailSession>,
    val ssgAttendanceUrl: String?,
    val assessments: List<Assessment>,
    val traqomUrl: String?,
    val assessmentStatus: String = "",
    val traqomCompleted: Boolean = false,
    val feedback: FeedbackInfo? = null,
    val roster: List<RosterEntry> = emptyList(),
    val feedbackCount: Int = 0,
    /** Set when only the dashboard copy of the class could be shown. */
    val partialReason: String? = null,
) {
    val isCancelled: Boolean get() = isCancelledStatus(classStatus)

    companion object {
        fun parse(o: JSONObject): ClassDetail {
            val feedback = o.optJSONObject("feedback")?.let {
                FeedbackInfo(it.optBoolean("enabled"), it.link("externalURL"), it.optBoolean("submitted"))
            }
            return ClassDetail(
                id = o.text("id"), runCode = o.text("runCode"), title = o.text("title"), code = o.text("code"),
                startDate = o.text("startDate"), endDate = o.text("endDate"), format = o.text("format"),
                classStatus = o.text("classStatus"), venue = o.text("venue"), meetingUrl = o.link("meetingURL"),
                materials = o.array("materials").objects().mapNotNull { m ->
                    m.link("url")?.let { Material(m.text("id"), m.text("title").ifEmpty { "Material" }, it) }
                },
                sessions = o.array("sessions").objects().map { s ->
                    DetailSession(
                        s.text("id"), s.text("label").ifEmpty { "Session" }, s.text("date"), s.text("startTime"), s.text("endTime"),
                        s.text("attendance").takeIf { it == "present" || it == "absent" },
                        if (s.has("presentCount") && !s.isNull("presentCount")) s.optInt("presentCount") else null,
                    )
                },
                ssgAttendanceUrl = o.link("ssgAttendanceURL"),
                assessments = o.array("assessments").objects().map { a ->
                    Assessment(
                        a.text("type"), a.text("label").ifEmpty { "Assessment" }, a.link("questionURL"), a.optBoolean("published"),
                        a.array("submissions").objects().map { s ->
                            Submission(s.text("id"), s.text("fileName"), s.text("fileURL"), s.text("submittedAt"), s.optBoolean("signed"))
                        },
                    )
                },
                traqomUrl = o.link("traqomURL"),
                assessmentStatus = o.text("assessmentStatus"),
                traqomCompleted = o.optBoolean("traqomCompleted"),
                feedback = feedback,
                roster = o.array("roster").objects().map { r ->
                    RosterEntry(r.text("enrolmentId"), r.text("name").ifEmpty { "Learner" }, r.text("status"), r.optBoolean("traqomCompleted"), r.optInt("submissions"))
                },
                feedbackCount = o.optInt("feedbackCount"),
            )
        }

        /** What the dashboard already knows about a class, used when the detail cannot be loaded. */
        fun fromCourse(course: Course, sessions: List<ClassSession>, reason: String) = ClassDetail(
            id = course.id, runCode = course.runCode, title = course.title, code = course.code,
            startDate = course.start, endDate = course.end, format = course.format, classStatus = course.classStatus,
            venue = course.venue, meetingUrl = sessions.firstOrNull { isSafeLink(it.meeting) }?.meeting,
            materials = course.resources,
            sessions = sessions.filter { !it.id.startsWith("run-") }.map {
                DetailSession(it.id, it.subtitle.ifEmpty { "Session" }, it.date.toString(), it.time, it.endTime, null, null)
            },
            ssgAttendanceUrl = null, assessments = emptyList(), traqomUrl = null, partialReason = reason,
        )
    }
}

// ---------------------------------------------------------------- attendance

data class AttendanceLearner(val enrolmentId: String, val name: String, val isPresent: Boolean?, val reason: String)

data class AttendanceSheet(val checkinUrl: String?, val learners: List<AttendanceLearner>) {
    companion object {
        fun parse(data: JSONObject, checkinUrl: String? = data.link("checkinURL")) = AttendanceSheet(
            checkinUrl,
            data.array("learners").objects().map {
                AttendanceLearner(
                    it.text("enrolmentId"), it.text("name").ifEmpty { "Learner" },
                    if (it.isNull("isPresent")) null else it.optBoolean("isPresent"), it.text("reason"),
                )
            },
        )
    }
}

fun attendanceRecords(learners: Collection<AttendanceLearner>): JSONArray = JSONArray().apply {
    learners.filter { it.isPresent != null }.forEach {
        val record = JSONObject().put("enrolmentId", it.enrolmentId).put("isPresent", it.isPresent)
        if (it.isPresent == false && it.reason.isNotBlank()) record.put("reason", it.reason.trim())
        put(record)
    }
}

data class CheckinResult(val message: String, val already: Boolean, val course: String, val session: String)

// ---------------------------------------------------------------- assessments

data class UploadedFile(val fileUrl: String, val folderId: String?)

// ---------------------------------------------------------------- course feedback

data class FeedbackField(
    val id: String,
    val label: String,
    val type: String,
    val required: Boolean,
    val options: List<String>,
    val autofill: String?,
    val readonly: Boolean,
) {
    /** Filled in by the server or fixed: shown but never editable. */
    val locked: Boolean get() = autofill != null || readonly || type == "email"
}

data class FeedbackSection(val id: String, val title: String, val fields: List<FeedbackField>)

data class FeedbackForm(
    val title: String,
    val sections: List<FeedbackSection>,
    val submitted: Boolean,
    val autofill: Map<String, String>,
) {
    val fields: List<FeedbackField> get() = sections.flatMap { it.fields }

    /** The first required question the learner still has to answer, or null when the form is complete. */
    fun firstMissing(answers: Map<String, String>): FeedbackField? = fields.firstOrNull { field ->
        field.required && !field.locked && when (field.type) {
            "rating1to5" -> (answers[field.id]?.toIntOrNull() ?: 0) !in 1..5
            else -> answers[field.id].isNullOrBlank()
        }
    }

    /** Answers in the shape the server expects: ratings as numbers, blanks and server-filled fields left out. */
    fun answersJson(answers: Map<String, String>): JSONObject = JSONObject().apply {
        fields.filter { it.autofill == null && it.type != "email" }.forEach { field ->
            val value = answers[field.id]?.trim().orEmpty()
            if (value.isEmpty()) return@forEach
            if (field.type == "rating1to5") value.toIntOrNull()?.takeIf { it in 1..5 }?.let { put(field.id, it) }
            else put(field.id, value)
        }
    }

    companion object {
        fun parse(o: JSONObject): FeedbackForm {
            val auto = o.optJSONObject("autofill") ?: JSONObject()
            return FeedbackForm(
                o.text("title").ifEmpty { "Course Feedback" },
                o.array("sections").objects().map { s ->
                    FeedbackSection(
                        s.text("id"), s.text("title"),
                        s.array("fields").objects().map { f ->
                            val options = f.array("options").let { a -> (0 until a.length()).map { a.optString(it) } }
                            FeedbackField(
                                f.text("id"), f.text("label"), f.text("type").ifEmpty { "text" }, f.optBoolean("required"),
                                options, f.text("autofill").takeIf { it.isNotBlank() }, f.optBoolean("readonly"),
                            )
                        },
                    )
                },
                o.optBoolean("submitted"),
                auto.keys().asSequence().associateWith { auto.text(it) },
            )
        }
    }
}

// ---------------------------------------------------------------- class quiz

data class QuizQuestion(val id: String, val question: String, val options: List<String>, val correctIndex: Int)

data class Quiz(
    val id: String,
    val courseId: String,
    val title: String,
    val courseTitle: String,
    val courseCode: String,
    val questions: List<QuizQuestion>,
)

/** The learner's latest finished attempt, as the server recorded it. */
data class QuizAttempt(val score: Int, val total: Int, val completedAt: String)

/** A quiz with the signed-in user's latest attempt, if there is one. */
data class QuizLoad(val quiz: Quiz, val previous: QuizAttempt?) {
    companion object {
        fun parse(o: JSONObject): QuizLoad {
            val q = o.optJSONObject("quiz") ?: throw ApiException(502, "The service sent an unexpected answer. Please try again.", fromApi = false)
            val questions = q.array("questions").objects().map { item ->
                val options = item.array("options").let { a -> (0 until a.length()).map { a.optString(it) } }
                QuizQuestion(item.text("id"), item.text("question"), options, item.optInt("correctIndex", -1))
            }.filter { it.id.isNotEmpty() && it.options.size >= 2 }
            val quiz = Quiz(q.text("id"), q.text("courseId"), q.text("title").ifBlank { "Quiz" }, q.text("courseTitle"), q.text("courseCode"), questions)
            val previous = o.optJSONObject("previous")?.let { QuizAttempt(it.optInt("score"), it.optInt("total"), it.text("completedAt")) }
            return QuizLoad(quiz, previous)
        }
    }
}

/** How one question was marked. [selectedIndex] is null when it was left unanswered. */
data class QuizMark(val questionId: String, val correct: Boolean, val correctIndex: Int, val selectedIndex: Int?)

/** One question of a marked quiz, ready to show: what was picked and which option is right. */
data class QuizReview(val question: QuizQuestion, val selectedIndex: Int?, val correctIndex: Int, val correct: Boolean)

/** The server's marking of a submitted quiz. The score is always the server's, never worked out on the phone. */
data class QuizResult(val score: Int, val total: Int, val completedAt: String, val marks: List<QuizMark>) {
    val percent: Int get() = if (total > 0) Math.round(100f * score / total) else 0

    /**
     * The marking lined up with the questions, in the order they were asked. A question the
     * server did not mark (the quiz was edited mid-attempt) falls back to the learner's own
     * answer and the answer key the quiz came with.
     */
    fun review(quiz: Quiz, answers: Map<String, Int>): List<QuizReview> {
        val byId = marks.associateBy { it.questionId }
        return quiz.questions.map { q ->
            val mark = byId[q.id]
            if (mark != null) {
                QuizReview(q, mark.selectedIndex, mark.correctIndex, mark.correct)
            } else {
                val selected = answers[q.id]
                QuizReview(q, selected, q.correctIndex, selected != null && selected == q.correctIndex)
            }
        }
    }

    companion object {
        fun parse(o: JSONObject) = QuizResult(
            o.optInt("score"), o.optInt("total"), o.text("completedAt"),
            o.array("breakdown").objects().map {
                QuizMark(it.text("questionId"), it.optBoolean("correct"), it.optInt("correctIndex", -1), if (it.isNull("selectedIndex")) null else it.optInt("selectedIndex"))
            },
        )
    }
}

/** The request body for a quiz submission: answers are keyed by question id. */
fun quizSubmission(userId: String, courseId: String, quizId: String, answers: Map<String, Int>): JSONObject {
    val picked = JSONObject()
    answers.forEach { (questionId, index) -> picked.put(questionId, index) }
    return JSONObject().put("userId", userId).put("courseId", courseId).put("quizId", quizId).put("answers", picked)
}
