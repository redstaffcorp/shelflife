package hu.rsc.shelflife.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.KnownProduct
import hu.rsc.shelflife.data.ProductLookup
import hu.rsc.shelflife.ui.components.ProductNameField

/**
 * Ismeretlen vonalkod: online termeknev-kereses (Open Food Facts, majd
 * UPCitemdb), talalat eseten elore kitoltve; kulonben kezi bevitel.
 */
@Composable
fun NewProductDialog(
    barcode: String,
    knownProducts: List<KnownProduct>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var nameInput by remember(barcode) { mutableStateOf("") }
    var isLookingUp by remember(barcode) { mutableStateOf(true) }
    var searchingSource by remember(barcode) { mutableStateOf(ProductLookup.Source.OPEN_FOOD_FACTS) }
    var result by remember(barcode) { mutableStateOf<ProductLookup.Result?>(null) }

    LaunchedEffect(barcode) {
        isLookingUp = true
        val r = ProductLookup.lookup(context, barcode) { searchingSource = it }
        if (r is ProductLookup.Result.Found && nameInput.isBlank()) nameInput = r.name
        result = r
        isLookingUp = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_product_title)) },
        text = {
            Column {
                Text(stringResource(R.string.new_product_barcode, barcode))
                Spacer(modifier = Modifier.height(8.dp))
                if (isLookingUp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when (searchingSource) {
                                ProductLookup.Source.OPEN_FOOD_FACTS -> stringResource(R.string.lookup_searching_off)
                                ProductLookup.Source.UPCITEMDB -> stringResource(R.string.lookup_searching_upc)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    val note = when (val r = result) {
                        is ProductLookup.Result.Found -> stringResource(R.string.lookup_found, r.source.displayName)
                        ProductLookup.Result.Offline -> stringResource(R.string.lookup_offline)
                        ProductLookup.Result.NotFound -> stringResource(R.string.lookup_not_found)
                        null -> null
                    }
                    note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                ProductNameField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    knownProducts = knownProducts,
                    onProductSelected = { nameInput = it.name },
                    label = stringResource(R.string.product_name_label)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = nameInput.isNotBlank(), onClick = { onConfirm(nameInput) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
