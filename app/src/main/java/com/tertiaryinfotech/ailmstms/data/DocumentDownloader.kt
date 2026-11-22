package com.tertiaryinfotech.ailmstms.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** A material could not be downloaded; the message is written for the user. */
class DownloadException(message: String) : IOException(message)

const val DOWNLOAD_IS_FOLDER = "This is a folder, not a single file. Open it in your browser to download its files."
const val DOWNLOAD_NOT_ALLOWED = "This document can't be downloaded here. Open it in your browser instead."
const val DOWNLOAD_INCOMPLETE = "The download did not finish. Check your connection and try again."

/**
 * Fetches a material to a file under [root] (the app's cache) so it can be saved, printed or
 * sent with the system share sheet. Like [PdfFetcher] it only follows https links and never
 * sends the session token: these are third-party hosts, mostly Google Drive.
 */
class DocumentDownloader(private val root: File, private val maxBytes: Long = 200L * 1024 * 1024) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    /** A fresh folder for one file, after dropping downloads that were handed over long ago. */
    private fun slot(name: String): File {
        root.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > TimeUnit.HOURS.toMillis(1) }?.forEach { it.deleteRecursively() }
        return File(File(root, UUID.randomUUID().toString()).apply { mkdirs() }, name)
    }

    suspend fun fetch(title: String, url: String): File = withContext(Dispatchers.IO) {
        val source = downloadSource(url) ?: throw DownloadException(if (isSafeLink(url)) DOWNLOAD_IS_FOLDER else DOWNLOAD_NOT_ALLOWED)
        val request = try {
            Request.Builder().url(source).header("Accept", "*/*").build()
        } catch (e: IllegalArgumentException) {
            throw DownloadException(DOWNLOAD_NOT_ALLOWED)
        }
        val call = http.newCall(request)
        val handle = coroutineContext.job.invokeOnCompletion { if (it != null) call.cancel() }
        var file: File? = null
        try {
            call.execute().use { response ->
                val body = response.body
                val type = response.header("Content-Type").orEmpty().lowercase()
                // A web page instead of a file means the owner has not allowed downloads, or it needs a sign-in.
                if (!response.isSuccessful || body == null || !response.request.url.isHttps || type.startsWith("text/html")) {
                    throw DownloadException(DOWNLOAD_NOT_ALLOWED)
                }
                if (body.contentLength() > maxBytes) throw DownloadException(DOWNLOAD_NOT_ALLOWED)
                val target = slot(fileName(title, response.header("Content-Disposition").orEmpty(), type, source)).also { file = it }
                target.outputStream().use { out ->
                    val input = body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw DownloadException(DOWNLOAD_NOT_ALLOWED)
                        out.write(buffer, 0, n)
                    }
                }
                target
            }
        } catch (e: DownloadException) {
            file?.parentFile?.deleteRecursively()
            throw e
        } catch (e: IOException) {
            file?.parentFile?.deleteRecursively()
            throw DownloadException(DOWNLOAD_INCOMPLETE)
        } finally {
            handle.dispose()
        }
    }

    /** A file the app already holds (the demo's sample PDF), copied where the share sheet may read it. */
    suspend fun adopt(title: String, existing: File): File = withContext(Dispatchers.IO) {
        slot(safeName(title.ifBlank { "Document" } + "." + existing.extension.ifEmpty { "pdf" })).also { existing.copyTo(it, overwrite = true) }
    }

    fun clear() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    companion object {
        fun safeName(name: String): String = name.replace(Regex("""[/\\:*?"<>|\p{Cntrl}]"""), "-").trim().trim('.').take(120).ifEmpty { "Document" }

        /** The file name from a Content-Disposition header (`filename*=UTF-8''…` or `filename="…"`), or "". */
        fun suggestedName(header: String): String {
            Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)""").find(header)?.let { m ->
                runCatching { URLDecoder.decode(m.groupValues[1].trim().trim('"').replace("+", "%2B"), "UTF-8") }.getOrNull()?.let { return it }
            }
            return Regex("""filename\s*=\s*("([^"]*)"|[^;]+)""").find(header)?.let { m ->
                m.groupValues[2].ifEmpty { m.groupValues[1] }.trim().trim('"')
            }.orEmpty()
        }

        /** The server's file name when it gives a real one; otherwise the material's title with a fitting extension. */
        fun fileName(title: String, disposition: String, contentType: String, source: String): String {
            val suggested = suggestedName(disposition)
            if (suggested.isNotEmpty() && suggested != "download" && suggested != "export") return safeName(suggested)
            val fromLink = source.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "")
                .takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
            val extension = if ("pdf" in contentType) "pdf" else fromLink ?: "pdf"
            return safeName(title.ifBlank { "Document" } + "." + extension)
        }
    }
}
