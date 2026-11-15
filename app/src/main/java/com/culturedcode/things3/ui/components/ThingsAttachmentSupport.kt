package com.culturedcode.things3.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.culturedcode.things3.R
import com.culturedcode.things3.data.repository.SyncRepository
import com.culturedcode.things3.domain.model.TaskAttachment
import com.culturedcode.things3.ui.theme.ThingsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class ProcessedAttachmentResult(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

sealed interface AttachmentImportOutcome {
    data class Success(val result: ProcessedAttachmentResult) : AttachmentImportOutcome
    data object TooLarge : AttachmentImportOutcome
    data object Error : AttachmentImportOutcome
}

private const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024 // 5 MB
private const val TARGET_INLINE_SYNC_IMAGE_BYTES = 680_000 // < 700 KB Cloud Sync inline cap
private val JPEG_COMPRESSION_STEPS = intArrayOf(82, 68, 54)

internal fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}

private fun applyExifOrientation(source: Bitmap, exifOrientation: Int): Bitmap {
    val matrix = Matrix()
    when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.preScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.preScale(-1f, 1f)
        }
        else -> return source
    }
    return runCatching {
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source && !source.isRecycled) {
            source.recycle()
        }
        rotated
    }.getOrDefault(source)
}

private fun decodeSampledByteArrayWithExif(
    rawBytes: ByteArray,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
    if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(boundsOpts, reqWidth, reqHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return null
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(rawBytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return applyExifOrientation(decoded, orientation)
}

private fun decodeSampledFileWithExif(
    file: File,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
    if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(boundsOpts, reqWidth, reqHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
    val orientation = runCatching {
        ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return applyExifOrientation(decoded, orientation)
}

suspend fun processPickedAttachmentUri(
    context: Context,
    uri: Uri
): AttachmentImportOutcome = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val rawMime = resolver.getType(uri)?.lowercase(Locale.ROOT) ?: "application/octet-stream"
        var displayName = "attachment"
        var reportedSize = -1L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                    displayName = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: displayName
                }
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    reportedSize = cursor.getLong(sizeIdx)
                }
            }
        }

        val isImage = rawMime.startsWith("image/") ||
            displayName.endsWith(".jpg", ignoreCase = true) ||
            displayName.endsWith(".jpeg", ignoreCase = true) ||
            displayName.endsWith(".png", ignoreCase = true) ||
            displayName.endsWith(".webp", ignoreCase = true)

        if (!isImage && reportedSize > MAX_ATTACHMENT_BYTES) {
            return@withContext AttachmentImportOutcome.TooLarge
        }

        val rawBytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val r = input.read(buffer)
                if (r == -1) break
                total += r
                // Allow reading up to 24 MB for camera images before sampled bounds decode, or 5 MB for non-images
                val readCap = if (isImage) 24 * 1024 * 1024 else MAX_ATTACHMENT_BYTES
                if (total > readCap) {
                    return@withContext AttachmentImportOutcome.TooLarge
                }
                out.write(buffer, 0, r)
            }
            out.toByteArray()
        } ?: return@withContext AttachmentImportOutcome.Error

        if (rawBytes.isEmpty()) return@withContext AttachmentImportOutcome.Error

        if (isImage) {
            val decoded = decodeSampledByteArrayWithExif(rawBytes, reqWidth = 1600, reqHeight = 1600)
            if (decoded != null) {
                val compressed = compressBitmapForAttachment(decoded, recycleSourceWhenDone = true)
                val baseName = displayName.substringBeforeLast('.').ifBlank { "photo" }
                return@withContext AttachmentImportOutcome.Success(
                    ProcessedAttachmentResult(
                        fileName = "$baseName.jpg",
                        mimeType = "image/jpeg",
                        bytes = compressed
                    )
                )
            }
        }

        if (rawBytes.size > MAX_ATTACHMENT_BYTES) {
            return@withContext AttachmentImportOutcome.TooLarge
        }

        AttachmentImportOutcome.Success(
            ProcessedAttachmentResult(
                fileName = displayName,
                mimeType = rawMime,
                bytes = rawBytes
            )
        )
    }.getOrElse {
        AttachmentImportOutcome.Error
    }
}

fun processCapturedPhotoBitmap(bitmap: Bitmap): ProcessedAttachmentResult {
    val bytes = compressBitmapForAttachment(bitmap, recycleSourceWhenDone = false)
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
    return ProcessedAttachmentResult(
        fileName = "Photo_$stamp.jpg",
        mimeType = "image/jpeg",
        bytes = bytes
    )
}

private fun scaleBitmapDownIfNeeded(source: Bitmap, maxEdgePx: Int): Bitmap {
    val w = source.width.coerceAtLeast(1)
    val h = source.height.coerceAtLeast(1)
    val longest = max(w, h)
    if (longest <= maxEdgePx) return source
    val ratio = maxEdgePx.toFloat() / longest.toFloat()
    val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
    val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, targetW, targetH, true)
}

private fun compressBitmapForAttachment(
    source: Bitmap,
    maxEdgePx: Int = 1440,
    recycleSourceWhenDone: Boolean = false
): ByteArray {
    val working = scaleBitmapDownIfNeeded(source, maxEdgePx)
    if (working !== source && recycleSourceWhenDone && !source.isRecycled) {
        source.recycle()
    }

    val out = ByteArrayOutputStream()
    var resultBytes = ByteArray(0)
    for (quality in JPEG_COMPRESSION_STEPS) {
        out.reset()
        working.compress(Bitmap.CompressFormat.JPEG, quality, out)
        resultBytes = out.toByteArray()
        if (resultBytes.size < TARGET_INLINE_SYNC_IMAGE_BYTES) {
            break
        }
    }

    if (resultBytes.size >= TARGET_INLINE_SYNC_IMAGE_BYTES && max(working.width, working.height) > 1152) {
        val smaller = scaleBitmapDownIfNeeded(working, 1152)
        if (smaller !== working) {
            if ((working !== source || recycleSourceWhenDone) && !working.isRecycled) {
                working.recycle()
            }
            out.reset()
            smaller.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_STEPS.last(), out)
            resultBytes = out.toByteArray()
            if (!smaller.isRecycled) {
                smaller.recycle()
            }
            return resultBytes
        }
    }

    if ((working !== source || recycleSourceWhenDone) && !working.isRecycled) {
        working.recycle()
    }
    return resultBytes
}

fun formatAttachmentSize(sizeBytes: Long): String {
    if (sizeBytes < 1024L) return "$sizeBytes B"
    val kb = sizeBytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.ROOT, "%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.ROOT, "%.1f MB", mb)
}

fun openAttachmentExternally(context: Context, attachment: TaskAttachment) {
    runCatching {
        val file = SyncRepository.resolveVerifiedAttachmentFile(context.filesDir, attachment.localRelativePath)
        if (!file.exists() || !file.isFile) return
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, attachment.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(viewIntent, attachment.fileName).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

@Composable
fun TaskAttachmentsStrip(
    attachments: List<TaskAttachment>,
    onDeleteAttachment: (String) -> Unit,
    onAttachFileClick: (() -> Unit)? = null,
    onTakePhotoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty() && onAttachFileClick == null && onTakePhotoClick == null) return

    val colors = ThingsTheme.colors
    var previewAttachment by remember { mutableStateOf<TaskAttachment?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (attachments.isNotEmpty()) {
                Text(
                    text = "${stringResource(R.string.attachments_section_title)} (${attachments.size})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.secondaryText
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onAttachFileClick != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.tagPillBg)
                            .clickable(onClick = onAttachFileClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = stringResource(R.string.action_attach_file),
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.action_attach_file),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.thingsBlue
                        )
                    }
                }
                if (onTakePhotoClick != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.tagPillBg)
                            .clickable(onClick = onTakePhotoClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = stringResource(R.string.action_take_photo),
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.action_take_photo),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.thingsBlue
                        )
                    }
                }
            }
        }

        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                attachments.forEach { attachment ->
                    AttachmentThumbnailCard(
                        attachment = attachment,
                        onClick = { previewAttachment = attachment },
                        onDelete = { onDeleteAttachment(attachment.id) }
                    )
                }
            }
        }
    }

    val currentPreview = previewAttachment
    if (currentPreview != null) {
        AttachmentFullScreenPreviewDialog(
            attachment = currentPreview,
            onDismiss = { previewAttachment = null },
            onDelete = {
                onDeleteAttachment(currentPreview.id)
                previewAttachment = null
            }
        )
    }
}

@Composable
private fun AttachmentThumbnailCard(
    attachment: TaskAttachment,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val colors = ThingsTheme.colors
    var thumbnailBitmap by remember(attachment.id, attachment.localRelativePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(attachment.id, attachment.localRelativePath) {
        if (attachment.isImage) {
            thumbnailBitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val file = SyncRepository.resolveVerifiedAttachmentFile(
                        context.filesDir,
                        attachment.localRelativePath
                    )
                    if (!file.exists() || !file.isFile) return@runCatching null
                    decodeSampledFileWithExif(file, reqWidth = 160, reqHeight = 160)?.asImageBitmap()
                }.getOrNull()
            }
        } else {
            thumbnailBitmap = null
        }
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.tagPillBg.copy(alpha = 0.7f))
            .border(0.5.dp, colors.secondaryText.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(colors.thingsBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbnailBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = attachment.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (attachment.isImage) Icons.Outlined.Image else Icons.Outlined.Description,
                    contentDescription = attachment.fileName,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.width(116.dp)) {
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${if (attachment.isPdf) "PDF • " else ""}${formatAttachmentSize(attachment.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText,
                maxLines = 1
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.attachment_remove),
                tint = colors.secondaryText,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AttachmentFullScreenPreviewDialog(
    attachment: TaskAttachment,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val colors = ThingsTheme.colors
    var fullImageBitmap by remember(attachment.id) { mutableStateOf<ImageBitmap?>(null) }
    var pdfPageCount by remember(attachment.id) { mutableIntStateOf(0) }
    var currentPdfPage by remember(attachment.id) { mutableIntStateOf(0) }
    var pdfPageBitmap by remember(attachment.id) { mutableStateOf<ImageBitmap?>(null) }
    val heldImageRawRef = remember(attachment.id) { arrayOfNulls<Bitmap>(1) }
    val heldPdfPageRawRef = remember(attachment.id) { arrayOfNulls<Bitmap>(1) }
    var scale by remember(attachment.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(attachment.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(attachment.id) { mutableFloatStateOf(0f) }

    DisposableEffect(attachment.id) {
        onDispose {
            fullImageBitmap = null
            pdfPageBitmap = null
            heldImageRawRef[0]?.let { bmp ->
                heldImageRawRef[0] = null
                if (!bmp.isRecycled) runCatching { bmp.recycle() }
            }
            heldPdfPageRawRef[0]?.let { bmp ->
                heldPdfPageRawRef[0] = null
                if (!bmp.isRecycled) runCatching { bmp.recycle() }
            }
        }
    }

    LaunchedEffect(attachment.id, currentPdfPage) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f

        if (attachment.isImage) {
            if (fullImageBitmap == null) {
                val decoded = withContext(Dispatchers.IO) {
                    runCatching {
                        val file = SyncRepository.resolveVerifiedAttachmentFile(
                            context.filesDir,
                            attachment.localRelativePath
                        )
                        if (!file.exists() || !file.isFile) return@runCatching null
                        decodeSampledFileWithExif(file, reqWidth = 2048, reqHeight = 2048)
                    }.getOrNull()
                }
                if (decoded != null) {
                    val prev = heldImageRawRef[0]
                    heldImageRawRef[0] = decoded
                    fullImageBitmap = decoded.asImageBitmap()
                    if (prev != null && prev !== decoded && !prev.isRecycled) {
                        withFrameNanos { }
                        runCatching { prev.recycle() }
                    }
                }
            }
        } else if (attachment.isPdf) {
            val renderedPair = withContext(Dispatchers.IO) {
                runCatching {
                    val file = SyncRepository.resolveVerifiedAttachmentFile(
                        context.filesDir,
                        attachment.localRelativePath
                    )
                    if (!file.exists() || !file.isFile) return@runCatching null
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            val count = renderer.pageCount
                            if (count <= 0) return@runCatching count to null
                            val pageIdx = currentPdfPage.coerceIn(0, count - 1)
                            val bmp = renderer.openPage(pageIdx).use { page ->
                                val renderW = (page.width * 2).coerceIn(400, 1800)
                                val renderH = (page.height * 2).coerceIn(400, 2400)
                                Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888).also { target ->
                                    target.eraseColor(android.graphics.Color.WHITE)
                                    page.render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                            count to bmp
                        }
                    }
                }.getOrNull()
            }
            if (renderedPair != null) {
                pdfPageCount = renderedPair.first
                val newBmp = renderedPair.second
                if (newBmp != null) {
                    val prevBmp = heldPdfPageRawRef[0]
                    heldPdfPageRawRef[0] = newBmp
                    pdfPageBitmap = newBmp.asImageBitmap()
                    if (prevBmp != null && prevBmp !== newBmp && !prevBmp.isRecycled) {
                        withFrameNanos { }
                        runCatching { prevBmp.recycle() }
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            color = colors.cardSurface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.fileName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatAttachmentSize(attachment.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { openAttachmentExternally(context, attachment) }) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInNew,
                                contentDescription = stringResource(R.string.attachment_open_external),
                                tint = colors.thingsBlue
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.attachment_remove),
                                tint = colors.deadlineUrgentRed
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = colors.secondaryText
                            )
                        }
                    }
                }

                // Preview Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.05f))
                        .pointerInput(attachment.id, currentPdfPage) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = newScale
                                if (newScale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val img = fullImageBitmap ?: pdfPageBitmap
                    if (img != null) {
                        Image(
                            bitmap = img,
                            contentDescription = attachment.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = attachment.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primaryText
                            )
                            TextButton(onClick = { openAttachmentExternally(context, attachment) }) {
                                Text(text = stringResource(R.string.attachment_open_external))
                            }
                        }
                    }
                }

                // PDF Pagination Footer
                if (attachment.isPdf && pdfPageCount > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { if (currentPdfPage > 0) currentPdfPage-- },
                            enabled = currentPdfPage > 0
                        ) {
                            Text("←")
                        }
                        Text(
                            text = "${currentPdfPage + 1} / $pdfPageCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.secondaryText
                        )
                        TextButton(
                            onClick = { if (currentPdfPage < pdfPageCount - 1) currentPdfPage++ },
                            enabled = currentPdfPage < pdfPageCount - 1
                        ) {
                            Text("→")
                        }
                    }
                }
            }
        }
    }
}
