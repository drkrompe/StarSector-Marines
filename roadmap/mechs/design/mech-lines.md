# Mech lines: tech family by weight class

Status: DRAFT — the direction for growing the mech roster beyond the three first chassis; planned for v0.2 (`v0.2.md`).

Written: 2026-10-23

Updated: 2026-10-23 — order of work set by the campaign's Hegemony, Independent and pirate focus.

## Where the roster stands

The Hound, Warden and Bulwark are the first three chassis ever built, and they are
stand-ins. Each was made to prove a weight class (light, medium, heavy) and the systems
behind it: mounts, flux, internals, footsteps, doctrine. None was designed as one entry
in a roster. All three are now Ordnance Board patterns, the Hegemony's low-tech walkers
(owner), drawn as such for the Hegemony, Independents and pirates. Every other faction
wears a body over the same frame that stands in for its own line. Tri-Tachyon's is the
high-tech row's concept body (owner pick, the heavy shell); the rest wear sleek shells.
A stand-in body is a look, not a line: it carries the frame's mounts and numbers.

## The target: a grid of lines

A **mech line** is one tech family (`tech-families.md`) at one weight class. It has a
base chassis and grows by **variants** (`mechs-nouns.md`, variant), the same way a
vehicle line climbs: each variant is its own pattern card and bill, and earns its place
through a trade-off.

|  | Light | Medium | Heavy |
|---|---|---|---|
| **Low-tech** | Hound | Warden | Bulwark |
| **Midline** | open (shells stand in) | open (shells stand in) | open (shells stand in) |
| **High-tech** | shield skirmisher (concept body on the Hound's frame) | drone controller (concept body on the Warden's frame) | lance platform (concept body on the Bulwark's frame) |

Nine lines in all. The low-tech row is the three first chassis; the other six cells are
empty, filled meanwhile by other factions' shells over those frames (a livery, not a line).
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
its ships. No rule checks a mech's family (`tech-families.md`, law 4).

## Factions

A faction mainly fields the lines in its family's row, plus whatever it buys. Its roster
weights its own row up, much as vehicle rosters already favour the variants each faction
is known for. Until a faction's row exists, its roster uses the stand-ins. Tri-Tachyon
would field nothing but stand-ins until the high-tech row exists.

## Order of work

The campaign is set in Hegemony, Independent and pirate space for now (owner,
2026-10-23). Those factions are low-tech, apart from the Independents' midline
purchases, so the low-tech row comes first:

1. **Low-tech medium and heavy.** Done as the Warden and Bulwark: the Hegemony,
   Independents and pirates walk the low-tech row (pirates in repaired-scrap bodies).
2. **Low-tech variants** across the row, including the field-rig and salvage
   variants that pirates and Independents would build.
3. **Midline light.** It completes the row that Independents buy into.
4. **High-tech.** The look is chosen (owner, the heavy shell): steel-blue armour lobes
   over exposed machinery, sockets sunk into the shells, after vanilla's high-tech
   hulls, and Tri-Tachyon already wears it on the three frames
   (art-source/mech-modular-topdown/sources/hightech). The row's own rules wait until
   the campaign reaches Tri-Tachyon space: a frontal shield fed from the mech's one flux
   pool, so blocking and firing compete; a combat drone wing on the medium, relaunched
   for flux; the lance and shield sharing one pool on the heavy.

## Laws

1. Every line has a base chassis, art, sound and doctrine of its own. A line is never a
   recolour or livery of another (`mechs-nouns.md`, livery).
2. A variant stays in its line's family and weight class. A heavier or higher-tech
   machine is a different line.
3. A line's weight class is the lift it needs, so a line never changes lift through
   variants.

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
