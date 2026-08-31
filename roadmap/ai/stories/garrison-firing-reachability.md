# Garrison firing-position reachability

Status: DRAFT

Written: 2026-08-31

## Scope

`AbstractZoneAction.advanceToReachableFiringPosition` now refuses a firing
position that cannot be walked to, or that costs a march out of proportion to
the straight line it stands in for. Three call sites go through it: the
committed firing line and the objective-anchored improvement inside
`advanceIntoZone`, and `AttackMove.holdBaseOfFire`.

Four call sites still take the picker's answer unguarded:

- `DefendArea.engageInsideArea` — two calls, anchored on the area centre
- `GuardPostPatrol` — two calls, anchored on the authored post
- `HoldPost` — two calls, anchored on the authored post

Their leashes are smaller and each already has a fallback for a null answer, so
the exposure is narrower than the advancing orders had. It is not zero: an
authored post with a wall inside its own radius can still be handed a firing
cell on the far side of it.

## Why it was not done in the same change

The advancing orders share one method, so one guard covered all three of their
sites. These four are in three unrelated files with three different fallbacks —
`DefendArea` falls back to a prepared position, the garrison posts to their own
hold — and each fallback is a judgement about what that behaviour should do when
it cannot shoot from where it wanted. Folding them in blind would have meant
inventing three of those judgements while the change was about a freeze in a
fourth place.

## Constraints

- Do not pay a second pathfind. The guard is cheap precisely because it uses the
  path the caller was already computing; a version that pathfinds inside
  `TacticalScoring.findFiringPositionWithin` would double the cost on a hot path
  that carries its own `TickInnerProfile` bucket.
- `TacticalScoring.findReachableFiringPosition` already exists and validates by
  pathfinding, with `GarrisonPatrol` as its only caller. Decide whether these
  four want that method or the cheaper caller-side guard; do not grow a third
  way of asking the same question.

## Acceptance

- Each of the four sites either refuses an unreachable position or documents why
  its fallback makes the refusal unnecessary.
- A unit test per behaviour showing the garrison holds rather than freezing.
- Conquest matrix unmoved, or moved for a stated reason.
