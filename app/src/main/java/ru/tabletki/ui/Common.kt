package ru.tabletki.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.tabletki.data.Store

/**
 * Читает данные из базы в фоне и перечитывает после каждой записи,
 * а также раз в полминуты, чтобы статусы «пора принять» не устаревали.
 */
@Composable
fun <T> rememberStoreData(initial: T, vararg keys: Any?, load: (Store) -> T): T {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val version by store.version.collectAsState()
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }
    val value by produceState(initial, version, tick, *keys) {
        value = withContext(Dispatchers.IO) { load(store) }
    }
    return value
}

/** Запускает действие с базой в фоне. */
@Composable
fun rememberBackground(): ((Context) -> Unit) -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return { action -> scope.launch(Dispatchers.IO) { action(context) } }
}

@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
fun EmptyHint(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Меняется при каждом возвращении на экран, например из системных настроек. */
@Composable
fun rememberResumeKey(): Int {
    val state by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var count by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(state) {
        if (state == androidx.lifecycle.Lifecycle.State.RESUMED) count++
    }
    return count
}
