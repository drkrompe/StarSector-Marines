---
name: ship-model-pipeline
description: Build a Starsector hull as a scripted Blender model for cinematics and carry it through the repeatable passes - blockout, plausible detailing, review loop, real-world scale, condition states, texturing, VFX/lighting and cinematic export. Use when starting a new ship model, advancing an existing one (Second Wind, Hound (A), Mudskipper) to its next pass, or reviewing a ship-model session's output.
---

# Ship model pipeline

Every cinematic ship goes through the same passes, in order. The Second Wind
(`art-source/company-prologue/second-wind-geometry/`) is the worked example
for each one. Its `second-wind-pipeline.md` is the full contract for condition
states and surfacing; this skill is the per-hull procedure that generalises it.

A pass is finished only when its exit check holds and the work is merged to
local `main` under CLAUDE.md's session-worktree workflow. Ask the owner to
review between passes, not inside them.

## Package layout

One package per hull: `art-source/<area>/<hull>-geometry/`. `<area>` is
`company-prologue` until a hull has a better home.

- `build_reference.py` (or `build_<hull>.py`) is the driver. It takes
  `--out --views --samples --no-save --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE`, and
  `--state` once condition states exist.
- `<prefix>_kit.py` holds materials, `box`/`cylinder`/`mesh`/`finish`, and
  `surface(x,y)` (a ray-cast onto the hull).
- One module per area (`<prefix>_hull.py`, `_engines.py`, `_turrets.py`,
  `_bridge.py`, and so on), so parallel agents never edit the same file.
- A committed `.blend`, `geometry.json` (the manifest), review PNGs and
  `README.txt`.
- Copy kit helpers into the new package. Never import another hull's modules:
  other sessions are editing them.

Run Blender headless:
`"/c/Program Files/Blender Foundation/Blender 5.2/blender.exe" --background --factory-startup --python <driver> -- <args>`.

## Pass 1: blockout

Purpose: the correct silhouette, sections, mass and slots. No fine detail.

1. **Identify the hull in the read-only install** (`C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core`; never edit it):
   - the row in `data/hulls/ship_data.csv` (crew, cargo, fuel, mass);
   - the `.ship` file: weapon slots (location, angle, arc, size, type),
     engine slots (location, width) and built-ins;
   - the sprite under `graphics/ships`.

   A `.ship` filename can differ from its `hullId` (the Cerberus is
   `warhound.ship`). Variant hulls such as (A), (D) or (P) can have their own
   files. Say which file you used.
2. **Axes**: +X bow, +Y port, +Z dorsal. 1 sprite px = 1 unit, about the
   sprite centre.
3. **Never invent mounts.** Turrets sit at the game's slots and rest at their
   slot angles. Train only the parts above the traverse gap:
   `T(x,y) @ Rz(angle) @ T(-x,-y)`. After rotating, check the barrels against
   neighbouring parts with a BVH overlap test. Fix a clash by lifting the
   barbette, not by moving the slot.
4. **Engines are whole drive units** (pods, cans, shrouds) sized from the slot
   width ratio. They are not nozzles poking out of the hull. Where the sprite
   shows whole engine mounds, the pod is the whole mound.
5. **Sections**: flat-shaded prisms. Each section has one keel chamfer, one
   vertical wall and one shoulder chamfer. Stacked chamfers read as a blob,
   most visibly in side view. Use `prism()` with an (inset, z) profile and
   `union()` with the MANIFOLD solver.
6. **Facets**: bounded plane cuts (`facet`/`plane_cut` with a `span` along X)
   wherever the sprite shows angled surfaces, especially the bow. An unbounded
   cut clips distant parts.
7. **Seating**: added parts seat themselves with `surface(x,y)` ray-casts.
   Never hard-code their Z.
8. **Names are identifiers**: `Engine {i} ...`, `Turret {i} ...`, and named
   hull and armour parts. Damage, surfacing and VFX all key off object names,
   so a name must never change once a later pass depends on it.
9. **Manifest** (`geometry.json`): axes, units, slots, engines (with the
   exhaust direction), dimensions, cameras and the source files.

**Exit check:**
- an orthographic top view overlaid on the sprite, with silhouette IoU
  reported (aim for at least 0.93);
- side, front, stern and three-quarter renders at 1920x1080;
- slots match the `.ship` file exactly.

## Pass 2: plausible detailing

Purpose: infer the detail the sprite implies but doesn't spell out, from the
sprite's shading and from lore (the wiki page, the game description, the
hull's role and tech level), and from what the campaign needs the ship to be.

- Read the sprite at 4-8x: raised blocks, recesses, stripes, seams, hatches
  and greebles. Each raised or recessed blob is a protrusion or a well to
  account for (command deck, access trunks, hatches). Don't move protrusions
  to where a "ship usually has them"; the sprite decides.
- Low tech or industrial reads as segmented armour modules with dark seam
  gaps, strata, weld lines, bolted service covers and exposed structure, not
  smooth skins.
- Function must read: a discernible bridge or command deck, real
  naval-looking turrets (barbette, shield, trunnion, barrels), bays with
  jambs and coamings, lights, mast and sensors, hard points and grapples.
- Keep the pass-1 silhouette: re-run the overlay after every change.
- Fan out with subagents (Agent tool), one module per area (hull and facets,
  engines, deck and bridge, turrets, special fittings). Each agent works in its
  own scratch copy and writes only its own module. The integrating session
  reviews and merges. CLAUDE.md asks for Sol 6.1 subagents; if the harness
  doesn't offer it, use Opus and say so.

**Exit check:**
- close-up cameras (bow, bridge, turret, stern) and the four standard views at
  1920x1080, compared before and after;
- the IoU did not regress.

## Pass 3: the review loop

The owner reviews pass 2's renders. Each piece of feedback becomes one focused
change, re-rendered from the same cameras so before and after can be compared.
Repeat until the owner accepts the hull.

Faults that kept recurring on the Second Wind, worth checking for before the
owner has to point them out:
- lost angled bow surfaces;
- a blobby side profile;
- engines reading as poking out of the hull;
- pods too blocky (use faceted lofts);
- an invented or mismatched protrusion;
- a bridge in the wrong place.

Keep a `reference-history/` snapshot of the accepted blockout, so a later pass
can be compared against it.

## Pass 4: real-world scale

Each hull gets exactly one `metres_per_unit` value, published in
`geometry.json` and the driver (`METRES_PER_UNIT`). Every metric value reads
it: VFX sizes and speeds, lamp falloff, cameras, depth of field, flight
speeds, plate framing and texel density. Nothing hard-codes metres.

Derive it from what the ship has to carry, not from the sprite (the Second
Wind method in `second-wind-pipeline.md`, Scale):
1. Take the deck area the mod's own deck program
   (`DeckSizing.programFor` with the `RoomRecipe` shapes; one cell is a
   metre) requires for the hull's class, minimum and maximum crew, and
   cargo. Add about 25% for circulation, and multiply by about 3.2 m of
   deck height.
2. Measure the model's main pressure-hull volume in Blender
   (`bmesh.calc_volume` on the main hull object, in cubic units). Take about
   55% of it as usable.
3. Solve `usable_units³ × s³ = required m³` for `s`, the scale in metres per
   unit. Cross-check it against a fitting with a known real size (the boat
   bay against `RoomRecipe.BOAT_BAY`/`SHUTTLE_BAY`, hatch heights, a 1.8 m
   person).

Report the plausible range. Note that the battle-map landed-ship convention
(`landed-ships-true-scale.md`) is about readability on the tactical map and
is a different number.

## Pass 5: condition states

When the campaign needs the hull in more than one condition, follow
`second-wind-pipeline.md` (Condition states, Layers):
- the clean base geometry;
- a scar table with stable ids and a per-state status
  (`open → patched → replaced → faded/gone`);
- wear as masks we control.

Damage never renames, replaces or deletes base objects. It adds
`Scar <id> ...` objects and gives the new faces damage materials. Each state
is built with one command (`--state`). Fan out per hull area. Check that
every scar id sits in the same place in every state.

## Pass 6: texturing

**Provisional:** the Second Wind's surfacing pilot (`second-wind-surface/`) is
the first real run; update this section from its README when it lands.
Current method, from `second-wind-projection-spike/README.md`:
- **Part-by-part Codex reskins** (the `codex-imagegen` skill) of major parts,
  each rendered alone and square-on. Attach one fixed finish reference to
  every request, register the result by silhouette fit, grade it to a shared
  palette per material, and store it on the part keyed by object name.
- Small parts and all damage/repair surfacing use graded swatches, so a scar
  can change status without regenerating any base skin.
- Do not build a whole-ship six-view atlas. Codex wear is uniform, so wear
  placement stays in our masks.
- De-light the generated images: they carry 25-30/255 of baked light.

## Pass 7: VFX, lighting and cinematic export

Reusable, state-aware effects key off anchor objects: nozzle mouths and
muzzles found by name and slot table. The Second Wind's kit is
`second-wind-vfx/`; reuse its modules and parameterise them rather than
forking them. Each state exports one appendable Blender collection
(`second-wind-pipeline.md`, Cinematic output). Cinematics follow the
`cinematic-production` skill.

## Rules for every pass

- Every preview shown to the owner is 1920x1080. Keep raw frames in an
  ignored `build/` directory.
- Pixel-diff renders across an integration to prove that nothing outside the
  change moved.
- Stage explicit paths. Commit, merge the latest `main`, `--ff-only` into
  local `main`, then clean up. Never push.
- A sibling session's report is a claim: look at its renders and diff before
  telling the owner it is done.
