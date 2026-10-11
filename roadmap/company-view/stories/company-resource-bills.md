# Company resource bills

Status: PLANNED — follow Barracks turnaround
Written: 2026-10-11

Read `company-view-nouns.md`, `ui-nouns.md` and the affected workshop's owning
noun doc before implementation. Alpha gate: legible company preparation and upgrades.

## Outcome

Barracks, Fleet Armory, Mech Lab and Boat Deck use one readable transaction bill.
The player can tell what an action costs, what is aboard, and exactly what is
missing without learning a new ratio convention in each room.

## Scope

- One shared presentation contract and MLX component: live commodity icon/name,
  explicitly labelled Cost and Available, stable commodity ordering, and Short by
  when blocked. Compact forms retain the same meaning and accessible detail.
- Keep persistent stock strips separate from the selected action's bill.
- Bulk orders display the combined eligible bill and skipped-squad reasons.
- Explain zero-cost cases: already equipped, free assignment, or installing an
  owned spare. Distinguish fabrication from fitting held stock.
- Costs and affordability come from each existing transaction authority; the
  presentation does not unify infantry templates, mech spare stock and boat fittings
  into a new inventory economy. Commit revalidates live cargo.
- Cover founding, personnel recruitment, infantry issue, mech component/chassis
  fabrication and boat replacement/fittings. Named reserves are people already
  enlisted, not an extra commodity or a cargo charge.
- Use the same vocabulary in the castellan's management guidance.

## Acceptance

Compare all covered screens at 1920x1080 / 100% and enlarged UI scales with
affordable, exact-stock, short, free and bulk transactions. Each shortage names
the commodity and amount; essential meaning never depends on color or hover.
Existing transaction tests remain authoritative for payment and failure safety.
Add focused presentation checks for the shared bill and deterministic snapshots
through the existing snapshot catalog. No economy rebalance belongs in this story.
