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
}

/**
 * Thread-safe, single-transaction wrapper around DiagnosticTransport.
 *
 * Guarantees:
 * 1. Mutual exclusion: Exactly ONE diagnostic transaction in-flight at any time.
 * 2. Firewall protection: Disallowed commands are blocked before touching the transport.
 * 3. Session generation tracking: In-flight operations from dead/reconnected sessions are aborted.
 * 4. Response parsing: Handles fragmentation, timeouts, and adapter error codes (BUFFER FULL, NO DATA).
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
     * Executes a single diagnostic command through the safe pipeline.
     */
    suspend fun executeCommand(
        rawCommand: String,
        timeoutMs: Long = 1200L,
        allowMode04Clear: Boolean = false
    ): DiagnosticExecutionResult = withContext(Dispatchers.IO) {
        val expectedGeneration = currentGeneration.get()

        // Step 1: Pre-execution Command Firewall validation
        val validation = CommandFirewall.validate(rawCommand, allowMode04Clear)
        if (validation is CommandValidationResult.Blocked) {
            return@withContext DiagnosticExecutionResult.BlockedByFirewall(
                rawCommand = rawCommand,
                reason = validation.reason
            )
        }
        val allowed = validation as CommandValidationResult.Allowed

        // Step 2: Acquire transaction lock (Guarantees single transaction on bus)
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

            // Step 3: Check session validity after command execution
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
                    DiagnosticExecutionResult.TransportError(
                        errorType = "BUFFER_FULL",
                        message = "Przepełnienie bufora ELM327",
                        sessionGeneration = currentGeneration.get()
                    )
                }
                upper.contains("CAN ERROR") -> {
                    DiagnosticExecutionResult.TransportError(
                        errorType = "CAN_ERROR",
                        message = "Błąd magistrali CAN",
                        sessionGeneration = currentGeneration.get()
                    )
                }
                upper.contains("UNABLE TO CONNECT") -> {
                    DiagnosticExecutionResult.TransportError(
                        errorType = "UNABLE_TO_CONNECT",
                        message = "Brak połączenia z magistralą pojazdu",
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
