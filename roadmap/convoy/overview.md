# Convoy

> Long-form companion to the story docs. Open this when picking up the
> ground-vehicle work cold.

## What this is

Ground-vehicle reinforcement for the battle layer. The HEAVY_APC (sole
active variant — MILITIA_TRUCK retired) drives in along the road graph,
deboards a squad, then holds overwatch with a roof-mounted turret before
departing. Vehicles respect wall constraints — the footprint is checked
against the navigation grid each tick. The companion to the shuttle /
air system.

Convoy is one of three landed delivery means (walk-in, convoy, shuttle).
Dispatch is owned by the reinforcement system — convoy ships as the
`ConvoyMeans` provider, gated on compound-driven ticket production
(ARMORY → REINFORCEMENT). See [`../reinforcement/`](../reinforcement/).

## Architecture at a glance

Parallel to the shuttle / air stack, under
`com.dillon.starsectormarines.battle.ground`:

- **`Vehicle` / `VehicleType` / `GroundSystem`** — mirror
  `Shuttle` / `ShuttleType` / `AirSystem`. `GroundSystem` is a stateless
  tick consumer (constructor-injected services, no `*SimContext`).
- **`GroundBody`** abstraction with **`BicycleBody`** kinematic
  bicycle model (wheelbase, max steering, slew). `VehicleType.createBody()`
  is the extension seam for future vehicle classes (tanks etc.).
- **`PurePursuit`** — carrot picker for coarse polyline following.
- **`ReedsShepp`** — closed-form CSC + CCC docking solver.
- **`HybridAStarPlanner`** — `(x, y, heading)` config-space search;
  produces kinematically-feasible poses for inbound + outbound.
- **`VehicleFootprint`** — OBB feasibility check against `NavigationGrid`.
- **`ConvoyPlanner`** — BFS over the road graph, waypoint expansion,
  exit-node picking, three-tier refinement fallback.
- **`Pose`** — immutable `(x, y, facingDeg)` tuple for planners.

Path execution dispatches on plan shape: if the plan carries per-pose
headings it plays poses directly (`advancePlayback`); otherwise it falls
back to `PurePursuit` (`advancePath`). Within docking range it switches
to Reeds-Shepp playback (`advanceDocking`). State machine:
PENDING → INCOMING → LANDED → OVERWATCH → DEPARTING → GONE.

The road graph itself is generator-side
(`battle/mapgen/road/RoadGraphBuilder`, `RoadReservation`); see
[[road_graph_design]].

## Status

V1 (PoC) and the V1-polish maturation pass are **shipped** — see
[`complete/`](complete/). Stage 2 item 1 (Conquest reinforcement
integration) **shipped** as `ConvoyMeans`; the `DEBUG_SPAWN_TEST_CONVOY`
path is now legacy/unused. Item 6 (HEAVY_APC) and item 7 (art) shipped.

Remaining Stage 2 work lives in [`stories/`](stories/):

| Story | What it adds |
| --- | --- |
| [`multi-truck-convoys`](stories/multi-truck-convoys.md) | 2–4 staggered trucks on one road (LZ separation already in) |
| [`truck-infantry-interaction`](stories/truck-infantry-interaction.md) | marines vs. trucks — dodge / squash / hybrid |
| [`vehicle-damage`](stories/vehicle-damage.md) | trucks take anti-vehicle fire; wrecks block roads |
| [`vehicle-variants`](stories/vehicle-variants.md) | supply truck + light scout (asset-side + deboard logic) |
| [`driving-feel-tuning`](stories/driving-feel-tuning.md) | deliberate post-playtest controller tuning pass |

### Why this order

Conquest integration was the visible payoff — without it convoys were a
debug toy (done). Multi-truck is the next visual upgrade — one truck
reads as a coincidence, three reads as a deliberate reinforcement push.
Marine interaction is what makes trucks feel real in the battle. Damage
lets the player counter them. Variants are content; tuning is the
finishing pass that drops in alongside playtest feedback.

## Parked (Stage 3+)

- **BSP frame perim entries — infiltration variant.** V1/Stage 2 only
  use trunk-exit perim nodes (4 per map). A "sneak truck" via a back
  alley needs `PERIMETER_EXIT_MIN_INTERIOR_DEPTH` lowered to 2 or a
  separate BSP-frame perim pass. Trunks-only is more readable
  ("reinforcements come down the main road"); save unless a mission type
  specifically calls for sneaky reinforcements.
- **Tanks / armored vehicles** with hull-mounted turrets that fire while
  moving. Player-side. New combat-side wiring (target acquisition while
  driving, suppression). Its own big slice after trucks are fully
  integrated. The user has ideas here.
- **Air ↔ ground interaction.** Shuttle-mounted A2G turrets vs. ground
  trucks. Easy once vehicles have HP, but the AA-vs-shuttle story has to
  land first or it reads asymmetric.
- **Truck-spawned defenders join the commander.** The marine commander
  loop doesn't yet know about vehicle-spawned squads. For v1 the
  deboarded squad enters the same "free agent" pool the commander picks
  up; explicit wiring is unthreaded.

## Open questions for the design conversation

- Should defender convoys ever go *back out* with wounded marines (like a
  medivac)? Or always one-way reinforcement?
- Player-side convoys — does the player ever call in a friendly truck
  drop? Probably not on shuttle-based maps (shuttles are the player's
  reinforcement vector), but an on-map mission start might fit ground
  reinforcement.
- Convoy routes through hostile territory — does the truck avoid
  known-occupied tactical nodes when path-planning, or take the shortest
  road and pay the cost?

## Cross-references

- [`../reinforcement/`](../reinforcement/) — the orchestration layer above
  convoy. Convoy is the `ConvoyMeans` provider; reinforcement owns *why*
  and *when* reinforcements arrive.
- [`../ai/`](../ai/overview.md) — battle AI (GOAP for infantry/mechs).
  Convoys spawn squads that hook into that planner; commander-loop wiring
  for vehicle-spawned squads is still open (see Parked).
- [`../conquest/`](../conquest/) — convoy is the ARMORY-tier supply means
  in the compound-capture loop.
- Memory: `[[ground_vehicle_kinematics]]`, `[[ground_vehicle_playback]]`,
  `[[road_graph_design]]`, `[[render2d_batching]]`,
  `[[air_unit_render_sync]]`.

## How this directory is laid out

- **`overview.md`** (this file) — concept, architecture, scope framing.
  The stable view; edit rarely.
- **`stories/`** — active/queued story docs, one per slice.
- **`complete/`** — sealed shipped work (commit hash + what actually
  landed).
- **`next-session.md`** — handoff state for picking up cold.
