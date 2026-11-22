package com.tertiaryinfotech.ailmstms.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.tertiaryinfotech.ailmstms.data.isSafeLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

const val APP_NAME = "TIA TMS"
const val PRIVACY_URL = "https://lms-tms.tertiaryinfotech.com/mobile/privacy.html"
const val SUPPORT_WHATSAPP = "https://wa.me/6588666375"

/** Opens a mailto:, tel: or https link in the app that handles it (the Contact rows on the Account page). */
fun openContact(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "No app is available to open this.", Toast.LENGTH_LONG).show() }
}

/** The system share sheet for a link or a line of text. */
fun shareText(context: Context, text: String, subject: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, subject)
    val chooser = Intent.createChooser(send, "Share")
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}

/** The system share sheet for a downloaded file: save to Files or Drive, print, send to another app. */
fun shareFile(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    val send = Intent(Intent.ACTION_SEND).setType(type)
        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The chooser passes the read grant on through the clip data.
    send.clipData = ClipData.newRawUri(file.name, uri)
    val chooser = Intent.createChooser(send, file.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** Opens an https link in the browser or the app that handles it. Unsafe links are ignored. */
fun openLink(context: Context, url: String) {
    if (!isSafeLink(url)) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "No app is available to open this link.", Toast.LENGTH_LONG).show() }
}

private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .apply { addPath(PathParser().parsePathString(path).toNodes(), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black)) }
        .build()

// The few icons that are not in the core Material set, drawn here to avoid the large extended icon library.
val SchoolIcon by lazy { icon("School", "M2,8 L12,3 L22,8 L12,13 Z M5,12 L12,15 L19,12 L19,18 L12,21 L5,18 Z") }
val QrIcon by lazy {
    icon(
        "QrCode",
        "M3,3h8v8H3z M5,5v4h4V5z M13,3h8v8h-8z M15,5v4h4V5z M3,13h8v8H3z M5,15v4h4v-4z " +
            "M13,13h2v2h-2z M17,13h4v2h-4z M13,17h4v2h-4z M19,17h2v4h-2z M13,19h2v2h-2z M15,15h2v2h-2z",
    )
}
val DocumentIcon by lazy { icon("Document", "M6,2h8l6,6v14H4V2z M13,3.5V9h5.5z M7,12h10v2H7z M7,16h10v2H7z") }
val UploadIcon by lazy { icon("Upload", "M11,16V7.8L8.4,10.4L7,9l5,-5l5,5l-1.4,1.4L13,7.8V16z M5,18h14v2H5z") }
val ClipboardIcon by lazy { icon("Clipboard", "M9,2h6v2h4v18H5V4h4z M7,6v14h10V6h-2v2H9V6z M9,11h6v2H9z M9,15h6v2H9z") }
val OpenExternalIcon by lazy { icon("OpenExternal", "M5,5h6v2H7v10h10v-4h2v6H5z M14,5h5v5h-2V8.4l-5.3,5.3l-1.4,-1.4L15.6,7H14z") }
val ZoomInIcon by lazy { icon("ZoomIn", "M11,5h2v6h6v2h-6v6h-2v-6H5v-2h6z") }
val ZoomOutIcon by lazy { icon("ZoomOut", "M5,11h14v2H5z") }
val DownloadIcon by lazy { icon("Download", "M11,4h2v8.2l2.6,-2.6L17,11l-5,5l-5,-5l1.4,-1.4L11,12.2z M5,18h14v2H5z") }
val ClockIcon by lazy { icon("Clock", "M12,2a10,10 0 1,0 0,20a10,10 0 1,0 0,-20z M12,4a8,8 0 1,1 0,16a8,8 0 1,1 0,-16z M11,7h2v4.6l3.2,1.9l-1,1.7L11,12.7z") }
val PaymentsIcon by lazy { icon("Payments", "M2,6h20v12H2z M4,8v8h16V8z M12,9.5a2.5,2.5 0 1,0 0,5a2.5,2.5 0 1,0 0,-5z") }
val CertificateIcon by lazy { icon("Certificate", "M12,2a7,7 0 1,0 0,14a7,7 0 1,0 0,-14z M12,5a4,4 0 1,1 0,8a4,4 0 1,1 0,-8z M8,17h8v5l-4,-2l-4,2z") }
val CreditCardIcon by lazy { icon("CreditCard", "M2,5h20v14H2z M4,7v2h16V7z M4,12v5h16v-5z M6,14h5v1.5H6z") }
val SwapIcon by lazy { icon("Swap", "M7,7h10.2l-2.6,-2.6L16,3l5,5l-5,5l-1.4,-1.4L17.2,9H7z M17,15H6.8l2.6,-2.6L8,11l-5,5l5,5l1.4,-1.4L6.8,17H17z") }
val ChatIcon by lazy { icon("Chat", "M4,4h16v12H9l-5,4z M6,6v9.8L8.3,14H18V6z") }
val LinkIcon by lazy { icon("Link", "M4,12a4,4 0 0,1 4,-4h3v2H8a2,2 0 0,0 0,4h3v2H8a4,4 0 0,1 -4,-4z M13,8h3a4,4 0 0,1 0,8h-3v-2h3a2,2 0 0,0 0,-4h-3z M8,11h8v2H8z") }

// ---------------------------------------------------------------- building blocks
// The house layout shared with the sibling Tertiary Courses app (cards with a hairline border,
// small uppercase section labels, tinted chips), drawn in this app's own brand colours.

private val CardShape = RoundedCornerShape(14.dp)

/** The app's card: a lighter surface with a hairline border. Tappable when [onClick] is given. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val lms = LocalLmsColors.current
    val colors = CardDefaults.cardColors(containerColor = lms.card, contentColor = MaterialTheme.colorScheme.onSurface)
    val border = BorderStroke(1.dp, lms.cardBorder)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, shape = CardShape, colors = colors, border = border, content = content)
    else Card(modifier = modifier, shape = CardShape, colors = colors, border = border, content = content)
}

/** A full-width card with inner padding; use 0 padding for a list of rows. */
@Composable
fun InfoCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    AppCard(modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(padding), content = content) }
}

/** The small uppercase label above a group of cards. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier.padding(start = 4.dp, top = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.4.sp,
    )
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = SectionLabel(text, modifier)

/** The soft brand tint behind icons and links. */
@Composable
fun accentSoft(): Color = MaterialTheme.colorScheme.primary.copy(alpha = if (LocalLmsColors.current.dark) 0.20f else 0.10f)

@Composable
fun EmptyCard(title: String, body: String) {
    InfoCard(padding = 20.dp) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(6.dp))
        Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A whole tab with nothing to list yet: a centred icon, a title and one line of explanation. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 36.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.size(14.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.size(6.dp))
        Text(text, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingCard(text: String) {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorCard(message: String, onRetry: (() -> Unit)? = null) {
    InfoCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Warning, null, Modifier.padding(top = 1.dp).size(18.dp), tint = MaterialTheme.colorScheme.error)
            Text(message, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (onRetry != null) {
            Spacer(Modifier.size(10.dp))
            OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

/**
 * A small rounded label for a status such as Present, Confirmed or Competent: coloured text on
 * a tint of the same colour. [solid] gives a filled chip for use on the dark class header.
 */
@Composable
fun StatusChip(text: String, tone: Tone = Tone.Neutral, solid: Boolean = false) {
    val colors = LocalLmsColors.current
    val scheme = MaterialTheme.colorScheme
    val (container, content) = if (solid) {
        when (tone) {
            Tone.Good -> colors.successContainer to colors.onSuccessContainer
            Tone.Bad -> scheme.errorContainer to scheme.onErrorContainer
            Tone.Info -> scheme.primaryContainer to scheme.onPrimaryContainer
            Tone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
        }
    } else {
        val tint = when (tone) {
            Tone.Good -> colors.success
            Tone.Bad -> scheme.error
            Tone.Info -> scheme.primary
            Tone.Neutral -> scheme.onSurfaceVariant
        }
        tint.copy(alpha = if (colors.dark) 0.20f else 0.12f) to tint
    }
    Text(
        text, Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 8.dp, vertical = 3.dp),
        color = content, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
    )
}

enum class Tone { Good, Bad, Info, Neutral }

/** A tappable card row with a tinted icon, a title, an optional line of detail and a chevron. */
@Composable
fun ActionRow(icon: ImageVector, title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accentSoft()), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** One row of a settings-style list inside an [InfoCard] with 0 padding. */
@Composable
fun MenuRow(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    chevron: Boolean = true,
    external: Boolean = false,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (tint == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else tint)
        Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f), fontSize = 16.sp, color = tint)
        if (external) Icon(OpenExternalIcon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        else if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun MenuDivider() {
    HorizontalDivider(Modifier.padding(start = 52.dp), color = LocalLmsColors.current.cardBorder)
}

/** One contact detail; tapping it emails, calls, opens WhatsApp or the LinkedIn profile. */
@Composable
fun ContactRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title: $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(accentSoft()), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(OpenExternalIcon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

/** A label on the left and its value on the right, for the facts on a card. */
@Composable
fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp).weight(1f))
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
}

/** The headline figure at the top of a tab (total paid, total hours), on the brand colour. */
@Composable
fun SummaryCard(title: String, value: String, caption: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LocalLmsColors.current.headerBrush).padding(18.dp).semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 38.sp)
        Text(caption, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

// ---------------------------------------------------------------- avatar

/** Profile pictures, fetched with OkHttp and kept in memory. No session token is sent: the host is a file store. */
private object Pictures {
    private val http by lazy { OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).followSslRedirects(false).build() }
    private val memory = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    suspend fun load(url: String, maxPx: Int = 256): ImageBitmap? {
        if (!isSafeLink(url)) return null
        memory.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null || body.contentLength() > 8L * 1024 * 1024) null else body.bytes()
                } ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()?.also { memory.put(url, it) }
            }.getOrNull()
        }
    }
}

/** The profile picture when the account has one, otherwise the person's initials on the brand colour. */
@Composable
fun AvatarView(pictureUrl: String?, name: String, size: Dp = 80.dp) {
    val initials = name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }
    val image by produceState<ImageBitmap?>(null, pictureUrl) { value = pictureUrl?.let { Pictures.load(it) } }
    Box(Modifier.size(size).clip(CircleShape).background(LocalLmsColors.current.brandFill), contentAlignment = Alignment.Center) {
        val picture = image
        if (picture != null) Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(initials, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
