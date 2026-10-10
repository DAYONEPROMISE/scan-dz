package dz.scandz.scan

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GeometryTest {
    @Test fun identityHomography() {
        val q = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0), Pt(0.0, 10.0))
        val h = solveHomography(q, q)
        for (p in q + listOf(Pt(3.0, 7.0))) {
            val m = applyHomography(h, p)
            assertTrue(abs(m.x - p.x) < 1e-6)
            assertTrue(abs(m.y - p.y) < 1e-6)
        }
    }

    @Test fun scaleHomography() {
        val src = listOf(Pt(0.0, 0.0), Pt(100.0, 0.0), Pt(100.0, 50.0), Pt(0.0, 50.0))
        val dst = listOf(Pt(0.0, 0.0), Pt(200.0, 0.0), Pt(200.0, 100.0), Pt(0.0, 100.0))
        val h = solveHomography(src, dst)
        val m = applyHomography(h, Pt(25.0, 10.0))
        assertTrue(abs(m.x - 50.0) < 1e-6)
        assertTrue(abs(m.y - 20.0) < 1e-6)
    }

    @Test fun warpUniformStaysUniform() {
        val w = 20; val h = 10
        val gray = IntArray(w * h) { 128 }
        val quad = defaultQuad(w, h, 0.0)
        val out = warpGrayscale(gray, w, h, quad, 20, 10)
        assertTrue(out.all { abs(it - 128) <= 1 })
    }

    @Test fun invertRoundtrip() {
        val src = listOf(Pt(10.0, 20.0), Pt(300.0, 15.0), Pt(310.0, 400.0), Pt(5.0, 410.0))
        val dst = listOf(Pt(0.0, 0.0), Pt(200.0, 0.0), Pt(200.0, 300.0), Pt(0.0, 300.0))
        val h = solveHomography(src, dst)
        val inv = invertHomography(h)
        val p = Pt(150.0, 200.0)
        val back = applyHomography(inv, applyHomography(h, p))
        assertTrue(abs(back.x - p.x) < 1e-6)
        assertTrue(abs(back.y - p.y) < 1e-6)
    }
}
