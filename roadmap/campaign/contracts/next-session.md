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

Two contracted, neither started. They close the same gap from opposite ends:
stationing events currently arm silently *and* cost nothing to ignore.

1. [`stories/g31-stationing-response-deadlines.md`](stories/g31-stationing-response-deadlines.md)
   — domain only. A pending Garrison defense or Cadre incident gets a response
   deadline; letting it lapse fails the assignment through the shipped
   resolution policies and costs more reputation than losing the fight. Also
   adds the invariant that a stationing term can never COMPLETE with a live
   payload. Headless-testable, and worth shipping on its own.
2. [`stories/g32-player-event-popup.md`](stories/g32-player-event-popup.md)
   — presentation. A `PlayerEventInbox` projection over the persisted payloads,
   a persisted exactly-once acknowledgement, and a self-triggered
   `CustomVisualDialogDelegate` popup with our own chrome. Depends on G31 for
   the countdown and for its "write them off" option.

Take G31 first. The open questions in [`overview.md`](overview.md) remain
design prompts, not pre-approved work.

### Why these two, and why they are separate

Investigation on 2026-08-22 confirmed both halves of the gap in shipped code:

- **Ignoring an event is free.** `StationingAssignmentService` sets
  `contractPhasesTotal = 0`, so `ContractLifecycleSystem` COMPLETES a stationing
  row at term expiry regardless of a live payload, and `ContractRetainerSystem`
  pays through `IN_PROGRESS`. Take the retainer, never answer, collect
  tier-scaled MRB credibility.
- **Nothing tells the player.** No `MessageIntel`, no `addMessage`, no per-event
  `addIntel` in the campaign layer. The five intel plugins are registered once
  in `onGameLoad` as mutating singletons, so even the two passing
  `forceNoMessage = false` only ever notify at creation.

They are two stories rather than one because G31 is fully headless-testable
domain work and G32 is a UI surface whose shipping gate is in-game smoke —
the same foundation/surface split G26 → G27 → G28 already follow.

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
3. Read the two active stories above; G31 before G32.
4. Preserve the shared contract eligibility, exact-once result, and Garrison
   trigger boundaries rather than adding type-specific side paths. Both active
   stories are deliberately written as new *callers* of shipped policy, not as
   new policy.

## Last roadmap maintenance

- 2026-08-19: moved G1-G30 from `stories/` to `complete/`, added this handoff,
  and marked the campaign contracts thread as having no active story.
- 2026-08-22: contracted G31 (stationing response deadlines and lapse
  consequences) and G32 (player event inbox and self-triggered event popup)
  after an investigation pass over the shipped stationing loop. Recorded three
  follow-ups on G31: Cadre incident failure applies no reputation, stationing
  retainer economics are far below mission payouts, and Tier-1 stationing is
  unimplemented versus the `overview.md` rank table.
