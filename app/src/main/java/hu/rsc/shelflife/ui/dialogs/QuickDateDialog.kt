package hu.rsc.shelflife.ui.dialogs

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hu.rsc.shelflife.R
import hu.rsc.shelflife.ocr.QuickDateInput
import hu.rsc.shelflife.ui.daysUntil
import hu.rsc.shelflife.ui.longDateFormatter
import java.time.LocalDate

/**
 * Gyors lejarati datum bevitel szamjegyekkel ("2510" = okt. 25., "251026", "25102026"),
 * elo elonezettel. A naptaras valaszto egy gombnyomasra tovabbra is elerheto.
 */
@Composable
fun QuickDateDialog(
    calendarDefault: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val parsed = QuickDateInput.parse(text, today)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_date_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it in " ./-" }.take(10) },
                    label = { Text(stringResource(R.string.quick_date_label)) },
                    placeholder = { Text(stringResource(R.string.quick_date_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { parsed?.let(onConfirm) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    parsed != null -> {
                        val days = daysUntil(parsed, today)
                        val formatted = parsed.format(longDateFormatter)
                        Text(
                            when {
                                days < 0 -> stringResource(R.string.quick_date_overdue, formatted)
                                days == 0L -> stringResource(R.string.quick_date_today, formatted)
                                else -> pluralStringResource(R.plurals.quick_date_in_days, days.toInt(), formatted, days.toInt())
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (days < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    text.isNotBlank() -> Text(
                        stringResource(R.string.quick_date_invalid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Text(
                        stringResource(R.string.quick_date_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = {
                    val start = parsed ?: calendarDefault
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> onConfirm(LocalDate.of(year, month + 1, day)) },
                        start.year,
                        start.monthValue - 1,
                        start.dayOfMonth
                    ).show()
                }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.quick_date_calendar))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onConfirm) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
