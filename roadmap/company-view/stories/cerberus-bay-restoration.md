# Cerberus bay restoration

Status: IMPLEMENTED — live campaign acceptance open
Written: 2026-10-12
Updated: 2026-10-12 — transaction checks, paired layouts and occupied second-berth evidence pass.

## Outcome

Second Wind begins with one working shuttle bay and a second damaged bay already
reserved in her deck. Both bays, the machine shop and the Mech Lab keep their
positions when the second bay is repaired. This is an optional early campaign
money sink, separate from required founding preparation and boat commissioning.

## Acceptance

- The custom start hull reserves two physical bays before company conversion;
  the second is sealed, visibly damaged and supplies no fabrication or lift.
- Completing Hold Until Relieved unlocks an 8,000-credit repair at an accessible
  planetary spaceport. The price includes labor and structural materials.
- Boat Deck shows Castle's portrait, explanation, Cost / Available / Short by,
  prerequisites and an explicit reviewed repair action. No tutorial lockout.
- Repair changes only the damaged facility. Dimensions, compartment positions,
  workshop, Mech Lab and first boat identity/fittings remain unchanged.
- The new berth starts empty. The ordinary Hermes fabrication bill is separate.
- Failed, repeated and stale orders spend nothing; repair persists by hull
  identity across save/load, UI reopening and company transfer. Vanilla hull
  repair does not repair this interior facility. Existing custom-start saves
  acquire the damaged bay without losing their owned first boat.
- Focused transaction/state tests and opt-in before/after deck and UI evidence;
  package boundary verification. Live campaign acceptance is reported separately.

## Remaining acceptance

In Starsector, load both a fresh founding start and an older custom-start save.
Confirm the sealed bay before the first mission, complete Hold Until Relieved,
then inspect and repair it at a planetary spaceport. Confirm immediate empty
berth presentation, commission the second Hermes, save/reload, and move the
company away and back without losing the paid restoration or boat fittings.
No live game was launched or modified for automated verification.

## Verification

Focused yard/model/deck sizing/resolver tests pass. The opt-in `cerberus-start`
snapshot suite checks five converted layout seeds, unchanged compartments and
workshop positions, sealed/open apertures, cached deck refresh, and the paid
empty-to-occupied second berth. Boat Deck frames cover 100% and 150% scale;
package boundary verification passes. These are construction fixtures, not a
live campaign save or a completed battle replay.

Canonical model: `company-view-nouns.md` and `ship-interiors-nouns.md`.
