package com.tertiaryinfotech.ailmstms.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tertiaryinfotech.ailmstms.data.isSafeLink

const val APP_NAME = "TIA TMS"
const val PRIVACY_URL = "https://lms-tms.tertiaryinfotech.com/mobile/privacy.html"
const val SUPPORT_WHATSAPP = "https://wa.me/6588666375"

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

@Composable
fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun EmptyCard(title: String, body: String) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

/** A small rounded label for a status such as Present, Confirmed or Competent. */
@Composable
fun StatusChip(text: String, tone: Tone = Tone.Neutral) {
    val colors = LocalLmsColors.current
    val (container, content) = when (tone) {
        Tone.Good -> colors.successContainer to colors.onSuccessContainer
        Tone.Bad -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Tone.Info -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        Tone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

enum class Tone { Good, Bad, Info, Neutral }

/** A tappable row with a round icon, a title, an optional line of detail and a chevron. */
@Composable
fun ActionRow(icon: ImageVector, title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
