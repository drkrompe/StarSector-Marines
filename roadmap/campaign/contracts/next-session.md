# Contracts — next session

## State of play

The full numbered contracts spine, G1 through G30, is code-complete and now
archived under [`complete/`](complete/). The shipped vertical includes:

- deterministic, rank-gated offers for Strike, Escort, Planetary Assault,
  Garrison, and Cadre work;
- retainer-backed named stationing, withdrawal, default, and Recovery;
- MRB eligibility and outcome scoring;
- multi-phase Planetary Assault negotiation, retry, idempotence, and cadence;
- deterministic Cadre incidents with playable local-detachment resolution;
- playable Garrison defenses triggered by vanilla raids, rival Strikes, and
  internal market ownership flips.

The consolidated implementation record is
[`complete/contracts-loop.md`](complete/contracts-loop.md). Each G-story also
retains its own acceptance contract and implementation commit in `complete/`.

## Active story

None. Contract a new G31 story before changing production code in this thread.
Do not treat the open questions in [`overview.md`](overview.md) as pre-approved
work; they still require a deliberate product choice and a bounded vertical.

## Deferred manual checks

The prior session explicitly deferred UI validation. Two archived records retain
that fact:

- G2 — smoke a naturally generated rank-gated Escort offer end to end.
- G5 — smoke stationing offer acceptance and assignment management in game.

These are validation tasks, not missing implementation. Keep them deferred
unless the user asks to resume the manual queue.

## Cold start

1. Read [`../architecture.md`](../architecture.md), then
   [`overview.md`](overview.md).
2. Read [`complete/contracts-loop.md`](complete/contracts-loop.md) and the
   specific completed stories adjacent to the proposed extension.
3. Write the G31 story with locked rules and automated verification before
   implementation.
4. Preserve the shared contract eligibility, exact-once result, and Garrison
   trigger boundaries rather than adding type-specific side paths.

## Last roadmap maintenance

- 2026-08-19: moved G1-G30 from `stories/` to `complete/`, added this handoff,
  and marked the campaign contracts thread as having no active story.
