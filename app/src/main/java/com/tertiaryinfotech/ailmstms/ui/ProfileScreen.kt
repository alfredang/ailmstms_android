package com.tertiaryinfotech.ailmstms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.data.EMPLOYMENT_OPTIONS
import com.tertiaryinfotech.ailmstms.data.ProfilePatch
import com.tertiaryinfotech.ailmstms.data.UserProfile
import com.tertiaryinfotech.ailmstms.data.isSafeLink

@Composable
fun ProfileScreen(vm: LmsViewModel) {
    when (val state = vm.profileState) {
        Load.Idle, Load.Loading -> LoadingCard("Loading your profile…")
        is Load.Failed -> Column(Modifier.padding(20.dp)) { ErrorCard(state.message) { vm.loadProfile() } }
        is Load.Ready -> ProfileForm(vm, state.value)
    }
}

@Composable
private fun ProfileForm(vm: LmsViewModel, saved: UserProfile) {
    val roles = vm.profile?.roles.orEmpty()
    val learner = "learner" in roles
    val trainer = "trainer" in roles
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp).testTag("profile"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Heading("Your details")
        OutlinedTextField(saved.email, {}, label = { Text("Email (cannot be changed here)") }, readOnly = true, enabled = false, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            fullName, { fullName = it.take(200) }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        OutlinedTextField(
            phone, { phone = it.take(30) }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        if (learner) {
            OutlinedTextField(company, { company = it.take(200) }, label = { Text("Company") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Employment status", style = MaterialTheme.typography.labelLarge)
                EMPLOYMENT_OPTIONS.forEach { option ->
                    FilterChip(selected = employment == option, onClick = { employment = option }, label = { Text(option) })
                }
            }
        }
        if (trainer) {
            OutlinedTextField(
                linkedin, { linkedin = it.take(300) }, label = { Text("LinkedIn profile link") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
        SectionTitle("Appearance")
        ThemePicker(vm)
        (vm.formError ?: problem)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (vm.working) "Saving…" else "Save profile") }
        if (vm.isDemo) Text("Demo: changes stay on this device and are discarded when you leave the demo.", style = MaterialTheme.typography.bodySmall)
    }
}
