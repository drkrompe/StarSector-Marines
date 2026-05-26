package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.weapons.MechLoadoutState;
import com.dillon.starsectormarines.battle.weapons.MechRole;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.ai.MechCombatantBehavior;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * LR Support doctrine: hold at LR-band cover, lob LRMs at the squad's
 * known threat axis, withhold SRMs. Picks a cover cell ~25–35 cells from
 * {@code squad.lastSeenEnemyX/Y} with LoS to that cell — once per threat
 * axis shift, cached on {@link MechLoadoutState#overwatchCellX} so per-tick
 * re-search doesn't blow the planner budget.
 *
 * <p>Per-member execution branches on role. An LR_SUPPORT member runs the
 * overwatch body; any other member in the squad (e.g. an ARMORED_SUPPORT
 * mech in a mixed-role squad — possible with round-robin spawn assignment)
 * falls through to the parity {@link EngageAtCurrentBand} body so it still
 * fights instead of idling. Per-member goal assignment (Story F) is the
 * Stage 2 path that gives each member its own goal cleanly; for Stage 1
 * the squad-goal-wins-once model with action-side branching handles mixed
 * squads acceptably.
 *
 * <p>The "withhold SRM" piece is doctrine-as-positioning: the mech holds
 * at LR band, so SRM band targets are typically out of range and the gate
 * is a no-op. When an enemy closes inside chaingun range (the kill
 * corridor is being overrun), the chaingun fires as last-ditch defense.
 * SRM is never called from this action regardless. Future morale-driven
 * "pressured" override (see {@code roadmap/ai/14-mech-stage1.md} "Mech
 * survival") can unlock SRM as a pressure-release valve in Stage 2.
 *
 * <p>Always returns {@link ActionStatus#RUNNING} — same lifecycle as
 * {@link EngageAtCurrentBand}; replan handles posture changes.
 */
public final class OverwatchKillZone implements Action {

    public static final OverwatchKillZone INSTANCE = new OverwatchKillZone();

    /** Lower bound on overwatch-cell distance from {@code lastSeenEnemy}. Above the mech's chaingun range (22 cells) so a chaingun-band target can't sustain on the mech's overwatch position. */
    private static final float OVERWATCH_MIN_DIST = 25f;
    /** Upper bound. Below the mech's LRM range (40 cells) so the picked cell is comfortably inside the firing envelope for arc'd LRMs. */
    private static final float OVERWATCH_MAX_DIST = 36f;
    /** Cover-bonus weight when scoring candidate overwatch cells. Higher = strong preference for high-cover cells over short-walk cells. */
    private static final float OVERWATCH_COVER_WEIGHT = 5f;

    private static final WorldState PRE = WorldState.EMPTY;
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.KILL_ZONE_COVERED, true);

    private OverwatchKillZone() {}

    @Override public String name() { return "OverwatchKillZone"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        // Per-member role branching. Non-LR_SUPPORT members fall through to
        // parity engagement so mixed-role squads have every member doing
        // something sensible.
        if (member.mech == null || member.mech.role != MechRole.LR_SUPPORT) {
            return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
        }

        // No known contact → no kill corridor anchor. Drop back to parity
        // until the alert-update pass sets lastSeenEnemy.
        if (squad.lastSeenEnemyX < 0 || squad.lastSeenEnemyY < 0) {
            return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
        }

        // Refresh overwatch cell when threat axis shifts or we have no cached
        // pick yet. Pick is per-mech (each LR member gets its own cell).
        MechLoadoutState m = member.mech;
        if (m.overwatchCellX < 0 ||
            m.overwatchAxisX != squad.lastSeenEnemyX ||
            m.overwatchAxisY != squad.lastSeenEnemyY) {
            int[] cell = pickOverwatchCell(member, squad, sim);
            if (cell == null) {
                // No valid LR-band cover cell in range — fall back to parity.
                // Re-tries next replan when the threat axis may have shifted.
                return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
            }
            m.overwatchCellX = cell[0];
            m.overwatchCellY = cell[1];
            m.overwatchAxisX = squad.lastSeenEnemyX;
            m.overwatchAxisY = squad.lastSeenEnemyY;
        }

        // Path to the overwatch cell. Idempotent — only requests a new path
        // when the mech isn't already at the cell and isn't already moving.
        if ((member.getCellX() != m.overwatchCellX || member.getCellY() != m.overwatchCellY)
                && member.moveProgress == 0f
                && member.pathIdx >= member.pathCellCount()) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.getCellX(), member.getCellY(),
                    m.overwatchCellX, m.overwatchCellY,
                    sim.getOccupancyMap()));
        }
        if (member.pathIdx < member.pathCellCount()) {
            sim.advanceMovement(member);
        } else {
            member.moveProgress = 0f;
            member.renderX = member.getCellX();
            member.renderY = member.getCellY();
        }

        // Fire pass — withhold SRM (overwatch doctrine), allow LRM (preferred)
        // and chaingun (last-ditch if a target closes to chaingun band).
        // Re-pick whenever the cached target isn't currently shootable: an
        // LR mech parked at its overwatch cell can otherwise stay locked onto
        // an enemy that's slid behind cover while ignoring a fresh enemy now
        // standing in its kill lane.
        Unit target = sim.getTacticalScoring().refreshTargetIfNotShootable(member);
        member.setTarget(target);
        if (target != null) {
            float dist = TacticalScoring.cellDistance(member.getCellX(), member.getCellY(),
                    target.getCellX(), target.getCellY());
            boolean inRange = dist <= member.attackRange;
            boolean visible = sim.getGrid().hasLineOfSight(member.getCellX(), member.getCellY(),
                    target.getCellX(), target.getCellY());
            if (inRange) {
                MechCombatantBehavior.tryFireLrm(member, target, dist, sim, visible);
                MechCombatantBehavior.tryFireChaingun(member, target, dist, sim, visible);
                // SRM intentionally withheld — see class doc.
            }
        }
        return ActionStatus.RUNNING;
    }

    /**
     * Picks the best cover cell in the LR band of {@code squad.lastSeenEnemy}.
     * Scans the {@code [-OVERWATCH_MAX_DIST, OVERWATCH_MAX_DIST]} box around
     * the threat, filters to walkable cells in
     * {@code [OVERWATCH_MIN_DIST, OVERWATCH_MAX_DIST]} with LoS to the threat,
     * scores by cover quality (per-facing, against the threat axis) minus walk
     * distance from the mech's current cell. Returns {@code null} when no cell
     * satisfies the filter — caller falls back to parity engagement.
     */
    private static int[] pickOverwatchCell(Unit member, Squad squad, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        int tx = squad.lastSeenEnemyX;
        int ty = squad.lastSeenEnemyY;
        int radius = (int) Math.ceil(OVERWATCH_MAX_DIST);

        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget < OVERWATCH_MIN_DIST || distFromTarget > OVERWATCH_MAX_DIST) continue;
                if (!grid.hasLineOfSight(cx, cy, tx, ty)) continue;
                // Cover lookup is directional against the threat axis (Story G
                // primitive). High-cover cells facing the threat win.
                int fdx = tx - cx;
                int fdy = ty - cy;
                int cover = grid.getCoverAt(cx, cy, fdx, fdy);
                int doodadCover = sim.getDoodadCoverAt(cx, cy, fdx, fdy);
                float walk = TacticalScoring.cellDistance(member.getCellX(), member.getCellY(), cx, cy);
                float score = walk
                        - OVERWATCH_COVER_WEIGHT * cover
                        - OVERWATCH_COVER_WEIGHT * doodadCover;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best;
    }
}
