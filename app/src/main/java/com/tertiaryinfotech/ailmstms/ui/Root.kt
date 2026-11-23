package com.tertiaryinfotech.ailmstms.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Screen
import com.tertiaryinfotech.ailmstms.data.Roles
import kotlinx.coroutines.launch

/** A bottom tab. [id] stays the same when the set of tabs changes with the role. */
private data class TabSpec(val id: Int, val label: String, val icon: ImageVector)

private const val TAB_CLASSES = 0
private const val TAB_CALENDAR = 1
private const val TAB_THIRD = 2
private const val TAB_FOURTH = 3
private const val TAB_ACCOUNT = 4

/** The tabs for the role being viewed. Developer and Admin: Home, Calendar, Courses, Account. */
private fun tabsFor(role: String): List<TabSpec> = buildList {
    val staff = Roles.isStaff(role)
    add(if (staff) TabSpec(TAB_CLASSES, "Home", Icons.Default.Home) else TabSpec(TAB_CLASSES, "My Classes", SchoolIcon))
    // Developers have no classes to schedule, so no Calendar tab.
    if (role != Roles.DEVELOPER) add(TabSpec(TAB_CALENDAR, "Calendar", Icons.Default.DateRange))
    if (staff) {
        add(TabSpec(TAB_THIRD, "Courses", BooksIcon))
    } else if (role == Roles.TRAINER) {
        add(TabSpec(TAB_THIRD, "Payout", PaymentsIcon))
        add(TabSpec(TAB_FOURTH, "Hours", ClockIcon))
    } else {
        add(TabSpec(TAB_THIRD, "Certificates", CertificateIcon))
        add(TabSpec(TAB_FOURTH, "Support", SupportIcon))
    }
    add(TabSpec(TAB_ACCOUNT, "Account", Icons.Default.AccountCircle))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Root(openCalendar: Boolean, onCalendarOpened: () -> Unit, vm: LmsViewModel) {
    val context = LocalContext.current
    val colors = LocalLmsColors.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val signedIn = vm.profile != null
    val gated = signedIn && vm.mustChangePassword
    val top = vm.stack.lastOrNull()

    LaunchedEffect(openCalendar, signedIn) {
        if (openCalendar && signedIn) {
            vm.closeAll()
            tab = TAB_CALENDAR
            onCalendarOpened()
        }
    }
    LaunchedEffect(signedIn) { if (!signedIn) tab = TAB_CLASSES }
    // Changing role (the owner's switcher) goes back to the home tab of the new role.
    LaunchedEffect(vm.role) { tab = TAB_CLASSES }
    // A material fetched with Download goes to the system share sheet: save, print or send.
    LaunchedEffect(vm.downloaded) {
        vm.downloaded?.let { ready ->
            vm.downloaded = null
            runCatching { shareFile(context, ready.file, ready.title) }.onFailure { vm.notice = "No app is available to save or share this file." }
        }
    }
    LaunchedEffect(vm.notice) {
        vm.notice?.let {
            vm.notice = null
            // Shown from a scope that outlives this effect, which restarts as soon as the notice is cleared.
            scope.launch { snackbar.showSnackbar(it) }
        }
    }
    BackHandler(enabled = top != null) { vm.back() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.setPush(true) }
    val onNotifications: (Boolean) -> Unit = { enabled ->
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            !enabled -> vm.setPush(false)
            needsPermission -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            else -> vm.setPush(true)
        }
    }

    val tabs = tabsFor(vm.role)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (gated) "Set a new password" else top?.title ?: APP_NAME,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (top != null && !gated) IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (signedIn && !gated && top == null) {
                        IconButton(onClick = vm::refresh, enabled = !vm.busy, modifier = Modifier.testTag("refresh")) { Icon(Icons.Default.Refresh, "Refresh") }
                    }
                    if (signedIn && !gated && top is Screen.Pdf) {
                        if (vm.downloading) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = colors.onHeader, strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = { vm.downloadMaterial(top) }, modifier = Modifier.testTag("downloadDocument")) { Icon(DownloadIcon, "Download") }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, titleContentColor = colors.onHeader,
                    navigationIconContentColor = colors.onHeader, actionIconContentColor = colors.onHeader,
                ),
                modifier = Modifier.background(colors.headerBrush),
            )
        },
        bottomBar = {
            if (signedIn && !gated && top == null) {
                NavigationBar(containerColor = colors.card) {
                    tabs.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item.id, onClick = { tab = item.id },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.label, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = accentSoft(),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.testTag("tab-${item.label}"),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.isDemo) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("DEMO · Fictional sample data", Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.pendingQuiz != null && (!signedIn || gated)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Text(
                        if (gated) "Set your new password to open the class quiz." else "Sign in to open the class quiz. It opens right after you sign in.",
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).testTag("quiz-pending"),
                    )
                }
            }
            vm.error?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, Modifier.weight(1f).padding(vertical = 12.dp))
                        IconButton(onClick = vm::dismissError) { Icon(Icons.Default.Close, "Dismiss message") }
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    !signedIn -> LoginScreen(vm)
                    gated -> PasswordScreen(vm, forced = true)
                    top != null -> when (top) {
                        is Screen.ClassRoom -> ClassScreen(vm, top)
                        is Screen.Pdf -> PdfScreen(vm, top)
                        is Screen.Assessments -> AssessmentsScreen(vm, top)
                        is Screen.Attendance -> AttendanceScreen(vm, top)
                        is Screen.Feedback -> FeedbackScreen(vm, top)
                        is Screen.Quiz -> QuizScreen(vm, top)
                        is Screen.Grading -> GradingScreen(vm, top)
                        Screen.Certificates -> CertificatesScreen(vm, embedded = true)
                        Screen.ChangePassword -> PasswordScreen(vm, forced = false)
                    }
                    vm.isStaffView -> when (tab) {
                        TAB_CALENDAR -> if (vm.role == Roles.ADMIN) AdminCalendarScreen(vm) else CalendarScreen(vm)
                        TAB_THIRD -> StaffCoursesScreen(vm)
                        TAB_ACCOUNT -> AccountScreen(vm, onNotifications)
                        else -> StaffHomeScreen(vm, onProfile = { tab = TAB_ACCOUNT })
                    }
                    else -> when (tab) {
                        TAB_CALENDAR -> CalendarScreen(vm)
                        TAB_THIRD -> if (vm.role == Roles.TRAINER) PayoutScreen(vm) else CertificatesScreen(vm)
                        TAB_FOURTH -> if (vm.role == Roles.TRAINER) TrainingHoursScreen(vm) else SupportScreen(vm)
                        TAB_ACCOUNT -> AccountScreen(vm, onNotifications)
                        else -> MyClassesScreen(vm, onProfile = { tab = TAB_ACCOUNT }, onCalendar = { tab = TAB_CALENDAR })
                    }
                }
            }
        }
    }

    vm.downloadProblem?.let { problem ->
        AlertDialog(
            onDismissRequest = { vm.downloadProblem = null },
            title = { Text("Download") },
            text = { Text(problem.message) },
            confirmButton = { TextButton(onClick = { vm.downloadProblem = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { vm.downloadProblem = null; openLink(context, problem.url) }) { Text("Open in browser") } },
        )
    }

    vm.result?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.result = null },
            title = { Text(message.title) },
            text = { Text(message.message) },
            confirmButton = { TextButton(onClick = { vm.result = null }) { Text("OK") } },
        )
    }
}
