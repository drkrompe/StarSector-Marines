# S1 — Power framework skeleton — ✅ SHIPPED (compile-verified)

Stood up the `CommandPower` abstraction, a player command-point pool on the
battle sim, and one trivial power — a **recon ping** that lifts fog in a radius
— wired all the way through the battle UI. Proves the
invoke → target → resolve → cooldown loop end-to-end before any catalog (S2),
real combat power (S3), or meta-progression (S5) work.

## What landed

**Sim core — new `battle/power/` package:**

- `CommandPower` — abstract power: `id`, `displayName`, `cpCost`,
  `cooldownSeconds`, a `resolve(cellX, cellY, service)` hook, and
  `previewRadiusCells()` (targeting-ring shape; 0 = point target).
- `ReconPing extends CommandPower` — the S1 power. Placeholder tuning: 2 CP,
  8s cooldown, reveals an 8-cell radius for 15s. `resolve` registers an
  `ActivePing` on the service.
- `CommandPowerService` — state owner (sibling to `command.BattleResources` /
  `command.CommanderService`): a **single player CP scalar** (4 start / 10 max /
  0.5 per sec regen — *not* the per-faction array `BattleResources` uses, since
  powers are player-only), the available-power roster (hardcoded to `ReconPing`
  in S1; S2's resolver replaces this), per-power cooldown timers, the
  UI-requested activation queue, and the live `ActivePing` list. Nested
  `ActivePing` (cell, radius, remaining TTL) and `PendingActivation` records.
- `CommandPowerSystem` — stateless consumer: drains the activation queue
  (commit CP + start cooldown + `resolve`), regens CP, ages cooldowns + ping
  TTLs each tick.
- `package-info.java` charter.

**Wire-up:**

- `BattleSimulation` — `commandPowers` + `commandPowerSystem` fields,
  `getCommandPowerService()`, `commandPowerSystem.tick(TICK_DT)` folded into the
  command-tier region of the tick loop (after `commanders.tick`).
- `BattleScreen.advance` — projects each `ActivePing` into the fog via the
  existing `VisionService.addEphemeralSource` seam, right after the shuttle
  loop. **No `FogOfWarService` change** — the sim owns the ping state + TTL, the
  view layer projects it (same pattern shuttles/fighters use).

**UI — `battle/ui/panel/CommandPowerPanel`:**

- Bottom-center power bar, one button per available power, CP readout,
  cooldown/affordability tinting.
- Click a ready power → arm **targeting mode** (a view-only lifecycle state —
  the sim never sees `TARGETING`); cursor reticle + reveal-radius ring track the
  mouse cell; a world click queues the activation via
  `requestActivation(powerId, cellX, cellY)`; RMB or re-clicking the armed
  button cancels.
- Registered **after** `WorldPicker` in `ensureHud` so `BattleHud`'s
  reverse-order input lets it claim the button click and the targeting
  world-click first; when not targeting, world clicks fall through to the picker
  untouched.

## Decisions that shaped the build (grounded in the code)

- **Queued activation seam** (the one real architectural choice): a UI click
  *enqueues* an activation rather than applying it inline — mirrors
  `queueSpawn` / the `DamageService` queue-then-flush — because GOAP plans in
  parallel and the sim ticks on its own loop. This also splits the lifecycle:
  `TARGETING` is UI-only; the sim only ever sees `COMMITTED → COOLDOWN`.
- **Fog projection lives in the view layer, not a sim system** — ephemeral
  vision sources are pushed from `BattleScreen.advance` (where shuttles/fighters
  already are), so recon pings join that seam instead of the sim ever touching
  ephemeral sources.
- **Ephemeral-source reveal** (vs a one-shot bitmap bump): re-projected each
  frame from the service's `ActivePing` list, so it respects line-of-sight via
  the existing shadowcast and gives a timed reveal for free.
- **Single player CP scalar** (vs per-faction): generalize only if an enemy
  commander ever gets powers.

## Verified

Forced clean recompile (`gradlew compileJava --rerun-tasks`) **green**; all five
new files + `BattleScreen` report zero IntelliJ errors. (A transient red during
development was a parallel session's in-flight `CompoundFiller.fill` refactor,
unrelated to these files — since landed.)

**In-game feel-out is still pending** — the placeholder tuning (2 CP / 8s /
8-cell × 15s) hasn't been exercised in live play. That's the natural next touch
when picking this back up, alongside the open forks below.

## Follow-ups / open for the next slices

- **Settle the "committed" fork** (explicit detachment vs implicit fleet) before
  S2 — it changes the resolver's input. Recommendation in `overview.md`:
  explicit detachment.
- Tuning (CP cost/regen, cooldown, reveal radius/duration) is placeholder;
  capacity scaling is S5.
- UI surface is minimal (bottom-center bar) — power-bar placement + the
  zone-targeting flow are still an open fork in `overview.md`.
