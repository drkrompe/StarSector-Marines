package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryCohesion;
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
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        // Cooldown ticks + mid-aim short-circuit are handled by
        // GoapInfantryBehavior.prepareForAction before this method runs —
        // see InfantryUnitPrep. By the time we get here, the unit is ready
        // to act and not locked in any animation.

        // Refresh target if dead or missing, OR if the pursuit gate says the
        // current target is no longer worth chasing (LOS lost into a cluster,
        // or target drifted out of the squad-cohesion clamp). Story I: dropping
        // a fleer that ran into 3 buddies and picking an isolated target or
        // no-target rather than charging in.
        Unit target = sim.targetOf(member);
        if (target == null
                || !TacticalScoring.shouldKeepPursuing(member, target, sim)) {
            target = TacticalScoring.findBestTarget(member, sim);
            member.setTarget(target);
        }
        if (target == null) return ActionStatus.FAILURE;

        float dist = TacticalScoring.cellDistance(member.getCellX(), member.getCellY(),
                target.getCellX(), target.getCellY());
        // Rocketeers vs turrets use the rocket's longer range as the act-here
        // gate, so a marine inside rocket range but outside rifle range
        // engages from where they stand instead of running into rifle range.
        // The inner rocket check (line below) still enforces dist <= rocket
        // range, and the primary-fire branch guards on primary range so we
        // don't fire the rifle from beyond its reach.
        float effectiveRange = TacticalScoring.effectiveAttackRange(member, target);
        boolean inRange = dist <= effectiveRange;
        boolean visible = TacticalScoring.canSeePair(sim.getGrid(),
                member.getCellX(), member.getCellY(), target.getCellX(), target.getCellY(),
                member.airLosRadius, target.airLosRadius);

        if (inRange && visible) {
            boolean startedSecondary = false;
            // Rocket eligibility broadened from MapTurret-only to any hardened
            // target (turrets, drone hubs, heavy mechs) — anything the rocket's
            // vsTurretMult bonus is worth burning a tube on.
            if (member.secondaryWeapon != null && member.secondaryAmmo > 0
                    && member.secondaryCooldownTimer <= 0f
                    && TacticalScoring.isHardened(target)
                    && dist <= member.secondaryWeapon.range
                    && TacticalScoring.shouldCommitRocket(member, target, sim)) {
                member.secondaryActionTimer = member.secondaryWeapon.aimDuration;
                member.secondaryFiredThisAction = false;
                member.setSecondaryAimTarget(target);
                startedSecondary = true;
            }
            if (!startedSecondary && member.cooldownTimer <= 0f
                    && dist <= member.attackRange) {
                sim.fireShot(member, target);
                member.cooldownTimer = member.attackCooldown;
                member.beginBurst(target);
                // Story G — cooldown-gated cover-aware reposition replaces
                // the old 30% per-shot RNG. A unit in heavy cover whose
                // current cell already wins cover-preferred no-ops out;
                // exposed members move when their cooldown expires. The
                // cooldown is per-unit (Unit.repositionCooldown), so squad
                // members visibly shift at different times.
                RepositionToCover.tryReposition(member, sim);
            }
            if (member.pathIdx < member.pathCellCount()) {
                sim.advanceMovement(member);
            } else {
                member.moveProgress = 0f;
                member.renderX = member.getCellX();
                member.renderY = member.getCellY();
            }
        } else {
            // Stage 1 fallback for members who personally lack LOS or range
            // while the squad is in Engage posture. Cohesion override first,
            // otherwise path to a firing position. Stage 2 retires this when
            // per-member action assignment lets us put approach-only members
            // on {@link ApproachPosture} concurrently with engage-only members.
            if (member.moveProgress == 0f) {
                int[] dest = InfantryCohesion.cohesionOverride(member, sim);
                if (dest == null) dest = TacticalScoring.findFiringPosition(member, target, sim);
                if (dest == null) {
                    // Same dead-end as ApproachPosture's else branch — target
                    // has no reachable firing position or vantage from here.
                    // Drop and let findBestTarget re-pick next tick.
                    member.targetId = 0L;
                    return ActionStatus.RUNNING;
                }
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        member.getCellX(), member.getCellY(), dest[0], dest[1], sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
        }

        return ActionStatus.RUNNING;
    }
}
