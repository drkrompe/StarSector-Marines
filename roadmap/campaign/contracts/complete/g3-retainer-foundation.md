# G3 — Stationing retainer foundation

**Status:** CODE COMPLETE (2026-08-12)

**Implemented in:** `2487cfaf`

## Goal

Build the persisted term/payment layer Garrison and Cadre acceptance will use,
without generating offers that the current mission-only UI cannot accept.

## Locked rules

- A campaign month is 30 days. Missed ticks catch up whole months exactly once.
- Payment is capped at the contract's expiry day; repeated ticks do not repay.
- Garrison monthly baseline is committed marines × Cr. 20 × 1.10.
- Cadre monthly baseline is committed marines × Cr. 20 × 0.40.
- Patron tier multipliers remain T1 1x, T2 1.5x, T3 3x; Tier 4 standard
  stationing terms are unavailable.
- T1 terms cap at one month, T2 at three, and T3 at six.
- Garrison/Cadre default salvage is 25%/5% respectively.

## Acceptance

- Contract rows persist committed marine count and last-paid day with legacy-save
  backfills and capacity growth.
- Pure term math is directly tested.
- Retainers catch up, cap at expiry, reject mission-mode rows, and only advance
  the payment clock after credits are successfully delivered.
- Full build green.

## Automated verification

- `CampaignStateStationingColumnsTest` covers SoA growth and legacy backfill.
- `StationingContractTermsTest` covers mode, rank, duration, payout, and salvage.
- `ContractRetainerSystemTest` covers exact-once catch-up, expiry caps, failed
  delivery, and excluded rows.
- `gradlew.bat build` passes on 2026-08-12.
