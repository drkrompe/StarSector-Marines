# Modular variant prompt study

This directory tests independent ImageGen prompts for swappable true-overhead layers.
Every delivered PNG is one sprite, never a sprite sheet.

Armor families:

- `armor/armorless/` â€” cloth fatigues/webbing plus a separate bare head.
- `armor/charcoal/` â€” baseline dark marine body and head.
- `armor/blue-scout/` â€” rugged dark field-blue/naval infantry body and head.
- `armor/red-heavy/` â€” rugged elite oxblood veteran body and head.
- `armor/outlaw/` â€” asymmetric repaired red/brown salvage armor.
- `armor/army-green/` â€” standardized faded olive-drab campaign armor.
- `armor/militia/` â€” civilian-industrial plates, carrier, and helmet.
- `armor/aegis/` â€” sleek Tri-Tachyon composite line armor with an EW blister.
- `armor/palatine/` â€” artisan-restored Church line armor in ivory ceramic and brass.
- `armor/furnace-line/` â€” petro-industrial Sindrian line armor with cooling hardware.
- `armor/reaver/` â€” coherent but asymmetric outlaw line armor rebuilt from ship plate.
- `armor/specter-heavy/` â€” tapered Tri-Tachyon composite walking-tank armor.
- `armor/bulwark-heavy/` â€” reproducible League modular heavy plate blocks.
- `armor/reliquary-heavy/` â€” dense consecrated Church legacy battlesuit armor.
- `armor/lions-mantle/` â€” conspicuous Lion's Guard prestige heavy armor.
- `armor/foundry-breaker/` â€” cargo exoframe buried beneath crude welded heavy plate.

Weapons (geometry-first: `art-source/templates/infantry-weapons-guide.py` owns every
silhouette, `art-source/infantry-weapons/export.py` writes these at their contract sizes;
all seen from straight overhead, muzzle at the top edge):

- `weapons/rifle.png` — FR-1 field rifle: vented handguard, top optic, solid stock.
- `weapons/srm-1.png` — olive tube, flared ends, sight housing on the right.
- `weapons/srm-1-packed.png` — the same tube capped and strapped, the launcher's
  `packedLayer`: slung across the back whenever it is not being fired.
- `weapons/laser-gun.png` — pulse rifle: copper focusing coils between radiator plates, glow
  only at the lens and two indicators.
- `weapons/smg.png` — pirate SMG: side magazine jutting left, wire stock.
- `weapons/dmr.png` — railgun marksman rifle: twin rails, capacitor block, long scope.
- `weapons/anti-materiel-rifle.png` — ported brake, folded bipod, side box magazine.
- `weapons/grades/surplus/rifle.png` — the rifle frame in worn paint and wood.
- `weapons/grades/masterwork/dmr.png` — the DMR frame in navy, ivory and brass.

The head is separate from the shoulder/body mass and can rotate independently around its
center. Each body has an empty helmet socket. Running `art-source/marine-modular-topdown/build_variants.py` writes disposable
family, head-look, and mixed-armor previews to `build/sprite-previews/infantry/`.

## Shoulder-width anatomy shorthand

`1 sw` is the opaque alpha-bounds width of the body/shoulder sprite. The current reference
body is 150 px wide, but authored measurements are ratios resolved with
`pixels = round(sw * shoulderWidthPixels)`. This keeps equipment and attachment anatomy
consistent if unit scale or armor silhouette changes.

Current landmarks:

- Head width: `0.48 sw`.
- Standard rifle/laser length: `0.6533 sw`.
- SMG length: `0.52 sw`.
- DMR length: `0.82 sw`.
- Anti-materiel rifle length: `0.96 sw`.
- Standard aimed offset: `(0.1733 sw, -0.12 sw)`.
- Rocket launcher size: `(0.4267 sw, 1.04 sw)`.
- Rocket aimed offset: `(0.3333 sw, -0.12 sw)`, placing its centerline approximately
  two-thirds of the way from the spine to the right shoulder edge.

`art-source/marine-modular-topdown/build_variants.py` uses the `sw(value)` helper as its source of truth. `variants.json`
records proportional `*Sw` values alongside compiled reference-pixel values for consumers
that have not yet adopted shoulder-relative layout.

## Weapon occlusion

Rifles and carried weapons normally render beneath the body. The rocket launcher switches
to an over-shoulder firing layer: render `body`, then `weapon`, then `head`, followed by
firing effects. This makes the launcher occlude the right shoulder while keeping the
independently rotating helmet visually above it. The carried/aimed launcher remains under
the body until the shouldering transition reaches its firing pose.

The successful prompt pattern is: one isolated object, strict zenith projection, a fixed
north orientation, an explicit empty socket or operator-free constraint, a flat chroma
background, and an existing accepted layer used only as geometry/style reference.

`art-source/marine-modular-topdown/build_variants.py` normalizes the retained alpha originals in `art-source/marine-modular-topdown/sources/` and rebuilds
these runtime sprites plus ignored build-tree previews. Chroma-key intermediates are
deliberately discarded.

The exact source prompts and reference lineage for the additive faction patterns live in
`sources/faction-armor/PROMPTS.md` beside their retained ImageGen source layers.
