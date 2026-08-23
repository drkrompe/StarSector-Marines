# S2D — Fragmentation grenades

> Punish a soft cluster behind cover without spending anti-armor ordnance.

Status: PLANNED — preserves the frag grenade from the original combined S2
catalog story and depends on the shared special-equipment identity established
by `s2a-anti-materiel-rifle.md`.

Written: 2026-08-23

Read `progression-nouns.md`, `company-view-nouns.md`, and
`moddable-weapons-nouns.md` before implementing this story.

## Tactical identity

A **fragmentation grenade** is a short arcing anti-personnel throw with a small
lethal radius and friendly fire enabled. It answers clustered infantry and
soft targets behind intervening low cover; it is not an anti-emplacement tool,
does not open walls, and cannot replace the rocket's hardened-target role.

Initial balance should start at two or three grenades per carrier. Range,
flight time, scatter, and blast radius are playtest values. The special item
owns finite carried count and activation; its conventional arcing projectile
and detonation reference the weapon catalog rather than duplicating damage or
FX values.

## Battle execution

- The carrier pauses for a readable throw and the grenade follows the shared
  arcing projectile pipeline to a committed ground endpoint.
- Arrival resolves through the ordinary detonation, cover, friendly-fire,
  telemetry, and audio-belief seams. The blast has negligible hardened-target
  and wall value.
- Ammunition is consumed on release. A dead carrier cannot finish a queued
  throw, while a grenade already in flight remains authoritative and detonates.

## Gameplay-AI integration

### Use policy

- A carrier considers an identified soft hostile cluster inside throw range.
  Expected useful damage must beat a configurable threshold; one nearly-dead
  isolated infantry target does not earn a grenade.
- Landing-point scoring rewards multiple hostiles and useful cover bypass, then
  rejects any footprint containing a friendly or intersecting a friendly's
  committed movement. The safety margin accounts for blast radius and the
  grenade's flight time.
- The throw may begin from normal engagement but cannot interrupt a
  mission-priority channel, active retreat, or the moving half of a bound.
- A squad-level area reservation counts grenades already aimed or in flight.
  Sibling carriers do not saturate the same cluster after projected damage is
  already sufficient, but may engage genuinely separate clusters.
- The policy is faction-neutral. Defender availability is an explicit loadout
  decision, not a different targeting implementation.

### Counterplay

- The projectile, throw report, and landing cue are readable. A hostile squad
  that honestly observes the incoming grenade may use its existing movement or
  survival policy to clear the footprint when flight time permits; an unseen
  squad receives no supernatural warning.
- Detonation creates the ordinary anonymous hostile-noise belief event. It
  does not reveal the thrower's exact identity through walls.

## Campaign and presentation integration

- Fragmentation grenades occupy one billet's single special slot and use finite
  armory stock. Add an Assault template with one carrier and an explicit
  acquisition path in `s6-unlock-ladder-expansion.md`.
- The Fleet Armory and battle HUD show the grenade as anti-personnel area kit,
  including remaining throws and friendly-fire warning; they do not reuse the
  rocket's anti-armor language.

## Boundaries

- No cooking, manual player targeting, alternate fuse modes, persistent mines,
  or inventory pickup in the first slice.
- No generic “grenadier” role. Equipment may influence per-action suitability
  without changing the marine's enduring role or fire-team membership.

## Acceptance

- A grenade arcs to a ground endpoint, damages soft units through the shared
  AoE/cover resolver, permits friendly fire, and has negligible wall and
  hardened-target value.
- AI spends it on a useful hostile cluster, never on one weak isolated target,
  and rejects predicted friendly blast or committed movement through the area.
- Multiple carriers reserve landing areas and do not waste lethal projected
  damage on one cluster.
- Observed opponents may react during flight; unobserving opponents receive no
  exact hazard knowledge.
- Player and defender carriers pass the same focused use and response
  scenarios.
- Armory stock, templates, deployment freeze, HUD ammunition, telemetry, save
  compatibility, and unlock reachability recognize the frag special.
