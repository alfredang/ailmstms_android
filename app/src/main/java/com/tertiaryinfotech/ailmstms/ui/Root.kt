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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Screen
import kotlinx.coroutines.launch

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
            tab = 2
            onCalendarOpened()
        }
    }
    LaunchedEffect(signedIn) { if (!signedIn) tab = 0 }
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

    val tabs = listOf("Home" to Icons.Default.Home, "Courses" to SchoolIcon, "Calendar" to Icons.Default.DateRange, "Account" to Icons.Default.Person)
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
                        IconButton(onClick = vm::refresh, enabled = !vm.busy) { Icon(Icons.Default.Refresh, "Refresh classes") }
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
                NavigationBar {
                    tabs.forEachIndexed { i, item ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(item.second, null) }, label = { Text(item.first) })
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
                        Screen.EditProfile -> ProfileScreen(vm)
                        Screen.ChangePassword -> PasswordScreen(vm, forced = false)
                    }
                    else -> when (tab) {
                        0 -> HomeScreen(vm, onCourses = { tab = 1 }, onCalendar = { tab = 2 })
                        1 -> CoursesScreen(vm)
                        2 -> CalendarScreen(vm)
                        else -> AccountScreen(vm, onNotifications)
                    }
                }
            }
        }
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
