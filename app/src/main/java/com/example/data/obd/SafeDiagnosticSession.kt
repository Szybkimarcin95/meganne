package com.example.data.obd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
class SafeDiagnosticSession(
    private val transport: DiagnosticTransport
) {
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
        val validation = CommandFirewall.validate(rawCommand)
        if (validation is CommandValidationResult.Blocked) {
            return@withContext DiagnosticExecutionResult.BlockedByFirewall(
                rawCommand = rawCommand,
                reason = validation.reason
            )
        }
        val allowed = validation as CommandValidationResult.Allowed

        // Step 2: Acquire transaction lock (Guarantees single transaction on bus, no interleaving)
        transactionMutex.withLock {
            // Check session validity after lock acquisition
            if (currentGeneration.get() != expectedGeneration) {
                return@withContext DiagnosticExecutionResult.TransportError(
                    errorType = "SESSION_EXPIRED",
                    message = "Sesja została zresetowana przed rozpoczęciem transakcji",
                    sessionGeneration = currentGeneration.get()
                )
            }

            if (!transport.isTransportOpen()) {
                return@withContext DiagnosticExecutionResult.TransportError(
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
                return@withContext DiagnosticExecutionResult.TransportError(
                    errorType = "IO_EXCEPTION",
                    message = e.message ?: "Błąd transmisji transportu",
                    sessionGeneration = currentGeneration.get()
                )
            }

            val rttMs = System.currentTimeMillis() - startTime

            // Step 3: Check session validity after command execution (Rejects stale result if session reset during await)
            if (currentGeneration.get() != expectedGeneration) {
                return@withContext DiagnosticExecutionResult.TransportError(
                    errorType = "SESSION_EXPIRED",
                    message = "Sesja wygasła w trakcie wykonywania transakcji",
                    sessionGeneration = currentGeneration.get()
                )
            }

            // Step 4: Analyze raw ELM327 / bus responses
            val clean = ObdParser.cleanResponse(response)
            val upper = response.uppercase()

            return@withContext when {
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
    }
}
