Status: IN PROGRESS
Written: 2026-09-27

# District patrol work budget

`ai-nouns.md` owns local movement intent and optional patrol behavior. The paced
support-lance replay attributed 113522 expansions to one PatrolRoute squad in
a tick, but did not distinguish quiet waypoints from investigation. Label the
query-time branches before attributing that burst.

If quiet district wandering is responsible, give its randomly chosen waypoints
the existing bounded optional route policy, preserving independent guard and
district controls and counters. Preserve node selection, squad dwell and stale
parallel-result rejection. Investigation, required home travel and compound
room rounds remain separate obligations. Use tiny direct action tests plus the
production-paced eight-mech fixture, not a timing assertion in the unit suite.
