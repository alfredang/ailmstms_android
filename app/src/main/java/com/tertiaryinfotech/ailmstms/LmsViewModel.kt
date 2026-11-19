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
import com.tertiaryinfotech.ailmstms.data.Profile
import com.tertiaryinfotech.ailmstms.data.ProfilePatch
import com.tertiaryinfotech.ailmstms.data.RosterEntry
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

/** A screen opened on top of the four tabs. */
sealed interface Screen {
    val title: String

    data class ClassRoom(val id: String) : Screen {
        override val title = "Class"
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

    data object EditProfile : Screen {
        override val title = "Edit profile"
    }

    data object ChangePassword : Screen {
        override val title = "Change password"
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

class LmsViewModel(app: Application) : AndroidViewModel(app) {
    private val lms = app as LmsApplication
    val repo = lms.repo
    private var demo = DemoApi()
    private val api: LmsApi get() = if (isDemo) demo else repo

    var isDemo by mutableStateOf(false); private set
    var profile by mutableStateOf<Profile?>(null); private set
    var userProfile by mutableStateOf<UserProfile?>(null); private set
    var dashboard by mutableStateOf(Dashboard.EMPTY); private set
    var role by mutableStateOf(repo.store.role); private set
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
    var attendanceState by mutableStateOf<Load<AttendanceSheet>>(Load.Idle); private set
    val attendanceDraft = mutableStateMapOf<String, AttendanceLearner>()
    var working by mutableStateOf(false); private set
    var upload by mutableStateOf<UploadState?>(null); private set
    var feedbackState by mutableStateOf<Load<FeedbackForm>>(Load.Idle); private set
    val feedbackAnswers = mutableStateMapOf<String, String>()
    var formError by mutableStateOf<String?>(null)
    var pdfState by mutableStateOf<Load<File>>(Load.Idle); private set
    var pdfProgress by mutableStateOf<Float?>(null); private set
    var profileState by mutableStateOf<Load<UserProfile>>(Load.Idle); private set
    private var pdfJob: Job? = null
    private var uploadJob: Job? = null

    init {
        if (repo.store.token.isNotEmpty()) {
            profile = runCatching { Profile.parse(JSONObject(repo.store.profile)) }.getOrNull()
            userProfile = runCatching { UserProfile.parse(JSONObject(repo.store.userProfile)) }.getOrNull()
            if (profile == null) repo.store.clear() else refresh()
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
        ClassAlerts.cancelAll(lms)
        lms.getSystemService(NotificationManager::class.java).cancelAll()
        pdfJob?.cancel()
        uploadJob?.cancel()
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
        profileState = Load.Idle
        pdfState = Load.Idle
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

    fun enterDemo() {
        demo = DemoApi()
        isDemo = true
        pushEnabled = false
        role = "learner"
        profile = DemoApi.PROFILE
        userProfile = DemoApi.PROFILE_DETAIL
        themeMode = ThemeMode.SYSTEM
        brandColor = ""
        error = null
        stack.clear()
        action { dashboard = demo.dashboard(role) }
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

    private suspend fun afterSignIn(signedIn: Profile) {
        profile = signedIn
        role = repo.store.role
        pushEnabled = repo.store.pushEnabled
        otp = ""
        password = ""
        stack.clear()
        if (mustChangePassword) return
        dashboard = repo.dashboard(role)
        loadProfileQuietly()
    }

    fun login() = action { afterSignIn(repo.login(email.trim().lowercase(), otp)) }

    fun loginWithPassword() = action {
        val signedIn = repo.passwordLogin(email.trim().lowercase(), password)
        mustChangePassword = signedIn.forcePasswordChange
        afterSignIn(signedIn.profile)
    }

    fun logout() = action {
        if (!isDemo) runCatching { repo.logout() }
        endSession()
    }

    // ---------------------------------------------------------------- dashboard, role, reminders

    private fun syncAlerts() {
        if (isDemo) return
        if (repo.store.pushEnabled) ClassAlerts.sync(lms, dashboard) else ClassAlerts.cancelAll(lms)
    }

    fun refresh() = action {
        dashboard = api.dashboard(role)
        if (isDemo) return@action
        if (repo.store.pushEnabled) PushSyncWorker.enqueue(lms)
        syncAlerts()
        loadProfileQuietly()
        (stack.lastOrNull() as? Screen.ClassRoom)?.let { loadClass(it.id, quiet = true) }
    }

    fun switchRole(value: String) = action {
        if (profile?.roles?.contains(value) != true || value == role) return@action
        if (!isDemo) repo.store.role = value
        role = value
        stack.clear()
        dashboard = Dashboard.EMPTY
        dashboard = api.dashboard(value)
        syncAlerts()
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

    fun openEditProfile() {
        stack.add(Screen.EditProfile)
        loadProfile()
    }

    fun loadProfile() {
        profileState = userProfile?.let { Load.Ready(it) } ?: Load.Loading
        viewModelScope.launch {
            try {
                val value = api.profile()
                applyProfile(value)
                profileState = Load.Ready(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = failure(e)
                if (profileState !is Load.Ready) profileState = Load.Failed(message)
            }
        }
    }

    fun saveProfile(patch: ProfilePatch) {
        formError = null
        work(onError = { formError = it }) {
            val value = api.updateProfile(patch)
            applyProfile(value)
            profileState = Load.Ready(value)
            notice = "Profile saved"
            if (stack.lastOrNull() == Screen.EditProfile) stack.removeAt(stack.lastIndex)
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
            if (forced) refresh()
        }
    }

    // ---------------------------------------------------------------- navigation

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        val top = stack.removeAt(stack.lastIndex)
        formError = null
        if (top is Screen.Pdf) {
            pdfJob?.cancel()
            pdfState = Load.Idle
        }
        return true
    }

    fun closeAll() {
        while (stack.isNotEmpty()) back()
    }

    // ---------------------------------------------------------------- class detail

    fun openClass(id: String) {
        if (id.isBlank()) return
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

    // ---------------------------------------------------------------- attendance

    fun checkIn(code: String) {
        if (code.isBlank()) return
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

    /** The viewer could not show the file: leave it and hand the original link to the browser. */
    fun pdfFallback(screen: Screen.Pdf) {
        if (stack.lastOrNull() != screen) return
        stack.removeAt(stack.lastIndex)
        pdfState = Load.Idle
        notice = "This material opens in your browser."
        openLink(lms, screen.url)
    }
}
