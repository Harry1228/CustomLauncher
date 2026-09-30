package com.harry.launcher.data.graphics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

class IconNormalizer(context: Context) {

    // Resolves target pixel size based on screen density (48dp standard launcher grid cell)
    val targetSizePx: Int = (48 * context.resources.displayMetrics.density).toInt().coerceIn(144, 256)

    fun normalize(drawable: Drawable): ImageBitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val original = drawable.bitmap
            if (original.width == targetSizePx && original.height == targetSizePx && !original.isRecycled) {
                return original.asImageBitmap()
            }
        }

        val bitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawFilter = PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        drawable.setBounds(0, 0, targetSizePx, targetSizePx)
        drawable.draw(canvas)

        return bitmap.asImageBitmap()
    }
}
