package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.weapons.FireStance;

/**
 * <b>Squad posture: move into a target zone.</b> Each member paths to a
 * representative cell inside {@link #targetZoneId} and walks. The plan
 * advances to {@link ClearZone} as soon as the <em>first</em> member's
 * logical cell crosses into the target zone — matches Stage 1's
 * first-arrival semantics on {@link ApproachPosture}. Stragglers catch up
 * inside the next step (ClearZone keeps moving members not yet in zone).
 *
 * <p>Parameterized per-zone — Story K's customPlan creates one instance per
 * zone in the BFS path. Not a singleton (unlike Stage 1's postures), and not
 * registered in {@code GoapInfantryBehavior.INFANTRY_ACTIONS}: the
 * backward-chaining planner never sees these; they're emitted only by
 * {@link com.dillon.starsectormarines.battle.ai.goap.goals.SecureObjectiveZone}'s
 * custom plan.
 *
 * <p>Empty preconditions/effects: this action doesn't participate in the
 * predicate-chain search, so there's nothing to declare. Cost is also
 * irrelevant for the custom-plan path.
 */
public final class EnterZone implements Action {

    private final int targetZoneId;
    /** Destination cell inside the target zone — chosen at construction so all members aim at the same spot and the pathfinder routes them through the portal naturally. */
    private final int destX;
    private final int destY;

    public EnterZone(int targetZoneId, int destX, int destY) {
        this.targetZoneId = targetZoneId;
        this.destX = destX;
        this.destY = destY;
    }

    /**
     * Picks a representative interior cell for {@code zone} — the middle entry
     * in its flat cell-index array. Good-enough centroid stand-in without
     * needing to compute coordinates for every cell: cellIndices is in
     * detection order (roughly scan-line), so the middle is usually deep in
     * the zone rather than at a portal edge.
     */
    public static EnterZone forZone(NavigationZone zone, NavigationGrid grid) {
        int[] cells = zone.getCellIndices();
        int pick = cells.length > 0 ? cells[cells.length / 2] : 0;
        int x = pick % grid.getWidth();
        int y = pick / grid.getWidth();
        return new EnterZone(zone.getZoneId(), x, y);
    }

    public int targetZoneId() { return targetZoneId; }
    public int destX() { return destX; }
    public int destY() { return destY; }

    @Override public String name() { return "EnterZone[" + targetZoneId + "]"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        if (sim.getZoneGraph().zoneIdAt(member.cellX, member.cellY) == targetZoneId) {
            return ActionStatus.SUCCESS;
        }
        // Opportunistic fire while walking: any visible enemy in range gets a
        // shot if the cooldown's ready. Doesn't break path or stop movement —
        // a member shoots on the move. Without this, marines marching through
        // a contested zone toward the target zone wouldn't return fire, which
        // reads as cowardice on screen.
        if (member.target == null || !member.target.isAlive()) {
            member.target = TacticalScoring.findBestTarget(member, sim);
        }
        if (member.target != null && member.cooldownTimer <= 0f) {
            float d = TacticalScoring.cellDistance(member.cellX, member.cellY,
                    member.target.cellX, member.target.cellY);
            boolean visible = sim.getGrid().hasLineOfSight(member.cellX, member.cellY,
                    member.target.cellX, member.target.cellY);
            if (d <= member.attackRange && visible) {
                // EnterZone is the move-toward-zone branch — every shot here
                // happens mid-step, so MOVING applies as a baseline. Burst
                // weapons still rip the full burst — InfantryWeapons.tick
                // re-reads FireStance per round so the marine keeps the
                // MOVING penalty across the whole burst if still walking.
                sim.fireShot(member, member.target, FireStance.MOVING);
                member.cooldownTimer = member.attackCooldown;
                member.beginBurst(member.target);
            }
        }
        if (member.moveProgress == 0f) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.cellX, member.cellY, destX, destY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }
}
