package com.din.htmltomp4.engine

import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import java.io.File

sealed class HtmlBuildEvent {
    data class Progress(val percent: Int) : HtmlBuildEvent()
    data class Done(val outputFile: File) : HtmlBuildEvent()
    data class Failed(val error: Throwable) : HtmlBuildEvent()
}

/**
 * Reads a sequence of numbered JPEG frames (and, if present, an extracted
 * audio track) and bakes them into a single portable .html file: a JS array
 * of base64 image data URIs plus a small canvas player, optionally
 * synced to an embedded <audio> element.
 *
 * Deliberately a single self-contained file (no separate asset folder) so
 * it's trivially shareable — the tradeoff is file size, which is why the
 * config screen keeps fps/resolution/quality on the conservative side.
 */
class HtmlFromFramesBuilder {

    fun build(frameDir: File, fps: Int, outputFile: File, audioFile: File? = null): Flow<HtmlBuildEvent> = flow {
        try {
            val frameFiles = frameDir.listFiles { f -> f.extension.equals("jpg", true) }
                ?.sortedBy { it.name }
                ?: emptyList()

            if (frameFiles.isEmpty()) {
                emit(HtmlBuildEvent.Failed(IllegalStateException("No frames were extracted from the video")))
                return@flow
            }

            // Read actual frame dimensions from the first frame (ffmpeg's
            // scale filter with -2 rounds height to the nearest even number,
            // so we don't assume it ourselves).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(frameFiles.first().absolutePath, bounds)
            val width = bounds.outWidth.takeIf { it > 0 } ?: 480
            val height = bounds.outHeight.takeIf { it > 0 } ?: 270

            val sb = StringBuilder()
            sb.append("[")
            val total = frameFiles.size
            frameFiles.forEachIndexed { index, file ->
                val bytes = file.readBytes()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                sb.append("\"data:image/jpeg;base64,").append(b64).append("\"")
                if (index != frameFiles.lastIndex) sb.append(",")

                // frames are ~90% of build progress, audio encoding is the rest
                val percent = ((index + 1) * 90 / total)
                if (index % 5 == 0 || index == frameFiles.lastIndex) {
                    emit(HtmlBuildEvent.Progress(percent))
                }
            }
            sb.append("]")

            var audioDataUri: String? = null
            if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                val audioB64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
                audioDataUri = "data:audio/mp4;base64,$audioB64"
            }
            emit(HtmlBuildEvent.Progress(95))

            val html = buildHtmlDocument(sb.toString(), fps, width, height, total, audioDataUri)
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(html)

            emit(HtmlBuildEvent.Progress(100))
            emit(HtmlBuildEvent.Done(outputFile))
        } catch (t: Throwable) {
            emit(HtmlBuildEvent.Failed(t))
        }
    }

    private fun buildHtmlDocument(
        framesJsArray: String,
        fps: Int,
        width: Int,
        height: Int,
        frameCount: Int,
        audioDataUri: String?
    ): String {
        val audioTag = if (audioDataUri != null) {
            """<audio id="track" preload="auto"><source src="$audioDataUri" type="audio/mp4"></audio>"""
        } else {
            ""
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
<title>Video Export</title>
<style>
  html, body { margin:0; padding:0; width:100%; height:100%; overflow:hidden; background:#000; }
  #stage { width:100%; height:100%; display:flex; align-items:center; justify-content:center; position:relative; }
  canvas { max-width:100%; max-height:100%; display:block; }
  #tapOverlay {
    position:fixed; inset:0; display:none; align-items:center; justify-content:center;
    background:rgba(0,0,0,0.55); color:#fff; font-family:-apple-system,Roboto,Arial,sans-serif;
    font-size:5vw; font-weight:600; z-index:10; cursor:pointer; text-align:center;
  }
</style>
</head>
<body>
<div id="stage">
  <canvas id="player" width="$width" height="$height"></canvas>
  $audioTag
  <div id="tapOverlay">Tap to play &#9658;</div>
</div>
<script>
(function () {
  "use strict";
  var FPS = $fps;
  var FRAME_COUNT = $frameCount;
  var FRAMES = $framesJsArray;

  var canvas = document.getElementById('player');
  var ctx = canvas.getContext('2d');
  var audioEl = document.getElementById('track');
  var tapOverlay = document.getElementById('tapOverlay');
  var HAS_AUDIO = !!audioEl;

  var images = new Array(FRAMES.length);
  FRAMES.forEach(function (src, i) {
    var img = new Image();
    img.src = src;
    images[i] = img;
  });

  var startTime = performance.now();
  var loopStarted = false;

  function drawFrameAt(tSeconds) {
    var idx = Math.floor(tSeconds * FPS) % FRAME_COUNT;
    if (idx < 0) idx = 0;
    var img = images[idx];
    if (img && img.complete && img.naturalWidth > 0) {
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    }
  }

  function loop(now) {
    // While audio is actively playing, drive the frame index from its
    // currentTime so video stays sample-accurate in sync with sound.
    // Otherwise (no audio track, or autoplay was blocked and the user
    // hasn't tapped yet) fall back to wall-clock timing so the animation
    // still plays — just silently.
    if (HAS_AUDIO && !audioEl.paused && !audioEl.ended) {
      drawFrameAt(audioEl.currentTime);
    } else {
      drawFrameAt((now - startTime) / 1000);
    }
    requestAnimationFrame(loop);
  }

  function startLoop() {
    if (loopStarted) return;
    loopStarted = true;
    requestAnimationFrame(loop);
  }

  if (HAS_AUDIO) {
    audioEl.addEventListener('ended', function () {
      audioEl.currentTime = 0;
      var p = audioEl.play();
      if (p && p.catch) p.catch(function () {});
    });

    // Try autoplay immediately (works if the WebView/browser allows it).
    var attempt = audioEl.play();
    if (attempt && attempt.then) {
      attempt.then(function () {
        tapOverlay.style.display = 'none';
        startLoop();
      }).catch(function () {
        // Autoplay-with-sound was blocked — show a tap prompt instead of
        // silently failing, since the whole point of this export is audio.
        tapOverlay.style.display = 'flex';
        startTime = performance.now();
        startLoop();
        tapOverlay.addEventListener('click', function () {
          tapOverlay.style.display = 'none';
          audioEl.currentTime = 0;
          var p2 = audioEl.play();
          if (p2 && p2.catch) p2.catch(function () {});
        });
      });
    } else {
      // Older WebView without Promise-based play() — best effort.
      tapOverlay.style.display = 'none';
      startLoop();
    }
  } else {
    startLoop();
  }
})();
</script>
</body>
</html>
""".trimIndent()
    }
}
