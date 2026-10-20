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
  Sixteen turrets are hand-built from their own sprites: vulcan, lightac,
  lightmg, heavyac, heavymauler, hveldriver, devastator, mark9, hellbore,
  hephaestus, needler, flak, dualflak, squall, hurricane, railgun. Each builder in
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

The Cerberus' railgun (house='hand')
  railgun, the Second Wind's WS 003 (cerberus_Standard). The game draws it as
  one sprite with no gun layer, so the builder splits it: the house is the
  low grey body widest through its middle, a pale silver capacitor bulb on
  the crown, the copper breech block sunk in a well behind it, two pale lugs
  either side of the dark tail notch and armour cheeks with lit tell-tales
  down the flanks; the elevation empty carries the copper rail pair (banded
  dark every two pixels, a pale core between them, grey guide fins along
  their roots) on recoil 1, which the game never moves. Muzzle at the rails'
  tip. Paints: grey body and copper rails from the sprite, the bulb an
  'accent2' silver override. Top-view IoU 0.90.

Tri-Tachyon energy weapons (house='hand')
  tachyonlance, gravitonbeam, taclaser, the reaper launcher and the pdlaser,
  the Paragon's Elite fit (with the kit's needler). A beam has no gun sprite,
  so the whole weapon trains and the elevation empty carries only the muzzle
  anchors (at the .wpn's offsets). Each trains on a round chamfered turntable
  (_turntable), and everything the sprite shows is a part: blades, a nose and
  rails cantilever past it, their undersides lifting toward the tips, and the
  sprite's dark masses are machinery blocks, fairings and webbed roots, not a
  plate cut to the outline (owner, 2026-10-19: the outline board read strange).
  A catalogue `roller` (x ring radius, default .9) shrinks the kit's traverse
  roller where it would show (the lance's sits under its small turntable).
  The sprite is read as solids,
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
  side. The forms are smooth lofts through Catmull-Rom profiles (_cr), laid
  in plates (_panelled): each plate its own loft, a pulled-in core in the gap,
  so a seam is a groove with a floor rather than a painted line. Detail is
  sprite-pixel scale (a lance is about 60 m across; true-metre bolts would
  vanish): cooling rings, vent slats, rims, dome plate seams cut through a
  shell to its core, skirt bands. Lamps are 'lamp_blue', 'lamp_red' and
  'lamp_core'. Silhouette IoU 0.86 (lance), 0.88 (graviton), 0.87 (tactical
  laser; sensor lugs on its flanks, a rear cap, a neck cowl), 0.86 (reaper).
  The PD laser (pdlaser, common tech) is the same family, built for busy
  scenes. A round chamfered turntable carries it (not a plate cut to the
  sprite's outline); a dark housing lofted to the sprite's plan, a red armoured
  collar with louvres on top; and the emitter drum on a gimbal: a yoke of two
  arms carries it on trunnion hubs with red slip-ring bands, and it rides the
  elevation empty from 5 degrees down to 75 up, clear of the house through the
  whole range (BVH sweep). It is a laser / plasma mount, so its power is heavy
  cabling (_cable: a tube swept along a Bezier): a junction box on the
  housing's crown, two armoured cables a side over the collar's shoulders on a
  clamp saddle, sleeved, into the trunnion hubs, where slip rings carry the
  power across the elevation axis, so nothing flexes as the drum moves. IoU
  0.87. Cables are the 'cable' key (dark rubber by default).

Elevation limits
  A builder may return 'elevation_limits': (min, max) in its extra dict
  (degrees, positive up); build() stores them on the elevation empty as
  'elevation_min_deg' / 'elevation_max_deg'. Remember the empty lifts the
  muzzles with a negative rotation about its local Y.

Dressing a turret in the hull's finish
  build(..., hull_finish=True) uses the hull's own materials for every key the
  hull supplies (paint, accent, metal, steel, lamp_blue ...), as they are,
  instead of the sprite's paints and the kit's weathering. Keys the hull
  leaves out still come from the sprite. sprite_tint=f then mixes the sprite's
  own colour a fraction f into the hull's paint and accent (tinted(): a Mix node
  ahead of the material's base colour, so a procedural finish keeps its
  pattern). The Paragon dresses its Tri-Tachyon guns in its hex ceramic this
  way, with sprite_tint=.5.

The Wolf (P)'s pirate fit
  ioncannon (house='hand'), built as one casting (owner, 2026-10-19: the
  earlier capsule, studs and prongs read as parts stuck together): a low grey
  armoured casing over the rear plan whose flanks run straight on into the two
  field-guide arms (each one swept wedge, _sweep, rising out of the flank and
  closing to a cantilevered tip, a lit guide rail in its inner wall); the green
  capacitor drum seated in a well in the casing, a coil hub with a lit bore on
  its crown, round lit coil ports in the flanks; and on the elevation empty,
  in the slot between the arms, a breech housing rounded about its trunnions,
  the coil-wound spine and the lens at the fire point. Elevates -5 to 20. IoU
  0.85. Hardpoints miningblaster (a ribbed capacitor body with two focusing
  prongs either side of a red-lit emitter face) and annihilator (a ribbed
  launcher box, five tubes across its face, a muzzle anchor at each mouth).
  derive_sprites.py records all three for their colours.

Other catalogue entries (WEAPONS)
  none (dome), and the box launchers harpoonpod, sabotpod, salamanderpod (open
  rails), pilum, typhoon, annihilatorpod (tubes): the rack is drawn with its
  loaded missiles (RENDER_LOADED_MISSILES), on a yoke with elevation rams.
  Not yet: other hardpoints, and energy weapons other than the Tri-Tachyon
  guns above; the Onslaught's built-in TPCs stay in os_turrets.

Hardpoints (fixed mounts)
  build_hardpoint(weapon, prefix, parent, mats, mpu, k=1.0, **over) builds a
  fixed gun on a hull's empty at the slot (+X along the line of fire, z=0 on the
  mount's axis), from HARDPOINT_WEAPONS (kept apart from WEAPONS, so the turret
  catalogue never builds them as turrets). The game draws a hardpoint sprite
  with its slot a quarter of the sprite's length from its back and the fire
  point at hardpointOffsets ahead of it, so most of a big hardpoint runs out
  ahead of its slot. hull_finish / sprite_tint work as for build(); muzzles are
  '<prefix> muzzle anchor <k>'. So far, both sized to a 21 x 22 bay:
  - tachyonlance: a spinal beam projector (the Paragon's bow pair): a power
    block with a capacitor bank on a sled, trunks back to the bay wall, the
    emitter's lens sunk at the root of the beam slot (the fire point, 5 px
    ahead of the slot), two plated prongs with focusing rails, shoulder pods;
  - autopulse: a breech on a sled, a capacitor bulb, a finned cooling flange,
    the hollow emitter barrel down to its lens, a coolant pipe and a
    capacitor box;
  - miningblaster, annihilator: the Wolf (P)'s (above).

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
  legion-geometry (lg_turrets), paragon-geometry (pg_turrets: its Elite fit,
  hull_finish), wolf-geometry (wf_mounts), second-wind-geometry (sw_turrets:
  its cerberus_Standard fit, wrecked and stripped per condition state by
  sw_scars_spine). Lasher, Onslaught, Hermes and Hound still use their own
  turret modules; moving one onto the kit is that hull's session's call.

Cinematics hooks
----------------
Each barrel hangs from its own 'recoil k' empty. A hand builder's 'barrel_root'
carries the barrel's shaft back inside the house, so sliding that empty aft
under fire pushes the barrel into the hull rather than out of a hole in it
(space-combat cinematics). Loaded missiles (Squall's rockets, Hurricane's
MIRV) are each their own 'missile k' empty and objects, so a shot can launch
them; the game sprite draws them loaded, which is why they are modelled.
