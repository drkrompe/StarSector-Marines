# Campaign narrative — next session

## State of play

- S1 patron engagement memory is shipped in `1b950e48`; S2 relationship-pattern
  callbacks are shipped in `cbfebaef`; S3 local cross-patron echoes are shipped
  in `53cda364`; S4 patron-linked Chronicle references are shipped in
  `03d8a24e`.
- Completion, failure, voluntary withdrawal, and employer breach append one
  immutable source-contract snapshot through their production authorities.
- Memory survives terminal contract compaction and save/load, and replay is a
  no-op for both memory and reputation.
- Returning-client briefings insert one deterministic, data-authored comms
  officer callback between the officer prefix and current patron body. One
  valid prior engagement uses the S1 outcome line; two or more use the newest
  two facts to recognize one of seven exhaustive relationship patterns.
- First-time patrons may receive one deterministic echo from the newest valid
  different-house engagement at the same market in the prior 180 days. Without
  one, or with malformed/legacy memory, they remain unchanged.
- A patron with no direct engagement history may instead receive one confirmed
  Chronicle reference when that patron was the actor or target in a terminal
  chain outcome, applied throne claim, or kingmaker testament learned within
  the prior 365 days. Chronicle relevance wins over an unrelated local echo.
- Automated focused and full Gradle suites are green. Manual UI validation was
  explicitly deferred for this session.

## Next boundary

No narrative story is active. Contract the next slice before implementation.
Good candidates from the overview are captain observations, contract target
name-checks, or carefully bounded longer-form patron continuity; each should
remain grounded in persisted player-visible facts rather than inferred motives.

## Shipped stories

- [x] [S1 — Patron Engagement Memory](complete/s1-patron-engagement-memory.md)
- [x] [S2 — Relationship Pattern Callbacks](complete/s2-relationship-pattern-callbacks.md)
- [x] [S3 — Local Cross-Patron Echoes](complete/s3-local-cross-patron-echoes.md)
- [x] [S4 — Patron-Linked Chronicle References](complete/s4-patron-linked-chronicle-references.md)
