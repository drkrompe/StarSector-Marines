# Time of day

> A *night raid* mission where the player wants to be done before dawn.
> If the battle drags, the sun comes up and enemy reinforcements arrive —
> the dawn transition is a soft deadline that turns a stealth/ambush
> mission into a meatgrinder if mishandled. The visual cycle is the
> diegetic clock telling the player how long they've been fighting.

Time of day is a **gameplay system, not just a render effect** — it lives in
this feature dir because night shrinks vision (the fog/vision tie-in). It is
[world-reactive, not expressive][world-reactive]: the clock is a mechanical
deadline the mission reads, not a cosmetic skybox.

## Why

The vision above is the payoff. A diegetic day/night clock gives the player a
felt sense of mission pacing and ties a hard mechanical consequence (dawn
reinforcements) to a value they can read off the screen without a HUD timer.
Night also multiplies `visionRange` down, so the same clock that pressures the
player on the deadline also tightens their fog-of-war — one knob, two effects.

## Design

- **V1** ships as a single ambient knob set at battle start (Day / Dusk /
  Night presets), implemented as a lightmap-multiply pass in the
  [`render2d`][render2d-batching] pipeline. Night also multiplies
  `visionRange` down — the fog/vision tie-in.
- **Design the `TimeOfDay` type so an animated cycle slots in without
  rework** — ambient color should be a function of battle-elapsed-time even
  when v1 returns a constant. Don't bury TOD inside the renderer; it's a
  gameplay-visible value the mission script reads.
- **When the animated cycle lands**, the same clock drives reinforcement
  triggers in Conquest/Assault (dawn-arrival reinforcements) — the soft
  deadline becomes mechanical.
- Light kernels and emitters (muzzle flash / HE / wreck fire) are reusable
  regardless of TOD value; the only thing the cycle changes is ambient color
  (and the vision multiplier).

## Slices

### Slice 1: `TimeOfDay` value + ambient multiply

`TimeOfDay` type whose ambient color is a function of battle-elapsed-time
(constant in v1, returning the battle-start preset). Lightmap-multiply pass in
the [`render2d`][render2d-batching] pipeline reads it. Day / Dusk / Night
presets selectable at battle start.

### Slice 2: vision multiplier

Night multiplies `visionRange` down through `VisionService`. The multiplier
reads from the same `TimeOfDay` value so an animated cycle later tightens fog
continuously rather than per-preset.

### Slice 3: animated cycle + dawn reinforcements

`TimeOfDay` ambient becomes a real function of elapsed time. The crossing of a
dawn threshold fires reinforcement triggers in Conquest/Assault — the soft
deadline becomes mechanical. Blocked on the reinforcement orchestration layer.

## Cross-refs

- [`../overview.md`](../overview.md) — fog-of-war feature this plugs into.
- [`../complete/fog-of-war-v1.md`](../complete/fog-of-war-v1.md) — the
  `VisionService` / `visionRange` surface the night multiplier hooks.
- [`render2d` batching][render2d-batching] — the lightmap-multiply pass slots
  into this pipeline.
- [`../../conquest/`](../../conquest/README.md) /
  [`../../reinforcement/`](../../reinforcement/) — dawn-arrival reinforcement
  triggers when the animated cycle lands.

[render2d-batching]: ../../battle-render/overview.md
[world-reactive]: ../../README.md
