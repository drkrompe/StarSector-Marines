# Story: VehicleController → id-keyed component + stateless system

**Status:** CODE COMPLETE — **S1–S5b shipped** (state id-keyed in `VEHICLE_CONTROL`,
behaviour in the stateless `VehicleControlSystem`; `VehicleController` is now a
non-instantiable static-math holder). **S6 (cosmetic rename) optional/deferred.**
**One user checkpoint remains:** playtest the full lifecycle (INCOMING→dock→reverse→DEPARTING) —
the automated suite only covers the static helpers, so the behavioural cutover in S5b wants a
human eyeball. Design vetted 2026-07-05 via a 4-phase workflow (5-reader map → 3 design
proposals → judge synthesis → 2 adversarial critiques). Post-epic follow-on to
identity-collapse — the last per-instance state bag in the battle tier that lived as loose
fields on a controller object.

**Shipped:** S1 `7fe601c9` (component + register(30) + control(id)) · S2 `92842b4d` (seed at
spawn, both branches) · S3 `d485c3ea` (relocate ~20 fields onto the component, ~60 sites,
compiler-verified pure flip) · S4 `63b1e1cc` (debug/render reads → component, mission→controller
coupling severed) · S5a `a081900c` (stateless `VehicleControlSystem`; instance logic moved +
param-threaded, gotchas #1/#2 honored; `VehicleController` → thin `{id,system}` shim; ~16
constants + 3 helpers promoted) · S5b `40aafd4e` (drive loop → `controlSystem.tick(id,…)`;
`mission.controller` + the shim deleted; `VehicleController` = static holder). Each:
`get_file_problems` clean + full suite green.

## Goal

`VehicleController` currently holds ~20 per-vehicle motion-state fields (corridor,
rolling trajectory, Reeds-Shepp docking, committed-reverse recovery ladder, wall-stuck
timers, `arrived`) as instance fields on an object hung off `mission.controller`. Bring it
into the same id-keyed shape every other per-unit datum already has: **state → one OBJECT
component column; logic → a stateless system keyed by entity id.** Physics behavior must be
preserved exactly.

## The crux (resolved: scope is small)

"Stateless system keyed by id" is *already mostly true*. `GroundSystem` is a stateless
by-id driver, and the vehicle's peers are already id-native OBJECT columns:

| datum | reached by id via | column |
|---|---|---|
| `GroundBody` (pose) | `convoy.body(id)` | `GROUND_KINEMATICS` |
| `VehicleType` | `convoy.vehicleType(id)` | `GROUND_IDENTITY` |
| `VehicleMission` (routes/lz/costfield) | `convoy.mission(id)` | `VEHICLE_MISSION` |
| `NavigationService` | injected singleton | — (not id-keyed) |

The **only** thing not component-shaped is the controller's own field bag (one hop below
addressing, on `mission.controller`). We id-key exactly that one bag and leave the rest
alone. Vehicles are allocated via `roster.allocateVehicle` (world-resident), **never** in
the dense `long[]` and **never** corpse-transmuted → the swap-and-pop trap and the
live-only-readable death path are both **N/A**; drop is a free `world.destroy(id)`.

## End-state shape

**Component** — `battle/vehicle/components/VehicleControlComponent.java` (new `components/`
subpackage + `package-info.java`, per the naming convention). A plain mutable POJO with the
~20 fields copied verbatim from `VehicleController` **with initializers preserved**
(`recovery = Recovery.NONE`, `recoveryBestRemaining = Float.MAX_VALUE`, `lastInbound = null`),
the nested `public enum Recovery { NONE, REVERSING }`, and the 7 read-only debug getters
(`wallStuckTime`, `trajectoryProgress`, `hasTrajectory`, `waypointIndex` — keep the
`corridor != null ? cursor() : 1` fallback — `dockingPath`, `dockingStartPose`,
`dockingTurnRadius`). **Single OBJECT column**, not a 20-scalar decomposition — the
`MECH_LOADOUT`/`GROUND_KINEMATICS`/`VEHICLE_MISSION` precedent (object-valued sub-fields,
population 1–4, no other system reads the sub-fields), *not* the Turret/Drone scalar bags.

**Registration** — `BattleComponents`: `public static final int VEHICLE_CONTROL_STATE = 0;`,
`public final ComponentType VEHICLE_CONTROL;`, `VEHICLE_CONTROL = world.register(30,
"VehicleControl", FieldKind.OBJECT);`. **Slot 30 verified next-free** (`DRONE_STATE` = 29 is
the last register; the mask is a 64-bit `long`).

**Accessor** — fold `control(id)` onto `ConvoyService` (already the by-id owner of every peer
vehicle column), mirroring `body(id)`/`mission(id)` verbatim:
```java
public VehicleControlComponent control(long id) {
    BattleComponents c = roster.components();
    EntityWorld world = roster.entityWorld();
    return world.has(id, c.VEHICLE_CONTROL)
            ? (VehicleControlComponent) world.getObject(id, c.VEHICLE_CONTROL, BattleComponents.VEHICLE_CONTROL_STATE)
            : null;
}
```
No dedicated `XxxStateService`, no `Long2ObjectMap` side-table, no `World`-facade hop (facade
deprecated for new migrated state).

**System** — `battle/vehicle/VehicleControlSystem.java`, stateless, fields = `ConvoyService
convoy` + `NavigationService navigation` only. Entry points mirror the two current calls:
`void tick(long id, float dt, boolean isInbound)` and `boolean consumeArrived(long id)`.
Stays interleaved in `GroundSystem`'s INCOMING/DEPARTING FSM (a global pass would reorder it
against the per-vehicle state machine). Top of `tick` resolves the five refs by id; every
current private method moves onto the system, rewriting `this.<field>` → `s.<field>` and
threading `body`/`type`/`mission`/`grid` as params.

## Constraints / gotchas (from the adversarial critiques — do NOT skip)

1. **`corridor.advance(body.x, body.y)` is the FIRST, UNCONDITIONAL statement of the ported
   `advance`** — above the `REVERSING` early-return and the docking dispatch. It must run
   every tick even mid-reverse/mid-dock (the cursor tracks the receding/ docking body). Do
   **not** fold it into the forward-track fallback branch, or taper-braking + stall detection
   silently diverge on the reverse→forward handoff. Compiles green, passes the static-only
   tests, only shows in playtest.
2. **Constant partition, no duplication.** `VehicleController` has ~20 `private static final`
   constants; ~17 are used only by the instance logic. When that logic moves to the system,
   **promote those to package-private on `VehicleController`** and reference them qualified
   (`VehicleController.DOCKING_SPEED`). Never copy a constant into the system. The two the
   surviving `maxReverseDistance` static + the tests reference (`REVERSE_RECOVERY_CELLS`,
   `MIN_USEFUL_REVERSE_CELLS`) stay co-located on `VehicleController`.
3. **Seed BOTH archetype ternary branches** in `ConvoyService.spawn` (armed and unarmed) —
   a one-branch edit NPEs unarmed vehicles at first `s.field` access.
4. Field-initializer "safety net" is *not* load-bearing (`initCorridor` runs on tick 1 and
   rewrites every field before first read) — it's a tidiness win, not a correctness gate.
   Storage-flavor choice rests on the real reasons (reference-typed fields, tiny population).

## Slice plan (green-per-commit; each compiles + full suite passes)

- **S1 — Foundation (additive dead code).** New `VehicleControlComponent` (+ `package-info`),
  `BattleComponents` index/field/`register(30,…)`, `ConvoyService.control(id)`. Nothing reads
  the column. *Files:* `vehicle/components/VehicleControlComponent.java`,
  `vehicle/components/package-info.java`, `component/BattleComponents.java`,
  `sim/ConvoyService.java`.
- **S2 — Seed at spawn (inert).** `ConvoyService.spawn` adds `VEHICLE_CONTROL` to **both**
  archetype branches + `setObject(new VehicleControlComponent())`. Seeded, unread. *Files:*
  `sim/ConvoyService.java`.
- **S3 — Relocate state onto the component.** `VehicleController` drops the ~20 fields, gains
  `private final VehicleControlComponent s` (ctor param), `this.field`→`s.field`,
  `Recovery`→`VehicleControlComponent.Recovery`, accessors delegate to `s`. Statics/constants
  untouched. `GroundSystem.add` passes `convoy.control(id)`. *Files:* `vehicle/VehicleController.java`,
  `vehicle/GroundSystem.java`. *Checkpoint:* playtest INCOMING→dock→reverse→DEPARTING identical.
- **S4 — Move debug/history reads to the component.** `VehicleMission.recordTick(GroundBody,
  float wallStuckTime)` (severs mission→controller); `GroundSystem` supplies
  `convoy.control(id).wallStuckTime()`; `BattleRenderer` + `VehicleStateDumper` swap to
  `convoy.control(id).X()`. After this `VehicleController`'s accessors have no external callers.
  *Files:* `vehicle/VehicleMission.java`, `vehicle/GroundSystem.java`,
  `ops/battleview/BattleRenderer.java`, `battle/ui/debug/VehicleStateDumper.java`.
- ✅ **S5a — Stand up the stateless system (param-threading + constant promotion).** `a081900c`.
  New `VehicleControlSystem` (stateless, `convoy` + `navigation`); all instance methods moved +
  param-threaded (`body`/`type`/`mission`/`s`), `s` resolved via `convoy.control(id)` at the
  top of the id-keyed public `tick`/`consumeArrived`. Gotcha #1 honored (`s.corridor.advance(...)`
  the unconditional first statement of `advance`). ~16 instance-only constants + the 3 route/tail
  static helpers (`lastOnGridIndex`/`appendTail`/`isPathFeasible`) promoted private→package on
  `VehicleController`, referenced qualified — no constant duplicated (gotcha #2).
  `VehicleController` reshaped to a thin `{id, system}` shim (its `tick`/`consumeArrived` forward
  to the system) so the drive loop stayed untouched → green. Method bodies moved verbatim; only
  signatures, constant/static-helper qualification, and javadoc links changed. Statics +
  `curvatureSpeedCap(...)` stayed put → tests untouched. *Files:* `vehicle/VehicleControlSystem.java`
  (new), `vehicle/VehicleController.java`, `vehicle/GroundSystem.java`.
- ✅ **S5b — Flip the drive loop, delete the shim.** `40aafd4e`. `GroundSystem` calls
  `controlSystem.tick(id,…)`/`consumeArrived(id)`; `add()` no longer builds a handle; the
  `mission.controller` field + `VehicleController`'s `{id,system}` shim (ctor + `tick`/`consumeArrived`)
  deleted; `VehicleController` is now a non-instantiable static-math holder (private ctor).
  Compiler-verified complete (deleting the field + methods makes any miss a compile error); full
  build + suite green. *Files:* `vehicle/GroundSystem.java`, `vehicle/VehicleController.java`,
  `vehicle/VehicleMission.java`. *Checkpoint (user):* playtest full lifecycle — statics-only suite
  can't catch a behavioural drift in the moved physics.
- **S6 — Optional cosmetic rename (deferred).** IDE `rename_refactoring` `VehicleController` →
  `VehicleControlMath`; updates the 3 test files + system refs. Off the critical path; resolves
  the `VehicleControlSystem`/`VehicleController` name overlap. Defer unless wanted.

## Tests

The three test classes (`VehicleControllerCurvatureTest`, `VehicleControllerRecoveryTest`,
`VehicleControllerTurnFeasibilityTest`) call **only static helpers** + the two
test-referenced constants. Those stay on `VehicleController` through S1–S5 → **tests are
never edited** until the optional S6 rename. No test constructs a `VehicleController` or calls
`tick()`/`consumeArrived()`, so the S3 ctor-signature change is safe.

## Ratified decisions (all had a clearly-correct, critique-validated answer)

1. Storage flavor → **single OBJECT column** (MECH_LOADOUT precedent), not 20 scalar columns.
2. Accessor home → **fold `control(id)` onto `ConvoyService`**, not a dedicated service.
3. Statics → **stay on `VehicleController`** (zero test churn); rename is optional S6.
4. `Recovery` enum → **nested `public enum` in `VehicleControlComponent`**.
5. System ownership → **`GroundSystem`-driven** (stays interleaved in the FSM); a first-class
   `BattleSimulation` tick phase symmetric with `AirSteeringSystem` is a larger reshape,
   out of scope (possible future follow-up).
6. S5 granularity → **split 5a/5b** (isolates the param-threading rewrite from the drive flip).
