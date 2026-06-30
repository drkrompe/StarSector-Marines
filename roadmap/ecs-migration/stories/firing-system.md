# FiringSystem — centralize the duplicated firing mechanics (future epic)

> **Status: captured, NOT started (2026-06-29).** Surfaced during the per-component
> Service decomposition: the behavior tier duplicates the firing mechanics across
> ~12 postures. Sequenced **after** the [`entity-field-migration`](entity-field-migration.md)
> slices (user decision 2026-06-29) — the field migration consolidates the COMBAT
> state this System reads, so do it first. This is the concrete, motivating case of
> the [`systems-to-columns`](systems-to-columns.md) epic's "convert the combatant hot
> loop to Systems."

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

## The open decision — the intent contract (decide via the audit, NOT up front)

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

## First steps when picked up

1. **Audit** all ~12 fire sites: is firing uniform once target+cooldown+range+LoS? Tabulate any per-site deviation (stance, hold-fire, extra gate). This decides (a) vs (b).
2. Stand up `FiringSystem`; move the fire block out of **one** posture (`EngagePosture`) as the proving slice; assert identical behavior (suite + a targeted fire-cadence test).
3. Sweep the remaining postures; delete the scattered cooldown decrements; one decrement in the System.

## Cross-refs

- [`systems-to-columns.md`](systems-to-columns.md) — FiringSystem is its headline case (combatant hot loop → System).
- [`entity-field-migration.md`](entity-field-migration.md) — upstream; consolidates the COMBAT state FiringSystem reads. Do first.
- Memory: [[battle_services_systems]] (Systems = stateless per-tick consumers),
  [[feedback_compose_effects_not_carrier]], [[feedback_scored_over_binary_gates]]
  (the fire/hold decision should stay a scored behavior output, not a sticky flag —
  informs the contract).
