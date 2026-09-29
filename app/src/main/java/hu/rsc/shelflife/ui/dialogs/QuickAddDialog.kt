package hu.rsc.shelflife.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.StorageLocation
import hu.rsc.shelflife.ui.QuickProduct

/**
 * Vonalkod nelkuli termekek egy koppintasos felvitele (kenyer, tojas, zoldseg...).
 * A lejarat mindig becsult (jelolve, kesobb pontosithato) -- ez a "becsult >
 * hianyzo" elv. A csempe sajat helyet hasznal (a kenyer a kamraba kerul akkor
 * is, ha a rogzitesi kor hutore van allitva).
 */
val quickProducts: List<QuickProduct> = listOf(
    QuickProduct(R.string.quick_bread, 3, StorageLocation.PANTRY),
    QuickProduct(R.string.quick_pastry, 2, StorageLocation.PANTRY),
    QuickProduct(R.string.quick_eggs, 21, StorageLocation.FRIDGE, quantity = 10),
    QuickProduct(R.string.quick_meat, 2, StorageLocation.FRIDGE),
    QuickProduct(R.string.quick_poultry, 2, StorageLocation.FRIDGE),
    QuickProduct(R.string.quick_fish, 1, StorageLocation.FRIDGE),
    QuickProduct(R.string.quick_cold_cuts, 5, StorageLocation.FRIDGE),
    QuickProduct(R.string.quick_cheese_deli, 10, StorageLocation.FRIDGE),
    QuickProduct(R.string.quick_vegetables, 5, StorageLocation.FRIDGE),
    QuickProduct(R.string.quick_salad, 3, StorageLocation.FRIDGE),
    QuickProduct(R.string.quick_fruit, 5, StorageLocation.PANTRY),
    QuickProduct(R.string.quick_leftovers, 3, StorageLocation.FRIDGE)
)

@Composable
fun QuickAddDialog(
    onPick: (QuickProduct) -> Unit,
    onOther: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_add_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.quick_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                quickProducts.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { product ->
                            OutlinedButton(
                                onClick = { onPick(product) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                            ) {
                                Text(
                                    stringResource(product.nameRes),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        // Csonka sor kiegeszitese, hogy a csempek egyforma szelesek maradjanak.
                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOther) { Text(stringResource(R.string.quick_add_other)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
