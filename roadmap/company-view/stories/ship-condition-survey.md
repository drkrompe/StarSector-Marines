# Walk the Ship: condition survey and reserved facilities

Status: Implemented; live acceptance open
Written: 2026-10-12
Updated: 2026-10-12 — Shipwide survey, sealed Mech Lab and spare billets, and distribution repairs.

## Scope

Second Wind starts as a battered but usable company home. Walk the Ship lets the
player select rooms directly or from the condition list, focus the real deck,
and read Castle's portrait-backed explanation of condition, benefit and next work.
The whole plan remains fixed through restoration.

The Mech Lab starts destroyed and sealed. Completing the second story mission,
Take Back the Depot, restores its single gantry as part of Wayward's recovery.
It needs no additional payment before the existing first-mech fitting tutorial.
Two spare bunkrooms start sealed; a 4,000-credit yard job after Relief restores
18 usable billets (36 to 54 on the converted starting hull). Working rooms
throughout the hull have unreliable distribution circuits. A separate 3,000-credit
post-Relief yard job restores their steady, broader lighting. Its benefit is
presentation and crew comfort, with no hidden combat modifier. Unrepaired
structural rooms retain emergency lights. Workshop and second-bay repairs retain
their existing prices and benefits, and are accessible from the room survey.

## Acceptance

- [x] Render damaged and restored interiors and real survey at 100% and 150%.
- [x] Verify multiple layout seeds retain essential access, 36 starting billets,
      zero sealed mech gantries, and the same compartment locations after repair.
- [ ] Live custom start: inspect and click rooms, pan/zoom, review and cancel
      orders, pay at the yard, verify immediate room/navigation refresh.
- [ ] Live mission two: recover Wayward, open the restored Mech Lab and continue
      Castle's existing fitting tutorial without an extra repair purchase.
- [ ] Save/reload and company transfer preserve paid work on the original hull;
      other hulls retain ordinary facilities. Verify insufficient funds and repeat
      orders charge nothing.

Standing model: `company-view-nouns.md`, `ship-interiors-nouns.md`.
