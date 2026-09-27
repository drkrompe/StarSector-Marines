# Convoy proof admission

Status: IN PROGRESS

Written: 2026-09-27

## Scope

A captured late-game tick started 104 raw routing snapshots, spending 36.7ms
before any convoy search expanded a node. Apply admission before snapshot
construction: retain lightweight request intents in FIFO order, admit at most
one per tick, and hold at most four prepared proofs including unconsumed results.
Keep the existing shared search and expansion budgets, delivery ranking,
retryable ticket semantics, frozen proof inputs and topology invalidation.

## Acceptance

- A 104-request burst captures no map during dispatch and at most one per tick.
- Repeated requests retain identity/order; obsolete requests expire before work.
- Prepared state stays bounded; stale topology cannot publish an old proof.
- Existing successful/rejected journeys and resource-transaction tests pass.
- Capture queue/prepared/admission counters alongside snapshot and search work.
- Run focused tests and production-paced evidence; report latency alongside cost.

Standing authority remains in `convoy-nouns.md` and `reinforcement-nouns.md`.
