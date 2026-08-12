# G9 — Default extraction mission and consequences

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Make the linked recovery obligation playable through Marine Ops and settle the
stranded stationing personnel exactly once from its battle result.

## Locked rules

- System-generated EXTRACTION rows map to Recovery missions at their own market;
  ordinary Strike/Escort target selection remains house-driven.
- A recovery client cannot be reputation-locked by the employer who defaulted.
- Debug Accept cannot bypass the real Recovery mission path.
- Recovery missions do not grant the defaulting patron normal completion rep,
  promotion progress, or territorial impact.
- Success returns all committed marines and restores the stranded captain to
  ACTIVE. Failed delivery retries without partial mutation.
- Failure loses the committed marines and returns the stranded captain INJURED
  for 45 days.
- Either resolution applies a one-time -10 employer-reputation breach penalty;
  DEFAULTED remains neutral to MRB reputation.
- Terminal recovery rows remain cleanup-protected until personnel settlement
  succeeds, preserving failed-delivery retries.

## Automated verification

- `ContractMissionProfileTest` and `ContractMissionTargetTest` cover Recovery
  mission shape and target selection.
- `ExtractionResolutionSystemTest` covers success, failure, exactly-once breach
  reputation, and delivery retry.
