# 28 — Assault mech cohesion

**Shipped 2026-08-22 in `eaef38b2`.**

## Player-facing result

The Hound remains the short-range point mech, but no longer interprets that
role as permission to charge alone. It advances aggressively only while it can
lead nearby combat infantry or operate beside another live mech. Otherwise it
holds its ground and continues using any weapon that can already reach the
enemy.

## Shipped contract

- `AssaultAssignedObjectiveGoal` remains at MISSION priority. This is a
  tactical movement correction inside `BreachAndAssault`, not a new competing
  goal.
- A same-faction human combat-infantry unit or another live mech can anchor the
  advance when it is within twelve cells.
- The authored objective/contact destination is clamped to at most six cells
  ahead of the nearest anchor. This lets the Hound walk point without leaving
  the formation behind.
- If no valid anchor exists, the Hound clears its current path immediately.
  Its existing fire pass still runs, so unsupported means hold-and-fight
  rather than become inert.
- Enemy units, distant friendlies, civilians, drones, static emplacements, and
  rescue-pickup mechs do not release the assault advance.
- LR Support and Armored Support delegation inside a mixed mech squad remains
  unchanged.

The twelve-cell acquisition radius matches the assigned infantry-screen search
in `MechScreenAdvance`; the six-cell lead is one cell looser than that screen's
five-cell formation pocket. The asymmetry allows the Hound to visibly lead
while still giving its followers room to catch up.

## Verification

- `AssaultAssignedObjectiveTest` covers faction-neutral supported movement,
  unsupported path cancellation, infantry-anchored lead clamping, mech-pair
  release, and rejection of enemy or distant apparent support.
- The focused mech and infantry-screen suites passed.
- Full `gradlew.bat build` passed with 2,036 tests before integration. One
  probabilistic telemetry assertion missed on the first run, passed in
  isolation, and remained green in the successful full rerun.

## Still out of scope

- New player order UI or explicit mech-to-infantry pairing.
- Commander-level lance composition, target assignment, reserve behavior, or
  dynamic role reassignment.
- Recon doctrine or Sirocco range tuning.
- Retreat/regroup pathfinding for a Hound that is already overextended when its
  support is destroyed. This slice stops further extension immediately; a
  deliberate regroup action can build on the same support query later.
