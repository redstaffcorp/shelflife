package hu.rsc.shelflife.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.PantryItem
import hu.rsc.shelflife.data.StorageLocation
import hu.rsc.shelflife.ui.INFO_SEPARATOR
import hu.rsc.shelflife.ui.daysUntil
import hu.rsc.shelflife.ui.expiryLabel
import hu.rsc.shelflife.ui.shortDateFormatter
import hu.rsc.shelflife.ui.theme.ExpiryFresh
import hu.rsc.shelflife.ui.theme.ExpiryOverdue
import hu.rsc.shelflife.ui.theme.ExpirySoon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Friss rogzites kiemelese a listaban. */
enum class ItemHighlight { NONE, SESSION, LATEST }

/** A tarolasi hely megjelenitett neve. */
@Composable
fun locationLabel(location: StorageLocation): String = stringResource(
    when (location) {
        StorageLocation.FRIDGE -> R.string.location_fridge
        StorageLocation.FREEZER -> R.string.location_freezer
        StorageLocation.PANTRY -> R.string.location_pantry
    }
)

/**
 * Huzhato tetelkartya: balra huzva "Elfogyott", jobbra huzva "Kidobtam".
 * Mindketto visszavonhato (a hivo snackbart mutat). Sajat, foundation-alapu
 * gesztus (nem a Material SwipeToDismissBox), hogy ne fuggjunk annak
 * verziorol verziora valtozo API-jatol.
 */
@Composable
fun SwipeablePantryItemCard(
    item: PantryItem,
    highlight: ItemHighlight,
    onEdit: () -> Unit,
    onConsumeOne: () -> Unit,
    onConsumed: () -> Unit,
    onWasted: () -> Unit,
    onSwapDayMonth: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }

    fun settle() {
        scope.launch {
            val threshold = widthPx * SWIPE_THRESHOLD_FRACTION
            val value = offsetX.value
            when {
                widthPx > 0f && value <= -threshold -> {
                    offsetX.animateTo(-widthPx)
                    onConsumed()
                }
                widthPx > 0f && value >= threshold -> {
                    offsetX.animateTo(widthPx)
                    onWasted()
                }
                else -> {
                    offsetX.animateTo(0f)
                    return@launch
                }
            }
            // Normalis esetben a tetel itt mar eltunt a listabol (a composable
            // megszunik, ez a korutin leall). Ha megsem, visszacsusszan.
            delay(1500)
            offsetX.animateTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat() }
    ) {
        val dx = offsetX.value
        if (dx != 0f) {
            val consumed = dx < 0f
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(if (consumed) ExpiryFresh else ExpiryOverdue, CardDefaults.shape)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = if (consumed) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (consumed) Icons.Filled.Check else Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(if (consumed) R.string.swipe_consumed else R.string.swipe_wasted),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        }
                    )
                }
        ) {
            PantryItemCard(
                item = item,
                highlight = highlight,
                onEdit = onEdit,
                onConsumeOne = onConsumeOne,
                onSwapDayMonth = onSwapDayMonth
            )
        }
    }
}

private const val SWIPE_THRESHOLD_FRACTION = 0.35f

@Composable
fun PantryItemCard(
    item: PantryItem,
    highlight: ItemHighlight,
    onEdit: () -> Unit,
    onConsumeOne: () -> Unit,
    onSwapDayMonth: () -> Unit
) {
    val resources = LocalContext.current.resources
    val daysLeft = daysUntil(item.expiry)
    val urgencyColor = when {
        daysLeft < 0 -> ExpiryOverdue
        daysLeft <= 3 -> ExpirySoon
        else -> ExpiryFresh
    }
    val urgencyLabel = resources.expiryLabel(item.expiry)

    val containerColor by animateColorAsState(
        when (highlight) {
            ItemHighlight.LATEST -> MaterialTheme.colorScheme.primaryContainer
            ItemHighlight.SESSION -> MaterialTheme.colorScheme.secondaryContainer
            ItemHighlight.NONE -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "itemHighlight"
    )

    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (highlight == ItemHighlight.LATEST) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(urgencyColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Text(item.productName, style = MaterialTheme.typography.titleMedium)
                if (highlight != ItemHighlight.NONE) {
                    Text(
                        stringResource(
                            if (highlight == ItemHighlight.LATEST) R.string.item_new_latest else R.string.item_new_session
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    (if (item.expiryEstimated) "~ " else "") +
                        urgencyLabel + INFO_SEPARATOR + item.expiry.format(shortDateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = urgencyColor
                )
                if (item.expiryEstimated) {
                    Text(
                        stringResource(R.string.item_estimated_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpirySoon
                    )
                }
                val infoParts = buildList<String> {
                    item.location?.let { add(locationLabel(it)) }
                    add(stringResource(R.string.item_recorded, item.recordedAt.format(shortDateFormatter)))
                    if (item.enteredManually) add(stringResource(R.string.item_manual))
                    item.quantity?.let { add(stringResource(R.string.item_quantity, it, item.quantityUnit)) }
                }
                Text(
                    infoParts.joinToString(INFO_SEPARATOR),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.ambiguousDayMonth) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.item_ambiguous_day_month),
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpirySoon
                        )
                        TextButton(onClick = onSwapDayMonth, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_swap))
                        }
                    }
                }
            }

            // Egy koppintasos elhasznalas: tobb egysegnel "-1", kulonben "Elfogyott".
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val qty = item.quantity ?: 1
                if (qty > 1) {
                    FilledTonalButton(
                        onClick = onConsumeOne,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(stringResource(R.string.action_minus_one))
                    }
                } else {
                    FilledTonalIconButton(onClick = onConsumeOne) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.swipe_consumed))
                    }
                }
            }
        }
    }
}
