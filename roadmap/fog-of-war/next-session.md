# Next session — fog of war

Read [`overview.md`](overview.md) first for status and scope.
Shipped work is in [`complete/`](complete/).

## State of play

- **V1 fog-of-war: shipped & live.** `VisionService` owns reveal state; the
  render gate and ephemeral vision sources are in `BattleScreen`. See
  [`complete/fog-of-war-v1.md`](complete/fog-of-war-v1.md) for the architecture
  and tuning knobs (commits `cce5e26`, `a883bd3`).
- **Time of day: designed, not started.** Day/night as a gameplay system —
  night shrinks vision, dawn drives reinforcements. Three slices laid out in
  [`stories/time-of-day.md`](stories/time-of-day.md); Slice 1 (the `TimeOfDay`
  value + ambient multiply pass) is the entry point.

## Next up

1. **Time of day Slice 1** — `TimeOfDay` value (ambient as a function of
   battle-elapsed-time, constant in v1) + lightmap-multiply pass in the
   [`battle-render`](../battle-render/overview.md) pipeline. Day/Dusk/Night
   presets at battle start.
2. **Time of day Slice 2** — night `visionRange` multiplier through
   `VisionService`, reading the same `TimeOfDay` value.
3. **Backlog cleanup** — the three follow-ups in [`overview.md`](overview.md)
   (building-visibility merge, last-known-position ghosts, shot gating) when a
   forcing function pulls them.

## Watch out for

- `unitVisibility` / `fadeAlpha` are keyed by **dense index**, not unit id —
  they ride the [`ecs-migration`](../ecs-migration/overview.md) SoA seam and
  must survive tail-swap on release like every other dense-keyed array.
- The fog overlay renders **after doodads, before units** as a `SolidQuadBatch`
  pass; the time-of-day lightmap-multiply pass must order correctly against it.
