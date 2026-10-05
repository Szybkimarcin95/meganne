package com.example.ui.eculab

sealed interface EcuLabEvent {
    data class ScanStarted(val total: Int, val atMillis: Long) : EcuLabEvent
    data class RequestCompleted(val result: EcuReadResult, val atMillis: Long) : EcuLabEvent
    data class ScanFinished(val atMillis: Long) : EcuLabEvent
    data class ScanCancelled(val remaining: List<EcuReadResult>, val atMillis: Long) : EcuLabEvent
}

fun reduceEcuLabEvent(state: EcuLabUiState, event: EcuLabEvent): EcuLabUiState = when (event) {
    is EcuLabEvent.ScanStarted -> state.copy(
        totalRequests = event.total,
        results = emptyList(),
        isScanning = true,
        cancelRequested = false,
        startedAtMillis = event.atMillis,
        updatedAtMillis = event.atMillis
    )
    is EcuLabEvent.RequestCompleted -> state.withResult(event.result, event.atMillis)
    is EcuLabEvent.ScanFinished -> state.copy(
        isScanning = false,
        cancelRequested = false,
        updatedAtMillis = event.atMillis
    )
    is EcuLabEvent.ScanCancelled -> event.remaining.fold(
        state.copy(isScanning = false, cancelRequested = true, updatedAtMillis = event.atMillis)
    ) { acc, result ->
        acc.withResult(result.copy(status = EcuLabStatus.CANCELLED), event.atMillis)
    }
}
