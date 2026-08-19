# Rescue escort dispersion and local pressure — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `f2159d82`

## Outcome

- Mobile rescue squads receive stable, ID-ordered line-and-depth rally slots
  instead of sharing one shelter or escort-screen destination. The lead owns
  the center lane; support squads fill four-wide echelons behind it.
- Rally-slot terrain repair searches nearby walkable, reachable cells while
  preserving at least four cells of anchor separation. This keeps squads from
  converging into a single ball before reaching the civilians and while the
  escort screen moves toward extraction.
- Each mobile squad advances to within two cells of its own rally slot. A squad
  five cells away therefore continues moving rather than entering the former
  broad pause radius.
- The two-cell, five-second engaged advance bound now requires local pressure:
  at least one live defender must be within twelve cells of a member of an
  engaged mobile squad. A stale alert or distant attacker no longer stutter
  steps the entire escort column.
- Pickup militia retain their separately authored perimeter posts and are not
  included in the mobile escort formation.

## Verification

- Commander coverage proves five squads receive separated anchors both before
  shelter relief and around the moving escort screen.
- Goal coverage proves every mobile squad closes on its own slot and does not
  pause while still five cells away.
- Pressure coverage proves a nearby attacker enables timed engaged bounds and
  a distant attacker leaves the normal five-cell screen advance intact.
- `gradlew.bat test` passes 1,815 root tests plus the asset-pipeline test
  (1,816 total).

## Manual follow-up

Play the rescue scenario with the full player force and watch the transition
from approach to escort. Confirm squads preserve useful lanes around terrain,
continue making progress under intermittent contact, and only slow while aliens
are visibly close enough to threaten the formation.
