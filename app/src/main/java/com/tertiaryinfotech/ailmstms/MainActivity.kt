package com.tertiaryinfotech.ailmstms

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tertiaryinfotech.ailmstms.ui.LmsTheme
import com.tertiaryinfotech.ailmstms.ui.Root
import com.tertiaryinfotech.ailmstms.ui.resolveDark

class MainActivity : ComponentActivity() {
    private var calendarRequested by mutableStateOf(false)

    /** A link the app was opened with (a scanned quiz QR code), waiting to be handed to the view model. */
    private var incomingLink by mutableStateOf<String?>(null)

    /** Debug builds only: `--es demoRole admin` opens the demo as that role (as DEMO_ROLE does on iOS). */
    private var demoRole by mutableStateOf<String?>(null)

    private fun linkOf(intent: Intent?): String? = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        calendarRequested = intent.getBooleanExtra("openCalendar", false)
        // Only a fresh launch carries a new link; a rotation re-delivers the old intent.
        if (savedInstanceState == null) incomingLink = linkOf(intent)
        if (savedInstanceState == null && BuildConfig.DEBUG) demoRole = intent.getStringExtra("demoRole")
        setContent {
            val vm: LmsViewModel = viewModel()
            LaunchedEffect(incomingLink) {
                incomingLink?.let {
                    incomingLink = null
                    if (it.startsWith("tiatms://")) vm.onAuthCallback(android.net.Uri.parse(it)) else vm.openLink(it)
                }
            }
            LaunchedEffect(demoRole) {
                demoRole?.let {
                    demoRole = null
                    if (vm.profile == null) vm.enterDemo(it)
                }
            }
            val dark = resolveDark(vm.themeMode, isSystemInDarkTheme())
            // The header is always dark blue, so the status bar keeps light icons; the
            // navigation bar follows the chosen theme rather than the device setting.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose {}
            }
            LmsTheme(vm.themeMode, vm.brandColor) {
                Root(openCalendar = calendarRequested, onCalendarOpened = { calendarRequested = false }, vm = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        calendarRequested = intent.getBooleanExtra("openCalendar", false)
        linkOf(intent)?.let { incomingLink = it }
    }
}
