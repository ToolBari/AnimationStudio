# AnimationStudio

HTML to MP4 converter for Android (9:16 portrait + 16:9 landscape).
Renders an HTML animation in a WebView, captures frames, and muxes them
into an MP4 with ffmpeg-kit.

## Build (official route — GitHub Actions)

Every push to `main` builds the debug APK. Download it from the run's
**Artifacts** (`app-debug`).

Local build: `./gradlew assembleDebug` (JDK 17).

## Fix in progress

- Add portrait 720x1280 / 1080x1920 presets (v1.0 is landscape-only)
- Deterministic frame capture (drive `document.getAnimations()` per frame
  instead of wall-clock capture)
- Encode at the configured fps

See `FIX_SPEC.md` for the full spec. `test/safar_dua.html` is the
verification file: after the fix, its output must be 720x1280, 30fps,
about 39 seconds.
