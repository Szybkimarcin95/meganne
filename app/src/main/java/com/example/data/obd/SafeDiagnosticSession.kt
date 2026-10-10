package com.example.data.obd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

sealed class DiagnosticExecutionResult {
    data class Success(
        val rawResponse: String,
        val cleanedResponse: String,
        val rttMs: Long,
        val category: String,
        val sessionGeneration: Long
    ) : DiagnosticExecutionResult()

    data class BlockedByFirewall(
        val rawCommand: String,
        val reason: String
    ) : DiagnosticExecutionResult()

    data class TransportError(
        val errorType: String,
        val message: String,
        val sessionGeneration: Long
    ) : DiagnosticExecutionResult()

    data class AdapterError(
        val errorType: String,
        val message: String,
        val sessionGeneration: Long
    ) : DiagnosticExecutionResult()
}

/**
 * Thread-safe, single-transaction wrapper around DiagnosticTransport.
 *
 * Guarantees:
 * 1. Mutual exclusion: Exactly ONE diagnostic transaction in-flight at any time.
 * 2. Firewall protection: Disallowed commands are blocked before touching the transport.
 * 3. Session generation tracking: In-flight operations from dead/reconnected sessions are aborted.
 * 4. Response parsing: Handles fragmentation, timeouts, and adapter error codes (BUFFER FULL, NO DATA, etc.).
 * 5. Clean resource release: Mutex is guaranteed released on timeout, error, or coroutine cancellation.
 */
class SafeDiagnosticSession internal constructor(
    private val transport: DiagnosticTransport,
    private val validateCommand: (String) -> CommandValidationResult
) {
    /** Production entry point always uses the unchanged CommandFirewall. */
    constructor(transport: DiagnosticTransport) : this(transport, CommandFirewall::validate)

    private val transactionMutex = Mutex()
    private val currentGeneration = AtomicLong(1)

    val sessionGeneration: Long
        get() = currentGeneration.get()

    fun resetSessionGeneration(): Long {
        return currentGeneration.incrementAndGet()
    }

    fun isTransportOpen(): Boolean {
        return transport.isTransportOpen()
    }

    suspend fun open(): Boolean = withContext(Dispatchers.IO) {
        transactionMutex.withLock {
            resetSessionGeneration()
            transport.open()
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        transactionMutex.withLock {
            resetSessionGeneration()
            transport.close()
        }
    }

    /**
     * Executes a single diagnostic command through the safe read-only pipeline.
     */
    suspend fun executeCommand(
        rawCommand: String,
        timeoutMs: Long = 1200L
    ): DiagnosticExecutionResult = withContext(Dispatchers.IO) {
        val expectedGeneration = currentGeneration.get()

        // Step 1: Pre-execution Command Firewall validation (Transport is NEVER touched if blocked)
        val validation = validateCommand(rawCommand)
        if (validation is CommandValidationResult.Blocked) {
            return@withContext DiagnosticExecutionResult.BlockedByFirewall(
                rawCommand = rawCommand,
                reason = validation.reason
            )
        }
        val allowed = validation as CommandValidationResult.Allowed

        // Step 2: Acquire transaction lock (Guarantees single transaction on bus, no interleaving)
        transactionMutex.withLock {
            executeValidatedCommandLocked(allowed, timeoutMs, expectedGeneration)
        }
    }

    /** Called only while transactionMutex is held; never acquires it recursively. */
    private suspend fun executeValidatedCommandLocked(
        allowed: CommandValidationResult.Allowed,
        timeoutMs: Long,
        expectedGeneration: Long
    ): DiagnosticExecutionResult {
        // Check session validity after lock acquisition
        if (currentGeneration.get() != expectedGeneration) {
            return DiagnosticExecutionResult.TransportError(
                errorType = "SESSION_EXPIRED",
                message = "Sesja została zresetowana przed rozpoczęciem transakcji",
                sessionGeneration = currentGeneration.get()
            )
        }

        if (!transport.isTransportOpen()) {
            return DiagnosticExecutionResult.TransportError(
                errorType = "TRANSPORT_CLOSED",
                message = "Interfejs diagnostyczny nie jest otwarty",
                sessionGeneration = currentGeneration.get()
            )
        }

        val startTime = System.currentTimeMillis()
        val response = try {
            withTimeoutOrNull(timeoutMs) {
                transport.sendCommand(allowed.normalizedCommand, timeoutMs)
            } ?: "ERROR: TIMEOUT"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return DiagnosticExecutionResult.TransportError(
                errorType = "IO_EXCEPTION",
                message = e.message ?: "Błąd transmisji transportu",
                sessionGeneration = currentGeneration.get()
            )
        }

        val rttMs = System.currentTimeMillis() - startTime

        // Step 3: Check session validity after command execution (Rejects stale result if session reset during await)
        if (currentGeneration.get() != expectedGeneration) {
            return DiagnosticExecutionResult.TransportError(
                errorType = "SESSION_EXPIRED",
                message = "Sesja wygasła w trakcie wykonywania transakcji",
                sessionGeneration = currentGeneration.get()
            )
        }

        // Step 4: Analyze raw ELM327 / bus responses
        val clean = ObdParser.cleanResponse(response)
        val upper = response.uppercase()

        return when {
            upper.contains("BUFFER FULL") -> {
                DiagnosticExecutionResult.AdapterError(
                    errorType = "BUFFER_FULL",
                    message = "Przepełnienie bufora ELM327",
                    sessionGeneration = currentGeneration.get()
                )
            }
            upper.contains("CAN ERROR") -> {
                DiagnosticExecutionResult.AdapterError(
                    errorType = "CAN_ERROR",
                    message = "Błąd magistrali CAN",
                    sessionGeneration = currentGeneration.get()
                )
            }
            upper.contains("UNABLE TO CONNECT") -> {
                DiagnosticExecutionResult.AdapterError(
                    errorType = "UNABLE_TO_CONNECT",
                    message = "Brak połączenia adaptera z magistralą pojazdu",
                    sessionGeneration = currentGeneration.get()
                )
            }
            upper.contains("STOPPED") -> {
                DiagnosticExecutionResult.AdapterError(
                    errorType = "STOPPED",
                    message = "Operacja adaptera ELM327 zatrzymana (STOPPED)",
                    sessionGeneration = currentGeneration.get()
                )
            }
            upper.contains("NO DATA") -> {
                DiagnosticExecutionResult.AdapterError(
                    errorType = "NO_DATA",
                    message = "Brak danych z ECU dla zapytania (NO DATA)",
                    sessionGeneration = currentGeneration.get()
                )
            }
            upper.contains("TIMEOUT") || response == "ERROR: TIMEOUT" -> {
                DiagnosticExecutionResult.TransportError(
                    errorType = "TIMEOUT",
                    message = "Przekroczono limit czasu odpowiedzi ECU (${timeoutMs}ms)",
                    sessionGeneration = currentGeneration.get()
                )
            }
            upper.contains("ERROR") -> {
                DiagnosticExecutionResult.AdapterError(
                    errorType = "ERROR",
                    message = "Błąd adaptera ELM327 (ERROR)",
                    sessionGeneration = currentGeneration.get()
                )
            }
            else -> {
                DiagnosticExecutionResult.Success(
                    rawResponse = response,
                    cleanedResponse = clean,
                    rttMs = rttMs,
                    category = allowed.category,
                    sessionGeneration = currentGeneration.get()
                )
            }
        }
    }

    /**
     * Atomic adapter setup/read/restore scaffold. Global firewall stays authoritative:
     * addressed setup is currently blocked before ANY transport operation.
     * restoreCommands must describe the caller's reviewed previous adapter configuration.
     * No RX inference, default protocol, reset, or universal DID allowlist is supplied.
     */
    suspend fun executeAddressedTransaction(
        txAddress: String,
        rxAddress: String,
        protocol: String?,
        commands: List<String>,
        restoreCommands: List<String>,
        timeoutMs: Long = 1500L
    ): AddressedTransactionResult = withContext(Dispatchers.IO) {
        fun blocked(command: String, reason: String) = AddressedTransactionResult(
            failure = DiagnosticExecutionResult.BlockedByFirewall(command, reason)
        )
        val addressPattern = Regex("[0-9A-Fa-f]{3}|[0-9A-Fa-f]{8}")
        if (!addressPattern.matches(txAddress) || !addressPattern.matches(rxAddress) ||
            txAddress.length != rxAddress.length || commands.isEmpty() || restoreCommands.isEmpty() ||
            timeoutMs !in 1L..10000L || (protocol != null && !Regex("[0-9A-Ca-c]").matches(protocol))) {
            return@withContext blocked("", "INVALID_ADDRESSED_PLAN")
        }
        if (restoreCommands.any { !it.trim().uppercase().startsWith("AT") }) {
            return@withContext blocked("", "RESTORE_MUST_CONTAIN_ADAPTER_COMMANDS_ONLY")
        }
        if (commands.any { it.trim().uppercase().startsWith("AT") }) {
            return@withContext blocked("", "READ_PHASE_MUST_CONTAIN_ECU_COMMANDS_ONLY")
        }
        val setup = listOfNotNull(protocol?.let { "ATSP$it" }, "ATSH$txAddress", "ATCRA$rxAddress")
        // Validate complete setup/read/restore plan BEFORE changing adapter state.
        val validated = mutableListOf<CommandValidationResult.Allowed>()
        for (command in setup + commands + restoreCommands) {
            when (val validation = validateCommand(command)) {
                is CommandValidationResult.Blocked -> return@withContext blocked(command, validation.reason)
                is CommandValidationResult.Allowed -> validated += validation
            }
        }
        val generation = sessionGeneration
        transactionMutex.withLock {
            val setupResults = mutableListOf<DiagnosticExecutionResult>()
            val readResults = mutableListOf<DiagnosticExecutionResult>()
            val restoreResults = mutableListOf<DiagnosticExecutionResult>()
            var failure: DiagnosticExecutionResult? = null
            var touched = false
            try {
                for (command in validated.take(setup.size)) {
                    touched = true
                    val result = executeValidatedCommandLocked(command, timeoutMs, generation)
                    setupResults += result
                    // AT acknowledgement is adapter metadata, never ECU evidence.
                    if (result !is DiagnosticExecutionResult.Success ||
                        result.cleanedResponse != "OK") {
                        failure = if (result is DiagnosticExecutionResult.Success)
                            DiagnosticExecutionResult.AdapterError("SETUP_REJECTED", "Adapter nie potwierdził konfiguracji", generation)
                        else result
                        break
                    }
                }
                if (failure == null) {
                    for (command in validated.drop(setup.size).take(commands.size)) {
                        val result = executeValidatedCommandLocked(command, timeoutMs, generation)
                        readResults += result
                        if (result !is DiagnosticExecutionResult.Success) { failure = result; break }
                    }
                }
            } finally {
                if (touched) withContext(NonCancellable) {
                    // Never apply a previous configuration to a new session generation.
                    if (sessionGeneration == generation) {
                        for (command in validated.takeLast(restoreCommands.size)) {
                            val result = executeValidatedCommandLocked(command, timeoutMs, generation)
                            restoreResults += result
                            if (result !is DiagnosticExecutionResult.Success || result.cleanedResponse != "OK") {
                                failure = DiagnosticExecutionResult.AdapterError("RESTORE_FAILED", "Konfiguracja adaptera nie została przywrócona", generation)
                                resetSessionGeneration()
                                transport.close()
                                break
                            }
                        }
                    } else {
                        failure = DiagnosticExecutionResult.TransportError("SESSION_EXPIRED", "Sesja zmieniła się podczas transakcji", sessionGeneration)
                        transport.close()
                    }
                }
            }
            AddressedTransactionResult(setupResults, readResults, restoreResults, failure)
        }
    }
}

/** Only readResults may be parsed as ECU replies, and still require protocol validation. */
data class AddressedTransactionResult(
    val setupResults: List<DiagnosticExecutionResult> = emptyList(),
    val readResults: List<DiagnosticExecutionResult> = emptyList(),
    val restoreResults: List<DiagnosticExecutionResult> = emptyList(),
    val failure: DiagnosticExecutionResult? = null
)
