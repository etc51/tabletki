package ru.tabletki.ui

import ru.tabletki.core.Medicine
import ru.tabletki.core.Repeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val RU: Locale = Locale.forLanguageTag("ru")
val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)
val DAY_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)

fun LocalTime.hm(): String = format(TIME)

fun LocalDate.dayTitle(): String = format(DAY_TITLE).replaceFirstChar { it.uppercase() }

fun DayOfWeek.short(): String = getDisplayName(TextStyle.SHORT, RU).replaceFirstChar { it.uppercase() }

/** Число без лишних нулей: 1 вместо 1.0, 0,5 вместо 0.5. */
fun Double.pretty(): String =
    if (this == Math.floor(this)) toLong().toString() else toString().replace('.', ',')

fun scheduleSummary(m: Medicine): String {
    val s = m.schedule
    val times = s.sortedTimes.joinToString(", ") { it.hm() }
    val days = when (s.repeat) {
        Repeat.DAILY -> "каждый день"
        Repeat.WEEKDAYS -> s.weekdays.sorted().joinToString(", ") { it.short() }
        Repeat.EVERY_N_DAYS -> "раз в ${s.everyNDays} дн."
    }
    val until = s.endDate?.let { ", до ${it.format(DATE)}" } ?: ""
    return "$days в $times$until"
}

fun stockSummary(m: Medicine): String? {
    val stock = m.stock ?: return null
    val days = m.daysLeft()
    return "Осталось ${stock.pretty()}" + (days?.let { ", хватит примерно на $it дн." } ?: "")
}
