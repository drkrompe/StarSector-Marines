# Seeded battle determinism — one stream, owned by the sim

> The map was reproducible. The fight was not. You could regenerate the exact
> same city and watch a completely different battle happen in it.

**Status:** shipped 2026-08-23.

## What was wrong

`BattleSimulation` held `private final Random rng = new Random()` — self-seeded
from the system clock — and seven files under `battle/` did not even use that,
reaching for `ThreadLocalRandom.current()` directly:

| File | What it rolled |
| --- | --- |
| `InfantryWeapons` | the hit roll, primary and secondary |
| `HeavyWeapons` | heavy-weapon fire and spread |
| `HitResponseSystem` | fallback-on-hit |
| `FleeBehavior` | wander direction, dwell timers |
| `GuardPostPatrol`, `PatrolRoute` | patrol waypoint selection |
| `DroneSwarmAction` | swarm slotting |

Four more self-seeded their own `new Random()`: `BattleRadioChatter`,
`ImpactFx`, `FlybyOverlay`, and `MarineInsertion`.

Meanwhile `BspCityGenerator` / `UrbanMapGenerator` take an explicit seed and
`BattleSetup` already had one in scope at every call site. The generation half
of the battle was reproducible and the simulation half was not.

### What that cost

- **A bug report could not be reproduced.** "My squad broke and fled at the
  wrong moment" was only ever debuggable by reading code.
- **Combat could not be tested without saturating it.**
  `CombatTelemetryServiceTest` had to set accuracy to `10f` to overshoot the
  roll, because at a nominal `1.0` the multiplier chain still missed about one
  round in twenty and a five-round assertion failed a quarter of the time
  (`418b3584`). That fix was correct for that test and is still in place — but
  needing it at all was the symptom.
- **`ThreadLocalRandom` is per-thread by definition**, so any future move of sim
  work off the tick thread would have silently changed outcomes.

## What shipped

**One stream, owned by the sim, seeded at construction.**

- `BattleSimulation(grid, topology, long seed)` is the new primary constructor;
  `random()` exposes the stream and is on `BattleView`, so the AI tier — which
  holds nothing else — can reach it without minting its own.
- The two-argument constructor still exists and delegates with a fixed
  `DEFAULT_SEED`. That matters more than it looks: roughly forty test fixtures
  build a sim without a seed, and they are now reproducible **by construction**
  rather than by luck, with no call-site churn.
- `BattleSetup.buildMap` takes the seed and every one of its seven callers passes
  the seed its **own map** was generated from. Two attempt-loop call sites needed
  care: `createSilentColony` derives a per-attempt `scenarioSeed` and the civilian
  path a per-attempt `battleSeed`, and passing the outer seed there would have
  replayed a different battle on the same ground.
- The three combat classes take the stream by constructor injection; the four
  behaviors draw from `sim.random()`, which they already had in scope.

**Two guards, because one is not enough.**

- `BattleDeterminismTest` — same seed replays an identical HP trace through 40
  rounds and 120 ticks; a different seed produces a different one (so a harness
  that returned a constant could not pass); the no-seed constructor matches
  `DEFAULT_SEED`; and `sim.random()` and `view.random()` are the same object.
- `NoUnseededRandomTest` — scans every file under `battle/` for
  `ThreadLocalRandom`, `Math.random(`, and `new Random()`. This is the one that
  actually holds the line: a single unseeded draw added months from now breaks
  replay everywhere while every other test keeps passing, because nothing else
  observes the property globally.

## Decisions worth keeping

**Presentation is exempt, explicitly and by name.** `BattleRadioChatter`,
`ImpactFx`, and `FlybyOverlay` stay self-seeded. None can change the outcome of a
battle — particle jitter, overlay sprites, and which bark plays are read by the
renderer and the audio layer, never by the sim — and they are built by hosts that
hold no seed. Replaying a battle reproduces the fight exactly; the smoke curls
differently. The exemption list is in the test with that reasoning attached, and
an entry on it is a claim that the file cannot affect sim state.

**The counting test keeps its overshoot.** With a seeded stream, accuracy `1.0`
would be *reproducible* — but that assertion needs all five rounds to land, and
reproducible-but-arbitrary is not the same as certain. It would pass on this seed
and flip the first time anything upstream changed the draw order. Overshooting
makes the assertion independent of the roll instead of lucky with it, and
determinism is now covered on its own.

**The stream is deliberately not thread-safe.** The sim ticks serially. A stream
that could be drawn from concurrently would not be reproducible even with a seed,
so making it safe would hide the bug rather than prevent it.

## Follow-up

**`MarineInsertion`'s loadout roll is seeded but not battle-seeded.**
`PowerCatalog` builds powers before any battle exists, so the instance has no seed
to inherit; it uses a fixed `LOADOUT_SEED`. Deterministic, but uniform — the first
drop of every battle rolls the same loadout. Tying it to the battle stream needs
the activation path to hand the `BattleControl` down through
`AirDeliveryPower.configureMission`, which is a small extension-seam change and
would also want `MarineInsertionTest`'s injected `PrimaryCyclingRandom` reworked.

## Verification

`gradlew.bat build` green with no exclusions, and the suite run twice to confirm
stability. `CombatTelemetryServiceTest`, the test that started this, passes on its
own terms.
