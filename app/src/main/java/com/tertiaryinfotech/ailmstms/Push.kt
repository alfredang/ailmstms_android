package com.tertiaryinfotech.ailmstms

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.*

class LmsApplication:Application() {
 val repo by lazy { Repository(SessionStore(this)) }
 override fun onCreate(){super.onCreate();getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("class_reminders","Upcoming classes",NotificationManager.IMPORTANCE_DEFAULT).apply { description="Class reminders three days and one day before class" }); if(FirebaseApp.initializeApp(this)!=null)FirebaseMessaging.getInstance().token.addOnSuccessListener {repo.store.pushToken=it;PushSyncWorker.enqueue(this)} }
}
class PushSyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
 override suspend fun doWork():Result {
  val repo=(applicationContext as LmsApplication).repo
  if(repo.store.token.isBlank()||repo.store.pushToken.isBlank())return Result.success()
  return try { repo.registerPush(repo.store.pushEnabled);Result.success() }catch(e:ApiException){if(e.status in listOf(401,403))Result.failure() else Result.retry()}catch(e:Exception){Result.retry()}
 }
 companion object {fun enqueue(context:Context){WorkManager.getInstance(context).enqueueUniqueWork("sync_push",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<PushSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())} }
}
class ClassMessagingService:FirebaseMessagingService() {
 override fun onNewToken(token:String){(application as LmsApplication).repo.store.pushToken=token;PushSyncWorker.enqueue(this)}
 override fun onMessageReceived(message:RemoteMessage) {
  val store=(application as LmsApplication).repo.store
  if(store.token.isBlank()||!store.pushEnabled)return
  val userId=runCatching {org.json.JSONObject(store.profile).optString("id")}.getOrNull()
  if(message.data["userId"]!=userId)return
  if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
  val intent=Intent(this,MainActivity::class.java).putExtra("openCalendar",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
  val pending=PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val id=message.data["deliveryId"]?:message.messageId?:"class"
  getSystemService(NotificationManager::class.java).notify(id.hashCode(),NotificationCompat.Builder(this,"class_reminders").setSmallIcon(com.tertiaryinfotech.ailmstms.R.drawable.ic_notification).setContentTitle(message.data["title"]?:"Upcoming class").setContentText(message.data["body"]?:"Open your calendar for class details.").setStyle(NotificationCompat.BigTextStyle().bigText(message.data["body"])).setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
 }
}

// A clearly labelled local preview exercises the notification UI without using real accounts.
object DemoNotifications {
 fun show(context:Context) {
  if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
  val pending=PendingIntent.getActivity(context,260901,Intent(context,MainActivity::class.java).putExtra("openCalendar",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  context.getSystemService(NotificationManager::class.java).notify(260901,NotificationCompat.Builder(context,"class_reminders").setSmallIcon(R.drawable.ic_notification).setContentTitle("DEMO · Upcoming class").setContentText("Sample reminder: Building AI Applications in 3 days. Open your class calendar.").setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
 }
}
