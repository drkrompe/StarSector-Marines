# G14 — MRB reputation scoring

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Activate the player-wide MRB credibility track for ordinary contract outcomes
and centralize reputation mutation before it becomes a contract gate.

## Locked rules

- Completed contracts award MRB reputation by patron tier: T1 +1, T2 +3,
  T3 +10, T4 +20.
- Failed contracts cost 1 MRB reputation; house-reputation deltas remain
  caller-specific (-2 mission failure, -1 expired stationing term).
- ABANDONED applies -15 house/-10 MRB and counts as a failed contract.
- Employer DEFAULTED/Recovery consequences apply -10 employer reputation but
  remain MRB-neutral and do not count as the player's failure.
- EXTRACTION followups remain excluded from ordinary completion/failure scoring.
- House rep, unsigned-short counters, and the MRB integer saturate safely.

## Automated verification

- `ContractReputationTest` covers tier scaling, failure, abandonment, employer
  breach neutrality, and saturation.
- Existing withdrawal and extraction-resolution tests lock their unchanged
  observable consequences through the central policy.
- Full build green.
