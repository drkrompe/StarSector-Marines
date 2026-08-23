# D2 — Armor-aware decisions

> Choose anti-armor fire because armor is present, not because a type name says
> it should be.

Status: PLANNED
Written: 2026-08-23

Read `combat-durability-nouns.md` before implementing this story.

## Goal

Move targeting, scarce-ammunition use, and mech morale from hardened unit-type
proxies onto current armor and predicted resolved damage.

## Scope

- Replace hardened-type target affinity and rocket/AMR gates with shared damage
  previews over current armor, rating, structure, and committed rounds.
- Prevent scarce anti-armor shots from being committed to already-exposed or
  already-doomed targets when a materially better armored target exists.
- Emit and consume one armor-break transition. Route mech morale's current
  “armor gone” cap through that transition and retain structure thresholds for
  later damage pressure.
- Keep policy faction-neutral and deterministic.

## Acceptance

- AI weapon choice changes when the same target moves from intact armor to
  exposed structure without changing unit type.
- Committed damage reservation understands expected armor break and avoids
  obvious scarce-ammunition overkill.
- Mech armor morale fires exactly once from real armor depletion.
- No combat decision relies on `TacticalScoring.isHardened`.

