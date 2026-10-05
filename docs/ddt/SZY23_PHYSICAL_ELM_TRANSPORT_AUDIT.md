# SZY-23 — Physical ELM327 READ Transport Audit

## Scope

This checkpoint is limited to establishing the physical READ transport path on the authorized Android/Termux device before any ECU request is sent.

Safety boundary is unchanged:

- no 0x10 DiagnosticSessionControl
- no 0x11 ECUReset
- no 0x14 ClearDiagnosticInformation
- no 0x27 SecurityAccess
- no 0x2E WriteDataByIdentifier
- no 0x2F / 0x30 controls
- no 0x31 / 0x32 routines
- no programming / transfer services
- no seed-key implementation
- no 984-request physical sweep

## Device audit

Authorized device:

- Android 16
- model: 23117RA68G
- kernel: 6.12.30 Android
- Termux API is installed

Direct Termux Bluetooth/serial findings:

- no /dev/ttyUSB* or /dev/ttyACM* currently visible
- `lsusb` is not installed
- `rfkill`: unavailable
- `hciconfig`: unavailable
- `bt-device`: unavailable
- `bluetoothctl`: unavailable
- `rfcomm`: unavailable
- no Termux Bluetooth scan/connect API command is installed

This confirms that the current Android/Termux environment is not exposing the Android Bluetooth stack as a native BlueZ/RFCOMM device to Termux.

## Existing Android Bluetooth bridge

Installed package:

`pl.netaudit.bluetoothbridge`

Application label:

`Net Audit Bluetooth Bridge`

Version:

`0.1.0`

Manifest confirms Android-native Bluetooth access:

- BLUETOOTH_SCAN
- BLUETOOTH_CONNECT
- Bluetooth Classic feature
- BLE feature

Launcher activity:

`pl.netaudit.bluetoothbridge.MainActivity`

The APK advertises a local HTTP API:

`http://127.0.0.1:8766`

Static inspection of the installed APK confirms these endpoints:

- `/status`
- `/devices`
- `/scan/start`
- `/scan/stop`

The current APK contains BLE and Classic discovery code.

However, static inspection found no evidence of a completed ELM serial transport API such as:

- RFCOMM connect
- GATT UART connect
- serial write endpoint
- serial read endpoint
- ELM command endpoint

Therefore the installed bridge is currently a discovery bridge, not yet an ELM327 transport bridge.

At audit time the app process was not running and nothing was listening on 127.0.0.1:8766.

## Important correction to the proposed Termux path

Installing BlueZ and creating `/dev/rfcomm0` is not the correct primary path on this Android 16 device.

Android owns the Bluetooth stack. Termux is sandboxed and is not currently receiving a native RFCOMM tty from Android.

The correct architecture for this device is:

```
SID307 READ scheduler
        |
        v
Termux HTTP transport client
        |
        v
127.0.0.1:8766
        |
        v
Android Bluetooth Bridge app
        |
        v
Android BluetoothSocket / BLE GATT
        |
        v
ELM327
        |
        v
CAN 11-bit / 500 kbit/s
        |
        v
SID307 ECU
```

The Python scheduler remains unchanged. Only the physical transport adapter changes.

## Required bridge milestone before physical DID smoke test

The Android bridge needs a narrowly scoped ELM READ transport API.

Minimum required operations:

1. list discovered / bonded devices
2. connect to one explicit ELM device
3. return connection state
4. write ASCII ELM command bytes
5. read the response until ELM prompt `>`
6. disconnect
7. expose no arbitrary UDS write helper

Suggested local-only HTTP surface:

- `GET /status`
- `GET /devices`
- `POST /connect`
- `POST /elm/command`
- `POST /disconnect`

The bridge must bind to 127.0.0.1 only.

The server must not contain ECU service logic. It only transports ELM ASCII commands.

## First physical smoke test after bridge completion

Do not begin with 5 DIDs.

Use one staged identity/read path first:

1. initialize ELM
2. verify protocol
3. verify addressing
4. execute one known-safe source-backed READ
5. capture raw request/response
6. decode it with the existing PR #6 decoder
7. stop

Only then expand to the five-read set.

Candidate staged reads:

- `2181` VIN
- `222005` battery voltage
- then `222496`, `222401`, `222801`, `222026`

## ELM command framing note

The existing scheduler stores UDS payload bytes such as `222496`.

An ELM327 in normal ISO-TP mode expects the UDS payload as ASCII hex, not a manually constructed raw CAN frame with a PCI length byte unless the adapter has explicitly been switched into a lower-level raw CAN mode.

Therefore the initial transport should send source-backed payload text such as:

`222496\r`

after safe ELM initialization and header configuration, rather than inventing `02 22 24 96` framing.

Expected positive UDS payload begins with:

`62 24 96 ...`

The ELM may include ISO-TP / formatting details depending on configuration, so the transport adapter must normalize the ELM text before passing bytes to the existing decoder.

## Current checkpoint result

Physical ECU communication has not been attempted.

Reason:

The installed Android bridge does not yet expose ELM connect/read/write transport operations.

This is an infrastructure gap, not a SID307 decoder/scheduler failure.
