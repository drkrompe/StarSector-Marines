Status: ACTIVE
Written: 2026-10-20

# Ship detail guide

This is the detail language for every cinematic hull, taken from the two
hulls the owner accepted. The **Legion** (XIV) battlecarrier is in
`legion-geometry/` and the **Colossus** heavy freighter is in
`colossus-geometry/`. The owner's standing rule: every visible part must hold
up at a close view. This guide says what that means in metres, and
`ship-detail-kit/` builds it. `ship-detail-kit/check_detail.py` measures a
build against it.

Read it before pass 2 (plausible detailing) and again before each pass-3 round
(the `ship-model-pipeline` skill). Every number is in **metres**, measured from
the reference build scripts. Convert to hull units through the hull's
`metres_per_unit` (`ship-scale.md`) with `sdk.Scale(mpu).u(metres)`.

**Figure plates.** Each section cites a plate made by
`ship-detail-kit/guide_plates.py`. The plates are crops of the accepted
review renders, not new renders, and live on the shared Drive at
`review/ship-detail-guide/2026-10-20_guide-plates_windows/`. Plates `00`
through `09` follow the order of this guide.

## Vocabulary

Use these words in feedback, captions and object names.

| Word | Means |
|------|-------|
| **plate** | One flat or folded piece of skin with its own edge. A hull is plates, never one skin. |
| **module** | A run of plates that repeats along a line: an armour belt module, a pod segment. |
| **seam** | The gap between two plates or modules. It is cut into the geometry and shows dark. |
| **strata** | A long horizontal groove round a body, cut skin-deep. |
| **underlay** | A dark backing body behind a run of modules, which is what a seam shows. |
| **recess** | A pocket cut into a plate that something sits in: glazing, grilles, a door, a bay. |
| **frame / surround** | The proud trim round a recess. **Jamb**: the side of an opening. **Header**: the top. **Sill**: the bottom. |
| **coaming** | The raised lip round a deck opening (hatch, well, bay). |
| **brow / eyebrow** | The armoured hood over glazing. |
| **fitting** | Anything person-sized seated on a plate: a hatch, vent, lamp, rail, ladder, cleat, RCS block. |
| **seated** | Sitting on the surface below it, sunk 2–5 cm into it, never floating above it. |
| **segment rule** | The minimum number of sides for a round part (Edge treatment, below). |
| **lights layer** | The game's `<hull>_lights.png`. The only places a hull may glow. |

## The reference hulls (plate 00)

The two hulls differ in era and scale; they share a method.

- **Legion: a capital warship at 0.99 m/unit.** Its armour is run in modules
  along plan lines over a dark underlay, a plated command dome sits in an
  armour collar, and its turrets come from the turret kit. It gets most of its
  crispness from chamfers in the plate profiles, not from bevel modifiers.
- **Colossus: a Domain-era freighter at 0.5 m/unit.** It has people-sized
  fittings everywhere. Every round part follows the segment rule, every box
  has a bevel, seams are cut, glazing is recessed and framed, and rails,
  ladders and doors are sized to a 1.8 m figure.

The Colossus is the better reference for **fittings**; the Legion is the better
reference for **armour, belts and massing**.

## Armour plating and plate breaks (plate 01)

A surface reads as built when it is broken into plates that a yard could have
cut, and the breaks are geometry.

| | Legion (capital) | Colossus (freighter) |
|---|---|---|
| Module or plate length | belt modules 14.9 m; strake plates 10.9–16.8 m | pod segments 7–8.5 m; bridge plates about 3.8 m |
| Seam width | 0.99 m between belt modules; 0.79 m between strake plates; 0.69 m on the dome | 0.3 m |
| Seam depth | to the underlay | cut 0.17–0.3 m |
| Behind the seam | dark underlay body (`armour_run(..., underlay=)`) | the cut faces take the recess material (`seam_cut`) |

- Plate length scales with the ship: about **8–15 plate lengths** along a
  hull's long axis reads right at both sizes. A plate over 25 m long with no
  break reads as a blockout.
- Stack courses on a large flank (the Legion's three strakes and buttresses).
  On a freighter, add one strata groove round each major body
  (`skin_groove`).
- Plates step: an upper course is set in from the one below it
  (`armour_run`'s base and upper profiles), so a flank has a shadow line
  every few metres.
- No face of the hull should stay flat and untouched over about
  **100 m²**: break it with a seam, a fitting, a cover or a course step. The
  check reports what remains.

## Seams

- **Cut, never painted.** A seam is a groove in the mesh, 0.25–0.35 m wide on a
  module hull and 0.6–1.0 m wide between capital armour modules, 0.15–0.3 m
  deep. Texture lines go on top; they don't replace the cut.
- Seams line up across neighbouring plates and turn corners. A seam that stops
  in the middle of a plate is a crack and is used only on purpose (wear).
- Bolt lines run along a seam at a 0.45–0.7 m pitch, with heads of 13–15 cm
  (Colossus). Use `studs` or `bolted_plate`. Heads are hexagonal (6 sides),
  which is exempt from the segment rule.

## Windows and glazing (plate 02)

Glass is a **dark** material set back in a recess under a frame. A few panes
may be lit; none are lit flush with the hull. Use `window_row` for a row of
panes and `round_port` for a single round one.

| Part | Colossus | Legion |
|---|---|---|
| Pane | windscreen 2.0 × 1.6 m; wing ports 1.0 × 0.85 m | slit viewports 1.8 × 0.8 m |
| Mullion | 0.40 wide × 0.22 m deep | between plates |
| Recess | 0.33 m | 0.3 m |
| Surround | 0.2 m, proud | the neighbouring plates |
| Brow | 1.6 m deep on gussets | 1.4 m |
| Round port | 0.8 m glass, 1.15 m rim, 8 bolts, a dead-light lug | n/a |

- Lit panes: no more than about a third of a row, and only where the lights
  layer marks a viewport. `window_row(lit=(...))` keeps the emissive material
  on those panes alone.
- Glazing always has a frame object whose name says so (`... frame`,
  `... surround`); the check looks for it.

## Hatches and doors (plate 03)

Everything a crew would walk through is sized to a **1.8 m figure**.

| Opening | Size | Detail |
|---|---|---|
| Personnel door (Colossus) | leaf 1.1 × 2.1–2.3 m | 0.25 m jambs and header proud of the wall, leaf set back 5 cm, hinge knuckles, lever handle, step, 1.1 m hand rails each side |
| Flank hatch (Legion) | 1.46 × 2.07 m | 0.5 m frame |
| Deck hatch (Colossus) | 1.9 × 1.9 m | 0.4 m coaming, 0.15 m lid, hinge pin, two dogs, handle |
| Skylight | 1.9 × 1.2 m | glazed lid with guard bars |
| Service cover | about 1.5–3 m | bolted plate, 0.35 m thick |

Use `wall_door`, `deck_hatch` and `bolted_plate`. A hatch that has to open is
built closed, with its parts parented to a named hinge empty
(`ship-model-pipeline`, Pass 3).

## Vents, grilles and RCS (plate 04)

- **Wall vent** (`wall_vent`): about 1.3 × 0.75 m, five slats, in a recess with
  a backing.
- **Louvre bank** (`louvre_vent`): blades in a box, set into a well, never laid
  on top of a plate.
- **Grille in a recess** (Legion stern and bow): slats inside a framed pocket.
  A grille's dark interior is part of the read.
- **Mushroom vent** (`mushroom_vent`): 0.42 m radius cap, 0.85 m tall, with a
  dark gap under the cap.
- **RCS cluster** (`rcs_cluster`): a round pedestal, a 1.2 m bolted housing and
  four 0.43 m bells (fore or aft, outboard, up, and a down-slant). Mount one on
  each corner a ship would need to turn about. Bells are smooth-shaded and
  follow the segment rule.

## Masts, antennae, dishes and lamps (plate 05)

- **Mast** (`mast`, from the Colossus bridge): 1.3 m bevelled base, a 0.175 m
  radius pole 3.5 m tall at 16 segments, a 3.2 m yard with braces, whip
  antennae with insulators, and a head lamp plus yard lamps.
- **Dish** (`dish`): a dished reflector of 1.7 m radius at 48 segments, with a
  rolled rim, 12 back ribs and a hoop, a hub, three feed struts and a feed
  horn, on an azimuth turntable and an elevation yoke. Tilt it off zenith; a
  dish pointing straight up reads as a bowl.
- **Sensor ring or dome**: 48 segments for the ring, 32 for a 1 m dome.
- **Lamps** (`lamp_fitting`): a 0.2 m lens recessed in a housing on a bracket,
  with an optional wire guard. **Only the lens is emissive**, and only where
  the lights layer has a light. A lamp is never a bare glowing sphere or
  cube.

## Railings, walkways and ladders (plate 06)

| Part | Size |
|---|---|
| Rail height | 1.1 m, with a knee rail at half height |
| Stanchion pitch | 1.4 m (Colossus); 2.97 m on the Legion catwalk |
| Post / rail section | 6 cm / 5 cm (`handrail`); the Colossus's default `rail_run` posts are 11 cm |
| Walkway width | 1.2–1.46 m |
| Ladder | stiles 0.55 m apart, rungs every 0.3 m, stand-off brackets to the wall, grab hoops over the top |

- Rails go round anything a person could walk off: roof decks, gantries, pod
  tops and the edges of bays.
- A ladder runs **somewhere**: from a deck to a roof, over a shoulder or to a
  hatch, never up a blank wall.

## Bays, jambs and coamings (plate 07)

- A bay is a **recess with a frame**. It has jambs and a header proud of the
  hull, a sill or coaming at the bottom, a lining inside darker than the hull,
  and something inside it at person scale: pallets, a lit lane, a lift, cell
  guides.
- Legion hangar mouths: jambs, header, sill and a lit lane, with a red light
  strip only where the lights layer has one.
- Colossus container bays: the boxes stand end-on in cell guides, so the bay
  reads as a rack.
- Ventral wells and hatches sit in framed recesses, not flush.

## Exposed structure and machinery (plate 08)

- **Open structure** (`space_frame`) where bodies are joined by structure:
  chords and lacing, with grating where crew would walk.
- **Machinery** sits on a bed: drive cans in collars, trunks and conduits
  (`pipes`, `bent_rail`) that run from somewhere to somewhere, and tanks held
  in straps on skids with feed lines.
- Pipes follow the segment rule. Under 5 cm radius 8 sides are allowed; above
  that, at least 12.
- Machinery is **seated**: every pipe ends in a flange or enters a body, and
  every tank sits in its straps.

## Edge treatment

The segment rule and the bevel table, both defined in
`ship_detail_kit.py`:

- **Segment rule.** The chord of any round may sit at most **max(5 mm, r/400)**
  inside the true arc, rounded up to a multiple of 4, with a minimum of 12.
  - 8 sides are allowed under 5 cm radius.
  - 6-sided bolt and nut heads are exempt.
  - Examples: a 0.17 m mast takes 16, a 0.4 m housing 24, a 1 m dome 32, a
    1.7 m dish 48.
  - `sdk.segments(r_m)` or `Scale.n(r_units)` gives the count.
- **Smooth shading** on every curved body (`smooth`: shade smooth with
  sharp-from-angle at 35°), never on plate.
- **Bevels**, angle-limited, one segment: hull plates 0.12 m, modules 0.06 m,
  fittings 0.03 m, small parts 0.015 m (`Scale.bevel(kind)`). The Colossus uses
  7.5 cm on its boxes and 6 cm on its prisms. Two or three segments only on
  parts seen within a few metres.
- A plate profile with its own chamfers (the Legion's belts) counts as
  bevelled. A plate with neither a bevel nor a chamfer catches no highlight
  and reads as a blockout.

## Low-tech and pirate wear in the form

The accepted references are both clean, so there is no reference image for
wear yet. Wear is **form first, texture second**.

- **Patched plates.** A plate of a different thickness or material bolted over
  a breach, 5–10 cm proud, with an irregular bolt line. Use `bolted_plate` with
  a mismatched material.
- **Missing and replaced fittings.** A rail with a gap in it, a stanchion bent
  or missing, a dish with a panel off, a mast with one whip.
- **Field welds.** A weld bead is a thin raised rod along a seam that is not on
  the module grid.
- **Scavenged add-ons.** Tanks, boxes and armour strapped on rather than built
  in (straps, U-bolts, chains), at an angle that doesn't match the hull.
- **Damage.** Holes and torn plate come from the condition-state pass
  (`second-wind-pipeline.md`), not from pass 2.

Pirate variants (the Wolf (P)) add these on top of the base model. They don't
replace the plating, seams or fittings above with mess: the wear has to read
as having happened to a ship that was once built properly.

## Turrets (plate 09)

Turrets come from the shared turret kit
(`turret-kit/turret_kit.py`, `turret-kit/README.txt`). Don't model them by
hand. What a turret must show:

- a barbette ring seated on the hull, stepped or bolted;
- a house with ribs or plates, set back from the barbette edge;
- a trunnion and mantlet the barrels pivot in;
- barrels with a jacket step and a muzzle, round by the segment rule.

Train a turret off the centreline for review renders, so the barrels are seen
to clear the deck. `turret-kit/check_gimbal.py` and `check_seating.py` own
turret validity.

## Never

- **Faceted curves.** A visible polygon edge on a dome, drum, tank, pipe,
  dish or lens, or any round below the segment rule.
- **Glowing rectangles for windows.** No flush emissive faces. Glass is dark,
  recessed and framed; lit panes are few and sit on the lights layer.
- **Emissives anywhere off the lights layer.** That includes vents, grilles
  and slats lit to look busy.
- **Bare boxes and cylinders.** A primitive with no bevel, no seam, no
  fitting and nothing seated on it. A box is a housing only when it has a
  bevel, a lid line, bolts or a hatch.
- **Floating greebles.** Anything not seated, or a sunk part whose top is lost
  in the plate. Every fitting is seated by the downward ray-cast
  (`seat`, `Caster`) or placed on a known surface.
- **Painted seams.** A seam that exists only in the texture.
- **Ladders and rails that go nowhere.**
- **One-size detail.** A person-sized fitting enlarged until it reads at the
  sprite's scale. Detail stays at real size; add more of it instead.
- **Large flat untouched faces** (over about 100 m²).

## Checking a build

Run `ship-detail-kit/check_detail.py` after the build driver, in the same
Blender process. The flags are read from `SDK_CHECK`, because the build
drivers' argparse is strict:

```bash
SDK_CHECK="--mpu 0.5 --label colossus --out build/check-colossus.json --exclude '^(Weapon|Turret) '" sh ../../blender.sh --background --factory-startup --python build_colossus.py --python ../ship-detail-kit/check_detail.py -- --views '' --no-save
```

Add `--lights <hull>_lights.png --ship <hull>.ship` when the hull has a lights
layer. The check reports these categories:

- **faceted**: objects whose rounds fall below the segment rule, at 0.8
  tolerance. It counts regular n-gon caps and crease runs. A run counts when
  it turns at least 75°, sits more than 0.6 m from any plating, and is not a
  hull facet over 5 m radius, which is listed separately.
- **unbevelled**: the length of hard convex edges (60° and sharper) between
  faces at least 0.5 m wide, per 100 m² of surface.
- **emissive outside**: emissive faces off the lights layer, or, without a
  lights layer, on objects whose names aren't lamp or light names. Engines are
  exempt.
- **unframed glazing**: glass with no frame around it.
- **bare patches**: coplanar regions with nothing in front of them over the
  threshold (100 m² by default).
- **bare boxes and cylinders**: primitives with no bevel and nothing touching
  them.

A pass-2 or pass-3 build goes to the owner only after the check has run. Each
remaining flag is either fixed or named in the review's `NOTES.md` with the
reason it stays.

### First run (2026-10-20)

Turrets excluded; mpu as in `ship-scale.md`.

| Hull | m/u | Faceted objects | Unbevelled m / 100 m² | Emissive off-layer | Bare patches (largest m²) | Bare boxes / cylinders |
|---|---|---|---|---|---|---|
| Legion | 0.99 | 140 | 15.65 | 6 | 97 (2,644) | 210 / 96 |
| Colossus | 0.5 | 170 | 2.88 | 3 | 11 (263) | 25 / 5 |
| Manticore (P) | 0.59 | 14 | 7.8 | 19 | 6 (145) | 0 / 2 |
| Wolf (P) | 0.76 | 6 | 3.85 | 4 | 0 | 0 / 0 |
| Lasher | 0.95 | 57 | 8.6 | 4 (0% on its lights layer) | 5 (310) | 0 / 0 |

How to read the table:

- **The references carry debt too.** The table measures the standard; it is
  not a ranking.
  - Legion: 8-segment masts, ico-sphere lamps, 20-sided drive cans, hexagonal
    pipes on the flight decks, and few bevel modifiers. Its plate chamfers keep
    its close views crisp, so its unbevelled figure overstates the problem.
  - Colossus: 8-sided conduits and 19-segment tanks.
- **The Legion's bare patches are mostly its broad hull tops** under the
  turrets, belts and deck tiles. They read in the accepted renders through
  texture and tile seams, which the check doesn't see.
- **Emissives off the layer:**
  - Legion: control booth glazing and gallery ports.
  - Colossus: windscreen and wing ports. Lit glazing is allowed on the lights
    layer, and the Colossus has no lights layer to test against.
  - Manticore: core viewports.
  - Wolf: vent slats.
  - Lasher: running lights that miss its lights layer entirely.
- **Nothing on these hulls was changed by this run.** Each hull's owning
  session decides what to fix.

### The kit sampler

`ship-detail-kit/kit_sampler.py` builds one of each metric builder on a 40 m
deckhouse. It scores zero on faceted, unbevelled, emissive and glazing at both
0.5 and 0.99 m/unit. Its only flags are the sampler's own blank walls. Re-run
it after changing the kit.
