package dev.argus.tracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.argus.tracker.data.EncounterRepository
import dev.argus.tracker.domain.Encounter
import dev.argus.tracker.domain.EncounterSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SourceSummary(
    val source: String,
    val count: Int
)

class ArgusViewModel(
    private val repository: EncounterRepository
) : ViewModel() {
    val recentEncounters: StateFlow<List<Encounter>> = repository.observeRecent(limit = 1000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent100Encounters: StateFlow<List<Encounter>> = repository.observeRecent(limit = 100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allEncounters: StateFlow<List<Encounter>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _summary = MutableStateFlow<List<SourceSummary>>(emptyList())
    val summary = _summary.asStateFlow()

    fun refreshSummary(windowHours: Long = 24) {
        viewModelScope.launch {
            val since = System.currentTimeMillis() - (windowHours * 60 * 60 * 1000)
            val sourceCounts = repository.sourceSummarySince(since)
            val knownSourceOrder = EncounterSource.entries.toList()

            val knownSources = knownSourceOrder.map { source ->
                SourceSummary(source = source.name, count = sourceCounts[source.name] ?: 0)
            }

            val unknownSources = sourceCounts
                .filterKeys { sourceName -> knownSourceOrder.none { it.name == sourceName } }
                .map { (source, count) -> SourceSummary(source = source, count = count) }
                .sortedByDescending { it.count }

            _summary.value = knownSources + unknownSources
        }
    }

    class Factory(
        private val repository: EncounterRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ArgusViewModel(repository) as T
        }
    }
}
