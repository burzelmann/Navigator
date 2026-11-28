# 🗺️ Nagivator - Modern Android Navigation & Outdoor App

![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-SDK%2037-3DDC84?logo=android&logoColor=white)
![Android Auto](https://img.shields.io/badge/Android%20Auto-Car%20App%20Library-3DDC84?logo=androidauto&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![OSM](https://img.shields.io/badge/OpenStreetMap-MapLibre%20Native-396CB2)
![Room](https://img.shields.io/badge/Database-Room%202.8.5-4285F4?logo=sqlite&logoColor=white)
![License](https://img.shields.io/badge/License-ODbL--1.0-blue)

**Nagivator** ist eine moderne, schlanke Android-Navigations-App für Fußgänger, Wanderer und Autofahrer. Die App kombiniert OpenStreetMap-Kartenmaterial, spezialisiertes Outdoor-Trailrouting (BRouter) und Stadt-Routing (OSRM) mit vollständigen Offline-Fähigkeiten und wissenschaftlich fundierten Geschwindigkeits-Präferenzen.

---

## 📋 Inhaltsverzeichnis
- [✨ Features](#-features)
- [📱 UI Preview & Navigationsansicht](#-ui-preview--navigationsansicht)
- [🛠️ Tech Stack & Architektur](#️-tech-stack--architektur)
- [📂 Projektstruktur](#-projektstruktur)
- [🚀 Installation & Build](#-installation--build)
- [🤝 Mitwirken & Feedback](#-mitwirken--feedback)
- [📄 Lizenz & Danksagung](#-lizenz--danksagung)

---

## ✨ Features

- 🗺️ **Interaktive GPU-Vektor-Karte (MapLibre Native + OpenFreeMap):**
  - Kostenlose, schlüssel- und trackingfreie OpenStreetMap-Vektordaten (`tiles.openfreemap.org`, OpenFreeMap Liberty & Dark) gerendert mit 60–120 FPS direkt auf der GPU (`org.maplibre.gl:android-sdk`).
  - **Gestochen scharfe Schriften & 4 echte Vektor-Nachtschemata:** *Graphit*, *Mitternachtsblau*, *Warm-Bernstein* und *Hochkontrast* mit sofortigem GPU-Restyling ohne Kachel-Flackern und aufrecht bleibenden Straßennamen bei Kartendrehung.
  - Native Standortanzeige mit **Blickrichtungskegel** (`Sensor.TYPE_ROTATION_VECTOR`) und Zwei-Finger-Kartendrehung mit **Kompass-FAB** zum Ein-Klick-Einnorden.

- 🥾 **Spezialisiertes Outdoor- & Fußgänger-Routing:**
  - **BRouter Outdoor-Hiking Engine (`brouter.de`):** Führt Routen zielsicher über schmale Pfade, Trampelpfade, Feldwege, Waldwege, Treppen und Schotterwege.
  - **Multi-Wegpunkt Routenplaner:** Flexibles Hinzufügen, Löschen und Umordnen von Zwischenstopps.
  - **Geodätisches Notfall-Fallback:** Verhindert fehlgeschlagene Routen im Funkloch oder unwegsamen Gelände.

- 🎙️ **Dedizierte Vollbild-Turn-by-Turn Navigation:**
  - Prominentes Abbiege-Banner mit riesigen Manöver-Icons, Entfernungsangabe und zweizeiliger Vorschau auf den übernächsten Schritt.
  - **Alternativrouten (bis zu 3 Strecken):** Vor Abfahrt auf der Karte direkt anklickbar oder per Auswahl-Chips in der Routen-Karte umschaltbar.
  - Ankunfts-Cockpit mit Live-Uhrzeit (ETA), Restdauer, Reststrecke, **Live-ETA teilen** (über das Android Share Sheet) und Schnellausstieg.
  - Kamera-Folgemodus mit enger Zoomstufe (17.5) und Umschaltung auf Gesamtrouten-Übersicht.
  - **Aktivierter Bildschirm-Timeout-Schutz (`FLAG_KEEP_SCREEN_ON`)** während aktiver Navigation.

- 📊 **Höhenmeter & Höhenprofil:**
  - Exakte Auswertung von Gesamtaufstieg und Gesamtabstieg (`↗ 120m  ↘ 85m`).
  - Interaktives, kompaktes Höhenprofil-Diagramm im Routenplaner.

- 📥 **Umfassendes Offline-Center (`OfflineManagerScreen`) & Globaler Offline-Modus:**
  - **Online-/Offline-Schalter (Profil & Android Auto Optionen):** Schaltet die gesamte App auf Wunsch komplett offline (`MapLibre.setConnected(false)`, keine Online-Suche, kein Online-Routing, kein Verkehrs-/OSM-Polling) – es werden ausschließlich lokal heruntergeladene Karten, Suchindizes und BRouter-Routing-Kacheln genutzt.
  - **Zentrales Vollbild-Offline-Center:** Erreichbar direkt über die Karte **und** über den **Profil-Tab**, mit gemeinsamer Speicher-Übersicht (*Karten + Routing + Suchindex + Cache*) und zwei klaren Tabs (*Regionen* & *Routing*).
  - **Regionen-Download & gebündelter Status:** Gezielter Kachel-Download für sichtbare Ausschnitte inkl. Datengrößen-Schätzung, 10s-ETA, automatischer Regionen-Benennung und 3 Live-Status-Badges pro Region (*Karte bereit*, *Offline-Ziele im Suchindex*, *Offline-Routing bereit/fehlt*).
  - **Lokaler Offline-Suchindex (`OfflineSearchIndexRepository`):** Lädt beim Regionen-Download (oder 1-Tap-Nachindizierung) automatisch Orte, benannte Straßen und wichtige POIs in Room/SQLite — blitzschnelle Offline-Suche auf Smartphone & Android Auto.
  - **Echtes On-Device Offline-Routing (`OfflineRoutingRepository` + BRouter-Katalog):** Straßengenaue Routenberechnung und Off-Route-Neuberechnung (Auto & Fuß) direkt auf dem Gerät über 5°×5°-Kacheln (`.rd5`). Inklusive Klartext-Katalog (*Deutschland & Nachbarn*, *Beliebte Urlaubsregionen* + automatische weltweite Erkennung), Live-Download-Fortschritt mit Abbrechen-Option und Mobilfunk-Warnhinweis.

- ❤️ **Favoriten, Fahrtenverlauf & Parkplatz-Merker:**
  - 100% offline gespeicherte Lieblingsorte, Favoriten-Routen und **Fahrtenverlauf (`TripHistoryEntity`)** in der lokalen Room-Datenbank (mit **Nochmal starten** & **Rückweg starten**).
  - **On-Device Ziel-Vorschläge (`RoutePredictionUseCase`):** Lernt wiederkehrende Ziele nach Startort, Tageszeitfenster und Wochentag komplett lokal ohne Cloud oder Tracking.
  - **Persönliches Strecken-Lernen (Opt-in, nur Auto):** Wenn im Profil freigegeben, merkt sich Nagivator rein lokal in der Room-Datenbank, wenn auf einer konkreten Start→Ziel-Autostrecke mindestens **3×** bewusst anders gefahren wird, und schlägt diese Route künftig als **„⭐ Bevorzugte Route“** neben der Standard-OSRM-Route vor (einzeln oder komplett löschbar).
  - **Parkplatz automatisch merken:** Beim Beenden einer Autofahrt wird die letzte GPS-Position automatisch als blauer `P`-Pin auf der Karte gespeichert – mit Ein-Klick-Fußweg-Navigation **„Zum Auto zurück (Fußweg)“**.
  - Schnellsuchleiste im Favoriten-Tab mit Echtzeit-Filterung.

- 👤 **Wissenschaftliches Nutzer-Profil:**
  - Konfigurierbare Gehgeschwindigkeiten (Langsam 3.3 km/h, Normal 4.5 km/h, Schnell 5.8 km/h) basierend auf biomechanischen Gangbild-Studien (*Bohannon 1997 / MUTCD*).
  - Anpassbare Auto-Fahrgeschwindigkeiten.
  - Dreifach-Toggle für Erscheinungsbild (*System*, *Immer hell*, *Immer dunkel*).

- 🚗 **Android Auto Integration:**
  - **GPU-Vektor-Kartenoberfläche (`CarMapSurfaceRenderer` mit MapLibre Native):** Rendert OpenFreeMap-Vektorkarten per `VirtualDisplay` + `Presentation` (`TextureView`-Modus) direkt auf das Fahrzeugdisplay – gestochen scharfe Schriften, automatischer Tag-/Nachtwechsel, 4 echte Vektor-Nachtschemata (*Graphit*, *Mitternachtsblau*, *Warm-Bernstein*, *Hochkontrast*), aufrecht bleibende Straßennamen im Heading-Up-Modus, dynamischer Auto-Zoom sowie persistente Ausrichtung (*Norden oben* / *Fahrtrichtung oben*).
  - **Schnelleinstellungen (`Optionen`):** Übersichtliche In-Car-Steuerung mit nativen Android-Auto-Schaltern (`Toggle`) für Sprachausgabe, Fahrtrichtung oben, Auto-Zoom und Kreuzungs-Symbole, nativen Radio-Button-Auswahllisten (`CarOptionPickerScreen`) für Erscheinungsbild (*System* / *Hell* / *Dunkel*), Nacht-Farbschema, Fahrtempo und **Offline-Stimme** sowie 1-Tap-`Testton` zum Einstellen der Navigationslautstärke.
  - **Hochwertige Offline-Stimmen & Sprachpakete:** Automatische Wahl der qualitativ besten lokal installierten deutschen Offline-Stimme (`TextToSpeech.getVoices()`), freie Stimmenauswahl mit direktem Probehören, wählbares Sprechtempo (*Ruhig* / *Normal* / *Zügig*) und 1-Tap-Zugang zu den Android-Sprachpaketen (`ACTION_INSTALL_TTS_DATA`) im Profil.
  - **Visueller Sprachausgabe-Indikator (`🔊 Ansage`):** Während einer laufenden Turn-by-Turn-Ansage zeigt die Karte ein kontrastreiches `🔊 Ansage`-Badge im HUD (bzw. einen hervorgehobenen Lautsprecher-Button auf dem Smartphone), damit die Medien-/Navigationslautstärke im Fahrzeug gezielt geregelt werden kann.
  - **Suche, Favoriten & „Ziele“-Schnellmenü:** Direkte Zielsuche über die Auto-Tastatur, Ein-Klick-Routenstart aus Favoriten sowie das neue **`Ziele`-Menü (`CarDestinationsScreen`)** mit lokalen Ziel-Vorschlägen für die aktuelle Tageszeit, den letzten Fahrten und einem **Rückweg-Umschalter**.
  - **Routenvorschau & Alternativrouten (`CarRoutePreviewScreen`):** Bei mehreren verfügbaren Strecken öffnet sich die native Android-Auto-Routenvorschau (`RoutePreviewNavigationTemplate`) zum Vergleichen und Auswählen von bis zu 3 Alternativrouten direkt auf der Karte vor Fahrtantritt.
  - **Automatisches Parkplatz-Merken:** Beim Beenden der Android-Auto-Navigation wird die Fahrzeugposition lokal gespeichert und in der Smartphone-App als `P`-Marker samt Fußweg-Rückführung angezeigt.
  - **Turn-by-Turn-Routenführung & Fahrspurassistent:** Wissenschaftlich fundierte 3-Stufen-Ansagekaskade (`TurnGuidanceEngine`) mit geschwindigkeitsabhängigem *Time-to-Maneuver*-Timing (Stadt/Landstraße/Autobahn), Kreisverkehr-Ausfahrtsnummern, Wegweisern (`destinations`/`exits`), nativem **Android-Auto-Fahrspurassistenten (`Step.addLane` + `setLanesImage`)** sowie Smartphone-Fahrspurleiste, Doppel-Manöver-Verkettung (*„… und dann sofort links“*), OSM-Ampel-Landmarken (*„an der Ampel rechts abbiegen“*) und deutscher TTS-Sprachhygiene.
  - **Stabile Kurs- & Off-Route-Erkennung:** Segment-Projektion mit 3-fach-Hysterese & Cooldown gegen Fehl-Neuberechnungen sowie Stand-/Ampel-Bearing-Stabilisierung (`< 7 km/h` oder ungenaues GPS-Bearing `> 45°`) im Auto-Modus.
  - **Tacho, Tempolimit & Kreuzungs-Marker:** Kompaktes Geschwindigkeits- & Tempolimit-HUD (mit Warnfarbe bei Überschreitung) sowie kontrastreiche Karten-Marker für Ampeln, Stoppschilder, Vorfahrt-achten-Schilder und Kreisverkehre über die zugangsfreie OpenStreetMap 0.6 Map API (lokal gecacht).
  - **Schlüsselfreie Open-Data-Verkehrsdaten (DE & NL, ohne Login/API-Key):** Kombiniert automatisch **Autobahn GmbH (`verkehr.autobahn.de`)**, **Verkehr.NRW / Mobilithek WFS (`verkehr.nrw/geoserver/vipnrw/wfs`)** (inkl. Echtzeit-Staus mit Verlustzeit, Sperrungen & Baustellen auf Bundes-, Landes- und Stadtstraßen) sowie **NDW Niederlande (`opendata.ndw.nu/actueel_beeld.xml.gz`, DATEX II v3)** mit räumlicher Deduplizierung, Sprach-/Toast-Warnung und automatischer Ausweichroute bei Vollsperrungen.

---

## 📱 UI Preview & Navigationsansicht

```
===============================================================
|                                                             |
|  ┌───────────────────────────────────────────────────────┐  |
|  │  [ ⬅️ ]  IN 80 METERN                                 │  |
|  │         Links abbiegen auf Buchenweg                  │  |
|  │  ───────────────────────────────────────────────────  │  |
|  │  Danach: [ ⬆️ ] 300m geradeaus                        │  |
|  └───────────────────────────────────────────────────────┘  |
|                                                             |
|                       ▲ (Eigene Live-Position)              |
|                      / \                                    |
|                     /   \                                   |
|                    /     \   (Blaue Routenführung)          |
|                   /   │   \                                 |
|                      │                                      |
|                                                    [ 🧭 ]   |
|                                                    [ 🔊 ]   |
|                                                    [ 🗺️ ]   |
|                                                             |
|  ┌───────────────────────────────────────────────────────┐  |
|  │  14:28 Uhr      12 Min      1,2 km     [ ✖ BEENDEN ]  │  |
|  │  Ankunft        Restzeit    Distanz                   │  |
|  └───────────────────────────────────────────────────────┘  |
|                                                             |
===============================================================
```

---

## 🛠️ Tech Stack & Architektur

Nagivator folgt den modernen Android Development Best Practices und der empfohlenen **Clean Architecture (MVVM)**:

| Komponente | Technologie / Bibliothek |
| :--- | :--- |
| **Sprache** | Kotlin 2.0.21 (Coroutines, Flow, StateFlow) |
| **UI Framework** | Jetpack Compose, Material 3, Material Icons Extended |
| **Android Auto** | Jetpack Car App Library (`androidx.car.app:app`) |
| **Navigation** | NavigationSuiteScaffold (Material 3 Adaptive) |
| **Karten-Engine** | `osmdroid` 6.1.20 |
| **Outdoor-Routing** | BRouter Web API (`brouter.de/brouter`, Hiking Profile) |
| **Stadt-Routing** | OSRM API (`router.project-osrm.org`) |
| **Geocoding** | Photon API (`photon.komoot.io`) |
| **Datenbank** | Room 2.8.5 (SQLite with `Flow` reactive streams) |
| **Netzwerk & Serialization** | Retrofit 2, OkHttp 4, Kotlinx Serialization |

---

## 📂 Projektstruktur

```
com.example.nagivator
├── car
│   ├── screens          # Android Auto Screens (CarHome, CarSearch, CarFavorites, CarQuickSettings, CarNavigation)
│   ├── surface          # CarMapSurfaceRenderer (2D-Karte, Heading-Up, 30-FPS-Interpolation)
│   └── ...              # CarManeuverConverter, CarNavigationHelper, CarVoiceGuidance
├── data
│   ├── local
│   │   ├── dao          # Room DAOs (PoiDao, SavedRouteDao, OfflineRegionDao)
│   │   └── entity       # Room Entities (PoiEntity, SavedRouteEntity, OfflineRegionEntity)
│   ├── remote           # Retrofit APIs (BRouter, OSRM, Photon Geocoding)
│   └── repository       # Repositories (PoiRepository, RoutingRepository, LocationRepository, UserSettingsRepository)
├── debug                # Debug-only Fahrtsimulator (DebugDriveSimulator, DebugDriveReceiver)
├── domain
│   ├── model            # Domain Modelle (LocationPoint, RouteResult, SpeedPreference, TravelMode)
│   └── NavigationStateHolder.kt  # Geteilter Live-Navigationszustand (App <-> Android Auto)
└── ui
    ├── components       # Wiederverwendbare UI-Komponenten (ElevationProfileView, IconPicker)
    ├── favorites        # Favoriten-Tab (FavoritesScreen, FavoritesViewModel)
    ├── map              # Hauptkarte & Navigation (MapScreen, MapViewModel, OsmMapView, MapStyleHelper, NavigationView, RoutePlannerCard)
    ├── profile          # Profil- & Einstellungen-Tab (ProfileScreen, ProfileViewModel)
    └── theme            # Material 3 Nagivator Theme

scripts/
├── publish_internal.sh  # Automatisierter Play-Store-Upload (Internal Track, Pflicht-Release-Notes)
└── start_dhu.sh         # Startet Google Desktop Head Unit (DHU) via ADB Port-Forwarding
```

---

## 🚀 Installation & Build

### Voraussetzungen
- **Android Studio** 2024.2+ (Ladybug / Koala / 2026+)
- **JDK:** Java 17+ (bzw. Android Studio JBR)
- **Android SDK:** Compile/Target/Min SDK 37
- **Gerät:** Physisches Android-Gerät oder Emulator (Google Play Services empfohlen)

### Schritte zum Ausführen
1. Repository klonen:
   ```bash
   git clone https://github.com/burzelmann/Navigator.git
   cd Navigator
   ```
2. Projekt in Android Studio öffnen.
3. Unit-Tests & Debug-APK bauen (`com.verhuelsdonk.navigator.debug`, läuft parallel zur Play-Store-App):
   ```bash
   ./gradlew test assembleDebug
   ```
4. Auf einem verbundenen Android-Gerät installieren:
   ```bash
   ./gradlew installDebug
   ```

### Android Auto Emulator (DHU) & Fahrtsimulation
1. Auf dem Smartphone in den Android-Auto-Entwicklereinstellungen **Head-Unit-Server starten** aktivieren.
2. DHU starten:
   ```bash
   ./scripts/start_dhu.sh
   ```
3. Optional im `debug`-Build eine Fahrt oder Off-Route-Situation per ADB simulieren:
   ```bash
   adb shell am broadcast -a com.verhuelsdonk.navigator.DEBUG_DRIVE_START -n com.verhuelsdonk.navigator.debug/com.example.nagivator.car.DebugDriveReceiver
   adb shell am broadcast -a com.verhuelsdonk.navigator.DEBUG_DRIVE_OFF_ROUTE -n com.verhuelsdonk.navigator.debug/com.example.nagivator.car.DebugDriveReceiver
   adb shell am broadcast -a com.verhuelsdonk.navigator.DEBUG_DRIVE_STOP -n com.verhuelsdonk.navigator.debug/com.example.nagivator.car.DebugDriveReceiver
   ```

### Automatisiertes Play-Store-Release (Internal Track)
Erhöht `versionCode`, speichert Release-Notes, führt `./gradlew test` aus und lädt das signierte App Bundle via Gradle Play Publisher hoch (benötigt lokale `keystore.properties` und `play-service-account.json`):
```bash
./scripts/publish_internal.sh "Aussagekräftige Beschreibung der Änderungen..."
```

---

## 🤝 Mitwirken & Feedback

Contributions, Issue-Meldungen und Feature-Wünsche sind herzlich willkommen! 
- Öffnen Sie gerne ein [Issue](https://github.com/burzelmann/Navigator/issues) für Fragen oder Vorschläge.
- Erstellen Sie einen Pull Request für Korrekturen oder Erweiterungen.

---

## 📄 Lizenz & Danksagung

- **Kartenmaterial:** © [OpenStreetMap-Mitwirkende](https://www.openstreetmap.org/copyright) (ODbL).
- **Routing-Dienste:** [BRouter](https://brouter.de/) & [OSRM](http://project-osrm.org/).
- **Geocoding-Dienst:** [Photon Komoot](https://photon.komoot.io/).
