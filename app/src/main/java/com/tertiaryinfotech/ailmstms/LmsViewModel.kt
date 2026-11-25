package com.tertiaryinfotech.ailmstms

import android.app.Application
import android.app.NotificationManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tertiaryinfotech.ailmstms.data.ApiException
import com.tertiaryinfotech.ailmstms.data.Assessment
import com.tertiaryinfotech.ailmstms.data.AttendanceLearner
import com.tertiaryinfotech.ailmstms.data.AttendanceSheet
import com.tertiaryinfotech.ailmstms.data.ClassDetail
import com.tertiaryinfotech.ailmstms.data.Dashboard
import com.tertiaryinfotech.ailmstms.data.DemoApi
import com.tertiaryinfotech.ailmstms.data.FeedbackForm
import com.tertiaryinfotech.ailmstms.data.LmsApi
import com.tertiaryinfotech.ailmstms.data.MAX_UPLOAD_BYTES
import com.tertiaryinfotech.ailmstms.data.Payout
import com.tertiaryinfotech.ailmstms.data.Profile
import com.tertiaryinfotech.ailmstms.data.ProfilePatch
import com.tertiaryinfotech.ailmstms.data.QuizAttempt
import com.tertiaryinfotech.ailmstms.data.QuizLink
import com.tertiaryinfotech.ailmstms.data.QuizLoad
import com.tertiaryinfotech.ailmstms.data.QuizResult
import com.tertiaryinfotech.ailmstms.data.parseQuizLink
import com.tertiaryinfotech.ailmstms.data.Roles
import com.tertiaryinfotech.ailmstms.data.RosterEntry
import com.tertiaryinfotech.ailmstms.data.SGT
import com.tertiaryinfotech.ailmstms.data.SignerKind
import com.tertiaryinfotech.ailmstms.data.SignerRecord
import com.tertiaryinfotech.ailmstms.data.StaffClass
import com.tertiaryinfotech.ailmstms.data.StaffCourse
import com.tertiaryinfotech.ailmstms.data.StaffTrainer
import com.tertiaryinfotech.ailmstms.data.Certificate
import com.tertiaryinfotech.ailmstms.data.MAX_ANNOUNCEMENT_BYTES
import com.tertiaryinfotech.ailmstms.data.Roster
import com.tertiaryinfotech.ailmstms.data.RosterStudent
import com.tertiaryinfotech.ailmstms.data.SupportInfo
import com.tertiaryinfotech.ailmstms.data.TrainerCredentials
import com.tertiaryinfotech.ailmstms.data.addTag
import com.tertiaryinfotech.ailmstms.data.SummaryReply
import com.tertiaryinfotech.ailmstms.data.needsSignatureSetup
import com.tertiaryinfotech.ailmstms.data.ThemeMode
import com.tertiaryinfotech.ailmstms.data.UploadSource
import com.tertiaryinfotech.ailmstms.data.UserProfile
import com.tertiaryinfotech.ailmstms.data.isSafeLink
import com.tertiaryinfotech.ailmstms.data.pdfCandidates
import com.tertiaryinfotech.ailmstms.notify.ClassAlerts
import com.tertiaryinfotech.ailmstms.ui.DemoPdf
import com.tertiaryinfotech.ailmstms.ui.openLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** A screen opened on top of the tabs. */
sealed interface Screen {
    val title: String

    data class ClassRoom(val id: String) : Screen {
        override val title = "Class details"
    }

    data class Pdf(val name: String, val url: String) : Screen {
        override val title get() = name
    }

    data class Assessments(val classId: String) : Screen {
        override val title = "Assessments"
    }

    data class Attendance(val sessionId: String, val label: String) : Screen {
        override val title = "Attendance"
    }

    data class Feedback(val runId: String, val courseTitle: String) : Screen {
        override val title = "Course feedback"
    }

    data class Quiz(val courseId: String, val quizId: String) : Screen {
        override val title = "Quiz"
    }

    data object ChangePassword : Screen {
        override val title = "Change password"
    }

    /** Trainer: the grading roster of a class. */
    data class Grading(val classId: String) : Screen {
        override val title = "Assessment Grading"
    }

    /** Trainer: certificates from courses attended as a learner (from Account). */
    data object Certificates : Screen {
        override val title = "My Certificates"
    }
}

/** Loading, ready or failed: every screen that fetches something shows all three. */
sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/** One assessment file on its way to the academy. [progress] is 0..1 while the file is being sent. */
data class UploadState(val type: String, val fileName: String, val progress: Float, val error: String? = null)

/** A result the user must see and dismiss, such as the answer to a check-in. */
data class ResultMessage(val title: String, val message: String, val success: Boolean)

/** A material fetched for the share sheet; [title] names it there. */
data class DownloadedFile(val file: File, val title: String)

/** A download that failed, with the original link so the browser can be offered instead. */
data class DownloadProblem(val message: String, val url: String)

class LmsViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        /** Stands for the learner themself in [summarySigning]. */
        const val SELF = "self"
    }

    private val lms = app as LmsApplication
    val repo = lms.repo
    private var demo = DemoApi()
    private val api: LmsApi get() = if (isDemo) demo else repo

    var isDemo by mutableStateOf(false); private set
    var profile by mutableStateOf<Profile?>(null); private set
    var userProfile by mutableStateOf<UserProfile?>(null); private set
    var dashboard by mutableStateOf(Dashboard.EMPTY); private set
    /** The role this sign-in is viewing as. Everything role-specific (tabs, classes, class detail) follows it. */
    var role by mutableStateOf(repo.store.role); private set

    /** The "Sign in as" choice on the sign-in screen. It becomes [role] at sign-in and returns to Learner at sign-out. */
    var loginRole by mutableStateOf(Roles.LEARNER); private set

    /** The roles offered in "Switch role": every role this account holds (Admin after an admin sign-in). */
    val availableRoles: List<String>
        get() = if (isDemo) listOf(role) else Roles.available(profile?.email, profile?.roles.orEmpty(), role, repo.store.adminSession)
    val canSwitchRole: Boolean get() = availableRoles.size > 1

    /** Showing a back-office role (Developer or Admin) rather than Learner or Trainer. */
    val isStaffView: Boolean get() = Roles.isStaff(role)
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var pushEnabled by mutableStateOf(repo.store.pushEnabled); private set

    // Sign-in form
    var sent by mutableStateOf(false); private set
    var usePassword by mutableStateOf(false); private set
    var email by mutableStateOf("")
    var otp by mutableStateOf("")
    var password by mutableStateOf("")
    var mustChangePassword by mutableStateOf(repo.store.mustChangePassword); private set

    // Appearance: the cached values apply at launch, before the profile is fetched again.
    var themeMode by mutableStateOf(ThemeMode.from(repo.store.theme)); private set
    var brandColor by mutableStateOf(repo.store.brandColor); private set

    // Navigation and one-off messages
    val stack = mutableStateListOf<Screen>()
    var notice by mutableStateOf<String?>(null)
    var result by mutableStateOf<ResultMessage?>(null)

    // Per-screen state
    var classState by mutableStateOf<Load<ClassDetail>>(Load.Idle); private set
    /** The class page's opened sections. All start collapsed; kept while a PDF, quiz or form is open on top. */
    val openSections = mutableStateListOf<String>()
    var attendanceState by mutableStateOf<Load<AttendanceSheet>>(Load.Idle); private set
    val attendanceDraft = mutableStateMapOf<String, AttendanceLearner>()
    var working by mutableStateOf(false); private set
    var upload by mutableStateOf<UploadState?>(null); private set
    var feedbackState by mutableStateOf<Load<FeedbackForm>>(Load.Idle); private set
    val feedbackAnswers = mutableStateMapOf<String, String>()
    var formError by mutableStateOf<String?>(null)
    var pdfState by mutableStateOf<Load<File>>(Load.Idle); private set
    var pdfProgress by mutableStateOf<Float?>(null); private set
    private var pdfJob: Job? = null

    // Trainer payouts (the Payout tab). Idle until the tab is first opened.
    var payoutState by mutableStateOf<Load<List<Payout>>>(Load.Idle); private set

    // Admin and Developer pages (GET /api/mobile/staff). Idle until first shown.
    var adminClasses by mutableStateOf<Load<List<StaffClass>>>(Load.Idle); private set
    var catalogue by mutableStateOf<Load<List<StaffCourse>>>(Load.Idle); private set
    /** Admin: the runs of the course opened from the catalogue ([courseRunsId]). */
    var courseRuns by mutableStateOf<Load<List<StaffClass>>>(Load.Idle); private set
    var courseRunsId by mutableStateOf<String?>(null); private set
    /** Admin: trainers to WhatsApp about a class. Loaded when the picker first opens. */
    var staffTrainers by mutableStateOf<Load<List<StaffTrainer>>>(Load.Idle); private set
    var calendarClasses by mutableStateOf<List<StaffClass>>(emptyList()); private set
    var calendarMonth by mutableStateOf<YearMonth?>(null); private set
    var calendarLoading by mutableStateOf(false); private set
    var calendarProblem by mutableStateOf<String?>(null); private set
    private var calendarJob: Job? = null
    private var pendingMonth: YearMonth? = null

    // Assessment Summary Record of the open class, and the signature setup sheet.
    var summaryState by mutableStateOf<Load<SummaryReply>>(Load.Idle); private set
    private var summaryRunId: String? = null
    /** Learners being signed for (the trainer), or [SELF] while the learner signs. */
    val summarySigning = mutableStateListOf<String>()
    var summaryProblem by mutableStateOf<String?>(null)
    var signerSheet by mutableStateOf<SignerKind?>(null); private set
    /** The sheet is signing records now: a signature must be drawn, and the button reads "Sign". */
    var signerSigning by mutableStateOf(false); private set
    /** How many records the signature goes on. */
    val signerCount: Int get() = signerGrading?.size ?: pendingSign.size
    var signerState by mutableStateOf<Load<SignerRecord>>(Load.Idle); private set
    var signerSaving by mutableStateOf(false); private set
    var signerProblem by mutableStateOf<String?>(null)
    /** Who to sign for once the signature is saved: [SELF] for the learner, learner ids for the assessor. */
    private var pendingSign by mutableStateOf<List<String>>(emptyList())

    /** The signing sheet's title and what the signature is stamped on (summary record or submitted files). */
    var signerTitle by mutableStateOf("Sign Summary Record"); private set
    var signerWhat by mutableStateOf("the Assessment Summary Record"); private set
    /** Learners whose submitted files the drawn signature goes on ("Sign Assessments"); null for the summary record. */
    private var signerGrading by mutableStateOf<List<RosterStudent>?>(null)

    // Trainer announcements: the composer's attachment and state.
    var announcementAttachment by mutableStateOf<AnnouncementFile?>(null); private set
    var announcementPosting by mutableStateOf(false); private set
    var announcementProblem by mutableStateOf<String?>(null)

    // Assessment Grading roster.
    var rosterState by mutableStateOf<Load<Roster>>(Load.Idle); private set
    val gradingBusy = mutableStateListOf<String>()
    var gradingProblem by mutableStateOf<String?>(null)

    // Certificates, Support and the trainer's credentials.
    var certificatesState by mutableStateOf<Load<List<Certificate>>>(Load.Idle); private set
    var supportState by mutableStateOf<Load<SupportInfo>>(Load.Idle); private set
    var supportSending by mutableStateOf(false); private set
    var supportNotice by mutableStateOf<String?>(null)
    var supportProblem by mutableStateOf<String?>(null)
    var credentialsState by mutableStateOf<Load<TrainerCredentials>>(Load.Idle); private set
    var credentialsProblem by mutableStateOf<String?>(null)

    // The trainer's Publish switches: assessments being saved and the last failure per assessment.
    val publishing = mutableStateListOf<String>()
    val publishProblems = mutableStateMapOf<String, String>()

    // Download from the in-app reader: fetched here, handed to the share sheet by the screen.
    var downloading by mutableStateOf(false); private set
    var downloaded by mutableStateOf<DownloadedFile?>(null)
    var downloadProblem by mutableStateOf<DownloadProblem?>(null)
    private var downloadJob: Job? = null
    private var uploadJob: Job? = null

    // Class quiz. [pendingQuiz] is a scanned quiz waiting for the user to sign in.
    private val quizHost: String = runCatching { java.net.URI(BuildConfig.API_BASE_URL).host }.getOrNull().orEmpty()
    var pendingQuiz by mutableStateOf<QuizLink?>(null); private set
    var quizState by mutableStateOf<Load<QuizLoad>>(Load.Idle); private set
    val quizAnswers = mutableStateMapOf<String, Int>()
    var quizIndex by mutableStateOf(0); private set
    var quizStarted by mutableStateOf(false); private set
    var quizResult by mutableStateOf<QuizResult?>(null); private set

    init {
        if (repo.store.token.isNotEmpty()) {
            profile = runCatching { Profile.parse(JSONObject(repo.store.profile)) }.getOrNull()
            userProfile = runCatching { UserProfile.parse(JSONObject(repo.store.userProfile)) }.getOrNull()
            val signedIn = profile
            if (signedIn == null) {
                repo.store.clear()
            } else {
                // A session from before the "Sign in as" choice keeps the trainer view when the account teaches;
                // the owner keeps the role last viewed as, and an admin sign-in keeps Admin.
                val stored = repo.store.loginRole
                val active = Roles.restore(signedIn.email, repo.store.role, stored, repo.store.adminSession, signedIn.roles)
                if (stored.isEmpty()) repo.store.loginRole = active
                if (repo.store.role != active) repo.store.role = active
                role = active
                loginRole = repo.store.loginRole
                refresh()
            }
        }
    }

    // ---------------------------------------------------------------- plumbing

    private fun describe(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException -> "You appear to be offline. Check your connection and retry."
        is java.net.SocketTimeoutException -> "The connection timed out. Please try again."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
    }

    /** The user's message for a failure. A rejected session token signs the user out, from any screen. */
    private fun failure(e: Throwable): String {
        if (e is ApiException && e.sessionEnded) {
            endSession()
            error = "Your session has ended. Please sign in again."
        }
        return describe(e)
    }

    private fun endSession() {
        if (!isDemo) {
            repo.store.clear()
            lms.pdfs.clear()
        }
        lms.downloads.clear()
        ClassAlerts.cancelAll(lms)
        lms.getSystemService(NotificationManager::class.java).cancelAll()
        pdfJob?.cancel()
        uploadJob?.cancel()
        downloadJob?.cancel()
        downloading = false
        downloaded = null
        downloadProblem = null
        payoutState = Load.Idle
        resetStaff()
        resetSummary()
        resetAccountPages()
        loginRole = Roles.LEARNER
        role = Roles.LEARNER
        isDemo = false
        demo = DemoApi()
        profile = null
        userProfile = null
        dashboard = Dashboard.EMPTY
        pushEnabled = false
        mustChangePassword = false
        sent = false
        otp = ""
        password = ""
        stack.clear()
        classState = Load.Idle
        attendanceState = Load.Idle
        feedbackState = Load.Idle
        pdfState = Load.Idle
        resetQuiz()
        upload = null
        result = null
        formError = null
        themeMode = ThemeMode.SYSTEM
        brandColor = ""
    }

    /** Sign-in, refresh and account actions: one at a time, with the shared progress bar and error banner. */
    private fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (error == null) error = message
            } finally {
                busy = false
            }
        }
    }

    /** A screen-level write (save, submit, delete): shows [working] and reports failures as a notice. */
    private fun work(onError: (String) -> Unit = { notice = it }, block: suspend () -> Unit) {
        if (working) return
        working = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null) onError(message)
            } finally {
                working = false
            }
        }
    }

    fun dismissError() {
        error = null
    }

    // ---------------------------------------------------------------- sign-in

    fun chooseLoginRole(value: String) {
        if (value in Roles.CLASSROOM) loginRole = value
    }

    /** Shown once when the sign-in could not give the role that was asked for. */
    private fun explainRole(chosen: String, given: String) {
        if (chosen == given) return
        result = ResultMessage(
            "Signed in as a ${Roles.label(given).lowercase()}",
            if (given == Roles.LEARNER) "This account does not teach any classes, so you are signed in as a learner."
            else "This account is not enrolled as a learner, so you are signed in as a ${Roles.label(given).lowercase()}.",
            true,
        )
    }

    /** [asRole] opens the demo as any role (Developer and Admin too); used by tests and debug launches. */
    fun enterDemo(asRole: String? = null) {
        demo = DemoApi()
        isDemo = true
        pushEnabled = false
        resetStaff()
        resetSummary()
        resetAccountPages()
        role = asRole?.takeIf { it in Roles.ALL } ?: Roles.resolve(loginRole, DemoApi.PROFILE.roles)
        profile = DemoApi.PROFILE
        userProfile = DemoApi.PROFILE_DETAIL
        themeMode = ThemeMode.SYSTEM
        brandColor = ""
        error = null
        stack.clear()
        openPendingQuiz()
        if (isStaffView) dashboard = Dashboard.EMPTY else action { dashboard = demo.dashboard(role) }
    }

    fun choosePassword(value: Boolean) {
        usePassword = value
        sent = false
        otp = ""
        password = ""
        error = null
    }

    fun sendOtp() = action {
        repo.sendOtp(email.trim().lowercase())
        sent = true
    }

    fun changeEmail() {
        sent = false
        otp = ""
        error = null
    }

    private suspend fun afterSignIn(signedIn: Profile, chosen: String) {
        profile = signedIn
        role = repo.store.role
        loginRole = role
        explainRole(chosen, role)
        pushEnabled = repo.store.pushEnabled
        otp = ""
        password = ""
        stack.clear()
        if (mustChangePassword) return
        // Before the classes are fetched, so a scanned quiz opens even if that fetch fails.
        openPendingQuiz()
        resetStaff()
        // Developer and Admin have no class data of their own in the app: their pages load on their own.
        dashboard = if (isStaffView) Dashboard.EMPTY else repo.dashboard(role)
        loadProfileQuietly()
    }

    fun login() = action {
        val chosen = loginRole
        afterSignIn(repo.login(email.trim().lowercase(), otp, chosen), chosen)
    }

    // ── Admin sign-in with Google ──
    // The app sends the hash of a random verifier; the server answers at tiatms://auth with a
    // single-use code, which only this verifier can exchange for a session.
    private var googleVerifier: String? = null

    /** Remembers a new verifier and returns the page to open in the browser. */
    fun startGoogleSignIn(): String {
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val verifier = bytes.joinToString("") { "%02x".format(it) }
        val challenge = java.security.MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        googleVerifier = verifier
        error = null
        return repo.googleStartUrl(challenge)
    }

    /** The server's answer: `tiatms://auth?code=…` or `?error=…`. Ignored unless a sign-in is pending. */
    fun onAuthCallback(uri: android.net.Uri) {
        if (uri.scheme != "tiatms" || uri.host != "auth") return
        val verifier = googleVerifier ?: return
        googleVerifier = null
        val code = uri.getQueryParameter("code")
        val message = uri.getQueryParameter("error")
        if (message != null || code.isNullOrEmpty()) {
            error = message?.takeIf { it.isNotBlank() } ?: "Google sign-in did not complete. Please try again."
            return
        }
        // An admin sign-in opens the Admin view, whatever was chosen on the sign-in screen.
        action { afterSignIn(repo.googleLogin(code, verifier), Roles.ADMIN) }
    }

    fun loginWithPassword() = action {
        val chosen = loginRole
        val signedIn = repo.passwordLogin(email.trim().lowercase(), password, chosen)
        mustChangePassword = signedIn.forcePasswordChange
        afterSignIn(signedIn.profile, chosen)
    }

    fun logout() = action {
        if (!isDemo) runCatching { repo.logout() }
        pendingQuiz = null
        endSession()
    }

    // ---------------------------------------------------------------- dashboard, role, reminders

    private fun syncAlerts() {
        // Developer and Admin leave the learner/trainer reminders already scheduled on this device alone.
        if (isDemo || isStaffView) return
        if (repo.store.pushEnabled) ClassAlerts.sync(lms, dashboard) else ClassAlerts.cancelAll(lms)
    }

    fun refresh() = action {
        if (isStaffView) {
            dashboard = Dashboard.EMPTY
            if (adminClasses !is Load.Idle) loadAdminClasses()
            if (catalogue !is Load.Idle) loadCatalogue()
            calendarMonth?.let { loadCalendar(it, force = true) }
        } else {
            dashboard = api.dashboard(role)
        }
        if (payoutState !is Load.Idle) loadPayouts()
        if (certificatesState !is Load.Idle) loadCertificates()
        if (isDemo) return@action
        if (repo.store.pushEnabled) PushSyncWorker.enqueue(lms)
        syncAlerts()
        loadProfileQuietly()
        (stack.lastOrNull() as? Screen.ClassRoom)?.let { loadClass(it.id, quiet = true) }
    }

    /** "Switch role": shows that role's classes, calendar and tabs. Kept on this device until the next sign-in. */
    fun switchRole(value: String) = action {
        if (value !in availableRoles || value == role) return@action
        if (!isDemo) repo.store.role = value
        role = value
        closeAll()
        payoutState = Load.Idle
        resetAccountPages()
        resetStaff()
        dashboard = Dashboard.EMPTY
        if (!isStaffView) dashboard = api.dashboard(value)
        syncAlerts()
    }

    // ---------------------------------------------------------------- Admin and Developer

    private fun resetStaff() {
        adminClasses = Load.Idle
        catalogue = Load.Idle
        courseRuns = Load.Idle
        courseRunsId = null
        staffTrainers = Load.Idle
        calendarJob?.cancel()
        pendingMonth = null
        calendarClasses = emptyList()
        calendarMonth = null
        calendarLoading = false
        calendarProblem = null
    }

    /** Admin home: classes running today and in the next 30 days. */
    fun loadAdminClasses() {
        if (role != Roles.ADMIN) return
        if (adminClasses !is Load.Ready) adminClasses = Load.Loading
        viewModelScope.launch {
            try {
                val today = LocalDate.now(SGT)
                val list = api.staffClasses(today, today.plusDays(30))
                if (role == Roles.ADMIN && profile != null) adminClasses = Load.Ready(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null && adminClasses !is Load.Ready) adminClasses = Load.Failed(message)
            }
        }
    }

    /** Admin and Developer: the course catalogue. */
    fun loadCatalogue() {
        if (!isStaffView) return
        if (catalogue !is Load.Ready) catalogue = Load.Loading
        viewModelScope.launch {
            try {
                val list = api.staffCourses()
                if (isStaffView && profile != null) catalogue = Load.Ready(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null && catalogue !is Load.Ready) catalogue = Load.Failed(message)
            }
        }
    }

    /** Admin: every run of one course, with its status and trainers. */
    fun loadCourseRuns(courseId: String) {
        if (role != Roles.ADMIN) return
        if (courseRunsId != courseId) courseRuns = Load.Idle
        courseRunsId = courseId
        if (courseRuns !is Load.Ready) courseRuns = Load.Loading
        viewModelScope.launch {
            try {
                val list = api.staffRuns(courseId)
                if (role == Roles.ADMIN && profile != null && courseRunsId == courseId) courseRuns = Load.Ready(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null && courseRunsId == courseId && courseRuns !is Load.Ready) courseRuns = Load.Failed(message)
            }
        }
    }

    /** Admin: the trainer list for "WhatsApp a trainer". */
    fun loadTrainers() {
        if (role != Roles.ADMIN) return
        if (staffTrainers !is Load.Ready) staffTrainers = Load.Loading
        viewModelScope.launch {
            try {
                val list = api.staffTrainers()
                if (role == Roles.ADMIN && profile != null) staffTrainers = Load.Ready(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null && staffTrainers !is Load.Ready) staffTrainers = Load.Failed(message)
            }
        }
    }

    /** Admin calendar: one month at a time, plus a week either side so classes crossing the edges show. */
    fun loadCalendar(month: YearMonth, force: Boolean = false) {
        if (role != Roles.ADMIN) return
        if (!force && (calendarMonth == month || pendingMonth == month)) return
        pendingMonth = month
        calendarJob?.cancel()
        calendarLoading = true
        calendarJob = viewModelScope.launch {
            try {
                val list = api.staffClasses(month.atDay(1).minusDays(7), month.atEndOfMonth().plusDays(7))
                if (role == Roles.ADMIN && profile != null) {
                    calendarClasses = list
                    calendarMonth = month
                    calendarProblem = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null) calendarProblem = message
            } finally {
                if (pendingMonth == month) {
                    calendarLoading = false
                    pendingMonth = null
                }
            }
        }
    }

    // ---------------------------------------------------------------- trainer payouts

    fun loadPayouts() {
        if (role != Roles.TRAINER) return
        if (payoutState !is Load.Ready) payoutState = Load.Loading
        viewModelScope.launch {
            try {
                val list = api.payouts()
                if (role == Roles.TRAINER && profile != null) payoutState = Load.Ready(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null) payoutState = Load.Failed(message)
            }
        }
    }

    fun setPush(enabled: Boolean) = action {
        if (isDemo) {
            pushEnabled = enabled
            if (enabled) DemoNotifications.show(lms) else lms.getSystemService(NotificationManager::class.java).cancel(DemoNotifications.ID)
            return@action
        }
        repo.registerPush(enabled)
        pushEnabled = enabled
        if (enabled) PushSyncWorker.enqueue(lms)
        syncAlerts()
    }

    // ---------------------------------------------------------------- profile, theme, password

    private fun applyProfile(value: UserProfile) {
        userProfile = value
        themeMode = value.theme
        if (!isDemo) brandColor = value.brandColor
        profile = profile?.copy(name = value.fullName.ifBlank { profile?.name.orEmpty() })
    }

    /** Best effort after sign-in and on refresh: a failure never blocks the classes. */
    private suspend fun loadProfileQuietly() {
        try {
            applyProfile(api.profile())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ApiException && e.sessionEnded) failure(e)
        }
    }

    /** The Account page shows the cached profile at once and brings it up to date in the background. */
    fun loadProfile() {
        viewModelScope.launch { loadProfileQuietly() }
    }

    fun saveProfile(patch: ProfilePatch) {
        formError = null
        work(onError = { formError = it }) {
            applyProfile(api.updateProfile(patch))
            notice = "Profile saved"
        }
    }

    /** Applies at once and is cached for the next launch; the profile is then updated in the background. */
    fun chooseTheme(mode: ThemeMode) {
        if (mode == themeMode) return
        themeMode = mode
        userProfile = userProfile?.copy(theme = mode)
        if (!isDemo) repo.store.theme = mode.key
        viewModelScope.launch {
            try {
                applyProfile(api.updateProfile(ProfilePatch(theme = mode)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure(e)
                if (profile != null) notice = "Theme changed on this device. It could not be saved to your profile yet."
            }
        }
    }

    fun changePassword(current: String, new: String) {
        formError = null
        work(onError = { formError = it }) {
            api.changePassword(current, new)
            val forced = mustChangePassword
            mustChangePassword = false
            notice = "Password changed"
            if (stack.lastOrNull() == Screen.ChangePassword) stack.removeAt(stack.lastIndex)
            if (forced) {
                refresh()
                openPendingQuiz()
            }
        }
    }

    // ---------------------------------------------------------------- navigation

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        // Inside a quiz, Back first steps to the previous question.
        if (stack.last() is Screen.Quiz && quizStarted && quizResult == null && quizIndex > 0) {
            quizIndex--
            formError = null
            return true
        }
        val top = stack.removeAt(stack.lastIndex)
        formError = null
        if (top is Screen.Quiz) resetQuiz()
        if (top is Screen.Pdf) {
            pdfJob?.cancel()
            pdfState = Load.Idle
            downloadJob?.cancel()
            downloading = false
        }
        return true
    }

    fun closeAll() {
        while (stack.isNotEmpty()) back()
    }

    // ---------------------------------------------------------------- class detail

    fun openClass(id: String) {
        if (id.isBlank()) return
        resetSummary()
        openSections.clear()
        stack.add(Screen.ClassRoom(id))
        loadClass(id)
    }

    fun loadClass(id: String, quiet: Boolean = false) {
        if (!quiet) classState = Load.Loading
        viewModelScope.launch {
            try {
                val detail = api.classDetail(id, role)
                if (stack.any { it is Screen.ClassRoom && it.id == id }) classState = Load.Ready(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile == null || quiet) return@launch
                // When the server cannot be reached (or does not offer class details yet), show
                // what the dashboard already has so the materials stay usable.
                val course = dashboard.courses.firstOrNull { it.id == id }
                val reachable = e is ApiException && e.fromApi && e.status < 500
                classState = if (course != null && !reachable) {
                    Load.Ready(ClassDetail.fromCourse(course, dashboard.sessions.filter { it.courseId == id }, message))
                } else {
                    Load.Failed(message)
                }
            }
        }
    }

    private fun currentClass(): ClassDetail? = (classState as? Load.Ready)?.value

    fun toggleSection(title: String) {
        if (!openSections.remove(title)) openSections.add(title)
    }

    // ---------------------------------------------------------------- attendance

    fun checkIn(code: String) {
        if (code.isBlank()) return
        // The same scanner reads a quiz QR code: open the quiz instead of treating it as a check-in.
        parseQuizLink(code, quizHost)?.let {
            openQuiz(it)
            return
        }
        work(onError = { result = ResultMessage("Check-in not recorded", it, false) }) {
            val answer = api.checkIn(code)
            val where = listOf(answer.course, answer.session).filter { it.isNotBlank() }.joinToString(" · ")
            result = ResultMessage(if (answer.already) "Already checked in" else "Checked in", listOf(answer.message, where).filter { it.isNotBlank() }.joinToString("\n"), true)
            currentClass()?.let { loadClass(it.id, quiet = true) }
        }
    }

    fun openAttendance(sessionId: String, label: String) {
        stack.add(Screen.Attendance(sessionId, label))
        loadAttendance(sessionId)
    }

    fun loadAttendance(sessionId: String) {
        attendanceState = Load.Loading
        attendanceDraft.clear()
        viewModelScope.launch {
            try {
                val sheet = api.attendance(sessionId)
                attendanceDraft.putAll(sheet.learners.associateBy { it.enrolmentId })
                attendanceState = Load.Ready(sheet)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attendanceState = Load.Failed(failure(e))
            }
        }
    }

    fun mark(learner: AttendanceLearner) {
        attendanceDraft[learner.enrolmentId] = learner
    }

    fun markAllPresent() {
        attendanceDraft.values.toList().forEach { attendanceDraft[it.enrolmentId] = it.copy(isPresent = true, reason = "") }
    }

    fun saveAttendance(sessionId: String) = work {
        val before = (attendanceState as? Load.Ready)?.value
        val marked = attendanceDraft.values.filter { it.isPresent != null }
        val saved = api.markAttendance(sessionId, marked)
        attendanceDraft.clear()
        attendanceDraft.putAll(saved.learners.associateBy { it.enrolmentId })
        attendanceState = Load.Ready(saved.copy(checkinUrl = saved.checkinUrl ?: before?.checkinUrl))
        notice = "Attendance saved for ${marked.size} learner${if (marked.size == 1) "" else "s"}"
        currentClass()?.let { loadClass(it.id, quiet = true) }
    }

    // ---------------------------------------------------------------- assessments

    fun clearUpload() {
        if (upload?.error != null) upload = null
    }

    /** Uploads the picked file to the academy's Drive, then records it against the assessment. */
    fun submitFile(detail: ClassDetail, assessment: Assessment, uri: Uri) {
        if (uploadJob?.isActive == true) return
        val resolver = lms.contentResolver
        var name = "Assessment file"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { name = it }
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        }
        if (size > MAX_UPLOAD_BYTES) {
            upload = UploadState(assessment.type, name, 0f, "This file is larger than 100 MB. Choose a smaller file.")
            return
        }
        if (size == 0L) {
            upload = UploadState(assessment.type, name, 0f, "This file is empty. Choose another file.")
            return
        }
        upload = UploadState(assessment.type, name, 0f)
        uploadJob = viewModelScope.launch {
            try {
                val source = UploadSource(name, resolver.getType(uri) ?: "application/octet-stream", size) {
                    resolver.openInputStream(uri) ?: throw java.io.IOException("The file could not be read. Choose it again.")
                }
                val student = userProfile?.fullName?.takeIf { it.isNotBlank() } ?: profile?.name.orEmpty()
                val file = api.uploadAssessment(student, detail.runCode, source) { progress ->
                    upload = upload?.copy(progress = progress)
                }
                upload = upload?.copy(progress = 1f)
                api.submitAssessment(detail.id, assessment.type, name, file)
                upload = null
                notice = "\"$name\" submitted for ${assessment.label}"
                loadClass(detail.id, quiet = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null) upload = UploadState(assessment.type, name, 0f, message)
            }
        }
    }

    fun deleteSubmission(detail: ClassDetail, submissionId: String) = work {
        api.deleteSubmission(submissionId)
        notice = "File removed"
        loadClass(detail.id, quiet = true)
    }

    fun grade(detail: ClassDetail, learner: RosterEntry, competent: Boolean) = work {
        val status = api.grade(listOf(learner.enrolmentId), competent)
        classState = Load.Ready(detail.copy(roster = detail.roster.map { if (it.enrolmentId == learner.enrolmentId) it.copy(status = status) else it }))
        notice = "${learner.name}: $status"
        loadClass(detail.id, quiet = true)
    }

    // ---------------------------------------------------------------- trainer: Publish switch

    /** The website's Publish switch for one assessment. Shown at once; put back if the server refuses. */
    fun publishAssessment(detail: ClassDetail, assessment: Assessment, value: Boolean) {
        if (assessment.type in publishing) return
        publishing.add(assessment.type)
        publishProblems.remove(assessment.type)
        fun show(published: Boolean) {
            currentClass()?.takeIf { it.id == detail.id }?.let { now ->
                classState = Load.Ready(now.copy(assessments = now.assessments.map { if (it.type == assessment.type) it.copy(published = published) else it }))
            }
        }
        show(value)
        viewModelScope.launch {
            try {
                api.publishAssessment(detail.id, assessment.type, value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                show(!value)
                if (profile != null) publishProblems[assessment.type] = message
            } finally {
                publishing.remove(assessment.type)
            }
        }
    }

    // ---------------------------------------------------------------- Assessment Summary Record

    private fun resetSummary() {
        summaryState = Load.Idle
        summaryRunId = null
        summarySigning.clear()
        summaryProblem = null
        signerSheet = null
        signerState = Load.Idle
        signerProblem = null
        pendingSign = emptyList()
    }

    /** The learner's own record, or every learner's for the trainer. */
    fun loadSummary(runId: String, quiet: Boolean = false) {
        if (summaryRunId != runId) {
            summaryRunId = runId
            summaryState = Load.Idle
            summaryProblem = null
        }
        if (!quiet && summaryState !is Load.Ready) summaryState = Load.Loading
        val learnerView = role != Roles.TRAINER
        viewModelScope.launch {
            try {
                val reply = api.summaryRecord(runId, learnerView)
                if (summaryRunId == runId) summaryState = Load.Ready(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile == null || summaryRunId != runId) return@launch
                if (summaryState is Load.Ready) summaryProblem = message else summaryState = Load.Failed(message)
            }
        }
    }

    /** Learner: sign my record. With no saved signature the setup sheet opens first, then the record is signed. */
    /** Learner: sign (or sign again). The signature is always drawn on the phone at the moment of signing. */
    fun signMyRecord(@Suppress("UNUSED_PARAMETER") runId: String) {
        if (summarySigning.isEmpty()) openSigner(SignerKind.LEARNER, listOf(SELF), signing = true)
    }

    /** Trainer: sign as assessor for the chosen learners, with one signature drawn now for all of them. */
    fun signAsAssessor(@Suppress("UNUSED_PARAMETER") runId: String, learnerIds: List<String>) {
        if (learnerIds.isNotEmpty() && summarySigning.isEmpty()) openSigner(SignerKind.ASSESSOR, learnerIds, signing = true)
    }

    private fun sign(runId: String, ids: List<String>) {
        if (ids.isEmpty() || summarySigning.isNotEmpty()) return
        summaryProblem = null
        viewModelScope.launch {
            try {
                for ((index, id) in ids.withIndex()) {
                    summarySigning.add(id)
                    try {
                        val note = api.signSummaryRecord(runId, id.takeIf { it != SELF })
                        if (note != null) summaryProblem = "Signed. The PDF will follow: $note"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (e is ApiException && e.sessionEnded) {
                            failure(e)
                            return@launch
                        }
                        if (needsSignatureSetup(e)) {
                            // No saved signature yet: set it up, then carry on from this learner.
                            openSigner(if (id == SELF) SignerKind.LEARNER else SignerKind.ASSESSOR, ids.drop(index), signing = true)
                        } else {
                            summaryProblem = failure(e)
                        }
                        break
                    } finally {
                        summarySigning.remove(id)
                    }
                }
            } finally {
                summarySigning.clear()
                if (profile != null && summaryRunId == runId) loadSummary(runId, quiet = true)
            }
        }
    }

    /** Opens the name, NRIC and signature sheet. [then] is who to sign for once it is saved. */
    fun openSigner(
        kind: SignerKind,
        then: List<String> = emptyList(),
        signing: Boolean = false,
        title: String = "Sign Summary Record",
        what: String = "the Assessment Summary Record",
        grading: List<RosterStudent>? = null,
    ) {
        pendingSign = then
        signerSigning = signing
        signerTitle = title
        signerWhat = what
        signerGrading = grading
        signerProblem = null
        signerSheet = kind
        signerState = Load.Loading
        viewModelScope.launch {
            signerState = try {
                Load.Ready(api.signer(kind))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiException && e.sessionEnded) failure(e)
                // A first-time signer has nothing saved: start from an empty form.
                Load.Ready(SignerRecord(userProfile?.fullName ?: profile?.name.orEmpty(), "", false))
            }
        }
    }

    fun closeSigner() {
        if (signerSaving) return
        signerSheet = null
        pendingSign = emptyList()
        signerGrading = null
    }

    /** Saves the signer details; [signaturePng] is null to keep the saved signature. Then signs whatever was waiting. */
    fun saveSigner(kind: SignerKind, name: String, nric: String, signaturePng: String?) {
        if (signerSaving) return
        signerSaving = true
        signerProblem = null
        viewModelScope.launch {
            try {
                api.saveSigner(kind, name, nric, signaturePng)
                val then = pendingSign
                pendingSign = emptyList()
                signerSheet = null
                // "Sign Assessments" from the grading roster: stamp the submitted files now.
                signerGrading?.let { list ->
                    signerGrading = null
                    signSubmissions(list, true)
                    return@launch
                }
                summaryRunId?.let { runId -> if (then.isNotEmpty()) sign(runId, then) else loadSummary(runId, quiet = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null) signerProblem = message
            } finally {
                signerSaving = false
            }
        }
    }

    // ---------------------------------------------------------------- trainer announcements

    /** A photo or file picked for an announcement; read only when it is posted. */
    class AnnouncementFile(val name: String, val mime: String, val size: Long, val photo: Boolean, val open: () -> java.io.InputStream)

    private fun pickedName(uri: Uri): Pair<String, Long> {
        var name = "Attachment"
        var size = -1L
        runCatching {
            lms.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.takeIf { it.isNotBlank() }?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        return name to size
    }

    /** A photo is sent as JPEG so every device can open it. */
    fun attachPhoto(uri: Uri) {
        announcementProblem = null
        viewModelScope.launch {
            try {
                val jpeg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val bitmap = lms.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                        ?: throw java.io.IOException()
                    val out = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    bitmap.recycle()
                    out.toByteArray()
                }
                if (jpeg.size > MAX_ANNOUNCEMENT_BYTES) {
                    announcementProblem = "That photo is larger than 50 MB."
                    return@launch
                }
                val stamp = LocalDate.now(SGT).toString()
                announcementAttachment = AnnouncementFile("Photo $stamp.jpg", "image/jpeg", jpeg.size.toLong(), true) { jpeg.inputStream() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                announcementProblem = "That photo could not be read. Please choose another."
            }
        }
    }

    fun attachFile(uri: Uri) {
        announcementProblem = null
        val (name, size) = pickedName(uri)
        if (size > MAX_ANNOUNCEMENT_BYTES) {
            announcementProblem = "That file is larger than 50 MB. Upload a smaller file, or paste a link to it in the message."
            return
        }
        val mime = lms.contentResolver.getType(uri) ?: "application/octet-stream"
        announcementAttachment = AnnouncementFile(name, mime, size, false) {
            lms.contentResolver.openInputStream(uri) ?: throw java.io.IOException("That file could not be read.")
        }
    }

    fun clearAttachment() {
        announcementAttachment = null
    }

    /** Posts the trainer's announcement, then reloads the class. Returns through [announcementPosting]. */
    fun postAnnouncement(detail: ClassDetail, message: String, onPosted: () -> Unit) {
        if (announcementPosting || (message.isBlank() && announcementAttachment == null)) return
        announcementPosting = true
        announcementProblem = null
        val file = announcementAttachment
        viewModelScope.launch {
            try {
                val postedBy = userProfile?.fullName?.takeIf { it.isNotBlank() } ?: profile?.name?.takeIf { it.isNotBlank() } ?: "Trainer"
                api.postAnnouncement(detail.id, postedBy, message, file?.let { UploadSource(it.name, it.mime, it.size, it.open) })
                announcementAttachment = null
                onPosted()
                loadClass(detail.id, quiet = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val problem = failure(e)
                if (profile != null) announcementProblem = problem
            } finally {
                announcementPosting = false
            }
        }
    }

    fun deleteAnnouncement(detail: ClassDetail, id: String) = work {
        api.deleteAnnouncement(id)
        notice = "Post deleted"
        loadClass(detail.id, quiet = true)
    }

    // ---------------------------------------------------------------- Assessment Grading

    fun openGrading(classId: String) {
        stack.add(Screen.Grading(classId))
        rosterState = Load.Idle
        gradingProblem = null
        loadRoster(classId)
    }

    private var rosterClass: String? = null

    fun loadRoster(classId: String) {
        rosterClass = classId
        if (rosterState !is Load.Ready) rosterState = Load.Loading
        viewModelScope.launch {
            try {
                val roster = api.roster(classId)
                if (rosterClass == classId) rosterState = Load.Ready(roster)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile == null) return@launch
                if (rosterState is Load.Ready) gradingProblem = message else rosterState = Load.Failed(message)
            }
        }
    }

    private fun updateStudent(student: RosterStudent, change: (RosterStudent) -> RosterStudent) {
        val now = (rosterState as? Load.Ready)?.value ?: return
        rosterState = Load.Ready(now.copy(students = now.students.map { if (it.enrolmentId == student.enrolmentId) change(it) else it }))
    }

    private fun gradingAction(student: RosterStudent, block: suspend () -> Unit, undo: (() -> Unit)? = null) {
        if (student.enrolmentId in gradingBusy) return
        gradingBusy.add(student.enrolmentId)
        gradingProblem = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                undo?.invoke()
                val message = failure(e)
                if (profile != null) gradingProblem = message
            } finally {
                gradingBusy.remove(student.enrolmentId)
            }
        }
    }

    fun gradeStudent(student: RosterStudent, competent: Boolean) = gradingAction(student, {
        api.gradeStudent(student, competent)
        rosterClass?.let { loadRoster(it) }
    })

    /** TRAQOM is ticked by the trainer (SSG does not report completion): shown at once, put back on failure. */
    fun setTraqom(student: RosterStudent, done: Boolean) {
        updateStudent(student) { it.copy(traqomCompleted = done) }
        gradingAction(student, { api.setTraqom(student, done) }) { updateStudent(student) { it.copy(traqomCompleted = !done) } }
    }

    /** Signing opens the signature sheet first ("Sign Assessments"); the files are stamped once it is drawn. */
    fun signSubmissionsTapped(students: List<RosterStudent>) {
        if (students.isEmpty() || gradingBusy.isNotEmpty()) return
        openSigner(
            SignerKind.ASSESSOR, signing = true, title = "Sign Assessments", what = "every file the learner submitted", grading = students,
        )
    }

    /** Stamps ([signed]) or removes the assessor signature, one learner at a time; stops at the first failure. */
    fun signSubmissions(students: List<RosterStudent>, signed: Boolean) {
        val classId = rosterClass ?: return
        gradingProblem = null
        viewModelScope.launch {
            for (s in students) {
                gradingBusy.add(s.enrolmentId)
                try {
                    api.signAssessments(classId, s.userIds, signed)
                    updateStudent(s) { it.copy(assessorSigned = signed) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val message = failure(e)
                    if (profile != null) gradingProblem = message
                    break
                } finally {
                    gradingBusy.remove(s.enrolmentId)
                }
            }
        }
    }

    // ---------------------------------------------------------------- certificates, support, credentials

    private fun resetAccountPages() {
        certificatesState = Load.Idle
        supportState = Load.Idle
        supportNotice = null
        supportProblem = null
        credentialsState = Load.Idle
        credentialsProblem = null
        rosterState = Load.Idle
        announcementAttachment = null
        announcementProblem = null
    }

    fun loadCertificates() {
        if (certificatesState !is Load.Ready) certificatesState = Load.Loading
        viewModelScope.launch {
            try {
                val list = api.certificates()
                if (profile != null) certificatesState = Load.Ready(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null && certificatesState !is Load.Ready) certificatesState = Load.Failed(message)
            }
        }
    }

    fun openCertificates() {
        stack.add(Screen.Certificates)
        loadCertificates()
    }

    /** The academy's contacts; the sample contacts when they cannot be loaded, as on iOS. */
    fun loadSupport() {
        if (supportState is Load.Ready) return
        supportState = Load.Loading
        viewModelScope.launch {
            supportState = try {
                Load.Ready(api.support())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiException && e.sessionEnded) failure(e)
                Load.Ready(SupportInfo.SAMPLE)
            }
        }
    }

    fun sendSupport(subject: String, message: String, onSent: () -> Unit) {
        if (supportSending || subject.isBlank() || message.isBlank()) return
        supportNotice = null
        supportProblem = null
        if (isDemo) {
            supportNotice = "Sent (sample). Sign in with your account to send feedback."
            return
        }
        supportSending = true
        viewModelScope.launch {
            try {
                api.sendSupport(subject, message)
                onSent()
                supportNotice = "Thank you. Your feedback has been sent."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val problem = failure(e)
                if (profile != null) supportProblem = problem
            } finally {
                supportSending = false
            }
        }
    }

    fun loadCredentials() {
        if (role != Roles.TRAINER) return
        viewModelScope.launch {
            try {
                credentialsState = Load.Ready(api.trainerCredentials())
                credentialsProblem = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null && credentialsState !is Load.Ready) credentialsState = Load.Failed(message)
            }
        }
    }

    /** Saves one change at once (shown before the server answers); a failure reloads the saved profile. */
    private fun saveCredentials(shown: TrainerCredentials, changes: JSONObject) {
        credentialsState = Load.Ready(shown)
        viewModelScope.launch {
            try {
                credentialsState = Load.Ready(api.saveTrainerCredentials(changes))
                credentialsProblem = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profile != null) {
                    credentialsProblem = message
                    loadCredentials()
                }
            }
        }
    }

    fun setCredential(field: String, value: Boolean) {
        val now = (credentialsState as? Load.Ready)?.value ?: return
        val next = when (field) {
            "aclp" -> now.copy(aclp = value)
            "dddpl" -> now.copy(dddpl = value)
            "taepp" -> now.copy(taepp = value)
            else -> return
        }
        saveCredentials(next, JSONObject().put(field, value))
    }

    /** Adds a tag to "areasOfExpertise" or "skillsTags"; returns false when it was blank or already there. */
    fun addCredentialTag(field: String, raw: String): Boolean {
        val now = (credentialsState as? Load.Ready)?.value ?: return false
        val list = if (field == "areasOfExpertise") now.areasOfExpertise else now.skillsTags
        val next = addTag(list, raw) ?: return false
        saveCredentials(withTags(now, field, next), JSONObject().put(field, org.json.JSONArray(next)))
        return true
    }

    fun removeCredentialTag(field: String, tag: String) {
        val now = (credentialsState as? Load.Ready)?.value ?: return
        val next = (if (field == "areasOfExpertise") now.areasOfExpertise else now.skillsTags).filter { it != tag }
        saveCredentials(withTags(now, field, next), JSONObject().put(field, org.json.JSONArray(next)))
    }

    private fun withTags(c: TrainerCredentials, field: String, tags: List<String>) =
        if (field == "areasOfExpertise") c.copy(areasOfExpertise = tags) else c.copy(skillsTags = tags)

    // ---------------------------------------------------------------- course feedback

    fun openFeedback(detail: ClassDetail) {
        stack.add(Screen.Feedback(detail.id, detail.title))
        loadFeedback(detail.id)
    }

    fun loadFeedback(runId: String) {
        feedbackState = Load.Loading
        feedbackAnswers.clear()
        formError = null
        viewModelScope.launch {
            try {
                val form = api.feedbackForm(runId)
                // The learner's own name is the one answer worth pre-filling; they can still change it.
                form.fields.firstOrNull { it.id == "learner_name" && !it.locked }?.let { feedbackAnswers[it.id] = profile?.name.orEmpty() }
                feedbackState = Load.Ready(form)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                feedbackState = Load.Failed(failure(e))
            }
        }
    }

    fun submitFeedback(runId: String) {
        val form = (feedbackState as? Load.Ready)?.value ?: return
        form.firstMissing(feedbackAnswers)?.let {
            formError = if (it.type == "rating1to5") "Please rate: ${it.label}" else "Please answer: ${it.label}"
            return
        }
        formError = null
        work(onError = { formError = it }) {
            api.submitFeedback(runId, form, feedbackAnswers.toMap())
            feedbackState = Load.Ready(form.copy(submitted = true))
            // Show "Feedback sent" on the class at once; the reload then confirms it.
            currentClass()?.let {
                classState = Load.Ready(it.copy(feedback = it.feedback?.copy(submitted = true)))
                loadClass(it.id, quiet = true)
            }
        }
    }

    // ---------------------------------------------------------------- class quiz

    /** A link the app was opened with. Only quiz links are registered, so anything else is reported. */
    fun openLink(url: String) {
        val link = parseQuizLink(url, quizHost)
        if (link == null) notice = "This quiz link is not valid. Scan the QR code again." else openQuiz(link)
    }

    /** Opens the quiz now, or keeps it until the user has signed in (and set a new password if required). */
    fun openQuiz(link: QuizLink) {
        if (profile == null || mustChangePassword) {
            pendingQuiz = link
            return
        }
        pendingQuiz = null
        stack.removeAll { it is Screen.Quiz }
        stack.add(Screen.Quiz(link.courseId, link.quizId))
        loadQuiz(link.courseId, link.quizId)
    }

    private fun openPendingQuiz() {
        pendingQuiz?.let { openQuiz(it) }
    }

    private fun resetQuiz() {
        quizState = Load.Idle
        quizAnswers.clear()
        quizIndex = 0
        quizStarted = false
        quizResult = null
    }

    fun loadQuiz(courseId: String, quizId: String) {
        resetQuiz()
        quizState = Load.Loading
        formError = null
        viewModelScope.launch {
            try {
                val loaded = api.quiz(courseId, quizId)
                if (stack.none { it is Screen.Quiz && it.courseId == courseId && it.quizId == quizId }) return@launch
                if (loaded.quiz.questions.isEmpty()) {
                    quizState = Load.Failed("This quiz has no questions yet. Ask your trainer to check it.")
                    return@launch
                }
                // A learner with an earlier attempt sees that score first and chooses to retake.
                quizStarted = loaded.previous == null
                quizState = Load.Ready(loaded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val ended = e is ApiException && e.sessionEnded
                val message = failure(e)
                // A session that has ended sends the user to sign-in; the quiz reopens afterwards.
                if (ended) pendingQuiz = QuizLink(courseId, quizId) else if (profile != null) quizState = Load.Failed(message)
            }
        }
    }

    /** Starts the questions from the first one, discarding any picks (used by Retake). */
    fun startQuiz() {
        quizAnswers.clear()
        quizIndex = 0
        quizResult = null
        formError = null
        quizStarted = true
    }

    fun pickQuizOption(questionId: String, option: Int) {
        if (working || quizResult != null) return
        quizAnswers[questionId] = option
        formError = null
    }

    fun nextQuizQuestion() {
        val quiz = (quizState as? Load.Ready)?.value?.quiz ?: return
        val question = quiz.questions.getOrNull(quizIndex) ?: return
        if (question.id !in quizAnswers) {
            formError = "Choose an answer to continue."
            return
        }
        if (quizIndex < quiz.questions.lastIndex) quizIndex++
    }

    fun submitQuiz(screen: Screen.Quiz) {
        val loaded = (quizState as? Load.Ready)?.value ?: return
        val missing = loaded.quiz.questions.indexOfFirst { it.id !in quizAnswers }
        if (missing >= 0) {
            quizIndex = missing
            formError = "Choose an answer to continue."
            return
        }
        formError = null
        work(onError = { formError = it }) {
            val marked = api.submitQuiz(screen.courseId, screen.quizId, quizAnswers.toMap())
            quizResult = marked
            quizState = Load.Ready(loaded.copy(previous = QuizAttempt(marked.score, marked.total, marked.completedAt)))
        }
    }

    // ---------------------------------------------------------------- materials

    /**
     * Opens a material in the in-app PDF viewer when it is a PDF. Anything else (a folder, a web
     * page, a file that is too large, a failed download) opens the original link in the browser.
     */
    fun openMaterial(title: String, url: String) {
        if (!isSafeLink(url)) {
            notice = "This link cannot be opened."
            return
        }
        val candidates = if (isDemo) listOf(url) else pdfCandidates(url)
        if (candidates.isEmpty()) {
            openLink(lms, url)
            return
        }
        val screen = Screen.Pdf(title, url)
        stack.add(screen)
        pdfState = Load.Loading
        pdfProgress = null
        pdfJob?.cancel()
        pdfJob = viewModelScope.launch {
            try {
                val file = if (isDemo) DemoPdf.create(lms, title) else lms.pdfs.fetch(candidates) { pdfProgress = it }
                pdfState = Load.Ready(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pdfFallback(screen)
            }
        }
    }

    /**
     * Download from the reader: fetches the file itself (Drive and Docs links in their download
     * form) and hands it to the screen, which opens the system share sheet to save or send it.
     */
    fun downloadMaterial(screen: Screen.Pdf) {
        if (downloading) return
        downloading = true
        downloadProblem = null
        downloadJob = viewModelScope.launch {
            try {
                val file = if (isDemo) lms.downloads.adopt(screen.name, DemoPdf.create(lms, screen.name)) else lms.downloads.fetch(screen.name, screen.url)
                if (stack.lastOrNull() == screen) downloaded = DownloadedFile(file, screen.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (stack.lastOrNull() == screen) downloadProblem = DownloadProblem(describe(e), screen.url)
            } finally {
                downloading = false
            }
        }
    }

    /** The viewer could not show the file: leave it and hand the original link to the browser. */
    fun pdfFallback(screen: Screen.Pdf) {
        if (stack.lastOrNull() != screen) return
        stack.removeAt(stack.lastIndex)
        pdfState = Load.Idle
        notice = "This material opens in your browser."
        openLink(lms, screen.url)
    }
}
