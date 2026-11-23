package com.tertiaryinfotech.ailmstms.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.data.Roles
import com.tertiaryinfotech.ailmstms.data.SGT
import com.tertiaryinfotech.ailmstms.data.StaffClass
import com.tertiaryinfotech.ailmstms.data.StaffCourse
import com.tertiaryinfotech.ailmstms.data.StaffTrainer
import com.tertiaryinfotech.ailmstms.data.trainerMessage
import com.tertiaryinfotech.ailmstms.data.whatsAppUrl
import com.tertiaryinfotech.ailmstms.data.longDay
import com.tertiaryinfotech.ailmstms.data.fundingExpiresText
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tertiaryinfotech.ailmstms.data.longRange
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// Admin and Developer pages, from GET /api/mobile/staff (read-only; the server checks the role).
//   Admin:     Home = Current and Upcoming classes; Calendar = every class by day; Courses tab.
//   Developer: Home and Courses tab = the course catalogue.

// ---------------------------------------------------------------- Home (first tab)

/** The staff home: the Welcome card, then the admin's classes or the developer's course catalogue. */
@Composable
fun StaffHomeScreen(vm: LmsViewModel, onProfile: () -> Unit) {
    var sheet by remember { mutableStateOf<StaffClass?>(null) }
    // Admin lists.
    var current by rememberSaveable { mutableStateOf(true) }
    var classQuery by rememberSaveable { mutableStateOf("") }
    var hideCancelled by rememberSaveable { mutableStateOf(true) }
    // Catalogue.
    var filter by rememberSaveable { mutableStateOf(CatalogueFilter.ALL) }
    var courseQuery by rememberSaveable { mutableStateOf("") }
    val admin = vm.role == Roles.ADMIN

    LaunchedEffect(vm.role) { if (admin) { if (vm.adminClasses is Load.Idle) vm.loadAdminClasses() } else if (vm.catalogue is Load.Idle) vm.loadCatalogue() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("staff-home")) {
        item(key = "header") { HomeHeader(vm, onProfile) }
        if (admin) {
            adminClasses(vm, current, { current = it }, classQuery, { classQuery = it }, hideCancelled, { hideCancelled = it }) { sheet = it }
        } else {
            catalogue(vm, filter, { filter = it }, courseQuery, { courseQuery = it })
        }
    }
    sheet?.let { StaffClassSheet(vm, it) { sheet = null } }
}

private fun LazyListScope.adminClasses(
    vm: LmsViewModel,
    current: Boolean,
    onBucket: (Boolean) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    hideCancelled: Boolean,
    onHide: (Boolean) -> Unit,
    onOpen: (StaffClass) -> Unit,
) {
    when (val state = vm.adminClasses) {
        Load.Idle, Load.Loading -> item(key = "loading") { LoadingCard("Loading classes…") }
        is Load.Failed -> item(key = "failed") { ErrorCard(state.message) { vm.loadAdminClasses() } }
        is Load.Ready -> {
            val classes = state.value
            val today = LocalDate.now(SGT)
            fun list(running: Boolean) = classes.filter { c ->
                !(hideCancelled && c.cancelled) && (if (running) c.covers(today) else c.start?.isAfter(today) == true) && c.matches(query)
            }
            val running = list(true)
            val upcoming = list(false)
            item(key = "tiles") {
                TileRow(
                    Triple("Running today", "${running.size}", false),
                    Triple("Next 30 days", "${upcoming.size}", false),
                    Triple("Learners today", "${running.sumOf { it.learners }}", true),
                )
            }
            item(key = "bucket") {
                Segments(listOf("Current (${running.size})", "Upcoming (${upcoming.size})"), if (current) 0 else 1, "bucket") { onBucket(it == 0) }
            }
            item(key = "search") { SearchField(query, "Search course, run ID or trainer", onQuery) }
            item(key = "hide") { HideCancelled(hideCancelled, onHide) }
            val shown = if (current) running else upcoming
            if (shown.isEmpty()) {
                item(key = "empty") {
                    EmptyCard(
                        if (current) "No classes running today" else "No upcoming classes",
                        if (classes.isEmpty()) "Tap Refresh at the top to check again." else "Try another search.",
                    )
                }
            }
            items(shown, key = { "class-" + it.id }) { StaffClassRow(it) { onOpen(it) } }
        }
    }
}

@Composable
private fun Segments(labels: List<String>, selected: Int, tag: String, onPick: (Int) -> Unit) {
    val colors = LocalLmsColors.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag(tag)) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = selected == i, onClick = { onPick(i) }, shape = SegmentedButtonDefaults.itemShape(i, labels.size), icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.brandFill, activeContentColor = Color.White, activeBorderColor = colors.brandFill,
                    inactiveContainerColor = colors.field, inactiveContentColor = MaterialTheme.colorScheme.onSurface, inactiveBorderColor = colors.cardBorder,
                ),
                modifier = Modifier.testTag("$tag-$i"),
            ) {
                // Four segments (All / With classes / Expiring / Expired) wrap rather than cut a label short.
                val crowded = labels.size > 3
                Text(
                    label, fontSize = if (crowded) 12.sp else 13.sp, maxLines = if (crowded) 2 else 1, lineHeight = 14.sp,
                    textAlign = TextAlign.Center, fontWeight = if (selected == i) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, hint: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        query, { onQuery(it.take(100)) }, placeholder = { Text(hint) }, leadingIcon = { Icon(Icons.Default.Search, null) },
        modifier = Modifier.fillMaxWidth().testTag("staff-search"), singleLine = true, shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun HideCancelled(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Hide cancelled classes", Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.testTag("hide-cancelled"))
    }
}

/** One class in an admin list; tap for the summary. */
@Composable
private fun StaffClassRow(item: StaffClass, onOpen: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(Modifier.fillMaxWidth().testTag("staff-class-${item.id}"), onClick = onOpen) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.code.ifBlank { "Course" }, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                StatusChip(item.classStatus.ifBlank { "Pending" }, classStatusTone(item.classStatus))
            }
            Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            SmallLine(Icons.Default.DateRange, longRange(item.startDate, item.endDate))
            SmallLine(
                Icons.Default.Person, if (item.trainers.isEmpty()) "No trainer assigned" else item.trainers.joinToString(", "),
                if (item.trainers.isEmpty()) LocalLmsColors.current.warning else secondary,
            )
            Text(listOf("${item.learners} learners", item.format).filter { it.isNotBlank() }.joinToString("  ·  "), fontSize = 13.sp, color = secondary)
        }
    }
}

@Composable
private fun SmallLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Text(text, fontSize = 13.sp, color = color)
    }
}

/** A class's summary for the admin (read-only). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffClassSheet(vm: LmsViewModel, item: StaffClass, onDone: () -> Unit) {
    var picking by remember(item.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        if (picking) {
            TrainerWhatsAppPicker(vm, item) { picking = false }
            return@ModalBottomSheet
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding().testTag("staff-class-sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
            val codeLine = listOfNotNull(item.code.takeIf { it.isNotBlank() }, item.runCode.takeIf { it.isNotBlank() }?.let { "Run $it" }).joinToString("  ·  ")
            if (codeLine.isNotBlank()) Text(codeLine, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.format.isNotBlank()) StatusChip(item.format, Tone.Info)
                StatusChip(item.classStatus.ifBlank { "Pending" }, classStatusTone(item.classStatus))
            }
            SectionLabel("Class")
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoRow("Dates", longRange(item.startDate, item.endDate))
                    InfoRow("Learners", "${item.learners}")
                    InfoRow(if (item.trainers.size > 1) "Trainers" else "Trainer", if (item.trainers.isEmpty()) "Not assigned" else item.trainers.joinToString("\n"))
                    if (item.venue.isNotBlank()) VenueLink(item.venue)
                    if (item.hasMeetingLink) InfoRow("Virtual class link", "Set")
                }
            }
            SectionLabel("Trainer")
            InfoCard {
                LinkRow(ChatIcon, "WhatsApp a trainer", external = false, tag = "whatsapp-trainer") { picking = true }
            }
            Text(
                "Opens WhatsApp with a message asking the trainer to take this class. Record the assignment on lms-tms.tertiaryinfotech.com, where TPGateway is updated.",
                fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

// ---------------------------------------------------------------- WhatsApp a trainer

/** Pick a trainer, then WhatsApp opens on this phone with a message about the class. */
@Composable
private fun TrainerWhatsAppPicker(vm: LmsViewModel, item: StaffClass, onBack: () -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable(item.id) { mutableStateOf("") }
    LaunchedEffect(Unit) { if (vm.staffTrainers !is Load.Ready) vm.loadTrainers() }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().testTag("whatsapp-picker")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back") }
            Text("WhatsApp a trainer", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { SearchField(query, "Search trainers") { query = it } }
        when (val state = vm.staffTrainers) {
            Load.Idle, Load.Loading -> LoadingCard("Loading trainers…")
            is Load.Failed -> Box(Modifier.padding(20.dp)) { ErrorCard(state.message) { vm.loadTrainers() } }
            is Load.Ready -> {
                val mine = state.value.filter { it.assignedTo(item) && it.matches(query) }
                val others = state.value.filter { !it.assignedTo(item) && it.matches(query) }
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mine.isNotEmpty()) {
                        item(key = "mine") { SectionLabel("Assigned to this class") }
                        items(mine, key = { "m-" + it.email + it.name }) { TrainerRow(it) { openTrainer(context, it, item) } }
                    }
                    item(key = "all") { SectionLabel("All trainers (${others.size})") }
                    items(others, key = { "a-" + it.email + it.name }) { TrainerRow(it) { openTrainer(context, it, item) } }
                }
            }
        }
    }
}

private fun openTrainer(context: android.content.Context, trainer: StaffTrainer, item: StaffClass) {
    val phone = trainer.phone ?: return
    whatsAppUrl(phone, trainerMessage(trainer, item))?.let { openLink(context, it) }
}

@Composable
private fun TrainerRow(trainer: StaffTrainer, onClick: () -> Unit) {
    val enabled = trainer.phone != null
    AppCard(Modifier.fillMaxWidth().testTag("trainer-${trainer.name}"), onClick = if (enabled) onClick else null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(trainer.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f))
                Text(trainer.phone ?: "No WhatsApp number on profile", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (enabled) Icon(ChatIcon, "WhatsApp", Modifier.size(20.dp), tint = Color(0xFF25D366))
        }
    }
}

/** A blinking orange alert when course funding ends within 3 months; steady when animations are turned off. */
@Composable
private fun ExpiringBadge(days: Int) {
    val context = LocalContext.current
    val reduceMotion = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val alpha = if (reduceMotion) 1f else {
        val blink = rememberInfiniteTransition(label = "expiring")
        blink.animateFloat(1f, 0.35f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "alpha").value
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(ExpiringOrange).alpha(alpha)
            .semantics(mergeDescendants = true) { contentDescription = "Alert: funding expires in $days days" }
            .padding(horizontal = 10.dp, vertical = 5.dp).testTag("expiring-badge"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(androidx.compose.material.icons.Icons.Default.Warning, null, Modifier.size(14.dp), tint = Color.White)
        Text(fundingExpiresText(days), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

private val ExpiringOrange = Color(0xFFF57C00)

// ---------------------------------------------------------------- Admin calendar (second tab)

/** Every academy class by day: a month calendar with dots, then the chosen day's classes. Loads a month at a time. */
@Composable
fun AdminCalendarScreen(vm: LmsViewModel) {
    var dayText by rememberSaveable { mutableStateOf(LocalDate.now(SGT).toString()) }
    var hideCancelled by rememberSaveable { mutableStateOf(true) }
    var sheet by remember { mutableStateOf<StaffClass?>(null) }
    val day = LocalDate.parse(dayText)
    val month = YearMonth.from(day)
    LaunchedEffect(month) { vm.loadCalendar(month) }
    val shown = vm.calendarClasses.filter { !(hideCancelled && it.cancelled) }
    val dayClasses = shown.filter { it.covers(day) }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("admin-calendar")) {
        item(key = "heading") { Heading("Academy Calendar") }
        item(key = "month") {
            AppCard {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { dayText = month.minusMonths(1).atDay(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                        Text(
                            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                        )
                        IconButton(onClick = { dayText = month.plusMonths(1).atDay(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
                    }
                    Row {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                            Text(it, Modifier.weight(1f).padding(vertical = 6.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val offset = month.atDay(1).dayOfWeek.value - 1
                    val today = LocalDate.now(SGT)
                    (0 until (offset + month.lengthOfMonth() + 6) / 7).forEach { week ->
                        Row {
                            (0..6).forEach { weekday ->
                                val n = week * 7 + weekday - offset + 1
                                Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                                    if (n in 1..month.lengthOfMonth()) {
                                        val date = month.atDay(n)
                                        val has = shown.any { it.covers(date) }
                                        val picked = date == day
                                        Column(
                                            Modifier.size(40.dp).clip(CircleShape)
                                                .background(if (picked) LocalLmsColors.current.brandFill else if (date == today) accentSoft() else Color.Transparent)
                                                .clickable { dayText = date.toString() }.testTag("day-$date"),
                                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                "$n", fontSize = 14.sp, fontWeight = if (has || picked) FontWeight.Bold else FontWeight.Normal,
                                                color = if (picked) Color.White else if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Box(
                                                Modifier.size(5.dp).clip(CircleShape)
                                                    .background(if (!has) Color.Transparent else if (picked) Color.White else MaterialTheme.colorScheme.primary),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "day") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (vm.calendarLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("${dayClasses.size} ${if (dayClasses.size == 1) "class" else "classes"}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "hide") { HideCancelled(hideCancelled) { hideCancelled = it } }
        vm.calendarProblem?.let { problem -> item(key = "problem") { ErrorCard(problem) { vm.loadCalendar(month, force = true) } } }
        if (dayClasses.isEmpty() && !vm.calendarLoading) item(key = "empty") { EmptyCard("No classes", "No classes on this day.") }
        items(dayClasses, key = { "cal-" + it.id }) { StaffClassRow(it) { sheet = it } }
    }
    sheet?.let { StaffClassSheet(vm, it) { sheet = null } }
}

// ---------------------------------------------------------------- course catalogue (admin and developer)

enum class CatalogueFilter(val label: String) { ALL("All"), RUNNING("With classes"), EXPIRING("Expiring"), EXPIRED("Expired") }

/** The Admin or Developer third tab: the course catalogue. */
@Composable
fun StaffCoursesScreen(vm: LmsViewModel) {
    var filter by rememberSaveable { mutableStateOf(CatalogueFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(vm.role) { if (vm.catalogue is Load.Idle) vm.loadCatalogue() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("staff-courses")) {
        item(key = "heading") { Heading("Courses") }
        catalogue(vm, filter, { filter = it }, query, { query = it })
    }
}

private fun LazyListScope.catalogue(
    vm: LmsViewModel,
    filter: CatalogueFilter,
    onFilter: (CatalogueFilter) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
) {
    when (val state = vm.catalogue) {
        Load.Idle, Load.Loading -> item(key = "courses-loading") { LoadingCard("Loading courses…") }
        is Load.Failed -> item(key = "courses-failed") { ErrorCard(state.message) { vm.loadCatalogue() } }
        is Load.Ready -> {
            val courses = state.value
            val today = LocalDate.now(SGT)
            val shown = courses.filter { c ->
                when (filter) {
                    CatalogueFilter.ALL -> true
                    CatalogueFilter.RUNNING -> c.upcomingRuns > 0
                    CatalogueFilter.EXPIRING -> c.expiringInDays(today) != null
                    CatalogueFilter.EXPIRED -> c.expired(today)
                } && c.matches(query)
            }
            item(key = "course-tiles") {
                TileRow(
                    Triple("Courses", "${courses.size}", false),
                    Triple("Expiring ≤ 3 mo", "${courses.count { it.expiringInDays(today) != null }}", false),
                    Triple("Expired", "${courses.count { it.expired(today) }}", true),
                )
            }
            item(key = "course-filter") { Segments(CatalogueFilter.entries.map { it.label }, filter.ordinal, "course-filter") { onFilter(CatalogueFilter.entries[it]) } }
            item(key = "course-search") { SearchField(query, "Search title, course code or TSC code", onQuery) }
            item(key = "course-count") { Text("${shown.size} courses", Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(shown, key = { "course-" + it.id }) { CourseRow(it, today) }
        }
    }
}

@Composable
private fun CourseRow(item: StaffCourse, today: LocalDate) {
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    val expired = item.expired(today)
    AppCard(Modifier.fillMaxWidth().testTag("course-${item.id}")) {
        Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.code.ifBlank { "No code" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                if (item.courseType.isNotBlank()) StatusChip(item.courseType, Tone.Info)
                StatusChip(if (expired) "Expired" else "Active", if (expired) Tone.Bad else Tone.Good)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, if (open) "Collapse" else "Expand", Modifier.rotate(if (open) 180f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            item.fundingValidityText?.let {
                SmallLine(SealIcon, it, if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.expiringInDays(today)?.let { ExpiringBadge(it) }
            SmallLine(Icons.Default.DateRange, if (item.upcomingRuns == 0) "No upcoming classes" else "${item.upcomingRuns} upcoming")
            if (open) {
                HorizontalDivider(color = LocalLmsColors.current.cardBorder)
                if (item.tscTitle.isNotBlank()) InfoRow("TSC Title", item.tscTitle)
                if (item.tscCode.isNotBlank()) InfoRow("TSC Code", item.tscCode)
                if (item.trainingHours != null || item.assessmentHours != null) {
                    InfoRow("Duration", "${(item.trainingHours ?: 0.0).toInt()} h training · ${(item.assessmentHours ?: 0.0).toInt()} h assessment")
                }
                if (item.nextRun.isNotBlank()) InfoRow("Next class", longDay(item.nextRun))
            }
        }
    }
}
