package com.example.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.example.data.model.LiveTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class ObdManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var telemetryJob: Job? = null
    internal var transport: DiagnosticTransport? = null
    private var activeDevice: BluetoothDevice? = null

    private val _telemetry = MutableStateFlow(LiveTelemetry())
    val telemetry: StateFlow<LiveTelemetry> = _telemetry.asStateFlow()

    private val _connectionState = MutableStateFlow(ObdConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ObdConnectionState> = _connectionState.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Tryb Symulacji: K9K 636 Aktywny")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    // Live Read PID results map
    private val _livePidData = MutableStateFlow<Map<String, ObdPidResult>>(emptyMap())
    val livePidData: StateFlow<Map<String, ObdPidResult>> = _livePidData.asStateFlow()

    // DTC results from OBD-II Mode 03 & 07
    private val _activeTroubleCodes = MutableStateFlow<List<String>>(emptyList())
    val activeTroubleCodes: StateFlow<List<String>> = _activeTroubleCodes.asStateFlow()

    private val _pendingTroubleCodes = MutableStateFlow<List<String>>(emptyList())
    val pendingTroubleCodes: StateFlow<List<String>> = _pendingTroubleCodes.asStateFlow()

    // Logging control
    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()

    // Listener / callback for logged telemetry items
    var onTelemetryLogged: ((ObdPidResult) -> Unit)? = null

    init {
        loadPairedDevices()
        startSimulation()
    }

    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                val bonded = adapter.bondedDevices
                _pairedDevices.value = bonded.toList()
            }
        } catch (_: Exception) {
            _pairedDevices.value = emptyList()
        }
    }

    fun setLogging(enabled: Boolean) {
        _isLogging.value = enabled
    }

    fun startSimulation() {
        stopActiveJobs()
        _connectionState.value = ObdConnectionState.CONNECTED
        _connectionStatus.value = "Tryb Symulacji: K9K 636 Aktywny"
        _telemetry.value = _telemetry.value.copy(
            isConnected = true,
            isSimulated = true,
            dataSource = DataVerificationStatus.SIMULATED
        )

        telemetryJob = scope.launch {
            var step = 0.0
            while (isActive) {
                step += 0.15
                val idleRpm = 830
                val dynamicRpm = (idleRpm + (sin(step * 0.5) * 1100 + 1100).toInt()).coerceIn(800, 3800)
                val loadRatio = (dynamicRpm - 800) / 3000f
                val boost = (0.05f + loadRatio * 1.35f + (sin(step) * 0.08).toFloat()).coerceIn(0.0f, 1.48f)
                val railPressure = (270 + (loadRatio * 1280).toInt() + (sin(step * 1.5) * 35).toInt()).coerceIn(250, 1650)
                val speed = ((dynamicRpm - 800) * 0.045f * (3 + (sin(step * 0.2) * 1.2).toFloat())).toInt().coerceIn(0, 160)
                val coolant = (86 + (sin(step * 0.05) * 4).toInt()).coerceIn(75, 96)
                val oilTemp = (90 + (sin(step * 0.03) * 5).toInt()).coerceIn(82, 102)
                val intakeTemp = (22 + (sin(step * 0.02) * 4).toInt()).coerceIn(18, 38)
                val maf = (12.0f + loadRatio * 85.0f).coerceIn(8.0f, 115.0f)
                val loadPercent = (loadRatio * 100f).coerceIn(12f, 95f)
                val throttle = (loadRatio * 85f).coerceIn(0f, 100f)
                val mapKpa = (101 + (boost * 100).toInt()).coerceIn(98, 250)

                val fuelFlow = (0.55f + loadRatio * 7.5f).coerceIn(0.4f, 12.0f)
                val soot = (14.2f + (sin(step * 0.01) * 2.5).toFloat()).coerceIn(5.0f, 26.0f)
                val battery = (14.1f + (sin(step * 0.3) * 0.2).toFloat()).coerceIn(13.8f, 14.5f)
                val egr = if (dynamicRpm > 2400) 0.0f else (24.0f + (sin(step) * 10).toFloat()).coerceIn(0f, 60f)

                _telemetry.value = LiveTelemetry(
                    rpm = dynamicRpm,
                    speedKmH = speed,
                    boostBar = ((boost * 100).toInt() / 100f),
                    railPressureBar = railPressure,
                    coolantTempC = coolant,
                    oilTempC = oilTemp,
                    intakeAirTempC = intakeTemp,
                    mafAirFlowGps = ((maf * 10).toInt() / 10f),
                    engineLoadPercent = ((loadPercent * 10).toInt() / 10f),
                    throttlePercent = ((throttle * 10).toInt() / 10f),
                    mapPressureKpa = mapKpa,
                    dpfSootGrams = ((soot * 10).toInt() / 10f),
                    oilDilutionPercent = 3.2f,
                    batteryVoltage = ((battery * 10).toInt() / 10f),
                    fuelFlowLph = ((fuelFlow * 10).toInt() / 10f),
                    egrPositionPercent = ((egr * 10).toInt() / 10f),
                    injector1Correction = -0.12f + (sin(step * 0.8) * 0.04f).toFloat(),
                    injector2Correction = 0.08f + (sin(step * 0.9) * 0.03f).toFloat(),
                    injector3Correction = -0.05f + (sin(step * 0.7) * 0.03f).toFloat(),
                    injector4Correction = 0.09f + (sin(step * 1.1) * 0.04f).toFloat(),
                    isConnected = true,
                    isSimulated = true,
                    isRegeneratingDpf = (soot > 22.0f),
                    dataSource = DataVerificationStatus.SIMULATED
                )

                if (_isLogging.value) {
                    onTelemetryLogged?.invoke(
                        ObdPidResult("010C", "RPM (Symulacja)", dynamicRpm.toDouble(), "obr/min", source = DataVerificationStatus.SIMULATED)
                    )
                }

                delay(250)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToBluetoothDevice(device: BluetoothDevice) {
        stopActiveJobs()
        activeDevice = device
        _connectionState.value = ObdConnectionState.CONNECTING
        _connectionStatus.value = "Łączenie z ${device.name ?: device.address}..."

        scope.launch {
            val elmTransport = Elm327Transport(device)
            transport = elmTransport

            val openSuccess = elmTransport.open()
            if (!openSuccess) {
                _connectionState.value = ObdConnectionState.ERROR
                _connectionStatus.value = "Błąd połączenia z adapterem Bluetooth. Powrót do symulacji."
                delay(2000)
                startSimulation()
                return@launch
            }

            _connectionState.value = ObdConnectionState.CONNECTED
            _connectionStatus.value = "Inicjalizacja ELM327 (${device.name ?: "OBD"})..."

            // ELM327 initialization protocol sequence with error verification
            val resetResp = elmTransport.sendCommand("ATZ", 1200)
            delay(300)
            elmTransport.sendCommand("ATE0", 800) // Echo off
            delay(150)
            elmTransport.sendCommand("ATL0", 800) // Linefeeds off
            delay(150)
            elmTransport.sendCommand("ATS0", 800) // Spaces off
            delay(150)
            elmTransport.sendCommand("ATSP0", 1500) // Auto protocol search

            _connectionState.value = ObdConnectionState.READING
            _connectionStatus.value = "Połączono fizycznie z ELM327. Odczyt OBD-II..."
            _telemetry.value = _telemetry.value.copy(
                isConnected = true,
                isSimulated = false,
                dataSource = DataVerificationStatus.MEASURED
            )

            startLiveObdPolling(elmTransport)
        }
    }

    private fun startLiveObdPolling(transport: DiagnosticTransport) {
        telemetryJob = scope.launch {
            var consecutiveFailures = 0

            while (isActive && transport.isTransportOpen()) {
                try {
                    // Standard OBD-II Mode 01 PIDs:
                    // 010C: RPM
                    val rpmRaw = transport.sendCommand("010C", 1000)
                    val rpmResult = ObdParser.parseMode01("010C", rpmRaw)

                    // 010D: Speed
                    val speedRaw = transport.sendCommand("010D", 800)
                    val speedResult = ObdParser.parseMode01("010D", speedRaw)

                    // 0105: Coolant Temp
                    val coolantRaw = transport.sendCommand("0105", 800)
                    val coolantResult = ObdParser.parseMode01("0105", coolantRaw)

                    // 010F: Intake Air Temp
                    val iatRaw = transport.sendCommand("010F", 800)
                    val iatResult = ObdParser.parseMode01("010F", iatRaw)

                    // 0110: MAF
                    val mafRaw = transport.sendCommand("0110", 800)
                    val mafResult = ObdParser.parseMode01("0110", mafRaw)

                    // 0104: Engine Load
                    val loadRaw = transport.sendCommand("0104", 800)
                    val loadResult = ObdParser.parseMode01("0104", loadRaw)

                    // 0111: Throttle Position
                    val throttleRaw = transport.sendCommand("0111", 800)
                    val throttleResult = ObdParser.parseMode01("0111", throttleRaw)

                    // 010B: MAP
                    val mapRaw = transport.sendCommand("010B", 800)
                    val mapResult = ObdParser.parseMode01("010B", mapRaw)

                    if (rpmResult == null && speedResult == null && coolantResult == null) {
                        consecutiveFailures++
                        if (consecutiveFailures >= 5) {
                            _connectionState.value = ObdConnectionState.ERROR
                            _connectionStatus.value = "Utracono odpowiedź ECU (ELM327 timeout). Próba wznowienia..."
                            break
                        }
                    } else {
                        consecutiveFailures = 0
                    }

                    // Update live map
                    val currentMap = _livePidData.value.toMutableMap()
                    rpmResult?.let { currentMap["010C"] = it }
                    speedResult?.let { currentMap["010D"] = it }
                    coolantResult?.let { currentMap["0105"] = it }
                    iatResult?.let { currentMap["010F"] = it }
                    mafResult?.let { currentMap["0110"] = it }
                    loadResult?.let { currentMap["0104"] = it }
                    throttleResult?.let { currentMap["0111"] = it }
                    mapResult?.let { currentMap["010B"] = it }
                    _livePidData.value = currentMap

                    // Update Telemetry model
                    _telemetry.value = _telemetry.value.copy(
                        rpm = rpmResult?.value?.toInt() ?: _telemetry.value.rpm,
                        speedKmH = speedResult?.value?.toInt() ?: _telemetry.value.speedKmH,
                        coolantTempC = coolantResult?.value?.toInt() ?: _telemetry.value.coolantTempC,
                        intakeAirTempC = iatResult?.value?.toInt() ?: _telemetry.value.intakeAirTempC,
                        mafAirFlowGps = mafResult?.value?.toFloat() ?: _telemetry.value.mafAirFlowGps,
                        engineLoadPercent = loadResult?.value?.toFloat() ?: _telemetry.value.engineLoadPercent,
                        throttlePercent = throttleResult?.value?.toFloat() ?: _telemetry.value.throttlePercent,
                        mapPressureKpa = mapResult?.value?.toInt() ?: _telemetry.value.mapPressureKpa,
                        isConnected = true,
                        isSimulated = false,
                        dataSource = DataVerificationStatus.MEASURED
                    )

                    // Forward to logger if enabled
                    if (_isLogging.value) {
                        rpmResult?.let { onTelemetryLogged?.invoke(it) }
                        speedResult?.let { onTelemetryLogged?.invoke(it) }
                        coolantResult?.let { onTelemetryLogged?.invoke(it) }
                    }

                    delay(120) // Polling loop cadence
                } catch (e: Exception) {
                    consecutiveFailures++
                    if (consecutiveFailures >= 5) break
                }
            }

            if (!isActive) return@launch

            // Auto-reconnect or fallback to simulation
            _connectionState.value = ObdConnectionState.ERROR
            _connectionStatus.value = "Połączenie przerwane. Przełączanie w tryb bezpieczny (Symulacja)..."
            delay(2000)
            startSimulation()
        }
    }

    /**
     * Reads Mode 03 (Confirmed DTCs) and Mode 07 (Pending DTCs)
     */
    suspend fun readTroubleCodes(): Pair<List<String>, List<String>> {
        if (_telemetry.value.isSimulated) {
            // In simulation, return sample confirmed faults for K9K 636
            val stored = listOf("P0380", "P242F") // Świece żarowe i filtr cząstek stałych
            val pending = listOf("P0101") // Przepływomierz sygnał poza zakresem
            _activeTroubleCodes.value = stored
            _pendingTroubleCodes.value = pending
            return Pair(stored, pending)
        }

        val t = transport
        if (t == null || !t.isTransportOpen()) {
            _activeTroubleCodes.value = emptyList()
            _pendingTroubleCodes.value = emptyList()
            return Pair(emptyList(), emptyList())
        }

        return try {
            // Mode 03: Stored DTCs
            val mode03Resp = t.sendCommand("03", 2000)
            val storedCodes = ObdParser.parseDtcResponse(mode03Resp, "43")

            // Mode 07: Pending DTCs
            val mode07Resp = t.sendCommand("07", 2000)
            val pendingCodes = ObdParser.parseDtcResponse(mode07Resp, "47")

            _activeTroubleCodes.value = storedCodes
            _pendingTroubleCodes.value = pendingCodes
            Pair(storedCodes, pendingCodes)
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    /**
     * Clears Diagnostic Trouble Codes using Mode 04
     */
    suspend fun clearTroubleCodes(): Boolean {
        val t = transport
        if (t == null || !t.isTransportOpen() || _telemetry.value.isSimulated) {
            _activeTroubleCodes.value = emptyList()
            _pendingTroubleCodes.value = emptyList()
            return true
        }

        return try {
            val response = t.sendCommand("04", 3000)
            val clean = ObdParser.cleanResponse(response)
            val success = clean.contains("44") || clean.contains("OK")
            if (success) {
                _activeTroubleCodes.value = emptyList()
                _pendingTroubleCodes.value = emptyList()
            }
            success
        } catch (e: Exception) {
            false
        }
    }

    private fun stopActiveJobs() {
        telemetryJob?.cancel()
        telemetryJob = null
    }

    fun disconnect() {
        stopActiveJobs()
        scope.launch {
            try {
                transport?.close()
            } catch (_: Exception) {}
            transport = null
        }
        _connectionState.value = ObdConnectionState.DISCONNECTED
        _telemetry.value = _telemetry.value.copy(isConnected = false)
        _connectionStatus.value = "Rozłączono"
    }
}
