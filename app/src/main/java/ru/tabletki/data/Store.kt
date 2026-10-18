package ru.tabletki.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.tabletki.core.Dose
import ru.tabletki.core.DoseStatus
import ru.tabletki.core.Medicine
import ru.tabletki.core.Repeat
import ru.tabletki.core.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Всё хранится локально в SQLite. Сети у приложения нет вообще.
 * [version] растёт после каждой записи, экраны по нему перечитывают данные.
 */
class Store private constructor(context: Context) {

    private val helper = Helper(context)
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    private fun changed() {
        _version.value = _version.value + 1
    }

    // ---------- настройки ----------

    var lastCheck: LocalDateTime?
        get() = prefs.getString("last_check", null)?.let { parseDateTime(it) }
        set(value) = prefs.edit().putString("last_check", value?.let { formatDateTime(it) }).apply()

    var largeText: Boolean
        get() = prefs.getBoolean("large_text", true)
        set(value) {
            prefs.edit().putBoolean("large_text", value).apply()
            changed()
        }

    // ---------- лекарства ----------

    fun medicines(): List<Medicine> =
        helper.readableDatabase.query("medicine", null, null, null, null, null, "name COLLATE NOCASE")
            .use { c -> c.map(::readMedicine) }

    fun medicine(id: Long): Medicine? =
        helper.readableDatabase.query("medicine", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> c.map(::readMedicine).firstOrNull() }

    fun saveMedicine(m: Medicine): Long {
        val db = helper.writableDatabase
        val values = medicineValues(m)
        val id = if (m.id == 0L) {
            db.insert("medicine", null, values)
        } else {
            db.update("medicine", values, "id = ?", arrayOf(m.id.toString()))
            m.id
        }
        changed()
        return id
    }

    /** Удаляет лекарство и его будущие напоминания, а журнал прошлых приёмов оставляет. */
    fun deleteMedicine(id: Long) {
        val db = helper.writableDatabase
        db.delete("dose", "medicine_id = ? AND status = ?", arrayOf(id.toString(), DoseStatus.PENDING.name))
        db.delete("medicine", "id = ?", arrayOf(id.toString()))
        changed()
    }

    fun changeStock(medicineId: Long, delta: Double) {
        helper.writableDatabase.execSQL(
            "UPDATE medicine SET stock = MAX(0, stock + ?) WHERE id = ? AND stock IS NOT NULL",
            arrayOf<Any>(delta, medicineId),
        )
        changed()
    }

    // ---------- приёмы ----------

    fun dose(id: Long): Dose? =
        helper.readableDatabase.query("dose", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> c.map(::readDose).firstOrNull() }

    fun pendingDoses(): List<Dose> =
        helper.readableDatabase.query(
            "dose", null, "status = ?", arrayOf(DoseStatus.PENDING.name), null, null, "scheduled_at",
        ).use { c -> c.map(::readDose) }

    /** Приёмы с [from] по [to] включительно. */
    fun dosesBetween(from: LocalDate, to: LocalDate): List<Dose> =
        helper.readableDatabase.query(
            "dose", null, "scheduled_at >= ? AND scheduled_at < ?",
            arrayOf(formatDateTime(from.atStartOfDay()), formatDateTime(to.plusDays(1).atStartOfDay())),
            null, null, "scheduled_at",
        ).use { c -> c.map(::readDose) }

    fun allDoses(): List<Dose> =
        helper.readableDatabase.query("dose", null, null, null, null, null, "scheduled_at")
            .use { c -> c.map(::readDose) }

    /** Добавляет новые приёмы, пропуская уже записанные. Возвращает те, что реально добавлены, с id. */
    fun insertDoses(doses: List<Dose>): List<Dose> {
        if (doses.isEmpty()) return emptyList()
        val db = helper.writableDatabase
        val inserted = mutableListOf<Dose>()
        db.beginTransaction()
        try {
            for (d in doses) {
                val id = db.insertWithOnConflict("dose", null, doseValues(d), SQLiteDatabase.CONFLICT_IGNORE)
                if (id != -1L) inserted += d.copy(id = id)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (inserted.isNotEmpty()) changed()
        return inserted
    }

    /** Записывает приём: обновляет существующий или создаёт, если его ещё нет (отметка заранее). */
    fun saveDose(d: Dose): Long {
        val db = helper.writableDatabase
        val id = if (d.id != 0L) {
            db.update("dose", doseValues(d), "id = ?", arrayOf(d.id.toString()))
            d.id
        } else {
            db.insertWithOnConflict("dose", null, doseValues(d), SQLiteDatabase.CONFLICT_REPLACE)
        }
        changed()
        return id
    }

    // ---------- резервная копия ----------

    /** Полностью заменяет данные, используется при восстановлении из файла. */
    fun replaceAll(medicines: List<Medicine>, doses: List<Dose>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("dose", null, null)
            db.delete("medicine", null, null)
            for (m in medicines) {
                val v = medicineValues(m)
                v.put("id", m.id)
                db.insert("medicine", null, v)
            }
            for (d in doses) {
                val v = doseValues(d)
                v.put("id", d.id)
                db.insert("dose", null, v)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        lastCheck = LocalDateTime.now()
        changed()
    }

    // ---------- преобразования ----------

    private fun medicineValues(m: Medicine) = ContentValues().apply {
        put("name", m.name)
        put("dose", m.dose)
        put("times", m.schedule.sortedTimes.joinToString(",") { it.format(TIME) })
        put("repeat", m.schedule.repeat.name)
        put("weekdays", m.schedule.weekdays.map { it.value }.sorted().joinToString(","))
        put("every_n", m.schedule.everyNDays)
        put("start_date", m.schedule.startDate.toString())
        put("end_date", m.schedule.endDate?.toString())
        put("units_per_dose", m.unitsPerDose)
        if (m.stock == null) putNull("stock") else put("stock", m.stock)
        put("active", if (m.active) 1 else 0)
    }

    private fun readMedicine(c: Cursor) = Medicine(
        id = c.long("id"),
        name = c.string("name"),
        dose = c.string("dose"),
        schedule = Schedule(
            times = c.string("times").split(',').filter { it.isNotBlank() }.map { LocalTime.parse(it, TIME) },
            startDate = LocalDate.parse(c.string("start_date")),
            repeat = Repeat.valueOf(c.string("repeat")),
            weekdays = c.string("weekdays").split(',').filter { it.isNotBlank() }
                .map { DayOfWeek.of(it.toInt()) }.toSet(),
            everyNDays = c.int("every_n"),
            endDate = c.stringOrNull("end_date")?.let { LocalDate.parse(it) },
        ),
        unitsPerDose = c.double("units_per_dose"),
        stock = c.doubleOrNull("stock"),
        active = c.int("active") == 1,
    )

    private fun doseValues(d: Dose) = ContentValues().apply {
        put("medicine_id", d.medicineId)
        put("scheduled_at", formatDateTime(d.scheduledAt))
        put("status", d.status.name)
        put("remind_at", d.remindAt?.let { formatDateTime(it) })
        put("remind_count", d.remindCount)
        put("acted_at", d.actedAt?.let { formatDateTime(it) })
    }

    private fun readDose(c: Cursor) = Dose(
        id = c.long("id"),
        medicineId = c.long("medicine_id"),
        scheduledAt = parseDateTime(c.string("scheduled_at")),
        status = DoseStatus.valueOf(c.string("status")),
        remindAt = c.stringOrNull("remind_at")?.let { parseDateTime(it) },
        remindCount = c.int("remind_count"),
        actedAt = c.stringOrNull("acted_at")?.let { parseDateTime(it) },
    )

    private class Helper(context: Context) : SQLiteOpenHelper(context, "tabletki.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE medicine (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    dose TEXT NOT NULL,
                    times TEXT NOT NULL,
                    repeat TEXT NOT NULL,
                    weekdays TEXT NOT NULL,
                    every_n INTEGER NOT NULL,
                    start_date TEXT NOT NULL,
                    end_date TEXT,
                    units_per_dose REAL NOT NULL,
                    stock REAL,
                    active INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE dose (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    medicine_id INTEGER NOT NULL,
                    scheduled_at TEXT NOT NULL,
                    status TEXT NOT NULL,
                    remind_at TEXT,
                    remind_count INTEGER NOT NULL,
                    acted_at TEXT,
                    UNIQUE (medicine_id, scheduled_at)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX dose_status ON dose (status)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    companion object {
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun formatDateTime(t: LocalDateTime): String = t.format(DATE_TIME)
        fun parseDateTime(s: String): LocalDateTime = LocalDateTime.parse(s, DATE_TIME)

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}

private inline fun <T> Cursor.map(read: (Cursor) -> T): List<T> {
    val result = ArrayList<T>(count)
    while (moveToNext()) result += read(this)
    return result
}

private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
private fun Cursor.int(name: String) = getInt(getColumnIndexOrThrow(name))
private fun Cursor.double(name: String) = getDouble(getColumnIndexOrThrow(name))
private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))
private fun Cursor.stringOrNull(name: String): String? =
    getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
private fun Cursor.doubleOrNull(name: String): Double? =
    getColumnIndexOrThrow(name).let { if (isNull(it)) null else getDouble(it) }
