# Boat condition and repairs

Status: IMPLEMENTED — live campaign acceptance open
Written: 2026-10-11
Updated: 2026-10-13 — Grounded timed recovery, paid acceleration, berth markers and resumable Castle guidance implemented.

The standing model is in `company-view-nouns.md` (Boat Deck) and `air-nouns.md`
(campaign overhaul versus tactical servicing). Surviving aircraft retain fittings
and recover concurrently within two campaign days. Optional paid acceleration
finishes the remaining work; destruction loses the hull and installed upgrades.
The original wrecked Hermes and sealed facilities remain authored restorations.

## Remaining live acceptance

- Return from victory, defeat and Retreat with a damaged lone Hermes. Confirm it
  remains parked and unavailable for mission lift, then wait with no repair stores
  and redeploy after the countdown. Campaign time, not time in menus, advances repairs.
- Leave and return through HQ, briefing, Barracks, equipment, Mech Lab and Boat Deck;
  save/reload during Castle's unfinished lesson. Check damaged, ready, expedited
  and missing/replaced aircraft. No forced purchase or tutorial material regrant.
- With two boats, confirm healthy lift remains available while the damaged boat
  recovers. Transfer to a compatible company hull without restarting its deadline.
- Review the falling acceleration bill, insufficient resources, retained fittings,
  and a destroyed upgraded boat replaced only at standard fit for the full bill.
- Check early total-loss replacement affordability against accessible markets,
  travel reserves and remaining income. `campaign-learning-curve.md` owns broader
  economy tuning; this feature adds no hidden respawn or rescue allocation.

## Evidence and limits

Focused tests cover worst damage across tactical servicing, stable settlement,
concurrent bounded recovery, fractional campaign time, persistence, ship transfer,
loss, stale quotes and atomic paid acceleration. The portrait/highlight interaction
check clicks the real Expedite control and verifies the payment and resumed lesson.
The `boat-recovery` snapshot suite covers damaged, shortage, overview, ready,
Castle and expanded localized text at 1920x1080 / 100%, with large stock values.

These are automated and headless checks, not a live Starsector save walkthrough.
Castle uses her existing portrait and localized dialogue; new narration is not recorded.
