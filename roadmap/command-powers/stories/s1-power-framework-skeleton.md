# S1 — Power framework skeleton

> First slice. Prove the invoke → target → resolve → cooldown loop end-to-end
> with one trivial power, before any catalog or resolver work.
> ([[feedback_ship_then_optimize]] — one working power first.)

## Goal

Stand up the `CommandPower` abstraction, a command-point pool on the battle
sim, and **one** trivial power (a **recon ping** that lifts fog in a radius),
wired all the way through the battle UI.

## Scope

**In:**
- `CommandPower` abstraction + a power lifecycle: `AVAILABLE → TARGETING →
  COMMITTED` (cost paid) `→ COOLDOWN → AVAILABLE`.
- A **`CommandPowerService`** owning state (CP pool, regen rate, per-power
  cooldown timers) and a stateless **system** that ticks it — the
  Services-own-state / Systems-are-stateless-consumers shape
  ([[battle_services_systems]], default to the ECS shape
  [[feedback_entity_for_loop_endgame]]).
- One concrete power: `ReconPing` — click a button, pick a cell, deduct CP,
  lift fog in a radius, start cooldown.
- Minimal battle-UI surface: a single power button + click-to-target a cell,
  bracketed correctly against Starsector's polluted GL state
  ([[gl_state_gotchas]]).
- A `package-info.java` charter for the new package
  ([[feedback_package_info_charters]]).

**Out (hardcode or stub):**
- Fleet → available-powers resolver (S2) — the one power is just always
  available.
- Real combat powers (S3), insertion (S4), resource costs beyond CP,
  meta-progression (S5).

## Design notes

- **Command points** are the in-battle activation currency (see overview §
  two-phase economy). For S1: a fixed starting pool + constant regen; the
  command-level curve comes in S5.
- The recon ping is chosen because it's *self-contained* — it touches the
  fog-of-war bitmap and nothing combat-critical, so the loop can be proven
  without damage/AI plumbing. Lift fog via the ref-counted vision bitmap
  ([[project_fog_of_war]]).

## Dependencies

- Fog-of-war (the ping lifts fog).
- The battle HUD / input routing in the full-canvas battle takeover.

## Acceptance

In a live battle: a recon-ping button is visible; clicking it enters targeting;
clicking a cell deducts CP, reveals fog in a radius around that cell, and starts
a cooldown; the power cannot refire until the cooldown elapses and CP is
available.
