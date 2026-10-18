package ru.tabletki.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Текстовый отчёт для родственника или врача, отправляется через любой мессенджер. */
object Report {
    private val dayFormat = DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.forLanguageTag("ru"))
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun build(medicines: List<Medicine>, doses: List<Dose>, from: LocalDate, to: LocalDate): String {
        val names = medicines.associate { it.id to it.name }
        val inRange = doses.filter { it.scheduledAt.toLocalDate() in from..to }
        val taken = inRange.count { it.status == DoseStatus.TAKEN }
        val counted = inRange.count { it.status != DoseStatus.PENDING }

        val sb = StringBuilder()
        sb.append("Приём лекарств с ${from.format(dayFormat)} по ${to.format(dayFormat)}\n")
        sb.append("Принято $taken из $counted\n")

        inRange.groupBy { it.scheduledAt.toLocalDate() }.toSortedMap().forEach { (day, list) ->
            sb.append('\n').append(day.format(dayFormat).replaceFirstChar { it.uppercase() }).append('\n')
            list.sortedBy { it.scheduledAt }.forEach { d ->
                val name = names[d.medicineId] ?: "Удалённое лекарство"
                sb.append(d.scheduledAt.format(timeFormat)).append(' ')
                    .append(statusMark(d.status)).append(' ').append(name).append('\n')
            }
        }
        medicines.filter { it.active && it.isRunningLow() }.forEach {
            sb.append("\nЗаканчивается: ${it.name}, осталось примерно на ${it.daysLeft()} дн.")
        }
        return sb.toString().trimEnd()
    }

    fun statusMark(status: DoseStatus): String = when (status) {
        DoseStatus.TAKEN -> "✅ принято"
        DoseStatus.SKIPPED -> "➖ пропущено намеренно"
        DoseStatus.MISSED -> "❌ не принято"
        DoseStatus.PENDING -> "⏳ ожидает"
    }
}
