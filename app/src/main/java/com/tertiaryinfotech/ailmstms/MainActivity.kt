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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tertiaryinfotech.ailmstms.ui.LmsTheme
import com.tertiaryinfotech.ailmstms.ui.Root
import com.tertiaryinfotech.ailmstms.ui.resolveDark

class MainActivity : ComponentActivity() {
    private var calendarRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        calendarRequested = intent.getBooleanExtra("openCalendar", false)
        setContent {
            val vm: LmsViewModel = viewModel()
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
    }
}
