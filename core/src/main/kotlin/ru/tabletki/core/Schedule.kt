package ru.tabletki.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class Repeat { DAILY, WEEKDAYS, EVERY_N_DAYS }

/**
 * Когда принимать лекарство. Время хранится как локальное время телефона,
 * поэтому смена часового пояса не сдвигает «08:00» на другое время.
 */
data class Schedule(
    val times: List<LocalTime>,
    val startDate: LocalDate,
    val repeat: Repeat = Repeat.DAILY,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val everyNDays: Int = 1,
    /** Последний день курса включительно, null — бессрочно. */
    val endDate: LocalDate? = null,
) {
    val sortedTimes: List<LocalTime> = times.distinct().sorted()

    fun isActiveOn(date: LocalDate): Boolean {
        if (date < startDate) return false
        if (endDate != null && date > endDate) return false
        return when (repeat) {
            Repeat.DAILY -> true
            Repeat.WEEKDAYS -> date.dayOfWeek in weekdays
            Repeat.EVERY_N_DAYS ->
                ChronoUnit.DAYS.between(startDate, date) % everyNDays.coerceAtLeast(1) == 0L
        }
    }

    fun occurrencesOn(date: LocalDate): List<LocalDateTime> =
        if (isActiveOn(date)) sortedTimes.map { date.atTime(it) } else emptyList()

    /** Приёмы в интервале (from, to]. */
    fun occurrencesBetween(from: LocalDateTime, to: LocalDateTime): List<LocalDateTime> {
        if (to <= from) return emptyList()
        val result = mutableListOf<LocalDateTime>()
        var day = from.toLocalDate()
        while (day <= to.toLocalDate()) {
            occurrencesOn(day).filterTo(result) { it > from && it <= to }
            day = day.plusDays(1)
        }
        return result
    }

    /** Ближайший приём строго после [after], либо null, если курс закончился. */
    fun nextAfter(after: LocalDateTime): LocalDateTime? {
        if (sortedTimes.isEmpty()) return null
        if (repeat == Repeat.WEEKDAYS && weekdays.isEmpty()) return null
        var day = maxOf(after.toLocalDate(), startDate)
        // Достаточно перебрать один полный цикл повторения.
        val horizon = day.plusDays(maxOf(7, everyNDays).toLong() + 1)
        while (day <= horizon) {
            if (endDate != null && day > endDate) return null
            occurrencesOn(day).firstOrNull { it > after }?.let { return it }
            day = day.plusDays(1)
        }
        return null
    }

    /** Среднее число приёмов в день, для расчёта, на сколько дней хватит упаковки. */
    fun dosesPerDay(): Double {
        val perDay = sortedTimes.size.toDouble()
        return when (repeat) {
            Repeat.DAILY -> perDay
            Repeat.WEEKDAYS -> perDay * weekdays.size / 7.0
            Repeat.EVERY_N_DAYS -> perDay / everyNDays.coerceAtLeast(1)
        }
    }
}
