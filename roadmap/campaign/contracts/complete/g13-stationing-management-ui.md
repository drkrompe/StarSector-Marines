# G13 — Stationing management UI

**Status:** CODE COMPLETE (2026-08-12)

**Implemented in:** `92c4910e`

## Goal

Keep an accepted stationing assignment visible at its market and expose the
G12 withdrawal operation through the existing Marine Ops surface.

## Locked rules

- Patrons with an active local Garrison/Cadre remain visible as clients after
  their offer leaves OFFERED state, even if faction reputation is hostile.
- The dossier stack prefers `Manage` for an active assignment over configuring
  a newer stationing offer from the same patron.
- Management shows the bound captain, committed marines, days remaining,
  monthly retainer, forfeiture, and exact employer/MRB penalties.
- `Withdraw Early` routes through `StationingWithdrawalService`; the UI never
  mutates personnel, contract state, or reputation directly.
- The first player-facing management surface is local to the stationed market;
  remote contract management remains a later intel-feed enhancement.

## Automated verification

- `StationingOfferLookupTest` covers active local assignment lookup and state,
  patron, and market filtering.
- `StationingWithdrawalServiceTest` remains the consequence invariant net.
- Full build green.
