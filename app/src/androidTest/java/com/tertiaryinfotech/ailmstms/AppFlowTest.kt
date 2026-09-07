package com.tertiaryinfotech.ailmstms
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
class AppFlowTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun capture(name:String){compose.waitForIdle();android.os.SystemClock.sleep(500);val instrumentation=InstrumentationRegistry.getInstrumentation();val bitmap=instrumentation.uiAutomation.takeScreenshot();File(compose.activity.getExternalFilesDir(null),"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun learnerTrainerNavigation(){
  compose.onNodeWithText("Send sign-in code").assertIsNotEnabled();capture("01-login")
  compose.onNodeWithText("Explore demo").performScrollTo().performClick()
  compose.onNodeWithText("Hello, Alex").assertExists();capture("02-home")
  compose.onNodeWithText("Courses",useUnmergedTree=true).performClick()
  compose.onNodeWithText("Building AI Applications").performClick()
  compose.onNodeWithText("Learner guide").assertExists();capture("03-courseware")
  compose.onNodeWithText("Calendar",useUnmergedTree=true).performClick()
  compose.onNodeWithText("Class calendar").assertExists();capture("04-calendar")
  compose.onNodeWithText("Account",useUnmergedTree=true).performClick()
  compose.onNodeWithText("Trainer",useUnmergedTree=true).performClick()
  compose.onNodeWithText("In demo, enabling reminders shows a sample notification. Live accounts receive scheduled class reminders.").assertExists();capture("05-account")
  val app=compose.activity
  androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS").close()
  compose.onNode(isToggleable()).performClick()
  compose.waitUntil(5000){app.getSystemService(android.app.NotificationManager::class.java).activeNotifications.any{it.id==260901}}
  capture("06-reminder-enabled")
  compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Sign out"))
  compose.onNodeWithText("Sign out").performClick()
  compose.onNodeWithText("Send sign-in code").assertExists()
 }
}
