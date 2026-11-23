package com.tertiaryinfotech.ailmstms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.tertiaryinfotech.ailmstms.BuildConfig
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.Screen
import com.tertiaryinfotech.ailmstms.data.Assessment
import com.tertiaryinfotech.ailmstms.data.ClassDetail
import com.tertiaryinfotech.ailmstms.data.DemoApi
import com.tertiaryinfotech.ailmstms.data.DetailSession
import com.tertiaryinfotech.ailmstms.data.FeedbackSummary
import com.tertiaryinfotech.ailmstms.data.QuizLink
import com.tertiaryinfotech.ailmstms.data.asOnWebsite
import com.tertiaryinfotech.ailmstms.data.hoursText
import com.tertiaryinfotech.ailmstms.data.isSafeLink
import com.tertiaryinfotech.ailmstms.data.longDay
import com.tertiaryinfotech.ailmstms.data.longRange
import com.tertiaryinfotech.ailmstms.data.mapsUrl
import com.tertiaryinfotech.ailmstms.data.meetingProvider
import com.tertiaryinfotech.ailmstms.data.portalUrl
import com.tertiaryinfotech.ailmstms.data.prettyDay
import com.tertiaryinfotech.ailmstms.data.scoreText
import com.tertiaryinfotech.ailmstms.data.ssgAttendanceListUrl
import com.tertiaryinfotech.ailmstms.data.ssgQrAttendanceUrl

// The class page, with the same sections, names and order as the class page on
// lms-tms.tertiaryinfotech.com: an always-visible summary, then one collapsible card per section,
// all collapsed when the page opens.

@Composable
fun ClassScreen(vm: LmsViewModel, screen: Screen.ClassRoom) {
    when (val state = vm.classState) {
        Load.Idle, Load.Loading -> LoadingCard("Loading class details…")
        is Load.Failed -> Column(Modifier.padding(20.dp)) { ErrorCard(state.message) { vm.loadClass(screen.id) } }
        is Load.Ready -> ClassContent(vm, state.value)
    }
}

/** A link the server may send as a path on the portal, made absolute. */
private fun portal(raw: String): String? = portalUrl(raw, BuildConfig.API_BASE_URL)

@Composable
private fun ClassContent(vm: LmsViewModel, detail: ClassDetail) {
    val context = LocalContext.current
    val trainer = vm.role == "trainer"
    val partial = detail.partialReason != null
    var manualCode by rememberSaveable { mutableStateOf<String?>(null) }
    var scanProblem by rememberSaveable { mutableStateOf<String?>(null) }
    // Sections the user has opened. Every section starts collapsed, like the website's cards.
    val open = vm.openSections.toSet()
    fun toggle(title: String) = vm.toggleSection(title)

    // The same scanner reads the check-in code and a quiz QR code (the view model tells them apart).
    fun scan() {
        if (vm.isDemo) {
            // The demo has no trainer to show a code, so a successful scan is simulated.
            vm.checkIn(DemoApi.DEMO_CODE)
            return
        }
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code -> vm.checkIn(code.rawValue.orEmpty()) }
            .addOnFailureListener {
                scanProblem = "The scanner could not start on this device. You can type the check-in code or link instead."
                manualCode = ""
            }
    }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("class-detail")) {
        item(key = "summary") { ClassSummary(detail) }
        detail.partialReason?.let { reason ->
            item(key = "partial") {
                ErrorCard("Showing the basic class details only. Attendance, assessments and feedback could not be loaded: $reason") { vm.loadClass(detail.id) }
            }
        }
        if (detail.isCancelled) item(key = "cancelled") { ErrorCard("This class has been cancelled.") }

        courseDetails(detail, open, ::toggle)

        if (!partial) {
            fold("E-Attendance", Icons.Default.CheckCircle, open, ::toggle,
                footer = if (trainer) "SSG QR Attendance and SSG E-Attendance List open on MySkillsFuture. Open a session below to mark attendance in the LMS."
                else "E-Attendance is taken on MySkillsFuture with Singpass. Also scan the class QR code your trainer shows.",
            ) {
                val da = detail.digitalAttendanceId
                if (da.isNotBlank()) InfoRow("Digital Attendance ID", da)
                when {
                    trainer && da.isNotBlank() -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SsgButton("SSG QR Attendance", QrIcon, Modifier.weight(1f)) { openLink(context, ssgQrAttendanceUrl(da)) }
                        SsgButton("SSG E-Attendance List", Icons.AutoMirrored.Filled.List, Modifier.weight(1f)) { openLink(context, ssgAttendanceListUrl(da)) }
                    }
                    detail.ssgAttendanceUrl != null -> LinkRow(
                        Icons.Default.CheckCircle, "E-Attendance", "Open E-Attendance for Course Run ${detail.runCode}",
                    ) { openInApp(context, detail.ssgAttendanceUrl) }
                    !trainer -> Text(
                        "E-Attendance link not yet available for this course run.", fontSize = 14.sp, fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (detail.sessions.isEmpty()) Text("Session times to be confirmed", color = MaterialTheme.colorScheme.onSurfaceVariant)
                detail.sessions.forEach { session ->
                    HorizontalDivider(color = LocalLmsColors.current.cardBorder)
                    SessionLine(session, trainer, !trainer) { vm.openAttendance(session.id, session.label) }
                }
                if (!trainer) {
                    OutlinedButton(onClick = { scan() }, modifier = Modifier.fillMaxWidth().testTag("scan-checkin")) {
                        Icon(QrIcon, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan class QR to check in")
                    }
                }
            }

            if (trainer) {
                fold("SWDA Enrolment", Icons.Default.AddCircle, open, ::toggle,
                    footer = "For learners not yet enrolled on SWDA. The academy then enrols them on TPGateway.",
                ) {
                    val subtitle = listOfNotNull(
                        detail.code.takeIf { it.isNotBlank() }?.let { "Course Code: $it" },
                        detail.runCode.takeIf { it.isNotBlank() }?.let { "Course Run ID: $it" },
                    ).joinToString(" · ")
                    val link = portal(detail.swdaEnrolmentUrl.ifBlank { "/enrol/${detail.id}" })
                    LinkRow(Icons.Default.AddCircle, "Open SWDA Enrolment Form", subtitle, enabled = link != null, tag = "open-swda") {
                        link?.let { openInApp(context, it) }
                    }
                }
            }

            fold("Announcements", MegaphoneIcon, open, ::toggle) {
                if (detail.announcements.isEmpty()) Text("No announcements yet.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                detail.announcements.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = LocalLmsColors.current.cardBorder)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (item.title.isNotBlank()) Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (item.message.isNotBlank()) Text(item.message, fontSize = 14.sp, lineHeight = 20.sp)
                        val meta = listOf(item.postedBy, item.createdAt.takeIf { it.isNotBlank() }?.let { longDay(it) }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        item.linkUrl?.let { link -> LinkRow(LinkIcon, "Open link") { openInApp(context, link) } }
                        item.fileUrl?.let { file -> LinkRow(DocumentIcon, item.fileName.ifBlank { "Attachment" }) { vm.openMaterial(item.fileName.ifBlank { "Attachment" }, file) } }
                    }
                }
            }
        }

        fold("Courseware", FolderIcon, open, ::toggle, footer = "PDF courseware opens inside the app. Other links open in the browser.") {
            val items = detail.materials.asOnWebsite(trainer)
            if (items.isEmpty()) Text("No courseware published yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { material -> LinkRow(DocumentIcon, material.title, tag = "material-${material.id}") { vm.openMaterial(material.title, material.url) } }
        }

        if (!partial) {
            if (!trainer) {
                fold("Lesson", SchoolIcon, open, ::toggle,
                    footer = "When your trainer shows a quiz QR code, scan it here or with your phone camera. The quiz opens in the app and your score is saved to your class.",
                ) {
                    LinkRow(QrIcon, "Scan a quiz QR code", tag = "scan-quiz") { scan() }
                    if (vm.isDemo) LinkRow(ClipboardIcon, "Try the sample quiz", tag = "sample-quiz") { vm.openQuiz(QuizLink(DemoApi.QUIZ_COURSE_ID, DemoApi.QUIZ_ID)) }
                }

                // Course Feedback, TRAQOM and the Briefing are for learners; trainers do not fill them in.
                // An older server sends no flag; show Course Feedback then, as the website does for this academy.
                val withFeedback = detail.showCourseFeedback ?: true
                fold(if (withFeedback) "Course Feedback & TRAQOM Survey" else "TRAQOM Survey", Icons.Default.Star, open, ::toggle,
                    footer = "Your feedback helps us improve. TRAQOM is the SkillsFuture Singapore training quality survey.",
                ) {
                    detail.feedback?.let { feedback ->
                        when {
                            feedback.externalUrl != null -> LinkRow(Icons.Default.Star, "Course Feedback") { openInApp(context, feedback.externalUrl) }
                            feedback.submitted -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Course feedback sent. Thank you.", color = MaterialTheme.colorScheme.primary)
                            }
                            feedback.enabled -> LinkRow(Icons.Default.Star, "Course Feedback", tag = "course-feedback", external = false) { vm.openFeedback(detail) }
                        }
                    }
                    detail.traqomUrl?.let { link -> LinkRow(ClipboardIcon, "TRAQOM Survey", tag = "traqom") { openInApp(context, link) } }
                }

                if (detail.briefingOnAssessment.isNotEmpty()) {
                    fold("Briefing on Assessment", Icons.AutoMirrored.Filled.List, open, ::toggle) {
                        detail.briefingOnAssessment.forEach { point ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(point, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }

            fold("Assessment", ClipboardIcon, open, ::toggle) {
                if (detail.assessments.isEmpty()) {
                    Text(if (trainer) "No assessments set for this course" else "No assessment is open yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                detail.assessments.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = LocalLmsColors.current.cardBorder)
                    if (trainer) {
                        PublishSwitch(vm, detail, item)
                    } else {
                        LinkRow(
                            DocumentIcon, item.label,
                            if (item.submissions.isEmpty()) "Not submitted" else "${item.submissions.size} file(s) submitted",
                            external = false, tag = "assessment-${item.type}",
                        ) { vm.stack.add(Screen.Assessments(detail.id)) }
                    }
                }
            }

            fold("Assessment Summary Record", SealIcon, open, ::toggle) {
                if (trainer) TrainerSummaryRecord(vm, detail) else LearnerSummaryRecord(vm, detail)
            }

            if (trainer) {
                fold("Assessment Grading", Icons.Default.Edit, open, ::toggle) {
                    LinkRow(
                        Icons.Default.Edit, "Assessment Grading",
                        "Open Assessment Grading for Course Run ${detail.runCode} · ${detail.roster.size} learners",
                        external = false, tag = "grading",
                    ) { vm.stack.add(Screen.Assessments(detail.id)) }
                }
                fold("Post Course Feedback Summary", ChartIcon, open, ::toggle) {
                    val summary = detail.feedbackSummary
                    if (summary != null && summary.count > 0) {
                        FeedbackSummaryView(summary)
                    } else {
                        Text(
                            if (detail.feedbackCount > 0) "${detail.feedbackCount} responses received." else "No feedback yet for this class.",
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    vm.signerSheet?.let { SignatureSetupDialog(vm, it) }

    manualCode?.let { code ->
        AlertDialog(
            onDismissRequest = { manualCode = null },
            title = { Text("Enter check-in code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    scanProblem?.let { Text(it) }
                    OutlinedTextField(code, { manualCode = it.take(300) }, label = { Text("Check-in link or code") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { manualCode = null; vm.checkIn(code) }, enabled = code.isNotBlank()) { Text("Check in") } },
            dismissButton = { TextButton(onClick = { manualCode = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- summary card

/** Always visible at the top: what, when, where, who, and the meeting link one tap away. */
@Composable
private fun ClassSummary(detail: ClassDetail) {
    val context = LocalContext.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    InfoCard(Modifier.testTag("class-summary"), padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(detail.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
            val codeLine = listOfNotNull(detail.code.takeIf { it.isNotBlank() }, detail.runCode.takeIf { it.isNotBlank() }?.let { "Run $it" }).joinToString("  ·  ")
            if (codeLine.isNotBlank()) Text(codeLine, fontSize = 14.sp, color = secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(detail.format.ifBlank { "Physical" }, Tone.Info)
                if (detail.classStatus.isNotBlank()) StatusChip(detail.classStatus, classStatusTone(detail.classStatus))
            }
            if (detail.startDate.isNotBlank()) {
                IconLine(Icons.Default.DateRange) { Text(longRange(detail.startDate, detail.endDate), fontSize = 14.sp) }
            }
            if (detail.venue.isNotBlank()) VenueLink(detail.venue)
            if (detail.trainerNames.isNotEmpty()) {
                IconLine(Icons.Default.Person, Modifier.testTag("summary-trainer")) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = secondary)) { append(if (detail.trainerNames.size > 1) "Trainers: " else "Trainer: ") }
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(detail.trainerNames.joinToString(", ")) }
                        },
                        fontSize = 14.sp,
                    )
                }
            }
            // Hybrid and online classes: the meeting link one tap away, opened outside the app.
            detail.meetingUrl?.let { link ->
                Button(
                    onClick = { openLink(context, link) }, enabled = isSafeLink(link),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("join-meeting"),
                ) { Text("Join ${meetingProvider(link)}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun IconLine(icon: ImageVector, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** The class venue's address; tapping opens it in Google Maps. */
@Composable
fun VenueLink(address: String) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClickLabel = "Open in Google Maps") { openLink(context, mapsUrl(address)) }
            .testTag("venue-map"),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(PinIcon, null, Modifier.padding(top = 1.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(address, fontSize = 14.sp, lineHeight = 19.sp)
            Text("Open in Google Maps", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------------------------------------------------------------- collapsible cards

/** A website-style card: a tappable title row with a chevron; the rows show only when open. */
private fun LazyListScope.fold(
    title: String,
    icon: ImageVector,
    open: Set<String>,
    toggle: (String) -> Unit,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = "fold-$title") {
        val isOpen = title in open
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { toggle(title) }
                        .semantics { stateDescription = if (isOpen) "Expanded" else "Collapsed" }
                        .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp).testTag("section-$title"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                    Icon(Icons.Default.KeyboardArrowDown, null, Modifier.rotate(if (isOpen) 180f else 0f), tint = MaterialTheme.colorScheme.primary)
                }
                if (isOpen) {
                    HorizontalDivider(color = LocalLmsColors.current.cardBorder)
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                }
            }
            if (isOpen && footer != null) {
                Text(footer, Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Course Details: the TSC, funding validity and hours. Empty fields are left out; so is the card when nothing is left. */
private fun LazyListScope.courseDetails(detail: ClassDetail, open: Set<String>, toggle: (String) -> Unit) {
    val info = detail.courseDetails ?: return
    val training = info.trainingHours
    val assessment = info.assessmentHours
    val hasAny = listOf(info.tscTitle, info.tscCode, info.caslSkills, info.fundingValidityStart, info.fundingValidityEnd).any { it.isNotBlank() } ||
        training != null || assessment != null
    if (!hasAny) return
    fold("Course Details", Icons.Default.Info, open, toggle) {
        if (info.tscTitle.isNotBlank()) InfoRow("TSC Title", info.tscTitle)
        if (info.tscCode.isNotBlank()) InfoRow("TSC Code", info.tscCode)
        if (info.caslSkills.isNotBlank()) InfoRow("CASL Skills", info.caslSkills)
        if (info.fundingValidityStart.isNotBlank() || info.fundingValidityEnd.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Funding Validity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val from = info.fundingValidityStart.takeIf { it.isNotBlank() }?.let(::longDay) ?: "N/A"
                    val to = info.fundingValidityEnd.takeIf { it.isNotBlank() }?.let(::longDay) ?: "N/A"
                    Text("$from – $to", Modifier.weight(1f), fontSize = 15.sp)
                    info.expired()?.let { expired -> StatusChip(if (expired) "Expired" else "Valid", if (expired) Tone.Bad else Tone.Good) }
                }
            }
        }
        if (training != null || assessment != null) {
            TileRow(
                Triple("Training", hoursText(training), false),
                Triple("Assessment", hoursText(assessment), false),
                Triple("Total", hoursText((training ?: 0.0) + (assessment ?: 0.0)), true),
            )
        }
    }
}

// ---------------------------------------------------------------- rows inside the cards

/** A tappable row inside a card: icon, title, optional detail, and an arrow (open-outside icon for links). */
@Composable
fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    external: Boolean = true,
    tag: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp).padding(vertical = 4.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
            if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (external) Icon(OpenExternalIcon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** One of the two SSG attendance links on MySkillsFuture. Opens in the browser so Singpass works. */
@Composable
private fun SsgButton(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 72.dp).testTag(title), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(22.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun SessionLine(session: DetailSession, trainer: Boolean, showUnmarked: Boolean, onAttendance: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(session.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val time = listOf(prettyDay(session.date), session.timeText.takeIf { it.isNotBlank() }?.let { "$it SGT" }).filter { !it.isNullOrBlank() }.joinToString(" · ")
                if (time.isNotBlank()) Text(time, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                session.presentCount != null -> StatusChip("${session.presentCount} present", if (session.presentCount > 0) Tone.Good else Tone.Neutral)
                session.attendance == "present" -> StatusChip("Present", Tone.Good)
                session.attendance == "absent" -> StatusChip("Absent", Tone.Bad)
                showUnmarked -> StatusChip("Not marked")
            }
        }
        // The session check-in QR code and hand marking stay on the trainer's session screen.
        if (trainer) OutlinedButton(onClick = onAttendance, modifier = Modifier.testTag("session-${session.id}")) { Text("QR code & attendance") }
    }
}

/** The website's Publish switch for one assessment of the class. Published assessments open for the learners to submit. */
@Composable
private fun PublishSwitch(vm: LmsViewModel, detail: ClassDetail, item: Assessment) {
    val busy = item.type in vm.publishing
    val on = item.published
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.label, fontSize = 15.sp)
                Text(
                    if (busy) "Saving…" else if (on) "Published to learners" else "Not published yet",
                    fontSize = 12.sp, color = if (on) LocalLmsColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = on, onCheckedChange = { vm.publishAssessment(detail, item, it) }, enabled = !busy, modifier = Modifier.testTag("publish-${item.type}"))
        }
        vm.publishProblems[item.type]?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
    }
}

fun resultTone(status: String): Tone = when (status.trim().lowercase()) {
    "competent" -> Tone.Good
    "not yet competent" -> Tone.Bad
    else -> Tone.Neutral
}

// ---------------------------------------------------------------- Post Course Feedback Summary

private val StarColor = Color(0xFFFFB300)

/** Laid out for a phone: overall score, a bar per question, then the comments (three shown, the rest on "Show all"). */
@Composable
private fun FeedbackSummaryView(summary: FeedbackSummary) {
    val colors = LocalLmsColors.current
    var allComments by rememberSaveable { mutableStateOf(false) }
    fun tint(v: Double?): Color = when {
        v == null -> Color.Unspecified
        v >= 4 -> colors.success
        v >= 3 -> colors.warning
        else -> Color(0xFFD32F2F)
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.testTag("feedback-summary"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(scoreText(summary.overall), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = tint(summary.overall).takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 44.sp)
            Text("out of 5", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val v = summary.overall ?: 0.0
                (1..5).forEach { i ->
                    val icon = when {
                        v >= i -> Icons.Default.Star
                        v >= i - 0.5 -> StarHalfIcon
                        else -> StarOutlineIcon
                    }
                    Icon(icon, null, Modifier.size(18.dp), tint = StarColor)
                }
            }
            Text("Overall average", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("${summary.count} ${if (summary.count == 1) "response" else "responses"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    summary.questions.forEachIndexed { i, q ->
        Column(Modifier.semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text("${i + 1}. ${q.label}", Modifier.weight(1f), fontSize = 14.sp, lineHeight = 19.sp)
                Spacer(Modifier.width(8.dp))
                Text("${scoreText(q.average)}/5", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            val t = tint(q.average)
            LinearProgressIndicator(
                progress = { ((q.average ?: 0.0).coerceIn(0.0, 5.0) / 5.0).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = if (t == Color.Unspecified) MaterialTheme.colorScheme.outline else t,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                drawStopIndicator = {},
            )
        }
    }
    if (summary.comments.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Comments", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            (if (allComments) summary.comments else summary.comments.take(3)).forEach { comment ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("“", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, lineHeight = 18.sp)
                    Text(comment, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
            if (summary.comments.size > 3) {
                TextButton(onClick = { allComments = !allComments }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.testTag("all-comments")) {
                    Text(if (allComments) "Show fewer" else "Show all ${summary.comments.size} comments", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
