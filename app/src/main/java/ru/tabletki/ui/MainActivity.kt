package ru.tabletki.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.tabletki.data.Store
import ru.tabletki.reminders.Notifier
import ru.tabletki.reminders.Reminders

private enum class Tab(val title: String, val icon: ImageVector) {
    TODAY("Сегодня", Icons.Filled.Home),
    MEDICINES("Лекарства", Icons.Filled.List),
    JOURNAL("Журнал", Icons.Filled.DateRange),
    SETTINGS("Настройки", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        val store = Store.get(this)
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val version by store.version.collectAsState()
            TabletkiTheme(largeText = version.let { store.largeText }) {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) { Reminders.refresh(applicationContext) }
    }
}

@Composable
private fun App() {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    // null — список, 0 — новое лекарство, иначе id редактируемого.
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }

    val editId = editing
    if (editId != null) {
        BackHandler { editing = null }
        EditMedicineScreen(medicineId = editId, onClose = { editing = null })
        return
    }
    BackHandler(enabled = tab != Tab.TODAY) { tab = Tab.TODAY }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.TODAY -> TodayScreen(modifier, onAddMedicine = { editing = 0L }, onOpenSettings = { tab = Tab.SETTINGS })
            Tab.MEDICINES -> MedicinesScreen(modifier, onEdit = { editing = it })
            Tab.JOURNAL -> JournalScreen(modifier)
            Tab.SETTINGS -> SettingsScreen(modifier)
        }
    }
}
