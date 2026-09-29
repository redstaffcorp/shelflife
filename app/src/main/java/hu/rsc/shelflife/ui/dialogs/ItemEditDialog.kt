package hu.rsc.shelflife.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.KnownProduct
import hu.rsc.shelflife.data.KnownProductEntity
import hu.rsc.shelflife.data.StorageLocation
import hu.rsc.shelflife.ui.components.LocationChips
import hu.rsc.shelflife.ui.ManualEditState
import hu.rsc.shelflife.ui.components.ProductNameField
import hu.rsc.shelflife.ui.components.QuantityRow
import hu.rsc.shelflife.ui.shortDateFormatter
import java.time.LocalDate

/** Teljesen kamera nelkuli felvitel VAGY egy meglevo tetel szerkesztese. */
@Composable
fun ItemEditDialog(
    editState: ManualEditState,
    knownProducts: List<KnownProduct>,
    onSave: (name: String, expiry: LocalDate, quantity: Int?, unit: String, estimated: Boolean, location: StorageLocation?) -> Unit,
    onDelete: (id: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember(editState) { mutableStateOf(editState.name) }
    var expiryInput by remember(editState) { mutableStateOf(editState.expiry) }
    var quantityInput by remember(editState) { mutableStateOf(editState.quantity) }
    var quantityUnitInput by remember(editState) { mutableStateOf(editState.quantityUnit) }
    // Ha a felhasznalo kezzel valaszt mertekegyseget, azt tobbe ne irjuk
    // felul automatikusan, meg akkor sem, ha kozben egy ismert termek nevet
    // gepel be.
    var unitTouchedByUser by remember(editState) { mutableStateOf(false) }
    var showQuickDate by remember(editState) { mutableStateOf(false) }
    var estimatedInput by remember(editState) { mutableStateOf(editState.expiryEstimated) }
    var locationInput by remember(editState) { mutableStateOf(editState.location) }
    val unitsByKey = remember(knownProducts) {
        knownProducts.associate { KnownProductEntity.keyOf(it.name) to it.unit }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editState.id == null) R.string.edit_title_new else R.string.edit_title_edit))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ProductNameField(
                    value = nameInput,
                    onValueChange = { newName ->
                        nameInput = newName
                        if (!unitTouchedByUser) {
                            unitsByKey[KnownProductEntity.keyOf(newName)]?.let { quantityUnitInput = it }
                        }
                    },
                    knownProducts = knownProducts,
                    onProductSelected = { product ->
                        nameInput = product.name
                        if (!unitTouchedByUser) quantityUnitInput = product.unit
                    },
                    label = stringResource(R.string.product_name_label_with_list)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.edit_expiry, expiryInput.format(shortDateFormatter)))
                    TextButton(onClick = { showQuickDate = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_modify))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = estimatedInput, onCheckedChange = { estimatedInput = it })
                    Text(stringResource(R.string.edit_estimated_checkbox), style = MaterialTheme.typography.bodyMedium)
                }
                if (showQuickDate) {
                    QuickDateDialog(
                        calendarDefault = expiryInput,
                        onDismiss = { showQuickDate = false },
                        onConfirm = {
                            expiryInput = it
                            // Kezzel megadott datum -> mar nem becsles.
                            estimatedInput = false
                            showQuickDate = false
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                QuantityRow(
                    quantity = quantityInput,
                    onQuantityChange = { quantityInput = it },
                    unit = quantityUnitInput,
                    onUnitChange = {
                        quantityUnitInput = it
                        unitTouchedByUser = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                LocationChips(
                    selected = locationInput,
                    onSelect = { locationInput = it },
                    allowNone = true
                )
                if (editState.id != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.item_recorded, editState.recordedAt.format(shortDateFormatter)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameInput.isNotBlank(),
                onClick = { onSave(nameInput, expiryInput, quantityInput, quantityUnitInput, estimatedInput, locationInput) }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                val id = editState.id
                if (id != null) {
                    TextButton(onClick = { onDelete(id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}
