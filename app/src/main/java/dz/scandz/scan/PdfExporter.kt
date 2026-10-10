package dz.scandz.scan

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/** Multi-page PDF writer using the built-in android.graphics.pdf.PdfDocument (no deps, offline). */
object PdfExporter {
    fun export(bitmaps: List<Bitmap>, outFile: File, dpi: Int = 200) {
        require(bitmaps.isNotEmpty())
        val doc = PdfDocument()
        try {
            for (bmp in bitmaps) {
                // A4 ratio page sized from bitmap aspect at ~dpi scale
                val w = bmp.width; val h = bmp.height
                val pageW = (w * 72.0 / dpi).toInt().coerceAtLeast(72)
                val pageH = (h * 72.0 / dpi).toInt().coerceAtLeast(72)
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
                val page = doc.startPage(info)
                page.canvas.drawBitmap(bmp, null, android.graphics.Rect(0, 0, pageW, pageH), null)
                doc.finishPage(page)
            }
            FileOutputStream(outFile).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        require(outFile.length() > 0) { "Empty PDF" }
    }
}
