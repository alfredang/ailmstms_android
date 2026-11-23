package com.tertiaryinfotech.ailmstms.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tertiaryinfotech.ailmstms.BuildConfig
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Screen
import com.tertiaryinfotech.ailmstms.data.EMPLOYMENT_OPTIONS
import com.tertiaryinfotech.ailmstms.data.ProfilePatch
import com.tertiaryinfotech.ailmstms.data.Roles
import com.tertiaryinfotech.ailmstms.data.UserProfile
import com.tertiaryinfotech.ailmstms.data.isSafeLink
import java.net.URLEncoder

private fun digits(phone: String, plus: Boolean = false): String =
    (if (plus && phone.trim().startsWith("+")) "+" else "") + phone.filter(Char::isDigit)

/** wa.me needs the country code: a bare 8-digit Singapore number gets 65. */
private fun whatsAppNumber(phone: String): String = digits(phone).let { if (it.length == 8) "65$it" else it }

@Composable
private fun Footnote(text: String) {
    Text(text, Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The one Account page: who you are and how to reach you, the editable profile, appearance,
 * password, reminders, support, then sign out and account deletion. There is no separate
 * edit screen.
 */
@Composable
fun AccountScreen(vm: LmsViewModel, onNotifications: (Boolean) -> Unit) {
    val context = LocalContext.current
    val colors = LocalLmsColors.current
    val saved = vm.userProfile
    val name = saved?.fullName?.takeIf { it.isNotBlank() } ?: vm.profile?.name.orEmpty()
    val email = saved?.email?.takeIf { it.isNotBlank() } ?: vm.profile?.email.orEmpty()
    var feedbackTitle by rememberSaveable { mutableStateOf("") }
    var feedback by rememberSaveable { mutableStateOf("") }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    // The cached profile shows at once; this brings it up to date (and retries after a failed load).
    LaunchedEffect(Unit) { vm.loadProfile() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp).testTag("account"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Who you are.
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarView(saved?.pictureUrl, name, 104.dp)
            Text(name.ifBlank { "My account" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 28.sp)
            StatusChip(Roles.label(vm.role), Tone.Info)
            saved?.academy?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        if (vm.canSwitchRole) {
            InfoCard(padding = 0.dp) { ViewAsMenu(vm, boxed = false) }
            Footnote("Owner account only: see the app as each role. The server still checks every request.")
        }

        SectionLabel("Contact")
        InfoCard(padding = 0.dp) {
            ContactRow(Icons.Default.Email, "Email", email) { openContact(context, "mailto:$email") }
            val phone = saved?.phone.orEmpty()
            if (digits(phone).length >= 8) {
                MenuDivider()
                ContactRow(Icons.Default.Phone, "Phone", phone) { openContact(context, "tel:" + digits(phone, plus = true)) }
                MenuDivider()
                ContactRow(ChatIcon, "WhatsApp", phone) { openLink(context, "https://wa.me/" + whatsAppNumber(phone)) }
            }
            val link = saved?.linkedinUrl.orEmpty()
            if (isSafeLink(link)) {
                MenuDivider()
                ContactRow(LinkIcon, "LinkedIn", link.removePrefix("https://")) { openLink(context, link) }
            }
        }

        SectionLabel("Your details")
        if (saved == null) ErrorCard("Your profile could not be loaded.") { vm.loadProfile() } else ProfileFields(vm, saved)

        SectionLabel("Appearance")
        InfoCard {
            ThemePicker(vm)
            Spacer(Modifier.height(8.dp))
            Text(
                "System follows your phone's light or dark setting. Your choice is saved to your profile.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel("Security")
        InfoCard(padding = 0.dp) {
            MenuRow(Icons.Default.Lock, "Change password") { vm.formError = null; vm.stack.add(Screen.ChangePassword) }
        }

        SectionLabel("Preferences")
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Class reminders", Modifier.weight(1f).padding(horizontal = 14.dp), fontSize = 16.sp)
                Switch(checked = vm.pushEnabled, onCheckedChange = onNotifications, enabled = !vm.busy)
            }
            Spacer(Modifier.height(6.dp))
            if (vm.isDemo) {
                Text("In demo, enabling reminders shows a sample notification. Live accounts receive scheduled class reminders.", fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                "Reminders 3 days and 1 day before each class, and at 8:00 AM Singapore time on the day of class. " +
                    "Delivery needs notification permission and may be a few minutes late.",
                fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = openSettings, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Open notification settings") }
        }

        SectionLabel("Help & feedback")
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(feedbackTitle, { feedbackTitle = it }, label = { Text("Feedback title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(feedback, { feedback = it }, label = { Text("How can we help?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = { openLink(context, "$SUPPORT_WHATSAPP?text=" + URLEncoder.encode("$APP_NAME Android feedback\n$feedbackTitle\n$feedback", "UTF-8")) },
                    enabled = feedback.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Send via WhatsApp") }
            }
        }
        InfoCard(padding = 0.dp) {
            MenuRow(Icons.Default.Info, "Privacy policy", external = true) { openLink(context, PRIVACY_URL) }
            MenuDivider()
            MenuRow(OpenExternalIcon, "Developer website", external = true) { openLink(context, "https://www.tertiaryinfotech.com") }
        }

        SectionLabel("About $APP_NAME")
        InfoCard {
            Text("Your classes, courseware, attendance and assessments, connected to Tertiary Infotech Academy's training management system.", fontSize = 14.sp, lineHeight = 20.sp)
            Text("Tertiary Infotech Academy Pte. Ltd.", Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(10.dp))
        InfoCard(padding = 0.dp) {
            MenuRow(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", tint = MaterialTheme.colorScheme.primary, chevron = false) { if (!vm.busy) confirmSignOut = true }
        }
        Spacer(Modifier.height(10.dp))
        InfoCard(padding = 0.dp) {
            MenuRow(Icons.Default.Delete, "Delete account", tint = MaterialTheme.colorScheme.error, chevron = false) { confirmDelete = true }
        }
        Footnote("Sends a deletion request to your training provider. Training records subject to legal retention requirements may be retained.")
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out of $APP_NAME?") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.logout() }, modifier = Modifier.testTag("confirmSignOut")) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("This opens an email to your training provider asking for your $APP_NAME account to be deleted. Send it from the address you sign in with.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sales@tertiarycourses.com.sg"))
                        .putExtra(Intent.EXTRA_SUBJECT, "$APP_NAME account deletion request")
                    runCatching { context.startActivity(intent) }
                        .onFailure { vm.notice = "No email app is installed. Write to sales@tertiarycourses.com.sg to request deletion." }
                }) { Text("Request deletion", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** The fields the profile API accepts: name and phone for everyone, company and employment for learners, LinkedIn for trainers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileFields(vm: LmsViewModel, saved: UserProfile) {
    // What the account holds decides the fields, whichever role is being viewed: the server saves each to its own record.
    val roles = vm.profile?.roles.orEmpty()
    val learner = Roles.LEARNER in roles
    val trainer = Roles.TRAINER in roles
    // Keyed on the profile id so the fields start from the saved values exactly once.
    var fullName by rememberSaveable(saved.id) { mutableStateOf(saved.fullName) }
    var phone by rememberSaveable(saved.id) { mutableStateOf(saved.phone) }
    var company by rememberSaveable(saved.id) { mutableStateOf(saved.company) }
    var employment by rememberSaveable(saved.id) { mutableStateOf(saved.employmentStatus) }
    var linkedin by rememberSaveable(saved.id) { mutableStateOf(saved.linkedinUrl) }

    val problem = when {
        fullName.trim().length < 2 -> "Please enter your full name."
        phone.isNotBlank() && !Regex("^[+0-9 ()-]{8,30}$").matches(phone.trim()) -> "Please enter a valid phone number."
        trainer && linkedin.isNotBlank() && !isSafeLink(linkedin.trim()) -> "The LinkedIn link must start with https://"
        else -> null
    }
    val field = RoundedCornerShape(12.dp)
    InfoCard(Modifier.testTag("profile")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                fullName, { fullName = it.take(200) }, label = { Text("Full name") }, singleLine = true, shape = field, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            OutlinedTextField(
                phone, { phone = it.take(30) }, label = { Text("Phone") }, singleLine = true, shape = field, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            if (learner) {
                OutlinedTextField(company, { company = it.take(200) }, label = { Text("Company") }, singleLine = true, shape = field, modifier = Modifier.fillMaxWidth())
                Column {
                    Text("Employment status", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EMPLOYMENT_OPTIONS.forEach { option ->
                            FilterChip(selected = employment == option, onClick = { employment = option }, label = { Text(option) })
                        }
                    }
                }
            }
            if (trainer) {
                OutlinedTextField(
                    linkedin, { linkedin = it.take(300) }, label = { Text("LinkedIn profile link") }, singleLine = true, shape = field, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            (vm.formError ?: problem)?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    vm.saveProfile(
                        ProfilePatch(
                            fullName = fullName.trim(),
                            phone = phone.trim(),
                            company = if (learner) company.trim() else null,
                            employmentStatus = if (learner) employment.takeIf { it.isNotBlank() } else null,
                            linkedinUrl = if (trainer) linkedin.trim() else null,
                        ),
                    )
                },
                enabled = problem == null && !vm.working,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("saveProfile"),
            ) { Text(if (vm.working) "Saving…" else "Save changes", fontSize = 16.sp) }
        }
    }
    Footnote(
        "Your phone number is also used for WhatsApp. Your sign-in email is ${saved.email}; contact your training provider to change it." +
            if (vm.isDemo) " Demo: changes stay on this device and are discarded when you leave the demo." else "",
    )
}
