package com.tertiaryinfotech.ailmstms.ui

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tertiaryinfotech.ailmstms.BuildConfig
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Screen
import com.tertiaryinfotech.ailmstms.data.ClassSession
import com.tertiaryinfotech.ailmstms.data.SGT
import com.tertiaryinfotech.ailmstms.data.ThemeMode
import com.tertiaryinfotech.ailmstms.data.isSafeLink
import com.tertiaryinfotech.ailmstms.data.prettyDay
import java.net.URLEncoder
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(vm: LmsViewModel, onCourses: () -> Unit, onCalendar: () -> Unit) {
    val colors = LocalLmsColors.current
    val upcoming = vm.dashboard.sessions.filter { it.endsAt > Instant.now() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Box(Modifier.fillMaxWidth().background(colors.headerBrush, RoundedCornerShape(24.dp)).padding(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(vm.role.uppercase() + " SPACE", color = colors.onHeaderMuted, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Hello, ${vm.profile?.name?.substringBefore(' ').orEmpty()}",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onHeader,
                    )
                    Text(vm.userProfile?.academy?.takeIf { it.isNotBlank() } ?: "Tertiary Infotech Academy", color = colors.onHeaderMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val chip = AssistChipDefaults.assistChipColors(labelColor = colors.onHeader)
                        val border = AssistChipDefaults.assistChipBorder(true, borderColor = colors.onHeaderMuted)
                        AssistChip(onClick = onCourses, label = { Text("${vm.dashboard.courses.size} courses") }, colors = chip, border = border)
                        AssistChip(onClick = onCalendar, label = { Text("${upcoming.size} upcoming") }, colors = chip, border = border)
                    }
                }
            }
        }
        item { Heading("Next on your calendar") }
        if (upcoming.isEmpty()) item { EmptyCard("No upcoming classes", "Your assigned classes will appear here. Refresh to check for changes.") }
        items(upcoming.take(3), key = { it.id }) { SessionCard(it, onOpen = { vm.openClass(it.courseId) }) }
        item { ActionRow(Icons.AutoMirrored.Filled.List, "Your classes", "Courseware, attendance, assessments and feedback", onClick = onCourses) }
        item { Text("Class times are shown in Singapore time (SGT).", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun CoursesScreen(vm: LmsViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val courses = vm.dashboard.courses
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("My classes") }
        item {
            OutlinedTextField(
                query, { query = it }, label = { Text("Search courses") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        if (courses.isEmpty()) {
            item {
                if (vm.busy) LoadingCard("Loading your classes…")
                else EmptyCard("No classes yet", "Only classes you are enrolled in or assigned to teach appear here.")
            }
        }
        val shown = courses.filter { it.title.contains(query, true) || it.code.contains(query, true) }
        if (courses.isNotEmpty() && shown.isEmpty()) item { EmptyCard("No match", "No class matches \"$query\".") }
        items(shown, key = { it.id }) { course ->
            Card(onClick = { vm.openClass(course.id) }) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(course.code, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (course.classStatus.isNotBlank()) StatusChip(course.classStatus, statusTone(course.classStatus))
                    }
                    Text(course.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(dateRange(course.start, course.end), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Open class  →", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

fun dateRange(start: String, end: String): String {
    val a = prettyDay(start)
    val b = prettyDay(end)
    return if (b.isBlank() || a == b) a else "$a – $b"
}

fun statusTone(status: String): Tone = when (status.trim().lowercase()) {
    "confirmed", "completed", "ongoing", "in progress" -> Tone.Good
    "cancelled", "canceled" -> Tone.Bad
    else -> Tone.Neutral
}

@Composable
fun CalendarScreen(vm: LmsViewModel) {
    val sessions = vm.dashboard.sessions
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.now(SGT).toString()) }
    var chosenValue by rememberSaveable { mutableStateOf<String?>(null) }
    val month = YearMonth.parse(monthValue)
    val filtered = sessions.filter { YearMonth.from(it.date) == month && (chosenValue == null || it.date.toString() == chosenValue) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("Class calendar") }
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { monthValue = month.minusMonths(1).toString(); chosenValue = null }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
                        }
                        Text(
                            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                        )
                        IconButton(onClick = { monthValue = month.plusMonths(1).toString(); chosenValue = null }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                        }
                    }
                    Row {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                            Text(it, Modifier.weight(1f).padding(vertical = 8.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val offset = month.atDay(1).dayOfWeek.value - 1
                    (0 until (offset + month.lengthOfMonth() + 6) / 7).forEach { week ->
                        Row {
                            (0..6).forEach { weekday ->
                                val day = week * 7 + weekday - offset + 1
                                Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                    if (day in 1..month.lengthOfMonth()) {
                                        val date = month.atDay(day)
                                        val has = sessions.any { it.date == date }
                                        val picked = chosenValue == date.toString()
                                        TextButton(
                                            onClick = { chosenValue = if (picked) null else date.toString() },
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.textButtonColors(
                                                containerColor = if (picked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                contentColor = if (picked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            ),
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(day.toString(), fontWeight = if (has) FontWeight.Bold else FontWeight.Normal)
                                                if (has) Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (chosenValue == null) "Classes this month" else prettyDay(chosenValue!!), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { monthValue = YearMonth.now(SGT).toString(); chosenValue = null }) { Text("Today") }
            }
        }
        if (filtered.isEmpty()) item { EmptyCard("No classes scheduled", "Select another date or month to explore your calendar.") }
        items(filtered, key = { it.id }) { SessionCard(it, onOpen = { vm.openClass(it.courseId) }) }
    }
}

/** One class session. Tapping the card opens the class it belongs to. */
@Composable
fun SessionCard(session: ClassSession, onOpen: () -> Unit) {
    val context = LocalContext.current
    Card(onClick = onOpen) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(session.whenText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(session.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                listOf(session.subtitle, session.format, session.venue).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, session.title)
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, session.startsAt.toEpochMilli())
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, session.endsAt.toEpochMilli())
                            .putExtra(CalendarContract.Events.EVENT_LOCATION, session.venue)
                            .putExtra(CalendarContract.Events.EVENT_TIMEZONE, "Asia/Singapore")
                        runCatching { context.startActivity(intent) }
                            .onFailure { Toast.makeText(context, "No calendar app is installed.", Toast.LENGTH_LONG).show() }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add to calendar")
                }
                if (isSafeLink(session.meeting)) TextButton(onClick = { openLink(context, session.meeting) }) { Text("Join class") }
                Spacer(Modifier.weight(1f))
                Text("Open class", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ThemePicker(vm: LmsViewModel) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = vm.themeMode == mode, onClick = { vm.chooseTheme(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
            ) { Text(mode.label) }
        }
    }
}

@Composable
fun AccountScreen(vm: LmsViewModel, onNotifications: (Boolean) -> Unit) {
    val context = LocalContext.current
    var feedbackTitle by rememberSaveable { mutableStateOf("") }
    var feedback by rememberSaveable { mutableStateOf("") }
    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Heading("Your account") }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(vm.profile?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Text(vm.profile?.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.profile?.roles?.forEach { r ->
                            FilterChip(selected = vm.role == r, onClick = { vm.switchRole(r) }, label = { Text(r.replaceFirstChar(Char::titlecase)) }, enabled = !vm.busy)
                        }
                    }
                }
            }
        }
        item { ActionRow(Icons.Default.Edit, "Edit profile", "Name, contact details and appearance") { vm.openEditProfile() } }
        item { ActionRow(Icons.Default.Lock, "Change password", "Requires your current password") { vm.formError = null; vm.stack.add(Screen.ChangePassword) } }
        item {
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Appearance")
                    ThemePicker(vm)
                    Text("System follows your device setting. Your choice is saved to your profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null)
                        Text("Class reminders", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
                        Switch(checked = vm.pushEnabled, onCheckedChange = onNotifications, enabled = !vm.busy)
                    }
                    if (vm.isDemo) Text("In demo, enabling reminders shows a sample notification. Live accounts receive scheduled class reminders.")
                    Text(
                        "Reminders 3 days and 1 day before each class, and at 8:00 AM Singapore time on the day of class. " +
                            "Delivery needs notification permission and may be a few minutes late.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = openSettings) { Text("Open notification settings") }
                }
            }
        }
        item { Heading("Help & feedback") }
        item { OutlinedTextField(feedbackTitle, { feedbackTitle = it }, label = { Text("Feedback title") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(feedback, { feedback = it }, label = { Text("How can we help?") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
        item {
            OutlinedButton(
                onClick = { openLink(context, "$SUPPORT_WHATSAPP?text=" + URLEncoder.encode("$APP_NAME Android feedback\n$feedbackTitle\n$feedback", "UTF-8")) },
                enabled = feedback.isNotBlank(),
            ) { Text("Send via WhatsApp") }
        }
        item { Heading("About $APP_NAME") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your classes, courseware, attendance and assessments, connected to Tertiary Infotech Academy's training management system.")
                Text("Tertiary Infotech Academy Pte. Ltd.", Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Column {
                TextButton(onClick = { openLink(context, PRIVACY_URL) }) { Text("Privacy policy") }
                TextButton(onClick = { openLink(context, "https://www.tertiaryinfotech.com") }) { Text("Developer website") }
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sales@tertiarycourses.com.sg"))
                        .putExtra(Intent.EXTRA_SUBJECT, "$APP_NAME account deletion request")
                    runCatching { context.startActivity(intent) }
                }) { Text("Request account deletion") }
                Text(
                    "Contact the training provider to request deletion. Training records subject to legal retention requirements may be retained.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { OutlinedButton(onClick = vm::logout, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") } }
    }
}
