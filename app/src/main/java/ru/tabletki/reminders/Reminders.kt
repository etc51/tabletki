package ru.tabletki.reminders

import android.content.Context
import ru.tabletki.core.Dose
import ru.tabletki.core.DoseStatus
import ru.tabletki.core.Planner
import ru.tabletki.data.Store
import java.time.Duration
import java.time.LocalDateTime

/**
 * Связывает [Planner] с Android: записывает результат в базу, звонит и ставит
 * следующий будильник. Любое изменение данных заканчивается вызовом [refresh].
 */
object Reminders {
    val SNOOZE: Duration = Duration.ofMinutes(10)

    private fun now(): LocalDateTime = LocalDateTime.now().withNano(0)

    @Synchronized
    fun refresh(context: Context) {
        val store = Store.get(context)
        val now = now()
        val lastCheck = store.lastCheck ?: now
        val plan = Planner.reconcile(store.medicines(), store.pendingDoses(), lastCheck, now)

        val inserted = store.insertDoses(plan.created)
        val insertedKeys = inserted.map { it.medicineId to it.scheduledAt }.toSet()
        plan.updated.forEach { store.saveDose(it) }
        store.lastCheck = now

        // Если приём уже отметили заранее, запись не добавилась, и звонить не надо.
        val ringing = plan.ringing.filter { it.id != 0L || (it.medicineId to it.scheduledAt) in insertedKeys }
        Notifier.update(context, alert = ringing.isNotEmpty())
        AlarmScheduler.schedule(context, plan.nextWake)
    }

    /** Приёмы, о которых сейчас напоминаем: уже прозвенели и ещё не отмечены. */
    fun dueNow(context: Context): List<Dose> =
        Store.get(context).pendingDoses().filter { it.remindCount > 0 }

    fun take(context: Context, dose: Dose) {
        val store = Store.get(context)
        if (dose.status == DoseStatus.TAKEN) return
        store.saveDose(Planner.take(dose, now()))
        store.medicine(dose.medicineId)?.let { store.changeStock(it.id, -it.unitsPerDose) }
        refresh(context)
    }

    fun skip(context: Context, dose: Dose) {
        val store = Store.get(context)
        if (dose.status == DoseStatus.TAKEN) {
            store.medicine(dose.medicineId)?.let { store.changeStock(it.id, it.unitsPerDose) }
        }
        store.saveDose(Planner.skip(dose, now()))
        refresh(context)
    }

    /** Отмена ошибочной отметки «Принял»: остаток возвращается, приём считается непринятым. */
    fun undo(context: Context, dose: Dose) {
        val store = Store.get(context)
        if (dose.status == DoseStatus.TAKEN) {
            store.medicine(dose.medicineId)?.let { store.changeStock(it.id, it.unitsPerDose) }
        }
        val reopened = if (dose.scheduledAt > now()) {
            dose.copy(status = DoseStatus.PENDING, remindAt = dose.scheduledAt, remindCount = 0, actedAt = null)
        } else {
            dose.copy(status = DoseStatus.MISSED, remindAt = null, actedAt = null)
        }
        store.saveDose(reopened)
        refresh(context)
    }

    fun snooze(context: Context, dose: Dose) {
        Store.get(context).saveDose(Planner.snooze(dose, now(), SNOOZE))
        refresh(context)
    }

    fun takeAllDue(context: Context) = dueNow(context).forEach { take(context, it) }

    fun snoozeAllDue(context: Context) = dueNow(context).forEach { snooze(context, it) }
}
