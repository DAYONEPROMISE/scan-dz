package dz.scandz.scan

/** Pure-Kotlin grayscale / enhancement filters operating on 0..255 arrays. */
object Filters {
    fun toGray(argb: IntArray): IntArray = IntArray(argb.size) { i ->
        val c = argb[i]
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt()
    }

    fun contrastStretch(gray: IntArray): IntArray {
        var mn = 255; var mx = 0
        for (v in gray) { if (v < mn) mn = v; if (v > mx) mx = v }
        if (mx <= mn) return gray.copyOf()
        val scale = 255.0 / (mx - mn)
        return IntArray(gray.size) { (((gray[it] - mn) * scale).toInt().coerceIn(0, 255)) }
    }

    fun otsuThreshold(gray: IntArray): Int {
        val hist = IntArray(256)
        for (v in gray) hist[v]++
        val total = gray.size.toDouble()
        var sum = 0.0
        for (t in 0..255) sum += t * hist[t]
        var sumB = 0.0; var wB = 0.0; var best = 0.0; var thresh = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += t * hist[t]
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; thresh = t }
        }
        return thresh
    }

    fun binarize(gray: IntArray, thresh: Int): IntArray =
        IntArray(gray.size) { if (gray[it] > thresh) 255 else 0 }

    fun grayToArgb(gray: IntArray): IntArray = IntArray(gray.size) { i ->
        val v = gray[i]
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
}

enum class ScanFilter { ORIGINAL, GRAY, BW }

fun buildPdfName(title: String, ts: Long): String {
    val safe = title.trim().ifEmpty { "ScanDZ" }.replace(Regex("[^A-Za-z0-9-_àâäéèêëîïôöùûüçÀÂÄÉÈÊËÎÏÔÖÙÛÜÇ\\u0600-\\u06FF ]"), "").trim().replace(Regex("\\s+"), "-")
    return "${safe.ifEmpty { "ScanDZ" }}-$ts.pdf"
}
