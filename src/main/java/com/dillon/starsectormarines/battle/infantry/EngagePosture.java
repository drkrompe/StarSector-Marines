package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;

/**
 * <b>Squad posture: actively engage.</b> The planner picks this when the
 * squad-wide WorldState says someone has LOS and someone is in range — i.e.
 * "we can fight from here."
 *
 * <p>Per-member execution: members who personally have LOS + range author a
 * primary-fire intent on {@code COMBAT} (rocket-secondary still gets fired
 * directly here — it's a separate weapon with its own aim window, out of
 * scope for the proving slice); {@code battle.combat.FiringSystem} picks up
 * the intent later this same tick and applies the uniform cooldown/range/LoS
 * gate, fires, resets cooldown, starts the burst, and (this posture only)
 * chains the post-fire reposition. Members who don't have LOS/range continue
 * advancing toward a firing position via the inline cohesion-override
 * fallback. Mixed-state behavior in a single tick — kept here because Stage 1
 * plans are squad-wide single-action, so the "in-range marines fire while
 * out-of-range squadmates close" rhythm has nowhere else to live. Stage 2
 * with per-member action assignment is where this fallback retires; see
 * {@code roadmap/ai/README.md}.
 *
 * <p>Always returns {@link ActionStatus#RUNNING} during normal engagement;
 * invalidates with {@link ActionStatus#FAILURE} when the target evaporates,
 * and otherwise advances on squad-level replan triggers (alert-level
 * transition, member death, periodic 2-second timer).
 */
public final class EngagePosture implements Action {

    public static final EngagePosture INSTANCE = new EngagePosture();

    // Story A: ENEMY_IN_KILL_ZONE is the ambush gate. For garrison squads it
    // flips true only after sustained LOS to a close enemy; for everyone else
    // the evaluator returns true unconditionally so the Engage precondition
    // is unchanged in practice for marines/patrols.
    private static final WorldState PRE = WorldState.EMPTY
            .with(Predicate.HAS_LOS_TO_TARGET, true)
            .with(Predicate.IN_RANGE_OF_TARGET, true)
            .with(Predicate.ENEMY_IN_KILL_ZONE, true);
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.ENEMY_DAMAGED, true);

    private EngagePosture() {}

    @Override public String name() { return "Engage"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        // Cooldown ticks + mid-aim short-circuit are handled by
        // GoapInfantryBehavior.prepareForAction before this method runs —
        // see InfantryUnitPrep. By the time we get here, the unit is ready
        // to act and not locked in any animation.

        // Refresh target if dead or missing, OR if the pursuit gate says the
        // current target is no longer worth chasing (LOS lost into a cluster,
        // or target drifted out of the squad-cohesion clamp). Story I: dropping
        // a fleer that ran into 3 buddies and picking an isolated target or
        // no-target rather than charging in.
        Entity target = sim.targetOf(member);
        if (target == null
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member.entityId, Entity.idOf(target));
        }
        if (target == null) return ActionStatus.FAILURE;

        float dist = TacticalScoring.cellDistance(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        // Rocketeers vs turrets use the rocket's longer range as the act-here
        // gate, so a marine inside rocket range but outside rifle range
        // engages from where they stand instead of running into rifle range.
        // The inner rocket check (line below) still enforces dist <= rocket
        // range, and the primary-fire branch guards on primary range so we
        // don't fire the rifle from beyond its reach.
        float effectiveRange = sim.getTacticalScoring().effectiveAttackRange(member, target,
                sim.world().attackRange(member.entityId));
        boolean inRange = dist <= effectiveRange;
        boolean visible = TacticalScoring.canSeePair(sim.getGrid(),
                sim.world().cellX(member.entityId), sim.world().cellY(member.entityId), sim.world().cellX(target.entityId), sim.world().cellY(target.entityId),
                sim.vision().airLosRadius(member.entityId), sim.vision().airLosRadius(target.entityId));

        if (inRange && visible) {
            boolean startedSecondary = false;
            // Rocket eligibility broadened from MapTurret-only to any hardened
            // target (turrets, drone hubs, heavy mechs) — anything the rocket's
            // vsTurretMult bonus is worth burning a tube on.
            long mid = member.entityId;
            if (sim.world().hasSecondaryWeapon(mid) && sim.world().secondaryAmmo(mid) > 0
                    && sim.world().secondaryCooldownTimer(mid) <= 0f
                    && TacticalScoring.isHardened(target)
                    && dist <= sim.world().secondaryWeapon(mid).range
                    && sim.getTacticalScoring().shouldCommitRocket(member, target)) {
                sim.world().setSecondaryActionTimer(mid, sim.world().secondaryWeapon(mid).aimDuration);
                sim.world().setSecondaryFired(mid, false);
                sim.world().setSecondaryAimTargetId(mid, Entity.idOf(target));
                startedSecondary = true;
            }
            // The pre-gate mirrors the old inline `dist <= attackRange` check
            // at behavior time — effectiveRange above is the rocket-extended
            // gate for "act from here" (so a rocketeer in rocket-but-not-
            // rifle range doesn't run into rifle range), but authoring a
            // primary-fire intent still requires the *rifle's* own range.
            // FiringSystem re-checks range post-movement too; this pre-gate
            // is strictly suppressive (it can only hold an intent that would
            // have failed the system's check anyway), so skipping it here
            // never lets a shot through the system wouldn't already allow.
            if (!startedSecondary && dist <= sim.world().attackRange(member.entityId)) {
                // Author intent instead of firing inline — FiringSystem
                // applies the uniform cooldown/range/LoS gate (the old inline
                // `cooldownTimer<=0 && dist<=attackRange` check) and executes
                // the shot later this same tick. reposition=true: EngagePosture
                // is the only fire site that chains Story G's cooldown-gated
                // cover-aware reposition (a unit in heavy cover whose current
                // cell already wins cover-preferred no-ops out; exposed
                // members move when their cooldown expires — see
                // RepositionToCover).
                sim.combat().setFireIntent(member.entityId, Entity.idOf(target), FireStance.STANCED, true);
            }
            int[] memberPath = sim.world().path(member.entityId);
            if (sim.world().pathIdx(member.entityId) < Paths.cellCount(memberPath)) {
                sim.advanceMovement(member);
            } else {
                sim.world().setMoveProgress(member.entityId, 0f);
                sim.world().setRenderPos(member.entityId, sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
            }
        } else {
            // Stage 1 fallback for members who personally lack LOS or range
            // while the squad is in Engage posture. Cohesion override first,
            // otherwise path to a firing position. Stage 2 retires this when
            // per-member action assignment lets us put approach-only members
            // on {@link ApproachPosture} concurrently with engage-only members.
            if (sim.world().moveProgress(member.entityId) == 0f) {
                int[] dest = InfantryCohesion.cohesionOverride(member, sim);
                if (dest == null) dest = sim.getTacticalScoring().findFiringPosition(member, target);
                if (dest == null) {
                    // Same dead-end as ApproachPosture's else branch — target
                    // has no reachable firing position or vantage from here.
                    // Drop and let findBestTarget re-pick next tick.
                    sim.world().setTargetId(member.entityId, 0L);
                    return ActionStatus.RUNNING;
                }
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(member.entityId), sim.world().cellY(member.entityId), dest[0], dest[1], sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
        }

        return ActionStatus.RUNNING;
    }
}
