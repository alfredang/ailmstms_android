package com.tertiaryinfotech.ailmstms.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** The link did not lead to a PDF the app can show (a web page, a folder, too large, unreachable). */
class NotPdfException(message: String) : IOException(message)

/**
 * Downloads a material into the app's cache when, and only when, it is a PDF.
 * No session token or cookie is ever sent: these are third-party hosts (mostly Google Drive).
 */
class PdfFetcher(
    private val dir: File,
    private val maxBytes: Long = 60L * 1024 * 1024,
    private val httpsOnly: Boolean = true,
    private val freshFor: Long = TimeUnit.HOURS.toMillis(1),
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false) // never follow a redirect from https down to http
        .build()

    private fun cacheFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.pdf")
    }

    /**
     * Tries each candidate link in turn and returns the first that is a PDF.
     * [progress] receives 0..1, or null while the size is unknown.
     */
    suspend fun fetch(candidates: List<String>, progress: (Float?) -> Unit = {}): File {
        var last: IOException = NotPdfException("This material is not a PDF.")
        for (url in candidates) {
            try {
                return fetchOne(url, progress)
            } catch (e: IOException) {
                last = e
            }
        }
        throw last
    }

    private suspend fun fetchOne(url: String, progress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        if (httpsOnly && !isSafeLink(url)) throw NotPdfException("Only secure links can be opened.")
        dir.mkdirs()
        val target = cacheFile(url)
        if (target.isFile && target.length() > 4 && System.currentTimeMillis() - target.lastModified() < freshFor) {
            return@withContext target
        }
        val call = http.newCall(Request.Builder().url(url).header("Accept", "application/pdf,*/*;q=0.5").build())
        val handle = coroutineContext.job.invokeOnCompletion { if (it != null) call.cancel() }
        val part = File(dir, target.name + ".part")
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw NotPdfException("The material could not be downloaded (${response.code}).")
                if (httpsOnly && !response.request.url.isHttps) throw NotPdfException("Only secure links can be opened.")
                val body = response.body ?: throw NotPdfException("The material is empty.")
                if (body.contentType()?.subtype?.lowercase() == "html") throw NotPdfException("This material is a web page.")
                val length = body.contentLength()
                if (length > maxBytes) throw NotPdfException("This material is too large to show in the app.")
                val source = body.source()
                // A PDF always starts with "%PDF". Anything else (sign-in or virus-scan pages) is not shown in the app.
                if (!source.request(4) || source.peek().readUtf8(4) != "%PDF") throw NotPdfException("This material is not a PDF.")
                progress(if (length > 0) 0f else null)
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw NotPdfException("This material is too large to show in the app.")
                        out.write(buffer, 0, n)
                        if (length > 0) progress((total.toFloat() / length).coerceIn(0f, 1f))
                    }
                }
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            trim(keep = target)
            target
        } finally {
            handle.dispose()
            part.delete()
        }
    }

    /** Keeps the cache small: nothing older than a day, and no more than about 200 MB in total. */
    private fun trim(keep: File) {
        val files = dir.listFiles()?.filter { it.isFile && it != keep }?.sortedByDescending { it.lastModified() } ?: return
        var total = keep.length()
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        for (file in files) {
            total += file.length()
            if (file.lastModified() < cutoff || total > 200L * 1024 * 1024) file.delete()
        }
    }

    /** Removes every cached material, for example at sign-out. */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
