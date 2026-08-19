# Dense urban row blocks — complete

Shipped in `4f04d47d`.

## Result

Large `DENSE_BLOCK` lots now fight like intentional close-quarters urban
spaces rather than four tiny generic shells. Qualifying 13x10-or-larger lots
become a mixed-use pair of elongated row buildings flanking a three-cell
service alley; smaller lots preserve the compact legacy fill.

Each pair contains one tenement and one market. The tenement has labeled
`APARTMENT_LIVING` and `BEDROOM` rooms; the market has `SHOP_FLOOR` and
`STOCKROOM`. A guaranteed transverse partition connects each pair of rooms,
and the public room aligns an alley-facing entrance with an opposed escape or
service exit.

Purpose-aware fixtures reuse the existing wall-oriented two-cell beds and
sofas plus shelves, crates, counters, and compact fallbacks. Generic purpose
fixture placement now reserves window-adjacent firing cells, preventing props
from silently disabling structural apertures.

The service alley receives at most two staggered, transparent cover pockets.
Placement reserves the full cross-alley station after the first prop, so
doorway avoidance can never converge both props onto one row and pinch the
route. Every station retains two walkable cells—well over the 0.6-cell marine
diameter. Selected bedroom and shop-floor facades reuse the non-traversable,
shoot-through window seam and provide directional cover to a clear interior
firing point.

## Validation

- `DenseUrbanRowFillerTest` covers both alley orientations, room purposes,
  alley-facing and opposed entrances, passage width at every station, fixtures,
  window LoS/cover, deterministic output, compact fallback, and
  representative-city visibility.
- `MapValidationScanTest` remains green across legacy, conquest, station,
  concentric, and diamond batches; every hard connectivity, deployment, and
  garrison invariant holds.
- `gradlew.bat :test :asset-pipeline:test` — full project and asset-pipeline
  suites green.
- Visual review: `build/zone-previews/buildings-dense-urban.png` clearly
  separates the legacy 12x9 block from minimum wide/tall and larger mixed-use
  rows, including the service alley, room partitions, windows, and cover.

## Follow-ons

- Dedicated fire escapes, dumpsters, market awnings, or alley clutter can
  layer onto the established grammar without changing the plan.
- Fusing rows across adjacent BSP leaves remains gated on an earlier
  footprint-planning stage that can suppress or reroute road-graph edges.
