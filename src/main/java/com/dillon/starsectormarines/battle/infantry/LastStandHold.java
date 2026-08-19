package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.TacticalNodeQueries;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

/**
 * Perpetual last-stand posture. An exposed survivor claims reachable cover
 * inside a tight leash around the must-hold node, then clears movement and
 * fights from there. Directional cover must face the known threat; if the
 * threat changes sides the survivor may claim a new covered cell. It never
 * pursues outside the objective leash. The infantry dispatcher's
 * opportunity-fire pass supplies legal fire without replacing the hold cell.
 */
public final class LastStandHold implements Action {

    public static final LastStandHold INSTANCE = new LastStandHold();

    /** Maximum cell distance from the must-hold anchor searched for cover. */
    public static final int COVER_LEASH_RADIUS = 5;

    private LastStandHold() {}

    @Override public String name() { return "LastStandHold"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        TacticalNode node = TacticalNodeQueries.assignedNode(squad);
        boolean hasHome = sim.home().hasHome(member);
        int fallbackX = hasHome ? sim.home().homeCellX(member)
                : node != null ? node.anchorX : sim.world().cellX(member);
        int fallbackY = hasHome ? sim.home().homeCellY(member)
                : node != null ? node.anchorY : sim.world().cellY(member);
        int anchorX = node != null ? node.anchorX : fallbackX;
        int anchorY = node != null ? node.anchorY : fallbackY;

        int[] threat = knownThreat(member, squad, sim);
        int currentX = sim.world().cellX(member);
        int currentY = sim.world().cellY(member);
        if (coverAt(currentX, currentY, threat, sim) > 0) {
            sim.clearPath(member);
            return ActionStatus.RUNNING;
        }

        // Preserve a move already aimed at valid cover. Re-scoring every tick
        // would make equal-quality posts churn as the unit crossed the room.
        int[] activePath = sim.world().path(member);
        int activeDestX = Paths.destX(activePath);
        int activeDestY = Paths.destY(activePath);
        if (insideLeash(activeDestX, activeDestY, anchorX, anchorY)
                && coverAt(activeDestX, activeDestY, threat, sim) > 0
                && sim.world().pathIdx(member) < Paths.cellCount(activePath)) {
            sim.advanceMovement(member);
            return ActionStatus.RUNNING;
        }

        CoveredCell covered = bestReachableCover(
                member, threat, anchorX, anchorY, sim);
        if (covered != null) {
            sim.setPath(member, covered.path);
            sim.advanceMovement(member);
            return ActionStatus.RUNNING;
        }

        // No cover exists inside the objective leash. Keep the old authored
        // post fallback rather than roaming arbitrarily or pursuing a target.
        if (sim.movement().atCell(member, fallbackX, fallbackY)) {
            sim.clearPath(member);
            return ActionStatus.RUNNING;
        }
        int[] path = sim.world().path(member);
        boolean staleDestination = Paths.destX(path) != fallbackX || Paths.destY(path) != fallbackY;
        if (staleDestination) sim.clearPath(member);
        if (sim.movement().mayRepath(member)
                && sim.world().pathIdx(member) >= Paths.cellCount(sim.world().path(member))) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    currentX, currentY, fallbackX, fallbackY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    private static int[] knownThreat(long member, Squad squad, BattleView sim) {
        long target = sim.resolveUnit(sim.targetOf(member));
        if (target != 0L) {
            return new int[]{sim.world().cellX(target), sim.world().cellY(target)};
        }
        if (squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0) {
            return new int[]{squad.lastSeenEnemyX, squad.lastSeenEnemyY};
        }
        return null;
    }

    private static int coverAt(int x, int y, int[] threat, BattleView sim) {
        if (!sim.getGrid().inBounds(x, y)) return 0;
        if (threat == null) {
            return sim.getGrid().getCoverAt(x, y) + sim.getDoodadCoverAt(x, y);
        }
        int dx = threat[0] - x;
        int dy = threat[1] - y;
        return sim.getGrid().getCoverAt(x, y, dx, dy)
                + sim.getDoodadCoverAt(x, y, dx, dy);
    }

    private static CoveredCell bestReachableCover(long member, int[] threat,
                                                   int anchorX, int anchorY,
                                                   BattleView sim) {
        int fromX = sim.world().cellX(member);
        int fromY = sim.world().cellY(member);
        CoveredCell best = null;
        for (int y = anchorY - COVER_LEASH_RADIUS; y <= anchorY + COVER_LEASH_RADIUS; y++) {
            for (int x = anchorX - COVER_LEASH_RADIUS; x <= anchorX + COVER_LEASH_RADIUS; x++) {
                if (!insideLeash(x, y, anchorX, anchorY)) continue;
                if (!sim.getGrid().inBounds(x, y) || !sim.getGrid().isWalkable(x, y)) continue;
                int cover = coverAt(x, y, threat, sim);
                if (cover <= 0) continue;

                int[] path = GridPathfinder.findPath(sim.getGrid(), fromX, fromY,
                        x, y, sim.getOccupancyMap());
                if (Paths.isEmpty(path) && (fromX != x || fromY != y)) continue;
                boolean hasLos = threat == null
                        || sim.getGrid().hasLineOfSight(x, y, threat[0], threat[1]);
                int moveDistSq = distanceSq(fromX, fromY, x, y);
                int anchorDistSq = distanceSq(anchorX, anchorY, x, y);
                CoveredCell candidate = new CoveredCell(
                        path, cover, hasLos, moveDistSq, anchorDistSq);
                if (best == null || candidate.betterThan(best)) best = candidate;
            }
        }
        return best;
    }

    private static boolean insideLeash(int x, int y, int anchorX, int anchorY) {
        return distanceSq(x, y, anchorX, anchorY)
                <= COVER_LEASH_RADIUS * COVER_LEASH_RADIUS;
    }

    private static int distanceSq(int ax, int ay, int bx, int by) {
        int dx = ax - bx;
        int dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static final class CoveredCell {
        final int[] path;
        final int cover;
        final boolean hasLos;
        final int moveDistSq;
        final int anchorDistSq;

        CoveredCell(int[] path, int cover, boolean hasLos,
                    int moveDistSq, int anchorDistSq) {
            this.path = path;
            this.cover = cover;
            this.hasLos = hasLos;
            this.moveDistSq = moveDistSq;
            this.anchorDistSq = anchorDistSq;
        }

        boolean betterThan(CoveredCell other) {
            if (cover != other.cover) return cover > other.cover;
            if (hasLos != other.hasLos) return hasLos;
            if (moveDistSq != other.moveDistSq) return moveDistSq < other.moveDistSq;
            return anchorDistSq < other.anchorDistSq;
        }
    }
}
