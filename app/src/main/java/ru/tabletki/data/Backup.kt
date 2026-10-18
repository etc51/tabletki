package ru.tabletki.data

import org.json.JSONArray
import org.json.JSONObject
import ru.tabletki.core.Dose
import ru.tabletki.core.DoseStatus
import ru.tabletki.core.Medicine
import ru.tabletki.core.Repeat
import ru.tabletki.core.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Резервная копия в JSON-файл, который человек сохраняет куда хочет: в «Загрузки», на флешку, в облако. */
object Backup {
    private const val FORMAT = 1

    fun export(store: Store): String {
        val meds = JSONArray()
        store.medicines().forEach { m ->
            meds.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("dose", m.dose)
                    .put("times", JSONArray(m.schedule.sortedTimes.map { it.toString() }))
                    .put("repeat", m.schedule.repeat.name)
                    .put("weekdays", JSONArray(m.schedule.weekdays.map { it.value }))
                    .put("everyNDays", m.schedule.everyNDays)
                    .put("startDate", m.schedule.startDate.toString())
                    .put("endDate", m.schedule.endDate?.toString() ?: JSONObject.NULL)
                    .put("unitsPerDose", m.unitsPerDose)
                    .put("stock", m.stock ?: JSONObject.NULL)
                    .put("active", m.active),
            )
        }
        val doses = JSONArray()
        store.allDoses().filter { it.status != DoseStatus.PENDING }.forEach { d ->
            doses.put(
                JSONObject()
                    .put("id", d.id)
                    .put("medicineId", d.medicineId)
                    .put("scheduledAt", Store.formatDateTime(d.scheduledAt))
                    .put("status", d.status.name)
                    .put("actedAt", d.actedAt?.let { Store.formatDateTime(it) } ?: JSONObject.NULL),
            )
        }
        return JSONObject()
            .put("app", "tabletki")
            .put("format", FORMAT)
            .put("medicines", meds)
            .put("doses", doses)
            .toString(2)
    }

    /** Разбирает файл. Бросает исключение, если файл не от этого приложения или повреждён. */
    fun parse(text: String): Pair<List<Medicine>, List<Dose>> {
        val root = JSONObject(text)
        require(root.optString("app") == "tabletki") { "Это не резервная копия «Таблеток по часам»" }
        val meds = root.getJSONArray("medicines").objects().map { o ->
            Medicine(
                id = o.getLong("id"),
                name = o.getString("name"),
                dose = o.optString("dose"),
                schedule = Schedule(
                    times = o.getJSONArray("times").strings().map { LocalTime.parse(it) },
                    startDate = LocalDate.parse(o.getString("startDate")),
                    repeat = Repeat.valueOf(o.getString("repeat")),
                    weekdays = o.getJSONArray("weekdays").let { a -> (0 until a.length()).map { DayOfWeek.of(a.getInt(it)) } }.toSet(),
                    everyNDays = o.getInt("everyNDays"),
                    endDate = if (o.isNull("endDate")) null else LocalDate.parse(o.getString("endDate")),
                ),
                unitsPerDose = o.getDouble("unitsPerDose"),
                stock = if (o.isNull("stock")) null else o.getDouble("stock"),
                active = o.getBoolean("active"),
            )
        }
        val doses = root.getJSONArray("doses").objects().map { o ->
            Dose(
                id = o.getLong("id"),
                medicineId = o.getLong("medicineId"),
                scheduledAt = Store.parseDateTime(o.getString("scheduledAt")),
                status = DoseStatus.valueOf(o.getString("status")),
                actedAt = if (o.isNull("actedAt")) null else Store.parseDateTime(o.getString("actedAt")),
            )
        }
        return meds to doses
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
