# FiringSystem — centralize the duplicated firing mechanics

> **Status: EXTRACTION COMPLETE (2026-07-01) — proving slice `c07a11ef`+`426f21db`,
> sweep `b418d835`. Every infantry-family fire site authors intent;
> `battle.combat.FiringSystem` is the sole executor of the fire mechanics.
> Remaining: playtest verification (garrison/patrol cadence returns to the intended
> `attackCooldown` spacing — the double-tick bug had those sites firing ~2× fast)
> and the optional Phase 3 stance normalization (deliberate behavior change,
> separate slice).** Surfaced during the per-component Service decomposition:
> the behavior tier duplicates the firing mechanics across ~12 postures. Sequenced
> after [`entity-field-migration`](entity-field-migration.md) (user decision
> 2026-06-29), which consolidated the COMBAT state this System reads. The concrete,
> motivating case of the [`systems-to-columns`](systems-to-columns.md) epic.

## Audit findings (2026-07-01 — three parallel extraction agents, full tabulations in session)

**14 trigger sites**, all sharing the fire→reset→burst tail verbatim; the gates differ:

| Site | Extra trigger gate beyond cooldown/range/LoS | Hold-fire w/ live target? | Stance | Local decrement |
|---|---|---|---|---|
| EngagePosture | rocket-branch suppression (`startedSecondary`) | no | 2-arg STANCED | — |
| HoldZone / ClearZone | — | no | 2-arg STANCED | — |
| AbstractZoneAction (main) | — | no | `haltOnContact ? STANCED : MOVING` | — |
| AbstractZoneAction (opportune) | fires an **ephemeral non-`targetId`** enemy | no | MOVING | — |
| BreakContact | — | no | MOVING transit / STANCED in-position | — |
| GarrisonCordon / HoldPortalCordon | — | no | MOVING transit / STANCED at-post | — |
| ChokePointHold | squad `ENEMY_IN_PORTAL_CELL` trigger (selection-fusable: intruder stands ON the gated cell) | **yes (by design)** | STANCED | — |
| GuardPostPatrol | **`withinLeash`** — holds fire while KEEPING the target | **yes** | 2-arg STANCED | **yes — double-tick** |
| HoldPost | — | no | 2-arg STANCED | **yes — double-tick (backlog bug)** |
| PatrolMotion.fireIfAble | — | no | MOVING **always** (even dwelling) | **yes — double-tick** |
| KitRetrieverBehavior | — | no | MOVING always | yes — **sole** decrement (bypasses GOAP prep) |

Decrement architecture: `InfantryUnitPrep.tickCooldowns` is the canonical GOAP-path
decrement — once per unit per tick, **skipped during the rocket-aim window**
(`tickAimAndShortCircuit` short-circuits first: cooldowns freeze mid-aim), and it also
decrements secondary + reposition cooldowns. Turrets+drones decrement once in
`TurretAim.tick` (shared state-machine; fire-arc slew gate; lock-drop ≠ hold-fire);
mechs decrement per-track on `MechLoadoutComponent` in `HeavyWeapons.advanceMechWeapons`.
**Three double-tick bugs** (HoldPost, GuardPostPatrol, PatrolMotion), not one.
`ClearZone` has a second dispatch path (`GarrisonPatrol` invokes it inline). Stances are
caller-supplied constants everywhere — never `FireStance.stanceFor(moveProgress)` — and
inconsistent with actual motion at several sites (preserve verbatim; normalizing is a
deliberate later behavior change). `fireShot` itself (InfantryWeapons) has **zero**
cooldown/burst side effects — callers own all bookkeeping (that's the duplication).

## The decision (2026-07-01) — explicit fire-intent on COMBAT; system executes, prep decrements

The audit **falsifies contract (a)** (`targetId` IS permission): GuardPostPatrol's leash
holds fire while *keeping* the target (`targetId` also feeds pursuit + `FacingSystem`
aim-facing — can't be cleared to express hold), and AbstractZoneAction's opportune path
fires a target that is deliberately NOT `targetId`. Contract is **(b), lean form**:

- **COMBAT gains a consume-once fire-intent**: `FIRE_TARGET_ID` (LONG, `0` = no intent =
  hold fire), `FIRE_STANCE` (INT ordinal), `FIRE_REPOSITION` (INT 0/1 — EngagePosture's
  post-fire `RepositionToCover`, flag-gated so it stays same-tick). Columns on COMBAT,
  not a presence component — per-tick add/remove would thrash archetypes.
- **Behaviors keep target selection + their own pre-gates** (leash, portal trigger,
  opportune pick, rocket-branch suppression — all selection-side) and **write intent**
  instead of firing. They stop reading/writing `cooldownTimer` entirely.
- **`FiringSystem` (serial, after `unitUpdate`, before `infantry.tick()`'s burst
  continuation)** consumes intent: resolve target (tolerant of death-in-flight), apply
  the uniform gate `cooldown ≤ 0 && dist ≤ attackRange && hasLineOfSight`, then
  `fireShot(shooter, target, stance)` + cooldown reset + `beginBurst` + optional
  reposition. Consume-once: intent cleared every tick whether or not it fired (stale
  intent must never re-fire).
- **The decrement STAYS in `InfantryUnitPrep.tickCooldowns`** — revising the original
  sketch ("the ONE decrement" in the system). The audit shows the canonical decrement is
  coupled to the aim-freeze + secondary/reposition cooldowns (prep concerns), and every
  other population (turret/drone/mech) already has exactly one owner. Fix the three
  double-ticks by **deleting the local extras**; KitRetriever (GOAP-bypassing) routes to
  `tickCooldowns` in the sweep phase (note: also starts ticking its secondary/reposition
  cooldowns — tiny behavior change, decide at sweep).
- **Scope: the infantry family only** (12 posture sites + KitRetriever). Turrets
  (`TurretAim` fire-arc model), drones (same), mechs (`HeavyWeapons` per-track) are OUT —
  no duplication exists there.
- **Known equivalence caveat (critique-driven fix, this commit):** execution moves from
  *during* the parallel behavior dispatch to a serial phase just after it (same tick,
  before burst continuation), but combat effects (damage / reprio / fallback) triggered
  from FIRING now DEFER to the same `APPLY_DAMAGE` barrier the old parallel path used —
  `DamageService.enterCombatEffectDeferral`/`exit` brackets the `FiringSystem.tick` call.
  This restores the three semantics the initial proving-slice flip had silently repealed
  by resolving those effects inline instead: doomed-unit final action (a target hit this
  tick stays roster-alive through its own later burst continuation), both-shooters-
  overkill (two shooters converging on one fragile target both land their shot instead of
  the second holding), and queued-guarded reprio (a reprioritize roll during FIRING gets
  the same expectedTargetId race-check a parallel-phase roll got). Occupancy is
  deliberately excluded from the deferral (it stays gated on the parallel-dispatch flag
  only) since its drain already ran for the tick by the time FIRING executes — deferring
  it would leak a delta into next tick. Residual deltas, now narrow: (i) the post-advance
  gate re-check is conservative-only (can suppress, never wrongly authorize, a fire —
  `EngagePosture` restores its own pre-gate at the rifle's range so this can't fire early
  either); (ii) a chained `RepositionToCover` installs its path at FIRING (first step next
  tick) instead of mid-dispatch, and being serial, same-tick repositions now see each
  other's cover claims (the old parallel dispatch could double-book a cell); (iii) the
  narrow marine-vs-drone `airLos` hold noted in the Phase-1 entry below. Within-tick shot
  ordering across units still shifts; cadence is unchanged. Covered by cadence tests
  (tightened to exact spacing) + two new deferral-specific tests + playtest.

## Phases

1. ~~**Proving slice**~~ — **SHIPPED `c07a11ef` (2026-07-01; Sonnet-implemented,
   main-thread reviewed; full suite green).** Intent columns (COMBAT fields 10–12) +
   `CombatService.setFireIntent`/reads/clear + `battle.combat.FiringSystem` (serial
   FIRING phase after unit dispatch + spawn flush, before `infantry.tick()`'s burst
   continuation) + `EngagePosture` flipped + 10 tests incl. the 120-tick cadence golden
   (30-tick reset spacing == attackCooldown). LoS verification: `hasLineOfSight ⟹
   canSeePair` proven, so the system's stricter re-check can never authorize a new fire;
   **one accepted narrow delta** — a marine engaging a DRONE across a close wall now
   holds fire where `canSeePair`'s airLos leniency previously fired (conservative
   direction; ground-vs-ground is byte-identical). Original slice plan: intent columns +
   accessors + system + flip `EngagePosture` only + cadence/gate/consume-once tests.
   **Critique-fix follow-up (`426f21db`, same day):** a review pass on
   `c07a11ef` found the inline-during-FIRING damage resolution had silently repealed
   three semantics the old parallel-dispatch path preserved (doomed-unit final action,
   both-shooters-overkill, queued-guarded reprio) and let a rocketeer fire one tick early
   at the outer rocket-extended range gate instead of the rifle's own range. Fixed via
   `DamageService.enterCombatEffectDeferral`/`exit` (a second, narrower deferral flag
   alongside `insideParallel`, occupancy deliberately excluded) bracketing
   `FiringSystem.tick`, plus an `EngagePosture` pre-gate restoring the old inline
   `dist <= attackRange` check at behavior time. Also: deleted three zero-caller
   `CombatService` accessors (`fireStance`/`fireRepositionAfter`/`clearFireIntent` —
   `FiringSystem` reads the columns directly), refreshed stale docs (`RepositionToCover`,
   `HitResponseSystem`, `package-info`), and tightened/added `FiringSystemTest` coverage
   (exact cadence spacing, reposition-not-chained-on-a-blocked-fire, overkill/focus-fire).
   See § The decision's "Known equivalence caveat" above for the corrected semantics.
2. ~~**The sweep**~~ — **SHIPPED `b418d835` (2026-07-01; Sonnet-implemented,
   main-thread reviewed; full suite green, 862 tests).** All 11 remaining sites +
   `KitRetrieverBehavior` author intent; the three double-tick decrements are DELETED.
   **Cadence direction, disambiguated** (the original phrasing above was ambiguous):
   prep (`InfantryUnitPrep.tickCooldowns`) already ticked once per tick before every
   action executes, so the local decrements had HoldPost/GuardPostPatrol/PatrolMotion
   sites draining cooldown at 2× — garrisons, guard posts, and patrols were firing
   roughly **twice the intended rate**. Deleting the locals *slows* them to the
   designed `attackCooldown` spacing; verify the feel in playtest. Sweep findings
   beyond the plan:
   - **HoldZone/ClearZone movement coupling** — their old fire branch `return`ed on
     the fire tick and fell through to creep-toward-a-firing-position movement during
     cooldown. Both keep a read-only `cooldownTimer` consult purely as the movement
     gate (commented as such in the code), preserving the creep-between-shots control
     flow exactly. Every other site's control flow never depended on the cooldown.
   - **KitRetriever routing** — the `tickCooldowns` call sits AFTER the demote
     early-return (the demote path delegates to `CombatantBehavior` → GOAP prep,
     which ticks; placing it before the check would double-tick on the demote tick).
     Accepted delta: secondary + reposition cooldowns now tick during retrieval (the
     old inline decrement was primary-only).
   - **ChokePointHold micro-delta** — `targetId` + intent now stamp on every
     trigger-active tick (was only on cooldown-ready ticks); the portal LoS/range
     pre-gates stay behavior-side. Direction: steadier aim-facing; same fire cadence.
   - **Range/LoS pre-gates kept verbatim at every site** — they prevent the system's
     post-move re-check from *authorizing* a fire the old inline gate would have
     blocked (a target that closed distance between dispatch and FIRING); the system
     re-check stays conservative-only. The zone-action opportune path
     (`closestEnemyInAttackRange` selects via `canSeePair`) inherits the accepted
     marine-vs-drone-across-a-close-wall suppress; its scan also now runs on every
     not-in-contact tick instead of only cooldown-ready ticks (perf-only).
   Tests: leash hold-fire golden (the contract-(b) motivating case — outside-leash
   member authors NO intent while keeping `targetId`), `fireIfAble` no-decrement pin,
   HoldZone creep-during-cooldown pin, new `KitRetrieverBehaviorTest` (three-cooldown
   routing + demote placement guard); 3 inline-fire assertions in
   `ChokePointHoldTest`/`GarrisonCordonTest` migrated to drive `FiringSystem`
   explicitly.
3. **(Later, deliberate behavior change)** stance normalization via
   `FireStance.stanceFor(moveProgress)` — kills the STANCED-while-lerping and
   MOVING-while-dwelling inconsistencies. Separate slice with its own playtest.

## The gap

Every GOAP posture/action that can shoot re-implements the same fire mechanics inline.
The canonical block (`EngagePosture` ~108–112):

```java
if (cooldownTimer(id) <= 0 && dist <= attackRange(id)) {
    sim.fireShot(member, target);
    sim.combat().setCooldownTimer(id, sim.combat().attackCooldown(id));   // reset
    member.beginBurst(sim.world(), target);                               // burst
    RepositionToCover.tryReposition(member, sim);                         // post-fire
}
```

Re-implemented inline in **~12 sites**: `EngagePosture`, `HoldZone`, `ClearZone`,
`BreakContact`, `GarrisonCordon`, `HoldPost`, `ChokePointHold`, `HoldPortalCordon`,
`PatrolMotion`, `GuardPostPatrol`, `KitRetrieverBehavior`, plus the turret/drone aim
variants. The cooldown **decrement** is separately scattered across
`InfantryUnitPrep.tickCooldowns` + `HoldPost` + `PatrolMotion` + `GuardPostPatrol` +
`KitRetrieverBehavior`. ~16 spots own firing mechanics that can (and will) drift.

## The seam already exists: `COMBAT.targetId`

The decision→execution boundary is already in the data: behaviors write
`COMBAT.targetId` ("who I'm engaging"); the inline fire blocks read it. The behaviors
are doing **two** jobs mashed together:

- **Decision (genuinely per-posture):** *pick* the target, decide stance, handle
  movement/positioning, decide *whether* to hold a target (the garrison
  `ENEMY_IN_KILL_ZONE` gate etc. live in target *selection*, not the trigger).
- **Execution (uniform, wrongly duplicated):** target present + cooldown up + in range
  + LoS ⇒ fire, reset cooldown, burst.

## The design: a FiringSystem owns execution

A per-tick **System** (column-walk over COMBAT entities) that reads behavior-derived
intent and applies the uniform mechanics:

```
for each combatant (COMBAT column-walk):
    decrement cooldownTimer                       // the ONE decrement (kills the scatter)
    if targetId live && cooldown<=0 && inRange && LoS && weaponsFree:
        fireShot(id, targetId); resetCooldown(id); beginBurst(id, targetId)
```

Behaviors shrink to *"maintain targetId + stance, move"* and stop touching the
trigger. `CombatService` is the substrate it reads/writes; the field migration is its
upstream (it consolidates attackDamage/range/accuracy/cooldown/burst into the COMBAT
columns this walks).

## ~~The open decision~~ — RESOLVED 2026-07-01 (see § The decision above; kept for the original framing)

User direction (2026-06-29): **let the posture audit pick the contract.** The audit
question: *does any posture today hold fire while keeping a live target, or apply a
non-uniform fire gate at the trigger (not in target selection)?*

- **(a) `targetId` IS the fire-permission** — FiringSystem fires at any live
  `targetId` when the gate opens; stance gets a small `COMBAT.stance` field. Simplest,
  likely behavior-preserving **iff** the audit finds all per-posture gates are in
  target *selection*. Near-drop-in.
- **(b) explicit `FireIntent`** (targetId + stance + holdFire) the behavior writes —
  more plumbing, self-documenting, allows hold-fire-with-target. Pick this only if the
  audit finds a posture that needs it.

Two pieces stay behavior-coupled (don't fold them in blindly): **post-fire reposition**
(`RepositionToCover`, cover-aware) and the **secondary/rocket aim** (separate weapon,
its own aim window) — each either stays in the behavior reacting to "did I just fire"
or becomes its own small system.

## ~~First steps when picked up~~ — step 1 (audit) DONE 2026-07-01; superseded by § Phases above

## Cross-refs

- [`systems-to-columns.md`](systems-to-columns.md) — FiringSystem is its headline case (combatant hot loop → System).
- [`entity-field-migration.md`](entity-field-migration.md) — upstream; consolidates the COMBAT state FiringSystem reads. Do first.
- Memory: [[battle_services_systems]] (Systems = stateless per-tick consumers),
  [[feedback_compose_effects_not_carrier]], [[feedback_scored_over_binary_gates]]
  (the fire/hold decision should stay a scored behavior output, not a sticky flag —
  informs the contract).
