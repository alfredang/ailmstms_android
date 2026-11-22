package com.tertiaryinfotech.ailmstms

import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.tertiaryinfotech.ailmstms.data.DemoApi
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * One end-to-end pass through the public demo, as a Google Play reviewer would use it: both
 * roles and every screen, with no account and no network. Screenshots are written to the app's
 * external files directory for the store listing.
 */
class AppFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(700)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(8000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    /** Snackbars sit over the bottom of the screen for a few seconds; wait them out before tapping or capturing there. */
    private fun waitUntilGone(text: String) =
        compose.waitUntil(15000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty() }

    private fun tab(name: String) = compose.onNodeWithTag("tab-$name").performClick()
    private fun back() = compose.onNodeWithContentDescription("Back").performClick()

    private fun openClass() {
        tab("My Classes")
        compose.onNodeWithTag("my-classes").performScrollToNode(hasText("DEMO · AI-101"))
        compose.onNodeWithText("DEMO · AI-101").performClick()
        waitForClass()
    }

    private fun waitForClass() = compose.waitUntil(8000) { compose.onAllNodes(hasTestTag("class-detail")).fetchSemanticsNodes().isNotEmpty() }

    /** Scrolls the class screen to [text], waiting for a background refresh of the class if needed. */
    private fun inClass(text: String) = compose.waitUntil(8000) {
        runCatching { compose.onNodeWithTag("class-detail").performScrollToNode(hasText(text)) }.isSuccess
    }

    @Test
    fun learnerTrainerNavigation() {
        // Sign-in: email code by default, password as an option.
        compose.onNodeWithText("Send sign-in code").assertIsNotEnabled()
        compose.onNodeWithText("Sign in with password").assertExists()
        capture("01-login")
        compose.onNodeWithText("Sign in with password").performScrollTo().performClick()
        compose.onNodeWithText("Password").assertExists()
        compose.onNodeWithText("Sign in with an email code instead").performScrollTo().performClick()

        // A scanned quiz QR code while signed out: the quiz is kept, and opens right after sign-in.
        val quizLink = "https://lms-tms.tertiaryinfotech.com/quiz/${DemoApi.QUIZ_COURSE_ID}/${DemoApi.QUIZ_ID}"
        val launchIntent = compose.activity.intent
        compose.activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(quizLink)).setPackage(compose.activity.packageName))
        compose.waitUntil(8000) { compose.onAllNodes(hasTestTag("quiz-pending")).fetchSemanticsNodes().isNotEmpty() }
        // The test rule follows the activity by its launch intent, which onNewIntent has just replaced.
        compose.runOnUiThread { compose.activity.intent = launchIntent }
        compose.onNodeWithText("Send sign-in code").assertExists()
        // One role per sign-in, Learner unless Trainer is chosen.
        compose.onNodeWithText("Sign in as").assertExists()
        compose.onNodeWithText("See the courses you are taking. Trainers attending a course sign in as a learner.").assertExists()
        compose.onNodeWithText("Explore demo").performScrollTo().performClick()
        waitForText("Question 1 of 3")
        compose.onNodeWithText("Day 1 recap quiz").assertExists()
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithText("The next token").performClick()
        capture("14-quiz")
        compose.onNodeWithText("Next").performClick()
        waitForText("Question 2 of 3")
        compose.onNodeWithText("Typing in capital letters").performClick()
        compose.onNodeWithText("Previous").performClick()
        waitForText("Question 1 of 3")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        waitForText("Question 3 of 3")
        compose.onNodeWithText("Submit").assertIsNotEnabled()
        compose.onNodeWithText("Grounding answers in your own documents").performClick()
        compose.onNodeWithTag("quiz").performScrollToNode(hasText("Submit"))
        compose.onNodeWithText("Submit").performClick()
        waitForText("Your score")
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithText("67% correct").assertExists()
        capture("15-quiz-result")
        compose.onNodeWithTag("quiz-result").performScrollToNode(hasText("Incorrect"))
        compose.onNodeWithTag("quiz-result").performScrollToNode(hasText("Your answer"))
        compose.onNodeWithTag("quiz-result").performScrollToNode(hasText("Done"))
        compose.onNodeWithText("Done").performClick()

        // Learner home
        waitForText("Welcome")
        compose.onNodeWithText("Alex").assertExists()
        compose.onNodeWithText("My Profile").assertExists()
        compose.onAllNodes(hasTestTag("viewAsPicker")).assertCountEquals(0)
        compose.onNodeWithText("TIA TMS").assertExists()
        compose.onNodeWithText("DEMO · Fictional sample data").assertExists()
        waitForText("Session 1 · Foundations", substring = true)
        capture("02-home")

        // Class detail
        openClass()
        compose.onNodeWithText("Scan class QR").assertExists()
        compose.onNodeWithText("SSG e-attendance (Singpass)").assertExists()
        capture("03-class")

        // The quiz again, from the class: the last score comes first, with Retake.
        inClass("Take the sample quiz")
        compose.onNodeWithText("Take the sample quiz").performClick()
        waitForText("Your last score")
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithText("Retake quiz").performClick()
        waitForText("Question 1 of 3")
        back()
        waitForClass()

        // Courseware opens in the in-app PDF viewer
        inClass("Learner slides")
        compose.onNodeWithText("Learner slides").performClick()
        waitForText("Page 1 of 3")
        compose.onNodeWithText("Open in browser").assertExists()
        compose.onNodeWithContentDescription("Download").assertExists()
        compose.onNodeWithContentDescription("Zoom in").performClick()
        compose.onNodeWithContentDescription("Zoom out").performClick()
        compose.waitUntil(8000) { compose.onAllNodes(hasContentDescription("Page 1")).fetchSemanticsNodes().isNotEmpty() }
        capture("04-pdf")
        back()

        // Digital attendance: the demo simulates a successful scan
        inClass("Scan class QR")
        compose.onNodeWithText("Scan class QR").performClick()
        waitForText("Checked in")
        capture("05-attendance")
        compose.onNodeWithText("OK").performClick()
        inClass("Present")

        // Assessments
        inClass("Assessments")
        compose.waitUntil(8000) { runCatching { compose.onNodeWithTag("class-detail").performScrollToNode(hasText("2 open", substring = true)) }.isSuccess }
        compose.onNode(hasText("Assessments") and hasText("2 open", substring = true)).performClick()
        compose.onNodeWithText("Written Assessment").assertExists()
        compose.onAllNodesWithText("View question paper").onFirst().assertExists()
        compose.onAllNodesWithText("Submit file").onFirst().assertExists()
        capture("06-assessment")
        back()

        // Course feedback
        inClass("Give course feedback")
        compose.onNodeWithText("Give course feedback").performClick()
        waitForText("Course Feedback")
        compose.onNodeWithTag("feedback").performScrollToNode(hasText("Send feedback"))
        compose.onNodeWithText("Send feedback").performClick()
        waitForText("Please rate", substring = true)
        compose.onNodeWithTag("feedback").performScrollToNode(hasContentDescription("5 of 5 stars", substring = true))
        compose.onAllNodes(hasContentDescription("5 of 5 stars", substring = true))[0].performClick()
        compose.onAllNodes(hasContentDescription("4 of 5 stars", substring = true))[1].performClick()
        capture("07-feedback")
        compose.onNodeWithTag("feedback").performScrollToNode(hasText("Send feedback"))
        compose.onNodeWithText("Send feedback").performClick()
        waitForText("Feedback sent")
        compose.onNodeWithText("Back to class").performClick()
        inClass("Feedback sent")
        inClass("TRAQOM survey")
        back()

        // Calendar: tapping a session opens its class
        tab("Calendar")
        compose.onNodeWithText("Class calendar").assertExists()
        // The demo class starts three days from today, which may fall in the next month.
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Singapore"))
        if (today.plusDays(3).month != today.month) compose.onNodeWithContentDescription("Next month").performClick()
        capture("08-calendar")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Open class"))
        compose.onAllNodes(hasText("Open class")).onFirst().performClick()
        waitForClass()
        back()

        // Learner tabs that wait for their data
        tab("Certificates")
        waitForText("Coming soon")
        compose.onNodeWithText("Your course certificates will appear here once they are issued.").assertExists()
        tab("Billing")
        compose.onNodeWithText("Your invoices and payment history will appear here.").assertExists()

        // Account, reached from the home header: one page with contact, profile, theme, password, reminders
        tab("My Classes")
        compose.onNodeWithTag("myProfileLink").performClick()
        waitForText("Contact", substring = true)
        compose.onNodeWithText("alex@example.com").assertExists()
        compose.onNodeWithContentDescription("WhatsApp: +65 6000 0000").assertExists()
        compose.onNodeWithText("LinkedIn profile link").assertExists()
        capture("09-account")
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        capture("10-profile-dark")
        compose.onNodeWithText("Light").performClick()
        compose.onNodeWithText("System").performClick()
        compose.onNodeWithTag("saveProfile").performScrollTo().performClick()
        waitForText("Profile saved")
        waitUntilGone("Profile saved")
        compose.onNodeWithText("In demo, enabling reminders shows a sample notification. Live accounts receive scheduled class reminders.").performScrollTo().assertExists()
        val app = compose.activity
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS").close()
        compose.onNode(isToggleable()).performClick()
        compose.waitUntil(8000) { app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == DemoNotifications.ID } }
        compose.onNodeWithText("Change password").performScrollTo().performClick()
        waitForText("Current password")
        back()

        // Sign out, then the demo again as a trainer
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNodeWithTag("confirmSignOut").performClick()
        waitForText("Send sign-in code")
        compose.onNodeWithTag("loginRole-trainer").performClick()
        compose.onNodeWithText("See the classes you teach, attendance, payout and training hours.").assertExists()
        compose.onNodeWithText("Explore demo").performScrollTo().performClick()
        waitForText("Welcome")
        compose.onNodeWithText("Classes you teach", ignoreCase = true).assertExists()
        capture("16-trainer-home")

        // Trainer tabs: payouts, and hours waiting for their data
        tab("Payout")
        waitForText("Total paid")
        compose.onNodeWithText("S$3,350.00").assertExists()
        compose.onNodeWithText("3 payouts").assertExists()
        capture("17-trainer-payout")
        tab("Hours")
        compose.onNodeWithText("Your training hours for the classes you teach will appear here.").assertExists()
        tab("Account")
        compose.onNodeWithText("Trainer").assertExists()

        // Trainer: QR code, manual attendance, results
        openClass()
        compose.onNodeWithText("Open SSG attendance QR").assertExists()
        inClass("Trainer slides")
        inClass("QR code & attendance")
        compose.onAllNodesWithText("QR code & attendance").onFirst().performClick()
        waitForText("Learner check-in")
        compose.onNodeWithContentDescription("Check-in QR code").assertExists()
        capture("11-trainer-attendance")
        compose.onNodeWithTag("attendance").performScrollToNode(hasText("Mark all present"))
        compose.onNodeWithText("Mark all present").performClick()
        compose.onNodeWithText("Save attendance").performClick()
        waitForText("Attendance is up to date")
        waitUntilGone("Attendance saved for")
        capture("12-trainer-attendance-saved")
        back()
        inClass("4 present")

        // TRAQOM: the trainer shows the survey as a QR code and can share the link
        inClass("Show TRAQOM Survey QR")
        compose.onNodeWithText("Show TRAQOM Survey QR").performClick()
        compose.onNodeWithTag("traqomQr").assertExists()
        compose.onNodeWithText("Share Link").assertExists()
        capture("18-trainer-traqom-qr")
        compose.onNodeWithText("Done").performClick()

        inClass("Assessments & results")
        compose.onNodeWithText("Assessments & results").performClick()
        waitForText("Learner results")
        compose.onNodeWithTag("assessments").performScrollToNode(hasTestTag("competent-demo-e2"))
        compose.onNodeWithTag("competent-demo-e2").performClick()
        waitForText("Mark as Competent?")
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithText("Mark as Competent?").fetchSemanticsNodes().isEmpty() }
        waitForText("Chen Mei Ling: Competent")
        waitUntilGone("Chen Mei Ling: Competent")
        capture("13-trainer-results")
        back()
        back()

        // Sign out returns to the sign-in screen, with the choice back on Learner
        tab("Account")
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNodeWithTag("confirmSignOut").performClick()
        waitForText("Send sign-in code")
        compose.onNodeWithText("See the courses you are taking. Trainers attending a course sign in as a learner.").assertExists()
    }
}
