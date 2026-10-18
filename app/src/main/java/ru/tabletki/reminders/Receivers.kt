package ru.tabletki.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false)) {
            Notifier.showTest(context)
        } else {
            Reminders.refresh(context)
        }
    }
}

/** После перезагрузки, обновления приложения или смены времени будильник надо поставить заново. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.refresh(context)
    }
}

/** Кнопки «Принял» и «Отложить» прямо в уведомлении. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TAKE_ALL -> Reminders.takeAllDue(context)
            ACTION_SNOOZE_ALL -> Reminders.snoozeAllDue(context)
        }
    }

    companion object {
        const val ACTION_TAKE_ALL = "ru.tabletki.TAKE_ALL"
        const val ACTION_SNOOZE_ALL = "ru.tabletki.SNOOZE_ALL"
    }
}
