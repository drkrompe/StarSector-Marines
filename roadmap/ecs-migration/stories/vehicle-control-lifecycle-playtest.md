# Vehicle-control lifecycle playtest

Status: READY FOR ACCEPTANCE

Written: 2026-08-23

Read `ecs-nouns.md` before running this acceptance story.

## Goal

The id-keyed `VEHICLE_CONTROL` extraction is code-complete. Play a convoy vehicle
through incoming travel, docking, reverse recovery when applicable, and departure.
Confirm that the vehicle remains controllable and that no transition stalls or
skips its intended route.

## Acceptance

- One live run observes the complete incoming → dock → reverse/depart lifecycle.
- Vehicle position, heading, docking, recovery, and mission state remain coherent.
- Any observed defect becomes its own reproducible implementation story.

## Out of scope

The optional `VehicleController` rename and unrelated route tuning.
