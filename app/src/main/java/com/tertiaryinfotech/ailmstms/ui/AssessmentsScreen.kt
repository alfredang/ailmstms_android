package com.tertiaryinfotech.ailmstms.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.Screen
import com.tertiaryinfotech.ailmstms.data.Assessment
import com.tertiaryinfotech.ailmstms.data.ClassDetail
import com.tertiaryinfotech.ailmstms.data.RosterEntry
import com.tertiaryinfotech.ailmstms.data.Submission
import com.tertiaryinfotech.ailmstms.data.prettyInstant

@Composable
fun AssessmentsScreen(vm: LmsViewModel, screen: Screen.Assessments) {
    when (val state = vm.classState) {
        Load.Idle, Load.Loading -> LoadingCard("Loading assessments…")
        is Load.Failed -> Column(Modifier.padding(20.dp)) { ErrorCard(state.message) { vm.loadClass(screen.classId) } }
        is Load.Ready -> if (vm.role == "trainer") TrainerAssessments(vm, state.value) else LearnerAssessments(vm, state.value)
    }
}

// ---------------------------------------------------------------- learner

@Composable
private fun LearnerAssessments(vm: LmsViewModel, detail: ClassDetail) {
    // The picker returns to this screen; remember which assessment the file is for.
    var pendingType by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Submission?>(null) }
    // The system document picker needs no storage permission: access is granted to the one chosen file.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val assessment = detail.assessments.firstOrNull { it.type == pendingType }
        pendingType = null
        if (uri != null && assessment != null) vm.submitFile(detail, assessment, uri)
    }
    val uploading = vm.upload?.let { it.error == null } == true

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("assessments")) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Heading(detail.title)
                if (detail.assessmentStatus.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your result")
                        StatusChip(detail.assessmentStatus, resultTone(detail.assessmentStatus))
                    }
                }
            }
        }
        if (detail.assessments.isEmpty()) item { EmptyCard("No assessment open yet", "Your trainer publishes each assessment when it is time to take it.") }
        items(detail.assessments, key = { it.type }) { assessment ->
            AssessmentCard(
                vm, assessment, uploading,
                onPick = {
                    vm.clearUpload()
                    pendingType = assessment.type
                    picker.launch(arrayOf("*/*"))
                },
                onDelete = { deleting = it },
            )
        }
        item {
            Text(
                "Files are stored in your academy's Google Drive for your assessor. A file can be removed until your assessor has signed it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    deleting?.let { file ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove this file?") },
            text = { Text("\"${file.fileName}\" will be removed from your submission.") },
            confirmButton = { TextButton(onClick = { deleting = null; vm.deleteSubmission(detail, file.id) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun AssessmentCard(vm: LmsViewModel, assessment: Assessment, uploading: Boolean, onPick: () -> Unit, onDelete: (Submission) -> Unit) {
    val upload = vm.upload?.takeIf { it.type == assessment.type }
    Card {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(assessment.label, style = MaterialTheme.typography.titleLarge)
            if (assessment.questionUrl != null) {
                OutlinedButton(onClick = { vm.openMaterial("${assessment.label} question paper", assessment.questionUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(DocumentIcon, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View question paper")
                }
            } else {
                Text("Your trainer will hand out the question paper in class.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onPick, enabled = !uploading && !vm.working, modifier = Modifier.fillMaxWidth()) {
                Icon(UploadIcon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Submit file")
            }
            if (upload != null) {
                if (upload.error == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val percent = (upload.progress * 100).toInt()
                        Text(if (percent >= 100) "Saving \"${upload.fileName}\"…" else "Uploading \"${upload.fileName}\" · $percent%", style = MaterialTheme.typography.bodyMedium)
                        if (percent >= 100) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { upload.progress }, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    ErrorCard("\"${upload.fileName}\" was not submitted. ${upload.error}")
                }
            }
            HorizontalDivider()
            if (assessment.submissions.isEmpty()) {
                Text("No file submitted yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Your files", style = MaterialTheme.typography.labelLarge)
                assessment.submissions.forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(file.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(prettyInstant(file.submittedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (file.signed) StatusChip("Signed", Tone.Good)
                        else IconButton(onClick = { onDelete(file) }, enabled = !vm.working) { Icon(Icons.Default.Delete, "Remove ${file.fileName}") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- trainer

@Composable
private fun TrainerAssessments(vm: LmsViewModel, detail: ClassDetail) {
    var grading by remember { mutableStateOf<Pair<RosterEntry, Boolean>?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("assessments")) {
        item { Heading(detail.title) }
        if (detail.assessments.isNotEmpty()) {
            item { SectionTitle("Assessment papers") }
            items(detail.assessments, key = { "a-" + it.type }) { assessment ->
                ActionRow(
                    DocumentIcon, assessment.label,
                    if (assessment.questionUrl == null) "No question paper link" else "View question paper",
                    trailing = { StatusChip(if (assessment.published) "Published" else "Not published", if (assessment.published) Tone.Good else Tone.Neutral) },
                ) { assessment.questionUrl?.let { vm.openMaterial("${assessment.label} question paper", it) } }
            }
        }
        item { SectionTitle("Learner results") }
        if (detail.roster.isEmpty()) item { EmptyCard("No learners yet", "Learners appear here once they are enrolled in this class.") }
        items(detail.roster, key = { it.enrolmentId }) { learner ->
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(learner.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${learner.submissions} file${if (learner.submissions == 1) "" else "s"} submitted" + if (learner.traqomCompleted) " · TRAQOM done" else "",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusChip(learner.status.ifBlank { "No result" }, resultTone(learner.status))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { grading = learner to true }, enabled = !vm.working, modifier = Modifier.weight(1f).testTag("competent-${learner.enrolmentId}"), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Competent") }
                        OutlinedButton(onClick = { grading = learner to false }, enabled = !vm.working, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Not Yet Competent") }
                    }
                }
            }
        }
    }

    grading?.let { (learner, competent) ->
        val status = if (competent) "Competent" else "Not Yet Competent"
        AlertDialog(
            onDismissRequest = { grading = null },
            title = { Text("Mark as $status?") },
            text = { Text("${learner.name} will be recorded as $status for ${detail.title}.") },
            confirmButton = { TextButton(onClick = { grading = null; vm.grade(detail, learner, competent) }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { grading = null }) { Text("Cancel") } },
        )
    }
}
