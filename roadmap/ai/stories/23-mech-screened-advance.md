# 23 — Mech-screened advance

## Player-facing contract

An infantry squad advancing toward an objective can adopt a nearby friendly
assault mech as its moving point unit. While the mech is pressing forward,
the infantry occupies a compact pocket behind its chassis relative to the
threat. When the mech makes contact, the squad fans only far enough to shoot
around it and continually rebuilds that pocket as the mech moves.

This is shared doctrine, not marine-only scripting. Marine and defender
infantry use the same selector and formation geometry around a same-faction
`ASSAULT` mech.

## Slice boundary

- Layer screening onto the existing `EnterZone` objective advance. It does
  not add a new GOAP goal and therefore cannot outrank planting, rescue,
  garrison, last-stand, or morale-survival work.
- Prefer an assault mech whose squad carries the same target-zone assignment.
  A nearby unassigned assault mech is a fallback only when it is already on
  the infantry squad's objective axis. Never select an enemy mech, a
  non-assault role, or the rescue pickup perimeter mech.
- In follow mode, assign distinct reachable cells behind the mech relative to
  its known contact, falling back to the objective direction before contact.
- In fan mode, assign distinct reachable cells to both flanks of the mech when
  it has a live visible target. Fan cells remain inside a short screen leash.
- Recompute the formation against the live mech position; no fixed terrain
  anchor may survive after the mech moves.
- If the mech dies, becomes incompatible, or cannot supply a usable formation,
  immediately fall through to ordinary `EnterZone` movement and bounding
  overwatch.
- Surface the selected mech, FOLLOW/FAN mode, and member target cells in the
  existing unversioned squad dump contract.

## Mechanical cover contract

The chassis is real protection, not an AI-only score. `BallisticResolver`
already walks every unit body crossed by a direct-fire ray in time order and
transfers a round into the first crossed hostile body at the full incidental
catch chance. From an enemy shooter's perspective, a same-faction mech between
the shooter and the intended infantry target is such a hostile body. This
slice pins that behavior explicitly with a mech-screen regression test rather
than introducing a second cover or LOS system.

The mech does not become an opaque navigation-grid LOS wall. Infantry can
still acquire the contact and step laterally into firing positions; actual
rounds remain responsible for physical interception.

## Acceptance

- Both MARINE and DEFENDER infantry select an eligible same-faction assault
  mech and reject enemy/non-assault/rescue candidates.
- Follow cells are geometrically behind the mech relative to threat/objective
  and update when the mech moves.
- A live visible mech contact flips the pocket to a leashed two-sided fan.
- Members move toward their own stable cell rather than dogpiling one anchor.
- Broken squads and non-`EnterZone` mission plans retain their existing
  behavior because this doctrine is action-scoped.
- A direct-fire round aimed at screened infantry strikes the intervening mech;
  moving the mech off the ray lets the intended infantry target be hit.
- Focused tests and the full build pass.

## Out of scope

- Player-issued "follow this mech" orders or UI selection.
- Mech-to-infantry ownership/pairing persisted beyond the current advance.
- Suppression, armor-facing bonuses, or a general dynamic-cover score used by
  arbitrary tactical-position searches.
- Recon mech doctrine and specialist-mech balance tuning.
