# OpenPencil pass — scan-dz

## Design (Shared/OpenPencil/scan-dz/)
- `design.html` (dark-first capture frame + 56dp capture button + doc card, DZ dark tokens)
  → `design.fig`
- `lint --preset accessibility`: 0 errors, 0 warnings

## Code review (no rewrite needed)
Camera/add/back icons already use string-resource descriptions (`back_desc`, `add_desc`).
Dark-first UI matches the viewfinder. No code change.

## APKs
`android.yml` adds `assembleRelease` (unsigned) + release artifact + versioned
`v*-b*` GitHub Release with notes + both APKs.
