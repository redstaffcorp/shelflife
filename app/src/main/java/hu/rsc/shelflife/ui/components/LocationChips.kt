package hu.rsc.shelflife.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.rsc.shelflife.data.StorageLocation

/**
 * Hely-valaszto chipsor (Hutő / Fagyaszto / Kamra). `allowNone = true` eseten
 * a kivalasztott chipre ujra koppintva torolheto a valasztas (null).
 */
@Composable
fun LocationChips(
    selected: StorageLocation?,
    onSelect: (StorageLocation?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        StorageLocation.entries.forEach { location ->
            FilterChip(
                selected = selected == location,
                onClick = {
                    onSelect(if (allowNone && selected == location) null else location)
                },
                label = { Text(locationLabel(location)) }
            )
        }
    }
}
