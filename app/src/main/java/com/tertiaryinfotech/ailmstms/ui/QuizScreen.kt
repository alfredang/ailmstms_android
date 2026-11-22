package com.tertiaryinfotech.ailmstms.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.Screen
import com.tertiaryinfotech.ailmstms.data.Quiz
import com.tertiaryinfotech.ailmstms.data.QuizAttempt
import com.tertiaryinfotech.ailmstms.data.QuizResult
import com.tertiaryinfotech.ailmstms.data.QuizReview
import com.tertiaryinfotech.ailmstms.data.prettyInstant

/** The class quiz behind a trainer's quiz QR code: one question at a time, then the marked result. */
@Composable
fun QuizScreen(vm: LmsViewModel, screen: Screen.Quiz) {
    when (val state = vm.quizState) {
        Load.Idle, Load.Loading -> LoadingCard("Loading the quiz…")
        is Load.Failed -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ErrorCard(state.message) { vm.loadQuiz(screen.courseId, screen.quizId) }
            OutlinedButton(onClick = { vm.back() }) { Text("Close") }
        }
        is Load.Ready -> {
            val quiz = state.value.quiz
            val result = vm.quizResult
            val previous = state.value.previous
            when {
                result != null -> QuizResultContent(vm, quiz, result)
                !vm.quizStarted && previous != null -> QuizPrevious(vm, quiz, previous)
                else -> QuizQuestions(vm, screen, quiz)
            }
        }
    }
}

@Composable
private fun QuizHeader(quiz: Quiz) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Heading(quiz.title)
        val course = listOf(quiz.courseCode, quiz.courseTitle).filter { it.isNotBlank() }.joinToString(" · ")
        if (course.isNotBlank()) Text(course, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScoreCard(label: String, score: Int, total: Int, detail: String) {
    val colors = LocalLmsColors.current
    Surface(color = colors.successContainer, contentColor = colors.onSuccessContainer, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                "$score / $total", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "$score out of $total" }.testTag("quiz-score"),
            )
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun percent(score: Int, total: Int): Int = if (total > 0) Math.round(100f * score / total) else 0

/** The learner has taken this quiz before: show the last score and let them choose to retake. */
@Composable
private fun QuizPrevious(vm: LmsViewModel, quiz: Quiz, previous: QuizAttempt) {
    Column(Modifier.fillMaxSize().padding(20.dp).testTag("quiz-previous"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuizHeader(quiz)
        val done = previous.completedAt.takeIf { it.isNotBlank() }?.let { "Completed ${prettyInstant(it)}" }
        ScoreCard("Your last score", previous.score, previous.total, listOfNotNull("${percent(previous.score, previous.total)}% correct", done).joinToString(" · "))
        Text(
            "You have already taken this quiz. You can retake it: ${quiz.questions.size} question${if (quiz.questions.size == 1) "" else "s"}, and your latest attempt is the one your trainer sees.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = vm::startQuiz, modifier = Modifier.fillMaxWidth()) { Text("Retake quiz") }
        OutlinedButton(onClick = { vm.back() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun QuizQuestions(vm: LmsViewModel, screen: Screen.Quiz, quiz: Quiz) {
    val index = vm.quizIndex.coerceIn(0, quiz.questions.lastIndex)
    val question = quiz.questions[index]
    val picked = vm.quizAnswers[question.id]
    val last = index == quiz.questions.lastIndex
    LazyColumn(Modifier.fillMaxSize().testTag("quiz"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { QuizHeader(quiz) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Question ${index + 1} of ${quiz.questions.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { (index + 1f) / quiz.questions.size }, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            AppCard {
                Column(Modifier.fillMaxWidth().padding(20.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(question.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    question.options.forEachIndexed { i, option ->
                        val selected = picked == i
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = !vm.working, role = Role.RadioButton) { vm.pickQuizOption(question.id, i) },
                        ) {
                            Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
                                Text(option, Modifier.weight(1f).padding(end = 8.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                vm.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (index > 0) OutlinedButton(onClick = { vm.back() }, enabled = !vm.working, modifier = Modifier.weight(1f)) { Text("Previous") }
                    if (last) {
                        Button(onClick = { vm.submitQuiz(screen) }, enabled = picked != null && !vm.working, modifier = Modifier.weight(1f)) {
                            Text(if (vm.working) "Submitting…" else "Submit")
                        }
                    } else {
                        Button(onClick = vm::nextQuizQuestion, enabled = picked != null, modifier = Modifier.weight(1f)) { Text("Next") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizResultContent(vm: LmsViewModel, quiz: Quiz, result: QuizResult) {
    val review = result.review(quiz, vm.quizAnswers)
    LazyColumn(Modifier.fillMaxSize().testTag("quiz-result"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { QuizHeader(quiz) }
        item { ScoreCard("Your score", result.score, result.total, "${result.percent}% correct") }
        itemsIndexed(review, key = { _, item -> "r-" + item.question.id }) { i, item -> ReviewCard(i + 1, item) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::startQuiz, modifier = Modifier.weight(1f)) { Text("Retake") }
                Button(onClick = { vm.back() }, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun ReviewCard(number: Int, item: QuizReview) {
    val colors = LocalLmsColors.current
    val wrong = MaterialTheme.colorScheme.error
    AppCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Question $number", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusChip(if (item.correct) "Correct" else if (item.selectedIndex == null) "Not answered" else "Incorrect", if (item.correct) Tone.Good else Tone.Bad)
            }
            Text(item.question.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            item.question.options.forEachIndexed { i, option ->
                val isCorrect = i == item.correctIndex
                val isPicked = i == item.selectedIndex
                val note = when {
                    isCorrect && isPicked -> "Your answer · Correct answer"
                    isCorrect -> "Correct answer"
                    isPicked -> "Your answer"
                    else -> null
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        isCorrect -> Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp), tint = colors.success)
                        isPicked -> Icon(Icons.Default.Close, null, Modifier.size(22.dp), tint = wrong)
                        else -> Icon(Icons.Default.Close, null, Modifier.size(22.dp), tint = androidx.compose.ui.graphics.Color.Transparent)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(option, fontWeight = if (isCorrect) FontWeight.SemiBold else FontWeight.Normal)
                        if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, color = if (isCorrect) colors.success else wrong)
                    }
                }
            }
        }
    }
}
