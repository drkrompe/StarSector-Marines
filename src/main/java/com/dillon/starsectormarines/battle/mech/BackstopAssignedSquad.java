package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Armored Support doctrine: pace a designated friendly infantry squad,
 * holding a cell behind their centroid (relative to the threat axis) at
 * follow distance. Fires all three weapons freely — no withhold gates,
 * the role's job is to backstop the friendlies with sustained heavy fire.
 *
 * <p>"Designated squad" is picked lazily at the first execute tick: the
 * nearest alive same-side infantry squad (any non-mech squad on the
 * mech's faction). Cached on {@link MechLoadoutState#assignedSquadId};
 * cleared back to -1 when the backed squad gets wiped, so the next tick
 * re-picks. The commander tier (future) will overwrite this with
 * explicit assignments via {@code ObjectiveAssignment}; until then this
 * heuristic is enough to give the mech a stable friendly to pace.
 *
 * <p>"Behind" direction = away from the squad's {@code lastSeenEnemy}
 * (the known threat axis). Without a known threat, "behind" is undefined
 * and the mech just holds within follow distance of the centroid — the
 * "march together" early posture before contact.
 *
 * <p>Per-member role branching matches {@link OverwatchKillZone}:
 * ARMORED_SUPPORT members run this body; everyone else falls through to
 * parity engagement for mixed-role squads.
 */
public final class BackstopAssignedSquad implements Action {

    public static final BackstopAssignedSquad INSTANCE = new BackstopAssignedSquad();

    /**
     * Cells between the backed squad's centroid and the mech's anchor cell.
     * Chosen so the mech's chaingun (22-cell range) reaches roughly two
     * cells beyond the marines' rifle envelope (~12 cells) — the mech adds
     * "outranging support fire" without crowding the squad's own LoS.
     */
    private static final float FOLLOW_DISTANCE = 6f;
    /**
     * How far the backed squad's centroid can drift from the cached
     * backstop anchor before we re-pick. Keeps the action from re-pathing
     * every tick during normal squad motion but catches a real shift.
     */
    private static final float REPICK_DRIFT_CELLS = 4f;

    private static final WorldState PRE = WorldState.EMPTY;
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.SQUAD_BACKED, true);

    private BackstopAssignedSquad() {}

    @Override public String name() { return "BackstopAssignedSquad"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        // Non-ARMORED_SUPPORT members fall through to parity (mixed squads).
        if (member.mech == null || member.mech.role != MechRole.ARMORED_SUPPORT) {
            return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
        }

        MechLoadoutState m = member.mech;

        // Pick or refresh the backed squad. Lazy pick on first call;
        // re-pick when the cached assignment is wiped or gone.
        Squad backed = m.assignedSquadId >= 0 ? sim.getSquad(m.assignedSquadId) : null;
        if (backed == null || backed.aliveMembers == 0) {
            backed = pickBackedSquad(member, squad, sim);
            m.assignedSquadId = backed != null ? backed.id : -1;
        }
        if (backed == null) {
            // No friendly infantry squad to back — drop to parity engagement.
            return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
        }

        // Pick or refresh the backstop cell behind the backed squad's
        // centroid. Refresh when the centroid has drifted past the
        // re-pick threshold or we have no cached cell yet.
        boolean needsRepick = m.overwatchCellX < 0;
        if (!needsRepick) {
            float drift = TacticalScoring.cellDistance(
                    m.overwatchAxisX, m.overwatchAxisY,
                    Math.round(backed.centroidX), Math.round(backed.centroidY));
            needsRepick = drift > REPICK_DRIFT_CELLS;
        }
        if (needsRepick) {
            int[] anchor = pickBackstopCell(member, squad, backed, sim);
            if (anchor == null) {
                return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
            }
            m.overwatchCellX = anchor[0];
            m.overwatchCellY = anchor[1];
            m.overwatchAxisX = Math.round(backed.centroidX);
            m.overwatchAxisY = Math.round(backed.centroidY);
        }

        // Path to the backstop cell. Same idempotent pattern as overwatch.
        if ((member.getCellX() != m.overwatchCellX || member.getCellY() != m.overwatchCellY)
                && member.getMoveProgress() == 0f
                && member.pathIdx >= member.pathCellCount()) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.getCellX(), member.getCellY(),
                    m.overwatchCellX, m.overwatchCellY,
                    sim.getOccupancyMap()));
        }
        if (member.pathIdx < member.pathCellCount()) {
            sim.advanceMovement(member);
        } else {
            member.setMoveProgress(0f);
            member.setRenderPos(member.getCellX(), member.getCellY());
        }

        // Fire pass — all three weapons free. Backstop doctrine is "throw
        // everything you have at whatever the marines are shooting at."
        Unit target = sim.targetOf(member);
        if (target == null) {
            target = sim.getTacticalScoring().findBestTarget(member);
            member.setTarget(target);
        }
        if (target != null) {
            float dist = TacticalScoring.cellDistance(member.getCellX(), member.getCellY(),
                    target.getCellX(), target.getCellY());
            boolean inRange = dist <= member.getAttackRange();
            boolean visible = sim.getGrid().hasLineOfSight(member.getCellX(), member.getCellY(),
                    target.getCellX(), target.getCellY());
            if (inRange) {
                MechCombatantBehavior.tryFireMechWeapons(member, target, dist, sim, visible);
            }
        }
        return ActionStatus.RUNNING;
    }

    /**
     * Picks the nearest alive same-side infantry squad as the mech's backstop
     * target. Returns {@code null} when no friendly infantry squad exists
     * (mech-only side, or all infantry wiped) — caller falls back to parity.
     */
    private static Squad pickBackedSquad(Unit member, Squad selfSquad, BattleView sim) {
        Squad best = null;
        float bestDist = Float.MAX_VALUE;
        for (Squad other : sim.getSquads()) {
            if (other.id == selfSquad.id) continue;
            if (other.faction != selfSquad.faction) continue;
            if (other.aliveMembers == 0) continue;
            if (other.isMechSquad()) continue;
            float dist = TacticalScoring.cellDistance(
                    member.getCellX(), member.getCellY(),
                    Math.round(other.centroidX), Math.round(other.centroidY));
            if (dist < bestDist) {
                bestDist = dist;
                best = other;
            }
        }
        return best;
    }

    /**
     * Picks a walkable cell {@link #FOLLOW_DISTANCE} cells behind
     * {@code backed.centroid} (relative to the known threat axis). Without
     * a known threat ({@code lastSeenEnemy} unset on the mech's squad),
     * "behind" is undefined and the picker falls back to the centroid
     * itself (the mech holds within walking range until contact gives a
     * direction to anchor against).
     *
     * <p>Spirals out from the desired anchor cell to find a walkable
     * neighbor when the exact cell is blocked — same shape as
     * {@code BattleSetup.pickCellsNear} but inline since we only need one
     * cell. Returns {@code null} when no walkable cell exists within a
     * small search radius (essentially never, but defensive).
     */
    private static int[] pickBackstopCell(Unit member, Squad selfSquad, Squad backed, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        float cx = backed.centroidX;
        float cy = backed.centroidY;

        // Compute the "behind" unit vector. Defaults to the direction from
        // the mech's current cell to the centroid (so it just trails the
        // squad) when no contact is known.
        float threatDx = selfSquad.lastSeenEnemyX >= 0 ? selfSquad.lastSeenEnemyX - cx
                : cx - member.getCellX();
        float threatDy = selfSquad.lastSeenEnemyY >= 0 ? selfSquad.lastSeenEnemyY - cy
                : cy - member.getCellY();
        float len = (float) Math.sqrt(threatDx * threatDx + threatDy * threatDy);
        if (len < 1e-3f) {
            // Degenerate — mech is on top of the centroid with no threat
            // axis. Just hold the centroid itself.
            int anchorX = Math.round(cx);
            int anchorY = Math.round(cy);
            return grid.inBounds(anchorX, anchorY) && grid.isWalkable(anchorX, anchorY)
                    ? new int[]{anchorX, anchorY}
                    : null;
        }
        float invLen = 1f / len;
        // "Behind" = away from threat → negate the threat vector.
        float backDx = -threatDx * invLen;
        float backDy = -threatDy * invLen;

        int anchorX = Math.round(cx + backDx * FOLLOW_DISTANCE);
        int anchorY = Math.round(cy + backDy * FOLLOW_DISTANCE);

        // Spiral out from the anchor to find a walkable cell.
        for (int r = 0; r <= 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = anchorX + dx;
                    int y = anchorY + dy;
                    if (!grid.inBounds(x, y)) continue;
                    if (!grid.isWalkable(x, y)) continue;
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }
}
