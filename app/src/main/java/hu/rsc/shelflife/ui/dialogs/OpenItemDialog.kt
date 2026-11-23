package hu.rsc.shelflife.ui.dialogs

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.isOpened
import hu.rsc.shelflife.data.packageExpiry
import hu.rsc.shelflife.ui.OpeningState
import hu.rsc.shelflife.ui.longDateFormatter
import hu.rsc.shelflife.ui.shortDateFormatter
import java.time.LocalDate
import java.time.ZoneId

// Gyorsvalasztok a "felbontas utan N napig" ertekhez.
private val SHELF_DAY_PRESETS = listOf(1, 2, 3, 5, 7, 14)
private const val MAX_SHELF_DAYS = 365

/**
 * "Felbontottam": a felbontas napja (alapbol ma) es a felbontas utani
 * eltarthatosag. Ha a termeknel ez meg nem ismert, bekerjuk (es megjegyezzuk);
 * ha ismert, elore kitoltjuk. Tobb darabos, bontatlan tetelnel megkerdezzuk,
 * hany darab fogyott mar el es hany marad felbontva -- alapbol 1, mert
 * altalaban csak egy darab all felbontva a hutoben.
 */
@Composable
fun OpenItemDialog(
    state: OpeningState,
    onConfirm: (openedAt: LocalDate, shelfDays: Int, used: Int, keptOpened: Int) -> Unit,
    onUnopen: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val item = state.item
    val today = LocalDate.now()
    val qty = item.quantity ?: 1

    var openedAt by remember(state) { mutableStateOf(item.openedAt ?: today) }
    var shelfDays by remember(state) { mutableStateOf(item.openedShelfDays ?: state.knownShelfDays) }
    var used by remember(state) { mutableIntStateOf(0) }
    var keptOpened by remember(state) { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (item.isOpened) R.string.open_title_edit else R.string.open_title, item.productName))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // -- Felbontas napja --
                Text(stringResource(R.string.open_date_label), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val yesterday = today.minusDays(1)
                    FilterChip(
                        selected = openedAt == today,
                        onClick = { openedAt = today },
                        label = { Text(stringResource(R.string.open_today)) }
                    )
                    FilterChip(
                        selected = openedAt == yesterday,
                        onClick = { openedAt = yesterday },
                        label = { Text(stringResource(R.string.open_yesterday)) }
                    )
                    val otherDay = openedAt != today && openedAt != yesterday
                    FilterChip(
                        selected = otherDay,
                        onClick = {
                            val dialog = DatePickerDialog(
                                context,
                                { _, y, m, d -> openedAt = LocalDate.of(y, m + 1, d) },
                                openedAt.year, openedAt.monthValue - 1, openedAt.dayOfMonth
                            )
                            // Felbontas nem lehet a jovoben.
                            dialog.datePicker.maxDate =
                                today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            dialog.show()
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        label = {
                            Text(
                                if (otherDay) openedAt.format(shortDateFormatter)
                                else stringResource(R.string.open_other_day)
                            )
                        }
                    )
                }

                // -- Felbontas utani eltarthatosag --
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.open_shelf_label), style = MaterialTheme.typography.labelLarge)
                Text(
                    if (state.knownShelfDays != null) {
                        pluralStringResource(R.plurals.open_shelf_known, state.knownShelfDays, state.knownShelfDays)
                    } else {
                        stringResource(R.string.open_shelf_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SHELF_DAY_PRESETS.forEach { days ->
                        FilterChip(
                            selected = shelfDays == days,
                            onClick = { shelfDays = days },
                            label = { Text(pluralStringResource(R.plurals.open_days_chip, days, days)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                BoundedStepper(
                    label = stringResource(R.string.open_shelf_custom),
                    value = shelfDays,
                    min = 1,
                    max = MAX_SHELF_DAYS,
                    onChange = { shelfDays = it }
                )

                // -- Tobb darab: mennyi fogyott, mennyi marad felbontva --
                if (state.canSplit) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.open_split_title, qty, item.quantityUnit),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    BoundedStepper(
                        label = stringResource(R.string.open_split_used),
                        value = used,
                        min = 0,
                        max = qty - keptOpened,
                        onChange = { used = it }
                    )
                    BoundedStepper(
                        label = stringResource(R.string.open_split_kept),
                        value = keptOpened,
                        min = 1,
                        max = qty - used,
                        onChange = { keptOpened = it }
                    )
                    Text(
                        stringResource(R.string.open_split_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.open_split_remaining, qty - used - keptOpened, item.quantityUnit),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // -- Eredmeny --
                val days = shelfDays
                if (days != null) {
                    val pkg = item.packageExpiry
                    val byOpening = openedAt.plusDays(days.toLong())
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.open_result, minOf(pkg, byOpening).format(longDateFormatter)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (pkg < byOpening) {
                        Text(
                            stringResource(R.string.open_result_package_earlier),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = shelfDays != null,
                onClick = { shelfDays?.let { onConfirm(openedAt, it, used, keptOpened) } }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (item.isOpened) {
                    TextButton(onClick = onUnopen) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.open_unopen))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

/** Cimke + [-] ertek [+] korlatos leptetovel. `value == null`: meg nincs megadva. */
@Composable
private fun BoundedStepper(label: String, value: Int?, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedIconButton(
            enabled = value != null && value > min,
            onClick = { value?.let { onChange((it - 1).coerceAtLeast(min)) } }
        ) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_quantity_decrease))
        }
        Text(
            text = value?.toString() ?: "-",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .widthIn(min = 28.dp)
        )
        OutlinedIconButton(
            enabled = value == null || value < max,
            onClick = { onChange(((value ?: (min - 1)) + 1).coerceIn(min, max)) }
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_quantity_increase))
        }
    }
}
