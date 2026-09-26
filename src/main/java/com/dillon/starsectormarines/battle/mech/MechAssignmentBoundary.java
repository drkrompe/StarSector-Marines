package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.Objects;

/** Shared movement law for a mech serving a command-authored assignment. */
final class MechAssignmentBoundary {

    /** Local tactical freedom around an exact command cell. */
    static final float EXACT_CELL_LEASH = 10f;
    /** Once this close, the exact command has been serviced for this assignment. */
    static final float EXACT_CELL_ARRIVAL = 2f;
    /** ATTACK_MOVE may fight locally, but never surrender more than this much progress. */
    static final float ATTACK_MOVE_PROGRESS_LEASH = 10f;
    /** Maximum standoff beyond mission-owned zone ground while engaging an in-zone contact. */
    static final float ZONE_COMBAT_STANDOFF_LEASH = 10f;

    private MechAssignmentBoundary() {}

    /**
     * Observes command provenance on the serial action pass. Releasing command,
     * temporarily masking it for form-up, or issuing a new ledger generation
     * clears the old arrival/progress latch even when the next order has the
     * same kind and destination. Doctrine-only overrides do not touch it.
     */
    static void observeAssignment(long member, Squad squad, BattleView sim) {
        MechLoadoutComponent loadout = sim.world().mechLoadout(member);
        if (loadout == null) return;
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() == AssignmentKind.WITHDRAW) {
            clearAssignment(loadout);
            return;
        }
        int[] destination = exactDestination(assignment, sim.getGrid());
        if (destination == null) {
            clearAssignment(loadout);
            return;
        }
        syncAssignment(loadout, squad, assignment,
                destination[0], destination[1], sim);
    }

    static boolean hasSupportedAssignment(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() == AssignmentKind.WITHDRAW) {
            return false;
        }
        long member = sim.resolveUnit(squad.leaderId);
        if (member == 0L && sim.squadMemberCount(squad.id) > 0) {
            member = sim.squadMemberAt(squad.id, 0);
        }
        return member != 0L && missionDestination(member, squad, sim) != null;
    }

    static boolean isAttackMove(Squad squad) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        return assignment != null && assignment.kind() == AssignmentKind.ATTACK_MOVE;
    }

    /** The exact command cell, or a deterministic cell inside its target zone. */
    static int[] missionDestination(long member, Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() == AssignmentKind.WITHDRAW) {
            return null;
        }
        if (assignment.targetCellX() >= 0 && assignment.targetCellY() >= 0) {
            return walkableNear(assignment.targetCellX(), assignment.targetCellY(),
                    sim.getGrid());
        }
        if (assignment.kind() == AssignmentKind.HOLD_NODE
                && assignment.targetNode() != null) {
            return nodeDestination(assignment.targetNode(), sim.getGrid());
        }
        if (assignment.targetZoneId() < 0) return null;
        NavigationZone zone = sim.getZoneGraph().zoneById(assignment.targetZoneId());
        if (zone == null || zone.getCellCount() == 0) return null;
        int currentX = sim.world().cellX(member);
        int currentY = sim.world().cellY(member);
        if (sim.getZoneGraph().zoneIdAt(currentX, currentY)
                == assignment.targetZoneId()) {
            return new int[]{currentX, currentY};
        }
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        int width = sim.getGrid().getWidth();
        for (int cell : zone.getCellIndices()) {
            int x = cell % width;
            int y = cell / width;
            if (!sim.getGrid().isWalkable(x, y)) continue;
            float distance = TacticalScoring.cellDistance(
                    sim.world().x(member), sim.world().y(member),
                    x + 0.5f, y + 0.5f);
            if (distance < bestDistance || distance == bestDistance && cell < best) {
                best = cell;
                bestDistance = distance;
            }
        }
        return best >= 0 ? new int[]{best % width, best / width} : null;
    }

    /** Whether a doctrine-authored cell remains inside the active command. */
    static boolean permitsCell(long member, Squad squad, int x, int y,
                               BattleView sim) {
        observeAssignment(member, squad, sim);
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() == AssignmentKind.WITHDRAW) {
            return true;
        }
        int[] exact = exactDestination(assignment, sim.getGrid());
        if (exact != null) {
            if (assignment.kind() == AssignmentKind.ATTACK_MOVE) {
                return insideAttackMoveProgress(
                        member, assignment, exact[0], exact[1], x, y, sim);
            }
            if (!hasReachedExactCell(
                    member, assignment, exact[0], exact[1], sim)) {
                return x == exact[0] && y == exact[1];
            }
            return TacticalScoring.cellDistance(x + 0.5f, y + 0.5f,
                    exact[0] + 0.5f, exact[1] + 0.5f) <= EXACT_CELL_LEASH;
        }
        return assignment.targetZoneId() < 0
                || sim.getZoneGraph().zoneIdAt(x, y) == assignment.targetZoneId();
    }

    /**
     * LR Support may use a bounded perimeter around a combat-zone assignment
     * while its perceived threat remains inside that exact mission zone. This
     * changes only the firing posture: the enemy and zone are still the
     * command-owned objective, and exact-cell/withdrawal orders stay strict.
     */
    static boolean permitsOverwatchCell(long member, Squad squad,
                                        int x, int y,
                                        int threatX, int threatY,
                                        BattleView sim) {
        if (permitsCell(member, squad, x, y, sim)) return true;
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.targetZoneId() < 0
                || !supportsZoneCombatStandoff(assignment.kind())
                || sim.getZoneGraph().zoneIdAt(threatX, threatY)
                != assignment.targetZoneId()) {
            return false;
        }
        int radius = (int) Math.ceil(ZONE_COMBAT_STANDOFF_LEASH);
        float maxDistanceSq = ZONE_COMBAT_STANDOFF_LEASH
                * ZONE_COMBAT_STANDOFF_LEASH;
        for (int oy = -radius; oy <= radius; oy++) {
            for (int ox = -radius; ox <= radius; ox++) {
                if (ox * ox + oy * oy > maxDistanceSq) continue;
                if (sim.getZoneGraph().zoneIdAt(x + ox, y + oy)
                        == assignment.targetZoneId()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Clamps a tactical destination back onto command-owned ground. */
    static int[] constrain(long member, Squad squad, int[] desired,
                           BattleView sim) {
        observeAssignment(member, squad, sim);
        if (desired != null && sim.getGrid().inBounds(desired[0], desired[1])
                && sim.getGrid().isWalkable(desired[0], desired[1])
                && permitsCell(member, squad, desired[0], desired[1], sim)) {
            return desired;
        }
        return missionDestination(member, squad, sim);
    }

    /** Advances toward the command destination and returns true when one exists. */
    static boolean advanceToMission(long member, Squad squad, BattleControl sim) {
        observeAssignment(member, squad, sim);
        int[] destination = missionDestination(member, squad, sim);
        if (destination == null) return false;
        moveToward(member, destination[0], destination[1], sim);
        return true;
    }

    static void moveToward(long member, int x, int y, BattleControl sim) {
        MechRouteIntent.forMember(member, MechAssignmentBoundary.class,
                sim.squadOf(member) != null ? sim.squadOf(member).routingEpoch : 0L, sim)
                .moveToward(member, x, y, sim);
    }

    private static int[] walkableNear(int targetX, int targetY,
                                      NavigationGrid grid) {
        for (int radius = 0; radius <= 4; radius++) {
            for (int oy = -radius; oy <= radius; oy++) {
                for (int ox = -radius; ox <= radius; ox++) {
                    if (Math.max(Math.abs(ox), Math.abs(oy)) != radius) continue;
                    int x = targetX + ox;
                    int y = targetY + oy;
                    if (grid.inBounds(x, y) && grid.isWalkable(x, y)) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        return null;
    }

    private static boolean supportsZoneCombatStandoff(AssignmentKind kind) {
        return kind == AssignmentKind.CLEAR_ZONE
                || kind == AssignmentKind.SECURE_COMPOUND;
    }

    private static int[] exactDestination(ObjectiveAssignment assignment,
                                          NavigationGrid grid) {
        if (assignment.targetCellX() >= 0 && assignment.targetCellY() >= 0) {
            return walkableNear(assignment.targetCellX(),
                    assignment.targetCellY(), grid);
        }
        if (assignment.kind() == AssignmentKind.HOLD_NODE
                && assignment.targetNode() != null) {
            return nodeDestination(assignment.targetNode(), grid);
        }
        return null;
    }

    private static int[] nodeDestination(TacticalNode node,
                                         NavigationGrid grid) {
        for (TacticalNode.StandPosition stand : node.standPositions()) {
            if (grid.inBounds(stand.x(), stand.y())
                    && grid.isWalkable(stand.x(), stand.y())) {
                return new int[]{stand.x(), stand.y()};
            }
        }
        return walkableNear(node.anchorX, node.anchorY, grid);
    }

    private static boolean hasReachedExactCell(long member,
                                               ObjectiveAssignment assignment,
                                               int destinationX,
                                               int destinationY,
                                               BattleView sim) {
        MechLoadoutComponent loadout = sim.world().mechLoadout(member);
        if (loadout == null) return false;
        syncAssignment(loadout, sim.squadOf(member), assignment,
                destinationX, destinationY, sim);
        if (!loadout.assignmentBoundaryReached) {
            loadout.assignmentBoundaryReached = TacticalScoring.cellDistance(
                    sim.world().x(member), sim.world().y(member),
                    destinationX + 0.5f,
                    destinationY + 0.5f) <= EXACT_CELL_ARRIVAL;
        }
        return loadout.assignmentBoundaryReached;
    }

    private static boolean insideAttackMoveProgress(
            long member, ObjectiveAssignment assignment, int destinationX,
            int destinationY, int desiredX, int desiredY, BattleView sim) {
        MechLoadoutComponent loadout = sim.world().mechLoadout(member);
        if (loadout == null) return false;
        syncAssignment(loadout, sim.squadOf(member), assignment,
                destinationX, destinationY, sim);
        float currentDistance = TacticalScoring.cellDistance(
                sim.world().x(member), sim.world().y(member),
                destinationX + 0.5f, destinationY + 0.5f);
        loadout.assignmentBoundaryBestDistance = Math.min(
                loadout.assignmentBoundaryBestDistance, currentDistance);
        float desiredDistance = TacticalScoring.cellDistance(
                desiredX + 0.5f, desiredY + 0.5f,
                destinationX + 0.5f, destinationY + 0.5f);
        return desiredDistance <= loadout.assignmentBoundaryBestDistance
                + ATTACK_MOVE_PROGRESS_LEASH;
    }

    private static void syncAssignment(MechLoadoutComponent loadout,
                                       Squad squad,
                                       ObjectiveAssignment assignment,
                                       int destinationX,
                                       int destinationY,
                                       BattleView sim) {
        CommandDirective directive = squad != null
                ? sim.getSquadCommandDirective(squad.id) : null;
        boolean ledgerOwnsAssignment = directive != null
                && directive.ownsAssignment()
                && Objects.equals(directive.assignment(), assignment);
        int issuedTick = ledgerOwnsAssignment
                ? directive.issuedTick() : Integer.MIN_VALUE;
        String issuer = ledgerOwnsAssignment ? directive.issuer() : null;
        CommandAuthority authority = ledgerOwnsAssignment
                ? directive.authority() : null;
        if (loadout.assignmentBoundaryKind != assignment.kind()
                || loadout.assignmentBoundaryCellX != destinationX
                || loadout.assignmentBoundaryCellY != destinationY
                || loadout.assignmentBoundaryIssuedTick != issuedTick
                || !Objects.equals(loadout.assignmentBoundaryIssuer, issuer)
                || loadout.assignmentBoundaryAuthority != authority) {
            loadout.assignmentBoundaryKind = assignment.kind();
            loadout.assignmentBoundaryCellX = destinationX;
            loadout.assignmentBoundaryCellY = destinationY;
            loadout.assignmentBoundaryIssuedTick = issuedTick;
            loadout.assignmentBoundaryIssuer = issuer;
            loadout.assignmentBoundaryAuthority = authority;
            loadout.assignmentBoundaryReached = false;
            loadout.assignmentBoundaryBestDistance = Float.POSITIVE_INFINITY;
        }
    }

    private static void clearAssignment(MechLoadoutComponent loadout) {
        loadout.assignmentBoundaryKind = null;
        loadout.assignmentBoundaryCellX = Integer.MIN_VALUE;
        loadout.assignmentBoundaryCellY = Integer.MIN_VALUE;
        loadout.assignmentBoundaryIssuedTick = Integer.MIN_VALUE;
        loadout.assignmentBoundaryIssuer = null;
        loadout.assignmentBoundaryAuthority = null;
        loadout.assignmentBoundaryReached = false;
        loadout.assignmentBoundaryBestDistance = Float.POSITIVE_INFINITY;
    }
}
