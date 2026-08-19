# 23 — Mech-screened advance

**Shipped 2026-08-19 in `0daf058e`.**

## Player-facing result

An infantry squad advancing toward an objective can adopt a nearby friendly
assault mech as its moving point unit. While the mech presses forward, the
infantry occupies a compact pocket behind its chassis relative to the threat.
When the mech makes contact, the squad fans only far enough to shoot around it
and continually rebuilds that pocket as the mech moves.

This is shared doctrine, not marine-only scripting. Marine and defender
infantry use the same selector and formation geometry around a same-faction
`ASSAULT` mech.

## Shipped doctrine

- Screening is layered onto the existing `EnterZone` objective action rather
  than introduced as a new GOAP goal. It cannot steal squads from planting,
  rescue, garrison, last-stand, or morale-survival work.
- Infantry prefers an assault mech carrying the same target-zone assignment.
  A nearby unassigned assault mech is eligible only when already ahead on the
  objective axis. Enemy, non-assault, behind-the-line, and rescue-perimeter
  mechs are rejected.
- FOLLOW assigns distinct reachable cells behind the live mech relative to a
  known contact, with the objective direction as the pre-contact fallback.
- A visible target inside the mech's engagement range switches the pocket to
  a short, two-sided FAN. Members move without firing, then add stanced fire
  after reaching their assigned flank cell.
- Formation cells are rebuilt from the mech's current position every tick.
  Mech death, role change, unusable geometry, or a plan change clears the
  transient state and returns the squad to ordinary `EnterZone` movement and
  bounding overwatch in the same action tick.
- The unversioned squad dump exposes `screeningMechId`, `mechScreenMode`,
  `mechScreenThreatId`, and the member-to-cell `mechScreenTargets` array.

## Mechanical moving cover

No second cover or LOS system was added. `BallisticResolver` already walks
every physical unit body crossed by a direct-fire ray in time order and
transfers a round into the first crossed hostile at the full incidental catch
chance. From an enemy shooter's perspective, the friendly assault mech between
it and the intended infantry target is that first hostile body. A Story 23
regression now pins both the screened hit and the exposed result after the
chassis moves off-axis.

The mech remains transparent to navigation-grid LOS. Infantry can acquire the
contact and step laterally to fire; the physical round, not an AI visibility
special case, determines whether the chassis catches a shot.

## Verification

- `MechScreenAdvanceTest` covers both factions, assignment preference,
  invalid-candidate rejection, the nearby on-axis fallback, behind-the-mech
  geometry, live-anchor movement, distinct cells, two-sided fanning, leashing,
  firing, and same-tick loss-of-screen fallback.
- `BallisticResolverTest` pins mech-body interception for infantry-bound fire.
- Existing bounding-overwatch and assault-mech focused tests passed.
- Full `gradlew.bat build` passed before integration.

## Still out of scope

- Player-issued "follow this mech" orders or UI selection.
- Persisted mech-to-infantry ownership beyond the current advance.
- Suppression, armor-facing bonuses, or a general dynamic-cover score used by
  arbitrary tactical-position searches.
- Recon mech doctrine and specialist-mech balance tuning.
