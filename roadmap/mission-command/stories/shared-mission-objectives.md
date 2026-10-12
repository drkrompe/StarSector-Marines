# Shared mission objectives

Status: IN PROGRESS — remaining mission adapters and live acceptance

Written: 2026-10-12

The standing presentation model lives in `ui-nouns.md`; command and objective
authority remain in `mission-command-nouns.md` and the mission-family designs.

## Remaining scope

- Adapt Raid, Sabotage, Conquest, Assault and the authored opening/freight/
  intervention sequences to the common public mission picture. Preserve each
  mission's actual victory conditions and private reinforcement knowledge.
- Replace anonymous capture codes with authored place names and connect every
  adapter's progress, blockers and friendly tasks to the common map/rail.
- Retire superseded family-specific HUD branches only after equivalent coverage.
- Live-accept extraction, rescue, Last Lift and Silent Colony at 1920 x 1080,
  100% UI scale: source arrival, contest/escort holds, pickup wait, boarding,
  partial survival, branch completion and player-order override.

## Acceptance

The player can identify the current objective, locate its physical place, read
what prevents progress, and understand friendly squad assignments without a
debug overlay or replaying a radio line. Concurrent branches remain independent.
Completion uses the owning mission law; no presentation timer or guessed enemy
count can grant victory. Mission state, squad intent and actual local contact
remain distinguishable. Screenshots use 1920 x 1080 at 100% UI scale only.

Use small projection and state-owner tests for ordinary regression coverage.
Production setup/phase screenshots and full mission replay belong in opt-in
evidence tasks, not the normal unit suite.
