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
- Placing beside another hull: scale the root by this metres_per_unit /
  that hull's, read from its geometry.json. The Legion's is 0.61 (local
  main 496340563): root scales Broadsword, Wasp, Dagger 0.82; Talon 0.90;
  Longbow, Thunder 0.74. The Legion has one fly-through hangar tunnel
  under each wing arm ("Wing <side> hangar", local main 8115783f5; figures
  in its geometry.json flight_decks[].hangar): 13 units high, at least 42
  clear wide. The widest span here (Longbow, 14.4 m) is about 24 Legion
  units and the tallest (Broadsword, 3.3 m) about 5.4, so every fighter
  clears every section.

Scale: each hull its own (owner policy)
Every crewed fighter is scaled from its cockpit, guns and drives, with
real fighters of 9-22 m as the sanity check. Canopies need not all be
the same size, only plausible (owner): a seated 1.8 m pilot proxy (seat
pan 0.55 m below the deck skin, crown 0.92 m above the seat) must clear
the glass. So the Talon is a compact interceptor with a small,
Gnat-like canopy, smaller than the Broadsword as in the game. The crewless Wasp is scaled
from its gun, drive cans and size.

Fighter     m/unit  range      size (m)          canopy (m)  head clear  IoU
Thunder     0.45    0.40-0.50  18.5 x 10.8 x 2.6  3.24x1.17   0.28 m      0.956 *
Dagger      0.50    0.43-0.58  16.4 x 12.1 x 2.2  3.10x1.15   0.21 m      0.959
Broadsword  0.50    0.43-0.57  15.5 x 14.1 x 3.3  3.0 x1.9    0.21 m      0.931 (pass 2)
Longbow     0.45    0.40-0.55  15.0 x 14.4 x 2.5  2.66x1.12   0.14 m      0.951
Talon       0.55    0.50-0.62  12.1 x 12.1 x 2.4  2.15x0.88   0.08 m      0.977
Wasp        0.50    0.40-0.65   9.8 x  8.1 x 2.4  (drone)     -           0.909 **
Each <id>/geometry.json has the method, cross-checks and cockpit fit.

For review
- * Thunder follows graphics/ships/thunder.png, which is asymmetric (the
  owner's call): a port gun block with two prongs (ion cannon on the outer
  one) and a long lance offset to starboard, with the dual MG on its root.
  The .ship itself draws thunder2.png (symmetric booms), and master-v1 is a
  mirrored hybrid, so the master gave sections only, not the plan. IoU
  0.956 against thunder.png.
- ** Wasp: its arms are drawn as pixel stairs on the sprite; the model
  runs each edge straight through the stair midpoints instead of tracing
  them, so the arms read as blades, not pixels. The stair corners it cuts
  and leaves cost the IoU (0.951 traced), putting it below the 0.93 aim;
  on arms one to three pixels wide they are a large share. It has no
  landing gear: its arms droop and taper, and a hooked claw under each
  tip grips the deck. The deck is pitched 6 degrees in the hull frame, so
  parked it crouches nose-down with its tail raised; the hull axes stay
  the flight frame. The build fits the deck plane through the claws,
  fails if they are not on it or anything else reaches it (body 0.42 m
  clear; parked_check in geometry.json, FEET in the module), and its
  sheet shows it parked, the deck levelled, in place of a cockpit cutaway.
- Asymmetric hulls keep their asymmetry: the Thunder (see above), the Talon (gun nacelle to port,
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
  ".../blender.exe" --background --factory-startup --python lineup.py
  python compose_lineup.py
