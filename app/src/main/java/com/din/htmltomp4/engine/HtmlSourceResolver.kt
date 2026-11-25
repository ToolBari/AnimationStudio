package com.din.htmltomp4.engine

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Accepts whatever the user picked via ActivityResultContracts.GetContent
 * (a raw .html file, or a .zip bundle containing index.html + assets) and
 * copies/extracts it into app cache so the WebView can load it via a
 * stable file:// path.
 */
object HtmlSourceResolver {

    fun resolve(context: Context, uri: Uri, displayName: String): File {
        val destDir = File(context.cacheDir, "html_src").apply {
            deleteRecursively()
            mkdirs()
        }

        return if (displayName.endsWith(".zip", ignoreCase = true)) {
            extractZip(context, uri, destDir)
        } else {
            copyDirectHtml(context, uri, destDir)
        }
    }

    private fun copyDirectHtml(context: Context, uri: Uri, destDir: File): File {
        val outFile = File(destDir, "index.html")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Could not open picked HTML file")
        return outFile
    }

    private fun extractZip(context: Context, uri: Uri, destDir: File): File {
        var htmlEntryFile: File? = null

        context.contentResolver.openInputStream(uri)?.use { rawInput ->
            ZipInputStream(rawInput).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    // Guard against zip-slip path traversal
                    val outFile = File(destDir, entry.name).canonicalFile
                    if (!outFile.path.startsWith(destDir.canonicalPath)) {
                        throw SecurityException("Illegal zip entry path: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zip.copyTo(fos) }
                        if (outFile.name.equals("index.html", ignoreCase = true) && htmlEntryFile == null) {
                            htmlEntryFile = outFile
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("Could not open picked ZIP file")

        return htmlEntryFile
            ?: destDir.walkTopDown().firstOrNull { it.extension.equals("html", true) }
            ?: throw IllegalStateException("No index.html found inside the ZIP")
    }
}
