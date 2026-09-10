package com.example.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DiagnosticFaultHistoryEntry
import com.example.data.model.DtcCode
import com.example.data.model.DtcSeverity
import com.example.data.model.EngineComponent
import com.example.data.model.FuelRecord
import com.example.data.model.FuseItem
import com.example.data.model.LiveTelemetry
import com.example.data.model.RepairGuide
import com.example.data.model.SensorTrendPoint
import com.example.data.model.ServiceRecord
import com.example.data.model.TorqueSpec
import com.example.data.obd.DataVerificationStatus
import com.example.data.obd.ObdManager
import com.example.data.repository.OverlordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OverlordTab(val title: String) {
    DASHBOARD("Kokpit"),
    DIGITAL_TWIN("Cyfrowy Bliźniak"),
    ORACLE("Wyrocznia OBD"),
    BLACK_BOX("Czarna Skrzynka"),
    ARSENAL("Arsenał")
}

internal fun findDtcByCatalogAlias(dtcDatabase: List<DtcCode>, ecuCode: String): DtcCode? {
    val normalizedCode = ecuCode.trim()
    if (normalizedCode.isBlank()) return null

    return dtcDatabase.firstOrNull { dtc ->
        dtc.code.split('/').any { alias ->
            alias.trim().equals(normalizedCode, ignoreCase = true)
        }
    }
}

internal fun resolveStoredDtc(dtcDatabase: List<DtcCode>, code: String): DtcCode {
    return findDtcByCatalogAlias(dtcDatabase, code)
        ?: DtcCode(
            code = code,
            system = "Standard OBD-II",
            title = "Usterka zarejestrowana w ECU ($code)",
            severity = DtcSeverity.HIGH,
            symptoms = listOf("Kontrolka Check Engine (MIL) aktywna"),
            rootCauses = listOf("Wykryto anomalię w podsystemie powertrain"),
            diagnosticSteps = listOf("Sprawdź parametry zamrożonej ramki (Freeze Frame) oraz czujnik"),
            urgencyScore = 7,
            isRenaultSpecific = false
        )
}

internal fun resolvePendingDtc(dtcDatabase: List<DtcCode>, code: String): DtcCode {
    return findDtcByCatalogAlias(dtcDatabase, code)
        ?: DtcCode(
            code = code,
            system = "Standard OBD-II (Oczekujący)",
            title = "Usterka oczekująca na potwierdzenie ($code)",
            severity = DtcSeverity.MEDIUM,
            symptoms = listOf("Brak objawów lub sporadyczna usterka"),
            rootCauses = listOf("Błąd w trakcie weryfikacji przez monitory ECU"),
            diagnosticSteps = listOf("Wymaga wykonania pełnego cyklu jazdy"),
            urgencyScore = 5,
            isRenaultSpecific = false
        )
}

internal fun resolveDtcHistoryStatus(isSimulatedScan: Boolean): DataVerificationStatus =
    if (isSimulatedScan) DataVerificationStatus.SIMULATED else DataVerificationStatus.MEASURED

internal fun resolveStoredDtcSource(isSimulatedScan: Boolean): String =
    if (isSimulatedScan) "OBD-II Mode 03 (Symulacja)" else "OBD-II Mode 03"

internal fun resolvePendingDtcSource(isSimulatedScan: Boolean): String =
    if (isSimulatedScan) "OBD-II Mode 07 (Pending, Symulacja)" else "OBD-II Mode 07 (Pending)"

class OverlordViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OverlordRepository(application)
    private val obdManager = ObdManager(application)

    // Current navigation tab
    private val _currentTab = MutableStateFlow(OverlordTab.DASHBOARD)
    val currentTab: StateFlow<OverlordTab> = _currentTab.asStateFlow()

    fun selectTab(tab: OverlordTab) {
        _currentTab.value = tab
    }

    // Telemetry & OBD
    val telemetry: StateFlow<LiveTelemetry> = obdManager.telemetry
    val connectionStatus: StateFlow<String> = obdManager.connectionStatus
    val connectionState = obdManager.connectionState
    val pairedDevices: StateFlow<List<BluetoothDevice>> = obdManager.pairedDevices
    val livePidData = obdManager.livePidData
    val isLogging = obdManager.isLogging

    val vehicleProfile = MutableStateFlow(com.example.data.model.VehicleProfile())

    init {
        obdManager.onTelemetryLogged = { pidResult ->
            viewModelScope.launch {
                repository.logTelemetry(
                    pid = pidResult.pidHex,
                    name = pidResult.name,
                    value = pidResult.value,
                    unit = pidResult.unit,
                    source = pidResult.source.name
                )
            }
        }
    }

    fun toggleTelemetryLogging() {
        val next = !isLogging.value
        obdManager.setLogging(next)
    }

    fun connectBluetooth(device: BluetoothDevice) = obdManager.connectToBluetoothDevice(device)
    fun enableSimulation() = obdManager.startSimulation()
    fun disconnectObd() = obdManager.disconnect()
    fun refreshBluetooth() = obdManager.loadPairedDevices()

    // Service & Fuel Records (Room)
    val serviceRecords: StateFlow<List<ServiceRecord>> = repository.allServiceRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fuelRecords: StateFlow<List<FuelRecord>> = repository.allFuelRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val telemetryLogs: StateFlow<List<com.example.data.model.TelemetryLog>> = repository.allTelemetryLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val totalServiceCost: StateFlow<Double?> = repository.totalServiceCost
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalFuelCost: StateFlow<Double?> = repository.totalFuelCost
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun addServiceEntry(
        title: String,
        category: String,
        mileageKm: Int,
        dateStr: String,
        costPln: Double,
        partsUsed: String,
        invoiceNumber: String,
        notes: String
    ) {
        viewModelScope.launch {
            repository.addServiceRecord(
                ServiceRecord(
                    title = title,
                    category = category,
                    mileageKm = mileageKm,
                    dateStr = dateStr,
                    costPln = costPln,
                    partsUsed = partsUsed,
                    invoiceNumber = invoiceNumber,
                    notes = notes
                )
            )
        }
    }

    fun deleteServiceEntry(record: ServiceRecord) {
        viewModelScope.launch { repository.deleteServiceRecord(record) }
    }

    fun addFuelEntry(
        dateStr: String,
        mileageKm: Int,
        liters: Double,
        costPln: Double,
        station: String
    ) {
        viewModelScope.launch {
            repository.addFuelRecord(
                FuelRecord(
                    dateStr = dateStr,
                    mileageKm = mileageKm,
                    liters = liters,
                    costPln = costPln,
                    station = station
                )
            )
        }
    }

    fun deleteFuelEntry(record: FuelRecord) {
        viewModelScope.launch { repository.deleteFuelRecord(record) }
    }

    // Fuse Database & Filtering
    private val _fuseSearchQuery = MutableStateFlow("")
    val fuseSearchQuery: StateFlow<String> = _fuseSearchQuery.asStateFlow()

    private val _fuseLocationFilter = MutableStateFlow("Wszystkie") // "Wszystkie", "UPC", "BSI"
    val fuseLocationFilter: StateFlow<String> = _fuseLocationFilter.asStateFlow()

    val allFuses = repository.getFuseCatalog()

    val filteredFuses: StateFlow<List<FuseItem>> = combine(_fuseSearchQuery, _fuseLocationFilter) { query, filter ->
        allFuses.filter { fuse ->
            val matchesFilter = when (filter) {
                "UPC" -> fuse.location.contains("UPC", ignoreCase = true)
                "BSI" -> fuse.location.contains("BSI", ignoreCase = true)
                else -> true
            }
            val matchesQuery = query.isBlank() ||
                    fuse.id.contains(query, ignoreCase = true) ||
                    fuse.name.contains(query, ignoreCase = true) ||
                    fuse.protectedCircuit.contains(query, ignoreCase = true) ||
                    fuse.oemNumber.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), allFuses)

    fun setFuseSearch(query: String) { _fuseSearchQuery.value = query }
    fun setFuseFilter(filter: String) { _fuseLocationFilter.value = filter }

    // Selected Fuse for Modal
    private val _selectedFuse = MutableStateFlow<FuseItem?>(null)
    val selectedFuse: StateFlow<FuseItem?> = _selectedFuse.asStateFlow()
    fun selectFuse(fuse: FuseItem?) { _selectedFuse.value = fuse }

    // Engine Components (Digital Twin)
    val engineComponents = repository.getEngineComponents()
    private val _selectedEngineComponent = MutableStateFlow<EngineComponent?>(null)
    val selectedEngineComponent: StateFlow<EngineComponent?> = _selectedEngineComponent.asStateFlow()
    fun selectEngineComponent(component: EngineComponent?) { _selectedEngineComponent.value = component }

    // DTC Database & Scanner
    val dtcDatabase = repository.getDtcDatabase()
    private val _activeDtcCodes = MutableStateFlow<List<DtcCode>>(emptyList())
    val activeDtcCodes: StateFlow<List<DtcCode>> = _activeDtcCodes.asStateFlow()

    private val _pendingDtcCodes = MutableStateFlow<List<DtcCode>>(emptyList())
    val pendingDtcCodes: StateFlow<List<DtcCode>> = _pendingDtcCodes.asStateFlow()

    fun scanTroubleCodes() {
        viewModelScope.launch {
            val isSimulatedScan = telemetry.value.isSimulated
            val (stored, pending) = obdManager.readTroubleCodes()
            val matchedStored = stored.map { code ->
                resolveStoredDtc(dtcDatabase, code)
            }
            val matchedPending = pending.map { code ->
                resolvePendingDtc(dtcDatabase, code)
            }
            _activeDtcCodes.value = matchedStored
            _pendingDtcCodes.value = matchedPending

            val scanVerificationStatus =
                if (isSimulatedScan) {
                    DataVerificationStatus.SIMULATED
                } else {
                    DataVerificationStatus.MEASURED
                }

            val storedSource =
                if (isSimulatedScan) {
                    "OBD-II Mode 03 (Symulacja)"
                } else {
                    "OBD-II Mode 03"
                }

            val pendingSource =
                if (isSimulatedScan) {
                    "OBD-II Mode 07 (Pending, Symulacja)"
                } else {
                    "OBD-II Mode 07 (Pending)"
                }

            // Log detected DTCs into local fault history for permanent audit trail
            matchedStored.forEach { dtc ->
                repository.logDiagnosticFault(dtc, scanVerificationStatus, storedSource)
            }
            matchedPending.forEach { dtc ->
                repository.logDiagnosticFault(dtc, scanVerificationStatus, pendingSource)
            }
        }
    }

    private val _selectedDtc = MutableStateFlow<DtcCode?>(null)
    val selectedDtc: StateFlow<DtcCode?> = _selectedDtc.asStateFlow()
    fun selectDtc(dtc: DtcCode?) { _selectedDtc.value = dtc }

    private val _dtcSearchQuery = MutableStateFlow("")
    val dtcSearchQuery: StateFlow<String> = _dtcSearchQuery.asStateFlow()
    fun setDtcSearch(query: String) { _dtcSearchQuery.value = query }

    val filteredDtcDatabase: StateFlow<List<DtcCode>> = _dtcSearchQuery.combine(_dtcSearchQuery) { q, _ ->
        if (q.isBlank()) dtcDatabase
        else dtcDatabase.filter {
            it.code.contains(q, ignoreCase = true) ||
                    it.title.contains(q, ignoreCase = true) ||
                    it.system.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), dtcDatabase)

    private val _clearDtcSuccess = MutableStateFlow<String?>(null)
    val clearDtcSuccess: StateFlow<String?> = _clearDtcSuccess.asStateFlow()

    fun clearActiveDtcCodes() {
        viewModelScope.launch {
            val cleared = obdManager.clearTroubleCodes()
            if (cleared) {
                _activeDtcCodes.value = emptyList()
                _pendingDtcCodes.value = emptyList()
                _clearDtcSuccess.value = "Polecenie kasowania kodów DTC zostało wykonane. Wykonaj ponowny skan, aby sprawdzić aktualny stan usterek."
            } else {
                _clearDtcSuccess.value = "Nie udało się skasować kodów błędów. Upewnij się, że zapłon jest włączony, a silnik zgaszony."
            }
        }
    }


    fun dismissClearMessage() { _clearDtcSuccess.value = null }

    // Torque Specs
    val torqueSpecs = repository.getTorqueSpecs()
    private val _torqueSearch = MutableStateFlow("")
    val torqueSearch: StateFlow<String> = _torqueSearch.asStateFlow()
    fun setTorqueSearch(q: String) { _torqueSearch.value = q }

    val filteredTorqueSpecs: StateFlow<List<TorqueSpec>> = _torqueSearch.combine(_torqueSearch) { q, _ ->
        if (q.isBlank()) torqueSpecs
        else torqueSpecs.filter {
            it.component.contains(q, ignoreCase = true) ||
                    it.category.contains(q, ignoreCase = true) ||
                    it.torqueNm.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), torqueSpecs)

    // Repair Guides
    val repairGuides = repository.getRepairGuides()
    private val _selectedGuide = MutableStateFlow<RepairGuide?>(null)
    val selectedGuide: StateFlow<RepairGuide?> = _selectedGuide.asStateFlow()
    fun selectGuide(guide: RepairGuide?) { _selectedGuide.value = guide }

    // Sensor Trends & History
    private val _selectedTrendSensorId = MutableStateFlow("turbocharger")
    val selectedTrendSensorId: StateFlow<String> = _selectedTrendSensorId.asStateFlow()

    fun selectTrendSensor(sensorId: String) {
        _selectedTrendSensorId.value = sensorId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSensorTrends: StateFlow<List<SensorTrendPoint>> = _selectedTrendSensorId
        .flatMapLatest { sensorId -> repository.observeSensorTrend(sensorId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getSensorTrends(sensorId: String): Flow<List<SensorTrendPoint>> =
        repository.observeSensorTrend(sensorId)

    fun logCurrentSensorSample(sensorId: String) {
        val t = telemetry.value
        val (value, unit) = when (sensorId) {
            "turbocharger" -> Pair(t.boostBar, "bar")
            "map_sensor" -> Pair(t.mapPressureKpa.toFloat(), "kPa")
            "hp_fuel_pump" -> Pair(t.railPressureBar.toFloat(), "bar")
            "piezo_injectors" -> Pair(t.injector1Correction, "mg/skok")
            "egr_valve" -> Pair(t.egrPositionPercent, "%")
            else -> return
        }
        viewModelScope.launch {
            repository.logSensorTrend(
                sensorId = sensorId,
                value = value,
                unit = unit,
                status = t.dataSource
            )
        }
    }

    fun logSensorTrend(
        sensorId: String,
        value: Float,
        unit: String,
        status: DataVerificationStatus = DataVerificationStatus.MEASURED
    ) {
        viewModelScope.launch {
            repository.logSensorTrend(sensorId, value, unit, status)
        }
    }

    fun clearSensorTrends(sensorId: String? = null) {
        viewModelScope.launch {
            repository.clearSensorTrends(sensorId)
        }
    }

    // Diagnostic Fault History (Local Persistence in Room)
    val diagnosticFaultHistory: StateFlow<List<DiagnosticFaultHistoryEntry>> = repository
        .observeDiagnosticFaultHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logFaultHistory(
        dtc: DtcCode,
        status: DataVerificationStatus = DataVerificationStatus.MEASURED,
        source: String = "OBD-II Mode 03"
    ) {
        viewModelScope.launch {
            repository.logDiagnosticFault(dtc, status, source)
        }
    }

    fun clearFaultHistory() {
        viewModelScope.launch {
            repository.clearDiagnosticFaultHistory()
        }
    }

    fun deleteFaultHistoryEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteDiagnosticFault(id)
        }
    }
}
