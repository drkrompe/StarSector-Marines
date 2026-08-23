# Air Hull and Effects Acceptance

Status: READY — shipped behavior awaits one focused live verification pass.

Written: 2026-08-23

Read `air-nouns.md` before running this story.

## Goal

Verify that the shipped hull frame, real mount placement, per-mount sight, and
flight-derived engine effects agree in a live battle rather than only in parser
and geometry checks.

## Contract under test

- An air body's position is the hull's authored centre of gravity. The sprite
  rotates around that point while engine and weapon attachments remain fixed to
  their painted locations.
- A turret fires and tests line of sight from its own mount. Front and rear
  mounts may therefore see different targets near intervening cover.
- Engine plumes respond to realized translation and turning, then rise and
  decay smoothly instead of snapping or glowing uniformly.
- Hull size, attachment placement, and movement remain coherent at the current
  shared calibration.

## Manual acceptance

1. Spawn an armed shuttle on a route that exposes both translation and a clear
   turn, preferably beside cover that can separate front- and rear-mount sight.
2. Watch the hull enter, rotate, loiter, and depart. Confirm that its painted
   body pivots around the authored centre and every turret and plume stays on
   its hardpoint throughout the turn.
3. Exercise a target near cover. Confirm each mount's fire follows its own
   world position and line of sight rather than the craft centre.
4. Confirm forward, lateral, and rotational demand produce plausible plume
   emphasis with a smooth attack and decay envelope.

## Acceptance

The hull, mounts, sight origins, and engine effects read as one actor throughout
the sortie. Record any observed correctness failure as a focused story; feed a
pure feel adjustment into the owning air tuning surface. Once the run passes,
fold and delete this story.

## Out of scope

- Fighter entity ownership, fighter survivability, or modeled fighter fire.
- New hull scale, steering, mount, or plume tuning without an observed problem.
- Automated tests for this documentation-only acceptance record.
