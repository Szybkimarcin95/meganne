package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.DiagnosticFaultHistoryEntry
import com.example.data.model.DtcCode
import com.example.data.model.DtcSeverity
import com.example.data.model.EngineComponent
import com.example.data.model.FuelRecord
import com.example.data.model.FuseItem
import com.example.data.model.RepairGuide
import com.example.data.model.SensorTrendPoint
import com.example.data.model.ServiceRecord
import com.example.data.model.TorqueSpec
import com.example.data.model.toDomain
import com.example.data.model.toEntity
import com.example.data.obd.DataVerificationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OverlordRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val serviceDao = db.serviceRecordDao()
    private val fuelDao = db.fuelRecordDao()
    private val telemetryDao = db.telemetryLogDao()
    private val sensorTrendDao = db.sensorTrendDao()
    private val faultHistoryDao = db.diagnosticFaultHistoryDao()

    val allServiceRecords: Flow<List<ServiceRecord>> = serviceDao.getAllServiceRecords()
    val allFuelRecords: Flow<List<FuelRecord>> = fuelDao.getAllFuelRecords()
    val allTelemetryLogs: Flow<List<com.example.data.model.TelemetryLog>> = telemetryDao.getRecentTelemetry()
    val totalServiceCost: Flow<Double?> = serviceDao.getTotalCost()
    val totalFuelCost: Flow<Double?> = fuelDao.getTotalFuelCost()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            seedInitialDataIfEmpty()
        }
    }

    private suspend fun seedInitialDataIfEmpty() {
        val currentRecords = serviceDao.getAllServiceRecords().first()
        if (currentRecords.isEmpty()) {
            serviceDao.insertRecord(
                ServiceRecord(
                    title = "Wymiana oleju i kompletu filtrów",
                    category = "Olej RN0720",
                    mileageKm = 178500,
                    dateStr = "2026-08-12",
                    costPln = 480.0,
                    partsUsed = "Elf Evolution Full-Tech FE 5W30 (4.5L), Purflux LS933, filtr kabiny węglowy Purflux AHC281",
                    invoiceNumber = "FV/2026/08/114",
                    notes = "Olej spuszczony gorący. Wymieniona uszczelka miedziana korka spustowego 16mm."
                )
            )
            serviceDao.insertRecord(
                ServiceRecord(
                    title = "Wymiana filtra paliwa z podgrzewaczem",
                    category = "Filtr paliwa",
                    mileageKm = 172000,
                    dateStr = "2026-04-05",
                    costPln = 230.0,
                    partsUsed = "Purflux FCS770 (czujnik wody przepięty ze starego filtra)",
                    invoiceNumber = "PAR/2026/04/89",
                    notes = "Układ zalany i odpowietrzony gruszką ręczną w komorze silnika do twardości."
                )
            )
            serviceDao.insertRecord(
                ServiceRecord(
                    title = "Kompletny rozrząd z pompą wody + pasek osprzętu",
                    category = "Rozrząd",
                    mileageKm = 150000,
                    dateStr = "2024-10-18",
                    costPln = 1250.0,
                    partsUsed = "Zestaw Gates KP15578XS, pompa cieczy Hepu, płyn Glaceol RX Type D",
                    invoiceNumber = "FV/WAW/9412",
                    notes = "Kolejna wymiana zalecana przy 270 000 km lub po 5 latach."
                )
            )
            serviceDao.insertRecord(
                ServiceRecord(
                    title = "Nowy akumulator AGM Start-Stop",
                    category = "Inne",
                    mileageKm = 165000,
                    dateStr = "2025-11-20",
                    costPln = 620.0,
                    partsUsed = "Varta Silver Dynamic AGM 70Ah 760A",
                    invoiceNumber = "FV/BAT/1102",
                    notes = "Wykonano procedurę adaptacji czujnika prądu akumulatora BMS (system Start-Stop)."
                )
            )
        }

        val currentFuels = fuelDao.getAllFuelRecords().first()
        if (currentFuels.isEmpty()) {
            fuelDao.insertFuel(
                FuelRecord(
                    dateStr = "2026-09-02",
                    mileageKm = 179420,
                    liters = 52.4,
                    costPln = 356.32,
                    station = "Orlen Verva Diesel"
                )
            )
            fuelDao.insertFuel(
                FuelRecord(
                    dateStr = "2026-08-18",
                    mileageKm = 178400,
                    liters = 54.1,
                    costPln = 367.88,
                    station = "Shell V-Power Diesel"
                )
            )
        }
    }

    suspend fun addServiceRecord(record: ServiceRecord) = serviceDao.insertRecord(record)
    suspend fun deleteServiceRecord(record: ServiceRecord) = serviceDao.deleteRecord(record)
    suspend fun addFuelRecord(record: FuelRecord) = fuelDao.insertFuel(record)
    suspend fun deleteFuelRecord(record: FuelRecord) = fuelDao.deleteFuel(record)

    suspend fun logTelemetry(pid: String, name: String, value: Double, unit: String, source: String) {
        telemetryDao.insertTelemetryLog(
            com.example.data.model.TelemetryLog(
                pid = pid,
                name = name,
                value = value,
                unit = unit,
                source = source
            )
        )
    }

    suspend fun clearTelemetryLogs() = telemetryDao.clearTelemetryLogs()

    // Sensor Trend Point Operations
    suspend fun logSensorTrend(
        sensorId: String,
        value: Float,
        unit: String,
        status: DataVerificationStatus = DataVerificationStatus.MEASURED,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        return sensorTrendDao.insertTrend(
            SensorTrendPoint(
                sensorId = sensorId,
                value = value,
                unit = unit,
                timestamp = timestamp,
                status = status
            ).toEntity()
        )
    }

    fun observeSensorTrend(sensorId: String, limit: Int = 100): Flow<List<SensorTrendPoint>> {
        return sensorTrendDao.getTrendsForSensor(sensorId, limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeRecentSensorTrends(limit: Int = 200): Flow<List<SensorTrendPoint>> {
        return sensorTrendDao.getRecentTrends(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun clearSensorTrends(sensorId: String? = null) {
        if (sensorId != null) {
            sensorTrendDao.clearTrendsForSensor(sensorId)
        } else {
            sensorTrendDao.clearAllTrends()
        }
    }

    // Diagnostic Fault History Operations
    suspend fun logDiagnosticFault(
        dtc: DtcCode,
        status: DataVerificationStatus = DataVerificationStatus.MEASURED,
        source: String = "ECU SID307"
    ): Long {
        return faultHistoryDao.insertFault(
            DiagnosticFaultHistoryEntry(
                dtcCode = dtc.code,
                title = dtc.title,
                system = dtc.system,
                severity = dtc.severity,
                timestamp = System.currentTimeMillis(),
                status = status,
                source = source
            ).toEntity()
        )
    }

    suspend fun logDiagnosticFault(entry: DiagnosticFaultHistoryEntry): Long {
        return faultHistoryDao.insertFault(entry.toEntity())
    }

    fun observeDiagnosticFaultHistory(): Flow<List<DiagnosticFaultHistoryEntry>> {
        return faultHistoryDao.getAllFaultHistory().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun clearDiagnosticFaultHistory() {
        faultHistoryDao.clearAllFaultHistory()
    }

    suspend fun deleteDiagnosticFault(id: Long) {
        faultHistoryDao.deleteFaultById(id)
    }

    // Baza Bezpieczników (UPC w komorze silnika + BSI w kabinie) dla Megane III 1.5 dCi
    fun getFuseCatalog(): List<FuseItem> = listOf(
        FuseItem(
            id = "F1",
            name = "Sterownik silnika ECU (SID307 / K9K)",
            location = "UPC - Skrzynka w komorze silnika",
            ratingAmps = 15,
            colorHex = 0xFF29B6F6, // Blue
            protectedCircuit = "Zasilanie główne komputera wtrysku Continental SID307, przekaźnik wtrysku",
            wireCode = "3FB1 / Zasilanie po zapłonie",
            oemNumber = "7701048885 (Mini fuse)",
            replacementGuide = "Standardowy bezpiecznik nożowy mini 15A (Niebieski). Dostępny na stacjach i w motoryzacyjnych.",
            failureSymptoms = "Silnik kręci ale nie odpala. Komunikat 'Skontroluj wtrysk' i brak komunikacji z ECU przez OBD."
        ),
        FuseItem(
            id = "F2",
            name = "Elektrozawór turbosprężarki & Grzałki paliwa",
            location = "UPC - Skrzynka w komorze silnika",
            ratingAmps = 20,
            colorHex = 0xFFFFEE58, // Yellow
            protectedCircuit = "Elektrozawór zmiennej geometrii turbo (N75), podgrzewacz filtra paliwa K9K",
            wireCode = "3S / Zawory podciśnienia",
            oemNumber = "7701048886 (Mini fuse 20A)",
            replacementGuide = "Bezpiecznik nożowy mini 20A (Żółty).",
            failureSymptoms = "Brak doładowania, auto w trybie awaryjnym (brak mocy powyżej 2000 obr.), zimą kłopot z parafiną w paliwie."
        ),
        FuseItem(
            id = "F3",
            name = "Sprzęgło kompresora klimatyzacji",
            location = "UPC - Skrzynka w komorze silnika",
            ratingAmps = 10,
            colorHex = 0xFFFF7043, // Red
            protectedCircuit = "Sprzęgło elektromagnetyczne sprężarki klimatyzacji Valeo",
            wireCode = "38J / Załączanie sprężarki",
            oemNumber = "7701048884 (Mini fuse 10A)",
            replacementGuide = "Bezpiecznik nożowy mini 10A (Czerwony).",
            failureSymptoms = "Klimatyzacja nie chłodzi, nawiew działa normalnie, obroty nie przysiadają przy włączaniu AC."
        ),
        FuseItem(
            id = "F4",
            name = "Pompa paliwa w zbiorniku / Czujnik ciśnienia",
            location = "UPC - Skrzynka w komorze silnika",
            ratingAmps = 15,
            colorHex = 0xFF29B6F6, // Blue
            protectedCircuit = "Obwód zasilania pompy wstępnej i czujnika obecności wody w filtrze",
            wireCode = "BP23 / Pompa niskiego ciśnienia",
            oemNumber = "7701048885 (Mini 15A)",
            replacementGuide = "Bezpiecznik mini 15A (Niebieski).",
            failureSymptoms = "Brak dopływu paliwa do pompy wysokiego ciśnienia, gaśnięcie silnika pod obciążeniem."
        ),
        FuseItem(
            id = "F5",
            name = "Światła mijania lewe (Ksenon / Halogen)",
            location = "UPC - Skrzynka w komorze silnika",
            ratingAmps = 10,
            colorHex = 0xFFFF7043, // Red
            protectedCircuit = "Lewy reflektor przedni (światło mijania H7)",
            wireCode = "CPG / Wiązka lewego reflektora",
            oemNumber = "7701048884 (Mini 10A)",
            replacementGuide = "Bezpiecznik 10A (Czerwony).",
            failureSymptoms = "Brak lewego światła mijania, komunikat 'Skontroluj oświetlenie'."
        ),
        FuseItem(
            id = "F6",
            name = "Światła mijania prawe",
            location = "UPC - Skrzynka w komorze silnika",
            ratingAmps = 10,
            colorHex = 0xFFFF7043, // Red
            protectedCircuit = "Prawy reflektor przedni (światło mijania H7)",
            wireCode = "CPD / Wiązka prawego reflektora",
            oemNumber = "7701048884 (Mini 10A)",
            replacementGuide = "Bezpiecznik 10A (Czerwony).",
            failureSymptoms = "Brak prawego światła mijania."
        ),
        FuseItem(
            id = "F7",
            name = "Wycieraczki przednie & Spryskiwacz",
            location = "UPC - Skrzynka w komorze silnika",
            ratingAmps = 25,
            colorHex = 0xFFFFFFFF, // Clear/White
            protectedCircuit = "Silnik wycieraczek z czujnikiem deszczu, pompka spryskiwacza szyb",
            wireCode = "14A / Wycieraczki przednie",
            oemNumber = "7701048887 (Mini 25A)",
            replacementGuide = "Bezpiecznik 25A biały/przezroczysty.",
            failureSymptoms = "Wycieraczki zamarły na szybie, brak reakcji na manetkę i czujnik deszczu."
        ),
        FuseItem(
            id = "MF1",
            name = "Świece żarowe K9K (Maxifuse)",
            location = "UPC - Skrzynka w komorze silnika",
            ratingAmps = 60,
            colorHex = 0xFFFFB300, // Maxi Yellow
            protectedCircuit = "Moduł sterownika świec żarowych K9K 636 Start-Stop",
            wireCode = "PR1 / Linia zasilania świec",
            oemNumber = "8200381281 (Maxi Fuse 60A)",
            replacementGuide = "Bezpiecznik typu MAXI 60A (Żółty gruby). Do wymiany niezbędne szczypce do bezpieczników.",
            failureSymptoms = "Bardzo ciężki rozruch w ujemnych temperaturach, błąd DF025 / P0380, dymienie po odpaleniu."
        ),
        FuseItem(
            id = "MF2",
            name = "Wentylator chłodnicy (Maxifuse)",
            location = "UPC - Skrzynka w komorze silnika",
            ratingAmps = 40,
            colorHex = 0xFFFF8A65, // Maxi Orange
            protectedCircuit = "Silnik wentylatora chłodnicy i skraplacza klimatyzacji (I i II bieg)",
            wireCode = "BP44 / Wentylator chłodnicy",
            oemNumber = "8200381280 (Maxi Fuse 40A)",
            replacementGuide = "Bezpiecznik MAXI 40A (Pomarańczowy).",
            failureSymptoms = "Przegrzewanie się silnika w korkach, wyłączanie klimatyzacji na postoju."
        ),
        FuseItem(
            id = "F11",
            name = "System Audio BOSE & Subwoofer",
            location = "BSI - Skrzynka w kabinie (pod kierownicą)",
            ratingAmps = 25,
            colorHex = 0xFFFFFFFF, // White
            protectedCircuit = "Wzmacniacz cyfrowy Bose pod fotelem, subwoofer Bose w podłodze bagażnika",
            wireCode = "SP7 / Zasilanie nagłośnienia Bose",
            oemNumber = "7701048887 (Mini 25A)",
            replacementGuide = "Bezpiecznik mini 25A biały w kabinie po lewej stronie pod kierownicą.",
            failureSymptoms = "Całkowity brak dźwięku z głośników mimo włączonego radia, brak basu z subwoofera."
        ),
        FuseItem(
            id = "F12",
            name = "Gniazdo diagnostyczne OBD-II & Wyświetlacz",
            location = "BSI - Skrzynka w kabinie (pod kierownicą)",
            ratingAmps = 10,
            colorHex = 0xFFFF7043, // Red
            protectedCircuit = "Gniazdo OBD-II (pin 16 zasilanie +12V stałe), panel klimatyzacji Climatronic",
            wireCode = "BP5 / Zasilanie diagnostyki",
            oemNumber = "7701048884 (Mini 10A)",
            replacementGuide = "Bezpiecznik mini 10A czerwony.",
            failureSymptoms = "Interfejs ELM327 nie świeci diodami i nie łączy się z telefonem, brak podświetlenia panelu AC."
        ),
        FuseItem(
            id = "F15",
            name = "Gniazdo zapalniczki 12V przód i tył",
            location = "BSI - Skrzynka w kabinie (pod kierownicą)",
            ratingAmps = 15,
            colorHex = 0xFF29B6F6, // Blue
            protectedCircuit = "Gniazdo 12V w konsoli środkowej oraz gniazdo w bagażniku kombi",
            wireCode = "SP15 / Akcesoria 12V",
            oemNumber = "7701048885 (Mini 15A)",
            replacementGuide = "Bezpiecznik mini 15A niebieski. Bardzo częsta usterka po zwarciu ładowarki.",
            failureSymptoms = "Brak ładowania telefonu, brak zasilania lodówki turystycznej w bagażniku."
        ),
        FuseItem(
            id = "F18",
            name = "Elektryczne szyby przednie i lusterka",
            location = "BSI - Skrzynka w kabinie (pod kierownicą)",
            ratingAmps = 30,
            colorHex = 0xFF66BB6A, // Green
            protectedCircuit = "Podnośniki szyb impulsowe, składanie lusterek, podgrzewanie lusterek",
            wireCode = "BPR / Moduł drzwi",
            oemNumber = "7701048888 (Mini 30A)",
            replacementGuide = "Bezpiecznik mini 30A zielony.",
            failureSymptoms = "Szyby nie reagują na przyciski w drzwiach kierowcy, lusterka nie składają się po zaryglowaniu."
        )
    )

    // Baza komponentów pod maską K9K 636 1.5 dCi
    fun getEngineComponents(): List<EngineComponent> = listOf(
        EngineComponent(
            id = "map_sensor",
            name = "MAP Sensor (Czujnik doładowania)",
            polishName = "Czujnik ciśnienia bezwzględnego w kolektorze",
            category = "Doładowanie",
            xRatio = 0.48f,
            yRatio = 0.28f,
            functionDescription = "Mierzy rzeczywiste ciśnienie powietrza w kolektorze dolotowym generowane przez turbosprężarkę Garrett.",
            failureSymptoms = "Brak mocy, szarpanie przy 1800-2400 obr., czarny dym z wydechu, błąd DF054 / P0235.",
            diagnosticsProcedure = "Sprawdzić ciśnienie na zgaszonym silniku (musi równać się atmosferycznemu ~1000 mbar). Podczas przyspieszania powinno wzrosnąć do ok. 2300-2450 mbar bezwzględnego.",
            nominalParameters = "Na biegu jałowym: 980-1020 mbar; Max boost: 2.35 - 2.45 bar bezwzględnego (1.35-1.45 bar nadciśnienia).",
            oemNumber = "8200682103 / 223650001R",
            aftermarketOptions = "Bosch 0 281 002 996, Pierburg 7.18222.01.0, Delphi PS10141"
        ),
        EngineComponent(
            id = "egr_valve",
            name = "Zawór EGR z chłodniczką",
            polishName = "Elektryczny zawór recyrkulacji spalin (Low & High Pressure)",
            category = "Układ Oczyszczania Spalin",
            xRatio = 0.72f,
            yRatio = 0.42f,
            functionDescription = "W silniku K9K 636 zastosowano zaawansowany układ EGR z niskim i wysokim ciśnieniem, redukujący tlenki azotu NOx.",
            failureSymptoms = "Nierówna praca na biegu jałowym, dławienie przy ruszaniu, komunikat 'Skontroluj układ wydechowy', błąd DF029 / P0409.",
            diagnosticsProcedure = "Sprawdzić pozycję zadaną vs zmierzoną w OBD. Jeśli różnica przekracza 5% w czasie przyspieszania, zawór zacina się od nagaru.",
            nominalParameters = "Otwarcie na jałowym: 15-35%; Przy pełnym bucie: 0% (zamknięty); Napięcie sygnału: 0.8V - 4.5V.",
            oemNumber = "147108197R / 147105543R",
            aftermarketOptions = "Pierburg 7.00075.07.0, Valeo 700444, Wahler 710940R"
        ),
        EngineComponent(
            id = "dpf_differential",
            name = "Czujnik różnicy ciśnień DPF",
            polishName = "Czujnik spadku ciśnienia na filtrze cząstek stałych",
            category = "Układ Oczyszczania Spalin",
            xRatio = 0.82f,
            yRatio = 0.65f,
            functionDescription = "Mierzy różnicę ciśnień spalin przed i za filtrem DPF za pomocą dwóch elastycznych przewodów silikonowych.",
            failureSymptoms = "Częste lub nieskuteczne próby wypalania DPF, przybywanie oleju silnikowego (rozcieńczenie ropą), błąd DF297 / P242F.",
            diagnosticsProcedure = "Sprawdzić czy wężyki gumowe nie są stopione lub popękane (częsty problem w Megane III!). Różnica ciśnień na jałowym nie może przekraczać 8 mbar.",
            nominalParameters = "Bieg jałowy: 0 - 8 mbar; 2500 RPM bez obciążenia: 15 - 35 mbar; 3000 RPM pod pełnym obciążeniem: < 120 mbar.",
            oemNumber = "227702184R / 8201027981",
            aftermarketOptions = "Bosch 0 281 006 252, Meat & Doria 82299, Hella 6PP 010 902-181"
        ),
        EngineComponent(
            id = "piezo_injectors",
            name = "Wtryskiwacze Continental / Siemens",
            polishName = "Wtryskiwacze piezoelektryczne Common Rail",
            category = "Wtrysk Common Rail",
            xRatio = 0.44f,
            yRatio = 0.52f,
            functionDescription = "Wtryskują paliwo pod ciśnieniem do 1800 barów w 5-6 dawkach na cykl. Każdy wtryskiwacz posiada 6-cyfrowy kod kalibracji IMA.",
            failureSymptoms = "Metaliczne stukanie na zimnym silniku, falowanie obrotów, dymienie, korekty wtrysku powyżej +1.5 lub poniżej -1.5 mg/skok.",
            diagnosticsProcedure = "Test przelewowy na menzurkach (max 20ml na 3 minuty pracy) oraz podgląd korekt na żywo w module OBD.",
            nominalParameters = "Korekty nominalne: -0.6 do +0.6 mg/hub; Ciśnienie na szynie wolne obroty: 260-290 bar; Max: 1650 bar.",
            oemNumber = "166006212R / 8200380253",
            aftermarketOptions = "Continental / VDO A2C59513484, Siemens 5WS40536"
        ),
        EngineComponent(
            id = "hp_fuel_pump",
            name = "Pompa wysokiego ciśnienia",
            polishName = "Pompa Common Rail z zaworem regulacji ciśnienia i wydatku",
            category = "Wtrysk Common Rail",
            xRatio = 0.30f,
            yRatio = 0.45f,
            functionDescription = "Napędzana paskiem rozrządu pompa tłoczkowa sprężająca olej napędowy do zasobnika paliwa.",
            failureSymptoms = "Długie kręcenie rozrusznikiem przed odpaleniem, gaszenie silnika przy nagłym depnięciu gazu (błąd ciśnienia na szynie DF053).",
            diagnosticsProcedure = "Sprawdzić ciśnienie przy rozruchu: rozrusznik musi nabić minimum 200 barów w ciągu 1-2 sekund aby ECU wyzwoliło wtrysk.",
            nominalParameters = "Ciśnienie rozruchowe: >200 bar; Wolne obroty: 280 bar; Pełne obciążenie: 1600 bar.",
            oemNumber = "167000061R / 8200704210",
            aftermarketOptions = "Continental A2C59513833, Bosch remanufactured"
        ),
        EngineComponent(
            id = "glow_plugs",
            name = "Świece żarowe Start-Stop",
            polishName = "Świece żarowe szybkiego grzania 4.4V",
            category = "Elektryka",
            xRatio = 0.58f,
            yRatio = 0.49f,
            functionDescription = "Zasilane napięciem impulsowym 4.4V (PWM) z modułu szybkiego dogrzewania. Ułatwiają rozruch i wspomagają dopalanie DPF.",
            failureSymptoms = "Trudny rozruch zimą, komunikat 'Skontroluj wtrysk' lub 'Sprawdź układ wydechowy', brak procedury dopalania DPF.",
            diagnosticsProcedure = "Zmierzyć oporność każdej świecy omomierzem po odpięciu fajki. Sprawna świeca ma oporność około 0.6 - 1.0 Ohm. Nigdy nie podłączać na krótko do 12V!",
            nominalParameters = "Rezystancja: 0.7 - 0.9 Ohm; Napięcie robocze: 4.4 Volt; Moment dokręcania: 15 Nm.",
            oemNumber = "110650819R / 8200682592",
            aftermarketOptions = "Beru GE110, NGK D-Power 73 (Y1035AS), Denso DG-609"
        ),
        EngineComponent(
            id = "turbocharger",
            name = "Turbosprężarka VNT Garrett",
            polishName = "Turbosprężarka o zmiennej geometrii łopatek z siłownikiem podciśnieniowym",
            category = "Doładowanie",
            xRatio = 0.50f,
            yRatio = 0.68f,
            functionDescription = "Doładowuje silnik K9K 636 zapewniając 240-260 Nm momentu już od 1750 obr./min.",
            failureSymptoms = "Gwizd/świst 'karetka pogotowia', zapieczona zmienna geometria (przeładowanie P0234), olej w intercoolerze.",
            diagnosticsProcedure = "Sprawdzić czy sztanga siłownika porusza się płynnie w zakresie ok. 10-12 mm po podaniu podciśnienia z pompki próżniowej.",
            nominalParameters = "Skok sztangi gruszki: 10-12 mm; Ciśnienie zasilania olejem: min. 2.0 bar przy 2000 obr.",
            oemNumber = "144114256R / 8200889697",
            aftermarketOptions = "Garrett 792290-5002S, BorgWarner 54399880070"
        ),
        EngineComponent(
            id = "fuel_filter_primer",
            name = "Filtr paliwa z gruszką ręczną",
            polishName = "Filtr paliwa Purflux z podgrzewaczem i pompką ręczną",
            category = "Wtrysk Common Rail",
            xRatio = 0.22f,
            yRatio = 0.32f,
            functionDescription = "Wychwytuje cząstki stałe do 2 mikronów i odwadnia paliwo. Gruszka umożliwia manualne odpowietrzenie po wymianie.",
            failureSymptoms = "Pęcherze powietrza w przezroczystych przewodach paliwowych, gaśnięcie po uruchomieniu, opiłki w paliwie.",
            diagnosticsProcedure = "Skontrolować przezroczyste przewody paliwowe biegnące do pompy wysokiego ciśnienia. Nie mogą w nich płynąć bąble powietrza.",
            nominalParameters = "Pojemność złoża filtracyjnego: filtr 2-mikronowy z separatorem wody; Wymiana co max 30 000 km.",
            oemNumber = "164009343R / 164005420R",
            aftermarketOptions = "Purflux FCS770, Mann WK 9012 z, Bosch 0 450 906 508"
        )
    )

    // Encyklopedia kodów DTC specyficznych dla Renault
    fun getDtcDatabase(): List<DtcCode> = listOf(
        DtcCode(
            code = "DF1012 / P1525",
            system = "Elektronika silnika / Tempomat",
            title = "Spójność informacji multipleksowych - Blokada tempomatu",
            severity = DtcSeverity.MEDIUM,
            symptoms = listOf(
                "Niedziałający tempomat i ogranicznik prędkości (wyświetla się komunikat 'Skontroluj tempomat')",
                "Kontrolka klucza serwisowego na desce rozdzielczej",
                "Silnik może jechać całkowicie normalnie bez utraty mocy"
            ),
            rootCauses = listOf(
                "Jest to błąd POCHODNY - Renault blokuje tempomat gdy W JAKIMKOLWIEK innym sterowniku (wtrysk, ABS, DPF) jest aktywny błąd",
                "Włącznik świateł stop przy pedale hamulca (uszkodzony jeden ze styków)",
                "Czujnik położenia pedału sprzęgła",
                "Chwilowy błąd ciśnienia doładowania lub zaworu EGR"
            ),
            diagnosticSteps = listOf(
                "Krok 1: Przeskanuj moduł silnika i ABS, aby znaleźć pierwotny błąd generujący ograniczenie.",
                "Krok 2: Sprawdź w parametrach rzeczywistych czy komputer widzi wciśnięcie pedału sprzęgła i hamulca.",
                "Krok 3: Po naprawieniu pierwotnej usterki skasuj kody DTC – błąd DF1012 zniknie automatycznie."
            ),
            urgencyScore = 4
        ),
        DtcCode(
            code = "DF297 / P242F",
            system = "Układ oczyszczania spalin DPF/FAP",
            title = "Filtr cząstek stałych zablokowany - Przekroczona masa sadzy",
            severity = DtcSeverity.CRITICAL,
            symptoms = listOf(
                "Komunikat 'Ryzyko awarii silnika' (czerwony STOP) lub 'Skontroluj wydech'",
                "Brak mocy, obroty ograniczone do 2800 RPM",
                "Podwyższony stan oleju na bagnecie (rozcieńczenie ropą po niedokończonych wypalaniach)"
            ),
            rootCauses = listOf(
                "Częsta jazda na krótkich odcinkach miejskich uniemożliwiająca osiągnięcie temp. 600°C",
                "Pęknięty silikonowy wężyk czujnika różnicy ciśnień DPF",
                "Zepsuty termostat (temperatura płynu poniżej 75°C blokuje regenerację!)",
                "Uszkodzone świece żarowe (ECU załącza je do obciążenia silnika podczas wypalania)"
            ),
            diagnosticSteps = listOf(
                "Krok 1: Odczytaj masę sadzy w gramach. Norma: <18g. Od 25g próba wypalania, powyżej 45g filtr zablokowany programowo.",
                "Krok 2: Sprawdź wężyki czujnika ciśnienia DPF pod kątem pęknięć i przypaleń.",
                "Krok 3: Sprawdź temperaturę cieczy chłodzącej - musi osiągnąć min. 80°C.",
                "Krok 4: Przeprowadź wymuszoną regenerację statyczną lub wyjedź na drogę ekspresową (2500 RPM przez 25 minut)."
            ),
            urgencyScore = 9
        ),
        DtcCode(
            code = "DF053 / P0089",
            system = "Wtrysk Common Rail K9K",
            title = "Funkcja regulacji ciśnienia w rampie wtryskowej",
            severity = DtcSeverity.CRITICAL,
            symptoms = listOf(
                "Silnik gaśnie przy gwałtownym wciśnięciu pedału gazu pod obciążeniem",
                "Czerwony komunikat 'Ryzyko awarii silnika' + dźwięk gongu",
                "Ciężki rozruch po postoju"
            ),
            rootCauses = listOf(
                "Zapchany filtr paliwa (zbyt mały wydatek przy wysokich obrotach)",
                "Zawór regulacji ciśnienia na pompie Common Rail (zacięty opiłkami lub nagarem)",
                "Nadmierny przelew jednego z wtryskiwaczy Continental (paliwo ucieka do powrotu)",
                "Powietrze w przewodach paliwowych"
            ),
            diagnosticSteps = listOf(
                "Krok 1: Sprawdź czy w przezroczystych przewodach paliwowych nie ma bąbli powietrza.",
                "Krok 2: Zrób próbę przelewową wtryskiwaczy Continental.",
                "Krok 3: Wymień filtr paliwa na oryginalny Purflux FCS770.",
                "Krok 4: Skontroluj zawartość filtra pod kątem obecności metalicznych opiłków (test magnesem)."
            ),
            urgencyScore = 10
        ),
        DtcCode(
            code = "DF025 / P0380",
            system = "Układ żarzenia",
            title = "Połączenie modułu diagnostycznego świec żarowych",
            severity = DtcSeverity.MEDIUM,
            symptoms = listOf(
                "Dłuższe kręcenie rozrusznika przy ujemnych temperaturach",
                "Biało-szary dym przez pierwsze 5 sekund po rozruchu",
                "Komunikat 'Skontroluj wtrysk'"
            ),
            rootCauses = listOf(
                "Spalona jedna lub więcej świec żarowych (najczęstsza usterka K9K)",
                "Uszkodzony bezpiecznik Maxi 60A w UPC",
                "Upalony styk w przekaźniku świec żarowych (z lewej strony za nadkolem)"
            ),
            diagnosticSteps = listOf(
                "Krok 1: Zmierz omomierzem rezystancję każdej świecy żarowej (powinno być ~0.8 Ohm).",
                "Krok 2: Sprawdź bezpiecznik MF1 (60A) w skrzynce w komorze silnika.",
                "Krok 3: Wymień spalone świece pamiętając o dokręceniu kluczem dynamometrycznym z momentem 15 Nm."
            ),
            urgencyScore = 5
        ),
        DtcCode(
            code = "DF054 / P0235",
            system = "Doładowanie Garrett",
            title = "Obwód czujnika ciśnienia doładowania (Sygnał poza zakresem)",
            severity = DtcSeverity.HIGH,
            symptoms = listOf(
                "Odczuwalny 'muł' - turbodziura, brak przyspieszenia powyżej 2200 RPM",
                "Komunikat 'Skontroluj wtrysk'",
                "Przejście sterownika w tryb awaryjny (limiter dawki paliwa)"
            ),
            rootCauses = listOf(
                "Pęknięta rura dolotowa intercoolera (charakterystyczny świst uciekającego powietrza)",
                "Zabrudzony olejem lub uszkodzony czujnik MAP na kolektorze dolotowym",
                "Nieszczelność przewodów podciśnienia gruszki turbiny",
                "Uszkodzony elektrozawór regulacji podciśnienia N75"
            ),
            diagnosticSteps = listOf(
                "Krok 1: Obejrzyj gumową rurę dolotu od intercoolera pod kątem pęknięć od spodu.",
                "Krok 2: Wyjmij czujnik MAP i umyj go preparatem do czyszczenia styków lub przepływomierzy.",
                "Krok 3: Sprawdź czy podciśnienie z pompy vacuum dochodzi do zaworu N75."
            ),
            urgencyScore = 7
        ),
        DtcCode(
            code = "DF029 / P0409",
            system = "Układ recyrkulacji spalin EGR",
            title = "Obwód czujnika położenia zaworu EGR",
            severity = DtcSeverity.HIGH,
            symptoms = listOf(
                "Szarpanie silnika w przedziale 1500-2000 obrotów podczas stałej prędkości",
                "Zwiększone dymienie na czarno",
                "Zwiększone zużycie paliwa o ok. 0.5 - 1.0 l/100km"
            ),
            rootCauses = listOf(
                "Silne zalepienie nagarem uniemożliwiające domknięcie zaworu EGR",
                "Wytarte ścieżki potencjometru w głowicy zaworu",
                "Zaśniedziałe styki 5-pinowej wtyczki zaworu EGR"
            ),
            diagnosticSteps = listOf(
                "Krok 1: Zdemontuj zawór EGR i wyczyść go chemicznie w nafcie lub zmywaczu do nagaru.",
                "Krok 2: Sprawdź płynność ruchu trzpienia zaworu po naciśnięciu palcem.",
                "Krok 3: Po montażu wykonaj procedurę przyuczenia położeń krańcowych EGR w menu diagnostycznym."
            ),
            urgencyScore = 6
        )
    )

    // Baza momentów dokręcania dla K9K 636 i skrzyni TL4
    fun getTorqueSpecs(): List<TorqueSpec> = listOf(
        TorqueSpec("T1", "Silnik K9K", "Korek spustowy oleju w misce", "20 Nm", "M16 x 1.5 (Kwadrat 8mm)", "Zawsze stosować nową uszczelkę miedzianą z wkładką elastomerową"),
        TorqueSpec("T2", "Silnik K9K", "Pokrywa filtra oleju", "25 Nm", "Nasadka 27mm", "Posmarować nową uszczelkę gumową świeżym olejem silnikowym"),
        TorqueSpec("T3", "Silnik K9K", "Świece żarowe (4 sztuki)", "15 Nm", "Gwint M10 x 1.0", "Dokręcać wyłącznie na zimnym silniku! Ryzyko zerwania gwintu w głowicy"),
        TorqueSpec("T4", "Silnik K9K", "Łapa dociskowa wtryskiwacza", "28 Nm", "Śruba Torx E10", "Wymienić miedzianą podkładkę termiczną pod wtryskiwaczem"),
        TorqueSpec("T5", "Silnik K9K", "Koło pasowe wału korbowego", "50 Nm + 115°", "Śruba M12 x 1.5", "Zawsze nowa śruba wału! Wał bez klina - montaż wyłącznie na blokadach"),
        TorqueSpec("T6", "Silnik K9K", "Koło zębate wałka rozrządu", "30 Nm + 85°", "Śruba centralna M10", "Wymagana blokada koła rozrządu trzpieniem 8mm"),
        TorqueSpec("T7", "Silnik K9K", "Czujnik ciśnienia na listwie Common Rail", "35 Nm", "Klucz płaski 27mm", "Nie dotykać końcówki pomiarowej"),
        TorqueSpec("T8", "Skrzynia TL4", "Korek wlewu oleju przekładniowego", "3.0 Nm (dokręcić ręcznie)", "Korek plastikowy motylkowy", "Pojemność skrzyni TL4: 1.9 Litra oleju 75W-80 (NFJ / NFX)"),
        TorqueSpec("T9", "Skrzynia TL4", "Korek spustowy skrzyni biegów", "22 Nm", "Kwadrat 8mm", "Stosować nową podkładkę miedzianą 16mm"),
        TorqueSpec("T10", "Hamulce", "Jarzmo zacisku hamulcowego przód", "105 Nm", "Śruby M12", "Zastosować kroplę kleju do gwintów średniej mocy (niebieski Loctite)"),
        TorqueSpec("T11", "Hamulce", "Prowadnice zacisku przód", "30 Nm", "Klucz imbusowy 7mm", "Oczyścić i nasmarować smarem silikonowym do prowadnic"),
        TorqueSpec("T12", "Podwozie", "Śruby kół aluminiowych Bose 17\"", "110 Nm", "Śruby M12 x 1.5 stożek", "Dokręcać na krzyż kluczem dynamometrycznym. Nie używać klucza pneumatycznego!")
    )

    // Baza procedur naprawczych DIY
    fun getRepairGuides(): List<RepairGuide> = listOf(
        RepairGuide(
            id = "guide_oil_change",
            title = "Wymiana oleju silnikowego (RN0720 5W30) i filtra",
            category = "Serwis olejowy",
            difficulty = "Łatwy",
            timeRequired = "45 minut",
            toolsNeeded = listOf("Klucz do korka miski - kwadrat 8mm", "Klucz do filtra oleju - nasadka 27mm", "Klucz Torx T20 i nasadka 10mm (osłona silnika)", "Miska zlewowa min. 6 litrów"),
            partsNeeded = listOf("Olej silnikowy Elf Evolution Full-Tech FE 5W30 RN0720 (4.5 L)", "Filtr oleju Purflux LS933", "Miedziana uszczelka korka spustowego 16mm z gumką"),
            torqueSpecs = listOf("Korek spustowy: 20 Nm", "Obudowa filtra oleju: 25 Nm"),
            steps = listOf(
                "Rozgrzej silnik do temperatury roboczej (ok. 80°C) dla lepszej płynności oleju.",
                "Wjedź na najazdy lub podnieś przód pojazdu i odkręć dolną osłonę silnika (śruby 10mm).",
                "Odkręć korek wlewu oleju na pokrywie zaworów i wyciągnij bagnet pomiarowy.",
                "Podstaw miskę pod korek spustowy i odkręć go kluczem kwadratowym 8mm. Uwaga na gorący olej!",
                "Odkręć obudowę filtra oleju kluczem 27mm. Wymień wkład i nową gumową uszczelkę (zwilż ją świeżym olejem).",
                "Po całkowitym spłynięciu załóż nową uszczelkę na korek spustowy i dokręć go momentem 20 Nm.",
                "Zamontuj filtr oleju dokręcając z momentem 25 Nm.",
                "Wlej dokładnie 4.2 litra świeżego oleju Elf RN0720, odczekaj 5 minut i sprawdź stan na bagnecie.",
                "Uruchom silnik na 1 minutę, zgaś, sprawdź ewentualne wycieki i uzupełnij stan do poziomu 3/4 na skali bagnetu.",
                "Wykonaj reset inspekcji olejowej z manetki przyciskami góra/dół (przytrzymaj 10 sek na komunikacie 'Wymień olej')."
            ),
            proTips = "W silniku K9K 636 z DPF stosuj WYŁĄCZNIE oleje z oficjalną normą Renault RN0720 (Low SAPS C4). Stosowanie zwykłego oleju natychmiast zapycha filtr cząstek stałych i skraca żywotność panewek."
        ),
        RepairGuide(
            id = "guide_fuel_filter",
            title = "Wymiana filtra paliwa z odpowietrzeniem gruszką",
            category = "Układ paliwowy",
            difficulty = "Średni",
            timeRequired = "40 minut",
            toolsNeeded = listOf("Nasadka 10mm z przedłużką", "Płaski śrubokręt do zwalniania szybkozłączek", "Czyste szmatki niepylące"),
            partsNeeded = listOf("Wkład filtra paliwa Purflux FCS770 / Delphi HDF958", "Nowe oringi uszczelniające szybkozłączki"),
            torqueSpecs = listOf("Śruba obejmy mocującej filtr: 8 Nm"),
            steps = listOf(
                "Wyłącz zapłon i odczekaj 10 minut na spadek ciśnienia w układzie niskiego ciśnienia.",
                "Filtr paliwa w Megane III znajduje się w prawej części komory silnika (w okolicach prawego kielicha amortyzatora).",
                "Dokładnie oczyść okolicę szybkozłączek z piasku i brudu (opiłki i brud są śmiertelne dla wtryskiwaczy Continental).",
                "Wciśnij kolorowe przyciski zabezpieczające na szybkozłączkach i ostrożnie zsuń przewody (zabezpiecz wypływające paliwo szmatką).",
                "Odepnij wtyczkę podgrzewacza i czujnika obecności wody w dnie filtra.",
                "Poluzuj śrubę mocującą 10mm i wyjmij stary filtr ku górze.",
                "Przełóż podgrzewacz i czujnik wody do nowego filtra stosując nowe oringi z zestawu.",
                "Zamontuj nowy filtr w uchwycie i podłącz wszystkie przewody (usłyszysz charakterystyczny 'klik' blokady).",
                "ODPOWIETRZENIE: Pompuj gumową gruszką znajdującą się obok filtra aż stanie się całkowicie twarda (ok. 30-50 naciśnięć).",
                "Sprawdź przezroczysty przewód czy nie ma w nim dużych bąbli powietrza, po czym uruchom silnik nie dotykając pedału gazu."
            ),
            proTips = "Nigdy nie kręć rozrusznikiem na 'suchym' filtrze! Pompa wysokiego ciśnienia smarowana jest wyłącznie olejem napędowym – praca na sucho powoduje łuszczenie metalu i zniszczenie wtryskiwaczy."
        ),
        RepairGuide(
            id = "guide_cabin_filter",
            title = "Wymiana filtra kabinowego (Węglowy)",
            category = "Wnętrze i komfort",
            difficulty = "Średni (trudny dostęp)",
            timeRequired = "35 minut",
            toolsNeeded = listOf("Klucz Torx T20", "Płaski ściągacz do spinek plastikowych", "Latarka czołowa"),
            partsNeeded = listOf("Filtr kabinowy węglowy Purflux AHC281 / Mann CUK 26 003"),
            torqueSpecs = listOf("Wkręty schowka: 2.5 Nm (dokręcać z wyczuciem w plastik)"),
            steps = listOf(
                "W Megane III filtr kabinowy znajduje się po prawej stronie konsoli środkowej za schowkiem pasażera.",
                "Otwórz schowek, wyjmij boczną zaślepkę deski z wyłącznikiem poduszki pasażera.",
                "Odkręć 5 wkrętów Torx T20 mocujących schowek (3 u góry pod krawędzią, 1 z boku, 1 na dole).",
                "Odepnij lampkę oświetlenia schowka i wyjmij całą komorę schowka pasażera.",
                "Zdejmij kanał nawiewu na nogi pasażera.",
                "Zlokalizuj pionową klapkę zespołu nagrzewnicy i odepnij dwa plastikowe zatrzaski.",
                "Zegnij stary filtr w harmonijkę i wyciągnij go na zewnątrz.",
                "Wsuń nowy filtr węglowy zwracając uwagę na strzałkę kierunku przepływu powietrza (Air Flow w stronę kabiny).",
                "Załóż klapkę i zmontuj schowek w odwrotnej kolejności."
            ),
            proTips = "Zaleca się montaż filtra węglowego z antyalergenem – w wersji Bose Edition z automatyczną klimatyzacją znacznie poprawia to jakość powietrza i zapobiega parowaniu szyb."
        )
    )
}
