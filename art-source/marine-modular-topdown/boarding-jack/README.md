# Boarding Jack layered art (open)

Status: GUIDE ONLY. No accepted raw art exists and nothing here is exported.
`armor.boarding-jack` declares `presentationPending` in the armor catalog and its
`BOARDING_JACK` appearance family loads no assets, so an actor wearing it draws the
legacy unit sheet and no card carrying it can become cargo. Shipping this art is
what lets that declaration be removed.

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
- a deliberately mismatched pair: a larger square welded slab on the left, a smaller
  rounded cargo cap on the right;
- material regions: casing (the only paintable region), bare steel, joint backing,
  hoses, service labels and at most one indicator lamp per part. Each exported part
  needs a casing mask so paint variants share identical silhouettes;
- Latch Assist's visible actuator/screen assembly is a separate carrier layer on the
  left shoulder (`latch-screen.png`), shown while carried like the Specter's emitter.

Run `python art-source/marine-modular-topdown/boarding-jack/make_guide.py`; it
writes `art-source/templates/boarding-jack-guide.png` (1920x1080 review sheet) and
one flat `guide-<part>.png` per part on its true export canvas.

## Prompts (built-in ImageGen, edit target = the matching `guide-<part>.png`)

Material reference: the accepted Specter v2 exports under
`mod/graphics/battle/marine-modular-topdown/variants/armor/specter-heavy/`.

**Torso.** Edit the referenced flat guide into the torso layer of a pirate
"Boarding Jack" rebuilt boarding exoframe, pixel art, strict 90-degree zenith view,
north/up. Preserve the guide's silhouette, canvas, placement and the empty oval
helmet socket exactly. Orange-coded regions become welded mismatched replacement
plate with weld beads and bolted patches; grey becomes a compact bare-steel power
pack; dark rim becomes flexible joint backing; purple lines become guarded hoses in
armored sleeves; yellow becomes a hand-lettered service panel with no readable text.
Worn, grounded, plausible industrial salvage. Transparent or flat #ff00ff background.
No head, arms, legs, weapon, spikes, skulls, chains, extra guns or perspective.

**Shoulders (one call each).** Edit the referenced flat guide into one isolated
shoulder slab of the same rig. Preserve the canvas, outline and position exactly
(the empty canvas around it is required). Left: a large square welded slab with a
small service panel. Right: a smaller rounded cargo-frame cap from a different donor
part. Same materials as the torso. Nothing outside the slab.

**Helmet.** Edit the referenced flat guide into a compact pressure helmet seen from
directly above: low oval dome, flat welded brow plate forward, one tiny indicator
lamp, two guarded hose ports aft. Preserve the oval footprint and centered pivot.

**Foot.** Edit the referenced flat guide into one broad flat cargo boot seen from
above: toe plate forward, heel latch, dark backing. One foot only.

**Latch screen carrier.** A narrow bolted actuator plate with a small salvaged
riot-screen panel, top-down, sized to the proposal in `geometry.json`. A screen,
not a shield dome and not a ram; no weapon.

## After generation

Retain each raw output and its exact prompt here, record measured dimensions and
registration, cut and export mechanically (as `specter-v2/export_assets.py` does),
produce casing masks and the four palettes named in `geometry.json`, add the
`boarding-jack` family to `BattleSprites`, `UnitLayerLayouts` mastering and the
headless renderers, review at 1920x1080 / 100% through `createSnapshots`, then remove
`presentationPending` from the catalog entry.
