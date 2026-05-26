# 15 — Convoy Stage 2

**Queued.** V1 landed as commit `76fe54d` ("Convoy V1: RoadGraph +
ground-vehicle layer + debug spawn"). One MILITIA_TRUCK spawns per
battle behind `DEBUG_SPAWN_TEST_CONVOY`, drives in from a perimeter
trunk exit, deboards 6 militia, leaves. Stage 2 turns that PoC into
real gameplay.

## V1 recap (what's already in)

- `RoadGraph` skeleton extracted from BSP trunks + frame roads —
  depth-field local-max for frames, TrunkPlan-aware overlay for
  trunks (including perimeter exits), spur stitching to connect
  frame ends through trunk bands. Single connected graph.
- `Vehicle` / `VehicleType` / `GroundSystem` parallel to
  `Shuttle` / `ShuttleType` / `AirSystem`. `GroundBody` abstraction
  with `BicycleBody` bicycle-model kinematics; `PurePursuit`
  carrot-picker; Reeds-Shepp docking (CSC + CCC families).
- `ConvoyPlanner` — BFS over road graph, cell-list waypoint
  expansion, reversal for outbound.
- `VehicleFootprint` wall constraint: OBB footprint checked against
  NavigationGrid each tick; pose reverts if non-walkable.
- Active variant: **HEAVY_APC** — 4-capacity armored personnel
  carrier with roof-mounted HEAVY_MG turret and 20s overwatch after
  deboard. MILITIA_TRUCK retired (sprite retained for mapgen flavor).
- Sprite sheet `army-apc.png` with separate chassis (frame 0) and
  turret (frame 1) frames; `turretSpriteFacingOffsetDeg` handles
  differing frame orientations.
- Dispatch resource-gated via `BattleResources` — compound-driven
  ticket production (ARMORY → REINFORCEMENT at 0.05/sec/compound).

## Stage 2 scope

### 1. Conquest reinforcement integration (the actual payoff)

> The orchestration side of this item is owned by
> [`../reinforcement/architecture.md`](../reinforcement/architecture.md) —
> the convoy is one means provider among several (walk-in, shuttle).
> The bullets below stay convoy-specific: what the convoy means
> provider does once the reinforcement system hands it a request.

`DEBUG_SPAWN_TEST_CONVOY` goes away. Convoys become the
`ConvoyMeans` provider in the reinforcement system, fulfilling
requests on Conquest maps with a road graph. Open design calls:

- **Trigger.** Time-based (every N sim-seconds) vs. event-based
  (when defender squad strength drops below threshold) vs. authored
  (mission briefing says "expect reinforcements at minute 4").
  Time-based is the simplest start; event-based reads better as
  a "the city is fighting back" mechanic.
- **Destination.** V1 routes to the trunk crossing; Stage 2 should
  route to a node near the `MILITARY_BASE` compound (or whichever
  defender garrison is most depleted). Probably needs a
  graph-distance query: "find the road graph node closest to
  cell (mx, my)."
- **Militia loadout.** V1 uses default `COMBATANT`. Stage 2 picks
  a low-tier militia loadout — rifles, no rockets, lighter armor.
  May need a new `MarineLoadout` preset.
- **Squad assignment.** The deboarded militia squad should hook
  into the `ConquestCommand` so it gets objective assignment like
  other defender squads. Today the squad mints fresh and might
  not enter the commander's attention loop.

### 2. Multi-truck convoys + spacing

One truck per spawn is the PoC. Real convoys are 2–4 trucks staggered
on the same road.

- **Spawn cadence.** Same `ShuttleAssignment` shape but for
  vehicles — `ConvoyAssignment(VehicleType type, int count, float
  staggerSec)`. Each truck spawns `staggerSec` after the previous.
- **Following distance.** Trucks consume the same waypoint queue
  but should keep 2–3 cells between centers. Cheapest implementation:
  per-truck "lead truck" reference; if `body.distanceTo(lead.body) <
  minFollowDist`, clamp `desiredFwd` lower. Avoids real
  car-following dynamics.
- **Stagger on deboard.** Trucks arrive at the same LZ in
  sequence; the LZ deboard scan already handles "no free cell,
  retry next tick," so the second truck just waits its turn.

### 3. Truck vs infantry interaction

Today the truck is fractional-position and the sim doesn't know it
exists for collision. Marines walk through it; trucks drive
through marines. Pick one of:

- **Marines dodge trucks.** Predictive avoidance — marines whose
  GOAP path crosses a truck's projected position re-plan. Read as
  "civilians scattering."
- **Trucks squash marines.** A truck driving over a marine cell
  applies impact damage. Reads as serious threat, but ugly when
  the truck flattens its own deboarded militia.
- **Hybrid.** Trucks slow down (or honk) when marines are in their
  path. Marines yield to friendly trucks, dodge enemy ones.

Hybrid is the right answer but most expensive. V2.1 likely starts
with "marines dodge" and adds the squash variant later.

### 4. Anti-vehicle weapons → trucks take damage

V1 trucks have no HP, never take damage. Stage 2 wires them into
the existing damage system:

- `Vehicle.hp` field (already on Shuttle as wired-forward; copy
  the pattern).
- Marines' rocket launchers + mech LRMs can damage trucks. Direct
  fire (rifles) does less than rockets.
- HP-zero trigger: truck wreck. New `wreckedSpriteFrame` on
  `VehicleType` or a separate `wrecks.png` sheet. Wreck stays as
  a blocking doodad on the road.
- Driver/passengers on a destroyed truck either die or eject as
  scattered militia (1-2 survivors, low HP).

### 5. BSP frame perim entries — infiltration variant

V1 only uses trunk-exit perim nodes (4 per map). Stage 2 could
optionally enable BSP-frame perim entries as a separate variant:
"sneak truck" comes in via a back alley. Requires lowering
`PERIMETER_EXIT_MIN_INTERIOR_DEPTH` to 2 OR adding a separate
"BSP frame perim" pass.

Open whether this is worth it. Trunks-only is more readable
("reinforcements come down the main road"). Save unless a mission
type specifically calls for sneaky reinforcements.

### 6. Vehicle variants

**HEAVY_APC: shipped.** Armored 4-capacity variant with roof-mounted
HEAVY_MG turret, 20s overwatch after deboard, dedicated `army-apc.png`
sprite sheet. MILITIA_TRUCK retired — enum constant removed, sprite
retained for mapgen flavor only.

Remaining variant ideas:
- **Supply truck** — instead of marines, drops crates/ammo at
  defender garrisons. Different deboard logic — equipment drops,
  not units.
- **Light scout vehicle** — faster, smaller footprint, no turret.
  Runs supplies or carries a 2-man recon team.

Both would reuse the same kinematic + path-following + wall constraint
code; the work is asset-side (sprites) + variant-specific deboard.

### 7. Art replacement

**Shipped.** `army-apc.png` is a purpose-built top-down sprite sheet
with separate chassis (frame 0) and turret (frame 1) frames. The
`TurretAuthorPanel` provides visual validation of mount/pivot
positions and facing offsets.

`VehicleType.spritePath` + `spriteFrame` + `spriteFacingOffsetDeg`
+ `turretSpriteFacingOffsetDeg` parameterize all of this; new
variants are a one-enum-constant addition.

### 8. Driving-feel tuning pass

V1's controller passed the "drives roads sensibly" bar. Stage 2
should do a deliberate tuning pass after watching dozens of
convoys:

- Does the truck commit to corners convincingly, or does the
  brake-formula make it too cautious?
- Does the look-ahead occasionally pick a "too far" target on
  near-aligned waypoints and cause the truck to clip corners?
- Should the truck back up if its goal is briefly behind it (e.g.,
  for a U-turn at a dead-end), or stop and re-plan?

These are visible-in-playtest tweaks; most are 1-line changes to
`GroundSystem` constants or the `TRUCK` handling profile.

## Out of Stage 2 (parked for Stage 3+)

- **Tanks / armored vehicles** with hull-mounted turrets that fire
  while moving. Player-side. The user has ideas; this is its own
  big slice with new combat-side wiring (target acquisition while
  driving, suppression effects, etc.). Save for after trucks are
  fully integrated.
- **Air ↔ ground interaction.** Shuttle-mounted A2G turrets vs
  ground trucks. Easy once vehicles have HP — but the AA-vs-shuttle
  story has to land first or it reads asymmetric.
- **Truck-spawned defenders join the commander.** Marine commander
  loop currently doesn't know about vehicle-spawned squads. Wiring
  exists; just hasn't been threaded.

## Why this order

Conquest integration (1) is the visible payoff — without it
convoys are a debug toy. Multi-truck (2) is the next visual upgrade
— one truck reads as a coincidence, three reads as a deliberate
reinforcement push. Marine interaction (3) is what makes the
trucks feel real in the battle. Damage (4) lets the player counter
them. Variants (6) and art (7) are content; tuning (8) is the
finishing pass that drops in alongside playtest feedback.

## Open questions for the design conversation

- Should defender convoys ever go *back out* with wounded marines
  (like a medivac)? Or always one-way reinforcement?
- Player-side convoys — does the player ever call in a friendly
  truck drop? Probably not on shuttle-based maps (shuttles are the
  player's reinforcement vector), but if a mission has the player
  starting on-map, ground reinforcement might fit.
- Convoy routes through hostile territory — does the truck attempt
  to avoid known-occupied tactical nodes when path-planning? Or
  just take the shortest road and pay the cost?
