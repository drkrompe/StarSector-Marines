# Phaeton: surfacing

Status: ACTIVE (pass 6 posted for owner review 2026-10-19). The cinematic export is `../phaeton-cinematic/`.
Written: 2026-10-19

The Phaeton tanker's folder for the shared surfacing harness
(`../second-wind-surface/`, run with `--hull`; see its README for the method).
Inputs and Codex generations live here; textures, grading, review renders and
builds are script outputs and are not committed. `./rebuild.sh` replays
everything except Codex.

## Livery

An independent merchant tanker in the Asharu convoy. Its red is read off
`tanker_medium.png` (`reference/sprite-livery.png`, the sprite at 8x); its
structure is tied to the convoy's independent escorts (the Lasher's
independent livery, `../hound-surface-independent/`), so the column reads as
one fleet:

- `red`: deep oxide-red tank hull, deck, flanks and crew gallery;
- `dome`: pale warm-grey insulated enamel on the three cargo spheres;
- `slate`: muted slate-blue frames, sponsons, pipework, catwalks, crane,
  masts and reaction drums (the build's 'Frame light grey');
- `cream`: buff-cream bridge cab, guard arcs and fittings;
- `gunmetal`: machinery strakes, aft spine and drive block;
- `dark`: recesses.

The glazing, gallery windows, lamps, drive housing glow, bell liners,
heat-blued nozzles, hose rubber and the crane hook's hazard yellow keep their
own materials. Wear runs the `service` row of `wear.json`; the owned wear
colours in `palette.json` use a pinkish-grey chalk (faded oxide red) and a
warm grime with fuel film.

## Owned values

`surface_hull.derive_palette` keeps the reference's means except two: the
recess class (thin slots measure their slate neighbours, L 46) is owned
near-black, and the slate class (measured nearly grey, b -3.5, because the
reference painted the drums and reactor dome gunmetal) is owned toward the
escorts' slate accent, b -8. Spreads are owned for every class.

## Parts

Five groups, one sheet each except the hull's two: `hull` (cargo deck,
flanks, bow, belly, gallery roof and wall, aft frame), `tanks` (three crowns
and one side, reused for the other five sides), `machinery` (both strakes,
spine, drive block top), `drive` (housings, block stern, drums, guard arcs;
starboard mirrors port) and `bridge` (the cab's four faces). The hull's
plating is seamed, so its deck and flanks are taken by box seeds. The long
deck and strakes sit at 4-5 texels per unit (13-15 per metre), plenty at the
shots' 1-1.5 km; the small parts reach 30.

## Destroyed state

Damage runs after the skin (second-wind-pipeline.md, Layers):
`../phaeton-cinematic/apply_damage.py` opens `build/skinned.blend`, applies
`ph_damage` and saves `build/skinned-destroyed.blend`. The booleans cut the
skin like any face; the faces they make take the scar materials.
