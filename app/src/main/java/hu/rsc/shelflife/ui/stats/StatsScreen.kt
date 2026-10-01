package hu.rsc.shelflife.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.rsc.shelflife.R
import hu.rsc.shelflife.ads.NativeAdCard
import hu.rsc.shelflife.data.MonthBar
import hu.rsc.shelflife.data.StatsPeriod
import hu.rsc.shelflife.data.WasteStats
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * "Megmentett etelek" kepernyo. Szandekosan pozitiv hangvetelu: a sikert
 * emeli ki (elfogyott, az utolso pillanatban megmentett), a kidobottat
 * semlegesen, szemrehanyas nelkul mutatja, es mindig ad egy kovetkezo
 * lepest (hamarosan lejaro tetelek, tipp).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    vm: StatsViewModel = viewModel()
) {
    BackHandler(onBack = onBack)
    val stats by vm.stats.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = period == StatsPeriod.LAST_30_DAYS,
                    onClick = { vm.selectPeriod(StatsPeriod.LAST_30_DAYS) },
                    label = { Text(stringResource(R.string.stats_period_30)) }
                )
                FilterChip(
                    selected = period == StatsPeriod.ALL_TIME,
                    onClick = { vm.selectPeriod(StatsPeriod.ALL_TIME) },
                    label = { Text(stringResource(R.string.stats_period_all)) }
                )
            }

            val s = stats ?: return@Column
            if (!s.hasAnyHistory) {
                EmptyStats()
                ExpiringSoonCard(s.expiringSoon, onBack)
                return@Column
            }

            HeroCard(s)
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(s.saved.toString(), stringResource(R.string.stats_tile_saved), Modifier.weight(1f), emphasized = true)
                StatTile(s.rescued.toString(), stringResource(R.string.stats_tile_rescued), Modifier.weight(1f), emphasized = true)
                StatTile(s.wasted.toString(), stringResource(R.string.stats_tile_wasted), Modifier.weight(1f), emphasized = false)
            }
            Text(
                stringResource(R.string.stats_rescued_explainer, WasteStats.RESCUE_WINDOW_DAYS.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExpiringSoonCard(s.expiringSoon, onBack)
            MonthlyChart(s.months)
            if (s.frequentlyWasted.isNotEmpty()) TipCard(s)
            NativeAdCard()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroCard(s: WasteStats) {
    val rate = s.saveRatePercent
    val headline = when {
        rate == null -> stringResource(R.string.stats_hero_no_period_data)
        s.wasted == 0 -> stringResource(R.string.stats_hero_perfect)
        rate >= 90 -> stringResource(R.string.stats_hero_excellent, rate)
        rate >= 75 -> stringResource(R.string.stats_hero_good, rate)
        else -> pluralStringResource(R.plurals.stats_hero_every_item_counts, s.saved, s.saved)
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rate != null) {
                    Text(
                        stringResource(R.string.stats_percent, rate),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.stats_rate_caption),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(36.dp))
                }
            }
            Text(headline, style = MaterialTheme.typography.titleMedium)
            s.improvementPoints?.let {
                Text(pluralStringResource(R.plurals.stats_improvement, it, it), style = MaterialTheme.typography.bodyMedium)
            }
            val streak = s.daysSinceLastWaste
            when {
                streak == null && s.saved > 0 ->
                    Text(stringResource(R.string.stats_streak_never), style = MaterialTheme.typography.bodyMedium)
                streak != null && streak >= 3 ->
                    Text(pluralStringResource(R.plurals.stats_streak_days, streak.toInt(), streak.toInt()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier, emphasized: Boolean) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ExpiringSoonCard(count: Int, onGoToList: () -> Unit) {
    if (count <= 0) return
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                pluralStringResource(R.plurals.stats_expiring_soon, count, count, WasteStats.EXPIRING_SOON_DAYS.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onGoToList) { Text(stringResource(R.string.stats_expiring_soon_action)) }
        }
    }
}

/**
 * Havi oszlopdiagram: elfogyott (zold, alul) + nem sikerult (semleges barna,
 * felul), 2dp hezaggal. Koppintasra az oszlop szamai a diagram felett.
 * A kidobott szin tudatosan visszafogott (nem piros): informal, nem szid.
 */
@Composable
private fun MonthlyChart(months: List<MonthBar>) {
    val dark = isSystemInDarkTheme()
    val savedColor = MaterialTheme.colorScheme.primary
    val wastedColor = if (dark) Color(0xFF8A7458) else Color(0xFFB8A07A)
    val maxTotal = (months.maxOfOrNull { it.saved + it.wasted } ?: 0).coerceAtLeast(1)
    var selected by rememberSaveable { mutableStateOf(months.lastIndex) }
    // Az app aktualis nyelve (nem fixen magyar).
    val locale: Locale = LocalConfiguration.current.locales[0]
    val longMonth = remember(locale) { DateTimeFormatter.ofPattern("LLLL", locale) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.stats_chart_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(savedColor)
                Text(stringResource(R.string.stats_legend_saved), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(12.dp))
                LegendDot(wastedColor)
                Text(stringResource(R.string.stats_legend_wasted), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            months.getOrNull(selected)?.let { m ->
                Text(
                    stringResource(
                        R.string.stats_chart_readout,
                        m.month.format(longMonth).replaceFirstChar { it.titlecase(locale) },
                        m.saved,
                        m.wasted
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                months.forEachIndexed { index, m ->
                    val monthName = m.month.month.getDisplayName(TextStyle.SHORT, locale)
                    val a11y = stringResource(
                        R.string.stats_chart_readout,
                        m.month.month.getDisplayName(TextStyle.FULL, locale), m.saved, m.wasted
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (index == selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .clickable { selected = index }
                            .semantics { contentDescription = a11y }
                            .padding(horizontal = 6.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        val total = m.saved + m.wasted
                        if (m.wasted > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(m.wasted.toFloat() / maxTotal, fill = true)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(wastedColor)
                            )
                            if (m.saved > 0) Spacer(Modifier.height(2.dp))
                        }
                        if (m.saved > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(m.saved.toFloat() / maxTotal, fill = true)
                                    .clip(
                                        if (m.wasted > 0) RoundedCornerShape(0.dp)
                                        else RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    )
                                    .background(savedColor)
                            )
                        }
                        // Maradek hely az oszlop felett (a weight-ek aranyosak a max-hoz).
                        if (total < maxTotal) Spacer(Modifier.weight((maxTotal - total).toFloat() / maxTotal, fill = true))
                    }
                }
            }
            HorizontalLine()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                months.forEach { m ->
                    Text(
                        m.month.month.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .padding(end = 6.dp)
            .size(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}

@Composable
private fun TipCard(s: WasteStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.stats_tip_title), style = MaterialTheme.typography.titleSmall)
                s.frequentlyWasted.forEach {
                    Text(stringResource(R.string.stats_tip_item, it.name, it.count), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.stats_tip_text), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyStats() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            stringResource(R.string.stats_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.stats_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

