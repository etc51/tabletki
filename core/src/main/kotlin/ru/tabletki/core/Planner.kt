package ru.tabletki.core

import java.time.Duration
import java.time.LocalDateTime

data class ReminderSettings(
    /** Через сколько повторять сигнал, если не нажали «Принял». */
    val repeatEvery: Duration = Duration.ofMinutes(10),
    /** Сколько раз звонить, прежде чем отметить приём пропущенным. */
    val maxRings: Int = 3,
    /** Приёмы старше этого (телефон был выключен) сразу считаются пропущенными. */
    val staleAfter: Duration = Duration.ofHours(3),
    /** Дальше этого назад не ищем приёмы после долгого простоя. */
    val maxLookBack: Duration = Duration.ofDays(2),
)

data class PlanResult(
    /** Новые приёмы, которых ещё нет в журнале (id = 0). */
    val created: List<Dose>,
    /** Изменённые существующие приёмы. */
    val updated: List<Dose>,
    /** Приёмы, о которых надо напомнить прямо сейчас. */
    val ringing: List<Dose>,
    /** Когда разбудить телефон в следующий раз, null — нечего ждать. */
    val nextWake: LocalDateTime?,
)

/**
 * Вся логика напоминаний без Android: по списку лекарств, незакрытым приёмам
 * и времени последней проверки решает, что записать, о чём звонить и когда
 * проснуться снова. Вызывается по будильнику, после перезагрузки и после
 * любого изменения данных.
 */
object Planner {

    fun reconcile(
        medicines: List<Medicine>,
        pending: List<Dose>,
        lastCheck: LocalDateTime,
        now: LocalDateTime,
        settings: ReminderSettings = ReminderSettings(),
    ): PlanResult {
        val active = medicines.filter { it.active }
        val from = maxOf(lastCheck, now.minus(settings.maxLookBack))
        val staleBefore = now.minus(settings.staleAfter)

        val found = mutableListOf<Dose>()
        for (med in active) {
            for (at in med.schedule.occurrencesBetween(from, now)) {
                found += if (at < staleBefore) {
                    Dose(0, med.id, at, DoseStatus.MISSED)
                } else {
                    Dose(0, med.id, at, DoseStatus.PENDING, remindAt = at)
                }
            }
        }

        val activeIds = active.map { it.id }.toSet()
        val ringing = mutableListOf<Dose>()

        fun process(dose: Dose): Dose {
            if (dose.status != DoseStatus.PENDING) return dose
            val remindAt = dose.remindAt ?: dose.scheduledAt
            return when {
                dose.medicineId !in activeIds || dose.scheduledAt < staleBefore ->
                    dose.copy(status = DoseStatus.MISSED, remindAt = null)
                remindAt > now -> dose
                dose.remindCount >= settings.maxRings ->
                    dose.copy(status = DoseStatus.MISSED, remindAt = null)
                else -> dose.copy(remindCount = dose.remindCount + 1, remindAt = now.plus(settings.repeatEvery))
                    .also { ringing += it }
            }
        }

        val existing = pending.filter { it.status == DoseStatus.PENDING }
        val processed = existing.map(::process)
        val updated = processed.filterIndexed { i, d -> d != existing[i] }
        val created = found.map(::process)

        val stillPending = (processed + created).filter { it.status == DoseStatus.PENDING }
        val nextOccurrence = active.mapNotNull { it.schedule.nextAfter(now) }.minOrNull()
        val nextRepeat = stillPending.mapNotNull { it.remindAt }.minOrNull()
        val nextWake = listOfNotNull(nextOccurrence, nextRepeat).minOrNull()

        return PlanResult(created, updated, ringing, nextWake)
    }

    fun take(dose: Dose, now: LocalDateTime): Dose =
        dose.copy(status = DoseStatus.TAKEN, remindAt = null, actedAt = now)

    fun skip(dose: Dose, now: LocalDateTime): Dose =
        dose.copy(status = DoseStatus.SKIPPED, remindAt = null, actedAt = now)

    /** Отложить: позвонить снова через [delay], счётчик повторов начинается заново. */
    fun snooze(dose: Dose, now: LocalDateTime, delay: Duration): Dose =
        dose.copy(status = DoseStatus.PENDING, remindAt = now.plus(delay), remindCount = 0)
}
