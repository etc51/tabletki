package ru.tabletki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.tabletki.core.Dose
import ru.tabletki.core.DoseStatus
import ru.tabletki.core.Medicine
import ru.tabletki.data.Store
import ru.tabletki.reminders.Reminders
import java.time.LocalDate
import java.time.LocalDateTime

private data class TodayItem(val medicine: Medicine, val dose: Dose)

private data class TodayData(
    val items: List<TodayItem> = emptyList(),
    val lowStock: List<Medicine> = emptyList(),
    val hasMedicines: Boolean = true,
)

private fun loadToday(store: Store): TodayData {
    val today = LocalDate.now()
    val meds = store.medicines()
    val byId = meds.associateBy { it.id }
    val records = store.dosesBetween(today, today)
    val recordByKey = records.associateBy { it.medicineId to it.scheduledAt }
    val items = mutableListOf<TodayItem>()
    for (m in meds.filter { it.active }) {
        for (at in m.schedule.occurrencesOn(today)) {
            val dose = recordByKey[m.id to at] ?: Dose(0, m.id, at, DoseStatus.PENDING)
            items += TodayItem(m, dose)
        }
    }
    // Записи, которых уже нет в расписании (его поменяли), всё равно показываем.
    val shown = items.map { it.medicine.id to it.dose.scheduledAt }.toSet()
    records.filter { (it.medicineId to it.scheduledAt) !in shown }.forEach { d ->
        byId[d.medicineId]?.let { items += TodayItem(it, d) }
    }
    return TodayData(
        items = items.sortedBy { it.dose.scheduledAt },
        lowStock = meds.filter { it.active && it.isRunningLow() },
        hasMedicines = meds.isNotEmpty(),
    )
}

@Composable
fun TodayScreen(modifier: Modifier, onAddMedicine: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val data = rememberStoreData(TodayData()) { loadToday(it) }
    val checks = rememberStoreData(emptyList<SetupCheck>(), rememberResumeKey()) { setupChecks(context) }
    val run = rememberBackground()

    // На маленьком экране текущий приём может оказаться ниже уже принятых:
    // прокручиваем к первому, который ещё надо отметить.
    val listState = rememberLazyListState()
    val firstOpen = data.items.indexOfFirst {
        it.dose.status == DoseStatus.PENDING || it.dose.status == DoseStatus.MISSED && it.dose.scheduledAt.toLocalDate() == LocalDate.now()
    }
    val headerCount = 1 + (if (checks.any { !it.ok }) 1 else 0)
    LaunchedEffect(firstOpen, data.items.size) {
        if (firstOpen > 0) listState.scrollToItem(headerCount + firstOpen)
    }

    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle(LocalDate.now().dayTitle()) }

        if (checks.any { !it.ok }) {
            item {
                WarningCard(
                    "Напоминания могут не сработать",
                    "Телефон пока не разрешил приложению звонить вовремя.",
                    "Настроить",
                    onOpenSettings,
                )
            }
        }

        if (!data.hasMedicines) {
            item {
                Column {
                    EmptyHint(
                        "Добавьте первое лекарство",
                        "Укажите название и время приёма, и телефон напомнит, когда пора его принять.",
                    )
                    Button(
                        onClick = onAddMedicine,
                        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("Добавить лекарство") }
                }
            }
        } else if (data.items.isEmpty()) {
            item { EmptyHint("Сегодня приёмов нет", "Следующие приёмы появятся здесь в свой день.") }
        }

        items(data.items, key = { "${it.medicine.id}-${it.dose.scheduledAt}" }) { item ->
            DoseCard(
                item,
                onTake = { run { ctx -> Reminders.take(ctx, item.dose) } },
                onSkip = { run { ctx -> Reminders.skip(ctx, item.dose) } },
                onUndo = { run { ctx -> Reminders.undo(ctx, item.dose) } },
            )
        }
        items(data.lowStock, key = { "low" + it.id }) { m ->
            WarningCard("Заканчивается: ${m.name}", stockSummary(m) ?: "", null, null)
        }
    }
}

@Composable
private fun DoseCard(item: TodayItem, onTake: () -> Unit, onSkip: () -> Unit, onUndo: () -> Unit) {
    val d = item.dose
    val now = LocalDateTime.now()
    val due = d.status == DoseStatus.PENDING && d.scheduledAt <= now
    val (statusText, container) = when {
        d.status == DoseStatus.TAKEN ->
            "Принято" + (d.actedAt?.let { " в ${it.toLocalTime().hm()}" } ?: "") to
                MaterialTheme.colorScheme.primaryContainer
        d.status == DoseStatus.SKIPPED -> "Пропущено" to MaterialTheme.colorScheme.surfaceVariant
        d.status == DoseStatus.MISSED -> "Не принято" to MaterialTheme.colorScheme.errorContainer
        due && d.remindCount == 0 && d.remindAt != null && d.remindAt!! > now ->
            "Отложено до ${d.remindAt!!.toLocalTime().hm()}" to MaterialTheme.colorScheme.tertiaryContainer
        due -> "Пора принять!" to MaterialTheme.colorScheme.tertiaryContainer
        else -> "Впереди" to MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.scheduledAt.toLocalTime().hm(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(item.medicine.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (item.medicine.dose.isNotBlank()) {
                        Text(item.medicine.dose, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Text(statusText, style = MaterialTheme.typography.titleMedium)
            when (d.status) {
                DoseStatus.PENDING, DoseStatus.MISSED -> {
                    Button(onClick = onTake, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("Принял", style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = onSkip) { Text("Пропустить этот приём") }
                }
                DoseStatus.TAKEN, DoseStatus.SKIPPED -> TextButton(onClick = onUndo) { Text("Отменить отметку") }
            }
        }
    }
}

@Composable
fun WarningCard(title: String, text: String, action: String?, onAction: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyLarge)
            if (action != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
            }
        }
    }
}
