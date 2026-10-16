# Mech pattern cards

Status: PLANNED
Written: 2026-10-16

Direction: `heavy-equipment-progression.md`. Model: `mechs-nouns.md`,
`progression-nouns.md`.

## Outcome

The Mech Lab's fabrication gate becomes a set of learned patterns. A company
starts knowing the Hound, nose chaingun and SRM-5 (today's
`starterPatternsOnly` set) and learns every other chassis, weapon component and
replenisher from a pattern card it found, bought, earned or recovered.

## Scope

- A pattern registry naming every fabricable mech item with its access tier.
  Chassis and weapon tiers are an authored table, not derived from stats.
- `MechBay` holds learned pattern ids. `MechWorkshop` refuses `PATTERN_LOCKED`
  by asking that set instead of the starter flag. Save migration: a legacy
  unrestricted workshop learns every pattern; a new-campaign save learns the
  starter three.
- A pattern card special item (`special_items.csv`) with a plugin modelled on
  `LoadoutCardItemPlugin`: right-click learns, consumed only when it teaches
  something new.
- Sources: the existing faction equipment catalog, picker, market stock, patron
  rewards and salvage carry pattern cards beside loadout cards, gated by the same
  `EquipmentAcquisitionEligibility`. Faction weights follow faction issue
  (`FactionMechLoadouts`): Tri-Tachyon lasers, Hegemony autocannons, Path
  demolition cannon, Lion's Guard thermal lance.
- Mech Lab: unlearned items are absent from the catalog (no placeholder), and a
  learned-but-unbuilt item shows its fabrication bill.

## Constraints

- Never touch the Marine Armory or vanilla blueprint/known-weapon APIs.
- Learning is permanent and independent of stock; losing every assembly keeps
  the pattern.
- Wayward's lesson and its loose SRM-5 keep working unchanged.

## Acceptance

- Unit: learn, duplicate, unknown id, migration of both save shapes, the
  workshop gate.
- A pattern card bought in a market appears in the lab catalog and can be
  fabricated; one at a higher tier is not offered below its gate.
- `createSnapshots` evidence of the lab catalog before and after learning, at
  1920x1080 / 100%.
