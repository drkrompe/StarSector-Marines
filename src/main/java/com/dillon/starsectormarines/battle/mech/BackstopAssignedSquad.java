package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.PathRequestStatus;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.Objects;

/**
 * Armored Support / Tank doctrine: pace a designated friendly infantry or
 * non-Tank mech squad and hold a threat-facing cell in front of its centroid.
 * Fires all three weapons freely — no withhold gates — but does not chase when
 * no valid anchor exists.
 *
 * <p>"Designated squad" is picked lazily at the first execute tick: the
 * nearest eligible same-side squad. A pure Tank squad is not eligible to
 * anchor another Tank, preventing two support elements from selecting each
 * other cyclically. Cached on {@link MechLoadoutComponent#assignedSquadId};
 * cleared back to -1 when the backed squad gets wiped, leaves the local
 * mission, or moves outside the acquisition cap, so the next tick re-picks.
 * Explicit squad assignments constrain which anchor relationships are legal.
 *
 * <p>"Front" direction = toward the best known threat axis. Without a known
 * threat, the mech closes to the anchor centroid and waits for contact.
 *
 * <p>Per-member role branching matches {@link OverwatchKillZone}:
 * ARMORED_SUPPORT members run this body; everyone else falls through to
 * parity engagement for mixed-role squads.
 */
public final class BackstopAssignedSquad implements Action {

    public static final BackstopAssignedSquad INSTANCE = new BackstopAssignedSquad();

    /**
     * Cells between the backed squad's centroid and the mech's anchor cell.
     * Chosen so the mech's chaingun (30-cell range) reaches roughly two
     * cells beyond the marines' 24-cell rifle envelope — the mech adds
     * "outranging support fire" without crowding the squad's own LoS.
     */
    static final float FRONT_DISTANCE = 4f;
    /** Nearby unassigned allies may be adopted without inventing an across-map task. */
    static final float MAX_ANCHOR_ACQUIRE_DISTANCE = 18f;
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
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        // Non-ARMORED_SUPPORT members fall through to parity (mixed squads).
        // Loadout reached by id (zero-alloc direct lookup).
        MechLoadoutComponent m = sim.world().mechLoadout(member);
        if (m == null || m.effectiveRole() != MechRole.ARMORED_SUPPORT) {
            return EngageAtCurrentBand.INSTANCE.execute(member, squad, sim);
        }

        // Pick or refresh the backed squad. Lazy pick on first call;
        // re-pick when the cached assignment is wiped or gone.
        Squad backed = m.assignedSquadId >= 0 ? sim.getSquad(m.assignedSquadId) : null;
        if (!isEligibleAnchor(backed, squad, sim)
                || !isMissionLegalAnchor(backed, squad, sim)
                || !isWithinAnchorCap(member, backed, sim)) {
            backed = pickBackedSquad(member, squad, sim);
            m.assignedSquadId = backed != null ? backed.id : -1;
        }
        if (backed == null) {
            holdAndDefend(member, m, sim);
            return ActionStatus.RUNNING;
        }

        MechRouteIntent route = MechRouteIntent.forMember(member, BackstopAssignedSquad.class, backed.id, sim);
        route.refreshCandidates(MechRouteIntent.cellKey((int) backed.centroidX, (int) backed.centroidY));

        // Pick or refresh the frontline cell ahead of the backed squad's
        // centroid. Refresh when the centroid has drifted past the
        // re-pick threshold or we have no cached cell yet.
        boolean needsRepick = m.overwatchCellX < 0;
        if (!needsRepick) {
            float drift = TacticalScoring.cellDistance(
                    m.overwatchAxisX, m.overwatchAxisY,
                    backed.centroidX, backed.centroidY);
            needsRepick = drift > REPICK_DRIFT_CELLS;
        }
        int threatX = knownThreatX(squad);
        int threatY = knownThreatY(squad);
        needsRepick |= m.frontlineThreatX != threatX || m.frontlineThreatY != threatY;
        if (needsRepick && !route.pending()) {
            int[] anchor = pickBackstopCell(member, squad, backed, sim);
            anchor = MechAssignmentBoundary.constrain(
                    member, squad, anchor, sim);
            if (anchor == null) {
                holdAndDefend(member, m, sim);
                return ActionStatus.RUNNING;
            }
            m.overwatchCellX = anchor[0];
            m.overwatchCellY = anchor[1];
            m.overwatchAxisX = Math.round(backed.centroidX);
            m.overwatchAxisY = Math.round(backed.centroidY);
            m.frontlineThreatX = threatX;
            m.frontlineThreatY = threatY;
        }

        // Path to the backstop cell. Same idempotent pattern as overwatch.
        if (route.moveToward(member, m.overwatchCellX, m.overwatchCellY, sim)
                == PathRequestStatus.FAILED) {
            m.overwatchCellX = -1;
            m.overwatchCellY = -1;
        }


        fireAtCurrentThreat(member, m, sim);
        return ActionStatus.RUNNING;
    }

    private static void holdAndDefend(long member, MechLoadoutComponent loadout,
                                      BattleControl sim) {
        if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
        fireAtCurrentThreat(member, loadout, sim);
    }

    private static void fireAtCurrentThreat(long member, MechLoadoutComponent loadout,
                                            BattleControl sim) {
        long target = MechTargeting.refreshTarget(member, sim);
        sim.world().setTargetId(member, target);
        if (target != 0L) {
            float dist = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                    sim.world().x(target), sim.world().y(target));
            boolean inRange = dist <= sim.world().attackRange(member);
            boolean visible = sim.getTacticalScoring().hasClearShot(member, target);
            if (inRange) {
                MechCombatantBehavior.tryFireMechWeapons(
                        member, loadout, target, dist, sim, visible);
            }
        }
    }

    /**
     * Picks the nearest eligible same-side infantry or non-cyclic mech squad.
     */
    static Squad pickBackedSquad(long member, Squad selfSquad, BattleView sim) {
        Squad best = null;
        float bestDist = Float.MAX_VALUE;
        boolean bestSharesMission = false;
        for (Squad other : sim.getSquads()) {
            if (!isEligibleAnchor(other, selfSquad, sim)) continue;
            if (!isMissionLegalAnchor(other, selfSquad, sim)) continue;
            float dist = TacticalScoring.cellDistance(
                    sim.world().x(member), sim.world().y(member),
                    other.centroidX, other.centroidY);
            if (dist > MAX_ANCHOR_ACQUIRE_DISTANCE) continue;
            boolean sharesMission = other.id == selfSquad.id
                    || sharesMissionTarget(selfSquad, other);
            if (best == null || (sharesMission && !bestSharesMission)
                    || (sharesMission == bestSharesMission
                    && (dist < bestDist
                    || (dist == bestDist && other.id < best.id)))) {
                bestDist = dist;
                best = other;
                bestSharesMission = sharesMission;
            }
        }
        return best;
    }

    private static boolean isMissionLegalAnchor(Squad candidate,
                                                Squad selfSquad,
                                                BattleView sim) {
        if (!MechAssignmentBoundary.hasSupportedAssignment(selfSquad, sim)) {
            return true;
        }
        return candidate.id == selfSquad.id
                || sharesMissionTarget(selfSquad, candidate);
    }

    private static boolean isWithinAnchorCap(long member, Squad candidate,
                                             BattleView sim) {
        return candidate != null && TacticalScoring.cellDistance(
                sim.world().x(member), sim.world().y(member),
                candidate.centroidX, candidate.centroidY)
                <= MAX_ANCHOR_ACQUIRE_DISTANCE;
    }

    static boolean isEligibleAnchor(Squad candidate, Squad selfSquad, BattleView sim) {
        if (candidate == null || candidate.faction != selfSquad.faction
                || candidate.aliveMembers == 0 || candidate.isDroneSquad()) {
            return false;
        }
        if (candidate.rescuePickupMech) return false;
        if (!candidate.isMechSquad()) {
            for (int i = 0, n = sim.squadMemberCount(candidate.id); i < n; i++) {
                UnitType type = sim.identity().type(sim.squadMemberAt(candidate.id, i));
                if (type == UnitType.MARINE || type == UnitType.MARINE_BLUE
                        || type == UnitType.MARINE_RED || type == UnitType.MILITIA) {
                    return true;
                }
            }
            return false;
        }
        for (int i = 0, n = sim.squadMemberCount(candidate.id); i < n; i++) {
            MechLoadoutComponent loadout =
                    sim.world().mechLoadout(sim.squadMemberAt(candidate.id, i));
            if (loadout != null
                    && loadout.effectiveRole() != MechRole.ARMORED_SUPPORT) {
                return true;
            }
        }
        return false;
    }

    private static boolean sharesMissionTarget(Squad first, Squad second) {
        ObjectiveAssignment a = first.assignmentForExecution();
        ObjectiveAssignment b = second.assignmentForExecution();
        if (a == null || b == null || a.kind() != b.kind()) return false;
        if (a.objectiveId() >= 0 || b.objectiveId() >= 0) {
            return a.objectiveId() >= 0 && a.objectiveId() == b.objectiveId();
        }
        if (a.targetNode() != null || b.targetNode() != null) {
            return a.targetNode() != null && Objects.equals(
                    a.targetNode(), b.targetNode());
        }
        if (a.targetZoneId() >= 0 || b.targetZoneId() >= 0) {
            return a.targetZoneId() >= 0 && a.targetZoneId() == b.targetZoneId();
        }
        return a.targetCellX() >= 0 && a.targetCellY() >= 0
                && a.targetCellX() == b.targetCellX()
                && a.targetCellY() == b.targetCellY();
    }

    /**
     * Picks a walkable cell {@link #FRONT_DISTANCE} cells toward the known
     * threat from {@code backed.centroid}. Without a known threat, the anchor
     * is the centroid itself.
     *
     * <p>Spirals out from the desired anchor cell to find a walkable
     * neighbor when the exact cell is blocked — same shape as
     * {@code BattleSetup.pickCellsNear} but inline since we only need one
     * cell. Returns {@code null} when no walkable cell exists within a
     * small search radius (essentially never, but defensive).
     */
    private static int[] pickBackstopCell(long member, Squad selfSquad, Squad backed, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        float cx = backed.centroidX;
        float cy = backed.centroidY;

        int threatX = knownThreatX(selfSquad);
        int threatY = knownThreatY(selfSquad);
        float threatDx = threatX >= 0 ? (threatX + 0.5f) - cx : 0f;
        float threatDy = threatY >= 0 ? (threatY + 0.5f) - cy : 0f;
        float len = (float) Math.sqrt(threatDx * threatDx + threatDy * threatDy);
        if (len < 1e-3f) {
            // Degenerate — mech is on top of the centroid with no threat
            // axis. Just hold the centroid's containing cell (floor — the
            // centroid is a center-based continuous position).
            int anchorX = (int) Math.floor(cx);
            int anchorY = (int) Math.floor(cy);
            return grid.inBounds(anchorX, anchorY) && grid.isWalkable(anchorX, anchorY)
                    && MechRouteIntent.candidate(member, anchorX, anchorY, sim)
                    ? new int[]{anchorX, anchorY}
                    : null;
        }
        float invLen = 1f / len;
        int anchorX = (int) Math.floor(cx + threatDx * invLen * FRONT_DISTANCE);
        int anchorY = (int) Math.floor(cy + threatDy * invLen * FRONT_DISTANCE);

        // Spiral out from the anchor to find a walkable cell.
        for (int r = 0; r <= 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = anchorX + dx;
                    int y = anchorY + dy;
                    if (!grid.inBounds(x, y)) continue;
                    if (!grid.isWalkable(x, y)
                            || !MechRouteIntent.candidate(member, x, y, sim)) continue;
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static int knownThreatX(Squad selfSquad) {
        return selfSquad.lastSeenEnemyX >= 0 && selfSquad.lastSeenEnemyY >= 0
                ? selfSquad.lastSeenEnemyX : -1;
    }

    private static int knownThreatY(Squad selfSquad) {
        return selfSquad.lastSeenEnemyX >= 0 && selfSquad.lastSeenEnemyY >= 0
                ? selfSquad.lastSeenEnemyY : -1;
    }
}
