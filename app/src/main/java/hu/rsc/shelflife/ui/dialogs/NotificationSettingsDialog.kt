package hu.rsc.shelflife.ui.dialogs

import android.Manifest
import android.content.Intent
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    var showPermissionHint by remember { mutableStateOf(false) }

    // Ha kozben (a rendszerbeallitasokban) visszavontak az engedelyt, ezt itt
    // vegyuk eszre, es ne mutassunk "bekapcsolva" allapotot ertesites nelkul.
    LaunchedEffect(Unit) {
        if (enabled && !NotificationHelper.hasPermission(context)) {
            enabled = false
            settingsStore.enabled = false
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
            Column {
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
                            stringResource(R.string.notif_days_n, daysBefore)
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        }
    )
}
