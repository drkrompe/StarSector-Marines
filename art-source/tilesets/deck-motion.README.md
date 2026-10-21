# Deck motion parts

The moving parts of shipboard props, cut away from their static bases so they can run
(`PropMotion`, catalog `mod/data/tilesets/deck-prop-motions.tileset.json`). Codex
`image_gen` reskin of a code-drawn guide, with the gym and engineering sheets as
material references only.

- Guide: `art-source/templates/deck-motion-guide.py` -> `deck-motion-guide.png`, 1024
  square, 64 px per cell, whole-cell frames with gutters (layout in the script's docstring).
- Accepted raw: `deck-motion.raw.png`, 1254 square RGBA (78.375 px per guide cell), with
  `deck-motion.raw.png.provenance.json`.
- Exact prompt: `deck-motion.prompt.txt`.
- Cuts: `deck-motion.tileset-authoring.json`. Every part is a full placement-frame cut so
  it keeps its registration against its base. The two belt tiles came back inset in a
  bordered swatch, so each is one measured period cut from inside it (treadmill ribs every
  19 px, conveyor cleats every 31 px); laid end to end they join without a seam.
- Runtime: `mod/graphics/tilesets/deck-motion.png` and `deck-motion.tileset.json`.
- The rower base and seat slots are still on the raw but excluded from export: the
  real-size gym sheet carries its own seatless rower and `ship-gym.rower-seat`.

| Piece | Base it moves over | Motion |
|---|---|---|
| `crane-runway` (5x7) | is the base: girders over a bay's two frame columns, laid as a nonblocking overlay | - |
| `crane-bridge` (5x1) | `crane-runway` | slides head to mouth, overhead |
| `rack-lift-rail` (2x6) | is the base: a guide rail layered on the provisions bay's picking rack | - |
| `rack-lift-carriage` (1x2) | `rack-lift-rail` | slides between the three shelves |
| `fan-rotor` (2x2) | `ship-engineering.life-support`; covers its painted grille | spins |
| `treadmill-belt` (1x1 tile) | `ship-gym.treadmill`; covers its painted belt | scrolls |
| `conveyor-belt` (1x1 tile) | `loading-bay.conveyor`; covers its roller bed | scrolls |

```powershell
'{"name":"deck-motion"}' | .\tools\authoring.cmd tileset_export -
```

Review: `gradlew.bat createSnapshots -Psnapshot=deck-props`.
