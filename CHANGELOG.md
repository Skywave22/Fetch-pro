# Changelog - FetchPro

## v1.0.0 (2026-07-17) - Production Release
- Initial production-ready build
- Multi-part HTTP Range downloads (up to 16 parts, adaptive)
- Pause/resume with ETag If-Match validation
- Queue management (max concurrent 1-5, auto WiFi-only)
- Foreground Service dataSync type (Android 14+ compliant)
- Notifications with actions: Pause/Resume/Cancel + progress + speed
- Room persistence with download parts FK cascade
- DataStore settings: custom dir (SAF), maxParts, wifiOnly, autoRetry, history retention
- File integrity: ETag + optional SHA256 checksum verification
- Browser integration: VIEW http/https + SEND text/plain, URL extraction regex
- Material3 dynamic color, dark/light, bottom nav, permission handler for POST_NOTIFICATIONS
- FileProvider secure file opening, dedup naming `(1)` suffix
- Cleanup Worker periodic 24h for orphan temp files & expired history
- Boot receiver + connectivity receiver for rescheduling
- ProGuard R8 fully optimized
- Unit tests: MultiPartDownloader with MockWebServer (HEAD, Range 206, multi-part 1MB), DB + mapper
- Instrumented test: QueueManager respects limit

## Optimizations
- 64KB buffer for IO, RandomAccessFile seek/write at offset, pre-allocation via setLength
- Speed calc via delta per 500ms, throttled DB updates
- Connection pooling via singleton OkHttp client, indefinite read timeout for download clients
- Exponential backoff retry (3 attempts)

## Known Limitations
- SAF URI persistence: UI stores SAF URI string; repository currently uses File path fallback - full SAF DocumentFile write not yet implemented for multipart (needs SAF RandomAccessFile workaround)
- No chunk checksum per part (could add per-part MD5)
- No download speed limiter

## Next
- Implement per-part checksum + re-download failed parts only
- Add download categories (music, video, docs) via mime detection
- Add bandwidth limiter via token bucket
