package com.miking.notas.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.miking.notas.data.ReminderRepeat
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

internal fun formatReminder(context: Context, timestamp: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

    return when {
        isSameDay(now, target) -> "Hoy, $time"
        isTomorrow(now, target) -> "Mañana, $time"
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) ->
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(timestamp))

        else ->
            DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)
                .format(Date(timestamp))
    }
}

internal fun formatReminderShort(context: Context, timestamp: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

    return if (isSameDay(now, target)) {
        time
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(timestamp))
    }
}

internal fun dayShortLabel(day: Int): String = when (day) {
    1 -> "L"
    2 -> "M"
    3 -> "X"
    4 -> "J"
    5 -> "V"
    6 -> "S"
    7 -> "D"
    else -> "?"
}

internal fun dayName(day: Int): String = when (day) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> ""
}

internal fun repeatDaysLabel(days: Set<Int>): String {
    val clean = ReminderRepeat.sanitize(days)
    return when {
        clean.isEmpty() -> "Una vez"
        clean == ReminderRepeat.ALL_DAYS -> "Todos los días"
        clean == ReminderRepeat.WEEKDAYS -> "Lunes a viernes"
        clean == ReminderRepeat.WEEKEND -> "Sábados y domingos"
        else -> clean.sorted().joinToString(", ") { dayName(it) }
    }
}

internal fun repeatDaysShortLabel(days: Set<Int>): String {
    val clean = ReminderRepeat.sanitize(days)
    return when {
        clean.isEmpty() -> "Una vez"
        clean == ReminderRepeat.ALL_DAYS -> "Todos los días"
        clean == ReminderRepeat.WEEKDAYS -> "Lun a vie"
        clean == ReminderRepeat.WEEKEND -> "Fin de semana"
        else -> clean.sorted().joinToString(" ") { dayShortLabel(it) }
    }
}

internal fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailsSettings(context) }
}

internal fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}"),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailsSettings(context) }
}

internal fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return

    val intent = Intent(
        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
        Uri.parse("package:${context.packageName}"),
    ).apply {
        putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailsSettings(context) }
}

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

internal fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching { context.startActivity(intent) }
        .onFailure { openBatteryOptimizationSettings(context) }
}

internal fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailsSettings(context) }
}

internal fun isHyperOsDevice(): Boolean {
    val brand = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
    return "xiaomi" in brand || "redmi" in brand || "poco" in brand
}

/**
 * Abre la pantalla de inicio automático de MIUI/HyperOS. Si la actividad
 * propietaria no existe, abre los detalles de la app.
 */
internal fun openHyperOsAutostartSettings(context: Context) {
    val candidates = listOf(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartActivity",
        ),
    )

    for (component in candidates) {
        val intent = Intent().setComponent(component).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }

    openAppDetailsSettings(context)
}

/**
 * Abre los permisos especiales de MIUI/HyperOS. Allí se activa «Mostrar
 * ventanas emergentes» y «Mostrar en pantalla de bloqueo», necesarios en
 * algunas versiones para que la pantalla de alarma aparezca.
 */
internal fun openHyperOsPermissionsSettings(context: Context) {
    val candidates = listOf(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
        ),
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
        ),
    )

    for (component in candidates) {
        val intent = Intent().setComponent(component).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("extra_pkgname", context.packageName)
            putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        }

        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }

    openAppDetailsSettings(context)
}

internal fun openAppDetailsSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching { context.startActivity(intent) }
}

private fun isSameDay(first: Calendar, second: Calendar): Boolean =
    first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

private fun isTomorrow(now: Calendar, target: Calendar): Boolean {
    val tomorrow = (now.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, 1)
    }
    return isSameDay(tomorrow, target)
}
