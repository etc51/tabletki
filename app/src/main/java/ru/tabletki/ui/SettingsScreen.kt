package ru.tabletki.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.tabletki.data.Backup
import ru.tabletki.data.Store
import ru.tabletki.reminders.AlarmScheduler
import ru.tabletki.reminders.Reminders
import java.time.LocalDate

@Composable
fun SettingsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val scope = rememberCoroutineScope()
    val checks = rememberStoreData(emptyList<SetupCheck>(), rememberResumeKey()) { setupChecks(context) }
    val largeText = rememberStoreData(store.largeText) { it.largeText }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(Backup.export(store).toByteArray()) }
                }.isSuccess
            }
            toast(if (ok) "Копия сохранена" else "Не удалось сохранить файл")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (text == null) toast("Не удалось прочитать файл") else pendingImport = text
        }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenTitle("Настройки")

        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Крупный шрифт", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = largeText, onCheckedChange = { value -> scope.launch(Dispatchers.IO) { store.largeText = value } })
        }

        HorizontalDivider()
        Section("Чтобы напоминания приходили вовремя")
        checks.forEach { check ->
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    (if (check.ok) "✅ " else "⚠️ ") + check.title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!check.ok) {
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(check.fix(context))
                        } catch (_: ActivityNotFoundException) {
                            openAppDetails(context)
                        }
                    }) { Text(check.fixHint) }
                }
            }
        }
        manufacturerTip()?.let { tip ->
            Text(tip, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            TextButton(onClick = { openAppDetails(context) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Открыть настройки приложения")
            }
        }
        Button(
            onClick = {
                AlarmScheduler.scheduleTest(context)
                toast("Через минуту придёт пробное напоминание. Можно заблокировать экран.")
            },
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Проверить напоминание через минуту") }

        HorizontalDivider()
        Section("Резервная копия")
        Text(
            "Все данные хранятся только на этом телефоне. Сохраните копию в файл, чтобы перенести её на новый телефон.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("tabletki-${LocalDate.now()}.json") }) { Text("Сохранить") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Восстановить") }
        }

        HorizontalDivider()
        Section("О приложении")
        Text(
            "«Таблетки по часам» бесплатны и без рекламы. Приложение не выходит в интернет, ничего не собирает " +
                "и никуда не передаёт. Оно только напоминает о приёме, который назначил врач, и не даёт медицинских советов.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Восстановить из файла?") },
            text = { Text("Текущий список лекарств и журнал на этом телефоне заменятся данными из файла.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val error = withContext(Dispatchers.IO) {
                            runCatching {
                                val (meds, doses) = Backup.parse(text)
                                store.replaceAll(meds, doses)
                                Reminders.refresh(context.applicationContext)
                            }.exceptionOrNull()
                        }
                        toast(if (error == null) "Данные восстановлены" else "Файл не подошёл: ${error.message}")
                    }
                }) { Text("Восстановить") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
