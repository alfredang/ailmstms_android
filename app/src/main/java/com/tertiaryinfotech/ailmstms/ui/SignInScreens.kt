package com.tertiaryinfotech.ailmstms.ui

import android.util.Patterns
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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tertiaryinfotech.ailmstms.LmsViewModel

@Composable
fun LoginScreen(vm: LmsViewModel) {
    val context = LocalContext.current
    val emailValid = Patterns.EMAIL_ADDRESS.matcher(vm.email.trim()).matches()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(SchoolIcon, null, Modifier.padding(20.dp).size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text("Your training.\nAll in one place.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Classes, courseware, attendance, assessments and reminders for Tertiary Infotech Academy learners and trainers.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (vm.sent) "Check your email" else "Welcome back", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    vm.email, { vm.email = it }, label = { Text("Registered email address") }, singleLine = true,
                    enabled = !vm.sent && !vm.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    vm.usePassword -> {
                        PasswordField(vm.password, { vm.password = it }, "Password", enabled = !vm.busy)
                        Button(onClick = vm::loginWithPassword, enabled = emailValid && vm.password.isNotEmpty() && !vm.busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Sign in")
                        }
                        TextButton(onClick = { vm.choosePassword(false) }, enabled = !vm.busy) { Text("Sign in with an email code instead") }
                    }
                    vm.sent -> {
                        Text("Enter the 6-digit code sent to your email. Codes expire after 30 minutes.")
                        OutlinedTextField(
                            vm.otp, { vm.otp = it.filter(Char::isDigit).take(6) }, label = { Text("One-time code") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = vm::login, enabled = vm.otp.length == 6 && !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
                        TextButton(onClick = vm::changeEmail, enabled = !vm.busy) { Text("Change email or request another code") }
                    }
                    else -> {
                        Button(onClick = vm::sendOtp, enabled = emailValid && !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Send sign-in code") }
                        OutlinedButton(onClick = { vm.choosePassword(true) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Sign in with password")
                        }
                    }
                }
            }
        }
        TextButton(onClick = vm::enterDemo, enabled = !vm.busy) { Text("Explore demo") }
        Text(
            "Use the email registered with your training provider. Learner and trainer accounts only.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row {
            TextButton(onClick = { openLink(context, PRIVACY_URL) }) { Text("Privacy policy") }
            TextButton(onClick = { openLink(context, SUPPORT_WHATSAPP) }) { Text("Need help?") }
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
