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

/** A class quiz named by the link in the trainer's quiz QR code. */
data class QuizLink(val courseId: String, val quizId: String)

private val QUIZ_ID = Regex("^[A-Za-z0-9._~-]{1,100}$")

/**
 * Reads `https://<host>/quiz/<courseId>/<quizId>`, the link behind a quiz QR code. Anything else
 * (another host, http, extra path parts, odd characters) is not a quiz link and gives null.
 */
fun parseQuizLink(url: String?, host: String): QuizLink? {
    if (url.isNullOrBlank() || !isSafeLink(url.trim())) return null
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    if (!uri.host.equals(host, ignoreCase = true) || (uri.port != -1 && uri.port != 443)) return null
    val parts = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
    if (parts.size != 3 || parts[0] != "quiz") return null
    if (!QUIZ_ID.matches(parts[1]) || !QUIZ_ID.matches(parts[2])) return null
    return QuizLink(parts[1], parts[2])
}

/**
 * Where the file behind a material can be downloaded: Google Drive and Docs viewer links are
 * turned into their download / PDF-export form, any other https link is used as it is. Null for
 * something that is not one file (a Drive folder) or a link that is not safe to fetch.
 */
fun downloadSource(url: String): String? {
    if (!isSafeLink(url)) return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host.lowercase()
    val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    fun idAfter(key: String): String? = parts.indexOf(key).let { i -> if (i >= 0) parts.getOrNull(i + 1) else null }?.takeIf(FILE_ID::matches)
    if (host == "drive.google.com") {
        if ("folders" in parts || "drive" == parts.firstOrNull()) return null
        val id = idAfter("d") ?: queryValue(uri, "id")?.takeIf(FILE_ID::matches) ?: return null
        return "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t"
    }
    if (host == "docs.google.com") {
        // /document/d/ID, /presentation/d/ID, /spreadsheets/d/ID
        val id = idAfter("d")
        if (id != null && parts[0] in listOf("document", "presentation", "spreadsheets")) return "https://docs.google.com/${parts[0]}/d/$id/export?format=pdf"
    }
    return url
}

/** A link the server may send as a path on the portal ("/enrol/<id>"): made absolute on [base], then checked. */
fun portalUrl(raw: String?, base: String): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val absolute = if (text.startsWith("/") && !text.startsWith("//")) base.trimEnd('/') + text else text
    return absolute.takeIf(::isSafeLink)
}

/** The venue's address in Google Maps. */
fun mapsUrl(address: String): String =
    "https://www.google.com/maps/search/?api=1&query=" + java.net.URLEncoder.encode(address.trim(), "UTF-8").replace("+", "%20")

/** "Google Meet", "Zoom" or "Microsoft Teams" from a meeting link, named like the website's Virtual Class Link. */
fun meetingProvider(url: String): String {
    val host = runCatching { URI(url.trim()).host?.lowercase() }.getOrNull().orEmpty()
    return when {
        "zoom." in host -> "Zoom"
        "teams." in host || "microsoft" in host -> "Microsoft Teams"
        "meet.google" in host -> "Google Meet"
        else -> "online class"
    }
}

/** The trainer's two SSG attendance links on MySkillsFuture, built from the Digital Attendance ID. */
fun ssgQrAttendanceUrl(da: String): String = "https://www.myskillsfuture.gov.sg/spface/splogin/select-session?course-run-code=" + da.trim()
fun ssgAttendanceListUrl(da: String): String = "https://www.myskillsfuture.gov.sg/api/take-attendance/" + da.trim()
