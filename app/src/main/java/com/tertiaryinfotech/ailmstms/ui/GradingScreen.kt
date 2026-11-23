package com.tertiaryinfotech.ailmstms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.Screen
import com.tertiaryinfotech.ailmstms.data.AssessmentMethod
import com.tertiaryinfotech.ailmstms.data.RosterStudent

// Assessment Grading for the class trainer, in the website's grading-roster form, through the
// website's own routes (each checks that the caller trains this class):
//   GET  /api/trainer/class-students?courseRunId=&withMeta=1   roster + assessment methods
//   POST /api/trainer/grade-student     {enrolmentIds, source, isCompetent}
//   POST /api/trainer/traqom-status     {enrolmentIds, completed}   (SSG gives no completion feed)
//   POST /api/trainer/sign-assessments  {courseRunId, learnerUserIds, signed}

/** An empty square, for a method not submitted or an unticked box. */
private val SquareIcon by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("Square", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            androidx.compose.ui.graphics.vector.PathParser().parsePathString("M4,4h16v16H4z M6,6v12h12V6z").toNodes(),
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd, fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
    }.build()
}

/** A ticked square. */
private val TickSquareIcon by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("TickSquare", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            androidx.compose.ui.graphics.vector.PathParser().parsePathString("M4,4h16v16H4z M10.2,15.6l-3.4,-3.4l1.4,-1.4l2,2l5.6,-5.6l1.4,1.4z").toNodes(),
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd, fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
    }.build()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradingScreen(vm: LmsViewModel, screen: Screen.Grading) {
    var pendingGrade by remember { mutableStateOf<Pair<RosterStudent, Boolean>?>(null) }
    var pendingUnsign by remember { mutableStateOf<RosterStudent?>(null) }
    when (val state = vm.rosterState) {
        Load.Idle, Load.Loading -> LoadingCard("Loading learners…")
        is Load.Failed -> Column(Modifier.padding(20.dp)) { ErrorCard(state.message) { vm.loadRoster(screen.classId) } }
        is Load.Ready -> {
            val roster = state.value
            val students = roster.students
            val n = students.size
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("grading-roster")) {
                item(key = "tiles") {
                    // One tile per assessment method, then TRAQOM, Signed and Competent; wrapped three to a row.
                    val tiles = roster.methods.map { m -> Triple("${AssessmentMethod.abbr(m)} submitted", "${students.count { it.submitted(m) }}/$n", false) } +
                        listOf(
                            Triple("TRAQOM done", "${students.count { it.traqomCompleted }}/$n", false),
                            Triple("Signed", "${students.count { it.assessorSigned }}/$n", false),
                            Triple("Competent", "${students.count { it.isCompetent == true }}/$n", true),
                        )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("grading-tiles")) {
                        tiles.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (label, value, strong) -> HoursTile(label, value, Modifier.weight(1f), strong) }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                val unsigned = roster.unsigned
                if (unsigned.size > 1) {
                    item(key = "sign-all") {
                        Button(
                            onClick = { vm.signSubmissionsTapped(unsigned) }, enabled = vm.gradingBusy.isEmpty(),
                            modifier = Modifier.fillMaxWidth().testTag("sign-all-submissions"),
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign all ${unsigned.size} submitted", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item(key = "legend") {
                    Text(AssessmentMethod.legend(roster.methods), Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                vm.gradingProblem?.let { problem -> item(key = "problem") { ErrorCard(problem) } }
                if (students.isEmpty()) item(key = "empty") { EmptyCard("No learners on this class yet", "Learners appear here once they are enrolled in this class.") }
                items(students, key = { it.enrolmentId }) { s ->
                    StudentRow(
                        vm, s, roster.methods,
                        onGrade = { pendingGrade = s to it },
                        onSign = { if (s.assessorSigned) pendingUnsign = s else vm.signSubmissionsTapped(listOf(s)) },
                    )
                }
            }
        }
    }

    pendingGrade?.let { (s, competent) ->
        AlertDialog(
            onDismissRequest = { pendingGrade = null },
            title = { Text("Mark ${s.name} as ${if (competent) "Competent" else "Not Yet Competent"}?") },
            text = { Text("Review the learner's submitted files before confirming.") },
            confirmButton = { TextButton(onClick = { pendingGrade = null; vm.gradeStudent(s, competent) }, modifier = Modifier.testTag("confirm-grade")) { Text("Confirm result") } },
            dismissButton = { TextButton(onClick = { pendingGrade = null }) { Text("Cancel") } },
        )
    }
    pendingUnsign?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingUnsign = null },
            title = { Text("Remove your signature from ${s.name}'s files?") },
            text = { Text("The original files are restored.") },
            confirmButton = {
                TextButton(onClick = { pendingUnsign = null; vm.signSubmissions(listOf(s), false) }, modifier = Modifier.testTag("confirm-unsign")) {
                    Text("Remove signature", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingUnsign = null }) { Text("Cancel") } },
        )
    }
    vm.signerSheet?.let { SignatureSetupDialog(vm, it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentRow(vm: LmsViewModel, s: RosterStudent, methods: List<String>, onGrade: (Boolean) -> Unit, onSign: () -> Unit) {
    val context = LocalContext.current
    val busy = s.enrolmentId in vm.gradingBusy
    val success = LocalLmsColors.current.success
    AppCard(Modifier.fillMaxWidth().testTag("student-${s.enrolmentId}")) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            // Submission status per assessment, e.g. ☑ WA ☐ PP (read-only).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                methods.forEach { m ->
                    val done = s.submitted(m)
                    Row(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = "${AssessmentMethod.label(m)}: ${if (done) "submitted" else "not submitted"}" },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(if (done) TickSquareIcon else SquareIcon, null, Modifier.size(18.dp), tint = if (done) success else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(AssessmentMethod.abbr(m), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (done) success else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                s.folderUrl?.let { folder ->
                    TextButton(onClick = { openLink(context, folder) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Icon(FolderIcon, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Files")
                    }
                }
            }
            // Trainer ticks: TRAQOM done, and the assessor signature on the submitted files.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CheckChip("TRAQOM done", s.traqomCompleted, !busy, "traqom-${s.enrolmentId}") { vm.setTraqom(s, !s.traqomCompleted) }
                CheckChip("Signed", s.assessorSigned, !busy && s.signable, "sign-${s.enrolmentId}", onSign)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onGrade(true) }, enabled = s.isCompetent != true && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = success, contentColor = Color.White),
                    modifier = Modifier.weight(1f).testTag("competent-${s.enrolmentId}"), contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Competent") }
                OutlinedButton(
                    onClick = { onGrade(false) }, enabled = !s.notYetCompetent && !busy,
                    modifier = Modifier.weight(1f).testTag("nyc-${s.enrolmentId}"), contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Not Yet Competent") }
            }
            Text(s.resultText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("result-${s.enrolmentId}"))
        }
    }
}

/** A tappable checkbox chip, e.g. "☑ TRAQOM done". */
@Composable
private fun CheckChip(title: String, on: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val tint = if (on) LocalLmsColors.current.success else MaterialTheme.colorScheme.primary
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.1f))
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = if (on) "Checked" else "Unchecked" }
            .alpha(if (enabled) 1f else 0.5f).padding(horizontal = 10.dp, vertical = 6.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (on) TickSquareIcon else SquareIcon, null, Modifier.size(18.dp), tint = tint)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}
