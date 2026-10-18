package ru.tabletki.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.tabletki.core.Dose
import ru.tabletki.core.DoseStatus
import ru.tabletki.core.Medicine
import ru.tabletki.core.Schedule
import ru.tabletki.data.Store
import ru.tabletki.reminders.Notifier
import ru.tabletki.reminders.Reminders
import ru.tabletki.ui.AlarmActivity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Заполняет приложение примером для скриншотов (только debug-сборка):
 * adb shell am broadcast -n <пакет>/ru.tabletki.debug.DemoDataReceiver -a ru.tabletki.debug.SEED
 */
class DemoDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ru.tabletki.debug.SEED" -> seed(context)
            "ru.tabletki.debug.ALARM" -> AlarmActivity.open(context)
            "ru.tabletki.debug.SOON" -> addSoon(context)
            "ru.tabletki.debug.CLEAR" -> Store.get(context).replaceAll(emptyList(), emptyList())
        }
    }

    private fun seed(context: Context) {
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        val today = now.toLocalDate()
        // Время выбрано относительно «сейчас», чтобы на экране были и принятые, и текущие, и будущие приёмы.
        val past = now.minusHours(2).toLocalTime().roundToHour()
        val due = now.minusMinutes(3).toLocalTime()
        val later = now.plusHours(3).toLocalTime().roundToHour()
        val start = today.minusDays(10)

        val enalapril = Medicine(1, "Эналаприл", "1 таблетка до еды", Schedule(listOf(past, later), start), 1.0, 6.0)
        val metformin = Medicine(2, "Метформин", "1 таблетка во время еды", Schedule(listOf(due), start), 1.0, 48.0)
        val vitamin = Medicine(3, "Витамин D", "2 капли", Schedule(listOf(later), start), 1.0, null)
        val meds = listOf(enalapril, metformin, vitamin)

        val doses = mutableListOf<Dose>()
        var id = 1L
        for (back in 6 downTo 1) {
            val day = today.minusDays(back.toLong())
            for (m in meds) for (t in m.schedule.sortedTimes) {
                val at = day.atTime(t)
                val status = if (back == 2 && m.id == 2L) DoseStatus.MISSED else DoseStatus.TAKEN
                doses += Dose(id++, m.id, at, status, actedAt = if (status == DoseStatus.TAKEN) at.plusMinutes(4) else null)
            }
        }
        doses += Dose(id++, 1, today.atTime(past), DoseStatus.TAKEN, actedAt = today.atTime(past).plusMinutes(3))
        doses += Dose(id, 2, today.atTime(due), DoseStatus.PENDING, remindAt = now.plusMinutes(7), remindCount = 1)

        Store.get(context).replaceAll(meds, doses)
        Notifier.update(context, alert = false)
        Reminders.refresh(context)
    }

    /** Лекарство с приёмом в начале следующей минуты, чтобы проверить настоящий будильник. */
    private fun addSoon(context: Context) {
        val next = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        val store = Store.get(context)
        store.saveMedicine(
            Medicine(0, "Проверка", "1 таблетка", Schedule(listOf(next.toLocalTime()), next.toLocalDate())),
        )
        Reminders.refresh(context)
    }

    private fun LocalTime.roundToHour(): LocalTime = withMinute(0)
}
