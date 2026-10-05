package com.example.ui.eculab

enum class EcuLabStatus {
    SUPPORTED, NRC, TIMEOUT, SESSION_REQUIRED, NOT_TESTED, CANCELLED
}

enum class EcuLabServiceFilter {
    ALL, READ_22, DTC_19, MEMORY_21
}

enum class EcuLabSort {
    REQUEST, NAME, STATUS, VALUE_ASC, VALUE_DESC
}

data class EcuDecodedValue(
    val name: String,
    val value: Any?,
    val unit: String? = null,
    val rawHex: String? = null
)

data class EcuReadResult(
    val requestHex: String,
    val name: String,
    val service: Int,
    val status: EcuLabStatus,
    val nrc: Int? = null,
    val nrcMeaning: String? = null,
    val raw: String? = null,
    val physicalValue: Any? = null,
    val unit: String? = null,
    val decodedValues: List<EcuDecodedValue> = emptyList(),
    val sourceFile: String = "SID307_00F7_550_V05_20130313T104520.xml",
    val sourceLine: Int,
    val requiredSession: String? = null
) {
    val displayRequest: String get() = "0x${requestHex.uppercase()}"
}

data class EcuLabCounters(
    val supported: Int = 0,
    val nrc: Int = 0,
    val timeout: Int = 0,
    val sessionRequired: Int = 0,
    val notTested: Int = 0,
    val cancelled: Int = 0
)

data class EcuLabUiState(
    val totalRequests: Int = 984,
    val results: List<EcuReadResult> = emptyList(),
    val statusFilter: EcuLabStatus? = null,
    val serviceFilter: EcuLabServiceFilter = EcuLabServiceFilter.ALL,
    val query: String = "",
    val groupBySession: Boolean = false,
    val sort: EcuLabSort = EcuLabSort.REQUEST,
    val isScanning: Boolean = false,
    val cancelRequested: Boolean = false,
    val startedAtMillis: Long? = null,
    val updatedAtMillis: Long? = null
) {
    val counters: EcuLabCounters
        get() {
            val grouped = results.groupingBy { it.status }.eachCount()
            val terminalCount = results.count { it.status != EcuLabStatus.NOT_TESTED }
                .coerceAtMost(totalRequests)
            return EcuLabCounters(
                supported = grouped[EcuLabStatus.SUPPORTED] ?: 0,
                nrc = grouped[EcuLabStatus.NRC] ?: 0,
                timeout = grouped[EcuLabStatus.TIMEOUT] ?: 0,
                sessionRequired = grouped[EcuLabStatus.SESSION_REQUIRED] ?: 0,
                notTested = (totalRequests - terminalCount).coerceAtLeast(0),
                cancelled = grouped[EcuLabStatus.CANCELLED] ?: 0
            )
        }

    val completedCount: Int
        get() = results.count { it.status != EcuLabStatus.NOT_TESTED }.coerceAtMost(totalRequests)
    val progressFraction: Float
        get() = if (totalRequests <= 0) 0f else completedCount.toFloat() / totalRequests.toFloat()

    val etaSeconds: Long?
        get() {
            val start = startedAtMillis ?: return null
            val now = updatedAtMillis ?: return null
            if (completedCount <= 0 || now <= start || completedCount >= totalRequests) return null
            val elapsedSeconds = (now - start) / 1000.0
            val perRequest = elapsedSeconds / completedCount
            return (perRequest * (totalRequests - completedCount)).toLong().coerceAtLeast(0)
        }

    val filteredResults: List<EcuReadResult>
        get() {
            val normalized = query.trim().lowercase().removePrefix("0x").replace(" ", "")
            val filtered = results.asSequence()
                .filter { statusFilter == null || it.status == statusFilter }
                .filter {
                    when (serviceFilter) {
                        EcuLabServiceFilter.ALL -> true
                        EcuLabServiceFilter.READ_22 -> it.service == 0x22
                        EcuLabServiceFilter.DTC_19 -> it.service == 0x19
                        EcuLabServiceFilter.MEMORY_21 -> it.service == 0x21
                    }
                }
                .filter {
                    normalized.isBlank() ||
                        it.name.lowercase().contains(normalized) ||
                        it.requestHex.lowercase().replace(" ", "").contains(normalized)
                }
                .toList()

            val sorted = when (sort) {
                EcuLabSort.REQUEST -> filtered.sortedBy { it.requestHex }
                EcuLabSort.NAME -> filtered.sortedBy { it.name.lowercase() }
                EcuLabSort.STATUS -> filtered.sortedBy { it.status.name }
                EcuLabSort.VALUE_ASC -> filtered.sortedWith(compareBy(nullsLast()) { numericValue(it.physicalValue) })
                EcuLabSort.VALUE_DESC -> filtered.sortedWith(
                    compareByDescending<EcuReadResult> { numericValue(it.physicalValue) ?: Double.NEGATIVE_INFINITY }
                )
            }
            return if (groupBySession) {
                sorted.sortedWith(
                    compareBy<EcuReadResult> { it.requiredSession ?: "~unknown" }
                        .thenBy { it.requestHex }
                )
            } else sorted
        }

    fun withResult(result: EcuReadResult, timestampMillis: Long): EcuLabUiState {
        val index = results.indexOfFirst { it.requestHex.equals(result.requestHex, ignoreCase = true) }
        val next = if (index >= 0) {
            results.toMutableList().also { it[index] = result }
        } else {
            results + result
        }
        return copy(results = next, updatedAtMillis = timestampMillis)
    }

    companion object {
        private fun numericValue(value: Any?): Double? = when (value) {
            is Number -> value.toDouble()
            else -> null
        }
    }
}
