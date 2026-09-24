package com.example.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DiagnosticCheckReport
import com.example.data.model.DiagnosticFaultHistoryEntry
import com.example.data.model.DtcCode
import com.example.data.model.DtcScanState
import com.example.data.model.DtcSeverity
import com.example.data.model.EngineComponent
import com.example.data.model.FuelRecord
import com.example.data.model.FuseItem
import com.example.data.model.HealthCheckItem
import com.example.data.model.HealthCheckStatus
import com.example.data.model.LiveTelemetry
import com.example.data.model.RepairGuide
import com.example.data.model.SensorTrendPoint
import com.example.data.model.ServiceRecord
import com.example.data.model.TorqueSpec
import com.example.data.obd.DataVerificationStatus
import com.example.data.obd.ObdConnectionState
import com.example.data.obd.ObdManager
import com.example.data.repository.OverlordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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

internal fun buildDiagnosticCheckReport(
    telemetry: LiveTelemetry,
    connectionState: ObdConnectionState,
    activeDtc: List<DtcCode>,
    pendingDtc: List<DtcCode>,
    historyFaults: List<DiagnosticFaultHistoryEntry>
): DiagnosticCheckReport {
    val checks = mutableListOf<HealthCheckItem>()

    // Check 1: Łączność OBD-II / ELM327
    val transportStatus = when {
        telemetry.isSimulated -> HealthCheckStatus.INFO
        telemetry.isConnected -> HealthCheckStatus.PASS
        else -> HealthCheckStatus.WARNING
    }
    val transportMsg = when {
        telemetry.isSimulated -> "Tryb symulacji telemetrycznej aktywny (K9K 636)"
        telemetry.isConnected -> "Połączenie z adapterem ELM327 aktywne, tor diagnostyczny otwarty"
        else -> "Brak aktywnego połączenia z fizycznym adapterem ELM327"
    }
    checks.add(
        HealthCheckItem(
            id = "obd_link",
            name = "Łączność diagnostyczna OBD-II",
            subsystem = "Komunikacja ECU",
            status = transportStatus,
            measuredValue = if (telemetry.isSimulated) "Symulacja" else if (telemetry.isConnected) "Połączono" else "Rozłączono",
            nominalRange = "Połączono (CAN 500k / ISO 15765-4)",
            message = transportMsg
        )
    )

    // Check 2: Błędy pamięci ECU (DTC)
    val dtcStatus = when {
        activeDtc.any { it.severity == DtcSeverity.CRITICAL } -> HealthCheckStatus.ALERT
        activeDtc.any { it.severity == DtcSeverity.HIGH } -> HealthCheckStatus.WARNING
        activeDtc.isNotEmpty() || pendingDtc.isNotEmpty() -> HealthCheckStatus.WARNING
        else -> HealthCheckStatus.PASS
    }
    val dtcMsg = when {
        activeDtc.isEmpty() && pendingDtc.isEmpty() -> "Brak zarejestrowanych błędów w ECU"
        activeDtc.any { it.severity == DtcSeverity.CRITICAL } -> "Wykryto błędy o statusie KRYTYCZNYM: ${activeDtc.filter { it.severity == DtcSeverity.CRITICAL }.joinToString { it.code }}"
        else -> "Wykryto ${activeDtc.size} aktywnych i ${pendingDtc.size} oczekujących kodów DTC"
    }
    checks.add(
        HealthCheckItem(
            id = "ecu_dtc",
            name = "Kody usterek ECU (DTC)",
            subsystem = "Sterownik silnika",
            status = dtcStatus,
            measuredValue = "${activeDtc.size} aktywnych / ${pendingDtc.size} oczekujących",
            nominalRange = "0 usterek",
            message = dtcMsg
        )
    )

    // Check 3: Napięcie instalacji elektrycznej
    val voltage = telemetry.batteryVoltage.value
    val rpm = telemetry.rpm.value
    val voltStatus = when {
        voltage == null -> HealthCheckStatus.INFO
        voltage < 11.5f -> HealthCheckStatus.ALERT
        rpm == 0 && voltage < 12.2f -> HealthCheckStatus.WARNING
        rpm == 0 && voltage in 12.2f..12.8f -> HealthCheckStatus.PASS
        voltage in 13.5f..14.8f -> HealthCheckStatus.PASS
        voltage > 15.0f -> HealthCheckStatus.ALERT
        else -> HealthCheckStatus.INFO
    }
    val voltMsg = when {
        voltage == null -> "Brak potwierdzonego odczytu napięcia w tej sesji"
        voltage < 11.5f -> "Niski poziom naładowania akumulatora! Ryzyko problemów z rozruchem"
        voltage > 15.0f -> "Napięcie przeładowania regulatora alternatora!"
        voltage in 13.5f..14.8f -> "Prawidłowe napięcie ładowania alternatora"
        rpm == 0 && voltage in 12.2f..12.8f -> "Napięcie spoczynkowe akumulatora w akceptowalnym zakresie"
        else -> "Odczyt napięcia wymaga interpretacji wraz ze stanem pracy silnika"
    }
    checks.add(
        HealthCheckItem(
            id = "electrical_battery",
            name = "Zasilanie i alternator",
            subsystem = "Układ elektryczny",
            status = voltStatus,
            measuredValue = voltage?.let { "%.1f V".format(Locale.US, it) } ?: "BRAK DANYCH",
            nominalRange = "12.4V - 12.8V (spoczynek) / 13.5V - 14.8V (ładowanie)",
            message = voltMsg
        )
    )

    // Check 4: Układ chłodzenia silnika
    val coolant = telemetry.coolantTempC.value
    val coolStatus = when {
        coolant == null -> HealthCheckStatus.INFO
        coolant > 105 -> HealthCheckStatus.ALERT
        coolant > 98 -> HealthCheckStatus.WARNING
        coolant in 75..98 -> HealthCheckStatus.PASS
        else -> HealthCheckStatus.INFO
    }
    val coolMsg = when {
        coolant == null -> "Brak potwierdzonego odczytu temperatury płynu w tej sesji"
        coolant > 105 -> "Temperatura płynu chłodzącego krytycznie wysoka! Ryzyko przegrzania"
        coolant in 75..98 -> "Prawidłowa temperatura robocza jednostki napędowej"
        else -> "Silnik poza typowym zakresem temperatury roboczej — wymagana obserwacja"
    }
    checks.add(
        HealthCheckItem(
            id = "thermal_cooling",
            name = "Temperatura płynu chłodzącego",
            subsystem = "Układ chłodzenia",
            status = coolStatus,
            measuredValue = coolant?.let { it.toString() + " °C" } ?: "BRAK DANYCH",
            nominalRange = "80 °C - 95 °C",
            message = coolMsg
        )
    )

    // Check 5: Układ wtryskowy Common Rail
    val corrections = listOfNotNull(
        telemetry.injector1Correction.value,
        telemetry.injector2Correction.value,
        telemetry.injector3Correction.value,
        telemetry.injector4Correction.value
    )
    val maxCorr = corrections.maxOfOrNull { abs(it) }
    val railPressure = telemetry.railPressureBar.value
    val injStatus = when {
        corrections.size < 4 -> HealthCheckStatus.INFO
        maxCorr != null && maxCorr > 2.5f -> HealthCheckStatus.ALERT
        maxCorr != null && maxCorr > 1.5f -> HealthCheckStatus.WARNING
        else -> HealthCheckStatus.PASS
    }
    val injMsg = when {
        corrections.size < 4 -> "Brak kompletu potwierdzonych korekt wtryskiwaczy w tej sesji"
        maxCorr != null && maxCorr > 2.5f -> "Znaczna odchyłka dawki wtryskiwacza (> 2.5 mg/cp)! Wskazana weryfikacja przelewowa"
        maxCorr != null && maxCorr > 1.5f -> "Podwyższona korekta wtryskiwacza (> 1.5 mg/cp). Wymagana obserwacja"
        else -> "Wszystkie dostępne korekty dawek wtryskiwaczy cylindrów 1-4 mieszczą się w przyjętym zakresie"
    }
    val injectionMeasured = if (maxCorr == null) {
        "BRAK DANYCH"
    } else {
        val railText = railPressure?.let { it.toString() + " bar" } ?: "BRAK DANYCH"
        "Max corr: %.2f mg/cp (Rail: %s)".format(Locale.US, maxCorr, railText)
    }
    checks.add(
        HealthCheckItem(
            id = "fuel_injection",
            name = "Wtryskiwacze i Common Rail",
            subsystem = "Układ paliwowy K9K",
            status = injStatus,
            measuredValue = injectionMeasured,
            nominalRange = "Odchyłka ± 1.0 mg/cp",
            message = injMsg
        )
    )

    // Check 6: Filtr cząstek stałych (DPF / FAP)
    val soot = telemetry.dpfSootGrams.value
    val isRegenerating = telemetry.isRegeneratingDpf.value
    val dpfStatus = when {
        soot == null -> HealthCheckStatus.INFO
        soot > 35f -> HealthCheckStatus.ALERT
        soot > 25f -> HealthCheckStatus.WARNING
        isRegenerating == true -> HealthCheckStatus.INFO
        else -> HealthCheckStatus.PASS
    }
    val dpfMsg = when {
        soot == null -> "Brak potwierdzonego odczytu masy sadzy DPF w tej sesji"
        soot > 35f -> "Krytyczne nagromadzenie sadzy w DPF (> 35g)! Konieczna weryfikacja serwisowa"
        soot > 25f -> "Podwyższona masa sadzy. Wymagana dalsza obserwacja parametrów regeneracji"
        isRegenerating == true -> "Trwa aktywna regeneracja termiczna filtra cząstek stałych"
        else -> "Odczyt masy sadzy nie wskazuje przekroczenia przyjętych progów"
    }
    checks.add(
        HealthCheckItem(
            id = "dpf_fap",
            name = "Filtr cząstek stałych (DPF)",
            subsystem = "Oczyszczanie spalin",
            status = dpfStatus,
            measuredValue = soot?.let { "%.1f g sadzy".format(Locale.US, it) } ?: "BRAK DANYCH",
            nominalRange = "< 20.0 g (dopuszczalne < 30.0 g)",
            message = dpfMsg
        )
    )

    // Check 7: Ciśnienie doładowania & dolot (MAP)
    val mapKpa = telemetry.mapPressureKpa.value
    val boostBar = telemetry.boostBar.value
    val boostStatus = when {
        mapKpa == null -> HealthCheckStatus.INFO
        boostBar != null && boostBar > 1.8f -> HealthCheckStatus.ALERT
        boostBar == null -> HealthCheckStatus.INFO
        mapKpa in 90..260 -> HealthCheckStatus.PASS
        else -> HealthCheckStatus.WARNING
    }
    val boostMsg = when {
        mapKpa == null -> "Brak potwierdzonego odczytu MAP w tej sesji"
        boostBar == null -> "MAP dostępny, ale brak potwierdzonego źródła ciśnienia doładowania"
        boostBar > 1.8f -> "Wysokie ciśnienie doładowania turbosprężarki!"
        else -> "Dostępne parametry MAP / Boost mieszczą się w przyjętym zakresie"
    }
    checks.add(
        HealthCheckItem(
            id = "turbo_map",
            name = "Układ doładowania (MAP / Turbo)",
            subsystem = "Układ dolotowy",
            status = boostStatus,
            measuredValue = when {
                mapKpa == null -> "BRAK DANYCH"
                boostBar == null -> "MAP: " + mapKpa + " kPa / Boost: BRAK DANYCH"
                else -> "%.2f bar (%d kPa)".format(Locale.US, boostBar, mapKpa)
            },
            nominalRange = "0.0 - 1.5 bar (w zależności od obciążenia)",
            message = boostMsg
        )
    )

    // Determine overall status
    val overall = when {
        checks.any { it.status == HealthCheckStatus.ALERT } -> HealthCheckStatus.ALERT
        checks.any { it.status == HealthCheckStatus.WARNING } -> HealthCheckStatus.WARNING
        checks.any { it.status == HealthCheckStatus.INFO } -> HealthCheckStatus.INFO
        else -> HealthCheckStatus.PASS
    }

    val recommendation = when (overall) {
        HealthCheckStatus.ALERT -> "Pojazd wymaga natychmiastowej weryfikacji serwisowej ze względu na wykryte anomalie krytyczne."
        HealthCheckStatus.WARNING -> "Wykryto ostrzeżenia w podsystemach. Zalecana dalsza diagnostyka parametrów bieżących."
        HealthCheckStatus.PASS -> "Wszystkie badane podsystemy pojazdu pracują w granicach norm fabrycznych."
        else -> "Diagnostyka ukończona. Brak wystarczających danych do pełnej oceny stanu pojazdu."
    }

    return DiagnosticCheckReport(
        timestamp = System.currentTimeMillis(),
        overallStatus = overall,
        isSimulated = telemetry.isSimulated,
        isConnected = telemetry.isConnected,
        activeDtcCount = activeDtc.size,
        pendingDtcCount = pendingDtc.size,
        historyFaultsCount = historyFaults.size,
        checks = checks,
        summaryRecommendation = recommendation
    )
}

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

    private val _dtcScanState = MutableStateFlow(DtcScanState.NOT_RUN)
    val dtcScanState: StateFlow<DtcScanState> = _dtcScanState.asStateFlow()

    val hasDtcScanRun: StateFlow<Boolean> = _dtcScanState
        .map { it == DtcScanState.COMPLETED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun scanTroubleCodes() {
        viewModelScope.launch {
            _dtcScanState.value = DtcScanState.RUNNING
            try {
                val isSimulatedScan = telemetry.value.isSimulated
                val isConnectedScan = telemetry.value.isConnected

                // If physical hardware scan requested without connection or active reading, mark FAILED
                if (!isSimulatedScan && (!isConnectedScan || (connectionState.value != ObdConnectionState.READING && connectionState.value != ObdConnectionState.CONNECTED))) {
                    _dtcScanState.value = DtcScanState.FAILED
                    return@launch
                }

                val (stored, pending) = obdManager.readTroubleCodes()

                // If not simulated, ensure session was actually open and operational
                if (!isSimulatedScan && (obdManager.safeSession?.isTransportOpen() != true)) {
                    _dtcScanState.value = DtcScanState.FAILED
                    return@launch
                }

                val matchedStored = stored.map { code ->
                    resolveStoredDtc(dtcDatabase, code)
                }
                val matchedPending = pending.map { code ->
                    resolvePendingDtc(dtcDatabase, code)
                }
                _activeDtcCodes.value = matchedStored
                _pendingDtcCodes.value = matchedPending
                _dtcScanState.value = DtcScanState.COMPLETED

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
            } catch (e: Exception) {
                _dtcScanState.value = DtcScanState.FAILED
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
                _dtcScanState.value = DtcScanState.NOT_RUN
                _clearDtcSuccess.value = "Polecenie kasowania kodów DTC zostało wykonane. Wykonaj ponowny skan, aby sprawdzić aktualny stan usterek."
            } else {
                _clearDtcSuccess.value =
                    "Kasowanie DTC jest wyłączone w bezpiecznym trybie READ-ONLY."
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
        val sample = when (sensorId) {
            "turbocharger" -> t.boostBar.value?.let { Triple(it, "bar", t.boostBar.source) }
            "map_sensor" -> t.mapPressureKpa.value?.let { Triple(it.toFloat(), "kPa", t.mapPressureKpa.source) }
            "hp_fuel_pump" -> t.railPressureBar.value?.let { Triple(it.toFloat(), "bar", t.railPressureBar.source) }
            "piezo_injectors" -> t.injector1Correction.value?.let { Triple(it, "mg/skok", t.injector1Correction.source) }
            "egr_valve" -> t.egrPositionPercent.value?.let { Triple(it, "%", t.egrPositionPercent.source) }
            else -> null
        } ?: return

        viewModelScope.launch {
            repository.logSensorTrend(
                sensorId = sensorId,
                value = sample.first,
                unit = sample.second,
                status = sample.third
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

    // Comprehensive Diagnostic Check & System Health
    private val _diagnosticCheckReport = MutableStateFlow<DiagnosticCheckReport?>(null)
    val diagnosticCheckReport: StateFlow<DiagnosticCheckReport?> = _diagnosticCheckReport.asStateFlow()

    private val _isDiagnosticCheckRunning = MutableStateFlow(false)
    val isDiagnosticCheckRunning: StateFlow<Boolean> = _isDiagnosticCheckRunning.asStateFlow()

    fun runDiagnosticCheck() {
        viewModelScope.launch {
            _isDiagnosticCheckRunning.value = true
            try {
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
                    if (isSimulatedScan) DataVerificationStatus.SIMULATED else DataVerificationStatus.MEASURED
                val storedSource =
                    if (isSimulatedScan) "OBD-II Mode 03 (Symulacja)" else "OBD-II Mode 03"
                val pendingSource =
                    if (isSimulatedScan) "OBD-II Mode 07 (Pending, Symulacja)" else "OBD-II Mode 07 (Pending)"

                matchedStored.forEach { dtc ->
                    repository.logDiagnosticFault(dtc, scanVerificationStatus, storedSource)
                }
                matchedPending.forEach { dtc ->
                    repository.logDiagnosticFault(dtc, scanVerificationStatus, pendingSource)
                }

                delay(300)

                val report = buildDiagnosticCheckReport(
                    telemetry = telemetry.value,
                    connectionState = connectionState.value,
                    activeDtc = matchedStored,
                    pendingDtc = matchedPending,
                    historyFaults = diagnosticFaultHistory.value
                )
                _diagnosticCheckReport.value = report
            } finally {
                _isDiagnosticCheckRunning.value = false
            }
        }
    }

    fun dismissDiagnosticCheckReport() {
        _diagnosticCheckReport.value = null
    }

    fun generateDtcHistorySummary(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = dateFormat.format(Date())
        val t = telemetry.value
        val report = diagnosticCheckReport.value
        val history = diagnosticFaultHistory.value
        val active = activeDtcCodes.value
        val pending = pendingDtcCodes.value

        val sb = StringBuilder()
        sb.appendLine("============================================================")
        sb.appendLine("CSDP-RM3 / MEGANE OVERLORD — RAPORT DIAGNOSTYCZNY")
        sb.appendLine("============================================================")
        sb.appendLine("Data raportu:       $now")
        sb.appendLine("Pojazd:             Renault Megane III Grandtour 1.5 dCi")
        sb.appendLine("Kod silnika:        K9K 636 (110 KM, 260 Nm, FAP)")
        sb.appendLine("Sterownik ECU:      Continental/Siemens SID307")
        sb.appendLine("Tryb telemetryczny: ${if (t.isSimulated) "SYMULACJA (K9K 636)" else if (t.isConnected) "FIZYCZNY ADAPTER OBD-II" else "ROZŁĄCZONO"}")
        sb.appendLine("Status weryfikacji: per-parametr (MEASURED / SIMULATED / UNAVAILABLE)")
        sb.appendLine()

        sb.appendLine("--- 1. PODSUMOWANIE STANU POJAZDU ---")
        if (report != null) {
            sb.appendLine("Ocena ogólna:       ${report.overallStatus.label}")
            sb.appendLine("Zalecenie:          ${report.summaryRecommendation}")
        } else {
            sb.appendLine("Ocena ogólna:       Brak wykonanego pełnego testu podsystemów")
        }
        sb.appendLine("Aktywne kody DTC:   ${active.size}")
        sb.appendLine("Kody oczekujące:    ${pending.size}")
        sb.appendLine("Wpisy w historii:   ${history.size}")
        sb.appendLine()

        if (report != null && report.checks.isNotEmpty()) {
            sb.appendLine("--- 2. WYNIKI TESTÓW PODSYSTEMÓW (SYSTEM HEALTH CHECKS) ---")
            report.checks.forEach { check ->
                sb.appendLine("[${check.status.label}] ${check.name} (${check.subsystem})")
                sb.appendLine("  Wartość zmierzona: ${check.measuredValue} | Norma: ${check.nominalRange}")
                sb.appendLine("  Ocena: ${check.message}")
            }
            sb.appendLine()
        }

        sb.appendLine("--- 3. BIEŻĄCE KODY DTC W PAMIĘCI ECU ---")
        if (active.isEmpty() && pending.isEmpty()) {
            sb.appendLine("Brak aktywnych ani oczekujących kodów usterek w ECU.")
        } else {
            if (active.isNotEmpty()) {
                sb.appendLine("[Aktywne błędy potwierdzone (Mode 03)]")
                active.forEach { dtc ->
                    sb.appendLine("• ${dtc.code} — ${dtc.title}")
                    sb.appendLine("  Podsystem: ${dtc.system} | Poziom: ${dtc.severity.label} (Pilność: ${dtc.urgencyScore}/10)")
                    if (dtc.symptoms.isNotEmpty()) {
                        sb.appendLine("  Objawy: ${dtc.symptoms.joinToString()}")
                    }
                    if (dtc.rootCauses.isNotEmpty()) {
                        sb.appendLine("  Możliwe przyczyny: ${dtc.rootCauses.joinToString()}")
                    }
                    if (dtc.diagnosticSteps.isNotEmpty()) {
                        sb.appendLine("  Kroki diagnostyczne: ${dtc.diagnosticSteps.joinToString()}")
                    }
                }
            }
            if (pending.isNotEmpty()) {
                sb.appendLine("[Błędy oczekujące na potwierdzenie (Mode 07)]")
                pending.forEach { dtc ->
                    sb.appendLine("• ${dtc.code} — ${dtc.title} (${dtc.system})")
                }
            }
        }
        sb.appendLine()

        sb.appendLine("--- 4. LOKALNA HISTORIA USTEREK (ROOM AUDIT TRAIL) ---")
        if (history.isEmpty()) {
            sb.appendLine("Brak wpisów w lokalnej bazie historii usterek Room.")
        } else {
            history.forEachIndexed { index, entry ->
                val dateStr = dateFormat.format(Date(entry.timestamp))
                sb.appendLine("${index + 1}. [$dateStr] ${entry.dtcCode} — ${entry.title}")
                sb.appendLine("   Podsystem: ${entry.system} | Poziom: ${entry.severity.label}")
                sb.appendLine("   Źródło: ${entry.source} | Status: ${entry.status}")
            }
        }
        sb.appendLine()

        sb.appendLine("============================================================")
        sb.appendLine("Wygenerowano automatycznie przez CSDP-RM3 Megane Overlord.")
        sb.appendLine("Poufny raport diagnostyczny do celów serwisowych.")
        sb.appendLine("============================================================")

        return sb.toString()
    }
}
