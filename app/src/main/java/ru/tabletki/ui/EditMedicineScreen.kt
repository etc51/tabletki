package ru.tabletki.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tabletki.core.Medicine
import ru.tabletki.core.Repeat
import ru.tabletki.core.Schedule
import ru.tabletki.data.Store
import ru.tabletki.reminders.Reminders
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditMedicineScreen(medicineId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val run = rememberBackground()
    val isNew = medicineId == 0L

    var loaded by remember { mutableStateOf(isNew) }
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    val times = remember { mutableStateListOf<LocalTime>() }
    var repeat by remember { mutableStateOf(Repeat.DAILY) }
    val weekdays = remember { mutableStateListOf<DayOfWeek>() }
    var everyN by remember { mutableStateOf("2") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var trackStock by remember { mutableStateOf(false) }
    var unitsPerDose by remember { mutableStateOf("1") }
    var stock by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(medicineId) {
        if (isNew) {
            times += LocalTime.of(9, 0)
            return@LaunchedEffect
        }
        val m = withContext(Dispatchers.IO) { Store.get(context).medicine(medicineId) } ?: return@LaunchedEffect onClose()
        name = m.name
        dose = m.dose
        times.clear(); times += m.schedule.sortedTimes
        repeat = m.schedule.repeat
        weekdays.clear(); weekdays += m.schedule.weekdays
        everyN = m.schedule.everyNDays.toString()
        startDate = m.schedule.startDate
        endDate = m.schedule.endDate
        trackStock = m.stock != null
        unitsPerDose = m.unitsPerDose.pretty()
        stock = m.stock?.pretty() ?: ""
        active = m.active
        loaded = true
    }

    fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(context, { _, h, min -> onPicked(LocalTime.of(h, min)) }, initial.hour, initial.minute, true).show()
    }

    fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context, { _, y, mon, d -> onPicked(LocalDate.of(y, mon + 1, d)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth,
        ).show()
    }

    fun save() {
        val n = everyN.toIntOrNull()
        val units = parseNumber(unitsPerDose)
        val stockValue = parseNumber(stock)
        error = when {
            name.isBlank() -> "Укажите название лекарства"
            times.isEmpty() -> "Добавьте хотя бы одно время приёма"
            repeat == Repeat.WEEKDAYS && weekdays.isEmpty() -> "Выберите дни недели"
            repeat == Repeat.EVERY_N_DAYS && (n == null || n < 1) -> "Укажите, через сколько дней принимать"
            endDate != null && endDate!! < startDate -> "Дата окончания раньше даты начала"
            trackStock && (units == null || units <= 0) -> "Укажите, сколько уходит за один приём"
            trackStock && (stockValue == null || stockValue < 0) -> "Укажите, сколько сейчас в упаковке"
            else -> null
        }
        if (error != null) return
        val medicine = Medicine(
            id = medicineId,
            name = name.trim(),
            dose = dose.trim(),
            schedule = Schedule(
                times = times.toList(),
                startDate = startDate,
                repeat = repeat,
                weekdays = weekdays.toSet(),
                everyNDays = if (repeat == Repeat.EVERY_N_DAYS) n!! else 1,
                endDate = endDate,
            ),
            unitsPerDose = if (trackStock) units!! else 1.0,
            stock = if (trackStock) stockValue else null,
            active = active,
        )
        run { ctx ->
            Store.get(ctx).saveMedicine(medicine)
            Reminders.refresh(ctx)
        }
        onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Новое лекарство" else "Изменить") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dose, onValueChange = { dose = it },
                label = { Text("Как принимать") },
                placeholder = { Text("1 таблетка после еды") },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Text("Время приёма", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                times.sorted().forEach { t ->
                    InputChip(
                        selected = false,
                        onClick = { pickTime(t) { new -> times.remove(t); if (new !in times) times += new } },
                        label = { Text(t.hm(), style = MaterialTheme.typography.titleMedium) },
                        trailingIcon = {
                            IconButton(onClick = { times.remove(t) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Удалить время ${t.hm()}")
                            }
                        },
                    )
                }
            }
            OutlinedButton(onClick = {
                pickTime(times.maxOrNull()?.plusHours(4) ?: LocalTime.of(9, 0)) { if (it !in times) times += it }
            }) { Text("Добавить время") }

            HorizontalDivider()
            Text("Как часто", style = MaterialTheme.typography.titleMedium)
            listOf(
                Repeat.DAILY to "Каждый день",
                Repeat.WEEKDAYS to "По дням недели",
                Repeat.EVERY_N_DAYS to "Через несколько дней",
            ).forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = repeat == value, onClick = { repeat = value }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = repeat == value, onClick = null)
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                }
            }
            if (repeat == Repeat.WEEKDAYS) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.values().forEach { day ->
                        FilterChip(
                            selected = day in weekdays,
                            onClick = { if (day in weekdays) weekdays.remove(day) else weekdays += day },
                            label = { Text(day.short()) },
                        )
                    }
                }
            }
            if (repeat == Repeat.EVERY_N_DAYS) {
                OutlinedTextField(
                    value = everyN, onValueChange = { everyN = it.filter(Char::isDigit).take(3) },
                    label = { Text("Раз в сколько дней") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            HorizontalDivider()
            Text("Курс", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pickDate(startDate) { startDate = it } }) {
                Text("Начало: ${startDate.format(DATE)}")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = endDate != null, onCheckedChange = { endDate = if (it) startDate.plusDays(13) else null })
                Text("Курс ограничен по времени", style = MaterialTheme.typography.bodyLarge)
            }
            endDate?.let { end ->
                OutlinedButton(onClick = { pickDate(end) { endDate = it } }) {
                    Text("Последний день: ${end.format(DATE)}")
                }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = trackStock, onCheckedChange = { trackStock = it })
                Text("Считать, сколько осталось в упаковке", style = MaterialTheme.typography.bodyLarge)
            }
            if (trackStock) {
                OutlinedTextField(
                    value = unitsPerDose, onValueChange = { unitsPerDose = it },
                    label = { Text("Сколько за один приём (таблеток, капсул)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = stock, onValueChange = { stock = it },
                    label = { Text("Сколько сейчас осталось") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!isNew) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Принимаю сейчас", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }

            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Сохранить", style = MaterialTheme.typography.titleMedium)
            }
            if (!isNew) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Удалить лекарство", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «$name»?") },
            text = { Text("Напоминания прекратятся. Журнал прошлых приёмов сохранится.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    run { ctx ->
                        Store.get(ctx).deleteMedicine(medicineId)
                        Reminders.refresh(ctx)
                    }
                    onClose()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}
