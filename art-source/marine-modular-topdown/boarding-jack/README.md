# Boarding Jack layered art

Status: SHIPPED. `armor.boarding-jack` declares its presentation and its card is
awardable; the `BOARDING_JACK` appearance family loads the exports below.

## What the rig is

A loading/boarding frame rebuilt by pirate yards: compact pressure helmet, welded
mismatched plate, visible joint backing, guarded hoses and hand-lettered service
labels. Heavy mass, low resistance, poor handling. It reads as a person in a powered
shell (helmet between broad shoulders, continuous torso, compact power pack, two
walking feet), never as a Hound chassis, and never as the Foundry-breaker's
rejected cartoonish silhouette. Volume is crude reserve, not material quality.
No incidental guns, spikes, skulls, chains or machinery promising unowned mechanics.

## Contract

`geometry.json` owns the layer contract and `make_guide.py` renders it:

- separate torso, left shoulder, right shoulder, helmet and feet; handheld weapons
  stay the shared swappable infantry layers;
- the Specter v2 shell registration: 150x95 body canvas shared by torso and both
  shoulders, inner-hinge pivots `[0.2653,0.3499]` / `[0.7347,0.3499]`, mastering
  scale 1.12, 72x69 helmet and 38x52 foot canvases, so the existing shell pose
  tracks articulate the rig;
- a deliberately mismatched pair: a long welded boarding shield on the left, a smaller
  rounded cargo cap on the right;
- material regions: casing (the only paintable region), bare steel, joint backing,
  hoses, service labels and at most one indicator lamp per part;
- the left part is a **boarding shield**: a long crude welded loading-frame plate on
  the left hinge with a forward lip (a donor plate) standing ahead of the chest, and the
  Latch Assist ram block and twin rams bolted to its face. It is wider and longer than
  the right cap on purpose. The shield turns with the torso: the armor master applies
  `LayeredArmorFamily.leftShoulderTurnGain` (1.0), so for every degree the shared pose
  track turns the torso away from neutral the shield turns one more about its hinge.
  At rest and on the walk (torso at 0 and +/-4 degrees) it sits on the shoulder; in aim,
  fire and brace (torso about -18 to -35 degrees) the lip sweeps forward across the
  left of the chest and the helmet's front. The right cap and every other shell keep
  the authored track;
- Latch Assist is that shield, not a separate layer. Its existing directional soak
  (4 over 100 degrees) already belongs to this side of the rig, and a separate screen
  on top of a shield read as two shields; the system's running halo still marks it
  while active. Values are unchanged;

`python art-source/marine-modular-topdown/boarding-jack/make_guide.py` writes the
1920x1080 review sheet `art-source/templates/boarding-jack-guide.png`, one flat
`guide-<part>.png` per part on its true export canvas, and the large vector edit
targets `art-source/templates/boarding-jack-<part>-guide.png`. Each edit target is
an exact multiple of a fixed window of its export canvas (`edit_windows`), so the
exporter's registration is arithmetic.

## Generation (Codex CLI built-in ImageGen)

`bash generate.sh <part> [reference.png ...]` sends `prompts/<part>.prompt.txt` with
the part's edit target to `codex exec -s workspace-write -i <guide> [-i <reference>]`
and retains the untouched result as `raw/<part>.raw.png`. The torso was generated
first with no reference; every other part passed `raw/torso.raw.png` as the material
reference so the set shares one paint, wear and finish. All raws came back with real
alpha: torso 1576x998, left shoulder 1211x1299, right shoulder 1293x1216, helmet
1281x1227, foot 1072x1467; the boarding shield, regenerated against its own guide, 1371x1148.

## Export

`python export_assets.py` is mechanical. Each raw is resampled to its edit target,
its opaque extent fitted onto the guide's (one per-axis scale and offset, recorded in
`export-measurements.json`; the torso came back about 21% larger on the canvas than
its guide, the other parts within 3%), its own alpha bounded by the guide silhouette
grown 1.2%, downsampled with premultiplied alpha through the window and placed on the
complete export canvas. The casing mask (`masks/<part>-casing.png`) is warm painted
plate by colour inside that silhouette; steel, rubber, hoses, cream labels and lamps
are never painted. The rust-orange default is the raw paint; `paints/hull-grey`,
`paints/oxide-green` and `paints/soot` run the Specter v1 luminance ramp through the
mask, so every paint shares one silhouette and alpha.

## Pirate paint (Greyhook's raiders)

A luminance ramp can recolour plate but cannot scrape, scorch or daub it, so the
`pirate` paint has hand-made detail of its own. `python export_assets.py
--pirate-bases` writes each body part at edit-target resolution in the soot paint to
`pirate/<part>-base.png`. `bash generate.sh pirate-<part>` sends that base, with
`prompts/pirate-<part>.prompt.txt`, through the same Codex call: the soot base is kept,
and the pass adds scrapes to bare steel, chipped edges, dents, scorching, and crude
crew-applied red (a slashed chest stripe, a band and tally slashes on the slab, a red
rail panel, a crooked dome stripe, a red-painted instep). The exporter registers
`raw/pirate-<part>.raw.png` like any raw, composites it over the soot export and then
takes the default export's alpha, so the pirate paint has exactly the shared
silhouette, the shield included.
`GreyhookRigIssue` draws raiders in `BOARDING_JACK_PIRATE`
(`armor-master-boarding-jack-pirate`, the same transforms as the issue master); the
company's issue keeps `BOARDING_JACK`.

Mastering lives in `unit-layer-layouts.appearance.json` (`armor-master-boarding-jack`):
the Specter's body and shoulder transforms, and a helmet at scale 0.8 offset to the
measured socket (centre 0.421 of the canvas height, so offset -0.004 shoulder widths),
which keeps the chest, neck ring and hoses visible between the slab and the cap.

Review: `gradlew.bat createSnapshots -Psnapshot=boarding-jack-layers` (1920x1080,
100%): separate parts, the rig beside the Specter and in Greyhook paint,
articulated poses, all five paints, world scale and Greyhook's raiders in their paint.
