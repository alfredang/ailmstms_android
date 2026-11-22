package com.tertiaryinfotech.ailmstms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.Screen
import com.tertiaryinfotech.ailmstms.data.ClassDetail
import com.tertiaryinfotech.ailmstms.data.DemoApi
import com.tertiaryinfotech.ailmstms.data.DetailSession
import com.tertiaryinfotech.ailmstms.data.QuizLink
import com.tertiaryinfotech.ailmstms.data.prettyDay

@Composable
fun ClassScreen(vm: LmsViewModel, screen: Screen.ClassRoom) {
    when (val state = vm.classState) {
        Load.Idle, Load.Loading -> LoadingCard("Loading class…")
        is Load.Failed -> Column(Modifier.padding(20.dp)) { ErrorCard(state.message) { vm.loadClass(screen.id) } }
        is Load.Ready -> ClassContent(vm, state.value)
    }
}

@Composable
private fun ClassContent(vm: LmsViewModel, detail: ClassDetail) {
    val context = LocalContext.current
    val colors = LocalLmsColors.current
    val trainer = vm.role == "trainer"
    var manualCode by rememberSaveable { mutableStateOf<String?>(null) }
    var scanProblem by rememberSaveable { mutableStateOf<String?>(null) }
    var traqomQr by rememberSaveable { mutableStateOf(false) }

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

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("class-detail")) {
        // Header
        item {
            Box(Modifier.fillMaxWidth().background(colors.headerBrush, RoundedCornerShape(24.dp)).padding(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(detail.code, color = colors.onHeaderMuted, style = MaterialTheme.typography.labelLarge)
                    Text(detail.title, color = colors.onHeader, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(dateRange(detail.startDate, detail.endDate), color = colors.onHeader)
                    val place = listOf(detail.format, detail.venue).filter { it.isNotBlank() }.joinToString(" · ")
                    if (place.isNotBlank()) Text(place, color = colors.onHeaderMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (detail.classStatus.isNotBlank()) StatusChip(detail.classStatus, statusTone(detail.classStatus), solid = true)
                        if (detail.runCode.isNotBlank()) Text("Run ${detail.runCode}", color = colors.onHeaderMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    detail.meetingUrl?.let { link ->
                        Button(
                            onClick = { openLink(context, link) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.onHeader, contentColor = colors.headerStart),
                        ) { Text("Join online class") }
                    }
                }
            }
        }
        detail.partialReason?.let { reason ->
            item {
                ErrorCard("Showing the basic class details only. Attendance, assessments and feedback could not be loaded: $reason") { vm.loadClass(detail.id) }
            }
        }
        if (detail.isCancelled) item { ErrorCard("This class has been cancelled.") }

        // Attendance actions
        if (detail.partialReason == null) {
            item { SectionTitle("Attendance") }
            if (!trainer) {
                item { ActionRow(QrIcon, "Scan class QR", "Check in with the code your trainer is showing") { scan() } }
                detail.ssgAttendanceUrl?.let { link ->
                    item { ActionRow(OpenExternalIcon, "SSG e-attendance (Singpass)", "Opens MySkillsFuture in your browser") { openLink(context, link) } }
                }
            } else {
                detail.ssgAttendanceUrl?.let { link ->
                    item { ActionRow(OpenExternalIcon, "Open SSG attendance QR", "Opens MySkillsFuture in your browser") { openLink(context, link) } }
                }
                item {
                    Text(
                        "Choose a session below to show its check-in QR code or to mark attendance by hand.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // The demo has no trainer showing a quiz QR code, so the sample quiz is offered here.
        if (vm.isDemo && !trainer) {
            item { SectionTitle("Class quiz") }
            item {
                ActionRow(ClipboardIcon, "Take the sample quiz", "In class, scan your trainer's quiz QR code to open it") {
                    vm.openQuiz(QuizLink(DemoApi.QUIZ_COURSE_ID, DemoApi.QUIZ_ID))
                }
            }
        }

        // Sessions
        item { SectionTitle("Sessions") }
        if (detail.sessions.isEmpty()) item { EmptyCard("Session times to be confirmed", "Your training provider has not published the sessions for this class yet.") }
        items(detail.sessions, key = { "s-" + it.id }) { session -> SessionRow(session, trainer && detail.partialReason == null, !trainer && detail.partialReason == null) { vm.openAttendance(session.id, session.label) } }

        // Materials
        item { SectionTitle("Courseware") }
        if (detail.materials.isEmpty()) item { EmptyCard("Materials coming soon", "Your training provider has not published materials for this class yet.") }
        items(detail.materials, key = { "m-" + it.id }) { material ->
            ActionRow(DocumentIcon, material.title, "Opens in the app when it is a PDF") { vm.openMaterial(material.title, material.url) }
        }

        // Assessments
        if (detail.partialReason == null) {
            item { SectionTitle("Assessments") }
            if (trainer) {
                item {
                    val graded = detail.roster.count { it.status.isNotBlank() }
                    ActionRow(ClipboardIcon, "Assessments & results", "${detail.roster.size} learners · $graded with a result") { vm.stack.add(Screen.Assessments(detail.id)) }
                }
            } else if (detail.assessments.isEmpty()) {
                item { EmptyCard("No assessment open yet", "Your trainer publishes each assessment when it is time to take it.") }
            } else {
                item {
                    val files = detail.assessments.sumOf { it.submissions.size }
                    ActionRow(
                        ClipboardIcon, "Assessments",
                        "${detail.assessments.size} open · $files file${if (files == 1) "" else "s"} submitted",
                        trailing = if (detail.assessmentStatus.isNotBlank()) ({ StatusChip(detail.assessmentStatus, resultTone(detail.assessmentStatus)) }) else null,
                    ) { vm.stack.add(Screen.Assessments(detail.id)) }
                }
            }

            // Feedback and TRAQOM
            item { SectionTitle("Feedback") }
            if (trainer) {
                item {
                    AppCard {
                        Text(
                            "${detail.feedbackCount} course feedback response${if (detail.feedbackCount == 1) "" else "s"} received",
                            Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            } else {
                detail.feedback?.let { feedback ->
                    item {
                        when {
                            feedback.submitted -> ActionRow(Icons.Default.CheckCircle, "Feedback sent", "Thank you for your course feedback", trailing = { StatusChip("Sent", Tone.Good) }) {}
                            feedback.externalUrl != null -> ActionRow(Icons.Default.Star, "Give course feedback", "Opens your academy's form in the browser") { openLink(context, feedback.externalUrl) }
                            feedback.enabled -> ActionRow(Icons.Default.Star, "Give course feedback", "Takes about two minutes") { vm.openFeedback(detail) }
                            else -> EmptyCard("Feedback is not open", "Your training provider has not opened course feedback.")
                        }
                    }
                }
            }
            detail.traqomUrl?.let { link ->
                item {
                    ActionRow(
                        OpenExternalIcon, "TRAQOM survey", "SSG's course quality survey, in your browser",
                        trailing = if (!trainer && detail.traqomCompleted) ({ StatusChip("Done", Tone.Good) }) else null,
                    ) { openLink(context, link) }
                }
                // The trainer shows the survey to the class as a QR code, as with the check-in code.
                if (trainer) {
                    item {
                        Button(onClick = { traqomQr = true }, modifier = Modifier.fillMaxWidth().testTag("showTraqomQR")) {
                            Icon(QrIcon, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Show TRAQOM Survey QR")
                        }
                    }
                }
            }
        }
    }

    val traqom = detail.traqomUrl
    if (traqomQr && trainer && traqom != null) {
        QrDialog("TRAQOM Survey", traqom, "Learners scan this to complete SSG's course quality survey.", tag = "traqomQr", shareLabel = "Share Link") { traqomQr = false }
    }

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

fun resultTone(status: String): Tone = when (status.trim().lowercase()) {
    "competent" -> Tone.Good
    "not yet competent" -> Tone.Bad
    else -> Tone.Neutral
}

@Composable
private fun SessionRow(session: DetailSession, canTakeAttendance: Boolean, showUnmarked: Boolean, onAttendance: () -> Unit) {
    AppCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(session.label, style = MaterialTheme.typography.titleMedium)
                    val time = listOf(prettyDay(session.date), session.timeText.takeIf { it.isNotBlank() }?.let { "$it SGT" }).filter { !it.isNullOrBlank() }.joinToString(" · ")
                    if (time.isNotBlank()) Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    session.presentCount != null -> StatusChip("${session.presentCount} present", if (session.presentCount > 0) Tone.Good else Tone.Neutral)
                    session.attendance == "present" -> StatusChip("Present", Tone.Good)
                    session.attendance == "absent" -> StatusChip("Absent", Tone.Bad)
                    showUnmarked -> StatusChip("Not marked")
                }
            }
            if (canTakeAttendance) OutlinedButton(onClick = onAttendance) { Text("QR code & attendance") }
        }
    }
}
