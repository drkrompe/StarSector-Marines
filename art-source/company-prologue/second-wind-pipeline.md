# Second Wind: geometry, condition and surfacing pipeline

Status: ACTIVE — the contract the geometry, damage and surfacing work share.
Written: 2026-10-16

The Second Wind appears in cinematics across its whole campaign life, so one
model has to produce every condition with the same parts, the same scars in
the same places and the same paint. This doc fixes how the pieces fit; each
package's own README says how it is built.

## Condition states

The hull's life is one sequence. Each state is a build of the same model.

| State | When | Reads as |
|---|---|---|
| `derelict` | ~8 years mothballed in an asteroid belt as a parts bin | Unpowered hulk: impact basins with crushed lips, torn and missing outer plates over dark backing, bent fins, sheared mast, smashed engine can, point defence stripped. |
| `patched` | Escape and arrival at Jangala | Emergency work only: mismatched salvage plates welded over craters, splints, the bent rib shored with cargo rail, pressure patches, external cable runs. Starboard pod's two engines run on a bolted-on salvaged drive coil; the port pod is dead and capped. Turrets reinstalled. |
| `refit` | After the limited berthing/command conversion | Local recent work: a few patches replaced by proper plates with fresh seams, the forward collar sorted out. Most old scars and the dead port pod remain (see `second-wind-skins/README.txt`). |
| `restored` | Engineering has worked through the hull | Structurally whole: replacement plates in place, both pods running, repainted but not new — mismatched panels and old scars remain visible under the paint. |
| `clean` | Reference only | The undamaged design baseline the others are built from. Not a campaign state. |

The campaign owns which state a scene shows. `refit` is limited: it must not
imply the heavy craters were repaired.

## Layers

A state is the clean model plus three layers, applied in this order:

1. **Base geometry** (`second-wind-geometry/sw_*.py`): the clean ship. Object
   names are stable identifiers; surfacing is keyed to them.
2. **Scars** (`sw_damage.py`): every damage site is one row in a table with a
   stable id, a location placed by hull ray-cast, and a status per state:
   `open` (crater, torn plate, bent part) → `patched` (salvage plate and weld
   beads over it) → `replaced` (proper plate, fresh seam) → `faded` (repainted,
   seam still visible), or `gone`. The same id is the same scar in every state
   and every shot. Engine condition is a scar row too (port pod: `dead` →
   `capped` → `running`).
3. **Wear** (surfacing parameters, not geometry): grime, soot at the drives,
   scorch, paint fade and chip density per state, as masks we control. Codex
   output never decides where wear goes.

Rules that keep the layers composable:

- Damage never renames, replaces or deletes a base object. It boolean-cuts a
  base object (its surface coordinates and materials survive on the remaining
  faces) or adds new objects with its own names (`Scar <id> ...`).
- New faces a cut creates, and every added repair object, take damage/repair
  materials, never the base skin.
- Every state builds from one command and the clean build stays reproducible.

## Surfacing

From the projection spike (`second-wind-projection-spike/README.md`):

- **Base skin, part by part.** Major armour parts are rendered alone and
  square-on, reskinned by Codex (`tools/codex_imagegen.py`, the
  `codex-imagegen` skill) with one fixed finish reference attached to every
  request, registered by silhouette fit, graded to a shared palette per
  material, and stored on the part as its own surface coordinates and image,
  keyed by object name. Parts are whole objects or face groups split at
  creases, never region boxes. Identical parts (turret pairs, mirrored pods)
  are generated once.
- **Small and unseen parts** use graded Codex material swatches on tiled
  coordinates, the full-surface material approach.
- **Damage and repair surfacing** comes from swatches too (salvage plate
  variants, weld, scorch, exposed backing), so a scar can change status
  without regenerating any base skin.
- **Hero shots** may add a whole-ship projection from the shot's own camera,
  held within about ±30° of it, on top of the base skin.
- Generated RGB carries 25–30/255 of baked light: grade or de-light before
  use, and measure every generation's registration.

Because the base skin lives on stable parts and damage surfaces itself, the
base skin is generated once and serves every state.

## Cinematic output

Each state exports one appendable Blender collection with packed images and
a turn review, the way `second-wind-cinematic/second-wind-full-surface/`
does today. That older package is built from the superseded landing-wreck
geometry; the per-state exports replace it once they exist.

## Ownership

| Area | Package |
|---|---|
| Base geometry | `second-wind-geometry/` (`sw_*.py`, `build_reference.py`) |
| Scars and states | `second-wind-geometry/sw_damage.py` |
| Surfacing and wear | a surfacing package beside the geometry (not inside it) |
| Cinematic exports | `second-wind-cinematic/` |
