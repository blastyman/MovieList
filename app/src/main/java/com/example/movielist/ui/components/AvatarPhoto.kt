package com.example.movielist.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a picked picture into what the server wants for a profile picture or group icon:
 * upright, cropped to the centre square, at most [SIZE] pixels wide, as a JPEG. A 12-megapixel
 * photo becomes a few dozen kilobytes.
 * Returns null if the picture can't be read.
 */
suspend fun avatarPhotoJpeg(context: Context, uri: Uri): ByteArray? =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            // Read only the size first, so a huge photo is decoded at a fraction of its size.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val shortSide = minOf(bounds.outWidth, bounds.outHeight)
            if (shortSide <= 0) return@runCatching null
            var sample = 1
            while (shortSide / (sample * 2) >= SIZE) sample *= 2

            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { rotationOf(ExifInterface(it)) } ?: 0f

            val side = minOf(decoded.width, decoded.height)
            val scale = minOf(1f, SIZE.toFloat() / side)
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation)
            }
            val square = Bitmap.createBitmap(
                decoded,
                (decoded.width - side) / 2,
                (decoded.height - side) / 2,
                side,
                side,
                matrix,
                true,
            )
            ByteArrayOutputStream().use { out ->
                square.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                out.toByteArray()
            }
        }.getOrNull()
    }

/** Phones store photos sideways and say which way is up in the EXIF data. */
private fun rotationOf(exif: ExifInterface): Float =
    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

private const val SIZE = 512
private const val QUALITY = 85
