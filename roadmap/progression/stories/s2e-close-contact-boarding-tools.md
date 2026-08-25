# S2E — Close-contact boarding tools

Status: PLANNED — depends on the shared special-equipment catalog and requires
a typed close-contact weapon activation.

Written: 2026-08-24

Read `progression-nouns.md`, `moddable-weapons-nouns.md`,
`combat-durability-nouns.md`, and `ai-nouns.md` before implementing this story.

## Problem

The current infantry catalog stops at projectile fire and the Breachhand
satchel. That leaves two recognizable Sector boarding tools without a ground
combat home: industrial thermal/arc cutters adapted to defeat hard material,
and vibro or monofilament blades used in desperate compartment fighting.

Treating them as short-range rifles would erase their identity. Treating them
as universal melee attacks would quietly give every marine another weapon and
force a new close-combat doctrine into unrelated loadouts.

## Goal

Add one typed **close-contact weapon** activation used by two distinct
special-equipment families. Both occupy the existing single special slot,
reference authoritative weapon definitions for damage, penetration, audio, and
effects, and may act only from honest physical contact. They do not grant a new
marine class or a universal secondary attack.

## Family catalog

| Family | Tactical identity | Initial provenance direction |
| --- | --- | --- |
| Thermal breacher / arc cutter | Sustained, low-collateral contact work against armor, emplacements, and authored breach points | Industrial and Independent arc tools; maintained Church cutters; refined corporate thermal units; pirate conversions |
| Vibro-blade / monofilament edge | Fast reaction strike against adjacent soft or exposed infantry when a firefight collapses into contact | Rugged Hegemony/League/Diktat vibro patterns; rare Tri-Tachyon monofilament tools; salvaged outlaw copies |

Thermal and arc names are mechanism/provenance variants of one breach-tool role
unless their live channel or payload differs. Vibro and monofilament names are
likewise not four automatic catalog entries. A distinct definition must earn
its place through behavior, payload, or readable presentation.

## Battle execution

- A close-contact item references a marine-secondary `WeaponDef`, but a typed
  executor replaces the traveling shot with an adjacent contact test. Damage
  and penetration still resolve through shared durability; presentation cannot
  create a hit.
- A breacher commits a visible, interruptible channel. Completion applies
  bounded contact work with no area blast. An authored wall/door breach point
  may receive weapon-owned wall damage; arbitrary obstacle-seeking and map-wide
  wall chewing are not authorized.
- A blade is a short reaction action against an adjacent living infantry
  contact. It cannot intentionally select a turret, mech, wall, or remembered
  but unseen target, and it has no reach through a blocked edge.
- Both actions reserve their target while committed so several carriers do not
  waste the same contact payload. Death, separation, lost line of contact, or
  a higher-priority survival action cancels before the payload applies.
- The Breachhand remains distinct: it plants a delayed friendly-fire explosive
  on a hardened actor. A cutter is sustained and local with no blast; a blade
  is anti-personnel reaction equipment.

## AI and maneuver

Equipment influences per-action suitability but does not create a permanent
breacher or swordsman role. A carrier may use a tool when normal squad movement
has already brought it into contact, or when an explicit authored breach
assignment names the adjacent breach point. Ordinary engagement AI must not
abandon a firing line, violate its maneuver leash, or path across the map just
to manufacture a melee opportunity.

The same legality, interruption, reservation, and target policy applies to
player and defender carriers. Faction profiles control availability and
provenance only; monofilament does not grant Tri-Tachyon hidden reach, and
religious or outlaw carriers receive no contact immunity.

## Campaign and presentation

- Each family is a stable special-equipment item with a collectible Armory template,
  template provenance, a contact use pose, and readable carried art.
- The Fleet Armory explains “anti-hard contact channel” versus “anti-personnel
  contact reaction” and compares each against the player's existing special
  slot; it does not present either as a primary weapon attachment.
- Recovery and unlocks route through `s6-unlock-ladder-expansion.md` and the
  shared loot manifest. Industrial facilities and intact armories
  are plausible cutter sources; faction provenance alone is not a guaranteed
  drop.

## Acceptance

- Contact validation rejects range, blocked-edge, dead, hidden, and illegal
  target cases before commitment and again before payload application.
- A breacher damages an eligible armored actor or authored breach point through
  shared durability/wall authority without splash or a phantom projectile.
- A blade can strike only adjacent valid infantry and never pulls its carrier
  out of squad cohesion to seek contact.
- Player and defender carriers pass the same execution and AI scenarios, with
  deterministic target reservation and interruption.
- Template ownership, cargo-backed issue, deployment freeze, HUD cooldown/ammunition,
  telemetry, save repair, effects, and unlock reachability recognize both
  items.
- Thermal/arc and vibro/monofilament variants are observably distinct where
  separate definitions exist; otherwise provenance reuses one role rather than
  padding the catalog.

## Out of scope

- A universal melee fallback for every marine.
- Grapples, executions, knockback, dismemberment, or a new melee formation.
- Freeform demolition of every wall cell or navigation obstacle.
- Boarding-map hazards, vacuum exposure, or ship capture rules.
- Pather martyr rigs and carried IEDs —
  `s2g-martyr-rigs-and-carried-ieds.md`.
