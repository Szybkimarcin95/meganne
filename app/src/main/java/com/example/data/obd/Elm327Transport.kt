package com.example.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class Elm327Transport(private val device: BluetoothDevice) : DiagnosticTransport {
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    @SuppressLint("MissingPermission")
    override suspend fun open(): Boolean = withContext(Dispatchers.IO) {
        try {
            close()
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket = sock
            // Attempt connect with timeout safeguard
            val connected = withTimeoutOrNull(8000) {
                sock.connect()
                true
            } ?: false

            if (connected) {
                inputStream = sock.inputStream
                outputStream = sock.outputStream
                // Clear initial garbage from adapter buffer
                clearBuffer()
                true
            } else {
                close()
                false
            }
        } catch (e: Exception) {
            close()
            false
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
    }

    override fun isTransportOpen(): Boolean {
        return socket?.isConnected == true
    }

    private fun clearBuffer() {
        try {
            val stream = inputStream ?: return
            while (stream.available() > 0) {
                stream.read()
            }
        } catch (_: Exception) {}
    }

    override suspend fun sendCommand(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        if (!isTransportOpen()) return@withContext "ERROR: TRANSPORT CLOSED"
        val out = outputStream ?: return@withContext "ERROR: NO OUTPUT STREAM"
        val inStream = inputStream ?: return@withContext "ERROR: NO INPUT STREAM"

        clearBuffer()

        try {
            out.write((command.trim() + "\r").toByteArray())
            out.flush()
        } catch (e: Exception) {
            return@withContext "ERROR: WRITE FAILED"
        }

        val result = StringBuilder()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                if (inStream.available() > 0) {
                    val b = inStream.read()
                    if (b == -1) break
                    val c = b.toChar()
                    result.append(c)
                    // ELM327 command completion is signaled by '>'
                    if (c == '>') {
                        break
                    }
                } else {
                    delay(15)
                }
            } catch (e: Exception) {
                return@withContext "ERROR: READ FAILED"
            }
        }

        val raw = result.toString()
        if (raw.isBlank()) {
            "ERROR: TIMEOUT"
        } else {
            raw
        }
    }
}
