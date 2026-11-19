package com.tertiaryinfotech.ailmstms

import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.SystemClock
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

    private fun tab(name: String) = compose.onNodeWithText(name, useUnmergedTree = true).performClick()
    private fun back() = compose.onNodeWithContentDescription("Back").performClick()

    private fun openClass() {
        tab("Courses")
        compose.onNodeWithText("Building AI Applications").performClick()
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
        compose.onNodeWithText("Explore demo").performScrollTo().performClick()

        // Learner home
        waitForText("Hello, Alex")
        compose.onNodeWithText("TIA TMS").assertExists()
        compose.onNodeWithText("DEMO · Fictional sample data").assertExists()
        waitForText("Session 1 · Foundations", substring = true)
        capture("02-home")

        // Class detail
        openClass()
        compose.onNodeWithText("Scan class QR").assertExists()
        compose.onNodeWithText("SSG e-attendance (Singpass)").assertExists()
        capture("03-class")

        // Courseware opens in the in-app PDF viewer
        inClass("Learner slides")
        compose.onNodeWithText("Learner slides").performClick()
        waitForText("Page 1 of 3")
        compose.onNodeWithText("Open in browser").assertExists()
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

        // Account: role switch, reminders, profile, theme, password
        tab("Account")
        compose.onNodeWithText("Trainer", useUnmergedTree = true).performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Class reminders", substring = true))
        compose.onNodeWithText("In demo, enabling reminders shows a sample notification. Live accounts receive scheduled class reminders.", substring = true).assertExists()
        val app = compose.activity
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS").close()
        compose.onNode(isToggleable()).performClick()
        compose.waitUntil(8000) { app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == DemoNotifications.ID } }
        capture("09-account")

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Edit profile"))
        compose.onNodeWithText("Edit profile").performClick()
        waitForText("Your details")
        compose.onNodeWithText("alex@example.com").assertExists()
        compose.onNodeWithText("LinkedIn profile link").assertExists()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        capture("10-profile-dark")
        compose.onNodeWithText("Light").performClick()
        compose.onNodeWithText("System").performClick()
        compose.onNodeWithText("Save profile").performScrollTo().performClick()
        waitForText("Your account")
        waitUntilGone("Profile saved")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Change password"))
        compose.onNodeWithText("Change password").performClick()
        waitForText("Current password")
        back()

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

        // Sign out returns to the sign-in screen
        tab("Account")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Sign out"))
        compose.onNodeWithText("Sign out").performClick()
        compose.onNodeWithText("Send sign-in code").assertExists()
    }
}
