package ru.tabletki.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.tabletki.core.Medicine

@Composable
fun MedicinesScreen(modifier: Modifier, onEdit: (Long) -> Unit) {
    val meds = rememberStoreData(emptyList<Medicine>()) { it.medicines() }

    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item { ScreenTitle("Мои лекарства") }
            if (meds.isEmpty()) {
                item { EmptyHint("Список пуст", "Нажмите «Добавить», чтобы внести лекарство и время приёма.") }
            }
            items(meds, key = { it.id }) { m ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onEdit(m.id) },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (m.dose.isNotBlank()) Text(m.dose, style = MaterialTheme.typography.bodyLarge)
                        Text(scheduleSummary(m), style = MaterialTheme.typography.bodyLarge)
                        stockSummary(m)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (m.isRunningLow()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (!m.active) Text("Приём приостановлен", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onEdit(0L) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Добавить") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}
