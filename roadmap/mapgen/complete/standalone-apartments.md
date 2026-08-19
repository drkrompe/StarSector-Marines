# Standalone apartment buildings and firing windows — shipped

**Code:** `d4595b97`

## Player-visible result

Ordinary residential lots at least 12x10 cells now reuse the apartment planner
previously limited to gated-housing compounds. Their strongest adjacent road
edge becomes the public frontage, producing a lobby, clear two-cell common
hall, two living rooms, two bedrooms, a street-facing entrance, and an aligned
rear exit. Smaller lots retain the compact `HOME` layout.

Apartment facades now also carry readable cyan firing windows aligned only
with living rooms and bedrooms. Each window:

- remains a non-traversable structural wall cell;
- permits sight and projectiles in both directions;
- supplies normal directional wall cover to the adjacent room cell;
- reserves that interior firing position from beds and sofas; and
- disappears cleanly when its wall is breached into rubble.

The window treatment applies to both standalone and courtyard apartment blocks.
It is deliberately an aperture rather than a separate breakable-glass entity;
detonations can still destroy the containing wall through the existing wall-HP
path.

## Structural seams

- `BuildingResidentialFiller` selects the apartment config only for 12x10+
  lots and scores direct `ROAD_CELLS` exposure for frontage. Equal/no-road
  cases face toward the map interior.
- `BuildingShellCore` stamps one window per contiguous private-room run on an
  exterior facade. Lobby and common-hall walls remain solid.
- `CellTopology.Tag.WINDOW` distinguishes an aperture from an ordinary wall
  without changing its structural status.
- `NavigationGrid.SEE_THROUGH` supplies the existing two-way LoS/projectile
  behavior, while non-walkability continues to drive directional cover baking.
- `GroundRenderSystem` overlays a compact directional frame/glass slit on the
  existing wall art; the sprite preview mirrors the same treatment.
- Purpose-aware fixture placement excludes every cell immediately inside a
  window, preserving an actual firing position rather than a cosmetic opening.

## Agent sizing

The reused common hall remains two cells wide. With
`UnitType.MARINE.radius == 0.3` cells, that exceeds the 1.2-cell combined
diameter of two marines and preserves the established two-abreast circulation
contract.

## Verification

- `StandaloneApartmentFillerTest` covers all four street frontages, opposed
  entrances, the four private room components, two-cell hall sizing,
  non-traversable/two-way-fire window semantics, directional cover, clear
  firing cells, compact-home fallback, and 80-seed standalone prevalence.
- `GatedHousingFillerTest` remains green with windows added to compound
  apartments and all four oriented two-cell furniture pieces retained.
- The BSP/map-validation batch retains one connected cell/edge component and
  valid deployment across legacy, conquest, and all station recipes.
- The sprite preview was regenerated and inspected with the directional cyan
  slit overlay.
- Full root suite: 1,768 tests green. Asset-pipeline suite green.

## Deliberate next boundary

The next fresh ground-structure identity slice is a medical/public-service
campus: reception/triage, treatment and ward rooms, storage, ambulance access,
and specialized fixtures. Reusing windows on other building families should be
done alongside their room-purpose plan so apertures preserve real firing cells.
