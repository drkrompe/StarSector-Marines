FIGHTERS - CINEMATIC GEOMETRY (PASS 1: BLOCKOUT)

Purpose
Modelled fighters for rendering in the Jangala cinematic (C2: the
Hegemony Legion's air wing launching from and recovering to its hangars).
Owner brief: "Create modeled fighters we can use for rendering. aka block
out." Each fighter is carried through ship-model-pipeline pass 1
(.claude/skills/ship-model-pipeline): correct silhouette, sections, mass,
slots and engines, flat-shaded chamfered sections, no detail. Pass 4
scale is done per hull. The next step is the owner's review.

The six fighters with up-res masters in art-source/aircraft/fighter-upres/:
Talon, Wasp, Dagger, Longbow and Thunder are here; the Broadsword lives in
../broadsword-geometry/ (built first, already at pass 2 detail) and joins
the lineup from there. Hoplon is out of scope.

Layout
fg_kit.py           shared helpers, copied from the Broadsword kit (no other
                    hull package is imported); engine() and slot_anchor()
                    give every fighter the same names
build_fighter.py    the driver: one build per fighter (--fighter ID); checks
                    SLOTS/ENGINES against the installed .ship, required
                    names, root, scale figure, seated-pilot fit, renders
compose_fighter.py  top-over-sprite overlay, IoU, the review sheet
ft_<id>.py          one module per fighter (built in parallel by one agent
                    each; Opus subagents, as Sol 6.1 is not offered here)
<id>/               <id>-geometry.blend, geometry.json, renders,
                    top-overlay.png, <id>-geometry.reference.png
lineup.py, compose_lineup.py
                    the common-scale lineup: lineup.png, lineup-top.png,
                    lineup-three-quarter.png, lineup.json, fighters-lineup.blend

Conventions (every fighter)
- +X nose, +Y port, +Z dorsal. 1 Blender unit = 1 sprite pixel = 1 hull
  unit about the .ship centre; slot and engine XY are the .ship's own.
- Append "<NAME> | geometry" from <id>/<id>-geometry.blend (e.g. "TALON |
  geometry"). Everything is parented to the empty "<Name> root" at the
  .ship centre, carrying custom props metres_per_unit and axes. Review
  cameras, lights, the 1.8 m figure and pilot proxies are in "<NAME> |
  review rig" and are not part of the asset.
- Engines, i in .ship engineSlots order: Engine {i} mouth lip / aft-facing
  bell / dark throat (the Second Wind names), and "Engine {i} <port|
  starboard|centre> exhaust anchor" at the mouth plane, local +X aft.
- Weapons: "Weapon <slot id> anchor" at every .ship slot (+X along the slot
  angle), and "Weapon <slot id> muzzle" at the tip of every drawn gun.
- Placing beside another hull: scale the root by
  this metres_per_unit / that hull's (the Legion is 0.65).

Scale: each hull its own (owner policy)
Every crewed fighter is scaled from its cockpit: the canopy against real
single-seat canopies (2.6-3.5 m long, 0.9-1.3 m at the sill), a seated
1.8 m pilot proxy (seat pan 0.55 m below the deck skin, crown 0.92 m
above the seat) that must clear the glass, guns and drives, with real
fighters of 12-22 m as the sanity check. The crewless Wasp is scaled
from its gun, drive cans and size.

Fighter     m/unit  range      size (m)          canopy (m)  head clear  IoU
Thunder     0.45    0.40-0.50  18.6 x 10.9 x 2.6  3.24x1.17   0.28 m      0.890 *
Talon       0.75    0.68-0.85  16.5 x 16.5 x 3.3  2.93x1.20   0.25 m      0.977
Dagger      0.50    0.43-0.58  16.4 x 12.1 x 2.2  3.10x1.15   0.21 m      0.959
Broadsword  0.50    0.43-0.57  15.5 x 14.1 x 3.3  3.0 x1.9    0.21 m      0.931 (pass 2)
Longbow     0.45    0.40-0.55  15.0 x 14.4 x 2.5  2.66x1.12   0.14 m      0.951
Wasp        0.50    0.40-0.65   9.5 x  8.0 x 1.5  (drone)     -           0.951
Each <id>/geometry.json has the method, cross-checks and cockpit fit.

For review
- Relative sizes follow the cockpits, not the game. In Starsector the
  Talon is a light interceptor drawn smaller than the Broadsword; scaled by
  its own large canopy patch it comes out 16.5 m, a metre longer. Choosing
  the low end of the Talon's range (0.68) and the high end of the
  Broadsword's (0.57) would restore the game's order; that is the owner's
  call.
- * Thunder: the .ship draws graphics/ships/thunder2.png (symmetric gun
  booms), while the up-res master and the mod's runtime texture follow
  thunder.png (an offset lance and port prongs). The master is a hybrid
  (a centred lance plus symmetric booms) and the model follows it. IoU is
  0.890 against thunder2.png (the recorded one) and 0.778 against
  thunder.png. Reaching 0.93 means dropping the lance (thunder2) or
  modelling thunder.png's asymmetry; the owner chooses.
- Asymmetric hulls keep their asymmetry: the Talon (gun nacelle to port,
  cockpit pod to starboard), the Dagger (port torpedo store) and the
  Longbow (emitter to port, missile cell to starboard).
- Integer slots that straddle an even-width sprite's centre line are
  modelled symmetric at their mean; anchors keep the exact .ship XY. Where
  the fighter-upres manifest gives painted-nozzle offsets (Wasp,
  Longbow), the visible bells and exhaust anchors sit at slot + offset.
  Each fighter's NOTES in geometry.json record its choices.
- Heights and undersides are proposed; only the plan comes from the game.
  Flat materials only.

Rebuild (from this folder; one Blender at a time)
  "/c/Program Files/Blender Foundation/Blender 5.2/blender.exe" --background --factory-startup --python build_fighter.py -- --fighter talon
  python compose_fighter.py --fighter talon
  (thunder: python compose_fighter.py --fighter thunder --sprite "<install>/graphics/ships/thunder2.png")
  ".../blender.exe" --background --factory-startup --python lineup.py
  python compose_lineup.py
