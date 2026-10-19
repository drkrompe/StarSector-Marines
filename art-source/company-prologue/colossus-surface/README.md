# Colossus: surfacing

Status: IN REVIEW (pass 6 round 1 posted 2026-10-19).
Written: 2026-10-19

This is the Colossus's folder for the shared surfacing harness
(`../second-wind-surface/`, run with `--hull`; its README describes the method).
Inputs and Codex generations live here. Textures, grading, review renders and
builds are script outputs and are not committed. `./rebuild.sh` replays
everything except the Codex calls.

## Livery

The livery is the Domain-era heavy freighter's, read off `colossus.png`, mapped
by `surface_hull.py` to these classes:

- dusty oxide-red enamel on the central hold, its hatch cover, the aft machinery
  body and the bridge deck;
- darker maroon on the four side cargo modules and the drive pods, which also
  have a lighter, worn aft deck plate (`red_w`);
- pale weathered grey on the longerons, the bridge deckhouse, the pods'
  shoulder bands and the underslung tanks;
- thin burnt-orange and faded-blue deck bands;
- gunmetal machinery, near-black recesses, and ceramic heat-shield tiles.

These keep their own materials:

- The cargo containers: seven liveries with their own door geometry. A class
  swatch would flatten them into one paint.
- The bridge glazing, lamps, drive bell liners, nozzle lips and fender rubber.

Wear runs the `service` row of `wear.json`: an old hauler that is still
maintained, dusty from planetside lifts, with soot at six drives. The owned
wear colours in `palette.json` are:

- a grey-brown primer;
- an ochre landing-field dust;
- a chalk that fades the oxide paler and pinker-grey rather than white.

- **Finish reference** (`reference/finish-reference.png`): one Codex reskin of
  the clean build's three-quarter camera, with the sprite as the colour key,
  registered at IoU 0.996.
  - `palette.py` reads the classes off it.
  - The spreads are owned in `derive_palette`, and so is pale's mean L. Pale is
    set at its median, because the shadowed longeron end faces drag the mean
    down.
  - `red_w` and `tile` are geometry offsets from red and dark.
- **Swatches** (`generated/sheet-swatch.png`, v2): one Codex sheet of red,
  maroon, pale, orange, blue and gunmetal, tiled at 12 hull units (6 m).
  - Dark and tile regrade from gunmetal by a shift.
  - v1 tried to derive blue from pale. The harness scales chroma about neutral,
    so a warm square cannot become blue, which is why blue is painted on the
    sheet.

## Parts

There are five groups, ten Codex sheets and one single-tile retry: 36 generated
sources and 25 free reuses, 61 parts in all. Every starboard twin is a mirrored
port source; the hull is symmetric about Y = 1.5. Every tile fitted at IoU
0.985 to 1.000, and no class swap was reported at grading. The whole hull took
14 Codex calls: the finish reference, two swatch sheets and eleven part sheets.

| Group | Sheets | Sources | Notes |
|---|---|---|---|
| `hold` | `hold-deck`, `hold-keel` | hatch cover aft leaves (inboard and outboard rows), fore leaf with crane plate, bow door and armour, aft and fore keel, aft face, aft deck | |
| `modules` | `modules-wall`, `modules-deck` | outboard walls cut at the Z -11 groove into upper and lower bands (fore and aft), decks, keels round the lift wells | |
| `spine` | `spine-longeron`, `spine-aft` | longeron flange top, outboard and inboard webs, keel-spine top, aft body deck (two), stern face, underside | |
| `bridge` | `bridge` | roof, port wall, forward face, aft face | |
| `pods` | `pods-top`, `pods-side`, `pods-flank1-v2` | casing top in five runs cut at the plate joints, two outboard flank runs, belly | `pods-flank1-v2` is the retry: `pods-side` painted the worn aft plate's edge pale grey (5% in family), and v2 is 98% in family |

Faces that are too large to tile densely, and stay below the 15 texels/unit
target:

- the longeron webs and flange (6 to 6.3): each is one polygon over 100 units
  long, mostly hidden by the modules and the hold;
- the hold's aft face (6.7), the aft body's underside (7.3) and the pod bellies
  (7.4);
- the stern face, the decks and the hatch cover's fore leaf (9.8 to 11.7).

Everything else is 13.7 to 23.3. Splitting the longeron polygons in the
geometry would let box seeds cut them.

## Condition states (pass 5)

Deferred, as on the Shepherd: texture first, and add states when a cinematic
needs them.
