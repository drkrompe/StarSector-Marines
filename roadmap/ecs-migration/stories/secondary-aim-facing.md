# Secondary-aim facing

Status: PROPOSED

Written: 2026-08-23

Read `ecs-nouns.md` before implementing this story.

## Goal

Finish the secondary-aim facing contract that `FacingSystem` already begins:
keep using a live secondary aim target while the pose is active, and choose the
intended fallback when that target is absent or has died.

## Acceptance

- Existing live-secondary-target selection remains covered and unchanged.
- An absent or dead secondary target follows one explicitly documented fallback
  (primary target, travel bearing, or neutral facing).
- Base-sheet fallback and ordinary primary-facing actors remain unchanged.
- Focused authored-appearance tests cover live, absent, and dead secondary targets.
