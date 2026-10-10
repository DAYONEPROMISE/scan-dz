package dz.scandz.scan

import android.graphics.Bitmap
import dz.scandz.scan.Filters
import dz.scandz.scan.Pt
import dz.scandz.scan.warpGrayscale

object Bitmaps {
    fun argbOf(bmp: Bitmap): IntArray {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        return px
    }

    fun grayOf(bmp: Bitmap): IntArray = Filters.toGray(argbOf(bmp))

    fun fromGray(gray: IntArray, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(Filters.grayToArgb(gray), 0, w, 0, 0, w, h)
        return bmp
    }

    /** Full pipeline: warp quad->rect, then apply filter. */
    fun scanPage(src: Bitmap, quad: List<Pt>, outW: Int, outH: Int, filter: ScanFilter): Bitmap {
        val gray = grayOf(src)
        val warped = warpGrayscale(gray, src.width, src.height, quad, outW, outH)
        return when (filter) {
            ScanFilter.ORIGINAL -> {
                // warp original colors via nearest through same geometry: reuse gray warp of each channel
                val argb = argbOf(src)
                val r = IntArray(argb.size) { (argb[it] shr 16) and 0xFF }
                val g = IntArray(argb.size) { (argb[it] shr 8) and 0xFF }
                val b = IntArray(argb.size) { argb[it] and 0xFF }
                val rw = warpGrayscale(r, src.width, src.height, quad, outW, outH)
                val gw = warpGrayscale(g, src.width, src.height, quad, outW, outH)
                val bw = warpGrayscale(b, src.width, src.height, quad, outW, outH)
                val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val px = IntArray(outW * outH) { i -> (0xFF shl 24) or (rw[i] shl 16) or (gw[i] shl 8) or bw[i] }
                out.setPixels(px, 0, outW, 0, 0, outW, outH)
                out
            }
            ScanFilter.GRAY -> fromGray(Filters.contrastStretch(warped), outW, outH)
            ScanFilter.BW -> {
                val stretched = Filters.contrastStretch(warped)
                fromGray(Filters.binarize(stretched, Filters.otsuThreshold(stretched)), outW, outH)
            }
        }
    }
}
