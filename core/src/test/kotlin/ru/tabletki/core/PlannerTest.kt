package ru.tabletki.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlannerTest {
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)
    private val med = Medicine(
        1, "Эналаприл", "1 таб.",
        Schedule(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), LocalDate.of(2026, 10, 1)),
    )

    @Test
    fun dueDoseRingsAndSchedulesRepeat() {
        val r = Planner.reconcile(listOf(med), emptyList(), lastCheck = at(19, 7, 50), now = at(19, 8))
        assertEquals(1, r.created.size)
        val d = r.created.single()
        assertEquals(DoseStatus.PENDING, d.status)
        assertEquals(1, d.remindCount)
        assertEquals(at(19, 8, 10), d.remindAt)
        assertEquals(listOf(d), r.ringing)
        assertEquals(at(19, 8, 10), r.nextWake)
    }

    @Test
    fun nothingDueWakesAtNextOccurrence() {
        val r = Planner.reconcile(listOf(med), emptyList(), lastCheck = at(19, 9), now = at(19, 10))
        assertTrue(r.created.isEmpty() && r.ringing.isEmpty())
        assertEquals(at(19, 20), r.nextWake)
    }

    @Test
    fun ringsThreeTimesThenMarksMissed() {
        var pending = Planner.reconcile(listOf(med), emptyList(), at(19, 7), at(19, 8)).created.map { it.copy(id = 5) }
        var now = at(19, 8)
        repeat(2) {
            now = now.plusMinutes(10)
            val r = Planner.reconcile(listOf(med), pending, now.minusMinutes(10), now)
            assertEquals(1, r.ringing.size)
            pending = r.updated
        }
        assertEquals(3, pending.single().remindCount)
        now = now.plusMinutes(10)
        val r = Planner.reconcile(listOf(med), pending, now.minusMinutes(10), now)
        assertTrue(r.ringing.isEmpty())
        assertEquals(DoseStatus.MISSED, r.updated.single().status)
        assertEquals(at(19, 20), r.nextWake)
    }

    @Test
    fun notYetDueRepeatIsKept() {
        val pending = listOf(Dose(5, 1, at(19, 8), DoseStatus.PENDING, remindAt = at(19, 8, 10), remindCount = 1))
        val r = Planner.reconcile(listOf(med), pending, at(19, 8), at(19, 8, 5))
        assertTrue(r.updated.isEmpty() && r.ringing.isEmpty())
        assertEquals(at(19, 8, 10), r.nextWake)
    }

    @Test
    fun phoneOffOvernightMarksOldDosesMissedWithoutRinging() {
        val r = Planner.reconcile(listOf(med), emptyList(), lastCheck = at(18, 21), now = at(19, 9))
        assertEquals(listOf(at(19, 8)), r.created.map { it.scheduledAt })
        // 08:00 меньше трёх часов назад, поэтому ещё звоним
        assertEquals(1, r.ringing.size)

        val r2 = Planner.reconcile(listOf(med), emptyList(), lastCheck = at(18, 7), now = at(19, 12))
        assertEquals(3, r2.created.size)
        assertTrue(r2.created.all { it.status == DoseStatus.MISSED })
        assertTrue(r2.ringing.isEmpty())
    }

    @Test
    fun lookBackIsCapped() {
        val r = Planner.reconcile(listOf(med), emptyList(), lastCheck = at(1, 0), now = at(19, 12))
        assertEquals(4, r.created.size)
    }

    @Test
    fun pausedMedicineStopsRinging() {
        val pending = listOf(Dose(5, 1, at(19, 8), DoseStatus.PENDING, remindAt = at(19, 8, 10), remindCount = 1))
        val r = Planner.reconcile(listOf(med.copy(active = false)), pending, at(19, 8), at(19, 8, 5))
        assertEquals(DoseStatus.MISSED, r.updated.single().status)
        assertEquals(null, r.nextWake)
    }

    @Test
    fun snoozeResetsCounter() {
        val d = Dose(5, 1, at(19, 8), DoseStatus.PENDING, remindAt = at(19, 8, 10), remindCount = 3)
        val s = Planner.snooze(d, at(19, 8, 5), Duration.ofMinutes(15))
        assertEquals(0, s.remindCount)
        assertEquals(at(19, 8, 20), s.remindAt)
    }

    @Test
    fun reportIsReadable() {
        val doses = listOf(
            Dose(1, 1, at(19, 8), DoseStatus.TAKEN),
            Dose(2, 1, at(19, 20), DoseStatus.MISSED),
        )
        val text = Report.build(listOf(med), doses, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 19))
        assertTrue("Принято 1 из 2" in text, text)
        assertTrue("08:00 ✅ принято Эналаприл" in text, text)
        assertTrue("20:00 ❌ не принято Эналаприл" in text, text)
    }
}
