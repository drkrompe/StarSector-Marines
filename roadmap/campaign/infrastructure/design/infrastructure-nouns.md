# Campaign infrastructure nouns

Status: DRAFT

Written: 2026-08-23

## Purpose

Campaign infrastructure is a future location-bound investment layer. It turns
credits and continuing upkeep into durable reduction of operational friction,
rather than another source of combat power or a second political simulation.
It is the mitigation side of the economy's scale pressure: investing in a
market or region should make a geographically grounded operation more
manageable, not erase the cost of growth everywhere.

There is no infrastructure implementation yet. The existing `housePower`
value is political stake power, and current stationing-default risk consumes
that patron-wide value only. Tactical map buildings are an unrelated battle
concept.

## Vocabulary

- **Infrastructure investment** is a persistent improvement belonging to an
  explicit campaign owner and associated with one campaign location and a
  declared scope.
- **Market infrastructure** affects operations attached to that market.
- **Regional infrastructure** is a more expensive investment whose scope must
  be an explicit region rule, not an inferred global bonus.
- **Build cost** is the one-time admission price; **upkeep** is the recurring
  cost of retaining an operating investment.
- **Operational modifier** is a bounded input into a policy owned elsewhere.
  Infrastructure does not replace that policy or independently resolve an
  event.
- **Defensive infrastructure** may reduce the risk borne by a stationing
  contract at its protected location.
- **Intelligence infrastructure** may improve access to discovery information,
  but never manufactures political facts or resolves a discovery by itself.

## Laws

1. Infrastructure is location-bound and has an explicit owner. Its effect must
   state its location scope, consumer, build cost, upkeep, and stacking rule.
2. A defensive modifier applies to the protected contract location, not by
   silently inflating a patron's political stake power or granting a global
   default-rate reduction.
3. Infrastructure supplies modifiers; contracts own default policy,
   living-world owns discovery policy, and the economy owns company-scale
   pressure. No building is a parallel contract or event system.
4. Player-paid build and upkeep use an explicit company-finance authority and
   fail visibly when unaffordable. A house-owned effect must name its own
   funding authority. A passive benefit may not persist as though it were paid
   when its upkeep cannot settle.
5. The feature must distinguish campaign infrastructure from tactical map
   geometry. A battle building neither grants nor represents a campaign
   investment.
6. A future catalog grows by named effect families and explicit consumers;
   numbers, tiers, and facility fiction follow from a contracted vertical
   slice, not a universal bonus table.

## Ownership and flow

The economy defines why an investment is worth its cost and the scale pressure
it counteracts. This feature would persist the location-bound investment and
expose its modifiers. A contract system then asks for a
location-specific defensive modifier when evaluating a stationing risk; a
living-world discovery system would ask for an intelligence modifier when it
owns a discoverability decision. The campaign framework supplies persistence
and day-based scheduling through `campaign-framework-nouns.md`.

`mechanics.md` remains authoritative for political stakes and `housePower`.
`contracts/overview.md` remains authoritative for stationing/default policy.
`economy.md` remains authoritative for the wider scale-inefficiency and
finance direction. This feature must not fold those nouns into a building
catalog.

## Contracting the first slice

The retained direction names defensive and intelligence effects, but it does
not settle the first facility's identity, owner/admission route, scope,
maintenance timing, affordability outcome, stacking, or exact consumer
formula. Those are coupled player-facing decisions, so no implementation story
is ready yet. Contract one vertical slice only after choosing them together;
do not add a speculative table or wire a modifier into `housePower` merely to
make the feature appear implemented.
