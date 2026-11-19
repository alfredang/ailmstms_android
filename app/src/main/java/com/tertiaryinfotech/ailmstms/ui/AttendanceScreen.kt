package com.tertiaryinfotech.ailmstms.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.Screen
import com.tertiaryinfotech.ailmstms.data.AttendanceSheet

/** The QR matrix for [text], generated on the device. Null when the text cannot be encoded. */
fun qrMatrix(text: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
}.getOrNull()

/** Always black on white, in both themes, so every phone camera can read it. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { qrMatrix(text) }
    Canvas(modifier.aspectRatio(1f).background(Color.White).semantics { contentDescription = "Check-in QR code" }) {
        val bits = matrix ?: return@Canvas
        val cell = size.width / bits.width
        for (y in 0 until bits.height) {
            for (x in 0 until bits.width) {
                // A hair of overlap hides the seams between neighbouring cells.
                if (bits[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/** Trainer: the session's check-in QR code and manual Present/Absent marking. */
@Composable
fun AttendanceScreen(vm: LmsViewModel, screen: Screen.Attendance) {
    when (val state = vm.attendanceState) {
        Load.Idle, Load.Loading -> LoadingCard("Loading attendance…")
        is Load.Failed -> Column(Modifier.padding(20.dp)) { ErrorCard(state.message) { vm.loadAttendance(screen.sessionId) } }
        is Load.Ready -> AttendanceContent(vm, screen, state.value)
    }
}

@Composable
private fun AttendanceContent(vm: LmsViewModel, screen: Screen.Attendance, sheet: AttendanceSheet) {
    var enlarged by rememberSaveable { mutableStateOf(false) }
    val learners = sheet.learners.map { vm.attendanceDraft[it.enrolmentId] ?: it }
    val changed = learners != sheet.learners
    val present = learners.count { it.isPresent == true }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f).testTag("attendance"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Heading(screen.label) }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Learner check-in")
                        if (sheet.checkinUrl != null) {
                            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.clickable { enlarged = true }) {
                                QrCode(sheet.checkinUrl, Modifier.padding(12.dp).size(240.dp))
                            }
                            Text(
                                "Learners scan this with Scan class QR in the app, or with their phone camera. Tap the code to enlarge it.",
                                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text("This session has no check-in code yet. Mark attendance by hand below.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("Manual attendance")
                        Text("$present of ${learners.size} present", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (learners.isNotEmpty()) TextButton(onClick = vm::markAllPresent, enabled = !vm.working) { Text("Mark all present") }
                }
            }
            if (learners.isEmpty()) item { EmptyCard("No learners yet", "Learners appear here once they are enrolled in this class.") }
            items(learners, key = { it.enrolmentId }) { learner ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(learner.name, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = learner.isPresent == true, enabled = !vm.working, label = { Text("Present") },
                                onClick = { vm.mark(learner.copy(isPresent = true, reason = "")) },
                            )
                            FilterChip(
                                selected = learner.isPresent == false, enabled = !vm.working, label = { Text("Absent") },
                                onClick = { vm.mark(learner.copy(isPresent = false)) },
                            )
                        }
                        if (learner.isPresent == false) {
                            OutlinedTextField(
                                learner.reason, { vm.mark(learner.copy(reason = it.take(300))) }, label = { Text("Reason for absence (optional)") },
                                singleLine = true, enabled = !vm.working, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = { vm.saveAttendance(screen.sessionId) },
                enabled = changed && !vm.working,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) { Text(if (vm.working) "Saving…" else if (changed) "Save attendance" else "Attendance is up to date") }
        }
    }

    if (enlarged && sheet.checkinUrl != null) {
        Dialog(onDismissRequest = { enlarged = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCode(sheet.checkinUrl, Modifier.fillMaxWidth())
                    Text(screen.label, color = Color.Black, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    TextButton(onClick = { enlarged = false }) { Text("Close", color = Color(0xFF0A3D8F)) }
                }
            }
        }
    }
}
