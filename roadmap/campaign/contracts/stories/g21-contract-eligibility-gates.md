# G21 — Contract eligibility gates

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Make MRB credibility and per-house reputation constrain which patrons can offer
and award new work, while preserving access to existing obligations.

## Locked rules

- Tier 1 is always the recovery floor. Tier 2 requires MRB 5; Tier 3 requires
  MRB 20. Tier 4 remains outside standard contract generation.
- House reputation below -25 blocks new work from that patron.
- Generator eligibility and first acceptance/deployment share one policy, so a
  stale offer cannot bypass a reputation loss after generation.
- Active/in-progress contracts and system-generated Recovery remain reachable;
  eligibility never traps personnel or voids an accepted obligation.
- Patron clients show a credibility lock reason when MRB/house standing, rather
  than vanilla faction hostility, is the blocker.
- The existing debug house-gating bypass explicitly overrides the policy.

## Automated verification

- `ContractEligibilityTest` covers rank thresholds, house-rep burn, bypass, and
  obligation/Recovery access.
- `ContractGeneratorTest` covers Tier-2 suppression and boundary unlock.
- Stationing assignment and briefing launch both call the same acceptance gate.
- Full build green.
