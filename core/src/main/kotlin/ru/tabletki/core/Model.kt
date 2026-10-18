package ru.tabletki.core

import java.time.LocalDateTime
import kotlin.math.floor

data class Medicine(
    val id: Long,
    val name: String,
    /** Как принимать, своими словами: «1 таблетка после еды». */
    val dose: String,
    val schedule: Schedule,
    /** Сколько единиц уходит за один приём, для учёта остатка. */
    val unitsPerDose: Double = 1.0,
    /** Остаток в упаковке, null — не считать. */
    val stock: Double? = null,
    val active: Boolean = true,
) {
    /** На сколько полных дней хватит остатка, null — остаток не ведётся. */
    fun daysLeft(): Int? {
        val left = stock ?: return null
        val perDay = schedule.dosesPerDay() * unitsPerDose
        if (perDay <= 0.0) return null
        return floor(left / perDay).toInt().coerceAtLeast(0)
    }

    fun isRunningLow(warnDays: Int = LOW_STOCK_DAYS): Boolean =
        daysLeft()?.let { it <= warnDays } ?: false

    companion object {
        const val LOW_STOCK_DAYS = 5
    }
}

enum class DoseStatus { PENDING, TAKEN, SKIPPED, MISSED }

/** Один запланированный приём и что с ним стало. */
data class Dose(
    val id: Long,
    val medicineId: Long,
    val scheduledAt: LocalDateTime,
    val status: DoseStatus,
    /** Когда звонить в следующий раз, для PENDING. */
    val remindAt: LocalDateTime? = null,
    val remindCount: Int = 0,
    val actedAt: LocalDateTime? = null,
)
