package hu.rsc.shelflife.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.rsc.shelflife.R

/** "Mennyiseg (opcionalis)" cimke + leptheto szam + mertekegyseg-valaszto. */
@Composable
fun QuantityRow(
    quantity: Int?,
    onQuantityChange: (Int?) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(stringResource(R.string.quantity_optional), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuantityStepper(value = quantity, onChange = onQuantityChange)
            Spacer(modifier = Modifier.width(8.dp))
            UnitSelector(value = unit, onChange = onUnitChange)
        }
    }
}

@Composable
fun QuantityStepper(value: Int?, onChange: (Int?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(onClick = {
            onChange(
                when {
                    value == null -> null
                    value <= 1 -> null
                    else -> value - 1
                }
            )
        }) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_quantity_decrease))
        }
        Text(
            text = value?.toString() ?: "-",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .widthIn(min = 24.dp)
        )
        OutlinedIconButton(onClick = { onChange((value ?: 0) + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_quantity_increase))
        }
    }
}

/**
 * Legordulo mertekegyseg-valaszto a gyakori mertekegysegekkel (R.array.quantity_units);
 * "Egyeb..." valasztva barmilyen szabad szoveg is megadhato.
 */
@Composable
fun UnitSelector(value: String, onChange: (String) -> Unit) {
    val commonUnits = stringArrayResource(R.array.quantity_units)
    var menuExpanded by remember { mutableStateOf(false) }
    var customDialogOpen by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf(value) }

    Box {
        OutlinedButton(onClick = { menuExpanded = true }) {
            Text(value)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.cd_choose_unit))
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            commonUnits.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit) },
                    onClick = {
                        onChange(unit)
                        menuExpanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.unit_other)) },
                onClick = {
                    menuExpanded = false
                    customText = value
                    customDialogOpen = true
                }
            )
        }
    }

    if (customDialogOpen) {
        AlertDialog(
            onDismissRequest = { customDialogOpen = false },
            title = { Text(stringResource(R.string.unit_title)) },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text(stringResource(R.string.unit_title)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customText.isNotBlank()) onChange(customText.trim())
                    customDialogOpen = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { customDialogOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
