package hu.rsc.shelflife.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.FoodCategory
import hu.rsc.shelflife.data.StorageLocation
import hu.rsc.shelflife.ui.QuickProduct
import hu.rsc.shelflife.ui.components.LocationChips

/**
 * Vonalkod nelkuli termekek egy koppintasos felvitele (kenyer, tojas, zoldseg...).
 * A lejarat mindig becsult (jelolve, kesobb pontosithato) -- ez a "becsult >
 * hianyzo" elv. Az erteket a kategoria + hely adja (lasd FoodCategory):
 * alapbol minden csempe a sajat szokasos helyere kerul (a kenyer a kamraba),
 * de a dialogus tetejen egy hely valaszthato -- pl. "Fagyaszto" eseten a hus
 * ~4 honapot kap 2 nap helyett.
 */
val quickProducts: List<QuickProduct> = listOf(
    QuickProduct(R.string.quick_bread, FoodCategory.BREAD),
    QuickProduct(R.string.quick_pastry, FoodCategory.PASTRY),
    QuickProduct(R.string.quick_eggs, FoodCategory.EGGS, quantity = 10),
    QuickProduct(R.string.quick_meat, FoodCategory.MEAT),
    QuickProduct(R.string.quick_poultry, FoodCategory.POULTRY),
    QuickProduct(R.string.quick_fish, FoodCategory.FISH),
    QuickProduct(R.string.quick_cold_cuts, FoodCategory.COLD_CUTS),
    QuickProduct(R.string.quick_cheese_deli, FoodCategory.CHEESE_DELI),
    QuickProduct(R.string.quick_vegetables, FoodCategory.VEGETABLES),
    QuickProduct(R.string.quick_salad, FoodCategory.SALAD),
    QuickProduct(R.string.quick_fruit, FoodCategory.FRUIT),
    QuickProduct(R.string.quick_leftovers, FoodCategory.LEFTOVERS)
)

/**
 * @param initialLocation elore kivalasztott hely (pl. fagyasztos rogzitesi
 *        korben a Fagyaszto); null = mindegyik a sajat szokasos helyere.
 * @param onPick a valasztott csempe + hely (null = szokasos hely).
 */
@Composable
fun QuickAddDialog(
    onPick: (QuickProduct, StorageLocation?) -> Unit,
    onOther: () -> Unit,
    onDismiss: () -> Unit,
    initialLocation: StorageLocation? = null
) {
    var location by remember { mutableStateOf(initialLocation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_add_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.quick_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LocationChips(
                    selected = location,
                    onSelect = { location = it },
                    allowNone = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (location == null) {
                        stringResource(R.string.quick_add_location_auto)
                    } else {
                        stringResource(R.string.quick_add_location_chosen)
                    },
                    style = MaterialTheme.typography.labelSmall,
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
                                onClick = { onPick(product, location) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 64.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        stringResource(product.nameRes),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        estimateLabel(product.category.estimateDays(location)),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
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

/** "ma", "~3 nap", "~4 hónap" -- a csempe alatti becsult eltarthatosag. */
@Composable
private fun estimateLabel(days: Int): String = when {
    days <= 0 -> stringResource(R.string.quick_tile_today)
    days < 60 -> pluralStringResource(R.plurals.quick_tile_days, days, days)
    else -> (days / 30).let { months -> pluralStringResource(R.plurals.quick_tile_months, months, months) }
}
