# ScanDZ — scanner de documents 100% hors-ligne (ماسح ضوئي يعمل دون إنترنت)

Photo → correction de perspective (4 coins) → filtres → PDF multi-pages, sur l'appareil uniquement.

- Permissions: CAMERA seulement. Pas d'INTERNET.
- Stockage: Room (registre) + fichiers internes + FileProvider pour partage.
- PDF: `android.graphics.pdf.PdfDocument` intégré (aucune dépendance).
- Perspective: homographie DLT + ré-échantillonnage bilinéaire en Kotlin pur (clean-room, MIT).
- UI: Jetpack Compose + Material 3, français / arabe (RTL).

## Limites connues
- Cadrage manuel par curseurs (pas de détection auto des bords) — v2.
- Pas d'OCR. Une page = une image PDF (non interrogeable).
- Nom du document saisi avant capture; pages ajoutées = 1 par scan (multi-pages via exports successifs — v2: file d'attente de pages).

## Coûts
- 0 $ récurrent: aucune API, aucun cloud, aucun compte. Build via GitHub Actions (minutes gratuites).

## Tests
`./gradlew :app:testDebugUnitTest` — homographie, warp, Otsu, nommage PDF.
