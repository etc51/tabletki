package ru.tabletki.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.tabletki.core.Dose
import ru.tabletki.core.Medicine
import ru.tabletki.reminders.Notifier
import ru.tabletki.reminders.Reminders
import java.lang.ref.WeakReference

/**
 * Экран-будильник поверх блокировки: крупно что принять, кнопки «Принял» и
 * «Отложить», мелодия будильника играет до минуты или до нажатия.
 */
class AlarmActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        showOverLockScreen()
        val test = intent.getBooleanExtra(EXTRA_TEST, false)
        startSound()
        setContent {
            TabletkiTheme(largeText = true) {
                Surface(Modifier.fillMaxSize()) {
                    if (test) TestContent() else DueContent()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startSound()
    }

    override fun onDestroy() {
        stopSound()
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startSound() {
        if (player != null) return
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            null
        }
        handler.postDelayed(::stopSound, 60_000)
    }

    private fun stopSound() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
    }

    private fun act(action: (Context) -> Unit) {
        stopSound()
        val app = applicationContext
        Thread { action(app) }.start()
    }

    @Composable
    private fun DueContent() {
        val due = rememberStoreData(emptyList<Pair<Dose, Medicine>>()) { store ->
            Reminders.dueNow(this).mapNotNull { d -> store.medicine(d.medicineId)?.let { d to it } }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Пора принять", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            due.forEach { (dose, med) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(med.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (med.dose.isNotBlank()) Text(med.dose, style = MaterialTheme.typography.titleMedium)
                        Text("Назначено на ${dose.scheduledAt.toLocalTime().hm()}", style = MaterialTheme.typography.bodyLarge)
                        Button(
                            onClick = { act { Reminders.take(it, dose) } },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        ) { Text("Принял", style = MaterialTheme.typography.titleLarge) }
                        TextButton(onClick = { act { Reminders.skip(it, dose) } }) { Text("Пропустить этот приём") }
                    }
                }
            }
            OutlinedButton(
                onClick = { act { Reminders.snoozeAllDue(it) }; finish() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text("Отложить на 10 минут", style = MaterialTheme.typography.titleMedium) }
        }
    }

    @Composable
    private fun TestContent() {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Проверка прошла", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Напоминание пришло вовремя, и звук работает. Так же будут приходить напоминания о лекарствах.",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = { Notifier.cancelTest(this@AlarmActivity); finish() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text("Хорошо", style = MaterialTheme.typography.titleLarge) }
        }
    }

    companion object {
        private const val EXTRA_TEST = "test"
        private var current: WeakReference<AlarmActivity>? = null

        fun intent(context: Context, test: Boolean = false): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_TEST, test)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        fun open(context: Context, test: Boolean = false) {
            try {
                context.startActivity(intent(context, test))
            } catch (_: Exception) {
                // Запуск из фона запрещён на новых Android, тогда остаётся уведомление.
            }
        }

        /** Закрывает экран, когда все приёмы отмечены, в том числе из уведомления. */
        fun closeIfOpen() {
            current?.get()
                ?.takeUnless { it.intent.getBooleanExtra(EXTRA_TEST, false) }
                ?.let { activity -> activity.runOnUiThread { activity.finish() } }
        }
    }
}
