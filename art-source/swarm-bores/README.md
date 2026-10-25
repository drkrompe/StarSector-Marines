# Swarm bores, spore colonies, the ground colony, the brood spire and nest clutter

Geometry-first ImageGen (`art-source/geometry-first-imagegen.md`) for the
Colony Breach bore bodies (`structure.swarm-bore`, `structure.swarm-mother-bore`)
and the three spore-colony grades (`structure.spore-colony-fissure`, `-bore`,
`-maw`), the ground colony (`structure.ground-colony`), the brood spire (`structure.brood-spire`) and a swarm nest's
ground clutter, in the Runner's chitin (`art-source/alien-modular-topdown/`).

| Step | Command | Writes |
| --- | --- | --- |
| Guides | `python art-source/swarm-bores/make_guide.py [part or state ...]` | `art-source/templates/swarm-<part>-<n>[-<state>]-guide.png` |
| Reskin one variant (one paid call) | `bash art-source/swarm-bores/generate.sh <part> <n> [wounded\|remains]` | `raw/<part>-<n>[-<state>].raw.png` + provenance |
| Export | `python art-source/swarm-bores/export.py [--part P] [--state S]` | `mod/graphics/structures/<name>-<n>[-wounded].png` and its `-glow.png`; `<name>-<n>-remains.png` |
| Bleed sheets (code-drawn) | `python art-source/swarm-bores/ooze.py` | `mod/graphics/structures/swarm-ooze.png` (punctures), `swarm-spray.png` (fissures) |
| Wound anchors (after any guide change) | `python art-source/swarm-bores/wounds.py` | each grown structure's `"bleed"` block in `turret-emplacements.turret.json` |
| Spore shots (code-drawn) | `python art-source/swarm-bores/spore_shots.py` | `mod/graphics/fx/spore-clump.png`, `spore-cluster.png`, `spore-lance.png` |
| Other-palette preview | `python art-source/swarm-bores/export.py --palette raw` (or `purple`, `chitin`) | `build/swarm-bores/<palette>/` |

Parts and variant counts are `make_guide.VARIANTS`: `bore` 3, `mother-bore` 2,
`spore-colony-fissure`, `spore-colony-bore`, `spore-colony-maw` 3 each, `ground-colony` 3, `brood-spire` 3,
and the nest clutter `nest-clutch`, `nest-pods`, `nest-husk` 3 each. The
structure's art block declares `"variants": N` and names its layers with
`{variant}`; `StructureArt.variantFor` picks one per structure from its entity
id, the same for the body and its glow.

**Damage states** (`make_guide.STATES`). `wounded` is drawn below half the
structure's durability (`"woundedBelow": 0.5`, layer paths carry `{state}`):
plates torn out of their sockets over raw sinew, gashes, a sac burst, a spine
snapped, the amber dimmed. `remains` is left on the ground when it dies
(`"remains"`): a bore caved in and choked with rubble, a colony's sacs burst
and deflated, no glow. Both are built from the variant's own seeded geometry,
with a separate draw stream deciding the damage, so the intact guides do not
change and each damaged painting keeps its structure's shape. A state's brief
is the part's prompt plus `prompts/state-<state>.prompt.txt`, and the
variant's intact raw rides along as its identity reference. The export drains
the remains towards grey (`husk`) and gives their berm the creep's withered
colour.

**Bleeding** (`StructureArt.Bleed`). A wounded body bleeds from its painted
wounds. `ooze.py` draws the loop procedurally, because its frames have to agree
pixel for pixel: a punctured membrane pulsing to a double heartbeat, its slit
dilating and its flaps lifting as each beat pushes a film of ichor out to run
downhill and drain. A heightfield lit by the game's sun in the bloom sinew
ramp; three kinds of puncture in eight run directions, 24 frames at 12 fps.
A long wound is a fissure instead, on its own sheet with a larger frame: on the
same beat its lips part and spray a fan of droplets downhill, each with the
sun's shadow under it at its height, landing as splats that soak away (the direction
is baked rather than turned, since turning would turn the light). `wounds.py`
reads each painting's wounds out of its guides, as the raw flesh the wounded
guide shows and the intact one does not, largest first, with the way downhill
from each on the body's heightfield. A patch twice as long as it is wide is a
fissure, its slit laid along the painted gash (every painting gets at least
one); the rest are punctures. It writes the structure's whole `"bleed"` block
(both families and every wound), so re-run it whenever `make_guide.py` changes.

**Geometry and light come from code.** Each guide is a seeded heightfield of
lopsided primitives (plates of differing size and lean, tusks, tilted paving
slabs, earth, sinew roots; sacs, buds, spines) lit by the battle's default sun
(`SunLight`: azimuth 135 degrees, from the upper left of the screen; elevation
38 degrees) with ray-marched shadows, so the shaft's far wall is lit and its
near wall dark, plates shadow one another, and the body shadows the ground.
The lighting is baked into the albedo, which is why the runtime never rotates
or mirrors these sprites. The reskin is asked to keep that light and shadow.

**The ground layer is not painted.** `make_guide.ground_layer` draws the cast
shadow, a contact shade and the cracks the body heaved into the ground from
the same heightfield, translucent and dark so it reads on paving, grass or
creep; `export.py` lays it under the fitted reskin. The canvas is larger than
the body (`CANVAS_CELLS`) so the shadow fits, and each part is drawn larger
than it is modelled (`DISPLAY_SCALE`: bores 1.3, mother bore 1.25, colonies
1.4) so it reads as a structure on a mass of creep; the layer's `cells` in
`turret-emplacements.turret.json` is `make_guide.display_cells(part)`. The
structure's hit radius and footprint are not scaled.

**Anti-air look.** The colonies' weapons (`weapon.spore-burst-*`) carry
`spore_shots.py`'s sprites and an `fx` block per grade (muzzle spit, a trail
of glow and loose spores, an airburst, drifting spore haze); `flightSec` and
`arcHeight` lob the slower grades. The glow layers set `fireFlash`, so a sac
flares and swells as it fires. Review: `createSnapshots -Psnapshot=spore-colony-fire`.

**Palette.** The shipped sprites are in the settled Rav Bloom palette,
`bloom` (owner, 2026-10-21). The guide's material map records which pixels are
creature (chitin, sinew, bone, flesh, sac) and which are earth or paving, so
the export recolours only the creature by luminance gradient maps sampled from
the approved Runner and Brute plates (rav-redesign `motion-runner` and
`motion-brute` heroes): carapace to lilac and dusky violet, roots and a shaft's
lining to crimson-magenta sinew, bone to ivory; amber stays amber. The raws
keep the generations' own colours (`--palette raw` previews them), so a
palette change is a ramp edit, not a new generation. The thrown earth of a
berm is creep heaved up out of the shaft: the export maps it to the creep's
default palette, read from `CreepPalette.java`, so **re-export after the creep
palette changes**. `purple` (round 3) and
`chitin` (the brown proposal) remain as previews.

The `-glow` layer is the reskin's own amber pixels lifted out by colour and
drawn over the body with a pulse; the structures set `groundPad: false`, so
they stand on the ground rather than on a hardstanding square.

Art direction: the swarm is one engineered organism (`colony-breach-nouns.md`,
Origin), so a bore and a colony are grown out of the same chitin, sinew and amber
nodes as the strains, never plants or machines. They erupted, so nothing is
symmetric: a bore has an upheaved side and a collapsed side, and a colony
grows round one dominant leaning sac. The ground colony is told from them at a glance: low on the creep,
no sac and no pore aimed at the sky, a plated core whose crimson sinew arms sprawl flat over the ground, studded
with raked spines (the owner's pick of three body plans, `make_guide.GROUND_COLONY_STYLE`; the other two,
`ridge` and `urchin`, stay selectable for comparison).

Review: `gradlew.bat createSnapshots -Psnapshot=swarm-spore-colonies,swarm-bore-collapse`,
and `python art-source/swarm-bores/review_sheet.py` for the side-by-side sheets.

**Brood spire** (`make_guide.spire_body`). The one tall growth, so it is modelled as round, tapering, leaning
columns (`stalk`: the highest point of a chain of spheres over each pixel), and its height is told by its lean and
the long shadow the ray-march throws down-sun; the canvas is large (`CANVAS_CELLS` 7) so the shadow fits. Three
body plans were drawn for the owner's pick (`SPIRE_STYLES`: `roost`, `chimney`, `clutch`); `SPIRE_STYLE` is the
pick, `roost`, whose option painting is variant 1. Each option's guide is `templates/swarm-brood-spire-<style>-1-guide.png`, its brief
`prompts/brood-spire-<style>.prompt.txt` and its painting `raw/options/brood-spire-<style>-1.raw.png`;
`export.py --spire-option <style>` exports one as `brood-spire-1` for the look review
(`createSnapshots -Psnapshot=brood-spire-look`). The remains snap the column and lay its top on the ground
(`snapped`).

**Mother bore** (`make_guide.mother_body`). The heart's root, so it must read as far more than a bore: a wider rim
and a throat more than twice as deep, ribbed all the way down with sinew cords down its walls and a faint amber glow
far down (the export keeps that dim amber through the recolour rather than mapping it to sinew), the colony's paving
laid on a grid and heaved up the berm in levered, broken pavers, and long branching roots out to the canvas edge;
the canvas is 6 cells so they fit. Three body plans were drawn for the owner's pick (`MOTHER_STYLES`: `jaw`, `vault`,
`collapse`; `MOTHER_STYLE` is built on): `python make_guide.py options` writes
`templates/swarm-mother-bore-<style>-1-guide.png`, the briefs are `prompts/mother-bore-<style>.prompt.txt` and the
paintings `raw/options/mother-bore-<style>-1.raw.png`; `export.py --mother-option <style>` exports one as
`mother-bore-1` for the look review (`createSnapshots -Psnapshot=mother-bore-look`). `prompts/mother-bore.prompt.txt`
is the built-on plan's brief, which `generate.sh` uses for the shipped variants and their damage states.

**Registration.** `export.py` nudges the bounding-box fit by the small scale and shift that best overlays the
painting's silhouette on the guide's, and grows the guide's material map over whatever the painting covers past the
guide's edge, so a root painted a few pixels off its guide is still recoloured as root.

**Nest clutter** (`make_guide.clutch_body`, `pods_body`, `husk_body`). Small, non-blocking ground decorations drawn
on the creep along the sinew roots that join a bore to its spore colonies and round the bore's mouth: an egg clutch
(glossy eggs lit amber from inside in a shallow cup of sinew and membrane), brood pods (ribbed carapace sacs
half-buried at different sizes and leans, sinew in their grooves, small amber nodes) and a shed husk (a Runner's
moulted shell split down its back, its torn edges curled over the hollow). They are drawn only, never hurt or
destroyed, so they have the intact state alone (`make_guide.INTACT_ONLY`, `part_states`) and `wounds.py` skips
them. All three stay low (at most about a fifth of a cell high, under half a spore sac) so they never read as a
colony. The clutch and the pods get a `-glow` layer; the husk has no amber and none, and the export dries it towards
grey like a dead body's remains (`export.DRIED`). Exported as `mod/graphics/structures/nest-<part>-<n>.png`; the
renderer's drawn size is `make_guide.display_cells(part)`.
