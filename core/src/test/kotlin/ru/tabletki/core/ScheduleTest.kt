package ru.tabletki.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleTest {
    private val start = LocalDate.of(2026, 10, 19) // понедельник
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)

    @Test
    fun dailyNextIsSameDayLaterTime() {
        val s = Schedule(listOf(t(20), t(8)), start)
        assertEquals(at(19, 8), s.nextAfter(at(19, 7)))
        assertEquals(at(19, 20), s.nextAfter(at(19, 8)))
        assertEquals(at(20, 8), s.nextAfter(at(19, 20)))
    }

    @Test
    fun nothingBeforeStart() {
        val s = Schedule(listOf(t(8)), start)
        assertEquals(at(19, 8), s.nextAfter(at(15, 12)))
    }

    @Test
    fun courseEnds() {
        val s = Schedule(listOf(t(8)), start, endDate = start.plusDays(1))
        assertEquals(at(20, 8), s.nextAfter(at(19, 9)))
        assertNull(s.nextAfter(at(20, 9)))
    }

    @Test
    fun weekdays() {
        val s = Schedule(listOf(t(9)), start, Repeat.WEEKDAYS, setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY))
        assertEquals(at(21, 9), s.nextAfter(at(19, 10)))
        assertEquals(at(25, 9), s.nextAfter(at(21, 9)))
        assertEquals(2.0 / 7, s.dosesPerDay(), 1e-9)
    }

    @Test
    fun weekdaysWithNoDaysNeverRings() {
        val s = Schedule(listOf(t(9)), start, Repeat.WEEKDAYS, emptySet())
        assertNull(s.nextAfter(at(19, 0)))
    }

    @Test
    fun everyThirdDay() {
        val s = Schedule(listOf(t(9)), start, Repeat.EVERY_N_DAYS, everyNDays = 3)
        assertEquals(at(22, 9), s.nextAfter(at(19, 9)))
        assertEquals(listOf(at(19, 9), at(22, 9), at(25, 9)), s.occurrencesBetween(at(18, 0), at(27, 0)))
    }

    @Test
    fun betweenIsExclusiveStartInclusiveEnd() {
        val s = Schedule(listOf(t(8), t(14), t(20)), start)
        assertEquals(listOf(at(19, 14), at(19, 20)), s.occurrencesBetween(at(19, 8), at(19, 20)))
    }

    @Test
    fun daysLeftCountsUnits() {
        val s = Schedule(listOf(t(8), t(20)), start)
        val m = Medicine(1, "Аспирин", "1 таб.", s, unitsPerDose = 1.0, stock = 9.0)
        assertEquals(4, m.daysLeft())
        assertEquals(true, m.isRunningLow())
        assertNull(m.copy(stock = null).daysLeft())
    }
}
