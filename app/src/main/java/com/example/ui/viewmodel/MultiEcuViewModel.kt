package com.example.ui.viewmodel

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.K95EcuRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.K95Ecu
import com.example.data.model.K95InventoryCatalog
import com.example.data.obd.ddt.DdtCapabilityIndexer
import com.example.data.repository.K95ServiceMapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MultiEcuInventoryState(
    val ecus: List<K95Ecu> = emptyList(),
    val loading: Boolean = true,
    val messages: List<String> = emptyList()
)

class MultiEcuViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(MultiEcuInventoryState())
    val state = _state.asStateFlow()

    private var loadStarted = false

    private val repository = K95EcuRepository(AppDatabase.getInstance(application).k95EcuDao())

    init {
        viewModelScope.launch {
            repository.inventory.catch { e ->
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(loading = false,
                    messages = _state.value.messages + "DATABASE ERROR: ${e.javaClass.simpleName}")
            }.collect { ecus -> _state.value = _state.value.copy(ecus = ecus) }
        }
        reloadSources()
    }

    fun reloadSources() {
        if (_state.value.loading && loadStarted) return
        loadStarted = true
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val assets = getApplication<Application>().assets
                val messages = mutableListOf<String>()
                val indexer = DdtCapabilityIndexer()
                // Only explicitly supplied K95 subset may be imported here.
                val files = assets.list("ddt/k95").orEmpty().filter { it.endsWith(".json") }.sorted()
                val descriptors = files.mapNotNull { file ->
                    try {
                        assets.open("ddt/k95/$file").bufferedReader().use {
                            indexer.indexJson(it.readText(), "ddt/k95/$file")
                        }
                    } catch (e: Exception) {
                        messages += "SOURCE ERROR: $file • ${e.javaClass.simpleName}"
                        null
                    }
                }
                if (files.isEmpty()) messages += "SOURCE NOT FOUND: brak podzbioru DDT K95."
                val map = K95ServiceMapRepository(getApplication()).load()
                if (map == null) messages += "SOURCE NOT FOUND: Service Map K95."
                MultiEcuInventoryState(
                    ecus = K95InventoryCatalog.fromDdt(descriptors) + K95InventoryCatalog.fromServiceMap(map),
                    loading = false, messages = messages
                )
            }
            try {
                repository.importCandidates(result.ecus)
                _state.value = _state.value.copy(loading = false, messages = result.messages)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(loading = false,
                    messages = result.messages + "DATABASE ERROR: ${e.javaClass.simpleName}")
            }
        }
    }

}
