# G27 — Pending Garrison defense visibility

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Keep a triggered Garrison reachable at its market and show why the assignment
left its idle ACTIVE state.

## Locked rules

- Local stationing management treats ACTIVE and IN_PROGRESS stationing rows as
  ongoing obligations.
- The patron remains visible and unlocked while its Garrison defense is pending.
- Management shows the persisted trigger source and the actual on-site captain
  and committed marine pool.
- Cadre incident presentation remains unchanged and takes its own payload path.

## Automated verification

- `StationingOfferLookupTest` covers IN_PROGRESS Garrison visibility alongside
  the existing ACTIVE/OFFERED filtering.
- Full build covers the management and patron-client integration.
