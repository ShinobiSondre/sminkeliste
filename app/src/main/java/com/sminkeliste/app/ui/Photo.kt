package com.sminkeliste.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/** A photo picked for the AI: shown in the app, and sent as a small JPEG. */
class Photo(val bitmap: Bitmap, val jpegBase64: String)

object Photos {
    /** Where the camera saves its picture (shared with the camera app through the FileProvider). */
    fun cameraUri(ctx: Context): Uri {
        val f = File(File(ctx.cacheDir, "photos").apply { mkdirs() }, "camera.jpg")
        return androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    }

    /** Reads, turns upright and shrinks a photo to at most 1024 px, so it uploads quickly. */
    fun load(ctx: Context, uri: Uri): Photo? = runCatching {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1024 || bounds.outHeight / (sample * 2) >= 1024) sample *= 2
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return@runCatching null
        val rotate = runCatching {
            cr.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        val scale = minOf(1f, 1024f / maxOf(raw.width, raw.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotate) }
        val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        Photo(bmp, Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }.getOrNull()
}
