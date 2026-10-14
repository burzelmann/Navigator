# 🗺️ Nagivator - Modern Android Navigation & Outdoor App

![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-SDK%2035-3DDC84?logo=android&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![OSM](https://img.shields.io/badge/OpenStreetMap-osmdroid-7E9680?logo=openstreetmap&logoColor=white)
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

- 🗺️ **Interaktive OpenStreetMap-Karte:**
  - Kostenloses, schlüssel- und lizenzfreies Kartenmaterial über `osmdroid` (`tile.openstreetmap.de`).
  - Native Canvas-Standortanimation mit pulsierendem Punkt und **Blickrichtungskegel** (`Sensor.TYPE_ROTATION_VECTOR`).
  - Zwei-Finger-Kartendrehung mit **Kompass-FAB** zum Ein-Klick-Einnorden gen Norden.
  - **Dunkler Nachtmodus:** Automatisches/manuelles Graphit-Kardendesign bei Nacht.

- 🥾 **Spezialisiertes Outdoor- & Fußgänger-Routing:**
  - **BRouter Outdoor-Hiking Engine (`brouter.de`):** Führt Routen zielsicher über schmale Pfade, Trampelpfade, Feldwege, Waldwege, Treppen und Schotterwege.
  - **Multi-Wegpunkt Routenplaner:** Flexibles Hinzufügen, Löschen und Umordnen von Zwischenstopps.
  - **Geodätisches Notfall-Fallback:** Verhindert fehlgeschlagene Routen im Funkloch oder unwegsamen Gelände.

- 🎙️ **Dedizierte Vollbild-Turn-by-Turn Navigation:**
  - Prominentes Abbiege-Banner mit riesigen Manöver-Icons, Entfernungsangabe und zweizeiliger Vorschau auf den übernächsten Schritt.
  - Ankunfts-Cockpit mit Live-Uhrzeit (ETA), Restdauer, Reststrecke und Schnellausstieg.
  - Kamera-Folgemodus mit enger Zoomstufe (17.5) und Umschaltung auf Gesamtrouten-Übersicht.
  - **Aktivierter Bildschirm-Timeout-Schutz (`FLAG_KEEP_SCREEN_ON`)** während aktiver Navigation.

- 📊 **Höhenmeter & Höhenprofil:**
  - Exakte Auswertung von Gesamtaufstieg und Gesamtabstieg (`↗ 120m  ↘ 85m`).
  - Interaktives, kompaktes Höhenprofil-Diagramm im Routenplaner.

- 📥 **Umfassendes Offline-Kartenmanagement:**
  - **Regionen-Download:** Gezielter Kachel-Download für sichtbare Ausschnitte über alle tieferen Zoomstufen ($Z_{aktuell} \dots 18$).
  - **Datengrößen-Schätzung:** Anzeige von Kachelanzahl und geschätzter MB-Größe vor dem Download.
  - **Gleitendes 10s-ETA:** Live-Restzeitanzeige basierend auf der Download-Geschwindigkeit der letzten 10 Sekunden.
  - **Automatische Regionen-Benennung:** Ortsnamenerkennung per Reverse-Geocoding.
  - **Verwaltungsmenü:** Einzelne Regionen gezielt aktualisieren oder löschen.

- ❤️ **Favoriten & Suche:**
  - 100% offline gespeicherte Lieblingsorte und Favoriten-Routen in der lokalen Room-Datenbank.
  - Schnellsuchleiste im Favoriten-Tab mit Echtzeit-Filterung.

- 👤 **Wissenschaftliches Nutzer-Profil:**
  - Konfigurierbare Gehgeschwindigkeiten (Langsam 3.3 km/h, Normal 4.5 km/h, Schnell 5.8 km/h) basierend auf biomechanischen Gangbild-Studien (*Bohannon 1997 / MUTCD*).
  - Anpassbare Auto-Fahrgeschwindigkeiten.
  - Dreifach-Toggle für Erscheinungsbild (*System*, *Immer hell*, *Immer dunkel*).

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
├── data
│   ├── local
│   │   ├── dao          # Room DAOs (PoiDao, SavedRouteDao, OfflineRegionDao)
│   │   └── entity       # Room Entities (PoiEntity, SavedRouteEntity, OfflineRegionEntity)
│   ├── remote           # Retrofit APIs (BRouter, OSRM, Photon Geocoding)
│   └── repository       # Repositories (PoiRepository, RoutingRepository, LocationRepository, UserSettingsRepository)
├── domain
│   └── model            # Domain Modelle (LocationPoint, RouteResult, SpeedPreference, TravelMode)
└── ui
    ├── components       # Wiederverwendbare UI-Komponenten (ElevationProfileView, IconPicker)
    ├── favorites        # Favoriten-Tab (FavoritesScreen, FavoritesViewModel)
    ├── map              # Hauptkarte & Navigation (MapScreen, MapViewModel, OsmMapView, NavigationView, RoutePlannerCard)
    ├── profile          # Profil- & Einstellungen-Tab (ProfileScreen, ProfileViewModel)
    └── theme            # Material 3 Nagivator Theme
```

---

## 🚀 Installation & Build

### Voraussetzungen
- **Android Studio** 2024.2+ (Ladybug / Koala / 2026+)
- **JDK:** Java 17+
- **Android SDK:** Compile SDK 35, Min SDK 26 (Android 8.0+)
- **Gerät:** Physisches Android-Gerät oder Emulator (Google Play Services empfohlen)

### Schritte zum Ausführen
1. Repository klonen:
   ```bash
   git clone https://github.com/burzelmann/Navigator.git
   cd Navigator
   ```
2. Projekt in Android Studio öffnen.
3. Gradle-Sync ausführen:
   ```bash
   ./gradlew app:assembleDebug
   ```
4. Auf einem verbundenen Android-Gerät installieren:
   ```bash
   ./gradlew installDebug
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
