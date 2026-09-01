# Conquest on precincts

Status: IN PROGRESS

Written: 2026-09-01

Read `precincts.md` in full first; it owns the model. `conquest-nouns.md` owns
what a Conquest map must contain and `conquest-command.md` owns the three-track
duel that plays on it. `mission-tier-nouns.md` owns tier and risk. This is the
step the whole arc was for: Conquest, the primary territorial battle, generates
its map as places, at the size the precinct model was measured at, with the
settled-or-remote character of the map stated by the mission rather than by an
accident of one recipe.

## Goal

A Conquest battle generates a fortress with a city around it — or a lone
fortress in open country, or a fortress inside a conurbation — from the target
world, at 560x336, with the marine arrival, the three tracks, the front-line
reinforcement layer and the counterattack all working on that map. The stock
crossroad recipe stops being what Conquest plays on.

## What stands between a precinct map and a Conquest map, found by survey

Everything Conquest reads at battle time comes off the finished `MapResult`
and the tactical nodes on it, never off the generator's own compounds. The
survey found four gaps and one trap.

1. **The reinforcement layer reads `BiomeMap` as the front.**
   `RecaptureTargetService`, `RecaptureTargetSystem`,
   `FrontLineReinforcementTrigger` and `CounterattackSystem` bucket every
   defender node and every live defender by `BiomeKind`, walk the buckets in a
   fixed rear-to-front order (fortress, city, port, beach), and rally a
   reinforcement by shifting rearward *along the axis*. A precinct map has no
   biome map, so `BattleSetup.installReinforcementLayer` silently falls back to
   the compound-only trigger: no front-line dispatch and no counterattack at
   all. `precincts.md` open item 4 names the answer — distance from the
   objective — and `grown-road-graph.md` reached the same one.
2. **No paired landing areas.** `ConquestLandingAreaStage` authors the 5x5
   berth pairs on the beach frontage and throws without an axis and a biome
   map; it is not on the precinct recipe, so `map.landingAreas` is empty and
   `conquestArrivalSlots` throws when the mission asks for its drop zones.
3. **A settlement's military base is a second keep.** On a map with no biome
   every `MILITARY_BASE` compound's filler emits a `COMMAND_POST`; the conquest
   recipe only gets one because the biome tags the port's and the city's as
   armoury and barracks. `ConquestCommand.canonicalKeep` returns null for more
   than one command post, which disables the keep phase, and the conquest
   recipe's own post-run assertion demands exactly one.
4. **Posts are unmanned.** `PrecinctDefence` stamps emplacements without a
   `GUARDPOST` node, and Conquest pairs every post with a manned squad through
   `linkGuardpostSquads`, which matches posts to guard-post nodes by anchor.
   The precinct path also has `DefensePostStamper.stampNonConquest` run over it
   again at setup, unmanned, which is a wart Assault and Raid live with today.

The trap: `VehicleCorridor` has **no battle-time consumer at all**. Every
reference is inside the generator and one validation test. `ConvoyMeans` drives
on the road graph. Open item 6 of `precincts.md` closes as "not needed"; the
corridor was a promise the game never collected.

Also confirmed: `CompoundService` is built from tactical node kinds, so the
garrison's rooms (`COMMAND_POST`, `ARMORY`, `BARRACKS`, `AIRBASE`) are
compounds already; `MissionMapRequirements` for Conquest (a defender garrison,
a command post, a garrison airfield, a marine landing zone) are all things the
precinct ward already produces; `TraversalAxis` has two values and both
commanders, the shuttle entry and the reinforcement rear edge are keyed on it.

## Design

### A. The front is a depth, not a biome

`FrontDepth` in `battle.world.model`: a per-cell band index, `0` at the
objective and rising toward the attacker, with a small fixed band count and a
display name per band. It is tier-neutral data on `MapResult` (`frontDepth`,
nullable for maps with no front), built by a closing generator stage on both
recipes so the two cannot disagree about what a front is:

- **Stock recipe:** from the biome map. `FORTRESS_DISTRICT` is band 0, `CITY`
  1, `PORT` 2, `BEACH` 3, `OUTSKIRTS` counts as the furthest band. Names are
  the biome display names the comms presenter already uses.
- **Precinct recipe:** from the objective's claim. Band 0 is the objective
  precinct's own claim; the rest of the map is banded by Euclidean distance
  from the nearest claim cell into three equal-width rings out to the furthest
  cell, so the count is four on both recipes. Names: *the citadel*, *the inner
  districts*, *the outer districts*, *the approaches*. A map with no
  objective has no `FrontDepth`.
- **Rearward** is a method on `FrontDepth`, not an axis: the cell a fixed
  number of steps from a point toward the objective's centroid, clamped to the
  map. On the stock recipe the centroid of band 0 is at the far end of the
  axis, so the shift is the old one within rounding.

The four consumers key on the band index instead of `BiomeKind`; the
rear-to-front walk is `0..bands-1`; `RecaptureTarget.slice` becomes an int
band; `CounterattackCommsPresenter` reads the band's name. The
`installReinforcementLayer` gate becomes `missionType == CONQUEST &&
map.frontDepth != null && tacticalMap non-empty`. `BiomeMap` stays what it is
for the stock recipe's theming and terrain; it just stops being the front.

### B. A precinct map can be landed on, has one keep, and mans its posts

- **`PrecinctLandingAreaStage`**, terminal on the precinct recipe, no RNG.
  Authors paired 5x5 berths inside the attacker's region — the plan's
  `attackerFrom` placement, or the corner `SpawnAnchorStage` chose — with the
  same 16-cell lateral step and 12-cell margin as the conquest stage, approach
  from whichever map edge that region touches, scanning from that edge inward
  so the first legal area is a beachhead. Legality is the conquest stage's
  minus the biome test: walkable, unbuilt, clear of water and posts, wholly
  inside the region. When the region touches no edge (a `CENTRE` placement)
  the approach is the nearest edge.
- **One keep.** When `BspKeys.PRECINCTS` is bound and the plan has a
  programmed objective, a zoned precinct's `MILITARY_BASE` compound emits
  `BARRACKS` or `ARMORY` (alternating by compound index) and never
  `COMMAND_POST`: the keep is the garrison's, and a settlement's base is a
  supply hub the way the port's and the city's are on the stock recipe. Maps
  with no plan are byte-identical.
- **Manned posts.** `PrecinctDefence.stamp` emits a `GUARDPOST` node per
  emplacement it places, priority and garrison as `DefensePostStamper` does
  for the conquest tiers. This is a property of a fortified place, not of the
  mission; Assault and Raid maps get it too, and their evidence deltas are
  reported.
- **The setup-time `stampNonConquest` pass is skipped for a map that already
  carries defence posts.** A fortified place stated its own; a second random
  scatter on top was never a decision anybody made.

### C. Sprawl is mission vocabulary

`PrecinctPlan.Sprawl` — `REMOTE` a lone installation, `BALANCED` a town with
an installation and outlying places, `DENSE` a conurbation to the map edge — is
the dial the arc asked for, and today only tests can turn it.

- **Derived default from the market**, in `SettlementZoning`, which owns what a
  size means: size at most `OUTPOST_MAX_SIZE` (3) is `REMOTE`, size 8 and up
  is `DENSE`, everything between is `BALANCED`; no market is `BALANCED`.
- **A mission may state its own.** `Mission.sprawl` (nullable, builder field),
  threaded through `MissionLaunch` into the factories; every battle fixture
  carries an optional root `sprawl` field (absent means derived, so every
  checked-in fixture is unchanged); the DEBUG briefing gets a sprawl selector
  beside its tier selector so the three maps can be played back to back.
- `BattleSetup.precinctPlanFor` takes the resolved sprawl instead of pinning
  `BALANCED`. Raid keeps refusing `REMOTE` for the reason `precincts.md`
  gives — its target is a point of interest — by clamping to `BALANCED` and
  saying so in one comment, until tactical nodes are targets.

### D. Conquest generates as places, at 560x336

- `MapScale.CONQUEST(560, 336)`, documented as mission-owned: Conquest is the
  authored siege the tier doc exempts from ordinary scaling, its three tracks
  and a fortress with a city around it were measured at this size, and
  `CONQUEST_GRID_W/H` point at it. Nothing else changes scale.
- **The plan is derived with a placement.** `PrecinctPlan.derive` gains an
  overload taking the objective's `MapPlacement` and `attackerFrom`; the
  garrison is seeded inside the objective placement and the plan carries
  `attackerFrom` so the spawn stage honours it. Conquest rolls its axis as
  today and places from it: `SOUTH_TO_NORTH` puts the objective in `NORTH`
  and the attacker in `SOUTH`; `WEST_TO_EAST` puts them `EAST` and `WEST`.
  Both commanders, the shuttle entry and the reinforcement rear edge keep
  keying on the axis, and the map now agrees with them.
- `createConquestBuild` calls `precinctPlanFor(CONQUEST, ...)` and the
  six-argument generate with a null axis, keeps its re-roll loop against
  `MissionMapRequirements`, and passes the rolled axis to everything that
  wanted it before. `precinctPlanFor` admits `CONQUEST`; the market gate
  stays, and a marketless Conquest (no fixture does this) takes the stock map
  as before.
- The stock conquest recipe stays reachable through the axis-without-plan
  generate for tests and scenes; retiring it is a separate cleanup once nothing
  else draws on it.

## Acceptance

- `FrontDepth` on the stock recipe reproduces the biome banding cell for cell
  on one seed, and on a precinct map band 0 is exactly the objective's claim
  and bands are monotone in distance from it. `rearward` on the stock recipe
  lands within one cell of the old axis shift.
- The reinforcement layer installs on a precinct Conquest map: a
  `RecaptureTargetService` with targets in more than one band, a
  `FrontLineReinforcementTrigger`, a `CounterattackSystem`.
- A precinct map at 560x336 with an `attackerFrom` placement authors at least
  as many landing areas as the largest fixture's drop-zone count; a precinct
  Conquest map has exactly one `COMMAND_POST`; every emplacement has a
  `GUARDPOST` node at its anchor.
- `SettlementZoning.sprawlFor` is pinned at sizes 0, 3, 4, 7, 8, 10; a fixture
  without `sprawl` decodes to null; a mission's stated sprawl reaches
  `precinctPlanFor`.
- `ConquestBattleSetupTest` builds a sim at 560x336 for each conquest fixture
  without throwing; `commanderEvidence -Pmission=conquest` runs to completion
  on both fixtures and the report states result, winner, ticks, captures and
  wall clock against the 280x168 baseline recorded before this story. Balance
  is not pinned: a delta is information. Wall clock is the one number that
  can veto the size, and the story says what it was.
- Assault and Raid evidence re-run and their deltas from manned posts are
  stated.

## Not in scope

- Retiring the stock conquest recipe and `BiomeMap`'s theming role.
- `REMOTE` for Raid.
- Authored Conquest briefs beyond the placement pair (a scenario stating
  several places by `PrecinctBrief`); `laidOut` exists and is untouched.
- Open items 1 and 2 of `precincts.md`.
