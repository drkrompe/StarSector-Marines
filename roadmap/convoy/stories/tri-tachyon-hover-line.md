# Story: Tri-Tachyon hover line

Status: IN PROGRESS — owner direction recorded; building.

Written: 2026-10-18

Tri-Tachyon's first purpose-built company vehicles. `convoy-nouns.md` (A vehicle
is a unit; The light roles) and `mechs-nouns.md` (vehicle patterns, gantries,
armor drop) own the model; `heavy-equipment-progression.md` (Vehicles: variants,
not fittings) owns the field-rig law and the armor-per-credit rule. This story
adds a line, not a second vehicle model.

## Why, and what the lore allows

The owner's laws (2026-10-18): a truck-built hull is a field rig that pirates and
independents weight, never a navy; Tri-Tachyon's hulls are **hover and drone
coded**. Today TT weights the tracked `HEAVY_GUN_TANK`, which is the wrong signal.

Evidence, by tier (`faction-lore-nouns.md`):

- **Canon anchor.** The `hand_weapons` commodity is "mechs, tanks, and
  hovercraft" — hover ground craft exist in the Sector. TT's core competencies
  are AI, force fields, high-power devices and armaments; Eochu Bres's salariat
  are "protected by security drones"; a hostile captain docking at a TT facility
  is "followed by an indiscreet security drone". The high-tech ship systems
  `drone_sensor` (drones that extend the parent's sensor and weapon range, armed
  with an ion cannon) and `drone_pd` (laser-armed defensive drones) are TT's
  drone vocabulary. The Terminator drone's AI is TT's work, and most drone
  systems are "on remote-control, or feature a sub-gamma personality at best".
- **Strong inference.** Autonomous alpha+ AI is the Domain/Hegemony ban that two
  AI Wars and the inspections were fought over. TT's ground drones are therefore
  **tethered**: remote-controlled from a crewed carrier, never free hunters.
  High technology is scarce, thermally demanding and dependent on infrastructure
  (`tri-tachyon-lore-guide.md`), so the line should be precise and fast while it
  works and brittle when its network fails.
- **Mod direction.** Hover means a lift-fan/field skirt hull, not antigravity.

Anti-caricature: these are crewed corporate hulls, not an AI army.

## Proposed line

| Hull | Role | Lift | Tier | Weapon | Replaces for TT |
| --- | --- | --- | --- | --- | --- |
| **Hover scout** (working name *Kite*) | Fast screen and sight hull: `ScoutSight` mast plus a light pulse-laser turret | Light | Advanced, Restricted to TT weighting | New vehicle turret on the existing turret catalog, firing the drone-pulse family | The spotter pickup as TT's military answer |
| **Drone skimmer** (working name *Choir*) | Carrier whose drones are the weapon: no turret, a tethered flight of drone-pulse drones that escort it | Medium | Prestige | Its drones (`battle.drone`, `DroneAnchor`) | — |

The drone skimmer is the hover scout's hull stretched with a drone bay, so both
can share one sheet's registration and one body model.

## What "hover" means mechanically (proposal)

1. **Crosses water.** Water is non-walkable on the navigation grid today, so no
   hull can enter it. A hover hull reads a **hover view**: a routing copy of the
   grid with water cells open, used for its route proof, its rolling planner
   *and* its physical sweep. That is the dozer's breach-view seam
   (`DozerBlade.breachView`), except the sweep also reads it, because the hull
   really does float over water. Nobody else gains the cells.
2. **Rubble is ordinary ground.** `TerrainCostField` charges rubble as open
   ground for a hover hull instead of `COST_RUBBLE`.
3. **Handling.** It stays a `BicycleBody` with a tight turn radius and a fast
   steering slew, so route proof, Reeds-Shepp docking and the collision sweep
   keep their shared curvature contract. A true holonomic or drifting body is
   *not* proposed here: every planner assumes bounded curvature, and changing
   that is its own story.
4. **Presentation.** No tread or tire animation (`hasAnimatedTreads` false), a
   skirt drawn on the sheet, a soft detached hover shadow and a low dust wash
   while it moves.

## What it gives up (proposal)

- **Thin plate.** Less than the technical; the scout sits near the spotter
  pickup, and the skimmer between them.
- **It cannot shove.** A hover hull does not repel infantry
  (`VehicleRepulsionSystem` skips it): no bow wave, no bulldozing a man aside.
  Infantry still path as if hulls were absent, so it simply cannot clear a road
  by driving into people.
- **Drones are finite and tethered.** Drones lost stay lost for the battle. When
  the carrier is wrecked, its drones crash (`DroneCrashSystem`), which is the
  "brittle when the network fails" trade.
- **Expensive and proprietary.** Its bill leans on rare metals.

An EMP or ion vulnerability is *not* proposed: no ion/EMP damage channel exists
for any body, and inventing one for a single hull would be a hidden rule.

## Seams reused

`VehicleType` + `createBody()`, `liftClass()`, `presentationDeclared()`;
`ScoutSight` for sight; `battle.drone` (`Drone`, `DroneAnchor`, `DroneSpawner`,
`DroneCrashSystem`) for the drones, as the tender does; `TurretCatalogRegistry`
for the turret; `MechPattern.of(VehicleType)`, `MechPatternPicker` faction issue,
`MechFabricationCatalog` VEHICLES; the armor drop's `LiftBudget`.

## Fabrication and lift

Both are role hulls, so the armor-per-credit rule holds them only to costing
less than their class mech (`heavy-equipment-progression.md`). The scout's bill
sits above the spotter pickup's (25/35/55/12) with more rare metals; the skimmer
below the Sirocco. Exact numbers are set against `armorEconomyEvidence` while
building, and the parallel balance session may retune.

## Acceptance

- Unit tests, one class at a time, each under a second, no simulation in `:test`:
  the hover view opens water and nothing else; the cost field treats rubble as
  open ground for hover only; `liftClass`, tier, pattern and presentation gates;
  picker weights (TT weights the hover line, pirates/independents do not); the
  skimmer anchor's escort contract.
- A `BehaviorScene` (`hover-line`) with verdicts on mechanics: a hover scout
  ordered across a river reaches the far bank while a wheeled control is refused;
  its mast reports a position no marine sees; the skimmer launches its drones,
  they escort and fire, and they crash when the carrier is wrecked; nobody is
  repelled by a hover hull.
- `createSnapshots` at 1920x1080 / 100% (lineup and battle scale); review stills
  to Drive under `review/hover-line/`.

## Decisions (owner, 2026-10-18)

1. Both hulls ship in this story: the hover scout and the drone skimmer.
2. Hover crosses **water and rubble**; low cover (sandbags, fences) still blocks it.
3. A hover hull is **exempt from repulsion**: it cannot shove or bow-wave bodies.
4. Tri-Tachyon **stops weighting the heavy-gun tank**; it weights its hover line.
   The tank stays on offer to everyone at base weight.
