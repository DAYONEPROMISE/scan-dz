package dz.scandz.scan

import org.junit.Assert.*
import org.junit.Test

class FiltersTest {
    @Test fun grayOfWhiteIs255() {
        assertArrayEquals(intArrayOf(255), Filters.toGray(intArrayOf(0xFFFFFFFF.toInt())))
    }

    @Test fun otsuBimodal() {
        val gray = IntArray(100) { if (it < 50) 30 else 220 }
        val t = Filters.otsuThreshold(gray)
        assertTrue("t=$t", t in 30..220)
        val bw = Filters.binarize(gray, t)
        assertTrue(bw.take(50).all { it == 0 })
        assertTrue(bw.takeLast(50).all { it == 255 })
    }

    @Test fun contrastStretchesRange() {
        val out = Filters.contrastStretch(intArrayOf(100, 120, 140))
        assertEquals(0, out.minOrNull())
        assertEquals(255, out.maxOrNull())
    }

    @Test fun pdfNameSanitized() {
        val n = buildPdfName("Facture/ Fournisseur: Alger*", 123L)
        assertTrue(n.endsWith("-123.pdf"))
        assertFalse(n.contains("/"))
        assertFalse(n.contains(":"))
    }

    @Test fun pdfNameFallback() {
        assertTrue(buildPdfName("   ", 5L).startsWith("ScanDZ-"))
    }
}
