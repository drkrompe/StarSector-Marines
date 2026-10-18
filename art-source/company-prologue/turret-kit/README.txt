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
  offsets, render hints.

Other catalogue entries (WEAPONS)
  none (dome), and the box launchers harpoonpod, sabotpod, salamanderpod (open
  rails), pilum, typhoon, annihilatorpod (tubes): the rack is drawn with its
  loaded missiles (RENDER_LOADED_MISSILES), on a yoke with elevation rams.
  Not yet: hardpoints (fixed mounts) and energy weapons; the Onslaught's
  built-in TPCs stay in os_turrets.

Review
  blender --background --factory-startup --python catalog.py -- --out DIR
  renders catalog-overview.png and a close three-quarter per weapon, studio
  lit, 1920x1080 (--only a,b to pick, --mpu, --detail).

The gauntlet (each turret, against its sprite)
  catalog.py ... --top adds an orthographic top view and a starboard side
  view of each turret alone on transparent film, trained dead ahead.
  python review_sheet.py DIR [--only a,b] then writes DIR/<w>-sheet.png
  (1920x1080: the sprite composited as the game layers it, the top view at
  the same scale with its silhouette IoU against the sprite's alpha, the side
  view, the three-quarter) and iou.json. An independent reviewer judges each
  sheet on silhouette (IoU 0.85 or better), parts, barrels, paint, form (no
  floating parts, no painted-on detail) and finish, and lists the geometric
  fixes; apply them and render again until it passes.

Users
  legion-geometry (lg_turrets). Lasher, Onslaught, Hermes, Hound and Second
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
