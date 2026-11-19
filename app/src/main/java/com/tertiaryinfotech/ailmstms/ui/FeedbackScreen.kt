package com.tertiaryinfotech.ailmstms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.Screen
import com.tertiaryinfotech.ailmstms.data.FeedbackField
import com.tertiaryinfotech.ailmstms.data.FeedbackForm
import com.tertiaryinfotech.ailmstms.data.prettyDay
import java.time.Instant
import java.time.ZoneOffset

/** Learner: the academy's course feedback form, rendered natively from the server's definition. */
@Composable
fun FeedbackScreen(vm: LmsViewModel, screen: Screen.Feedback) {
    when (val state = vm.feedbackState) {
        Load.Idle, Load.Loading -> LoadingCard("Loading the feedback form…")
        is Load.Failed -> Column(Modifier.padding(20.dp)) { ErrorCard(state.message) { vm.loadFeedback(screen.runId) } }
        is Load.Ready -> if (state.value.submitted) FeedbackSent(vm) else FeedbackFormContent(vm, screen, state.value)
    }
}

@Composable
private fun FeedbackSent(vm: LmsViewModel) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(72.dp), tint = LocalLmsColors.current.success)
        Text("Feedback sent", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Thank you. Your feedback helps your academy improve this course.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { vm.back() }) { Text("Back to class") }
    }
}

@Composable
private fun FeedbackFormContent(vm: LmsViewModel, screen: Screen.Feedback, form: FeedbackForm) {
    LazyColumn(
        Modifier.fillMaxSize().imePadding().testTag("feedback"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Heading(form.title)
                Text(screen.courseTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        form.sections.forEach { section ->
            item(key = "section-" + section.id) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (section.title.isNotBlank()) SectionTitle(section.title)
                        section.fields.forEach { field -> FieldInput(vm, form, field) }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                vm.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { vm.submitFeedback(screen.runId) }, enabled = !vm.working, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.working) "Sending…" else "Send feedback")
                }
                Text("Questions marked * are required. You can send feedback once for each class.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldInput(vm: LmsViewModel, form: FeedbackForm, field: FeedbackField) {
    val label = field.label + if (field.required && !field.locked) " *" else ""
    val value = vm.feedbackAnswers[field.id].orEmpty()
    val set: (String) -> Unit = { vm.feedbackAnswers[field.id] = it; vm.formError = null }

    if (field.locked) {
        // Filled in by the server from the class or the learner's account: shown, never edited.
        val shown = when {
            field.autofill != null -> form.autofill[field.autofill].orEmpty().let { if (field.type == "date") prettyDay(it) else it }
            field.type == "email" -> vm.profile?.email.orEmpty()
            else -> value
        }
        OutlinedTextField(shown, {}, label = { Text(label) }, readOnly = true, enabled = false, modifier = Modifier.fillMaxWidth())
        return
    }
    when (field.type) {
        "rating1to5" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label)
            val rating = value.toIntOrNull() ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..5).forEach { star ->
                    IconButton(onClick = { set(star.toString()) }, modifier = Modifier.semantics { role = Role.RadioButton }) {
                        Icon(
                            Icons.Default.Star, "$star of 5 stars: ${field.label}", Modifier.size(36.dp),
                            tint = if (star <= rating) LocalLmsColors.current.warning else MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
                if (rating > 0) Text("$rating / 5", Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "select" -> {
            var open by remember { mutableStateOf(false) }
            Box {
                OutlinedTextField(
                    value, {}, label = { Text(label) }, readOnly = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Default.ArrowDropDown, "Choose: ${field.label}") } },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    field.options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { set(option); open = false }) }
                }
            }
        }
        "date" -> {
            var open by remember { mutableStateOf(false) }
            OutlinedTextField(
                if (value.isBlank()) "" else prettyDay(value), {}, label = { Text(label) }, readOnly = true, modifier = Modifier.fillMaxWidth(),
                trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Default.DateRange, "Choose a date: ${field.label}") } },
            )
            if (open) {
                val picker = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { open = false },
                    confirmButton = {
                        TextButton(onClick = {
                            picker.selectedDateMillis?.let { set(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                            open = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
                ) { DatePicker(picker) }
            }
        }
        "textarea" -> OutlinedTextField(value, { set(it.take(4000)) }, label = { Text(label) }, minLines = 3, modifier = Modifier.fillMaxWidth())
        else -> OutlinedTextField(value, { set(it.take(4000)) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
