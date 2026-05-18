package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.MapTurret;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryCombatantBehavior;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * <b>Squad posture: actively engage.</b> The planner picks this when the
 * squad-wide WorldState says someone has LOS and someone is in range — i.e.
 * "we can fight from here."
 *
 * <p>Per-member execution: members who personally have LOS + range open fire
 * (primary, with rocket-secondary priority against turrets; bursts + reposition
 * rolls intact); members who don't continue advancing toward a firing position
 * via the inline cohesion-override fallback. Mixed-state behavior in a single
 * tick — kept here because Stage 1 plans are squad-wide single-action, so the
 * "in-range marines fire while out-of-range squadmates close" rhythm has
 * nowhere else to live. Stage 2 with per-member action assignment is where
 * this fallback retires; see {@code roadmap/ai/README.md}.
 *
 * <p>Always returns {@link ActionStatus#RUNNING} during normal engagement;
 * invalidates with {@link ActionStatus#FAILURE} when the target evaporates,
 * and otherwise advances on squad-level replan triggers (alert-level
 * transition, member death, periodic 2-second timer).
 */
public final class EngagePosture implements Action {

    public static final EngagePosture INSTANCE = new EngagePosture();

    private static final WorldState PRE = WorldState.EMPTY
            .with(Predicate.HAS_LOS_TO_TARGET, true)
            .with(Predicate.IN_RANGE_OF_TARGET, true);
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.ENEMY_DAMAGED, true);

    /** Same per-shot reposition probability as {@link InfantryCombatantBehavior}. */
    private static final float REPOSITION_CHANCE = 0.30f;

    private EngagePosture() {}

    @Override public String name() { return "Engage"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        // Cooldown ticks + mid-aim short-circuit are handled by
        // GoapInfantryBehavior.prepareForAction before this method runs —
        // see InfantryUnitPrep. By the time we get here, the unit is ready
        // to act and not locked in any animation.

        // Refresh target if dead or missing. Lost-target → plan invalidates;
        // replan will pick a null plan if no enemies remain.
        if (member.target == null || !member.target.isAlive()) {
            member.target = TacticalScoring.findBestTarget(member, sim);
        }
        if (member.target == null) return ActionStatus.FAILURE;

        float dist = TacticalScoring.cellDistance(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY);
        boolean inRange = dist <= member.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY);

        if (inRange && visible) {
            boolean startedSecondary = false;
            if (member.secondaryWeapon != null && member.secondaryAmmo > 0
                    && member.secondaryCooldownTimer <= 0f
                    && member.target instanceof MapTurret
                    && dist <= member.secondaryWeapon.range) {
                member.secondaryActionTimer = member.secondaryWeapon.aimDuration;
                member.secondaryFiredThisAction = false;
                member.secondaryAimTarget = member.target;
                startedSecondary = true;
            }
            if (!startedSecondary && member.cooldownTimer <= 0f) {
                sim.fireShot(member, member.target);
                member.cooldownTimer = member.attackCooldown;
                if (member.primaryWeapon != null && member.primaryWeapon.burstCount > 1) {
                    member.burstRemaining = member.primaryWeapon.burstCount - 1;
                    member.burstTimer = member.primaryWeapon.burstSpacing;
                    member.burstTarget = member.target;
                }
                if (sim.getRng().nextFloat() < REPOSITION_CHANCE) {
                    int[] firingPos = TacticalScoring.findFiringPosition(member, member.target, sim,
                            member.cellX, member.cellY);
                    if (firingPos[0] != member.cellX || firingPos[1] != member.cellY) {
                        sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                                member.cellX, member.cellY, firingPos[0], firingPos[1],
                                sim.getOccupancyMap()));
                    }
                }
            }
            if (member.pathIdx < member.pathCellCount()) {
                sim.advanceMovement(member);
            } else {
                member.moveProgress = 0f;
                member.renderX = member.cellX;
                member.renderY = member.cellY;
            }
        } else {
            // Stage 1 fallback for members who personally lack LOS or range
            // while the squad is in Engage posture. Cohesion override first,
            // otherwise path to a firing position. Stage 2 retires this when
            // per-member action assignment lets us put approach-only members
            // on {@link ApproachPosture} concurrently with engage-only members.
            if (member.moveProgress == 0f) {
                int[] dest = InfantryCombatantBehavior.cohesionOverride(member, sim);
                if (dest == null) dest = TacticalScoring.findFiringPosition(member, member.target, sim);
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        member.cellX, member.cellY, dest[0], dest[1], sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
        }

        return ActionStatus.RUNNING;
    }
}
