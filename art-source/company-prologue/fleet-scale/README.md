# Fleet scale lineup

Status: ACTIVE. A rerunnable render of every cinematic hull at its own size, so
relative scale can be seen rather than read off a table.
Written: 2026-10-18

`fleet_lineup.py` puts every hull into one Blender scene in metres. Every hull
gets the same grey material, there is one sun and a dim grey world, a 1.8 m
orange figure, and a grid with 10 m lines and 50 m majors. Size is the only
thing that differs. It renders six 1920x1080 frames and writes a sizes table.

```sh
python3 fleet_lineup.py                 # build, render, label, write the table
python3 fleet_lineup.py --post mac      # ... and post the run to the Drive review folder
python3 fleet_lineup.py --basis solved  # preview ship_scale.py's solution instead of published values
python3 fleet_lineup.py --pending pending-c.json   # a chosen basis not yet published: scale those hulls by hand
python3 fleet_lineup.py --no-references # leave out the real-world references
python3 fleet_lineup.py --frames 3,5    # rerender some frames; the others keep their raw renders
python3 fleet_lineup.py --skip-render   # relabel and rewrite the table from the last raw renders
```

Labelling needs Pillow. If the `python3` that runs the script has none (on the
Mac, Homebrew's), it re-runs itself under `$PYTHON` or the system `python3`.
Blender is found through `blender_path.py`.
Output goes to `<repo>/build/fleet-scale/<date>/`, which is ignored. Renders
are never committed.

## Where sizes come from

Nothing here holds a size.

- **Published hulls.** `metres_per_unit` comes from the package's
  `geometry.json`. A preserved hull's state uses that state's own
  `geometry.json`, for example the Second Wind's patched state.
- **Models.** A hull uses its cinematic export if one has been built locally,
  for example `hound-export/clean/hound-a-clean.blend` (`HOUND (A) | clean`) or
  the Second Wind's `SECOND WIND | patched`. Otherwise it appends the
  package's tracked geometry `.blend`. Every part is scaled to metres from its
  root.
- **Drive builds.** A package whose built `.blend` is not tracked, such as the
  Lasher's, publishes it to the shared Drive under
  `starsector/builds/<hull>/<short commit>/`. The script uses the build of the
  package's latest commit, or the newest one, and says which. A package with a
  documented build and no build anywhere (the Onslaught today) needs its build
  run once on the machine first:
  `sh ../../blender.sh --background --factory-startup --python build_onslaught.py -- --views top`.
- **Pending basis.** `--pending pending-c.json` scales the hulls it names to
  the lengths the owner chose (option C: Legion 293 m, Lasher 81 m,
  Onslaught 270 m), labelled "C, pending publish". Each override applies only
  while the hull's published length differs from its target by more than 5%.
  Once a hull's session publishes its C scale to `geometry.json`, a rerun uses
  the real value with no hand scaling. Delete an entry once its hull has
  published. The Legion frames carry the basis in their titles.
- **Estimates.** A hull with no package yet goes in as a neutral slab and is
  labelled ESTIMATE. The Onslaught and the Lasher are estimates today. The slab
  is the game sprite's silhouette, extruded to the mean thickness that gives
  `ship_scale.py`'s estimated volume, and it uses `ship_scale.py`'s solved
  `metres_per_unit`. Without the game install it falls back to a box. Once
  `<hull>-geometry/<hull>-geometry.blend` and its `geometry.json` exist, the
  next run uses the real model.

**Rerunning picks up changes as they land.** That covers a rescaled
`geometry.json`, such as the Legion's rebuild at the `ship_scale.py` basis, a
new package, and a newly built export. `sizes.md` lists, for every hull, the
`metres_per_unit` it used, where that came from, and the `ship_scale.py`
solution beside it. The run's `NOTES.md` repeats this.

## References

Real-world objects in one neutral grey, at the end of both lineup rows: a
20 ft container (6.1 x 2.4 x 2.6 m, the cargo unit), a Boeing 747 (70.7 m,
64.4 m span), the ISS truss (109 m), the Eiffel Tower lying flat (330 m), a
Nimitz-class carrier (333 m, 77 m flight deck) and a football pitch
(105 x 68 m, visible from above). They are simple primitives: only the
silhouette at the right size matters. The 1.8 m person stands beside every
hull. In the whole-fleet broadside strip, craft under 25 m share one label;
the magnified panel names each of them.

## Frames

| # | Frame | Notes |
|---|---|---|
| 01 | Broadside lineup, orthographic, longest first | The whole fleet above; the Hound and everything smaller, magnified, below |
| 02 | The same lineup from above | Bow to the right |
| 03 | The Hound holding just outside the Legion's aft port hangar mouth (`flight_decks` WS 010) | 50 mm lens from about 450 m, so perspective does not flatter the nearer hull |
| 04 | The Hound in that hangar lane | Orthographic section from astern, cut just inside the mouth. The lane's clear section is outlined. The subtitle says whether it fits |
| 05 | Eye level on the Hound's port walkway | 1.65 m above the deck, with the Legion alongside |
| 06 | The Legion's fighters, two abreast in the same lane | Orthographic section from outboard. No shadows, because the clipped wall would still shade the lane |

In the section views (04, 06) the Legion turns a darker grey and its faces seen
from behind become transparent, so the cut reads as a cutaway.

## Sizes table

`sizes.csv` and `sizes.md` list each hull's `metres_per_unit`, length, beam
and height, measured from the model's render-visible bounding box. Volume is
given two ways:

- **Box volume** is length x beam x height. Every hull has one, and the
  volume ratios to the Legion and the Hound use it.
- **Enclosed volume** is the crew-usable volume from `ship_scale.py`, given
  only for hulls registered there.

Each row says whether its value is `published`, `estimate` or `preview`.

## Review sets

`--post mac` (or `windows`) copies the frames, `NOTES.md` and the table to
`starsector/review/fleet-scale/<date>_lineup_<machine>/` on the shared Drive.
It refuses an existing folder (one run, one writer). After posting, prune:

```sh
python3 ../../../tools/review_prune.py --asset fleet-scale --apply
```
