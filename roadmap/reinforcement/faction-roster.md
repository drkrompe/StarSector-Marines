# Faction unit roster

> Per-faction catalogue of "what `UnitType` counts as tier T for this side."
> Centralises the hardcoded type picks in `BattleSetup` defender allocation,
> `AirSystem` deboard, and `WalkInMeans`, so reinforcement units come out
> thematically matched to the requesting faction.

## Why

The current code answers "what should this unit be?" three different ways
depending on call site:

- `BattleSetup.allocateDefenders` — defender alloc literal-types
  `UnitType.MILITIA` / `UnitType.MARINE_RED` / `UnitType.HEAVY_MECH`
  directly from `DefenderRoster.{militiaCount, eliteCount, mechCount}`.
- `AirSystem.tryDeboardMarine` (line ~431) — hardcodes
  `UnitType.MARINE` for every deboarded unit regardless of
  `shuttle.faction`. A `ShuttleMeans` dispatch for `DEFENDER` produces
  marine-stat units flying defender colours.
- `WalkInMeans.dispatch` — hardcodes `UnitType.MILITIA`, with no way
  to vary by side.

For v3 reinforcement this was acceptable (only defender means existed,
shuttle drop reads narratively as "elite quick-response"). But:

1. Attacker-side triggers / means are next on the roadmap. An attacker
   walk-in would currently spawn `MILITIA` (wrong); an attacker convoy
   doesn't exist yet but would hit the same problem.
2. The shuttle-drop thematic mismatch is real — defenders dropping in
   should look like *defender* elites, not marines. Today they're
   labelled DEFENDER but use the MARINE stat block.

A `FactionUnitRoster` per faction lets each call site ask
*"give me tier T infantry for this side"* and get back the right
`UnitType`.

## Shape

```
final class FactionUnitRoster {
    UnitType infantry();   // bulk
    UnitType elite();      // stiffening regulars
    UnitType mech();       // heavy mech, nullable when this faction
                           // doesn't field them
}
```

Per-faction defaults, baked into a static registry:

| Faction  | infantry      | elite          | mech         |
|----------|---------------|----------------|--------------|
| MARINE   | `MARINE`      | `MARINE_BLUE`  | (null) *     |
| DEFENDER | `MILITIA`     | `MARINE_RED`   | `HEAVY_MECH` |
| CIVILIAN | `MILITIA` **  | `MILITIA`      | (null)       |

\* Marine mechs are a future weapon-system addition; null today.
\** Civilians won't be reinforced, but the slot exists so the table is
    complete and the lookup never returns `null` for infantry.

`MARINE_BLUE` already exists in `UnitType` but no spawn site references
it — using it for the marine elite slot is the natural reclamation
(blue marines as "veteran marines" reads cleanly against the player's
default green/grey sprite).

Tiered access — three slots cover the strength scale already in
`ReinforcementRequest.Strength` (`SMALL` = infantry-only, `MEDIUM` =
mixed, `LARGE` = mech-stiffened). Strength scaling itself is still
deferred to a later reinforcement slice — this doc just makes sure
the unit-type pick is in the right place when strength does start to
matter.

## Lookup

Static registry, looked up by `Faction`:

```
FactionUnitRoster roster = FactionUnitRoster.forFaction(req.side);
UnitType infantryType = roster.infantry();
```

Static rather than constructor-injected because the per-faction
catalogue is content-data — same shape across every battle. If a
future contract type wants a faction reskin (e.g., "pirate raid uses
PIRATE_GUNNER instead of MILITIA"), that's the seam to override at
construction time — but until then the static registry keeps the call
sites tiny.

## Call sites

Three writes:

1. **`BattleSetup.allocateDefenders`** — replace the three literal
   `UnitType` references with `roster.infantry()` / `roster.elite()` /
   `roster.mech()`. `DefenderRoster` (mission-level quantity) stays —
   the two layers do different jobs: DefenderRoster says "12 militia
   / 5 elites / 1 mech," FactionUnitRoster maps those to concrete
   types per side.

2. **`AirSystem.tryDeboardMarine`** — the hardcoded `UnitType.MARINE`
   becomes `FactionUnitRoster.forFaction(s.faction).infantry()`.
   Marines stay `MARINE`; defender shuttle drops now produce
   `MILITIA`-stat units, matching their faction. (Or `elite()` if we
   want shuttle drops to read as "stiffening reinforcement" — a knob
   on `Shuttle` would resolve this; defer to playtest.)

3. **`WalkInMeans.dispatch`** — replace the literal
   `UnitType.MILITIA` with `roster.infantry()`. Walk-in is the floor;
   it always wants the bulk type for its side.

## Migration sequence

1. Add `FactionUnitRoster` + the per-faction static table. No call
   sites change yet.
2. Switch the three call sites above to the lookup. Same observable
   behaviour (same `UnitType`s per faction).
3. (Future, separate slice) Add the marine elite type (`MARINE_GREEN`?
   `MARINE_VET`?) and update the marine roster slot.

Steps 1+2 ship together as one small commit; step 3 waits on a real
content-driven need.

## Open questions (defer to implementation)

- **Tier for `ShuttleMeans`** — infantry (current default, militia-
  flavored for defender) vs. elite (reads "stiffening drop")? Either
  works; the choice is narrative. Going with **elite** at first cut
  since shuttle delivery cost-shapes as "expensive reinforcement" and
  the asymmetry vs. convoy/walk-in (both `infantry()`) reinforces
  that.
- **`ConvoyMeans`** — spawns a *vehicle*, not infantry, so no
  `UnitType` pick at dispatch. Its normal deboard already routes through
  this roster (per-faction, `ef4cfeb`); any future squashed-crew
  ejection (convoy [`truck-infantry-interaction`](../convoy/stories/truck-infantry-interaction.md))
  draws from the same lookup.

## Cross-refs

- [`architecture.md`](architecture.md) §v4 — once this lands, the
  attacker-side walk-in / shuttle paths can drop their hard-coded
  faction reject.
- [`../convoy/stories/truck-infantry-interaction.md`](../convoy/stories/truck-infantry-interaction.md)
  — convoy crew ejection routes through the same roster.
