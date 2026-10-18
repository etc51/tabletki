package ru.tabletki.ui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import ru.tabletki.reminders.AlarmScheduler
import ru.tabletki.reminders.Notifier

/** Одна проверка «телефон позволит напомнить вовремя» и куда вести, чтобы исправить. */
data class SetupCheck(val title: String, val ok: Boolean, val fixHint: String, val fix: (Context) -> Intent)

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

@SuppressLint("BatteryLife")
fun setupChecks(context: Context): List<SetupCheck> {
    val checks = mutableListOf<SetupCheck>()
    checks += SetupCheck(
        "Уведомления разрешены",
        Notifier.canNotify(context),
        "Включите уведомления для приложения",
    ) { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        } else {
            appDetails(ctx)
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        checks += SetupCheck(
            "Точные будильники разрешены",
            AlarmScheduler.canScheduleExact(context),
            "Разрешите «Будильники и напоминания»",
        ) { ctx -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")) }
    }
    if (Build.VERSION.SDK_INT >= 34) {
        checks += SetupCheck(
            "Показ поверх экрана блокировки",
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
            "Разрешите полноэкранные уведомления",
        ) { ctx -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")) }
    }
    checks += SetupCheck(
        "Экономия батареи не мешает",
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        "Снимите ограничения экономии батареи",
    ) { ctx -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")) }
    return checks
}

/** Советы для прошивок, которые особенно агрессивно усыпляют приложения. */
fun manufacturerTip(): String? {
    val brand = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
    return when {
        listOf("xiaomi", "redmi", "poco").any { it in brand } ->
            "На Xiaomi, Redmi и POCO: Настройки → Приложения → «Таблетки по часам» → включите «Автозапуск», " +
                "а в разделе «Контроль активности» выберите «Нет ограничений»."
        listOf("huawei", "honor").any { it in brand } ->
            "На Huawei и Honor: Настройки → Батарея → Запуск приложений → «Таблетки по часам» → " +
                "переключите на «Вручную» и включите все три пункта."
        "samsung" in brand ->
            "На Samsung: Настройки → Батарея → Ограничения фонового использования → " +
                "добавьте приложение в «Неспящие приложения»."
        listOf("oppo", "realme", "oneplus", "vivo").any { it in brand } ->
            "Откройте сведения о приложении → Батарея и разрешите работу в фоне и автозапуск."
        else -> null
    }
}

fun openAppDetails(context: Context) = context.startActivity(appDetails(context))
