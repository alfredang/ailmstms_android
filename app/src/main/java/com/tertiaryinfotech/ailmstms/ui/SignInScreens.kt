package com.tertiaryinfotech.ailmstms.ui

import android.util.Patterns
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.tertiaryinfotech.ailmstms.data.Roles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tertiaryinfotech.ailmstms.LmsViewModel

/**
 * Sign in with an emailed code (the default) or the LMS/TMS password. Layout follows the TIA
 * house sign-in (same as iOS): app mark, name, one card, quiet links at the foot.
 */
@Composable
fun LoginScreen(vm: LmsViewModel) {
    val context = LocalContext.current
    val lms = LocalLmsColors.current
    val prefs = remember { context.getSharedPreferences("signin", Context.MODE_PRIVATE) }
    var keepEmail by remember { mutableStateOf(prefs.getBoolean("rememberEmail", true)) }
    LaunchedEffect(Unit) {
        if (vm.email.isBlank()) prefs.getString("rememberedEmail", null)?.let { vm.email = it }
    }
    val emailValid = Patterns.EMAIL_ADDRESS.matcher(vm.email.trim()).matches()
    val primaryEnabled = !vm.busy && emailValid && when {
        vm.usePassword -> vm.password.isNotEmpty()
        vm.sent -> vm.otp.length == 6
        else -> true
    }
    fun primary() {
        if (!primaryEnabled) return
        if (keepEmail) prefs.edit().putString("rememberedEmail", vm.email.trim()).apply()
        when {
            vm.usePassword -> vm.loginWithPassword()
            vm.sent -> vm.login()
            else -> vm.sendOtp()
        }
    }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))))
            .verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // App mark and name
        Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(92.dp).clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF0A3D8F), Color(0xFF051C47)))),
                contentAlignment = Alignment.Center,
            ) { Text("TIA", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black) }
            Text("TIA TMS", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("Tertiary Infotech Academy", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AppCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sign in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (vm.sent && !vm.usePassword) "Enter the 6-digit code we emailed you."
                        else "Enter the email you registered with to continue.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // One role per sign-in. Chosen before signing in (and before entering the demo).
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("loginRole")) {
                        Roles.ALL.forEachIndexed { i, role ->
                            SegmentedButton(
                                selected = vm.loginRole == role, onClick = { vm.chooseLoginRole(role) }, enabled = !vm.busy,
                                shape = SegmentedButtonDefaults.itemShape(i, Roles.ALL.size),
                                icon = {},
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = lms.card, activeContentColor = MaterialTheme.colorScheme.primary, activeBorderColor = lms.cardBorder,
                                    inactiveContainerColor = lms.field, inactiveContentColor = MaterialTheme.colorScheme.onSurface, inactiveBorderColor = lms.cardBorder,
                                ),
                                modifier = Modifier.testTag("loginRole-$role"),
                            ) { Text(Roles.label(role), fontWeight = if (vm.loginRole == role) FontWeight.SemiBold else FontWeight.Normal) }
                        }
                    }
                    Text(
                        if (vm.loginRole == Roles.TRAINER) "See the classes you teach, attendance, payout and training hours."
                        else "See the courses you are taking. Trainers attending a course sign in as a learner.",
                        fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    vm.email, { vm.email = it; if (vm.sent) vm.changeEmail() },
                    placeholder = { Text("Email") }, singleLine = true, enabled = !vm.busy,
                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { primary() }),
                    modifier = Modifier.fillMaxWidth().testTag("email"),
                )
                if (vm.usePassword) {
                    PasswordField(vm.password, { vm.password = it }, "LMS/TMS password", enabled = !vm.busy)
                } else if (vm.sent) {
                    OutlinedTextField(
                        vm.otp, { vm.otp = it.filter(Char::isDigit).take(6) },
                        placeholder = { Text("6-digit code") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { primary() }),
                        modifier = Modifier.fillMaxWidth().testTag("otp"),
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Remember my email", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = keepEmail, onCheckedChange = {
                        keepEmail = it
                        prefs.edit().putBoolean("rememberEmail", it).apply()
                        if (!it) prefs.edit().remove("rememberedEmail").apply()
                    })
                }
                Button(
                    onClick = { primary() }, enabled = primaryEnabled,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = lms.brandFill, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 15.dp),
                    modifier = Modifier.fillMaxWidth().testTag(if (vm.usePassword) "signIn" else "continue"),
                ) {
                    if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (vm.usePassword) "Sign In" else if (vm.sent) "Verify & Sign In" else "Continue",
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!vm.usePassword && vm.sent) {
                        TextButton(onClick = vm::sendOtp, enabled = !vm.busy) { Text("Send a new code", fontSize = 13.sp) }
                    } else if (vm.usePassword) {
                        TextButton(onClick = { openLink(context, "https://lms-tms.tertiaryinfotech.com/") }) { Text("Forgot your password?", fontSize = 13.sp) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.choosePassword(!vm.usePassword) }, enabled = !vm.busy, modifier = Modifier.testTag("switchMethod")) {
                        Text(if (vm.usePassword) "Use email code instead" else "Use password instead", fontSize = 13.sp)
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Use the email your training provider registered for your class.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            val quiet = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.Center) {
                // A look around with sample data, for anyone without an account (and Play review).
                TextButton(onClick = vm::enterDemo, enabled = !vm.busy, colors = quiet) { Text("Try a demo", fontSize = 13.sp) }
                TextButton(onClick = { openLink(context, PRIVACY_URL) }, colors = quiet) { Text("Privacy policy", fontSize = 13.sp) }
                TextButton(onClick = { openLink(context, SUPPORT_WHATSAPP) }, colors = quiet) { Text("Need help?", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean = true, supporting: String? = null) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value, { onChange(it.take(200)) }, label = { Text(label) }, singleLine = true, enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") } },
        supportingText = supporting?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Change password: required straight after a sign-in that demands it ([forced]), optional from Account. */
@Composable
fun PasswordScreen(vm: LmsViewModel, forced: Boolean) {
    // Passwords are kept in memory only: never written to saved instance state.
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val problem = when {
        new.isNotEmpty() && new.length < 8 -> "Use at least 8 characters."
        new.isNotEmpty() && new == current -> "Choose a password different from your current one."
        again.isNotEmpty() && again != new -> "The two new passwords do not match."
        else -> null
    }
    val ready = current.isNotEmpty() && new.length >= 8 && new == again && new != current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Heading(if (forced) "Choose your own password" else "Change password")
        Text(
            if (forced) "Your training provider asks you to replace the password you were given before you continue."
            else "Changing your password signs you out on your other devices.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (vm.isDemo) Text("Demo: nothing is changed.", style = MaterialTheme.typography.bodySmall)
        PasswordField(current, { current = it }, "Current password", enabled = !vm.working)
        PasswordField(new, { new = it }, "New password", enabled = !vm.working, supporting = "At least 8 characters")
        PasswordField(again, { again = it }, "Repeat new password", enabled = !vm.working)
        (vm.formError ?: problem)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { vm.changePassword(current, new) }, enabled = ready && !vm.working, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.working) "Saving…" else "Change password")
        }
        if (forced) OutlinedButton(onClick = vm::logout, enabled = !vm.working && !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        else Text("Signed in with an email code and never set a password? Ask your training provider to set one for you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
