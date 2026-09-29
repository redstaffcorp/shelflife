package hu.rsc.shelflife.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.KnownProduct
import hu.rsc.shelflife.ui.INFO_SEPARATOR

/**
 * Termeknev-mezo beepitett, szures alapu javaslatlistaval a korabban mar
 * berogzitett (ismert) termekekbol -- ezek kozul lehet valasztani, vagy
 * tovabbra is szabadon be lehet gepelni egy uj termek nevet.
 */
@Composable
fun ProductNameField(
    value: String,
    onValueChange: (String) -> Unit,
    knownProducts: List<KnownProduct>,
    onProductSelected: (KnownProduct) -> Unit,
    label: String
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val filtered = remember(value, knownProducts) {
        if (value.isBlank()) {
            knownProducts
        } else {
            knownProducts.filter { it.name.contains(value, ignoreCase = true) }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                menuExpanded = knownProducts.isNotEmpty()
            },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                if (knownProducts.isNotEmpty()) {
                    IconButton(onClick = { menuExpanded = !menuExpanded }) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.cd_known_products))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) menuExpanded = knownProducts.isNotEmpty() }
        )
        DropdownMenu(
            expanded = menuExpanded && filtered.isNotEmpty(),
            onDismissRequest = { menuExpanded = false }
        ) {
            filtered.take(8).forEach { product ->
                DropdownMenuItem(
                    text = { Text(product.name + INFO_SEPARATOR + product.unit) },
                    onClick = {
                        onProductSelected(product)
                        menuExpanded = false
                    }
                )
            }
        }
    }
}
