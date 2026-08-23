# S11 — Combat reflexes

> **Shipped 2026-08-22** on `session/reflexes`, implementation commit
> `cb22c3d3`. Experience now controls how quickly a soldier can fire on a newly
> registered threat without changing sustained weapon cadence.
>
> Verification: all 2,117 repository tests green (2,116 root + 1
> asset-pipeline).

## Why

The lethality pass made first action matter, but experience only improved the
quality and cadence of shots after combat had already started. Veteran troops
needed a behavioral advantage that rewards surviving missions without becoming
another permanent DPS multiplier.

The motivating case was an experienced fireteam clearing into superior
numbers, but the mechanic is deliberately **not doorway-specific**. Reflexes
are a universal passive: how quickly this soldier registers a new threat.

## Decision

Selecting a new pursuit target starts a registration timer. Fire intents that
do not replace the pursuit target, such as opportunity shots, register their
own threat through the same path. Infantry primary fire is held until the
intent's target matches the registered threat and the timer has expired.

| Experience | Registration delay | 30 Hz ticks |
| --- | ---: | ---: |
| Green | 0.50 s | 15 |
| Regular | 0.35 s | 11 |
| Veteran | 0.20 s | 6 |
| Elite | 0.05 s | 2 |

The clock advances in the global firing phase even without a fire intent. A
soldier can therefore finish registering a distant threat while moving toward
it. Reasserting the same threat does not restart the clock; switching threats
does. Once registered, accuracy, cooldown, burst behavior, and trigger
discipline proceed normally, so reflexes change initiative rather than
sustained DPS.

## Scope

The passive applies to the humanoid soldier archetypes that already consume an
individual `SoldierProfile`: marines, aligned/opposed marines, and militia.
`UnitType.usesInfantryTraining()` is now the shared classification used by both
reflexes and trigger discipline.

Deliberately excluded:

- doorway, portal, room, and range-specific bonuses;
- movement or global GOAP/replan delays;
- burst continuation after the registered first shot;
- secondary-weapon aim windows;
- fauna, swarm runners, drones, turrets, and mechs, which keep their own
  behavior models.

## Verification

- Exact tier delays and resulting first-shot tick counts are pinned.
- Registration advances without a fire intent while a soldier closes range.
- Reasserting a threat is free and switching threats restarts registration.
- Follow-up fire at the same threat receives no second reflex delay.
- Non-soldier combatants do not inherit the fallback Regular profile.
- Choke and garrison action tests still verify their own intent/concentrated-
  fire contracts after explicitly completing the independent reflex gate.
