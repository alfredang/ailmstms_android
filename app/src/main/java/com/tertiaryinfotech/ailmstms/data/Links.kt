package com.tertiaryinfotech.ailmstms.data

import java.net.URI

/** Only plain https links with a host and no embedded credentials are ever opened or downloaded. */
fun isSafeLink(url: String): Boolean =
    runCatching { val u = URI(url); u.scheme == "https" && !u.host.isNullOrBlank() && u.userInfo == null }.getOrDefault(false)

private val FILE_ID = Regex("^[A-Za-z0-9_-]{10,}$")

private fun queryValue(uri: URI, name: String): String? =
    uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

/**
 * Where a material could be fetched as a PDF, most likely first. Google Drive and Docs viewer
 * links are rewritten to their download/export form. An empty list means the link can only be
 * opened in the browser (folders, published-to-web pages, spreadsheets, unsafe links).
 */
fun pdfCandidates(url: String): List<String> {
    if (!isSafeLink(url)) return emptyList()
    val uri = runCatching { URI(url) }.getOrNull() ?: return emptyList()
    val host = uri.host.lowercase()
    val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }

    fun drive(id: String) = listOf(
        "https://drive.google.com/uc?export=download&id=$id",
        // Larger files show a virus-scan page on the first link; this one serves the file itself.
        "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t",
    )

    if (host == "drive.google.com") {
        if (parts.size >= 3 && parts[0] == "file" && parts[1] == "d" && FILE_ID.matches(parts[2])) return drive(parts[2])
        if (parts.firstOrNull() in listOf("open", "uc")) {
            val id = queryValue(uri, "id")
            if (id != null && FILE_ID.matches(id)) return drive(id)
        }
        return emptyList()
    }
    if (host == "docs.google.com") {
        if (parts.size >= 3 && parts[1] == "d" && FILE_ID.matches(parts[2])) {
            return when (parts[0]) {
                "presentation" -> listOf("https://docs.google.com/presentation/d/${parts[2]}/export/pdf")
                "document" -> listOf("https://docs.google.com/document/d/${parts[2]}/export?format=pdf")
                else -> emptyList()
            }
        }
        return emptyList()
    }
    if (host.endsWith(".google.com") || host == "google.com") return emptyList()
    return listOf(url)
}

/** The checked link the Drive upload returns: only Google Drive file links are recorded. */
fun isDriveLink(url: String): Boolean =
    isSafeLink(url) && runCatching { URI(url).host.lowercase() in listOf("drive.google.com", "docs.google.com") }.getOrDefault(false)
