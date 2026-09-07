package com.tertiaryinfotech.ailmstms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity:ComponentActivity() {
 private var calendarRequested by mutableStateOf(false)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();calendarRequested=intent.getBooleanExtra("openCalendar",false);setContent { LmsTheme { Root(calendarRequested) } } }
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);calendarRequested=intent.getBooleanExtra("openCalendar",false)}
}
private val SchoolIcon by lazy {
 androidx.compose.ui.graphics.vector.ImageVector.Builder("School",24.dp,24.dp,24f,24f).apply {
  addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M2,8 L12,3 L22,8 L12,13 Z M5,12 L12,15 L19,12 L19,18 L12,21 L5,18 Z").toNodes(),fill=androidx.compose.ui.graphics.SolidColor(Color.Black))
 }.build()
}
private val Teal=Color(0xFF087F73)
@Composable fun LmsTheme(content:@Composable ()->Unit) {
 val dark=isSystemInDarkTheme()
 MaterialTheme(colorScheme=if(dark)darkColorScheme(primary=Color(0xFF79DACA),secondary=Color(0xFFBEDCCF)) else lightColorScheme(primary=Teal,secondary=Color(0xFF46665E),background=Color(0xFFF5F8F6),surface=Color(0xFFFBFDFB)),content=content)
}
fun openLink(context:android.content.Context,url:String){if(isSafeLink(url)) runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)))}.onFailure {android.widget.Toast.makeText(context,"No app is available to open this link.",android.widget.Toast.LENGTH_LONG).show()} }
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Root(openCalendar:Boolean=false,vm:LmsViewModel=viewModel()) {
 val context=LocalContext.current
 var tab by rememberSaveable { mutableIntStateOf(0) }
 var selected by remember {mutableStateOf<Course?>(null)}
 LaunchedEffect(openCalendar){if(openCalendar)tab=2}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)vm.setPush(true)}
 val tabs=listOf("Home" to Icons.Default.Home,"Courses" to SchoolIcon,"Calendar" to Icons.Default.DateRange,"Account" to Icons.Default.Person)
 Scaffold(topBar={TopAppBar(title={Text("AI LMS TMS",fontWeight=FontWeight.Bold)},actions={if(vm.profile!=null) IconButton(onClick=vm::refresh,enabled=!vm.busy){Icon(Icons.Default.Refresh,"Refresh classes")}})},bottomBar={if(vm.profile!=null)NavigationBar { tabs.forEachIndexed { i,item ->NavigationBarItem(selected=tab==i,onClick={tab=i;selected=null},icon={Icon(item.second,null)},label={Text(item.first)})} }}) { padding ->
 Column(Modifier.fillMaxSize().padding(padding)) {
  if(vm.isDemo)Surface(color=MaterialTheme.colorScheme.tertiaryContainer){Text("DEMO · Fictional sample courses",Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),style=MaterialTheme.typography.labelMedium)}
  if(vm.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  vm.error?.let{Surface(color=MaterialTheme.colorScheme.errorContainer){Text(it,Modifier.fillMaxWidth().padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer)}}
  if(vm.profile==null)Login(vm) else when(tab) {
   0->Home(vm,onCourses={tab=1},onCalendar={tab=2})
   1->if(selected!=null)CourseDetails(selected!!){selected=null} else Courses(vm.dashboard.courses){selected=it}
   2->CalendarScreen(vm.dashboard.sessions)
   else->Account(vm,onNotifications={enabled ->
    if(!enabled)vm.setPush(false)
    else if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    else if(!NotificationManagerCompat.from(context).areNotificationsEnabled())context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
    else vm.setPush(true)
   })
  }
 }
 }
}
@Composable private fun Login(vm:LmsViewModel) {
 val context=LocalContext.current
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
  Spacer(Modifier.height(12.dp))
  Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(SchoolIcon,null,Modifier.padding(20.dp).size(48.dp),tint=MaterialTheme.colorScheme.primary)}
  Text("Your learning.\nAll in one place.",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
  Text("Courseware, class dates and timely reminders for Tertiary learners and trainers.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Card {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   Text(if(vm.sent)"Check your email" else "Welcome back",style=MaterialTheme.typography.titleLarge)
   OutlinedTextField(vm.email,{vm.email=it},label={Text("Registered email address")},singleLine=true,enabled=!vm.sent&&!vm.busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
   if(vm.sent){Text("Enter the 6-digit code sent to your email. Codes expire after 30 minutes.");OutlinedTextField(vm.otp,{vm.otp=it.filter(Char::isDigit).take(6)},label={Text("One-time code")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.fillMaxWidth());Button(onClick=vm::login,enabled=vm.otp.length==6&&!vm.busy,modifier=Modifier.fillMaxWidth()){Text("Sign in")};TextButton(onClick=vm::changeEmail,enabled=!vm.busy){Text("Change email or request another code")}}
   else Button(onClick=vm::sendOtp,enabled=android.util.Patterns.EMAIL_ADDRESS.matcher(vm.email.trim()).matches()&&!vm.busy,modifier=Modifier.fillMaxWidth()){Text("Send sign-in code")}
  }}
  TextButton(onClick=vm::enterDemo,enabled=!vm.busy){Text("Explore demo")}
  Text("Use the email registered with your training provider. Learner and trainer accounts only.",style=MaterialTheme.typography.bodySmall)
  Row {TextButton(onClick={openLink(context,"https://lms-tms.tertiaryinfotech.com/mobile/privacy.html")}){Text("Privacy policy")};TextButton(onClick={openLink(context,"https://wa.me/6588666375")}){Text("Need help?")}}
 }
}
@Composable private fun Home(vm:LmsViewModel,onCourses:()->Unit,onCalendar:()->Unit) {
 val upcoming=vm.dashboard.sessions.filter{it.endsAt>Instant.now()}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
  item {Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF064E48),Teal)),RoundedCornerShape(24.dp)).padding(24.dp)){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(vm.role.uppercase()+" SPACE",color=Color(0xFFB4ECDD),style=MaterialTheme.typography.labelLarge);Text("Hello, ${vm.profile?.name?.substringBefore(' ')}",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Color.White);Text("Make room for what’s next.",color=Color.White);Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){AssistChip(onClick=onCourses,label={Text("${vm.dashboard.courses.size} courses",color=Color.White)});AssistChip(onClick=onCalendar,label={Text("${upcoming.size} upcoming",color=Color.White)})}}}}
  item {Heading("Next on your calendar")}
  if(upcoming.isEmpty())item{EmptyCard("No upcoming classes","Your assigned classes will appear here. Refresh to check for changes.")}
  items(upcoming.take(3),key={it.id}){SessionCard(it)}
  item {Card(onClick=onCourses){Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.List,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text("Your courseware",style=MaterialTheme.typography.titleMedium);Text("Slides, guides and hands-on activities")};Icon(Icons.Default.KeyboardArrowRight,null)}}}
  item{Text("Class times are shown in Singapore time (SGT).",style=MaterialTheme.typography.bodySmall)}
 }
}
@Composable private fun Heading(text:String){Text(text,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
@Composable private fun EmptyCard(title:String,body:String){OutlinedCard {Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
@Composable private fun Courses(courses:List<Course>,select:(Course)->Unit) {
 var query by rememberSaveable{mutableStateOf("")}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  item{Heading("My courses")}
  item{OutlinedTextField(query,{query=it},label={Text("Search courses")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true)}
  if(courses.isEmpty())item{EmptyCard("No courses yet","Only courses you are enrolled in or assigned to teach appear here.")}
  items(courses.filter{it.title.contains(query,true)||it.code.contains(query,true)},key={it.id}){course->Card(onClick={select(course)}){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(course.code,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(course.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text("${course.start.take(10)} – ${course.end.take(10)}");Text("${course.resources.size} courseware resources  →",color=MaterialTheme.colorScheme.primary)}}}
 }
}
@Composable private fun CourseDetails(course:Course,back:()->Unit) {
 val context=LocalContext.current
 androidx.activity.compose.BackHandler(onBack=back)
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  item{TextButton(onClick=back){Icon(Icons.AutoMirrored.Filled.ArrowBack,null);Text("All courses")}}
  item{Text(course.code,color=MaterialTheme.colorScheme.primary);Heading(course.title)}
  item{Text(listOf(course.format,course.venue).filter{it.isNotBlank()}.joinToString(" · "))}
  item{Text("Courseware",style=MaterialTheme.typography.titleMedium)}
  if(course.resources.isEmpty())item{EmptyCard("Materials coming soon","Your training provider has not published materials for this course yet.")}
  items(course.resources){(label,url)->OutlinedCard(onClick={openLink(context,url)}){Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.List,null);Text(label,Modifier.weight(1f).padding(horizontal=16.dp));Icon(Icons.AutoMirrored.Filled.ArrowForward,"Open $label")}}}
  item{Text("Resources open in your browser or document app. Google Drive may ask you to sign in with your registered account.",style=MaterialTheme.typography.bodySmall)}
 }
}
@Composable private fun CalendarScreen(sessions:List<ClassSession>) {
 var monthValue by rememberSaveable{mutableStateOf(YearMonth.now(ZoneId.of("Asia/Singapore")).toString())}
 var chosenValue by rememberSaveable{mutableStateOf<String?>(null)}
 val month=YearMonth.parse(monthValue)
 val filtered=sessions.filter{YearMonth.from(it.date)==month && (chosenValue==null||it.date.toString()==chosenValue)}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  item{Heading("Class calendar")}
  item{Card {Column(Modifier.padding(12.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={monthValue=month.minusMonths(1).toString();chosenValue=null}){Icon(Icons.Default.KeyboardArrowLeft,"Previous month")};Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);IconButton(onClick={monthValue=month.plusMonths(1).toString();chosenValue=null}){Icon(Icons.Default.KeyboardArrowRight,"Next month")}}
   Row {listOf("M","T","W","T","F","S","S").forEach{Text(it,Modifier.weight(1f).padding(vertical=8.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}
   val offset=month.atDay(1).dayOfWeek.value-1
   (0 until (offset+month.lengthOfMonth()+6)/7).forEach{week->Row{(0..6).forEach{weekday->val day=week*7+weekday-offset+1
    Box(Modifier.weight(1f).heightIn(min=48.dp),contentAlignment=Alignment.Center){if(day in 1..month.lengthOfMonth()){val date=month.atDay(day);val has=sessions.any{it.date==date};TextButton(onClick={chosenValue=if(chosenValue==date.toString())null else date.toString()},contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.textButtonColors(containerColor=if(chosenValue==date.toString())MaterialTheme.colorScheme.primaryContainer else Color.Transparent)){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(day.toString(),fontWeight=if(has)FontWeight.Bold else FontWeight.Normal);if(has)Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(4.dp)))}}}}
   }}}
  }}}
  item{Row(verticalAlignment=Alignment.CenterVertically){Text(if(chosenValue==null)"Classes this month" else chosenValue!!,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={monthValue=YearMonth.now(ZoneId.of("Asia/Singapore")).toString();chosenValue=null}){Text("Today")}}}
  if(filtered.isEmpty())item{EmptyCard("No classes scheduled","Select another date or month to explore your calendar.")}
  items(filtered,key={it.id}){SessionCard(it)}
 }
}
@Composable private fun SessionCard(session:ClassSession) {
 val context=LocalContext.current
 Card {Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text(session.whenText,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
  Text(session.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
  Text(listOf(session.subtitle,session.format,session.venue).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   TextButton(onClick={val intent=Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE,session.title).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,session.startsAt.toEpochMilli()).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,session.endsAt.toEpochMilli()).putExtra(CalendarContract.Events.EVENT_LOCATION,session.venue).putExtra(CalendarContract.Events.EVENT_TIMEZONE,"Asia/Singapore");runCatching{context.startActivity(intent)}.onFailure{android.widget.Toast.makeText(context,"No calendar app is installed.",android.widget.Toast.LENGTH_LONG).show()}}){Icon(Icons.Default.DateRange,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Add to calendar")}
   if(isSafeLink(session.meeting))TextButton(onClick={openLink(context,session.meeting)}){Text("Join class")}
  }
 }}
}
@Composable private fun Account(vm:LmsViewModel,onNotifications:(Boolean)->Unit) {
 val context=LocalContext.current
 var feedbackTitle by rememberSaveable{mutableStateOf("")}
 var feedback by rememberSaveable{mutableStateOf("")}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
  item{Heading("Your account")}
  item{Card{Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(vm.profile?.name.orEmpty(),style=MaterialTheme.typography.titleLarge);Text(vm.profile?.email.orEmpty());Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){vm.profile?.roles?.forEach{r->FilterChip(selected=vm.role==r,onClick={vm.switchRole(r)},label={Text(r.replaceFirstChar(Char::titlecase))},enabled=!vm.busy)}}}}}
  item{Card {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Notifications,null);Text("Class reminders",Modifier.weight(1f).padding(horizontal=12.dp),style=MaterialTheme.typography.titleMedium);Switch(checked=vm.pushEnabled,onCheckedChange=onNotifications,enabled=!vm.busy)};if(vm.isDemo)Text("In demo, enabling reminders shows a sample notification. Live accounts receive scheduled class reminders.");Text("A reminder 3 days and 1 day before each class, at 9:00 AM Singapore time. Delivery requires an internet connection and notification permission.");TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Open notification settings")}}}}
  item{Heading("Help & feedback")}
  item{OutlinedTextField(feedbackTitle,{feedbackTitle=it},label={Text("Feedback title")},singleLine=true,modifier=Modifier.fillMaxWidth())}
  item{OutlinedTextField(feedback,{feedback=it},label={Text("How can we help?")},minLines=3,modifier=Modifier.fillMaxWidth())}
  item{OutlinedButton(onClick={openLink(context,"https://wa.me/6588666375?text="+java.net.URLEncoder.encode("AI LMS TMS Android feedback\n$feedbackTitle\n$feedback","UTF-8"))},enabled=feedback.isNotBlank()){Text("Send via WhatsApp")}}
  item{Heading("About AI LMS TMS")}
  item{Text("Your courses and teaching schedule, connected to Tertiary Infotech Academy’s learning and training management system.");Text("Tertiary Infotech Academy Pte. Ltd.",Modifier.padding(top=12.dp),fontWeight=FontWeight.SemiBold);Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",style=MaterialTheme.typography.bodySmall)}
  item{TextButton(onClick={openLink(context,"https://lms-tms.tertiaryinfotech.com/mobile/privacy.html")}){Text("Privacy policy")};TextButton(onClick={openLink(context,"https://www.tertiaryinfotech.com")}){Text("Developer website")};TextButton(onClick={val intent=Intent(Intent.ACTION_SENDTO,android.net.Uri.parse("mailto:sales@tertiarycourses.com.sg")).putExtra(Intent.EXTRA_SUBJECT,"AI LMS TMS account deletion request");runCatching{context.startActivity(intent)}}){Text("Request account deletion")};Text("Contact the training provider to request deletion. Training records subject to legal retention requirements may be retained.",style=MaterialTheme.typography.bodySmall)}
  item{OutlinedButton(onClick=vm::logout,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Text("Sign out")}}
 }
}
