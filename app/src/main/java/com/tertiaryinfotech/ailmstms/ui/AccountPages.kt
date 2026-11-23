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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.data.Certificate
import com.tertiaryinfotech.ailmstms.data.SupportInfo
import com.tertiaryinfotech.ailmstms.data.TrainerCredentials
import com.tertiaryinfotech.ailmstms.data.classStatusLabel
import com.tertiaryinfotech.ailmstms.data.longRange

// The learner's Certificates and Support tabs, the trainer's credentials on Account, and the
// class status chip on every class card. Mobile API routes:
//   GET  /api/mobile/certificates           certificates of classes attended as a learner
//   GET/POST /api/mobile/support            contacts; feedback emailed to the academy
//   GET/POST /api/mobile/trainer-profile    ACLP / DDDPL / TAEPP, domain expertise, skill tags

/** The class status as the TMS records it: Confirmed (green), Pending (orange), Cancelled (red). */
@Composable
fun ClassStatusChip(status: String) {
    StatusChip(classStatusLabel(status), classStatusTone(status))
}

// ---------------------------------------------------------------- Certificates

/**
 * The signed-in account's course certificates: one row per class, the certificate to view in
 * the app (and download) once issued. [embedded] is the trainer's "My Certificates" from Account.
 */
@Composable
fun CertificatesScreen(vm: LmsViewModel, embedded: Boolean = false) {
    LaunchedEffect(Unit) { if (vm.certificatesState is Load.Idle) vm.loadCertificates() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("certificates")) {
        item { Heading(if (embedded) "My Certificates" else "Certificates") }
        when (val state = vm.certificatesState) {
            Load.Idle, Load.Loading -> item { LoadingCard("Loading certificates…") }
            is Load.Failed -> item { ErrorCard(state.message) { vm.loadCertificates() } }
            is Load.Ready -> {
                val items = state.value
                item {
                    TileRow(
                        Triple("Certificates", "${items.count { it.certificateUrl != null }}", true),
                        Triple("Classes", "${items.size}", false),
                    )
                }
                if (items.isEmpty()) {
                    item { EmptyState(CertificateIcon, "No certificates yet", "Certificates appear here once your class is complete and the certificate is issued.") }
                }
                items(items, key = { it.id }) { CertificateCard(vm, it) }
            }
        }
    }
}

@Composable
private fun CertificateCard(vm: LmsViewModel, item: Certificate) {
    InfoCard(Modifier.testTag("certificate-${item.id}")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.code.ifBlank { "Course" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.DateRange, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(longRange(item.startDate, item.endDate), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.result.isNotBlank()) StatusChip(item.result, if (item.result.lowercase().startsWith("competent")) Tone.Good else Tone.Warn)
            if (item.certificateUrl != null) {
                // PDF certificates open in the in-app viewer, which has Download; anything else opens in the browser.
                LinkRow(CertificateIcon, "Certificate", "Opens in the app when it is a PDF", external = false, tag = "open-certificate-${item.id}") {
                    vm.openMaterial("Certificate", item.certificateUrl)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(ClockIcon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Certificate not issued yet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Support

/** Support: a feedback form emailed to the academy, then the ways to reach it directly. */
@Composable
fun SupportScreen(vm: LmsViewModel) {
    val context = LocalContext.current
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadSupport() }
    val field = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp).testTag("support"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Heading("Support")
        SectionLabel("Feedback")
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    subject, { subject = it.take(150) }, label = { Text("Subject") }, singleLine = true, shape = field,
                    modifier = Modifier.fillMaxWidth().testTag("support-subject"),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    message, { message = it.take(5000) }, placeholder = { Text("How can we help? Tell us what happened.") },
                    minLines = 4, maxLines = 10, shape = field, modifier = Modifier.fillMaxWidth().testTag("support-message"),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Button(
                    onClick = { vm.sendSupport(subject, message) { subject = ""; message = "" } },
                    enabled = !vm.supportSending && subject.isNotBlank() && message.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("support-send"),
                ) { Text(if (vm.supportSending) "Sending…" else "Send feedback", fontWeight = FontWeight.SemiBold) }
                vm.supportNotice?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = LocalLmsColors.current.success)
                        Text(it, fontSize = 14.sp, color = LocalLmsColors.current.success)
                    }
                }
                vm.supportProblem?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
            }
        }
        Text(
            "Your message is emailed to the academy with your name and email, so we can reply.",
            Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val state = vm.supportState) {
            is Load.Ready -> {
                val info = state.value
                SectionLabel("Contact us")
                InfoCard(padding = 0.dp) {
                    var first = true
                    fun divider() = if (first) { first = false; false } else true
                    info.email?.let { email ->
                        if (divider()) MenuDivider()
                        ContactRow(Icons.Default.Email, "Email", email) { openContact(context, "mailto:$email") }
                    }
                    info.phone?.let { phone ->
                        if (divider()) MenuDivider()
                        ContactRow(Icons.Default.Phone, "Tel", phone) { openContact(context, "tel:+" + SupportInfo.digits(phone)) }
                    }
                    info.whatsapp?.let { wa ->
                        if (divider()) MenuDivider()
                        ContactRow(ChatIcon, "WhatsApp", wa) { openLink(context, "https://wa.me/" + SupportInfo.digits(wa)) }
                    }
                }
                info.address?.let { address ->
                    SectionLabel("Address")
                    InfoCard { VenueLink(address) }
                }
            }
            else -> LoadingCard("Loading contacts…")
        }
    }
}

// ---------------------------------------------------------------- Trainer credentials (Account)

/** The trainer's Account sections: badges, the three toggles, domain expertise and skill tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainerCredentialsSections(vm: LmsViewModel) {
    LaunchedEffect(vm.role) { if (vm.credentialsState !is Load.Ready) vm.loadCredentials() }
    SectionLabel("Trainer credentials")
    when (val state = vm.credentialsState) {
        is Load.Ready -> {
            val creds = state.value
            InfoCard(Modifier.testTag("trainer-credentials")) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (creds.badges.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            creds.badges.forEach { CredentialBadge(it) }
                        }
                    }
                    CredentialToggle(vm, creds, "ACLP", "Advanced Certificate in Learning and Performance", "aclp", creds.aclp)
                    CredentialToggle(vm, creds, "DDDPL", "Diploma in Design and Development of Learning for Performance", "dddpl", creds.dddpl)
                    CredentialToggle(vm, creds, "TAEPP", "Training and Adult Education Professional Pathway", "taepp", creds.taepp)
                    vm.credentialsProblem?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                }
            }
            Text(
                "Turn on the credentials you hold. They show as badges on your profile.", Modifier.padding(horizontal = 4.dp),
                fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TagSection(vm, "Domain expertise", "Add a domain, e.g. AI, Cybersecurity", "areasOfExpertise", creds.areasOfExpertise)
            TagSection(vm, "Skill tags", "Add a skill, e.g. Python, Power BI", "skillsTags", creds.skillsTags)
        }
        is Load.Failed -> InfoCard { Text(state.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else -> LoadingCard("Loading your credentials…")
    }
}

/** A credential badge, e.g. ACLP. */
@Composable
private fun CredentialBadge(text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(LocalLmsColors.current.brandFill).padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$text certified" }.testTag("badge-$text"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(SealIcon, null, Modifier.size(14.dp), tint = Color.White)
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun CredentialToggle(vm: LmsViewModel, @Suppress("UNUSED_PARAMETER") creds: TrainerCredentials, title: String, subtitle: String, field: String, on: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = on, onCheckedChange = { vm.setCredential(field, it) }, modifier = Modifier.testTag("credential-$field"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSection(vm: LmsViewModel, title: String, prompt: String, field: String, tags: List<String>) {
    var text by rememberSaveable(field) { mutableStateOf("") }
    fun add() {
        vm.addCredentialTag(field, text)
        text = ""
    }
    SectionLabel(title)
    InfoCard(Modifier.testTag("tags-$field")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(accentSoft()).clickable(onClickLabel = "Remove $tag") { vm.removeCredentialTag(field, tag) }
                                .padding(horizontal = 10.dp, vertical = 5.dp).semantics(mergeDescendants = true) { contentDescription = "Remove $tag" },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(tag, fontSize = 14.sp)
                            Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it.take(60) }, placeholder = { Text(prompt) }, singleLine = true, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("add-$field"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }),
                )
                TextButton(onClick = { add() }, enabled = text.isNotBlank(), modifier = Modifier.testTag("add-$field-button")) { Text("Add") }
            }
        }
    }
}
