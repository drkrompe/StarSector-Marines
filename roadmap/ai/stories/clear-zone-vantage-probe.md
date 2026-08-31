# ClearZone and the vantage probe

Status: DRAFT

Written: 2026-08-31

## The gap

`TacticalScoring.findFiringPosition` is two-stage. Stage 1 scores line of sight
and weapon range and never asks whether a path exists; stage 2 walks vantage
candidates and pathfinds, taking the first reachable hit.
`findReachableFiringPosition` is the seam between them — when stage 1 hands back
a cell across a wall, it falls through to the probe, and only a null from
*that* means no approach exists.

Four pursuit callers took a stage-1 cell, set the empty path, and pinned the
member. Those are fixed. `ClearZone` never pinned anyone — it already dropped
the target on an empty path, and its comment names the SQ-96 garrison freeze it
was written against. What it does not do is ask the probe first, so it drops
targets that have a reachable vantage.

## Why it is not fixed here

Making `ClearZone` consult the probe is a change to working shipped behaviour,
and it is the *only* part of that change that moves the canonical matrix at
all. Measured at one merge-base, identical fixture hashes, with the four
pursuit fixes as the control:

| | base | 4 pursuit fixes | + ClearZone |
|---|---|---|---|
| `reinforced-south` duration | 8124 | 8124 | 8675 |
| `reinforced-south` captures / losses | 6 / 4 | 6 / 4 | 3 / 1 |
| `reinforced-south` secure episodes | 31 | 31 | 16 |
| `full-strength-west` captures / losses | 3 / 3 | 3 / 3 | 5 / 4 |
| `full-strength-west` defender casualties | 371 | 371 | 432 |
| `full-strength-west` squads lost | 12 | 12 | 9 |

The four pursuit fixes are byte-identical to baseline — `summary.json` and both
traces — so the fixtures never exercise the pin they remove. Everything above
is `ClearZone` alone, and it is a trade: one fixture worse, one better, net
compounds held unchanged on both.

A trade needs a reason to prefer one side, and two fixtures do not supply one.

## What would settle it

`reinforced-south` loses captures and halves its secure-compound episodes while
losing fewer squads, which reads as less churn rather than less progress — the
same ground held with fewer attempts. Whether that is better is a question
about how the commander should value repeated attempts on a compound, and it
wants either a scene built to ask it or a stated preference, not a third
fixture.

## Acceptance

- A decision on the churn question, recorded here.
- If adopted: `ClearZone` asks `findReachableFiringPosition` before dropping,
  and the matrix moves only in the direction that decision chose.
