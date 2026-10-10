package dz.scandz.scan

/** 2D point in image pixels. Clean-room implementation, no third-party code. */
data class Pt(val x: Double, val y: Double)

/**
 * Solve 3x3 homography mapping src[4] -> dst[4] (DLT, Gaussian elimination).
 * Returns row-major 9 coefficients, h[8] normalized to 1.
 */
fun solveHomography(src: List<Pt>, dst: List<Pt>): DoubleArray {
    require(src.size == 4 && dst.size == 4)
    // 8x8 system A*h = b for h0..h7, h8 = 1
    val a = Array(8) { DoubleArray(8) }
    val b = DoubleArray(8)
    for (i in 0 until 4) {
        val (x, y) = src[i]
        val (u, v) = dst[i]
        a[2 * i][0] = x; a[2 * i][1] = y; a[2 * i][2] = 1.0
        a[2 * i][6] = -u * x; a[2 * i][7] = -u * y
        b[2 * i] = u
        a[2 * i + 1][3] = x; a[2 * i + 1][4] = y; a[2 * i + 1][5] = 1.0
        a[2 * i + 1][6] = -v * x; a[2 * i + 1][7] = -v * y
        b[2 * i + 1] = v
    }
    val h = gaussianSolve(a, b)
    return doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0)
}

fun applyHomography(h: DoubleArray, p: Pt): Pt {
    val w = h[6] * p.x + h[7] * p.y + h[8]
    return Pt(
        (h[0] * p.x + h[1] * p.y + h[2]) / w,
        (h[3] * p.x + h[4] * p.y + h[5]) / w
    )
}

fun invertHomography(h: DoubleArray): DoubleArray {
    val a = h[0]; val b = h[1]; val c = h[2]
    val d = h[3]; val e = h[4]; val f = h[5]
    val g = h[6]; val k = h[7]; val m = h[8]
    val A = e * m - f * k; val B = -(d * m - f * g); val C = d * k - e * g
    val det = a * A + b * B + c * C
    require(kotlin.math.abs(det) > 1e-12) { "Singular homography" }
    return doubleArrayOf(
        A / det, (c * k - b * m) / det, (b * f - c * e) / det,
        B / det, (a * m - c * g) / det, (c * d - a * f) / det,
        C / det, (b * g - a * k) / det, (a * e - b * d) / det
    )
}

/**
 * Perspective warp of a grayscale image into outW x outH using quad->rect mapping.
 * Bilinear sampling, pure Kotlin (no native deps).
 */
fun warpGrayscale(
    src: IntArray, srcW: Int, srcH: Int,
    quad: List<Pt>, outW: Int, outH: Int
): IntArray {
    val dst = listOf(Pt(0.0, 0.0), Pt(outW - 1.0, 0.0), Pt(outW - 1.0, outH - 1.0), Pt(0.0, outH - 1.0))
    val h = invertHomography(solveHomography(quad, dst))
    val out = IntArray(outW * outH)
    for (oy in 0 until outH) {
        for (ox in 0 until outW) {
            val p = applyHomography(h, Pt(ox.toDouble(), oy.toDouble()))
            out[oy * outW + ox] = bilinear(src, srcW, srcH, p.x, p.y)
        }
    }
    return out
}

private fun bilinear(src: IntArray, w: Int, h: Int, x: Double, y: Double): Int {
    val xc = x.coerceIn(0.0, (w - 1).toDouble())
    val yc = y.coerceIn(0.0, (h - 1).toDouble())
    val x0 = xc.toInt().coerceAtMost(w - 2); val y0 = yc.toInt().coerceAtMost(h - 2)
    // edge case 1px images
    if (w < 2 || h < 2) return src[(yc.toInt().coerceIn(0, h - 1)) * w + xc.toInt().coerceIn(0, w - 1)]
    val fx = (xc - x0).coerceIn(0.0, 1.0); val fy = (yc - y0).coerceIn(0.0, 1.0)
    val v00 = src[y0 * w + x0].toDouble(); val v10 = src[y0 * w + x0 + 1].toDouble()
    val v01 = src[(y0 + 1) * w + x0].toDouble(); val v11 = src[(y0 + 1) * w + x0 + 1].toDouble()
    return ((v00 * (1 - fx) + v10 * fx) * (1 - fy) + (v01 * (1 - fx) + v11 * fx) * fy).toInt().coerceIn(0, 255)
}

private fun gaussianSolve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
    for (col in 0 until n) {
        var piv = col
        for (r in col + 1 until n) if (kotlin.math.abs(m[r][col]) > kotlin.math.abs(m[piv][col])) piv = r
        val tmp = m[col]; m[col] = m[piv]; m[piv] = tmp
        require(kotlin.math.abs(m[col][col]) > 1e-12) { "Singular system" }
        for (r in 0 until n) {
            if (r == col) continue
            val f = m[r][col] / m[col][col]
            for (c in col until n + 1) m[r][c] -= f * m[col][c]
        }
    }
    return DoubleArray(n) { i -> m[i][n] / m[i][i] }
}

/** Default quad = full frame inset by margin fraction. */
fun defaultQuad(w: Int, h: Int, margin: Double = 0.08): List<Pt> = listOf(
    Pt(w * margin, h * margin),
    Pt(w * (1 - margin), h * margin),
    Pt(w * (1 - margin), h * (1 - margin)),
    Pt(w * margin, h * (1 - margin))
)
