# FetchPro - Production Android Download Manager

A production-ready Android download manager inspired by IDM (Internet Download Manager), built with modern Android stack.

## Goal (from PRD.md)
Build a production-ready Android download manager for direct HTTP/HTTPS downloads.

## Core Features Implemented

- **Multi-part downloads using HTTP Range** (`MultiPartDownloader.kt`)
  - Probes URL via HEAD + fallback GET Range:0-0
  - Splits files >1MB into up to 8 parts (dynamic based on size, min 2MB per part)
  - Concurrent part downloads via coroutines, writes directly at offset using RandomAccessFile
  - Single-part fallback for servers without Accept-Ranges

- **Pause/Resume**
  - Cooperative cancellation via AtomicBoolean + Job cancellation
  - Persists downloadedBytes per part in Room
  - Resume uses `Range: bytes=start-end` with If-Match ETag validation

- **Queue Management** (`DownloadQueueManager.kt`)
  - Max concurrent configurable (1-5, default 3)
  - Queued state sorted by creation time
  - Auto-start next queued when active finishes

- **Foreground Service + Notifications** (`DownloadForegroundService.kt`, `DownloadNotificationManager.kt`)
  - Long-running dataSync foreground service (Android 14+ compliant)
  - Per-download notification with progress, speed, pause/cancel/resume actions
  - Uses POST_NOTIFICATIONS permission

- **Retry / Failure Handling**
  - Exponential backoff retry (3 attempts) for IOException
  - Marks FAILED with error message, allows manual retry
  - Network availability check utility

- **Download History**
  - Room DB observes completed downloads ordered by completedAt
  - HistoryScreen

- **File Integrity**
  - ETag validation via If-Match
  - Optional SHA256 checksum verification (IntegrityChecker.kt)
  - File allocation to prevent sparse files

- **Browser Integration**
  - Intent filters for VIEW (http/https) and SEND (text/plain)
  - MainActivity extracts first URL via regex
  - DownloadInterceptor helper for future WebView / extension messaging
  - MimeType detection for downloadable content

## Architecture (from Architecture.md)

Layers:
- **UI (Jetpack Compose Material3)** - Screens: Home, Details, History, Settings
- **ViewModel** - HiltViewModel, StateFlow, uses UseCases
- **Repository** - Interface + Impl wrapping DAO + Engine + Service intents
- **Download Engine** - Probe + MultiPartDownloader (production OkHttp, no mocks)
- **Persistence (Room)** - DownloadEntity + DownloadPartEntity with foreign key cascade
- **Workers/Foreground Service** - DataSync FGS, QueueManager

Principles: MVVM, DI (Hilt), Single responsibility, Testable.

## Folder Structure

```
app/src/main/java/com/fetchpro/downloadmanager/
├── MainActivity.kt + DownloadManagerApp.kt
├── browser/ DownloadInterceptor.kt
├── data/
│   ├── local/db/ Database, Dao, Entities, Mappers
│   └── repository/ DownloadRepositoryImpl
├── di/ AppModule.kt
├── domain/
│   ├── model/ DownloadItem, DownloadPart, DownloadState
│   ├── repository/ contract
│   └── usecase/ UseCases
├── download/
│   ├── engine/ DownloadEngine, MultiPartDownloader, HttpClientProvider, FileAllocator, IntegrityChecker
│   ├── queue/ DownloadQueueManager
│   ├── service/ DownloadForegroundService, DownloadNotificationManager
│   └── utils/ FileUtils, NetworkUtils, MimeTypeUtils
└── presentation/
    ├── navigation/ NavGraph.kt
    └── ui/
        ├── theme/ Material3
        ├── components/ DownloadItemCard.kt
        └── screens/{home,details,history,settings}
```

## Tech Stack

- Kotlin 1.9.22, Coroutines, Flows
- Jetpack Compose BOM 2024.02.00, Material3, Navigation Compose
- Room 2.6.1, DataStore
- Hilt 2.50, OkHttp 4.12.0
- WorkManager (ready for future periodic cleanup worker)
- MinSdk 26, Target 34

## Build

Open in Android Studio Hedgehog+.

```
./gradlew assembleDebug
```

Permissions handled: INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS.

## Development Phases (from Phasis.md)

1. Project setup - Done (gradle, manifest, themes)
2. Download engine - Done (probe, multipart, allocator, integrity)
3. Queue/service - Done (FGS, notifications, queue manager)
4. UI - Done (Compose + ViewModels)
5. Browser integration - Done (intent filters + interceptor)
6. Testing - Pending manual + unit (Turbine ready)
7. Optimization & release - R8 minify enabled for release

## Known Implementation Notes

- FileProvider for opening completed files.
- Default download dir: /Download/FetchPro (public), fallback to app cache for temp.
- File naming deduplication: adds "(counter)" suffix.
- Speed calc: delta bytes / 500ms interval.
- Parts persistence every collection + 1s polling fallback in Details.

## Security / Non-goals Compliance

- No fake/demo code, no simulated progress.
- Only direct HTTP/HTTPS, no ToS-violating service download logic.
- ETag + Range validation prevents corruption.

## Testing & Optimization (Phases 6 & 7 - Completed)

### Unit Tests (JVM)
Location: `app/src/test/java/com/fetchpro/downloadmanager/`
- `MultiPartDownloaderTest`:
  - probe returns content-length + Accept-Ranges
  - Content-Range fallback parsing (206)
  - Single-part 100KB download via MockWebServer
  - Multi-part 1MB with custom Dispatcher that respects Range header (4 parts) and verifies byte equality
  - FileUtils sanitize
- `DatabaseAndMapperTest` (Robolectric):
  - Room inMemory insert + observe
  - Insert with parts FK cascade
  - State mapping

Run: `./gradlew testDebugUnitTest`

### Instrumented Tests
- `QueueManagerInstrumentedTest`: verifies maxConcurrent limit respected via Room inserts

### Optimization & Release
- Icons generated for hdpi-xxxhdpi + round
- `proguard-rules.pro` full: keep Room entities, OkHttp, Hilt, Serialization, remove logs
- `signing.properties.template` + dynamic signing config loader in build.gradle.kts
- `StorageAccessHelper`: SAF support - multipart downloads to temp File then copy to SAF on completion (RandomAccessFile can't work directly on DocumentFile)
- `SettingsDataStore`: DataStore Preferences persisting maxConcurrent, maxParts, wifiOnly, customDir, autoRetry, keepHistoryDays
- `CleanupWorker`: HiltWorker periodic 24h deletes tmp >24h & completed history beyond keepDays
- `DownloadBroadcastReceiver`: BOOT_COMPLETED reschedules cleanup, CONNECTIVITY_CHANGE placeholder for auto-resume
- `DownloadManagerApp` implements `Configuration.Provider` for HiltWorkerFactory
- `NotificationPermissionHandler` composable via Accompanist Permissions (Android 13+)
- `DownloadForegroundService` now checks wifiOnly setting via NetworkUtils before starting, pauses with "Waiting for WiFi" message, verifies checksum via IntegrityChecker on COMPLETED
- `DownloadRepositoryImpl`: respects DataStore custom dir + maxParts
- `SettingsScreen`: sliders for concurrent + maxParts + history days, switches for wifiOnly + autoRetry, SAF folder picker via `OpenDocumentTree`, persistable URI permission

### Build Release
```
cp signing.properties.template signing.properties
# edit keystore path/passwords
./gradlew assembleRelease
# APK at app/build/outputs/apk/release/
```

See `CHANGELOG.md` for v1.0.0 details.
