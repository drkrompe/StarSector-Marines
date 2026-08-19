# Rescue unit dispersion and pickup-mech leash — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `bdfd5bf8`

## Outcome

- Escort actions expose one role slot per walkable local formation cell. Role
  scoring binds the nearest squadmate to each distinct cell, eliminating the
  same-tick race in which several members could select the same still-unclaimed
  destination before deferred occupancy updates were applied.
- Four- and eight-person squads fill the one-cell ring around their squad rally
  first. The center and two-cell cardinal positions provide extra or obstructed-
  terrain capacity. Overflow members retain the previous occupancy-aware safe
  fallback.
- Formation slots encode relative offsets, so every member's destination moves
  immediately with a new escort-screen target while preserving the squad shape.
- Rescue pickup mechs retain their mission-priority center/five-point LZ patrol
  during contact. Generic LR-support overwatch and armored-support backstop goals
  explicitly exclude them, preventing contact from pulling the mech away from
  the perimeter.
- The patrol action acquires targets and fires all installed mech weapon tracks
  without replacing its bounded LZ movement route. Morale-broken pickup mechs
  still yield to the existing survival behavior.

## Dump diagnosis

The reported SQ-5 dump showed an engaged Sirocco at `(188,127)`, an LZ escort
objective at `(223,144)`, `OverwatchKillZone` as its current mission goal, and a
91-cell path. Contact made the old pickup-patrol goal irrelevant; the generic
LR-support goal then selected a firing position 32–38 cells from the last-seen
alien with no LZ leash.

## Verification

- Escort coverage proves four same-cell squadmates receive four distinct local
  path destinations and keep their relative offsets when the squad rally moves.
- Mech coverage reproduces an engaged LR-support pickup mech with an inherited
  long overwatch path, proves the patrol goal wins, proves the old path is
  cancelled, and proves the mech routes back to the authored LZ center.
- `gradlew.bat test` passes 1,818 root tests plus the asset-pipeline test
  (1,819 total).

## Manual follow-up

Replay the rescue mission and watch both a Bulwark and Sirocco outcome. Confirm
escort fireteams visibly occupy their local rings, and confirm the pickup mech
cycles only through the center and five squad posts while continuing to engage
aliens from those positions.
