package com.tertiaryinfotech.ailmstms

import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
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

    /** Opens one of the class page's collapsible sections (they all start collapsed). */
    private fun section(title: String) {
        compose.waitUntil(8000) { runCatching { compose.onNodeWithTag("class-detail").performScrollToNode(hasTestTag("section-$title")) }.isSuccess }
        compose.onNodeWithTag("section-$title").performClick()
    }

    private fun inClassTag(tag: String) = compose.waitUntil(8000) {
        runCatching { compose.onNodeWithTag("class-detail").performScrollToNode(hasTestTag(tag)) }.isSuccess
    }

    /** The signature setup sheet: draws a stroke on the pad and saves. */
    private fun signAndSave() {
        compose.waitUntil(8000) { compose.onAllNodes(hasTestTag("signature-pad")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("signer-nric").performTextReplacement("S0000000A")
        compose.onNodeWithTag("signature-pad").performTouchInput {
            swipe(Offset(width * 0.1f, height * 0.6f), Offset(width * 0.5f, height * 0.3f), 200)
            swipe(Offset(width * 0.5f, height * 0.3f), Offset(width * 0.9f, height * 0.6f), 200)
        }
        capture("20-signature")
        compose.onNodeWithTag("save-signature").performClick()
        compose.waitUntil(8000) { compose.onAllNodes(hasTestTag("signature-setup")).fetchSemanticsNodes().isEmpty() }
    }

    private fun demoAs(role: String) = compose.runOnUiThread {
        ViewModelProvider(compose.activity)[LmsViewModel::class.java].enterDemo(role)
    }

    /** The class page for both roles, entering the demo directly (independent of the sign-in screen). */
    @Test
    fun classPageDemo() {
        demoAs("learner")
        waitForText("ALEX TAN")
        // My Classes card: the address the server sends, no progress ring.
        compose.onNodeWithTag("my-classes").performScrollToNode(hasText("12 Woodlands Square", substring = true))
        openClass()
        compose.onNodeWithTag("class-summary").assertExists()
        compose.onNodeWithTag("summary-trainer").assertExists()
        compose.onNodeWithText("Join Google Meet").assertExists()
        compose.onNodeWithText("Open in Google Maps").assertExists()
        compose.onAllNodesWithText("Scan class QR to check in").assertCountEquals(0)
        capture("30-learner-class")
        section("Course Details")
        inClass("Funding Validity")
        inClass("16 hrs")
        section("E-Attendance")
        inClass("Scan class QR to check in")
        compose.onNodeWithText("Scan class QR to check in").performClick()
        waitForText("Checked in")
        compose.onNodeWithText("OK").performClick()
        inClass("Present")
        section("Courseware")
        inClass("Learner Slides")
        compose.onNodeWithText("Learner Slides").performClick()
        waitForText("Page 1 of 3")
        back()
        // Sections stay open after returning from the PDF.
        inClass("Activities/Lab")
        section("Briefing on Assessment")
        inClass("No discussion during assessment")
        section("Assessment Summary Record")
        inClass("Sign my Assessment Summary Record")
        compose.onNodeWithText("Sign my Assessment Summary Record").performClick()
        waitForText("Your signature")
        signAndSave()
        inClass("Open signed record (PDF)")
        capture("31-learner-summary-record")
        section("Course Feedback & TRAQOM Survey")
        inClass("TRAQOM Survey")
        inClassTag("course-feedback")
        back()

        demoAs("trainer")
        waitForText("ALEX TAN")
        openClass()
        section("E-Attendance")
        inClass("SSG QR Attendance")
        inClass("SSG E-Attendance List")
        compose.onAllNodesWithText("Show TRAQOM Survey QR").assertCountEquals(0)
        inClass("QR code & attendance")
        compose.onAllNodesWithText("QR code & attendance").onFirst().performClick()
        compose.waitUntil(8000) { compose.onAllNodes(hasContentDescription("Check-in QR code")).fetchSemanticsNodes().isNotEmpty() }
        back()
        section("SWDA Enrolment")
        inClass("Open SWDA Enrolment Form")
        section("Courseware")
        inClass("Trainer Slides")
        compose.onAllNodesWithText("Learner Slides").assertCountEquals(0)
        compose.onAllNodesWithText("Course Feedback & TRAQOM Survey").assertCountEquals(0)
        section("Assessment")
        inClassTag("publish-practicalExam")
        compose.onNodeWithTag("publish-practicalExam").performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithText("Not published yet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("publish-practicalExam").performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithText("Not published yet").fetchSemanticsNodes().isEmpty() }
        capture("32-trainer-class")
        section("Assessment Summary Record")
        inClass("Sign all 4 as assessor")
        compose.onNodeWithText("Sign all 4 as assessor").performClick()
        waitForText("Assessor signature")
        signAndSave()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Assessor pending").fetchSemanticsNodes().isEmpty() }
        capture("33-trainer-summary-record")
        section("Post Course Feedback Summary")
        inClass("Overall average")
        inClassTag("all-comments")
        compose.onNodeWithTag("all-comments").performClick()
        inClass("Show fewer")
        capture("34-trainer-feedback-summary")
        section("Assessment Grading")
        inClassTag("grading")
        compose.onNodeWithTag("grading").performClick()
        compose.waitUntil(8000) { compose.onAllNodes(hasTestTag("assessments")).fetchSemanticsNodes().isNotEmpty() }
        back()
    }

    @Test
    fun adminAndDeveloperDemo() {
        demoAs("admin")
        waitForText("Running today")
        compose.onNodeWithTag("tab-Home").assertExists()
        compose.onNodeWithTag("tab-Courses").assertExists()
        compose.onNodeWithText("Admin").assertExists()
        compose.onNodeWithText("Next 30 days").assertExists()
        compose.onNodeWithText("Learners today").assertExists()
        compose.onNodeWithText("Hide cancelled classes").assertExists()
        waitForText("Current (2)")
        capture("21-admin-home")
        compose.onNodeWithTag("staff-home").performScrollToNode(hasTestTag("staff-class-s1"))
        compose.onNodeWithTag("staff-class-s1").performClick()
        compose.waitUntil(8000) { compose.onAllNodes(hasTestTag("staff-class-sheet")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Open in Google Maps").assertExists()
        capture("22-admin-class")
        // WhatsApp a trainer: the picker lists every trainer; one without a phone cannot be chosen.
        compose.onNodeWithTag("whatsapp-trainer").performScrollTo().performClick()
        waitForText("No WhatsApp number on profile")
        compose.onNodeWithText("All trainers (2)", ignoreCase = true).assertExists()
        compose.onNodeWithText("+6580000000").assertExists()
        capture("27-admin-whatsapp-trainer")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.waitUntil(8000) { compose.onAllNodes(hasTestTag("staff-class-sheet")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("staff-home").performScrollToNode(hasTestTag("bucket-1"))
        compose.onNodeWithTag("bucket-1").performClick()
        waitForText("Prompt Engineering Workshop")
        // Cancelled classes are hidden until the switch is turned off.
        compose.onAllNodesWithText("Cloud Fundamentals").assertCountEquals(0)
        compose.onNodeWithTag("hide-cancelled").performClick()
        compose.onNodeWithTag("staff-home").performScrollToNode(hasText("Cloud Fundamentals"))

        tab("Calendar")
        waitForText("Academy Calendar")
        compose.waitUntil(8000) { compose.onAllNodes(hasTestTag("staff-class-s1")).fetchSemanticsNodes().isNotEmpty() }
        capture("23-admin-calendar")

        tab("Courses")
        waitForText("Search title, course code or TSC code")
        compose.onNodeWithTag("course-filter-2").performClick()
        waitForText("Legacy Office Skills (Expired)")
        compose.onNodeWithText("Legacy Office Skills (Expired)").performClick()
        waitForText("Duration")
        capture("24-admin-courses")

        // Developer: Home and Courses both show the course catalogue.
        demoAs("developer")
        waitForText("Developer")
        compose.onNodeWithTag("tab-Home").performClick()
        waitForText("With classes")
        compose.onNodeWithTag("staff-home").performScrollToNode(hasText("Building AI Applications"))
        capture("25-developer-home")
    }

    @Test
    fun learnerTrainerNavigation() {
        // Sign-in: email code by default (Continue), password as an option.
        compose.onNodeWithTag("continue").assertIsNotEnabled()
        compose.onNodeWithText("Use password instead").assertExists()
        compose.onNodeWithText("Remember my email").assertExists()
        compose.onNodeWithTag("adminSignIn").assertExists()
        capture("01-login")
        compose.onNodeWithText("Use password instead").performScrollTo().performClick()
        compose.onNodeWithText("LMS/TMS password").assertExists()
        compose.onNodeWithTag("signIn").assertExists()
        compose.onNodeWithText("Use email code instead").performScrollTo().performClick()

        // A scanned quiz QR code while signed out: the quiz is kept, and opens right after sign-in.
        val quizLink = "https://lms-tms.tertiaryinfotech.com/quiz/${DemoApi.QUIZ_COURSE_ID}/${DemoApi.QUIZ_ID}"
        val launchIntent = compose.activity.intent
        compose.activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(quizLink)).setPackage(compose.activity.packageName))
        compose.waitUntil(8000) { compose.onAllNodes(hasTestTag("quiz-pending")).fetchSemanticsNodes().isNotEmpty() }
        // The test rule follows the activity by its launch intent, which onNewIntent has just replaced.
        compose.runOnUiThread { compose.activity.intent = launchIntent }
        compose.onNodeWithTag("continue").assertExists()
        // One role per sign-in, Learner unless Trainer is chosen (Admin comes only from the admin sign-in).
        compose.onNodeWithTag("loginRole").assertExists()
        compose.onAllNodes(hasTestTag("loginRole-admin")).assertCountEquals(0)
        compose.onNodeWithText("See the courses you are taking. Trainers attending a course sign in as a learner.").assertExists()
        compose.onNodeWithText("Try a demo").performScrollTo().performClick()
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
        waitForText("ALEX TAN")
        compose.onNodeWithText("Welcome back").assertExists()
        compose.onNodeWithText("My Profile").assertExists()
        compose.onAllNodes(hasTestTag("viewAsPicker")).assertCountEquals(0)
        compose.onNodeWithText("TIA TMS").assertExists()
        compose.onNodeWithText("DEMO · Fictional sample data").assertExists()
        waitForText("Session 1 · Foundations", substring = true)
        capture("02-home")

        // Class detail: the summary card, then every section collapsed
        openClass()
        compose.onNodeWithTag("class-summary").assertExists()
        compose.onNodeWithTag("summary-trainer").assertExists()
        compose.onNodeWithText("Join Google Meet").assertExists()
        compose.onNodeWithText("Open in Google Maps").assertExists()
        compose.onAllNodesWithText("Scan class QR to check in").assertCountEquals(0)
        capture("03-class")
        section("E-Attendance")
        inClass("Scan class QR to check in")
        inClass("Digital Attendance ID")

        // The quiz again, from the class: the last score comes first, with Retake.
        section("Lesson")
        inClassTag("sample-quiz")
        compose.onNodeWithTag("sample-quiz").performClick()
        waitForText("Your last score")
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithText("Retake quiz").performClick()
        waitForText("Question 1 of 3")
        back()
        waitForClass()

        // Courseware opens in the in-app PDF viewer, with the website's names
        section("Courseware")
        inClass("Learner Slides")
        compose.onNodeWithText("Learner Slides").performClick()
        waitForText("Page 1 of 3")
        compose.onNodeWithText("Open in browser").assertExists()
        compose.onNodeWithContentDescription("Download").assertExists()
        compose.onNodeWithContentDescription("Zoom in").performClick()
        compose.onNodeWithContentDescription("Zoom out").performClick()
        compose.waitUntil(8000) { compose.onAllNodes(hasContentDescription("Page 1")).fetchSemanticsNodes().isNotEmpty() }
        capture("04-pdf")
        back()

        // Digital attendance: the demo simulates a successful scan (sections stay open after the PDF)
        inClass("Scan class QR to check in")
        compose.onNodeWithText("Scan class QR to check in").performClick()
        waitForText("Checked in")
        capture("05-attendance")
        compose.onNodeWithText("OK").performClick()
        inClass("Present")

        // Assessments
        section("Assessment")
        inClassTag("assessment-writtenAssessment")
        compose.onNodeWithTag("assessment-writtenAssessment").performClick()
        waitForText("Written Assessment")
        compose.onAllNodesWithText("View question paper").onFirst().assertExists()
        compose.onAllNodesWithText("Submit file").onFirst().assertExists()
        capture("06-assessment")
        back()

        // Assessment Summary Record: no signature yet, so signing opens the setup sheet first
        section("Assessment Summary Record")
        inClass("Sign my Assessment Summary Record")
        compose.onNodeWithText("Sign my Assessment Summary Record").performClick()
        waitForText("Your signature")
        signAndSave()
        inClass("Open signed record (PDF)")
        capture("19-summary-record")

        // Course feedback
        section("Course Feedback & TRAQOM Survey")
        inClassTag("course-feedback")
        compose.onNodeWithTag("course-feedback").performClick()
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
        inClass("Course feedback sent. Thank you.")
        inClass("TRAQOM Survey")
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
        waitForText("alex@example.com")
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
        waitForText("Use password instead")
        compose.onNodeWithTag("loginRole-trainer").performClick()
        compose.onNodeWithText("See the classes you teach, attendance, payout and training hours.").assertExists()
        compose.onNodeWithText("Try a demo").performScrollTo().performClick()
        waitForText("ALEX TAN")
        compose.onNodeWithTag("my-classes").performScrollToNode(hasText("Classes you teach", ignoreCase = true))
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
        section("E-Attendance")
        inClass("SSG QR Attendance")
        inClass("SSG E-Attendance List")
        inClass("QR code & attendance")
        compose.onAllNodesWithText("QR code & attendance").onFirst().performClick()
        compose.waitUntil(8000) { compose.onAllNodes(hasContentDescription("Check-in QR code")).fetchSemanticsNodes().isNotEmpty() }
        capture("11-trainer-attendance")
        compose.onNodeWithTag("attendance").performScrollToNode(hasText("Mark all present"))
        compose.onNodeWithText("Mark all present").performClick()
        compose.onNodeWithText("Save attendance").performClick()
        waitForText("Attendance is up to date")
        waitUntilGone("Attendance saved for")
        capture("12-trainer-attendance-saved")
        back()
        inClass("4 present")

        // No QR display buttons any more for SWDA, Course Feedback or TRAQOM; the session check-in QR stays.
        compose.onAllNodesWithText("Show TRAQOM Survey QR").assertCountEquals(0)
        section("SWDA Enrolment")
        inClass("Open SWDA Enrolment Form")
        section("Courseware")
        inClass("Trainer Slides")
        compose.onAllNodesWithText("Learner Slides").assertCountEquals(0)

        // The Publish switch per assessment
        section("Assessment")
        inClassTag("publish-practicalExam")
        compose.onNodeWithTag("publish-practicalExam").performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithText("Not published yet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("publish-practicalExam").performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithText("Not published yet").fetchSemanticsNodes().isEmpty() }

        // Assessment Summary Record: sign all as assessor (the assessor signature is set up first)
        section("Assessment Summary Record")
        inClass("Sign all 4 as assessor")
        compose.onNodeWithText("Sign all 4 as assessor").performClick()
        waitForText("Assessor signature")
        signAndSave()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Assessor pending").fetchSemanticsNodes().isEmpty() }
        capture("26-trainer-summary-record")

        // Post Course Feedback Summary
        section("Post Course Feedback Summary")
        inClass("Overall average")
        inClassTag("all-comments")
        compose.onNodeWithTag("all-comments").performClick()
        inClass("Show fewer")
        capture("18-trainer-feedback-summary")

        section("Assessment Grading")
        inClassTag("grading")
        compose.onNodeWithTag("grading").performClick()
        compose.waitUntil(8000) { compose.onAllNodes(hasTestTag("assessments")).fetchSemanticsNodes().isNotEmpty() }
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
        waitForText("Use password instead")
        compose.onNodeWithText("See the courses you are taking. Trainers attending a course sign in as a learner.").assertExists()
    }
}
