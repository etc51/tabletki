package ru.tabletki.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.tabletki.core.Dose
import ru.tabletki.core.DoseStatus
import ru.tabletki.core.Medicine
import ru.tabletki.core.Report
import java.time.LocalDate

private data class JournalData(val medicines: List<Medicine> = emptyList(), val doses: List<Dose> = emptyList())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(modifier: Modifier) {
    val context = LocalContext.current
    var days by rememberSaveable { mutableIntStateOf(7) }
    val to = LocalDate.now()
    val from = to.minusDays(days - 1L)
    val data = rememberStoreData(JournalData(), days) { JournalData(it.medicines(), it.dosesBetween(from, to)) }
    val names = data.medicines.associate { it.id to it.name }
    val shown = data.doses.filter { it.status != DoseStatus.PENDING || it.remindCount > 0 }
    val taken = shown.count { it.status == DoseStatus.TAKEN }

    LazyColumn(modifier) {
        item { ScreenTitle("Журнал приёма") }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30).forEach { n ->
                    FilterChip(selected = days == n, onClick = { days = n }, label = { Text("$n дней") })
                }
            }
        }
        item {
            Text(
                if (shown.isEmpty()) "За этот период записей нет" else "Принято $taken из ${shown.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        item {
            Button(
                onClick = {
                    val text = Report.build(data.medicines, data.doses, from, to)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, "Отправить отчёт"))
                },
                enabled = shown.isNotEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Отправить отчёт родным или врачу") }
        }
        shown.groupBy { it.scheduledAt.toLocalDate() }.toSortedMap(reverseOrder()).forEach { (day, list) ->
            item(key = day.toString()) {
                Text(
                    day.dayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(list.sortedBy { it.scheduledAt }, key = { "d" + it.id }) { d ->
                Text(
                    "${d.scheduledAt.toLocalTime().hm()}  ${Report.statusMark(d.status)}  ${names[d.medicineId] ?: "Удалённое лекарство"}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}
