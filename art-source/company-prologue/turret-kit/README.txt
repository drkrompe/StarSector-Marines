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
    as the game's separate recoil sprite layer). A gatling's barrels spin on
    '<prefix> rotor' about its local X.
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

Catalogue (WEAPONS)
  dome mounts  vulcan (six-barrel rotary), lightac, lightmg, none (plain gun)
  gunhouses    heavyac, heavymauler, hveldriver, devastator, mark9, hellbore,
               hephaestus, needler (six-barrel block)
  flak         flak, dualflak (low house with sight frames)
  launchers    squall, harpoonpod, sabotpod, salamanderpod (open rails),
               pilum, typhoon, annihilatorpod (tubes), hurricane (fork).
               Built from the game's layered sprites: the rack is drawn with
               its loaded missiles (RENDER_LOADED_MISSILES), so the missiles
               lie on rails along the line of fire, behind a magazine housing,
               on a yoke with elevation rams.
  Not yet: hardpoints (fixed mounts) and energy weapons; the Onslaught's
  built-in TPCs stay in os_turrets.

Review
  blender --background --factory-startup --python catalog.py -- --out DIR
  renders catalog-overview.png and a close three-quarter per weapon, studio
  lit, 1920x1080 (--only a,b to pick, --mpu, --detail).

Users
  legion-geometry (lg_turrets). Lasher, Onslaught, Hermes, Hound and Second
  Wind still use their own turret modules; moving one onto the kit is that
  hull's session's call.
