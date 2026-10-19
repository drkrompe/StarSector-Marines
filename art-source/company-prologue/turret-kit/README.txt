Turret kit: shared turrets for every cinematic hull

turret_kit.py builds the trainable turret and its fixed barbette for any
weapon in its catalogue, at any hull's scale. A hull's own turret module
keeps what is particular to that hull: slot positions, seating on its
surface, clash checks and records. It asks the kit for the geometry, so every
ship carries the same family of guns at the same standard.

Use (inside a hull's Blender build)
  sys.path.insert(0, '<company-prologue>/turret-kit'); import turret_kit as tk
  mats = {'armour': faction colour, 'plate': grey plate, 'steel': gunmetal,
          'dark': recesses and bores}          # optional 'glass' for sights
  tk.barbette(name, ring, lift, mats, mpu, location=(x, y, seat))
  t = tk.build(weapon, 'Weapon <slot>', pivot, ring, mats, mpu, detail='near')
  ring is the sprite's turret-ring radius in hull units; mpu is the hull's
  metres per unit. Override any catalogue field per slot, e.g. barrels=2.

What a turret gives a shot
  - Train: rotate the pivot (the hull's own empty) about its local Z.
  - Elevate: rotate '<prefix> elevation' about its local Y (negative lifts).
  - Recoil: slide '<prefix> recoil <k>' along its local -X (one per barrel,
    as the game's separate recoil sprite layer). The Vulcan's two barrels
    recoil alternately, as vulcan.wpn fires them (ALTERNATING). A gatling
    (gun='gatling', no catalogue entry uses it now) spins on '<prefix> rotor'.
  - '<prefix> muzzle anchor <k>' at each bore or launcher cell, +X along the
    line of fire, for muzzle flash and projectile VFX.
  - One object per material per moving group: about 7-8 objects and
    1.5-4.5k faces a turret.

Look
  Crisp plate, not pillows: every chamfer is capped at 0.1 m (CHAMFER_M),
  armour sections are straight-chamfered, the small mounts' domes are ten
  welded plates. Plate seams, weld beads, bolt rows, access panels and
  conduits break up every large face.

Paint (texture pass)
  Every sprite paint is a weathered material (turret_kit.paint_material):
  low mottle, edge wear chipped through to dark steel on every hard edge (a
  Bevel-node normal against the true normal finds them; noise breaks it up),
  and grime in crevices from ambient occlusion. Gun metal gets the same tree
  with a metallic base that rubs brighter at its edges. The hull's own plate
  and steel on a turret are weathered the same way at the hull's colour.
  Livery: a sprite's colour is only a hint (turret_kit.LIVERY). Each paint
  keeps part of its sprite's hue at service-paint brightness (bodies about a
  third, accents a little more, markings most); gun metal goes to gunmetal.
  A paint is named by its target, that livery colour. Its base colour
  is solved by calibrate_paints.py so that a flat roof of it, under the review
  studio (studio.py, shared with catalog.py), renders that colour from above;
  the table is paint_calibration.json. Paint is matte (a low specular level):
  at the default level the bright grey world and the overhead rim sun laid a
  grey sheen over every roof, which is why the turrets read paler than their
  sprites. Rerun the calibration after changing a paint, the studio or the
  material. Lamps (the amber glow, the Hurricane's cyan) are emissive.
  TURRET_PAINT_DEBUG=edge|wear renders the masks themselves.

Depth, not paint
  The sprites' dark pixels are gaps and shadow, so slots, vents, wells,
  dimples and ports are cut into the parts they sit in (Mesh.cut(), an exact
  boolean applied at emit), with a dark floor at the bottom, never laid on as
  dark plates. A Group key may carry a '#tag' ('paint#body') to make a
  separate object of that material, so a cut carves one part, not every part
  of the same paint.

Seating
  A barbette's foot runs down to the lowest hull surface under its whole
  flange (foot=), so a mount seated on a belt still stands on the deck
  beside it. A ring that overhangs the hull's edge gets a sponson tub
  (sponson=, sponson_dir=) tapering into the side wall. check_seating.py,
  run inside a built .blend, reports any barbette with daylight under it.

Scale
  The silhouette is proportioned to the ring, so a turret fills its sprite
  ring at any scale. Fittings are true metres: 0.9 m roof hatches, 2.2 m
  cheek doors, ladder rungs every 0.3 m, 0.9 m grab rails, periscopes,
  vents, bolts. detail='mid' drops everything under about 0.4 m, for shots
  from a kilometre or more.

Built from the game's sprites (house='hand')
  Fifteen turrets are hand-built from their own sprites: vulcan, lightac,
  lightmg, heavyac, heavymauler, hveldriver, devastator, mark9, hellbore,
  hephaestus, needler, flak, dualflak, squall, hurricane. Each builder in
  HANDS reads the sprite's measurements (sprite_outlines.json) and works in
  sprite pixels times k = ring / nominal ring (SMALL 5.5, MEDIUM 9.5, LARGE
  12.5), so at the nominal ring one pixel is one hull unit and the model
  covers its sprite from above. The sprite's dark pixels are gaps and shadow
  between parts, so they are built as depth (slots, recesses, louvres), never
  painted on. Paints come from the sprite's colours, with per-entry `paints`
  overrides where the measured colour is wrong. Barrels are lathed from the
  recoil sprite's width profile, its dark rows as grooves (barrel_scale and
  muzzle_ring per house); a builder may build its own gun group instead.
  derive_sprites.py (system Python + PIL) rewrites sprite_outlines.json from
  the installed game's .wpn files and sprites: outlines, barrels, colours,
  offsets, render hints. --game names the install folder holding
  starsector-core (on a Mac, a folder with a starsector-core link to
  Starsector.app/Contents/Resources/Java).

Tri-Tachyon energy weapons (house='hand')
  tachyonlance, gravitonbeam, taclaser and the reaper launcher, the Paragon's
  Elite fit. A beam has no gun sprite, so the whole weapon trains and the
  elevation empty carries only the muzzle anchors (at the .wpn's offsets).
  Each stands on a thin deck cut to the sprite's outline (`bodies`, which keeps
  both halves of the lance either side of its open beam slot, plus the thin
  `prongs`); that holds the silhouette. Over it the sprite is read as solids,
  not as layers of plate: a lit column is a crest with faces falling away
  either side, a dark band beside it a steep wall or a gap, a highlight running
  across a part says the part lies across. So the lance is two keel blades,
  each a wedge with a steep inner wall down to the open slot and an outer
  glacis, tallest at the shoulders and low at both tips, with athwartships
  capacitor cylinders, machinery blocks, coolant ports and falling vanes; the
  graviton a crested spire nose with its projector line, faceted cowls either
  side of a sunken spine, and a lens bar across; the tactical laser a tall
  dome with a trench across its crown holding the focusing core, and an
  emitter barrel; the reaper tall rails, a launcher with its tube mouth, the
  round loaded in the slot, and a base bar with lamps and end caps. Heights
  follow SECTION (roof 6.0 / 4.6 / 3.2 px), so each has a profile from the
  side. Lamps are 'lamp_blue', 'lamp_red' and 'lamp_core'. Silhouette IoU
  0.92 (lance), 0.95 (graviton), 0.93 (tactical laser), 0.86 (reaper).
  Their paints are halfway between the sprite's colour and Tri-Tachyon's pale
  ceramic, and keep more of their colour than the military livery does
  (catalogue `chroma` .7 against LIVERY's .35): recognisable, not cartoonish
  (owner, 2026-10-19).

Dressing a turret in the hull's finish
  build(..., hull_finish=True) uses the hull's own materials for every key the
  hull supplies (paint, accent, metal, steel, lamp_blue ...), as they are,
  instead of the sprite's paints and the kit's weathering. Keys the hull
  leaves out still come from the sprite. sprite_tint=f then mixes the sprite's
  own colour a fraction f into the hull's paint and accent (tinted(): a Mix node
  ahead of the material's base colour, so a procedural finish keeps its
  pattern). The Paragon dresses its Tri-Tachyon guns in its hex ceramic this
  way, with sprite_tint=.5.

Other catalogue entries (WEAPONS)
  none (dome), and the box launchers harpoonpod, sabotpod, salamanderpod (open
  rails), pilum, typhoon, annihilatorpod (tubes): the rack is drawn with its
  loaded missiles (RENDER_LOADED_MISSILES), on a yoke with elevation rams.
  Not yet: hardpoints (fixed mounts) and energy weapons other than the
  Tri-Tachyon four above; the Onslaught's built-in TPCs stay in os_turrets.

Review
  blender --background --factory-startup --python catalog.py -- --out DIR
  renders catalog-overview.png and a close three-quarter per weapon, studio
  lit, 1920x1080 (--only a,b to pick, --mpu, --detail).

The gauntlet (each turret, against its sprite)
  catalog.py ... --top adds an orthographic top view and a starboard side
  view of each turret alone on transparent film, trained dead ahead.
  python review_sheet.py DIR [--only a,b] then writes DIR/<w>-sheet.png
  (1920x1080: the sprite composited as the game layers it, the top view at
  the same scale with its silhouette IoU against the sprite's alpha and its
  mean colour and luminance against the sprite's, the side view, the
  three-quarter), iou.json and colour.json. An independent reviewer judges each
  sheet on silhouette (IoU 0.85 or better), parts, barrels, paint, form (no
  floating parts, no painted-on detail) and finish, and lists the geometric
  fixes; apply them and render again until it passes.

Users
  legion-geometry (lg_turrets), paragon-geometry (pg_turrets: the Tri-Tachyon
  four, hull_finish). Lasher, Onslaught, Hermes, Hound and Second
  Wind still use their own turret modules; moving one onto the kit is that
  hull's session's call.

Cinematics hooks
----------------
Each barrel hangs from its own 'recoil k' empty. A hand builder's 'barrel_root'
carries the barrel's shaft back inside the house, so sliding that empty aft
under fire pushes the barrel into the hull rather than out of a hole in it
(space-combat cinematics). Loaded missiles (Squall's rockets, Hurricane's
MIRV) are each their own 'missile k' empty and objects, so a shot can launch
them; the game sprite draws them loaded, which is why they are modelled.
