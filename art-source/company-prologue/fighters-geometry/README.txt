FIGHTERS - CINEMATIC GEOMETRY (PASS 2: PLAUSIBLE DETAIL)

Purpose
Modelled fighters for rendering in the Jangala cinematic (C2: the
Hegemony Legion's air wing launching from and recovering to its hangars).
Owner brief: "Create modeled fighters we can use for rendering. aka block
out." Each fighter follows the ship-model-pipeline skill
(.claude/skills/ship-model-pipeline). Pass 1 (blockout) was signed off
for now, and its renders are kept in <id>/reference-history/v1-blockout/.
Pass 2 adds the detail each sprite and up-res master imply, at the
fighter's own real-world scale. Pass 3 (owner review) is next. There are
no damage states, textures or VFX yet.

The six fighters with up-res masters in art-source/aircraft/fighter-upres/:
Talon, Wasp, Dagger, Longbow and Thunder are here; the Broadsword lives in
../broadsword-geometry/ (its own package, also at pass 2, and the house
reference for detail density) and joins the lineup from there. Hoplon is
out of scope.

Layout
fg_kit.py           shared helpers, copied from the Broadsword kit (no other
                    hull package is imported); engine() and slot_anchor()
                    give every fighter the same names; the pass-2 helpers
                    (below) size everything in metres through Kit.u()
build_fighter.py    the driver: one build per fighter (--fighter ID); checks
                    SLOTS/ENGINES against the installed .ship, required
                    names, root, scale figure, seated-pilot fit, parked
                    check, renders; --blockout builds pass 1 only
compose_fighter.py  top-over-sprite overlay, IoU, the review sheet, and
                    the before/after sheets against the blockout
check_clashes.py    overlap sweep of one integrated build -> <id>/clashes.txt
fg_gear.py          retractable landing gear (below): hinges, doors, bay
                    liners, the gear_down knob's drivers, the stow clash sweep,
                    and the gear shapes (box-section struts, wedge shoes,
                    claws, links)
review_gear.py      gear review renders from a textured build: flying,
                    mid-stow, landed and a landed close-up per fighter
package_build.py    verifies a textured build's gear knob, saves it flying
                    (<id>.blend) with <id>-manifest.json, for Drive
ft_<id>.py          one module per fighter: build() is pass 1, detail() is
                    pass 2 (run unless --blockout), CLOSEUPS its detail cameras
<id>/               <id>-geometry.blend, geometry.json, renders,
                    top-overlay.png, <id>-geometry.reference.png,
                    pass2-before-after.png (close-ups),
                    pass2-before-after-views.png (standard views),
                    clashes.txt, reference-history/v1-blockout/
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
  Pass 2 adds Engine {i} centre body / stator vane {k} / collar rib {k}.
- Weapons: "Weapon <slot id> anchor" at every .ship slot (+X along the slot
  angle), and "Weapon <slot id> muzzle" at the tip of every drawn gun.
- Pass-2 parts carry the custom property detail_pass = 2; every pass-1
  name, anchor and muzzle is unchanged.
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
from its gun, drive cans and size. Pass 2 changed no scale, canopy or
seat: every cockpit fit is as at pass 1.

Fighter     m/unit  range      size (m)          canopy (m)  head clear  IoU pass 1 -> 2
Thunder     0.45    0.40-0.50  18.5 x 10.8 x 2.6  3.24x1.17   0.28 m      0.956 -> 0.956 *
Dagger      0.50    0.43-0.58  16.4 x 12.1 x 2.3  3.10x1.15   0.21 m      0.959 -> 0.959
Broadsword  0.50    0.43-0.57  15.5 x 14.1 x 3.3  3.0 x1.9    0.21 m      0.927 -> 0.931 (own package)
Longbow     0.45    0.40-0.55  15.0 x 14.4 x 2.6  2.66x1.12   0.14 m      0.951 -> 0.951
Talon       0.55    0.50-0.62  12.1 x 12.1 x 2.5  2.15x0.88   0.08 m      0.977 -> 0.977
Wasp        0.50    0.40-0.65   9.8 x  8.1 x 2.4  (drone)     -           0.908 -> 0.908 **
Each <id>/geometry.json has the method, cross-checks, cockpit fit and IoU.
Heights grew by at most 0.1 m where an antenna or blister now stands
above the old top.

Pass 2 detail (budget: hero-capable, Broadsword density, not greebles)
Read off each up-res master at 4-8x, and from each hull's role. Every size
is in metres at the fighter's own scale (Kit.u), so a panel line is 5 cm
wide and 2.5-4 cm deep on all of them, and a grille slat or a bolt is the
same real size on the 9.8 m Wasp as on the 18.5 m Thunder.
- Panel lines are constant-depth grooves (Kit.engrave): a slab marks
  where, an inward offset of the part's own finished surface limits how
  deep, so lines follow chamfers and lofts without being traced.
- Raised plates, collar ribs and canopy frames are constant-thickness
  layers of a part's own surface inside a bounding solid (Kit.skin): a
  canopy sill and bows hug the glass, a rib hugs the drum.
- Seated fittings (grilles, vents, sensor windows, blisters, blade
  antennae: Kit.grille, seated, fairing, blade) lie on the surface normal
  under them and are bedded into it.
- Drives: every bell gets a centre body and a ring of stator vanes on its
  throat, and collar ribs on its drive unit ahead of the lip.
Per fighter (each module's NOTES has the full list):
- Talon: vulcan shroud collar, banded jacket, six-barrel cluster, breech
  and feed housing; closed swarmer-bay door at WS 003; canopy sill, arch
  and bow; nose strip with sensor blister, dorsal strip and vent;
  shoulder intakes, spine grille and slots, blade antenna, wing vents.
  The aft body's tail face used to stand inside each bell ahead of the
  throat, so the drives read as red armour; the bell well now runs
  through it.
- Wasp: articulated arms and claws (below); emitter coils, lens and
  cooling ribs; shoulder vents, flank sensor windows, tail grille and
  lamp; blade seams and tip sensor windows; can vents and ribs.
- Dagger: prong-tip vents and sensor blisters; a closed energy aperture
  on the notch root (WS 001, unfitted); canopy frame; flank intakes;
  drive-block grille, antenna and rib; torpedo store joint bands, tail
  fins, seeker window and sway braces; fin and pod vents.
- Longbow: prong sensor blisters, nose strip and lamp; canopy frame;
  dorsal sensor housing, antenna, aft grille, aft-arm vents; emitter
  collar and coils, housing window; four sabot tubes in the rack mouth
  and hold-down latches; pod slots, lamps and ribs.
- Thunder: lance grille, tip slot and radome seam; ion-cannon coils,
  focus ring and bore; inner-prong bands; dual-MG jackets, brakes and
  breech cover; gun-block vent; three launch cells in the swarmer bay;
  canopy frame; dorsal spine with ribbed grille; flank intakes; pod
  intakes, vents and ribs; tip-plate slots.

Wasp articulation (owner ask)
- Arm hinge: each pivot housing is the sweep bearing, capped with a
  bright slewing ring, cap and six-bolt circle. A fifth of the way out
  each arm is cut by a hinge seam (frame and blade) with two knuckles
  and a bright pin across it: the joint the arm droops about. A
  two-stage droop actuator (cylinder and rod) runs from the hub's
  underside to the arm's, at nearly half its length.
- Claw: cheek plates straddle each claw where it leaves the arm's
  underside, with a knuckle pin through them, and a tendon actuator
  runs from the arm's underside to the claw's back.
- The claws are still the only parts on the deck: the parked check finds
  the four soles coplanar (spread 0.000 m) at the same 6.0 degree
  nose-down crouch, the body clears by 0.42 m as before, and the lowest
  non-foot part is now a fore claw cheek at 0.09 m (was the fore arm
  frame at 0.26 m).

Clash check (check_clashes.py; <id>/clashes.txt)
Every mesh is pass-1 body or pass-2 detail. Contacts inside one fitting
(a grille's slats and well, a drive's centre body and vanes, the canopy
frame's pieces) are counted, not listed; seated detail bedded into the
body is listed per part; detail crossing detail across fittings and
detail touching nothing are the findings. Final builds: no floating
detail on any fighter, and no cross-fitting crossings except where a
dorsal strip runs up to the canopy sill by design (the Talon's dorsal
strip at the glass's aft end, the Longbow's nose strip at its front, with
its lamp set on the strip). Body-on-body pairs match a blockout build of
the same module pair for pair, except that the Talon's four bell and
throat intersections with its aft body are gone: that is the bell-well
fix above.

Retractable landing gear (fg_gear; owner 2026-10-20)
The Talon, Dagger, Longbow and Thunder were built with fixed gear and flew
with it hanging down. Their gear now retracts, and the thin piston legs and
puck feet are gone (owner: "a piston with a hockey puck foot is ugly"): legs
are box-section shock struts that taper like wedges, on broad wedge shoes
(Dagger), clawed pads (Longbow) or twin wheels on a trailing arm (Thunder);
the Talon's sled runners hang on wedge parallelogram links. Each fighter's
landed stance is unchanged: its feet stand where they stood, on DECK_Z.
- Built down; every moving part is parented to a hinge empty "<part> hinge"
  that turns about its local Y (a "<part> hinge mount" above it carries an
  axis that is not the hull's Y). The hinge stores stowed_deg, deployed_deg
  and knob_span.
- "<Name> control" (Talon control, ...) under the root carries the knob
  gear_down: 0 stowed, doors shut (flight; the saved pose and the default),
  1 landed. Drivers on every hinge evaluate fg_gear.Gear.pose: doors open
  over the first 30%, legs swing over 25-100%, Longbow claws uncurl over the
  last 15%. A pose set from Python needs control.update_tag().
- Legs fold into the solid above them (as the Broadsword's do), under doors:
  thin plates laid 8 mm off the skin, hinged on the bay's long edges, with a
  dark liner on the skin under them. The Talon has no doors: its runners lift
  flat into the trough of a skid fairing under each pod (and a slot under the
  drum) and their soles close it.
- The build fails unless fg_gear.sweep passes: at 16 knob values no moving
  part crosses anything but the solids of its own bay and its liners, no leg
  crosses its own doors, and stowed, every leg vertex lies between the skin's
  underside and top. The result is landing_gear.clash_sweep in geometry.json,
  with the hinges and bays. The parked check runs at gear_down 1;
  dimensions are landed, dimensions.flight_* gear up.
- Doors, liners, fairings and gear take class swatches when surfaced; the
  hull skins are untouched (their face selection is per object, so nothing
  new changes it).

For review
- * Thunder follows graphics/ships/thunder.png, which is asymmetric (the
  owner's call): a port gun block with two prongs (ion cannon on the outer
  one) and a long lance offset to starboard, with the dual MG on its root.
  The .ship itself draws thunder2.png (symmetric booms), and master-v1 is a
  mirrored hybrid, so the master gave sections and parts only, not the plan.
- ** Wasp: its arms are drawn as pixel stairs on the sprite; the model
  runs each edge straight through the stair midpoints instead of tracing
  them, so the arms read as blades, not pixels. The stair corners it cuts
  and leaves cost the IoU (0.951 traced), putting it below the 0.93 aim;
  on arms one to three pixels wide they are a large share. It has no
  landing gear: its arms droop and taper, and a hooked claw under each
  tip grips the deck. The deck is pitched 6 degrees in the hull frame, so
  parked it crouches nose-down with its tail raised; the hull axes stay
  the flight frame. The build fits the deck plane through the claws,
  fails if they are not on it or anything else reaches it (parked_check
  in geometry.json, FEET in the module), and its sheet shows it parked,
  the deck levelled, in place of a cockpit cutaway.
- Asymmetric hulls keep their asymmetry: the Thunder (see above), the Talon (gun nacelle to port,
  cockpit pod to starboard), the Dagger (port torpedo store) and the
  Longbow (emitter to port, missile cell to starboard).
- Integer slots that straddle an even-width sprite's centre line are
  modelled symmetric at their mean; anchors keep the exact .ship XY. Where
  the fighter-upres manifest gives painted-nozzle offsets (Wasp,
  Longbow), the visible bells and exhaust anchors sit at slot + offset.
  Each fighter's NOTES in geometry.json record its choices.
- Heights and undersides are proposed; only the plan comes from the game.
  Flat materials only (each fighter's armour, gunmetal, bright steel,
  dark recess, glass); no emissives, as none of these hulls has a lights
  layer (the Talon's glow files are drive glows).

Rebuild (from this folder; one Blender at a time)
  (--gear-knob 1 saves the gear down; the surfacing build uses it)
  sh ../../blender.sh --background --factory-startup --python build_fighter.py -- --fighter talon
  python compose_fighter.py --fighter talon
  sh ../../blender.sh --background talon/talon-geometry.blend --python check_clashes.py
  sh ../../blender.sh --background --factory-startup --python lineup.py
  python compose_lineup.py
Blockout before-shots for new close-up cameras:
  sh ../../blender.sh --background --factory-startup --python build_fighter.py -- --fighter talon --blockout --out talon/reference-history/v1-blockout --no-save --views details
Options: --views (top, port-side, front, three-quarter, cockpit-check,
parked, details or a close-up's name), --cam, --samples, --no-save,
--blockout (see the driver's docstring).
