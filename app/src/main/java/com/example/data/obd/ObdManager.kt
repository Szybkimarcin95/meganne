package com.example.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.example.data.model.LiveTelemetry
import com.example.data.model.TelemetryField
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
        set(value) {
            field = value
            safeSession = value?.let { SafeDiagnosticSession(it) }
        }
    internal var safeSession: SafeDiagnosticSession? = null
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
        _livePidData.value = emptyMap()
        _telemetry.value = LiveTelemetry(
            isConnected = true,
            isSimulated = true
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

                val sampleTimestamp = System.currentTimeMillis()
                _telemetry.value = LiveTelemetry(
                    rpm = TelemetryField.simulated(dynamicRpm, sampleTimestamp),
                    speedKmH = TelemetryField.simulated(speed, sampleTimestamp),
                    boostBar = TelemetryField.simulated(((boost * 100).toInt() / 100f), sampleTimestamp),
                    railPressureBar = TelemetryField.simulated(railPressure, sampleTimestamp),
                    coolantTempC = TelemetryField.simulated(coolant, sampleTimestamp),
                    oilTempC = TelemetryField.simulated(oilTemp, sampleTimestamp),
                    intakeAirTempC = TelemetryField.simulated(intakeTemp, sampleTimestamp),
                    mafAirFlowGps = TelemetryField.simulated(((maf * 10).toInt() / 10f), sampleTimestamp),
                    engineLoadPercent = TelemetryField.simulated(((loadPercent * 10).toInt() / 10f), sampleTimestamp),
                    throttlePercent = TelemetryField.simulated(((throttle * 10).toInt() / 10f), sampleTimestamp),
                    mapPressureKpa = TelemetryField.simulated(mapKpa, sampleTimestamp),
                    dpfSootGrams = TelemetryField.simulated(((soot * 10).toInt() / 10f), sampleTimestamp),
                    oilDilutionPercent = TelemetryField.simulated(3.2f, sampleTimestamp),
                    batteryVoltage = TelemetryField.simulated(((battery * 10).toInt() / 10f), sampleTimestamp),
                    fuelFlowLph = TelemetryField.simulated(((fuelFlow * 10).toInt() / 10f), sampleTimestamp),
                    egrPositionPercent = TelemetryField.simulated(((egr * 10).toInt() / 10f), sampleTimestamp),
                    injector1Correction = TelemetryField.simulated(-0.12f + (sin(step * 0.8) * 0.04f).toFloat(), sampleTimestamp),
                    injector2Correction = TelemetryField.simulated(0.08f + (sin(step * 0.9) * 0.03f).toFloat(), sampleTimestamp),
                    injector3Correction = TelemetryField.simulated(-0.05f + (sin(step * 0.7) * 0.03f).toFloat(), sampleTimestamp),
                    injector4Correction = TelemetryField.simulated(0.09f + (sin(step * 1.1) * 0.04f).toFloat(), sampleTimestamp),
                    isRegeneratingDpf = TelemetryField.simulated(soot > 22.0f, sampleTimestamp),
                    isConnected = true,
                    isSimulated = true
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

            val session = safeSession ?: SafeDiagnosticSession(elmTransport)

            val openSuccess = session.open()
            if (!openSuccess) {
                _connectionState.value = ObdConnectionState.ERROR
                _connectionStatus.value = "Błąd połączenia z adapterem Bluetooth. Powrót do symulacji."
                delay(2000)
                startSimulation()
                return@launch
            }

            _connectionState.value = ObdConnectionState.CONNECTED
            _connectionStatus.value = "Inicjalizacja ELM327 (${device.name ?: "OBD"})..."

            // ELM327 initialization protocol sequence with error verification & firewall protection
            session.executeCommand("ATZ", 1200)
            delay(300)
            session.executeCommand("ATE0", 800) // Echo off
            delay(150)
            session.executeCommand("ATL0", 800) // Linefeeds off
            delay(150)
            session.executeCommand("ATS0", 800) // Spaces off
            delay(150)
            session.executeCommand("ATSP0", 1500) // Auto protocol search

            _connectionState.value = ObdConnectionState.READING
            _connectionStatus.value = "Połączono fizycznie z ELM327. Odczyt OBD-II..."
            _livePidData.value = emptyMap()
            _telemetry.value = LiveTelemetry(
                isConnected = true,
                isSimulated = false
            )

            startLiveObdPolling(session)
        }
    }

    private fun startLiveObdPolling(session: SafeDiagnosticSession) {
        telemetryJob = scope.launch {
            var consecutiveFailures = 0

            while (isActive && session.isTransportOpen()) {
                try {
                    // Standard OBD-II Mode 01 PIDs safely executed through firewall
                    // 010C: RPM
                    val rpmExec = session.executeCommand("010C", 1000)
                    val rpmRaw = (rpmExec as? DiagnosticExecutionResult.Success)?.rawResponse ?: ""
                    val rpmResult = ObdParser.parseMode01("010C", rpmRaw)

                    // 010D: Speed
                    val speedExec = session.executeCommand("010D", 800)
                    val speedRaw = (speedExec as? DiagnosticExecutionResult.Success)?.rawResponse ?: ""
                    val speedResult = ObdParser.parseMode01("010D", speedRaw)

                    // 0105: Coolant Temp
                    val coolantExec = session.executeCommand("0105", 800)
                    val coolantRaw = (coolantExec as? DiagnosticExecutionResult.Success)?.rawResponse ?: ""
                    val coolantResult = ObdParser.parseMode01("0105", coolantRaw)

                    // 010F: Intake Air Temp
                    val iatExec = session.executeCommand("010F", 800)
                    val iatRaw = (iatExec as? DiagnosticExecutionResult.Success)?.rawResponse ?: ""
                    val iatResult = ObdParser.parseMode01("010F", iatRaw)

                    // 0110: MAF
                    val mafExec = session.executeCommand("0110", 800)
                    val mafRaw = (mafExec as? DiagnosticExecutionResult.Success)?.rawResponse ?: ""
                    val mafResult = ObdParser.parseMode01("0110", mafRaw)

                    // 0104: Engine Load
                    val loadExec = session.executeCommand("0104", 800)
                    val loadRaw = (loadExec as? DiagnosticExecutionResult.Success)?.rawResponse ?: ""
                    val loadResult = ObdParser.parseMode01("0104", loadRaw)

                    // 0111: Throttle Position
                    val throttleExec = session.executeCommand("0111", 800)
                    val throttleRaw = (throttleExec as? DiagnosticExecutionResult.Success)?.rawResponse ?: ""
                    val throttleResult = ObdParser.parseMode01("0111", throttleRaw)

                    // 010B: MAP
                    val mapExec = session.executeCommand("010B", 800)
                    val mapRaw = (mapExec as? DiagnosticExecutionResult.Success)?.rawResponse ?: ""
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

                    // Keep the live PID cache aligned with the latest poll only.
                    val currentMap = _livePidData.value.toMutableMap()
                    if (rpmResult != null) currentMap["010C"] = rpmResult else currentMap.remove("010C")
                    if (speedResult != null) currentMap["010D"] = speedResult else currentMap.remove("010D")
                    if (coolantResult != null) currentMap["0105"] = coolantResult else currentMap.remove("0105")
                    if (iatResult != null) currentMap["010F"] = iatResult else currentMap.remove("010F")
                    if (mafResult != null) currentMap["0110"] = mafResult else currentMap.remove("0110")
                    if (loadResult != null) currentMap["0104"] = loadResult else currentMap.remove("0104")
                    if (throttleResult != null) currentMap["0111"] = throttleResult else currentMap.remove("0111")
                    if (mapResult != null) currentMap["010B"] = mapResult else currentMap.remove("010B")
                    _livePidData.value = currentMap

                    // Every signal carries its own provenance. A failed current poll
                    // becomes UNAVAILABLE instead of retaining a stale measured value.
                    val pollTimestamp = System.currentTimeMillis()
                    _telemetry.value = _telemetry.value.copy(
                        rpm = rpmResult?.let {
                            TelemetryField.measured(it.value.toInt(), it.timestamp)
                        } ?: TelemetryField.unavailable("LATEST_POLL_NO_VALID_VALUE", pollTimestamp),
                        speedKmH = speedResult?.let {
                            TelemetryField.measured(it.value.toInt(), it.timestamp)
                        } ?: TelemetryField.unavailable("LATEST_POLL_NO_VALID_VALUE", pollTimestamp),
                        coolantTempC = coolantResult?.let {
                            TelemetryField.measured(it.value.toInt(), it.timestamp)
                        } ?: TelemetryField.unavailable("LATEST_POLL_NO_VALID_VALUE", pollTimestamp),
                        intakeAirTempC = iatResult?.let {
                            TelemetryField.measured(it.value.toInt(), it.timestamp)
                        } ?: TelemetryField.unavailable("LATEST_POLL_NO_VALID_VALUE", pollTimestamp),
                        mafAirFlowGps = mafResult?.let {
                            TelemetryField.measured(it.value.toFloat(), it.timestamp)
                        } ?: TelemetryField.unavailable("LATEST_POLL_NO_VALID_VALUE", pollTimestamp),
                        engineLoadPercent = loadResult?.let {
                            TelemetryField.measured(it.value.toFloat(), it.timestamp)
                        } ?: TelemetryField.unavailable("LATEST_POLL_NO_VALID_VALUE", pollTimestamp),
                        throttlePercent = throttleResult?.let {
                            TelemetryField.measured(it.value.toFloat(), it.timestamp)
                        } ?: TelemetryField.unavailable("LATEST_POLL_NO_VALID_VALUE", pollTimestamp),
                        mapPressureKpa = mapResult?.let {
                            TelemetryField.measured(it.value.toInt(), it.timestamp)
                        } ?: TelemetryField.unavailable("LATEST_POLL_NO_VALID_VALUE", pollTimestamp),
                        isConnected = true,
                        isSimulated = false
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

        val session = safeSession
        if (session == null || !session.isTransportOpen()) {
            _activeTroubleCodes.value = emptyList()
            _pendingTroubleCodes.value = emptyList()
            return Pair(emptyList(), emptyList())
        }

        return try {
            // Mode 03: Stored DTCs (Safely executed through CommandFirewall)
            val mode03Exec = session.executeCommand("03", 2000)
            val mode03Resp = (mode03Exec as? DiagnosticExecutionResult.Success)?.rawResponse ?: ""
            val storedCodes = ObdParser.parseDtcResponse(mode03Resp, "43")

            // Mode 07: Pending DTCs (Safely executed through CommandFirewall)
            val mode07Exec = session.executeCommand("07", 2000)
            val mode07Resp = (mode07Exec as? DiagnosticExecutionResult.Success)?.rawResponse ?: ""
            val pendingCodes = ObdParser.parseDtcResponse(mode07Resp, "47")

            _activeTroubleCodes.value = storedCodes
            _pendingTroubleCodes.value = pendingCodes
            Pair(storedCodes, pendingCodes)
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    /**
     * Clears Diagnostic Trouble Codes using Mode 04 (Protected action)
     */
    suspend fun clearTroubleCodes(): Boolean {
        val session = safeSession
        if (session == null || !session.isTransportOpen() || _telemetry.value.isSimulated) {
            _activeTroubleCodes.value = emptyList()
            _pendingTroubleCodes.value = emptyList()
            return true
        }

        return try {
            val execResult = session.executeCommand("04", 3000)
            val clean = (execResult as? DiagnosticExecutionResult.Success)?.cleanedResponse ?: ""
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
        val sessionToClose = safeSession
        transport = null
        safeSession = null
        scope.launch {
            try {
                sessionToClose?.close()
            } catch (_: Exception) {}
        }
        _connectionState.value = ObdConnectionState.DISCONNECTED
        _activeTroubleCodes.value = emptyList()
        _pendingTroubleCodes.value = emptyList()
        _livePidData.value = emptyMap()
        _telemetry.value = LiveTelemetry(
            isConnected = false,
            isSimulated = false
        )
        _connectionStatus.value = "Rozłączono"
    }

    internal fun setSimulationMode(enabled: Boolean) {
        stopActiveJobs()
        _livePidData.value = emptyMap()
        _telemetry.value = LiveTelemetry(
            isConnected = enabled,
            isSimulated = enabled
        )
    }
}
