# Mech lines: tech family by weight class

Status: DRAFT — the direction for growing the mech roster beyond the three first chassis; planned for v0.2 (`v0.2.md`).

Written: 2026-10-23

Updated: 2026-11-10 — the low-tech row's flamer rigs are built, the pirate Torch and the Path's Censer, each a fuel
load over its frame with a machine flamer as its own weapon layer; the owner keeps the Hauler a durable heavy with no
loot (Order of work, step 2). Also 2026-11-10 — the low-tech row's first faction variants are built: the pirate Hauler,
and the Luddic Path's Foundry Breaker and Pilgrim, each a body of its own over its frame. Also 2026-11-10 —
the midline row is built as three articulated carriers, wheeled hulls (`convoy-nouns.md`, the
articulated carriers). Earlier 2026-11-09 — Tri-Tachyon's light crawler is built, the high-tech row's first line of its own, and is the
only mech Tri-Tachyon fields: its heavy-shell stand-ins are off its roster (owner: "they're not good").

## Where the roster stands

The Hound, Warden and Bulwark are the first three chassis ever built, and they are
stand-ins. Each was made to prove a weight class (light, medium, heavy) and the systems
behind it: mounts, flux, internals, footsteps, doctrine. None was designed as one entry
in a roster. All three are now Ordnance Board patterns, the Hegemony's low-tech walkers
(owner), drawn as such for the Hegemony, Independents and pirates. Every other faction
wears a body over the same frame that stands in for its own line; they wear sleek shells.
A stand-in body is a look, not a line: it carries the frame's mounts and numbers.
Tri-Tachyon no longer fields stand-ins (owner, 2026-11-09): its heavy-shell bodies over the
three frames are off its roster, and it fields only its own crawler. The heavy-shell
livery stays drawn for a Tri-Tachyon mech a mission still spawns on a low-tech frame.

## The target: a grid of lines

A **mech line** is one tech family (`tech-families.md`) at one weight class. It has a
base chassis and grows by **variants** (`mechs-nouns.md`, variant), the same way a
vehicle line climbs: each variant is its own pattern card and bill, and earns its place
through a trade-off.

|  | Light | Medium | Heavy |
|---|---|---|---|
| **Low-tech** | Hound | Warden | Bulwark |
| **Midline** | light carrier (`LIGHT_CARRIER`, a hull) | medium carrier (`MEDIUM_CARRIER`) | heavy carrier (`HEAVY_CARRIER`) |
| **High-tech** | light crawler (`MechVariant.CRAWLER`, its own chassis) | none (skimmers carry the weight) | none (no heavy walker, owner) |

Nine lines in all. The low-tech row is the three first chassis. The high-tech light is
Tri-Tachyon's crawler, a carapace on four short legs with one front gun and a built-in
shield emitter (`mechs-nouns.md`, Family and doctrine). Tri-Tachyon puts its medium and
heavy weight into skimmers rather than walkers. The midline row is three wheeled hulls, the
articulated carriers; the midline factions' walkers remain shells over the low-tech frames
(a livery, not a line).
Each stand-in stays where it is until its line is designed. At that point the stand-in
either becomes the line's base chassis or gets reworked into it.

## What sets a line apart

Weight class decides the lift a mech needs, its size and its weapon budget. That is
shared across a whole column. Tech family decides how the mech goes about its job, and
that is shared across a whole row. A low-tech heavy and a high-tech heavy should play
noticeably differently, not just look different:

- **Low-tech**: heavy plate and a forgiving structure, ballistic and missile mounts, slow
  and hard to cripple. It runs hot and vents loud and long. Exposed hydraulics, slab
  armour, clanking footfalls.
- **Midline**: balanced and modular, with mixed mounts, shared parts and easy repair. It
  is the standard the others are measured against. Panelled casing and servo whir.
- **High-tech**: fast and precise, energy mounts, a large flux reserve and quick venting,
  thin plate. Damage shows as failure, not as wear. Composite shell, smooth gait, electric
  actuators. This row gives Tri-Tachyon mechs of its own.

These leanings shape each line's authored numbers the way vanilla's hull families shape
its ships. No rule checks a mech's family (`tech-families.md`, law 4). The bands those
numbers should land in, mount sizes, power per row, flux, plate and shield, are
`machine-budgets.md`'s.

## Factions

A faction mainly fields the lines in its family's row, plus whatever it buys. Its roster
weights its own row up, much as vehicle rosters already favour the variants each faction
is known for. Until a faction's row exists, its roster uses the stand-ins. Tri-Tachyon
fields only its crawler.

## Order of work

The campaign is set in Hegemony, Independent and pirate space for now (owner,
2026-10-23). Those factions are low-tech, apart from the Independents' midline
purchases, so the low-tech row comes first:

1. **Low-tech medium and heavy.** Done as the Warden and Bulwark: the Hegemony,
   Independents and pirates walk the low-tech row (pirates in repaired-scrap bodies).
2. **Low-tech variants** across the row, including the field-rig and salvage
   variants that pirates and Independents would build. **Five built** (owner picks from the concept sheets in
   art-source/mech-modular-topdown/sources/lowtech/variants/sheets; the Runner, Boarder and Cutter were rejected, and
   the second pass's two flamer rigs were both picked):
   - the pirate **Hauler** (`MechVariant.HAULER`) on the Bulwark frame: a cargo cage on its back, a folding salvage
     crane along its left shoulder, scrap slabs hung on chains, its left rack place shipping empty beside the crane;
     the most durable and slowest heavy a pirate fields. It carries no loot (owner, 2026-11-10: "not now");
   - the Path's **Foundry Breaker** (`MechVariant.FOUNDRY_BREAKER`) on the Hound frame: a foundry-cast breaching
     collar round the nose and five shell canisters across its back, stock the Path's Hound issue (DC-5, SRM-5);
   - the Path's **Pilgrim** (`MechVariant.PILGRIM`) on the Bulwark frame: a mining hauler with a dozer blade across
     its front (the concept drew it at the back by mistake), ore-hopper ribs and prayer cloths, the slowest walker in
     the row and the only one that crushes an ordinary wall by its weight, as the Onager does (`mechs-nouns.md`,
     Family and doctrine);
   - the pirate **Torch** (`MechVariant.TORCH`) on the Hound frame: mismatched fuel drums and a pressure bottle in a
     welded cradle across its back, its dorsal rack place shipping empty under the cradle, the FL-1 burner on its nose;
   - the Path's **Censer** (`MechVariant.CENSER`) on the Warden frame: a promethium tank across its rear, an armoured
     hose up its right flank and prayer cloths, the FL-2 lance on its right shoulder, a chaingun on its nose and an
     empty plate on its left rack place.
   A flamer rig's fuel load is its body; its burner is its own swappable weapon card and layer (law 4), one machine
   flamer family in two sizes (`mechs-nouns.md`, Machine flamers).
   A variant keeps its frame's legs, mounts, body plan, footprint and weight class (`MechVariant.frame`) and draws
   its own body in their place: one ImageGen master per variant over a guide that is its frame's own master,
   registered as the runtime fits it and shrunk inside a larger canvas so parts can hang past the frame's outline
   (variants/bodies.py). Its weapons stay layers on its frame's mounts (law 4). Pirates field the Hauler and the
   Torch; the Path fields its three conversions in place of the Hound. All numbers UNTUNED; the Kit Ladder holds the
   five patterns and the two flamers at "none".
3. **Midline.** It completes the row that Independents buy into. It takes the Eagle's material and colour (owner):
   weathered tan and khaki plate with engraved panel lines over grey machinery, round sockets, yellow-green lights,
   but never a ship's outline: the first concepts, built round the Eagle's forked prow and on bird legs, read as the
   Eagle or as chickens (owner). Its machines are wheeled (owner: "a wheeled concept might not be bad"). The second
   pass came from material swatches with no shape in them (art-source/mech-modular-topdown/sources/midline,
   briefs_v2.py), and the owner picked E (2026-11-10): **articulated carriers**, a drive cab and mission modules on
   a hinge that bends to steer, light two modules on four wheels, medium two on six, heavy three on eight. They are a
   wheeled hull line (`convoy-nouns.md`, the articulated carriers): a main gun card and secondary mounts on the
   mission module, the modules drawn trailing the cab through the hinge. Its numbers sit between
   the other two rows (`machine-budgets.md`): about 2x flux draw, a shield at 1.0 where it carries one. **Built**
   (2026-11-10): `LIGHT_CARRIER`, `MEDIUM_CARRIER` and `HEAVY_CARRIER`, each cut from one ImageGen master over a guide
   whose sockets run along the module's spine (the concept's pair over a hatch read as a face), no shield, first-pass
   and UNTUNED. The League and the Diktat field them; the Kit Ladder holds them at "none" for the owner to place.
4. **High-tech.** The look is chosen (owner, the heavy shell): steel-blue armour lobes
   over exposed machinery, sockets sunk into the shells, after vanilla's high-tech
   hulls, and Tri-Tachyon already wears it on the three frames
   (art-source/mech-modular-topdown/sources/hightech). That body still reads as a reskin
   of the low-tech frames (owner), against law 1, so the row needs frames of its own.
   Direction (owner, from the frame concepts in `sources/hightech/frames/`): Tri-Tachyon
   puts its weight into skimmers, a light, medium and heavy hover vehicle line built out
   from the hover scout and drone skimmer, now built (`convoy-nouns.md`, the hover line:
   the heavy skimmer carries the shield rule on its hull's pool), and into light crawlers,
   small four-legged machines read as a drone concept. It fields no heavy walker of its
   own. The concept sheets paint their guns in to show the silhouettes; every production
   hull is drawn bare, with empty mount sockets, and every weapon stays a separate,
   swappable layer (law 4). The shield rule is built: a
   compartment's shield emitter (`MachineSystems.shieldEmitter`, `MechShield`) raises a
   frontal flux shield run on the mech's one flux pool, so blocking and firing spend the
   same headroom; the shield folds while the guns have the pool venting, and filling the
   pool overloads the mech, shield and guns together. The light crawler is built
   (`MechVariant.CRAWLER`): its own parts cut from one ImageGen master over a guide
   (art-source/mech-modular-topdown/sources/hightech/crawler), drawn by the derelict
   walker's leg rig at its own size, one PL-1 on a front socket as its own layer, and a
   shield emitter its compartment carries as built, so every crawler fields the shield.
   It is the only mech on Tri-Tachyon's roster, the heavy-shell stand-ins taken off
   (owner). It is crewed and
   a company can buy it as a Tri-Tachyon pattern (owner, 2026-11-09), though the owner
   saw it as a drone concept. Still to come: the skimmer line (a separate
   session); a combat drone wing on the medium, relaunched for flux; the lance and shield
   sharing one pool on the heavy.

## Laws

1. Every line has a base chassis, art, sound and doctrine of its own. A line is never a
   recolour or livery of another (`mechs-nouns.md`, livery).
2. A variant stays in its line's family and weight class. A heavier or higher-tech
   machine is a different line.
3. A line's weight class is the lift it needs, so a line never changes lift through
   variants.
4. Machines are modular (owner). A mech's or a vehicle's body never has a gun or turret
   built into it: the hull is drawn with empty mounts, and every weapon is its own
   swappable layer on a mount.

## Outside the grid

An **automated line** stands outside the grid: an unpiloted machine no company
fields, builds, buys or refits, because the Sector's law keeps armed machines
under crewed control. The first is derelict security's walker
(`MechVariant.SECURITY_WALKER`, `MechVariant.automated()`), one heavy chassis
whose line, siege and vault frames are loadouts on it
(`derelict-security-network.md`). The laws hold for it as for any line: it
shares nothing with the Bulwark, and is a torso on reaching legs drawn by its
own rig, with its own body plan.

## Open

- **Which low-tech cell first, medium or heavy**, and whether the Hound stays the light.
- **Whether a stand-in is kept or replaced** when its line is designed.
- **Art pipeline.** The modular top-down mech art (`art-source/`) was built for three
  chassis. A line needs its own parts kit in its family's visual language.
- **Sound by family.** Footsteps and servos currently vary by chassis weight only.
