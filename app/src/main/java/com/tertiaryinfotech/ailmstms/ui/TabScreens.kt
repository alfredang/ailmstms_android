package com.tertiaryinfotech.ailmstms.ui

import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.data.ClassSession
import com.tertiaryinfotech.ailmstms.data.Roles
import com.tertiaryinfotech.ailmstms.data.SGT
import com.tertiaryinfotech.ailmstms.data.ThemeMode
import com.tertiaryinfotech.ailmstms.data.isSafeLink
import com.tertiaryinfotech.ailmstms.data.prettyDay
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Top of My Classes: who is signed in, a link to the profile and, for the owner account only,
 * the "Viewing as" role switcher.
 */
@Composable
fun HomeHeader(vm: LmsViewModel, onProfile: () -> Unit) {
    val colors = LocalLmsColors.current
    var roleMenu by remember { mutableStateOf(false) }
    val name = vm.userProfile?.fullName?.takeIf { it.isNotBlank() } ?: vm.profile?.name.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarView(vm.userProfile?.pictureUrl, name, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Welcome", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    name.substringBefore(' ').ifBlank { "back" }, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(accentSoft()).clickable(onClick = onProfile)
                    .padding(horizontal = 12.dp, vertical = 8.dp).testTag("myProfileLink"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.AccountCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("My Profile", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
        if (vm.canSwitchRole) {
            Box {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.brandFill).clickable(enabled = !vm.busy) { roleMenu = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp).testTag("viewAsPicker"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(SwapIcon, null, Modifier.size(18.dp), tint = Color.White)
                    Text("Viewing as ${Roles.label(vm.role)}", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = Color.White)
                }
                DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }, containerColor = colors.card) {
                    vm.availableRoles.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(Roles.label(role)) },
                            trailingIcon = { if (role == vm.role) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary) },
                            onClick = { roleMenu = false; vm.switchRole(role) },
                            modifier = Modifier.testTag("viewAs-$role"),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The home tab for both roles: the welcome header, what is next on the calendar (the old Home
 * tab) and every class with search (the old Courses tab).
 */
@Composable
fun MyClassesScreen(vm: LmsViewModel, onProfile: () -> Unit, onCalendar: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val trainer = vm.role == Roles.TRAINER
    val courses = vm.dashboard.courses
    val upcoming = vm.dashboard.sessions.filter { it.endsAt > Instant.now() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("my-classes")) {
        item { HomeHeader(vm, onProfile) }

        item {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionLabel("Next on your calendar", Modifier.weight(1f))
                if (upcoming.isNotEmpty()) {
                    Text(
                        "${upcoming.size} upcoming · See calendar", Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onCalendar).padding(horizontal = 4.dp, vertical = 2.dp),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (upcoming.isEmpty()) {
            item {
                if (vm.busy && courses.isEmpty()) LoadingCard("Loading your classes…")
                else EmptyCard("No upcoming classes", "Your assigned classes will appear here. Refresh to check for changes.")
            }
        }
        items(upcoming.take(3), key = { "next-" + it.id }) { SessionCard(it, onOpen = { vm.openClass(it.courseId) }) }

        item { SectionLabel(if (trainer) "Classes you teach" else "My classes") }
        if (courses.isNotEmpty()) {
            item {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Search classes") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                )
            }
        }
        if (courses.isEmpty() && !vm.busy) {
            item {
                EmptyCard(
                    "No classes yet",
                    if (trainer) "Only classes you are assigned to teach appear here." else "Only classes you are enrolled in appear here.",
                )
            }
        }
        val shown = courses.filter { it.title.contains(query, true) || it.code.contains(query, true) }
        if (courses.isNotEmpty() && shown.isEmpty()) item { EmptyCard("No match", "No class matches \"$query\".") }
        items(shown, key = { it.id }) { course ->
            AppCard(onClick = { vm.openClass(course.id) }) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(course.code, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        if (course.classStatus.isNotBlank()) StatusChip(course.classStatus, statusTone(course.classStatus))
                    }
                    Text(course.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dateRange(course.start, course.end), Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Open class", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Text(
                "Class times are shown in Singapore time (SGT).", Modifier.padding(horizontal = 4.dp),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("Class calendar") }
        item {
            AppCard {
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
    AppCard(onClick = onOpen) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.whenText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(session.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            Text(
                listOf(session.subtitle, session.format, session.venue).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    contentPadding = PaddingValues(start = 0.dp, end = 8.dp),
                ) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add to calendar")
                }
                if (isSafeLink(session.meeting)) TextButton(onClick = { openLink(context, session.meeting) }) { Text("Join class") }
                Spacer(Modifier.weight(1f))
                Text("Open class", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ThemePicker(vm: LmsViewModel) {
    val colors = LocalLmsColors.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = vm.themeMode == mode, onClick = { vm.chooseTheme(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.brandFill, activeContentColor = Color.White, activeBorderColor = colors.brandFill,
                    inactiveContainerColor = colors.field, inactiveContentColor = MaterialTheme.colorScheme.onSurface, inactiveBorderColor = colors.cardBorder,
                ),
            ) { Text(mode.label, fontWeight = if (vm.themeMode == mode) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}
