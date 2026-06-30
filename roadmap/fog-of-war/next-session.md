# Next session — fog of war

Read [`overview.md`](overview.md) first for status and scope.
Shipped work is in [`complete/`](complete/).

## State of play

- **V1 fog-of-war: shipped & live.** `FogOfWarService` owns reveal state; the
  render gate and ephemeral vision sources are in `BattleScreen`. See
  [`complete/fog-of-war-v1.md`](complete/fog-of-war-v1.md) for the architecture
  and tuning knobs (commits `cce5e26`, `a883bd3`).
- **Time of day: designed, V1 impl removed.** Day/night as a gameplay system —
  night shrinks vision, dawn drives reinforcements. Three slices laid out in
  [`stories/time-of-day.md`](stories/time-of-day.md); the V1 lightmap-multiply
  implementation was removed 2026-06-29 (see that doc for status). Feature is
  currently UNIMPLEMENTED; not a near-term priority.

## Next up

1. ~~**Time of day Slice 1** — `TimeOfDay` value + lightmap-multiply pass.~~ ✅ removed 2026-06-29;
   see [`stories/time-of-day.md`](stories/time-of-day.md) for revised status.
2. ~~**Time of day Slice 2** — night `visionRange` multiplier through `FogOfWarService`.~~ Blocked
   on Slice 1 (feature unimplemented).
3. **Backlog cleanup** — the three follow-ups in [`overview.md`](overview.md)
   (building-visibility merge, last-known-position ghosts, shot gating) when a
   forcing function pulls them.

## Watch out for

- `unitVisibility` / `fadeAlpha` are keyed by **dense index**, not unit id —
  they ride the [`ecs-migration`](../ecs-migration/overview.md) SoA seam and
  must survive tail-swap on release like every other dense-keyed array.
- The fog overlay renders **after doodads, before units** as a `SolidQuadBatch`
  pass. (The time-of-day lightmap-multiply pass that was planned to slot after it
  was removed 2026-06-29.)
