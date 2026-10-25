package hu.rsc.shelflife.ui.dialogs

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.collectAsState
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkInfo
import hu.rsc.shelflife.notify.ExpiryCheck
import java.text.DateFormat
import java.util.Date
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.rsc.shelflife.BuildConfig
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.NotificationSettingsStore
import hu.rsc.shelflife.notify.NotificationHelper
import hu.rsc.shelflife.notify.ReminderScheduler

/**
 * Lejarat-elotti ertesitesek be/kikapcsolasa es a napok szamanak
 * beallitasa. Az engedelykeres (Android 13+, POST_NOTIFICATIONS) tudatosan
 * ITT, a felhasznalo sajat kezdemenyezesere tortenik -- ez a Google altal
 * ajanlott, kontextusban torteno engedelykeres mintaja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsDialog(
    settingsStore: NotificationSettingsStore,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(settingsStore.enabled) }
    var daysBefore by remember { mutableIntStateOf(settingsStore.daysBefore) }
    var refineEnabled by remember { mutableStateOf(settingsStore.refineReminderEnabled) }
    var showPermissionHint by remember { mutableStateOf(false) }

    // Ha a funkcio be van kapcsolva, de nincs rendszer-engedely (pl. kesobb
    // visszavontak), ne mutassunk "bekapcsolva" allapotot ertesites nelkul --
    // de a mentett beallitast nem irjuk felul: ha az engedely visszakerul,
    // az ertesitesek maguktol ujra mennek.
    LaunchedEffect(Unit) {
        if (enabled && !NotificationHelper.hasPermission(context)) {
            enabled = false
            showPermissionHint = true
        }
    }

    fun turnOn() {
        NotificationHelper.ensureChannel(context)
        enabled = true
        settingsStore.enabled = true
        ReminderScheduler.schedule(context)
        showPermissionHint = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            turnOn()
        } else {
            enabled = false
            settingsStore.enabled = false
            showPermissionHint = true
        }
    }

    fun enableNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasPermission(context)) {
            // A felhasznalo eppen most kapcsolta be a funkciot, tehat mar tudja, miert kerdezzuk.
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            turnOn()
        }
    }

    fun disableNotifications() {
        enabled = false
        settingsStore.enabled = false
        showPermissionHint = false
        ReminderScheduler.cancel(context)
    }

    fun changeDays(newValue: Int) {
        daysBefore = newValue.coerceIn(0, 30)
        settingsStore.daysBefore = daysBefore
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notif_settings_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.notif_settings_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.notif_settings_enable))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked -> if (checked) enableNotifications() else disableNotifications() }
                    )
                }

                if (showPermissionHint) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.notif_permission_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.action_open_settings))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(stringResource(R.string.notif_days_before_question), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedIconButton(onClick = { changeDays(daysBefore - 1) }) {
                        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_decrease))
                    }
                    Text(
                        text = if (daysBefore == 0) {
                            stringResource(R.string.notif_days_same_day)
                        } else {
                            pluralStringResource(R.plurals.notif_days_n, daysBefore, daysBefore)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .widthIn(min = 48.dp)
                    )
                    OutlinedIconButton(onClick = { changeDays(daysBefore + 1) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_increase))
                    }
                }

                // Heti pontositas-emlekezteto (ugyanaz a napi hatterellenorzes kuldi,
                // ezert csak bekapcsolt lejarati ertesitessel mukodik).
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.notif_refine_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.notif_refine_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = refineEnabled,
                        enabled = enabled,
                        onCheckedChange = { checked ->
                            refineEnabled = checked
                            settingsStore.refineReminderEnabled = checked
                        }
                    )
                }

                // Teszt/diagnosztika csak debug buildben (az eles verzioban rejtve).
                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(12.dp))
                    NotificationTestSection(settingsStore)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        }
    )
}

/**
 * Ertesites-teszt es diagnosztika -- CSAK debug buildben jelenik meg
 * (BuildConfig.DEBUG), az eles verzioban rejtve. Alapbol osszecsukva.
 *  - "Teszt most": azonnali ertesites -> engedely + csatorna rendben van-e.
 *  - "Hatter-teszt 1 perc mulva": ugyanaz az ExpiryCheck fut, mint naponta, csak
 *    teszt-modban (a "ma mar szoltunk" es a "Holnap szolj" jelzot figyelmen
 *    kivul hagyja, es mindig kuld egy osszegzo ertesitest).
 *  - Futasnaplo: mi inditotta (reggeli ebresztes / tartalek / teszt) es mi lett. Bezart appal ez mutatja meg, hogy a gyartoi
 *    akkukimelo (MIUI, One UI) engedi-e a hatterfuttatast.
 */
@Composable
private fun NotificationTestSection(settingsStore: NotificationSettingsStore) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(R.string.notif_test_section) + if (expanded) "  ▴" else "  ▾")
    }
    if (!expanded) return

    val dailyWork by remember { ReminderScheduler.dailyWorkInfo(context) }.collectAsState(initial = null)
    val testWork by remember { ReminderScheduler.testWorkInfo(context) }.collectAsState(initial = null)

    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))

    val ok = stringResource(R.string.notif_diag_ok)
    val small = MaterialTheme.typography.bodySmall
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val err = MaterialTheme.colorScheme.error

    // Az utolso futas adatait a teszt-futas allapotvaltozasakor ujraolvassuk.
    val runLog = remember(testWork?.state) { settingsStore.runLog }
    val nextAlarm = remember(testWork?.state) { settingsStore.nextAlarmMillis }

    val permissionOk = NotificationHelper.hasPermission(context)
    val channelOk = isExpiryChannelOn(context)
    val batteryRestricted = isBatteryRestricted(context)

    @Composable
    fun line(text: String, bad: Boolean = false) {
        Text(text, style = small, color = if (bad) err else dim)
    }

    line(
        stringResource(R.string.notif_diag_permission, if (permissionOk) ok else stringResource(R.string.notif_diag_missing)),
        bad = !permissionOk
    )
    line(
        stringResource(R.string.notif_diag_channel, if (channelOk) ok else stringResource(R.string.notif_diag_channel_off)),
        bad = !channelOk
    )
    line(
        stringResource(
            R.string.notif_diag_battery,
            stringResource(if (batteryRestricted) R.string.notif_diag_battery_on else R.string.notif_diag_battery_off)
        ),
        bad = batteryRestricted
    )
    line(
        stringResource(R.string.notif_diag_alarm, nextAlarm?.let { formatTime(it) } ?: stringResource(R.string.notif_diag_not_set)),
        bad = nextAlarm == null || nextAlarm < System.currentTimeMillis()
    )
    val daily = dailyWork
    line(
        stringResource(R.string.notif_diag_daily, daily?.state?.name ?: stringResource(R.string.notif_diag_not_scheduled)),
        bad = daily == null || daily.state.isFinished
    )
    if (daily != null && daily.state == WorkInfo.State.ENQUEUED && daily.nextScheduleTimeMillis != Long.MAX_VALUE) {
        line(stringResource(R.string.notif_diag_next, formatTime(daily.nextScheduleTimeMillis)))
    }
    if (runLog.isEmpty()) {
        line(stringResource(R.string.notif_diag_last, stringResource(R.string.notif_diag_never), "–"))
    } else {
        line(stringResource(R.string.notif_diag_runs))
        runLog.forEach { entry ->
            line(
                "• " + formatTime(entry.millis) + " · " + triggerLabel(context, entry.trigger) +
                    " · " + outcomeLabel(context, entry.outcome, entry.count)
            )
        }
    }
    testWork?.let { line(stringResource(R.string.notif_diag_test, it.state.name)) }

    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            info = if (NotificationHelper.notifyTestNow(context)) null
            else context.getString(R.string.notif_permission_missing)
        }
    ) { Text(stringResource(R.string.notif_test_now_button)) }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            ReminderScheduler.scheduleTestRun(context, delayMinutes = 1)
            info = context.getString(R.string.notif_test_bg_scheduled)
        }
    ) { Text(stringResource(R.string.notif_test_bg_button)) }
    if (batteryRestricted) {
        TextButton(onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }) { Text(stringResource(R.string.notif_diag_battery_settings)) }
    }
    info?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(it, style = small)
    }
}

private fun isExpiryChannelOn(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID) ?: return false
    return channel.importance != NotificationManager.IMPORTANCE_NONE
}

private fun isBatteryRestricted(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

private fun triggerLabel(context: Context, trigger: String): String = when (trigger) {
    ExpiryCheck.TRIGGER_ALARM -> context.getString(R.string.notif_trigger_alarm)
    ExpiryCheck.TRIGGER_WORKER -> context.getString(R.string.notif_trigger_worker)
    ExpiryCheck.TRIGGER_TEST -> context.getString(R.string.notif_trigger_test)
    else -> trigger
}

private fun outcomeLabel(context: Context, outcome: String?, count: Int): String = when (outcome) {
    ExpiryCheck.OUTCOME_NO_PERMISSION -> context.getString(R.string.notif_outcome_no_permission)
    ExpiryCheck.OUTCOME_DISABLED -> context.getString(R.string.notif_outcome_disabled)
    ExpiryCheck.OUTCOME_TOO_EARLY -> context.getString(R.string.notif_outcome_too_early)
    ExpiryCheck.OUTCOME_ALREADY_TODAY -> context.getString(R.string.notif_outcome_already_today)
    ExpiryCheck.OUTCOME_NOTHING_DUE -> context.getString(R.string.notif_outcome_nothing_due)
    ExpiryCheck.OUTCOME_NOTIFIED -> context.getString(R.string.notif_outcome_notified, count)
    ExpiryCheck.OUTCOME_TEST -> context.getString(R.string.notif_outcome_test, count)
    else -> "–"
}
