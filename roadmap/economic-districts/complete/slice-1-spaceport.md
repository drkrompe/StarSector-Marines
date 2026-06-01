# Slice 1 — Spaceport apron (shipped)

The first economy-reflective district: the spaceport apron, an **open killing
ground**. Wide walkable tarmac, only sparse isolated hard cover, long
sightlines — the tactical opposite of downtown CQB and the natural home of the
defense-intensity overwatch line. The cheapest "new" district (it sharpens the
existing port path rather than inventing terrain treatment), so it's the proving
run for the slice-0 substrate: a content drop onto a live pipe.

## What landed

- **`BlockKind.SPACEPORT_PAD`** — new leaf kind.
- **`SpaceportFiller`** (`bsp/fill/`) — the open killing ground:
  - whole leaf painted walkable `STRIPED` apron;
  - **sparse isolated hard-cover islands** — 1-cell non-walkable cargo blocks,
    budget `area / 42` clamped `[2, 8]`, kept off the perimeter, off the tower,
    and never cardinally adjacent to each other. **Tactical identity is real
    nav structure** (the economic-districts rule): cover is *derived* from these
    walls at the finalize bake (`recomputeCoverAt`), and `seedWallHp` makes them
    destructible cargo — not pure-visual doodads;
  - a single hardened **control-tower** hardpoint in a corner (3×3 shell, one
    inward-facing doorway, `COMMS` POI) — the apron's tactical anchor. Skipped on
    leaves below 7×7;
  - placeholder doodads from `SKYPORT_DOODADS` (bespoke pad gear is the slice-5
    art pass — see the overview's doodad palette).
- **Wiring:** registered in `BspCityGenerator`; added to the `HARBOR_PORT` theme
  (weight 18) so the conquest **port band** — structurally the harbor/spaceport
  zone — gains the open apron on every conquest map. This is "sharpen the
  existing port path" made literal.

## Why the port band, not the CITY band

`EconomicZoning.dominantTheme` still maps `SPACEPORT` → no theme: a spaceport is
on nearly every market, so routing the CITY band on it would turn every city
core into tarmac. The apron belongs in the PORT band (its geographic home),
which is structural to conquest maps. **Follow-up:** scale apron presence /
size off `spaceportTier` (megaport → bigger/more pads) — needs dynamic theme
weighting, deferred.

## Invariants held

- `SpaceportFillerTest`: apron ≥ 85% walkable; hard cover produces real cover at
  the bake; exactly one `COMMS` control-tower POI (interior reachable); small
  leaves skip the tower but still field cover; **apron stays one connected
  region across a seed sweep** (cover + tower never partition it); `HARBOR_PORT`
  rolls `SPACEPORT_PAD` (selection wired).
- `MapValidationScanTest` `scanConquestBatch` green — whole-map connectivity /
  garrison-deployability holds with aprons present.
- `OverwatchTowerStageTest` / `EconomicZoningTest` / taxonomy suites green.

## Follow-ups

- `spaceportTier`-scaled apron density (the economy-reflective intensity knob).
- Bespoke apron doodads (slice 5): bowsers, loaders, light masts.
