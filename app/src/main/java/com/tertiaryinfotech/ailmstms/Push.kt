package com.tertiaryinfotech.ailmstms

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tertiaryinfotech.ailmstms.data.ApiException
import com.tertiaryinfotech.ailmstms.data.DocumentDownloader
import com.tertiaryinfotech.ailmstms.data.PdfFetcher
import com.tertiaryinfotech.ailmstms.data.Repository
import com.tertiaryinfotech.ailmstms.data.SessionStore
import com.tertiaryinfotech.ailmstms.notify.ClassAlerts
import com.tertiaryinfotech.ailmstms.notify.PlannedAlert
import java.io.File

class LmsApplication : Application() {
    val repo by lazy { Repository(SessionStore(this)) }
    val pdfs by lazy { PdfFetcher(File(cacheDir, "materials")) }
    val downloads by lazy { DocumentDownloader(File(cacheDir, "downloads")) }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(ClassAlerts.CHANNEL, "Upcoming classes", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Class reminders before each class and on the morning of class" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        if (FirebaseApp.initializeApp(this) != null) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener {
                repo.store.pushToken = it
                PushSyncWorker.enqueue(this)
            }
        }
    }
}

/** Registers the device's push token and the reminder preference with the server. */
class PushSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as LmsApplication).repo
        if (repo.store.token.isBlank() || repo.store.pushToken.isBlank()) return Result.success()
        return try {
            repo.registerPush(repo.store.pushEnabled)
            Result.success()
        } catch (e: ApiException) {
            if (e.status in listOf(401, 403)) Result.failure() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PushSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync_push", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

class ClassMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (application as LmsApplication).repo.store.pushToken = token
        PushSyncWorker.enqueue(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val store = (application as LmsApplication).repo.store
        if (store.token.isBlank() || !store.pushEnabled) return
        val userId = runCatching { org.json.JSONObject(store.profile).optString("id") }.getOrNull()
        if (message.data["userId"] != userId) return
        val delivery = message.data["deliveryId"] ?: message.messageId ?: "class"
        val session = message.data["sessionId"]
        // The server's one-day reminder and the local "class tomorrow" alert share one
        // notification slot, so whichever arrives second updates the first instead of stacking.
        val id = if (session != null && delivery.endsWith(":1")) PlannedAlert.notificationId(session, 1) else delivery.hashCode()
        val body = message.data["body"] ?: "Open your calendar for class details."
        ClassAlerts.show(this, id, message.data["title"] ?: "Upcoming class", body)
    }
}

/** A clearly labelled local preview exercises the notification UI without using real accounts. */
object DemoNotifications {
    const val ID = 260901

    fun show(context: Context) {
        if (!ClassAlerts.canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).putExtra("openCalendar", true)
        val pending = PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, ClassAlerts.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DEMO · Upcoming class")
            .setContentText("Sample reminder: Building AI Applications starts tomorrow. Open your class calendar.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID, notification)
    }
}
