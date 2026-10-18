package ru.tabletki.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.tabletki.R
import ru.tabletki.data.Store
import ru.tabletki.ui.AlarmActivity

object Notifier {
    private const val CHANNEL_ALARM = "reminders"
    private const val NOTIFICATION_DUE = 1
    private const val NOTIFICATION_TEST = 2

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ALARM, "Напоминания о приёме", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Сигнал, когда пора принять лекарство"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    /**
     * Показывает одно уведомление со всеми лекарствами, которые пора принять,
     * или убирает его, если таких нет. [alert] — со звуком и экраном поверх блокировки.
     */
    fun update(context: Context, alert: Boolean) {
        val due = Reminders.dueNow(context)
        val nm = NotificationManagerCompat.from(context)
        if (due.isEmpty()) {
            nm.cancel(NOTIFICATION_DUE)
            AlarmActivity.closeIfOpen()
            return
        }
        if (!canNotify(context)) {
            if (alert) AlarmActivity.open(context)
            return
        }
        ensureChannel(context)
        val store = Store.get(context)
        val names = due.mapNotNull { d -> store.medicine(d.medicineId)?.let { "${it.name}, ${it.dose}" } }
        val title = if (due.size == 1) "Пора принять лекарство" else "Пора принять лекарства: ${due.size}"
        val text = names.joinToString("\n")

        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(names.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setContentIntent(activityIntent(context))
            .addAction(0, "Принял", actionIntent(context, NotificationActionReceiver.ACTION_TAKE_ALL))
            .addAction(0, "Отложить на 10 мин", actionIntent(context, NotificationActionReceiver.ACTION_SNOOZE_ALL))
        if (alert) {
            builder.setFullScreenIntent(activityIntent(context), true)
            builder.setSilent(false)
        } else {
            builder.setSilent(true)
        }
        try {
            nm.notify(NOTIFICATION_DUE, builder.build())
        } catch (_: SecurityException) {
            // Разрешение на уведомления отозвали между проверкой и показом.
        }
    }

    fun showTest(context: Context) {
        if (!canNotify(context)) {
            AlarmActivity.open(context, test = true)
            return
        }
        ensureChannel(context)
        val intent = PendingIntent.getActivity(
            context, 3, AlarmActivity.intent(context, test = true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Проверка напоминания")
            .setContentText("Всё работает: напоминания будут приходить вовремя.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setFullScreenIntent(intent, true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TEST, n)
        } catch (_: SecurityException) {
        }
    }

    fun cancelTest(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_TEST)

    private fun activityIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 1, AlarmActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun actionIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, NotificationActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
