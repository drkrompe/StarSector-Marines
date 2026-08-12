# G12 — Stationing withdrawal domain

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Make voluntary early withdrawal an atomic domain operation with explicit
personnel, employer-reputation, and MRB consequences.

## Locked rules

- Only idle ACTIVE Garrison/Cadre assignments can withdraw. IN_PROGRESS and
  terminal contracts keep their dedicated resolution paths.
- All committed marines return and the bound captain becomes ACTIVE.
- Personnel delivery must succeed before any contract or reputation mutation.
- Withdrawal flips the contract to ABANDONED and naturally forfeits future
  retainers and Cadre training ticks.
- First-pass penalties are -15 employer reputation and -10 MRB reputation;
  the employer's failed-contract counter increments once.
- Repeated withdrawal attempts are no-ops.

## Automated verification

- `StationingWithdrawalServiceTest` covers successful exactly-once settlement,
  failed-delivery atomicity, and invalid-state rejection.
- Full build green.
