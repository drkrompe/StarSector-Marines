# Bloom installation flavour

Status: PLANNED

Written: 2026-10-22

Model: `colony-breach-nouns.md` (Campaign: blooms, Colony).

## Scope

The colony a bloom erupts in is laid out the same whatever market raised the
call: a town, a dormitory suburb and a working installation whose character is
always a depot (`BattleSetup.colonyPlanFor`). Let the bloomed market's industry
pick the outlying installation instead:

- mining or refining: a mine head (the lore's "lost miners in the lower
  workings");
- aquaculture: a pumping yard;
- farming: a farm;
- none of those: the depot, as today.

The rescue mission carries no market facts into the battle yet, so the slice
is: freeze the installation kind on the rescue's mission boundary from the
bloomed market when the call is raised, carry it through the mission factory
to `colonyPlanFor`, and choose the precinct character from it. A rescue fixture
with no market keeps the depot.

## Constraints

- Deterministic: the kind is frozen at the call, never read live at launch.
- Map requirements (`MissionMapRequirements.missingFromColony`) still hold for
  every kind; a kind whose precinct character cannot meet them falls back to
  the depot.
- `BattleSetup` is edited by other sessions; keep the change to the plan
  choice.

## Acceptance

- Each kind generates a colony that passes the map requirements at a handful of
  seeds (generated-space evidence, not `test`).
- A unit test of the industry-to-kind choice.
