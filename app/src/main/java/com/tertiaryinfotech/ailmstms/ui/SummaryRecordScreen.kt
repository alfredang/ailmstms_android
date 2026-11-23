package com.tertiaryinfotech.ailmstms.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.data.ClassDetail
import com.tertiaryinfotech.ailmstms.data.LearnerSummaryStatus
import com.tertiaryinfotech.ailmstms.data.SignerKind
import com.tertiaryinfotech.ailmstms.data.SummaryParty
import com.tertiaryinfotech.ailmstms.data.longDay
import java.io.ByteArrayOutputStream

// Assessment Summary Record e-signing with the website's own APIs:
//   /api/assessments/summary-record   read the record(s); sign as candidate or assessor
//   /api/learner/signature            the learner's saved name, NRIC and drawn signature
//   /api/trainer/assessor-signature   the trainer's saved assessor name, NRIC and signature
// The server stamps the signature onto the record's PDF and returns the Drive link.

@Composable
private fun SummaryLoading(vm: LmsViewModel, detail: ClassDetail, ready: @Composable () -> Unit) {
    // Loads when the card is opened (this content only exists while it is open).
    LaunchedEffect(detail.id) { vm.loadSummary(detail.id) }
    when (val state = vm.summaryState) {
        Load.Idle, Load.Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        is Load.Failed -> {
            Text(state.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { vm.loadSummary(detail.id) }) { Text("Try again") }
        }
        is Load.Ready -> ready()
    }
    vm.summaryProblem?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("summary-problem")) }
}

/** The learner's Assessment Summary Record: their Candidate signature, the assessor's, and the PDF. */
@Composable
fun LearnerSummaryRecord(vm: LmsViewModel, detail: ClassDetail) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Assessment Result", Modifier.weight(1f), fontSize = 15.sp)
        Text(detail.assessmentStatus.ifBlank { "Pending" }, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SummaryLoading(vm, detail) {
        val record = (vm.summaryState as? Load.Ready)?.value?.record
        val busy = LmsViewModel.SELF in vm.summarySigning
        SignedRow("Candidate (you)", record?.learner)
        SignedRow("Assessor", record?.trainer)
        // Every signature is drawn on the phone at the moment of signing.
        val signed = record?.learner != null
        Button(
            onClick = { vm.signMyRecord(detail.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("sign-summary-record"),
            colors = if (signed) ButtonDefaults.buttonColors(containerColor = SignAgainGrey, contentColor = Color.White) else ButtonDefaults.buttonColors(),
        ) {
            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "Signing…" else if (signed) "Sign again" else "Sign my Assessment Summary Record", fontWeight = FontWeight.SemiBold)
        }
        record?.fileUrl?.let { file -> LinkRow(DocumentIcon, "Open signed record (PDF)", tag = "summary-pdf") { openInApp(context, file) } }
    }
}

/** The trainer's Assessment Summary Record: three counts, sign all, then one row per learner with both signatures. */
@Composable
fun TrainerSummaryRecord(vm: LmsViewModel, detail: ClassDetail) {
    SummaryLoading(vm, detail) {
        val learners = (vm.summaryState as? Load.Ready)?.value?.learners.orEmpty()
        val awaitingMe = learners.filter { it.record?.trainer == null }
        val signing = vm.summarySigning.isNotEmpty()
        TileRow(
            Triple("Learners", "${learners.size}", false),
            Triple("Candidate signed", "${learners.count { it.record?.learner != null }}", false),
            Triple("Assessor signed", "${learners.count { it.record?.trainer != null }}", true),
        )
        if (awaitingMe.size > 1) {
            Button(
                onClick = { vm.signAsAssessor(detail.id, awaitingMe.map { it.learnerUserId }) }, enabled = !signing,
                modifier = Modifier.fillMaxWidth().testTag("sign-all-summary"),
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign all ${awaitingMe.size} as assessor", fontWeight = FontWeight.SemiBold)
            }
        }
        if (learners.isEmpty()) Text("No learners on this class yet.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        learners.forEach { learner ->
            HorizontalDivider(color = LocalLmsColors.current.cardBorder)
            TrainerSummaryRow(learner, busy = learner.learnerUserId in vm.summarySigning, enabled = !signing) {
                vm.signAsAssessor(detail.id, listOf(learner.learnerUserId))
            }
        }
    }
}

@Composable
private fun TrainerSummaryRow(learner: LearnerSummaryStatus, busy: Boolean, enabled: Boolean, sign: () -> Unit) {
    val context = LocalContext.current
    val record = learner.record
    Column(Modifier.fillMaxWidth().testTag("summary-${learner.learnerUserId}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(learner.learnerName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(if (record?.learner != null) "Candidate signed" else "Candidate pending", if (record?.learner != null) Tone.Good else Tone.Warn)
            StatusChip(if (record?.trainer != null) "Assessor signed" else "Assessor pending", if (record?.trainer != null) Tone.Good else Tone.Warn)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            record?.fileUrl?.let { file ->
                TextButton(onClick = { openInApp(context, file) }) {
                    Icon(DocumentIcon, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PDF")
                }
            }
            Spacer(Modifier.weight(1f))
            val signed = record?.trainer != null
            OutlinedButton(
                onClick = sign, enabled = enabled && !busy, modifier = Modifier.testTag("sign-${learner.learnerUserId}"),
                colors = if (signed) ButtonDefaults.outlinedButtonColors(contentColor = SignAgainGrey) else ButtonDefaults.outlinedButtonColors(),
            ) { Text(if (busy) "Signing…" else if (signed) "Sign again" else "Sign") }
        }
    }
}

@Composable
private fun SignedRow(title: String, party: SummaryParty?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 15.sp)
        if (party != null) {
            Icon(SealIcon, null, Modifier.size(18.dp), tint = LocalLmsColors.current.success)
            Spacer(Modifier.width(6.dp))
            Text(listOf("Signed", longDay(party.day)).filter { it.isNotBlank() }.joinToString(" "), fontSize = 14.sp, color = LocalLmsColors.current.success)
        } else {
            Text("Not signed", fontSize = 14.sp, color = LocalLmsColors.current.warning)
        }
    }
}

// ---------------------------------------------------------------- signature setup

/** Name, NRIC and a drawn signature, saved once and reused for every record (as on the website). */
@Composable
fun SignatureSetupDialog(vm: LmsViewModel, kind: SignerKind) {
    val saving = vm.signerSaving
    Dialog(onDismissRequest = vm::closeSigner, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val state = vm.signerState) {
                is Load.Ready -> SignatureForm(vm, kind, state.value.name, state.value.nric, state.value.hasSignature, saving, vm.signerSigning, vm.signerCount)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun SignatureForm(
    vm: LmsViewModel,
    kind: SignerKind,
    savedName: String,
    savedNric: String,
    hadSignature: Boolean,
    saving: Boolean,
    signing: Boolean,
    count: Int,
) {
    var name by rememberSaveable(kind) { mutableStateOf(savedName) }
    var nric by rememberSaveable(kind) { mutableStateOf(savedNric) }
    // Strokes are kept as points from 0 to 1, so the drawing survives a size change and exports at any size.
    val strokes = remember(kind) { mutableStateListOf<SnapshotStateList<Offset>>() }
    var unreadable by remember { mutableStateOf(false) }
    // Signing a record: the signature must be drawn now, even when one is already saved.
    val canSave = !saving && name.isNotBlank() && (strokes.isNotEmpty() || (!signing && hadSignature))

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().testTag("signature-setup")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::closeSigner, enabled = !saving) { Text("Cancel") }
            Text(if (signing) "Sign Summary Record" else kind.title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            TextButton(
                onClick = {
                    unreadable = false
                    val png = if (strokes.isEmpty()) null else signaturePng(strokes)
                    if (strokes.isNotEmpty() && png == null) unreadable = true
                    else vm.saveSigner(kind, name, nric, png)
                },
                enabled = canSave, modifier = Modifier.testTag("confirm-signature"),
            ) { Text(if (saving) "Saving…" else if (signing) "Sign" else "Save", fontWeight = FontWeight.SemiBold) }
        }
        HorizontalDivider(color = LocalLmsColors.current.cardBorder)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                name, { name = it.take(120) }, label = { Text(kind.nameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("signer-name"),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            OutlinedTextField(
                nric, { nric = it.take(20) }, label = { Text("NRIC / FIN") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("signer-nric"),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
            )
            Text("Printed on the Assessment Summary Record beside your signature.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionLabel("Sign with your finger")
            SignaturePad(strokes)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        signing -> "Your signature, name and today's date are stamped on the Assessment Summary Record" +
                            if (count > 1) " of $count learners." else "."
                        hadSignature && strokes.isEmpty() -> "Leave blank to keep your saved signature."
                        else -> "Sign inside the box."
                    },
                    Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) { Text("Clear", color = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Unspecified) }
            }
            if (unreadable) Text("The signature could not be read. Please sign again.", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            vm.signerProblem?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private val SignAgainGrey = Color(0xFF8E8E93)

/** A finger-drawn signature: dark ink on a white pad with a dashed border. */
@Composable
private fun SignaturePad(strokes: SnapshotStateList<SnapshotStateList<Offset>>) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxWidth().height(180.dp).clip(shape).background(Color.White)
            // One accessibility element: "Signature pad", drawn on directly.
            .clearAndSetSemantics {
                contentDescription = "Signature pad"
                testTag = "signature-pad"
                stateDescription = if (strokes.isEmpty()) "Empty" else "Signed"
            }
            .pointerInput(Unit) {
                // Consumed at once, so the page does not scroll while signing.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun point(p: Offset) = Offset(
                        (p.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                        (p.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                    )
                    val stroke = mutableStateListOf(point(down.position))
                    strokes.add(stroke)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        stroke.add(point(change.position))
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (strokes.isEmpty()) Text("Sign here", color = Color.Gray.copy(alpha = 0.6f))
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                Color.Gray.copy(alpha = 0.4f), style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            )
            val path = Path()
            strokes.forEach { stroke ->
                val first = stroke.firstOrNull() ?: return@forEach
                path.moveTo(first.x * size.width, first.y * size.height)
                if (stroke.size == 1) path.lineTo(first.x * size.width + 0.5f, first.y * size.height)
                stroke.drop(1).forEach { path.lineTo(it.x * size.width, it.y * size.height) }
            }
            drawPath(path, Color.Black, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The strokes as a PNG data URL at the website's pad size (560 × 180), dark ink on a clear background. */
fun signaturePng(strokes: List<List<Offset>>, width: Int = 560, height: Int = 180): String? = runCatching {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        val first = stroke.firstOrNull() ?: return@forEach
        val path = android.graphics.Path()
        path.moveTo(first.x * width, first.y * height)
        if (stroke.size == 1) path.lineTo(first.x * width + 0.5f, first.y * height)
        stroke.drop(1).forEach { path.lineTo(it.x * width, it.y * height) }
        canvas.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}.getOrNull()
