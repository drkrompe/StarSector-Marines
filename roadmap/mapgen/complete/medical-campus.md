# Medical campus — complete

Shipped in `adf608b4`.

## Result

Ground-city generation now has a rare, large-lot `MEDICAL_CAMPUS` compound in
civic and mixed districts. A successful claim owns exactly three BSP parcels:

- the qualifying seed is a medical clinic with an apron-facing public entrance
  and opposed service exit;
- the larger neighbor is a medical supplies/support building facing shared
  circulation; and
- the remaining parcel is an open striped ambulance court with three edge
  cover points and a broad clear maneuver lane.

The clinic is a purpose-built infantry plan rather than a civic reskin. A clear
two-cell `MEDICAL_CORRIDOR` connects reception to the rear exit, with directly
accessible `TRIAGE`, `TREATMENT_ROOM`, `PATIENT_WARD`, and `PHARMACY` rooms.
Existing coherent props become medical fixtures through purpose-aware
placement: reception desk, workstation bank, opaque treatment console,
wall-oriented two-cell patient bed, and pharmacy shelves. Fixture placement
preserves each room's remaining connectivity.

Treatment and ward facade runs reuse the structural-window seam. Their windows
stay non-walkable, pass sight and projectiles, provide directional wall cover,
and retain a clear interior firing position. Reception and the circulation
spine do not receive apertures.

Shared BSP road shoulders repaint as striped ambulance apron only when they are
not part of the vehicle reservation. The reserved centerline remains walkable,
unfurnished `STREET`. Claims that cannot reach three parcels demote to a large
civic building; undersized rolls demote directly to commercial so they cannot
bypass the civic headquarters' own size guard.

## Validation

- `gradlew.bat :test` — 1,795-test merged root suite green.
- `gradlew.bat :asset-pipeline:test` — green.
- `MedicalCampusFillerTest` covers all four clinic frontages, room purposes,
  medical fixtures, opposed entrances, windows/cover/LoS, exact three-parcel
  claims, failed-claim demotion, ambulance-court clearance, reserved roads, and
  representative-city visibility.
- `MapValidationScanTest` — all hard connectivity, reachability, and garrison
  invariants held for the legacy, conquest, station, concentric, and diamond
  batches.
- `BspMapPreviewTest` — green; compound overlay uses the `MED` glyph.
- Visual review: `build/zone-previews/medical-campus.png` cleanly separates the
  clinic, support wing, and open ambulance court.

## Follow-ons

- Dedicated stretcher, scanner, diagnostic-bed, and ambulance art can layer
  onto the medical room-purpose vocabulary without changing the floor plan.
- A truly fused multi-lot hospital remains gated on a footprint-planning stage
  that can suppress or re-route BSP road edges before road-graph publication.
