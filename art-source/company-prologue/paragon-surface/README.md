# Paragon: surfacing

Status: ACCEPTED (pass 6 round 3, accepted by the owner 2026-10-19).
Written: 2026-10-19

The Paragon's folder for the shared surfacing harness
(`../second-wind-surface/`, run with `--hull`; see its README for the method).
Inputs and Codex generations live here. Textures, grading, review renders and
builds are script outputs and are not committed. `./rebuild.sh` replays
everything except Codex.

## The first overlay hull

The Paragon's armour is the procedural hex-ablative ceramic the owner chose in
pass 3 (`paragon-geometry/pg_finish.py`): one pattern, continuous across every
face, its plate size set per part, with its own relief. The owner kept it
(2026-10-19). So the harness runs in its opt-in **overlay** mode
(`surface_hull.OVERLAY`): every base material keeps its own colour and relief,
and the graded Codex texture only modulates it, by its departure from its class
mean at a per-class strength (0.6-0.75: round 2 ran 0.8-1.0 and read too dirty for a Tri-Tachyon flagship). The wear layer bumps the hex relief
rather than replacing it. No surface is repainted, so no hex moves.

## Livery

Tri-Tachyon, read off `paragon.png`: cool blue-grey glazed ceramic armour,
pale blue-white ceramic on the prow grille and shoulder plates, a near-white
command deck and core, dark blue-slate bands, light emitter steel, near-black
machinery recesses, the bore's grey plating. `surface_hull.py` maps the base
materials to ten classes. The guns keep their own (the turret kit's tinted
copies of these materials), and so do the glazing and every glow and lamp.

- **Finish reference** (`reference/finish-reference.png`): one Codex reskin of
  the clean build's three-quarter camera, the sprite (`build/sprite-livery.png`,
  bow-right, 3x, never committed) as colour key. It registers at IoU 0.997
  and holds the hex pattern in place. `palette.py` reads eight classes off it;
  hatch and substrate are not seen from that camera and are owned derivations.
- **Swatches** (`generated/sheet-swatch-v2.png`; round 1's fainter
  `sheet-swatch.png` is kept): one Codex sheet of six squares painted as a
  **finish layer only**: strong glaze mottling, cloudy grime and visible
  blue-gold heat tint, with no tiles, seams or fasteners (the geometry has those). Graded
  to the palette within about 2 L, tiled at 12 hull units (10.4 m) per image.
- **Wear**: the `service` row of `wear.json`, set halfway between rounds 1 and 2 (grime 0.36,
  soot 0.6): grime in seams, soot at the ten drives, scuffing on exposed
  ceramic edges. The wear colours are owned for ceramic: a scuffed
  glaze shows a paler body, and fade goes paler and bluer, never yellow.

## Parts

Overlay tiles: each carries only the finish that belongs to one place, never a
pattern (`prompts/sheet-top-a.prompt.txt`, `sheet-ends-a.prompt.txt`).
`make_groups.py` writes them as world-space box tiles seen square-on, the port
tile painted and its starboard twin reusing it mirrored:

- `top`: stern, aft, mid and bow deck from above (drive soot trails, heat round
  the core emitters and turret rings, grime round the service fields);
- `ends`: stern and bow faces (soot round the ten drive mouths, heat round the
  bow projectors) and the aft and fore sides.

Two sheets, two Codex calls. Registration IoU 0.958-0.986. The colour-layout
check flags regions painted toward a neighbouring class (mostly skin painted
paler); grading forces each back to its declared class, which under overlay
only moves its tone.
