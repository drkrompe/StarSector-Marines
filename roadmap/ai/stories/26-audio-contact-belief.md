# 26 — Noise-backed squad contacts

## Player-visible contract

Gunfire and explosions can alert a squad through walls without granting exact
vision. A detected direct-fire launch creates a lower-confidence contact near
the muzzle, with deterministic localization error. The squad investigates the
heard cell and may use the contact in belief-backed tactical reads until it
decays. Direct LOS still wins immediately: it refreshes the same contact to
full confidence at the exact observed cell.

Marine and defender squads use the same detection, localization, confidence,
and decay rules. Training-tier differences remain a later tuning layer.

## Event ownership and timing

A battle-scoped, thread-safe `NoiseEventBus` owns pending events. Combat
producers post during firing/detonation phases; `SquadAlertSystem` drains the
bus once at the next awareness pass, giving noise a fixed one-tick maximum
latency and preventing a visual projectile from being "heard" repeatedly for
its entire lifetime.

Initial producers:

- every `ShotEvent` posts one launch noise at its muzzle;
- every resolved `PendingDetonation` posts one impact noise at its endpoint;
- the bus exposes the same post surface for future loud world events.

Events carry position, magnitude, source faction, optional source unit id,
kind, and emitted tick. Direct weapon classes retain their shooter id.
Indirect/arcing launches and every detonation deliberately set source id to
zero: they may create an investigation bearing, but cannot identify or stamp
the launcher.

## Detection and localization

Each living squad evaluates every hostile event once, from its current
centroid. Sound ignores navigation LOS. The first tuning contract is:

- maximum hearing radius: `12 + magnitude * 12` cells;
- detection probability: `clamp01(90 * magnitude / (distance² + 25))`;
- the roll is a stable hash of squad, source/event position, kind, and emitted
  tick, so parallel fire-post order and render timing cannot alter simulation;
- a detected event is offset 1.25–4 cells from its real position, scaling with
  range and using a second stable hash for angle;
- source-linked contacts receive confidence 0.4–0.7, highest near the sound;
  ordinary Story 25 decay then ages them out sooner than direct contacts.

Multiple detected noises may update multiple contacts in one pass. A same-tick
direct observation cannot be downgraded by audio. The legacy last-seen
projection uses the newest identified contact or anonymous bearing, with direct
observation winning a same-tick tie.

## Indirect-fire secrecy

- LRM, grenade, Locust, and any other arcing/indirect launch noise is anonymous.
- Explosion noise is always emitted at the impact endpoint with no source link.
- A target squad may therefore learn "a blast happened near here," but neither
  the launch event nor impact event can insert the launcher id into its belief.
- A squad that genuinely sees the launcher still records it through Story 25's
  direct-LOS path.

## Diagnostics

`BelievedContact` exposes `DIRECT` versus `AUDIO` provenance. Selected-squad
debug ghosts render direct contacts magenta and audio contacts amber, both
alpha-scaled by confidence. The most recent anonymous/source-linked audible
bearing has its own yellow marker. The ordinary unversioned squad dump adds
contact provenance plus the latest bearing's cell, tick, age, confidence,
source id when available, and noise kind.

## Acceptance coverage

- A nearby hostile direct shot is heard through a complete wall and creates an
  inexact, sub-1-confidence `AUDIO` contact.
- The same behavior works when marine and defender factions exchange roles.
- Friendly shots never alert or populate the listening squad's belief.
- Direct LOS refreshes an audio contact to exact `DIRECT` confidence 1.
- An indirect launch and its later detonation never create a source-linked
  launcher contact; the impact may still create an anonymous bearing.
- One launch is consumed once rather than re-detected throughout tracer life.
- Overlay and dump diagnostics distinguish provenance without a schema version.

## Out of scope

- Per-unit training/hearing stats, suppression-specific noise, or accumulated
  burst loudness.
- Commander briefings, cross-squad sharing, and commander influence maps.
- Replacing every remaining ground-truth target/path query in this slice.
