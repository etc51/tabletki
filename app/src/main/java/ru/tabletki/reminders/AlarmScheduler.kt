package ru.tabletki.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.tabletki.ui.MainActivity
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Держит один системный будильник на ближайшее событие. setAlarmClock —
 * самый надёжный способ сработать вовремя: система не откладывает его даже
 * в режиме экономии энергии.
 */
object AlarmScheduler {
    private const val REQUEST_MAIN = 1
    private const val REQUEST_TEST = 2
    const val EXTRA_TEST = "test"

    fun schedule(context: Context, at: LocalDateTime?) {
        val pi = receiverIntent(context, REQUEST_MAIN, test = false)
        val am = context.getSystemService(AlarmManager::class.java)
        if (at == null) {
            am.cancel(pi)
            return
        }
        setAlarm(context, am, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    /** Пробное напоминание через минуту, чтобы проверить, что телефон его не глушит. */
    fun scheduleTest(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        setAlarm(context, am, System.currentTimeMillis() + 60_000, receiverIntent(context, REQUEST_TEST, test = true))
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun setAlarm(context: Context, am: AlarmManager, epochMillis: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact(context)) {
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(epochMillis, show), pi)
                return
            }
        } catch (_: SecurityException) {
            // Разрешение на точные будильники отозвали, ставим обычный.
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pi)
    }

    private fun receiverIntent(context: Context, requestCode: Int, test: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_TEST, test),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
