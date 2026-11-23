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

/**
 * The views a signed-in account can take. Learner and Trainer come from the sign-in screen's
 * choice; Admin is what an admin sign-in (Google, allow-listed on the server) shows. The owner
 * account can switch between all four to check each role's screens. The choice changes what this
 * device shows, never what the server allows: every request is still checked by the server.
 */
object Roles {
    const val LEARNER = "learner"
    const val TRAINER = "trainer"
    const val DEVELOPER = "developer"
    const val ADMIN = "admin"

    /** Every role the app can show, in display order. The tabs, chip and switcher follow it. */
    val ALL = listOf(LEARNER, TRAINER, DEVELOPER, ADMIN)

    /** The roles with classes of their own, offered on the sign-in screen and kept on the profile. */
    val CLASSROOM = listOf(LEARNER, TRAINER)

    /** Back-office roles: no class data of their own in the app, only the staff pages. */
    val STAFF = setOf(DEVELOPER, ADMIN)
    private val LABELS = mapOf(LEARNER to "Learner", TRAINER to "Trainer", DEVELOPER to "Developer", ADMIN to "Admin")
    const val OWNER_EMAIL = "angch@tertiaryinfotech.com"

    fun label(role: String): String = LABELS[role] ?: role.replaceFirstChar(Char::titlecase)

    fun isStaff(role: String): Boolean = role in STAFF

    /** The role a sign-in gets: the chosen one when the account holds it, otherwise the first it does hold. */
    fun resolve(chosen: String, held: List<String>): String =
        if (chosen in held) chosen else CLASSROOM.firstOrNull { it in held } ?: LEARNER

    /** A session from before the sign-in choice existed keeps the trainer view when the account teaches. */
    fun legacy(held: List<String>): String = resolve(TRAINER, held)

    fun isOwner(email: String?): Boolean = email?.trim()?.lowercase() == OWNER_EMAIL

    /**
     * The role to show when the app starts again with a saved session: the owner keeps the role
     * last viewed as, an admin sign-in keeps Admin, everyone else their learner or trainer role.
     */
    fun restore(email: String?, stored: String, loginRole: String, adminSession: Boolean, held: List<String>): String = when {
        isOwner(email) && stored in ALL -> stored
        stored == ADMIN && adminSession -> ADMIN
        loginRole.isEmpty() -> legacy(held)
        else -> resolve(stored, held)
    }

    /** The roles this account may view as right now: just [active], except for the owner account, which sees all. */
    fun available(email: String?, @Suppress("UNUSED_PARAMETER") held: List<String>, active: String): List<String> =
        if (isOwner(email)) ALL else listOf(active)
}

/** The signed-in identity: only the classroom roles ([Roles.CLASSROOM]) are kept; Admin comes from the admin sign-in. */
data class Profile(val id: String, val name: String, val email: String, val roles: List<String>) {
    companion object {
        fun parse(o: JSONObject): Profile {
            val roles = o.array("roles").let { a -> (0 until a.length()).map { a.optString(it) } }
                .filter { it in Roles.CLASSROOM }
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

data class Material(val id: String, val title: String, val url: String) {
    /** The website's name for this item, so an older server's "Trainer slides" or "Labs & activities" still reads "Trainer Slides" and "Activities/Lab". */
    val websiteTitle: String
        get() = WEBSITE_TITLES[id] ?: title

    companion object {
        private val WEBSITE_TITLES = mapOf(
            "courseware-link" to "Courseware Link", "lesson-plan" to "Lesson Plan", "guide" to "Learner Guide",
            "facilitator-guide" to "Facilitator Guide", "trainer-slides" to "Trainer Slides", "slides" to "Learner Slides",
            "activities" to "Activities/Lab", "lab-simulator" to "Lab Simulator", "practice-exam" to "Practice Exam",
            "assessment-plan" to "Assessment Plan", "skillsfuture" to "SkillsFuture Link",
        )

        /** The order of the website's Courseware section for each role. */
        fun websiteOrder(trainer: Boolean): List<String> =
            if (trainer) listOf("courseware-link", "lesson-plan", "guide", "facilitator-guide", "trainer-slides", "activities", "lab-simulator", "practice-exam", "assessment-plan", "skillsfuture")
            else listOf("lesson-plan", "guide", "slides", "activities", "lab-simulator", "practice-exam", "skillsfuture")

        internal val KNOWN: Set<String> get() = WEBSITE_TITLES.keys
    }
}

/**
 * The website's Courseware order and names for the role, whatever the server sent: items the
 * website does not show for the role (Learner Slides for a trainer) are dropped, unknown items go last.
 */
fun List<Material>.asOnWebsite(trainer: Boolean): List<Material> {
    val order = Material.websiteOrder(trainer)
    return withIndex()
        .filter { it.value.id in order || it.value.id !in Material.KNOWN }
        .sortedBy { (i, m) -> order.indexOf(m.id).takeIf { it >= 0 } ?: (order.size + i) }
        .map { it.value.copy(title = it.value.websiteTitle) }
}

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
            Triple("trainer-slides", "Trainer Slides", "trainerSlidesURL"),
            Triple("slides", "Learner Slides", "slidesURL"),
            Triple("guide", "Learner Guide", "guideURL"),
            Triple("activities", "Activities/Lab", "activitiesURL"),
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
    // The website class page's panels. All optional, so an older server's reply still reads.
    val courseDetails: CourseDetails? = null,
    val trainerNames: List<String> = emptyList(),
    val digitalAttendanceId: String = "",
    val announcements: List<Announcement> = emptyList(),
    val briefingOnAssessment: List<String> = emptyList(),
    /** The provider shows the Course Feedback card beside TRAQOM Survey; null from an older server. */
    val showCourseFeedback: Boolean? = null,
    /** Trainer: the Course Feedback link (may be a path on the portal). */
    val courseFeedbackUrl: String = "",
    /** Trainer: the class's public SWDA enrolment form (a path on the portal). */
    val swdaEnrolmentUrl: String = "",
    /** Trainer: the Post Course Feedback Summary (averages and comments, no names). */
    val feedbackSummary: FeedbackSummary? = null,
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
                courseDetails = o.optJSONObject("courseDetails")?.let(CourseDetails::parse),
                trainerNames = o.strings("trainerNames").map { it.trim() }.filter { it.isNotEmpty() },
                digitalAttendanceId = o.text("digitalAttendanceId").trim(),
                announcements = o.array("announcements").objects().map(Announcement::parse),
                briefingOnAssessment = o.strings("briefingOnAssessment").map { it.trim() }.filter { it.isNotEmpty() },
                showCourseFeedback = if (o.has("showCourseFeedback") && !o.isNull("showCourseFeedback")) o.optBoolean("showCourseFeedback") else null,
                courseFeedbackUrl = o.text("courseFeedbackURL").trim(),
                swdaEnrolmentUrl = o.text("swdaEnrolmentURL").trim(),
                feedbackSummary = o.optJSONObject("feedbackSummary")?.let(FeedbackSummary::parse),
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

/** A number the server may send as a number, as text or as null. */
fun JSONObject.number(key: String): Double? =
    if (!has(key) || isNull(key)) null else (opt(key) as? Number)?.toDouble() ?: text(key).toDoubleOrNull()

fun JSONObject.strings(key: String): List<String> = array(key).let { a -> (0 until a.length()).mapNotNull { if (a.isNull(it)) null else a.optString(it) } }

/** The website class page's "Course Details" panel. */
data class CourseDetails(
    val tscTitle: String = "",
    val tscCode: String = "",
    val caslSkills: String = "",
    val fundingValidityStart: String = "",
    val fundingValidityEnd: String = "",
    val trainingHours: Double? = null,
    val assessmentHours: Double? = null,
) {
    /** True when the funding validity has ended; null when no end date is known. */
    fun expired(today: LocalDate = LocalDate.now(SGT)): Boolean? = parseDay(fundingValidityEnd)?.isBefore(today)

    companion object {
        fun parse(o: JSONObject) = CourseDetails(
            o.text("tscTitle").trim(), o.text("tscCode").trim(), o.text("caslSkills").trim(),
            o.text("fundingValidityStart").trim(), o.text("fundingValidityEnd").trim(),
            o.number("trainingHours"), o.number("assessmentHours"),
        )
    }
}

data class Announcement(
    val id: String,
    val title: String,
    val message: String,
    val linkUrl: String?,
    val fileName: String,
    val fileUrl: String?,
    val postedBy: String,
    val createdAt: String,
) {
    companion object {
        fun parse(o: JSONObject) = Announcement(
            o.text("id"), o.text("title"), o.text("message"), o.link("linkURL"), o.text("fileName"), o.link("fileURL"),
            o.text("postedBy"), o.text("createdAt"),
        )
    }
}

/** The class's Post Course Feedback Summary: average per 1–5 question, overall average, comments. */
data class FeedbackSummary(val count: Int, val overall: Double?, val questions: List<Question>, val comments: List<String>) {
    data class Question(val label: String, val average: Double?)

    companion object {
        fun parse(o: JSONObject) = FeedbackSummary(
            o.optInt("count"), o.number("overall"),
            o.array("questions").objects().map { Question(it.text("label"), it.number("average")) },
            o.strings("comments").filter { it.isNotBlank() },
        )
    }
}

/** "1 hr", "7 hrs", "1.5 hrs"; "N/A" when unknown. */
fun hoursText(value: Double?): String {
    if (value == null) return "N/A"
    val text = if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.ENGLISH, "%.1f", value)
    return "$text ${if (value == 1.0) "hr" else "hrs"}"
}

/** A 1–5 score with one decimal when needed: "4", "4.5"; "—" when there is none. */
fun scoreText(value: Double?): String = when {
    value == null -> "—"
    value == Math.rint(value) -> value.toLong().toString()
    else -> String.format(Locale.ENGLISH, "%.1f", value)
}

private val LONG_DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "16 Nov 2026", as the website prints dates; the raw text when unreadable. */
fun longDay(value: String): String = parseDay(value)?.format(LONG_DAY) ?: value.take(10)

/** "16 Nov 2026 – 17 Nov 2026", one date when both are the same. */
fun longRange(start: String, end: String): String {
    val a = longDay(start)
    val b = longDay(end)
    if (a.isBlank()) return b.ifBlank { "Dates to be confirmed" }
    return if (b.isBlank() || a == b) a else "$a – $b"
}

// ---------------------------------------------------------------- Assessment Summary Record

data class SummaryParty(val name: String, val signDate: String, val signedAt: String) {
    val day: String get() = signDate.ifBlank { signedAt }

    companion object {
        fun parse(o: JSONObject?) = o?.let { SummaryParty(it.text("name"), it.text("signDate"), it.text("signedAt")) }
    }
}

data class SummaryRecord(val learner: SummaryParty?, val trainer: SummaryParty?, val fileUrl: String?) {
    companion object {
        fun parse(o: JSONObject?) = o?.let {
            SummaryRecord(SummaryParty.parse(it.optJSONObject("learner")), SummaryParty.parse(it.optJSONObject("trainer")), it.optJSONObject("file")?.link("url"))
        }
    }
}

data class LearnerSummaryStatus(val learnerUserId: String, val learnerName: String, val record: SummaryRecord?)

/** GET /api/assessments/summary-record: the learner's own record, or every learner's for the trainer. */
data class SummaryReply(val record: SummaryRecord?, val learners: List<LearnerSummaryStatus>, val hasSignature: Boolean) {
    companion object {
        fun parse(o: JSONObject) = SummaryReply(
            SummaryRecord.parse(o.optJSONObject("record")),
            o.array("learners").objects().map { LearnerSummaryStatus(it.text("learnerUserId"), it.text("learnerName").ifBlank { "Learner" }, SummaryRecord.parse(it.optJSONObject("record"))) },
            o.optBoolean("hasSignature"),
        )
    }
}

/** Whose saved signature: the learner's own, or the trainer's as assessor. */
enum class SignerKind(val path: String, val title: String, val nameLabel: String) {
    LEARNER("/api/learner/signature", "Your signature", "Full name (as in NRIC)"),
    ASSESSOR("/api/trainer/assessor-signature", "Assessor signature", "Assessor name"),
}

/** Saved signer details. The learner and assessor endpoints name the name field differently. */
data class SignerRecord(val name: String, val nric: String, val hasSignature: Boolean) {
    companion object {
        fun parse(o: JSONObject?): SignerRecord {
            val d = o?.optJSONObject("data") ?: return SignerRecord("", "", false)
            return SignerRecord(d.text("learner_name").ifBlank { d.text("assessor_name") }, d.text("nric"), d.text("signature_png").isNotBlank())
        }
    }
}

/** The body for saving signer details. [signaturePng] null keeps the saved signature. */
fun signerBody(kind: SignerKind, name: String, nric: String, signaturePng: String?, today: LocalDate = LocalDate.now(SGT)): JSONObject =
    JSONObject().put("name", name.trim()).put("nric", nric.trim().uppercase()).apply {
        if (kind == SignerKind.ASSESSOR) put("signDate", today.toString())
        if (signaturePng != null) put("signaturePng", signaturePng)
    }

/** True when signing failed only because no signature is saved yet (the server answers 409). */
fun needsSignatureSetup(e: Throwable): Boolean {
    val message = e.message.orEmpty().lowercase()
    return (e is ApiException && e.status == 409) || "signature" in message || "assessor" in message
}

/** The assessment's Publish switch also flips the older per-class flag for the written and practical papers. */
fun legacyPublishField(type: String): String? = mapOf("writtenAssessment" to "written", "practicalExam" to "practical")[type]

// ---------------------------------------------------------------- staff (Admin and Developer)

/** One class run in the admin lists (GET /api/mobile/staff?view=classes). */
data class StaffClass(
    val id: String,
    val runCode: String,
    val title: String,
    val code: String,
    val startDate: String,
    val endDate: String,
    val format: String,
    val classStatus: String,
    val hasMeetingLink: Boolean,
    val trainers: List<String>,
    val learners: Int,
    val venue: String,
) {
    val start: LocalDate? get() = parseDay(startDate)
    val end: LocalDate? get() = parseDay(endDate.ifBlank { startDate }) ?: start
    val cancelled: Boolean get() = classStatus.trim().lowercase().startsWith("cancel")

    fun covers(day: LocalDate): Boolean {
        val a = start ?: return false
        val b = end ?: a
        return !day.isBefore(a) && !day.isAfter(b)
    }

    fun matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || title.contains(q, true) || code.contains(q, true) || runCode.contains(q) || trainers.any { it.contains(q, true) }
    }

    companion object {
        fun parse(o: JSONObject) = StaffClass(
            o.text("id"), o.text("runCode"), o.text("title"), o.text("code"), o.text("startDate"), o.text("endDate"),
            o.text("format"), o.text("classStatus"), o.optBoolean("hasMeetingLink"),
            o.strings("trainers").map { it.trim() }.filter { it.isNotEmpty() }, o.optInt("learners"), o.text("venue").trim(),
        )

        fun parseAll(o: JSONObject): List<StaffClass> = o.array("classes").objects().map(::parse)
    }
}

/** A trainer the admin can WhatsApp about a class (GET /api/mobile/staff?view=trainers). [phone] is E.164 or null. */
data class StaffTrainer(val name: String, val email: String, val phone: String?) {
    fun matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || name.contains(q, true) || email.contains(q, true)
    }

    /** Already on the class, by name (as the class list names its trainers). */
    fun assignedTo(item: StaffClass): Boolean = item.trainers.any { it.equals(name, ignoreCase = true) }

    companion object {
        fun parseAll(o: JSONObject): List<StaffTrainer> = o.array("trainers").objects().map {
            StaffTrainer(it.text("name").trim(), it.text("email").trim(), it.text("phone").trim().ifBlank { null })
        }.filter { it.name.isNotEmpty() }
    }
}

/** The message the admin sends a trainer about a class; they can edit it in WhatsApp before sending. */
fun trainerMessage(trainer: StaffTrainer, item: StaffClass): String {
    val first = trainer.name.split(" ").firstOrNull { it.isNotEmpty() } ?: trainer.name
    val lines = mutableListOf(
        "Hi $first, this is Tertiary Infotech Academy.",
        "Are you available to train this class?",
        "",
        item.title + (item.code.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
    )
    if (item.runCode.isNotBlank()) lines.add("Course Run: ${item.runCode}")
    lines.add("Dates: ${longRange(item.startDate, item.endDate)}")
    if (item.format.isNotBlank()) lines.add("Class type: ${item.format}")
    if (item.venue.isNotBlank()) lines.add("Venue: ${item.venue}")
    lines += listOf("", "Please reply to confirm. Thank you!")
    return lines.joinToString("\n")
}

/** https://wa.me/<digits>?text=<message>; null when the phone has no digits. */
fun whatsAppUrl(phone: String, text: String): String? {
    val digits = phone.filter(Char::isDigit)
    if (digits.isEmpty()) return null
    return "https://wa.me/$digits?text=" + java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}

/** One course in the catalogue (GET /api/mobile/staff?view=courses). */
data class StaffCourse(
    val id: String,
    val title: String,
    val code: String,
    val courseType: String,
    val status: String,
    val tscTitle: String,
    val tscCode: String,
    val trainingHours: Double?,
    val assessmentHours: Double?,
    val fundingValidityEnd: String,
    val upcomingRuns: Int,
    val nextRun: String,
    val fundingValidityStart: String = "",
) {
    val fundingEnd: LocalDate? get() = parseDay(fundingValidityEnd)

    /** Days until funding ends, when that is within 3 months (90 days) and not yet past. */
    fun expiringInDays(today: LocalDate = LocalDate.now(SGT)): Int? {
        if (expired(today)) return null
        val end = fundingEnd ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, end).toInt()
        return if (days <= 90) days.coerceAtLeast(0) else null
    }

    /** "Funding validity: 30 Jan 2025 – 29 Jan 2027"; null when neither date is known. */
    val fundingValidityText: String?
        get() = if (fundingValidityStart.isBlank() && fundingValidityEnd.isBlank()) null
        else "Funding validity: " + listOf(fundingValidityStart, fundingValidityEnd).joinToString(" – ") { if (it.isBlank()) "N/A" else longDay(it) }

    fun expired(today: LocalDate = LocalDate.now(SGT)): Boolean =
        title.contains("(expired)", true) || (fundingEnd?.isBefore(today) ?: false)

    fun matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || title.contains(q, true) || code.contains(q, true) || tscCode.contains(q, true)
    }

    companion object {
        fun parse(o: JSONObject) = StaffCourse(
            o.text("id"), o.text("title"), o.text("code"), o.text("courseType"), o.text("status"), o.text("tscTitle"), o.text("tscCode"),
            o.number("trainingHours"), o.number("assessmentHours"), o.text("fundingValidityEnd"), o.optInt("upcomingRuns"), o.text("nextRun"),
            o.text("fundingValidityStart"),
        )

        fun parseAll(o: JSONObject): List<StaffCourse> = o.array("courses").objects().map(::parse)
    }
}

/** "Funding expires today" / "Funding expires in 1 day" / "Funding expires in N days". */
fun fundingExpiresText(days: Int): String =
    if (days == 0) "Funding expires today" else "Funding expires in $days day${if (days == 1) "" else "s"}"

/** The class status as the TMS records it, for the chip on every class card: blank reads Pending, any cancel reads Cancelled. */
fun classStatusLabel(status: String): String {
    val s = status.trim()
    return when {
        s.isEmpty() -> "Pending"
        s.lowercase().startsWith("cancel") -> "Cancelled"
        else -> s
    }
}

// ---------------------------------------------------------------- certificates

/** One class's certificate (GET /api/mobile/certificates); [certificateUrl] is null until it is issued. */
data class Certificate(
    val id: String,
    val courseId: String,
    val title: String,
    val code: String,
    val runCode: String,
    val startDate: String,
    val endDate: String,
    val result: String,
    val certificateUrl: String?,
) {
    companion object {
        fun parseAll(o: JSONObject): List<Certificate> = o.array("certificates").objects().map {
            Certificate(
                it.text("id"), it.text("courseID"), it.text("title"), it.text("code"), it.text("runCode"),
                it.text("startDate"), it.text("endDate"), it.text("result"), it.link("certificateURL"),
            )
        }
    }
}

// ---------------------------------------------------------------- support and trainer credentials

/** The academy's contacts (GET /api/mobile/support). */
data class SupportInfo(val email: String?, val phone: String?, val whatsapp: String?, val address: String?, val feedback: Boolean) {
    companion object {
        val SAMPLE = SupportInfo(
            "enquiry@tertiaryinfotech.com", "61000613", "+65 8866 6375",
            "12 Woodlands Square, Woods Square Tower 1, Singapore 737715", true,
        )

        fun parse(o: JSONObject) = SupportInfo(
            o.text("email").trim().ifBlank { null }, o.text("phone").trim().ifBlank { null }, o.text("whatsapp").trim().ifBlank { null },
            o.text("address").trim().ifBlank { null }, o.optBoolean("feedback"),
        )

        /** A phone number's digits for tel: and wa.me; a bare 8-digit Singapore number gets 65. */
        fun digits(phone: String): String = phone.filter(Char::isDigit).let { if (it.length == 8) "65$it" else it }
    }
}

/** The trainer's credentials (GET/POST /api/mobile/trainer-profile). */
data class TrainerCredentials(
    val aclp: Boolean,
    val dddpl: Boolean,
    val taepp: Boolean,
    val areasOfExpertise: List<String>,
    val skillsTags: List<String>,
) {
    /** The badges to show, in a fixed order. */
    val badges: List<String> get() = listOfNotNull("ACLP".takeIf { aclp }, "DDDPL".takeIf { dddpl }, "TAEPP".takeIf { taepp })

    companion object {
        fun parse(o: JSONObject) = TrainerCredentials(
            o.optBoolean("aclp"), o.optBoolean("dddpl"), o.optBoolean("taepp"), o.strings("areasOfExpertise"), o.strings("skillsTags"),
        )
    }
}

/** A tag added to a list, ignoring blanks and case-insensitive duplicates; null when nothing changes. */
fun addTag(tags: List<String>, raw: String): List<String>? {
    val tag = raw.trim()
    if (tag.isEmpty() || tags.any { it.equals(tag, ignoreCase = true) }) return null
    return tags + tag
}

// ---------------------------------------------------------------- Assessment Grading

/** The website's assessment methods: abbreviation and name. */
object AssessmentMethod {
    val INFO = mapOf(
        "writtenAssessment" to ("WA" to "Written Assessment"),
        "practicalExam" to ("PP" to "Practical Performance"),
        "caseStudy" to ("CS" to "Case Study"),
        "rolePlay" to ("RP" to "Role Play"),
        "oralQuestioning" to ("OQ" to "Oral Questioning"),
        "project" to ("PJ" to "Project"),
        "assignment" to ("AS" to "Assignment"),
    )
    val DEFAULT = listOf("writtenAssessment", "practicalExam")

    fun abbr(method: String): String = INFO[method]?.first ?: method
    fun label(method: String): String = INFO[method]?.second ?: method

    /** "WA = Written Assessment · PP = Practical Performance · Signed = … " — the grading footer. */
    fun legend(methods: List<String>): String =
        methods.joinToString(" · ") { "${abbr(it)} = ${label(it)}" } +
            " · Signed = your assessor signature stamped on the learner's submitted files. TRAQOM is ticked by you: SSG does not report completion."
}

/** One learner on the trainer's grading roster (GET /api/trainer/class-students?withMeta=1). */
data class RosterStudent(
    val enrolmentId: String,
    val userId: String,
    val name: String,
    val competentStatus: String,
    val source: String,
    val isCompetent: Boolean?,
    val submittedAssessments: List<String>,
    val traqomCompleted: Boolean,
    val submissionCount: Int,
    val assessorSigned: Boolean,
    val folderUrl: String?,
    val enrolmentIdList: List<String> = emptyList(),
    val userIdList: List<String> = emptyList(),
) {
    val enrolmentIds: List<String> get() = enrolmentIdList.ifEmpty { listOf(enrolmentId) }
    val userIds: List<String> get() = userIdList.ifEmpty { listOfNotNull(userId.ifBlank { null }) }
    fun submitted(method: String): Boolean = method in submittedAssessments
    val hasSubmissions: Boolean get() = submissionCount > 0 || submittedAssessments.isNotEmpty()

    /** Can be signed (or unsigned) as assessor: something was submitted and the learner has an account. */
    val signable: Boolean get() = hasSubmissions && userIds.isNotEmpty()

    /** The Not Yet Competent button is off once the learner is already graded so. */
    val notYetCompetent: Boolean get() = isCompetent == false && competentStatus.contains("not", ignoreCase = true)
    val resultText: String get() = if (competentStatus.isNotBlank()) "Result: $competentStatus" else "Result: not graded"

    companion object {
        fun parse(o: JSONObject) = RosterStudent(
            o.text("enrolment_id"), o.text("user_id"), o.text("student_name").ifBlank { "Learner" }, o.text("competent_status"),
            o.text("source").ifBlank { "manual" }, if (o.has("is_competent") && !o.isNull("is_competent")) o.optBoolean("is_competent") else null,
            o.strings("submitted_assessments"), o.optBoolean("traqom_completed"), o.optInt("submission_count"), o.optBoolean("assessor_signed"),
            o.link("assessment_folder_url"), o.strings("enrolment_ids"), o.strings("user_ids"),
        )
    }
}

data class Roster(val students: List<RosterStudent>, val methods: List<String>) {
    /** Learners with submissions still to sign, for "Sign all N submitted". */
    val unsigned: List<RosterStudent> get() = students.filter { it.signable && !it.assessorSigned }

    companion object {
        fun parse(o: JSONObject) = Roster(
            o.array("students").objects().map(RosterStudent::parse),
            o.strings("assessment_methods").ifEmpty { AssessmentMethod.DEFAULT },
        )
    }
}

/** The announcement composer's attachment limit, as on the website. */
const val MAX_ANNOUNCEMENT_BYTES = 50L * 1024 * 1024

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

// ---------------------------------------------------------------- trainer payouts

/** One completed payout for a class the trainer taught, from /api/trainer/<id>/payout-history. */
data class Payout(
    val id: String,
    val title: String,
    val courseCode: String,
    val learners: Int,
    val amount: Double,
    val paymentDate: String,
    val startDate: String,
    val endDate: String,
    /** "wsq" for a course run, "manual" for a class added by payroll. */
    val source: String,
) {
    companion object {
        fun parse(o: JSONObject) = Payout(
            o.text("id"), o.text("course_title"), o.text("course_code"), o.optInt("num_learners"),
            // Postgres numerics arrive as text ("1200.00"); a plain number is read the same way.
            o.text("actual_payout").toDoubleOrNull() ?: 0.0,
            o.text("payment_date"), o.text("start_date"), o.text("end_date"), o.text("source"),
        )

        fun parseAll(json: JSONObject): List<Payout> = json.array("data").objects().map(::parse)
    }
}

/** Singapore dollars, e.g. "S$1,200.00". */
fun money(amount: Double): String = "S$" + String.format(Locale.ENGLISH, "%,.2f", amount)
