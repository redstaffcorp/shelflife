package hu.rsc.shelflife.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hu.rsc.shelflife.data.PantryRepository
import hu.rsc.shelflife.data.StatsPeriod
import hu.rsc.shelflife.data.WasteStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PantryRepository.get(application)

    val period = MutableStateFlow(StatsPeriod.LAST_30_DAYS)

    /** null = meg toltodik (ne villanjon fel az "ures" allapot). */
    val stats: StateFlow<WasteStats?> = combine(
        repository.finishedItems,
        repository.items,
        period
    ) { finished, active, p ->
        WasteStats.compute(finished, active, p, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPeriod(p: StatsPeriod) {
        period.value = p
    }
}
