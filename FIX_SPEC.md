# Animation Studio (com.din.htmltomp4) — Fix Spec for Puchu

## What's in this ZIP
- `FIX_SPEC.md` — this file
- `source/` — the app's full source (74 files, from the creator's shared ZIP)
- `test/safar_dua.html` — the test file (720×1280, 39.31s, verified good)

## Test result that drives this fix (26 Nov 2026, Safar 39s HTML)
Output was 1280×720 landscape, mpeg4, 1170 frames at 3.019fps, 387.5s long.
Animation played in the first ~48s, end card frozen ~340s. Takbir labels and
end-card reference line cropped off. Verdict: UNUSABLE for 9:16 Shorts.

## Root causes (read from the source in this ZIP's `source/` copy)
1. **No portrait option.** `ConversionModels.kt` — `Resolution` enum has ONLY
   `HD_720P(1280, 720)` and `HD_1080P(1920, 1080)`. The engine sizes the
   WebView from `config.resolution`, so a portrait HTML is rendered into a
   landscape frame and cropped.
2. **Wall-clock capture.** `HtmlAnimationCaptureEngine.kt` captures frames in
   real time while the CSS animation plays. On the owner's phone the software
   Canvas draw + JPEG q92 manages only ~3fps, so 1170 frames take 387s of
   wall time. `ConverterViewModel.kt` then muxes at the MEASURED fps
   (1170 / 387s = 3.019) — the video faithfully reproduces the slow capture.
3. **mpeg4 codec** — forced by the LGPL ffmpeg-kit build (no libx264).
   Acceptable; YouTube takes it. Do not change.

## Fixes (in order)
1. **Portrait presets** — add to the `Resolution` enum:
   `PORTRAIT_720P(720, 1280, "720p portrait")` and
   `PORTRAIT_1080P(1080, 1920, "1080p portrait")`.
   Add an orientation picker on the Config screen (Portrait 9:16 / Landscape
   16:9). Nothing else needs to change for orientation — the engine already
   sizes the WebView from `config.resolution`.
2. **Deterministic capture (the real fix)** — stop depending on wall clock:
   after page load, inject JS to pause every CSS animation:
   `document.getAnimations().forEach(a => a.pause())`.
   Then before capturing frame N, inject JS to seek all animations to the
   exact frame time: `document.getAnimations().forEach(a => a.currentTime = N * frameIntervalMs)`.
   Frame N then shows exactly animation-time N/30s, no matter how slow the
   phone captures. (Works for CSS-animation HTMLs — all of ours. Pages driven
   by requestAnimationFrame will not seek; note that limit in the UI.)
3. **Encode at the CONFIG fps** (30/60 as chosen), not the measured fps —
   once capture is deterministic the measured fps is meaningless. Keep mpeg4.
4. **Keep everything else** — permissions, FileProvider, history, Video→HTML
   direction untouched.

## Verification (must pass before delivery)
- Test file: `test/safar_dua.html` in this ZIP (720×1280, 39.31s) — the verified-good Safar file. Use it for the verification below.
- Output must be: 720×1280, 30fps, ~39s, h264-or-mpeg4, with audio NOT
  required (video-only is fine — audio is merged separately).
- Check frames: hook 1s, takbir 10.5s (all 3 "আল্লাহু আকবর" labels readable),
  ayah 20s, end card 37.5s (full reference line visible).

## Build rule (owner standing rule — do not break)
- Build the official way: Gradle / Android SDK with proper dependency
  management (GitHub Actions route). NEVER hand-assemble a third-party SDK
  closure (the manual aapt2/d8 pipeline crashed the AI Video app twice).
- Deliver a signed/debug APK + the changed source files as a ZIP.
