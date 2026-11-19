package com.tertiaryinfotech.ailmstms.notify

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tertiaryinfotech.ailmstms.LmsApplication
import com.tertiaryinfotech.ailmstms.MainActivity
import com.tertiaryinfotech.ailmstms.R
import com.tertiaryinfotech.ailmstms.data.Dashboard
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Local class alerts, scheduled with WorkManager so no exact-alarm permission is needed.
 * Android may deliver them a few minutes late (longer in Doze); they complement the server's
 * push reminders and also work when the device has no push token.
 */
object ClassAlerts {
    const val CHANNEL = "class_reminders"
    private const val TAG = "class_alerts"
    private const val PREFS = "class_alerts"
    private const val KEY = "scheduled"

    private fun workName(key: String) = "class-alert:$key"

    /**
     * Brings the scheduled alerts in line with the user's current classes. Alerts for sessions
     * that disappeared (cancelled, unassigned, withdrawn) are cancelled; an alert that is
     * already scheduled for the same time is left alone, so refreshing never duplicates one.
     */
    fun sync(context: Context, dashboard: Dashboard, now: Instant = Instant.now()) {
        val app = context.applicationContext
        val work = WorkManager.getInstance(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY, emptySet()).orEmpty().associate { it.substringBeforeLast('|') to it.substringAfterLast('|') }
        val plan = AlertPlanner.plan(dashboard, now)
        val wanted = plan.associateBy { it.key }

        before.keys.filter { it !in wanted }.forEach { work.cancelUniqueWork(workName(it)) }
        for (alert in plan) {
            val stamp = alert.at.epochSecond.toString()
            if (before[alert.key] == stamp) continue
            val request = OneTimeWorkRequestBuilder<ClassAlertWorker>()
                .setInitialDelay(Duration.between(now, alert.at).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        "id" to alert.notificationId, "title" to alert.title, "body" to alert.body,
                        "startsAt" to alert.startsAt.epochSecond,
                    ),
                )
                .addTag(TAG)
                .build()
            work.enqueueUniqueWork(workName(alert.key), ExistingWorkPolicy.REPLACE, request)
        }
        prefs.edit().putStringSet(KEY, plan.map { "${it.key}|${it.at.epochSecond}" }.toSet()).apply()
    }

    /** Sign-out, or reminders switched off: nothing may fire afterwards. */
    fun cancelAll(context: Context) {
        val app = context.applicationContext
        WorkManager.getInstance(app).cancelAllWorkByTag(TAG)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, id: Int, title: String, body: String) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).putExtra("openCalendar", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}

class ClassAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = (applicationContext as LmsApplication).repo.store
        // Signed out or reminders off since this was scheduled: stay silent.
        if (store.token.isBlank() || !store.pushEnabled) return Result.success()
        // The device was off past the start of class: a late "class today" is noise.
        if (Instant.now().epochSecond > inputData.getLong("startsAt", Long.MAX_VALUE)) return Result.success()
        ClassAlerts.show(
            applicationContext,
            inputData.getInt("id", 0),
            inputData.getString("title") ?: "Upcoming class",
            inputData.getString("body") ?: "Open your calendar for class details.",
        )
        return Result.success()
    }
}
