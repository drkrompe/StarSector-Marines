package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.ArrayList;
import java.util.List;

/** Moving rally posture used by the rescue commander before and during evacuation. */
public final class EscortAssignedCivilians implements Action {

    public static final EscortAssignedCivilians INSTANCE =
            new EscortAssignedCivilians();
    /** The lead squad must enter the shelter's physical relief trigger. */
    static final int RELIEF_RADIUS = 2;
    /** Each squad forms locally around its distinct commander-authored screen slot. */
    static final int ESCORT_RADIUS = 2;
    static final int PICKUP_GUARD_RADIUS = 2;
    private static final String SLOT_PREFIX = "escort:";
    private static final String OVERFLOW_SLOT = SLOT_PREFIX + "overflow";
    /**
     * Common four- and eight-person squads occupy the one-cell ring first.
     * The anchor and two-cell cardinal posts are fallbacks for larger squads
     * or terrain-obstructed rings.
     */
    private static final int[][] FORMATION_OFFSETS = {
            {0, -1}, {-1, 0}, {1, 0}, {0, 1},
            {-1, -1}, {1, -1}, {-1, 1}, {1, 1},
            {0, 0}, {0, -2}, {-2, 0}, {2, 0}, {0, 2}
    };

    private EscortAssignedCivilians() {}

    @Override public String name() { return "EscortCivilians"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    /**
     * Binds each squadmate to one distinct cell around the squad's commander-
     * authored rally anchor. Slot identity is an offset-list index rather than
     * an absolute cell, so the formation moves immediately when the escort
     * screen advances without waiting for the next periodic replan.
     */
    @Override
    public List<RoleAssigner.Slot<Long>> roles(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null || assignment.kind() != AssignmentKind.ESCORT) {
            return List.of(new RoleAssigner.Slot<>(OVERFLOW_SLOT,
                    Math.max(1, squad.aliveMembers), candidate -> 0f));
        }
        List<int[]> cells = formationCells(assignment.targetCellX(),
                assignment.targetCellY(), standoffRadius(squad, sim), sim);
        int distinctSlots = Math.min(squad.aliveMembers, cells.size());
        List<RoleAssigner.Slot<Long>> slots = new ArrayList<>(
                distinctSlots + 1);
        for (int slot = 0; slot < distinctSlots; slot++) {
            int offsetIndex = cells.get(slot)[2];
            int cellX = cells.get(slot)[0];
            int cellY = cells.get(slot)[1];
            slots.add(new RoleAssigner.Slot<>(slotName(offsetIndex), 1,
                    candidate -> -cellDistanceSquared(candidate, cellX,
                            cellY, sim)));
        }
        slots.add(new RoleAssigner.Slot<>(OVERFLOW_SLOT,
                Math.max(1, squad.aliveMembers), candidate -> -1_000_000f));
        return slots;
    }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null || assignment.kind() != AssignmentKind.ESCORT
                || assignment.targetCellX() < 0
                || assignment.targetCellY() < 0) {
            return ActionStatus.FAILURE;
        }

        int tx = assignment.targetCellX();
        int ty = assignment.targetCellY();
        int standoffRadius = standoffRadius(squad, sim);
        int[] rally = assignedFormationCell(member, squad, tx, ty,
                standoffRadius, sim);
        if (rally == null) {
            rally = retainedFallbackCell(member, tx, ty, standoffRadius, sim);
        }
        if (rally == null) {
            rally = nearestOpenRallyCell(member, tx, ty, standoffRadius, sim);
        }
        if (rally == null) {
            PatrolMotion.hold(member, sim);
            PatrolMotion.fireIfAble(member, sim);
            return ActionStatus.RUNNING;
        }
        if (sim.movement().atCell(member, rally[0], rally[1])) {
            PatrolMotion.hold(member, sim);
            PatrolMotion.fireIfAble(member, sim);
            return ActionStatus.RUNNING;
        }
        int[] path = sim.movement().path(member);
        if (Paths.destX(path) != rally[0] || Paths.destY(path) != rally[1]) {
            sim.clearPath(member);
        }
        PatrolMotion.moveToward(member, sim, rally[0], rally[1]);
        PatrolMotion.fireIfAble(member, sim);
        return ActionStatus.RUNNING;
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null || assignment.kind() != AssignmentKind.ESCORT) {
            return List.of();
        }
        return List.of(new int[]{assignment.targetCellX(), assignment.targetCellY()});
    }

    static int standoffRadius(Squad squad, BattleView sim) {
        if (squad.rescuePickupGuard) return PICKUP_GUARD_RADIUS;
        if (sim.isCivilianEvacuationTriggered()) return ESCORT_RADIUS;
        return RELIEF_RADIUS;
    }

    private static String slotName(int offsetIndex) {
        return SLOT_PREFIX + offsetIndex;
    }

    private static float cellDistanceSquared(long member, int cellX,
                                             int cellY, BattleView sim) {
        float dx = sim.world().x(member) - (cellX + 0.5f);
        float dy = sim.world().y(member) - (cellY + 0.5f);
        return dx * dx + dy * dy;
    }

    private static int[] assignedFormationCell(long member, Squad squad,
                                                int tx, int ty, int radius,
                                                BattleView sim) {
        int offsetIndex = assignedOffsetIndex(member, squad);
        if (offsetIndex < 0 || offsetIndex >= FORMATION_OFFSETS.length) {
            return null;
        }
        int x = tx + FORMATION_OFFSETS[offsetIndex][0];
        int y = ty + FORMATION_OFFSETS[offsetIndex][1];
        int dx = x - tx;
        int dy = y - ty;
        if (dx * dx + dy * dy > radius * radius
                || !sim.getGrid().inBounds(x, y)
                || !sim.getGrid().isWalkable(x, y)) {
            return null;
        }
        return new int[]{x, y};
    }

    private static int assignedOffsetIndex(long member, Squad squad) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null && !plan.isComplete()
                ? plan.currentStep() : null;
        String name = step != null ? step.slotOf(member) : null;
        if (name == null || !name.startsWith(SLOT_PREFIX)) return -1;
        try {
            return Integer.parseInt(name.substring(SLOT_PREFIX.length()));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Keeps an overflow/unslotted member's legal in-flight destination. */
    private static int[] retainedFallbackCell(long member, int tx, int ty,
                                               int radius, BattleView sim) {
        int[] path = sim.movement().path(member);
        if (sim.movement().pathIdx(member) >= Paths.cellCount(path)) return null;
        int x = Paths.destX(path);
        int y = Paths.destY(path);
        int dx = x - tx;
        int dy = y - ty;
        if (dx * dx + dy * dy > radius * radius) return null;
        return sim.getGrid().inBounds(x, y) && sim.getGrid().isWalkable(x, y)
                ? new int[]{x, y} : null;
    }

    /** Ordered walkable formation cells as {@code [x, y, offsetIndex]}. */
    private static List<int[]> formationCells(int tx, int ty, int radius,
                                               BattleView sim) {
        List<int[]> cells = new ArrayList<>(FORMATION_OFFSETS.length);
        for (int offsetIndex = 0; offsetIndex < FORMATION_OFFSETS.length;
             offsetIndex++) {
            int dx = FORMATION_OFFSETS[offsetIndex][0];
            int dy = FORMATION_OFFSETS[offsetIndex][1];
            if (dx * dx + dy * dy > radius * radius) continue;
            int x = tx + dx;
            int y = ty + dy;
            if (!sim.getGrid().inBounds(x, y)
                    || !sim.getGrid().isWalkable(x, y)) continue;
            cells.add(new int[]{x, y, offsetIndex});
        }
        return cells;
    }

    private static int[] nearestOpenRallyCell(long member, int tx, int ty,
                                               int radius, BattleView sim) {
        int mx = sim.world().cellX(member);
        int my = sim.world().cellY(member);
        byte[] occupied = sim.getOccupancyMap();
        int bestX = -1;
        int bestY = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = ty - radius; y <= ty + radius; y++) {
            for (int x = tx - radius; x <= tx + radius; x++) {
                int ex = x - tx;
                int ey = y - ty;
                if (ex * ex + ey * ey > radius * radius) continue;
                if (!sim.getGrid().inBounds(x, y) || !sim.getGrid().isWalkable(x, y)) continue;
                if ((x != mx || y != my)
                        && occupied[sim.getGrid().index(x, y)] != 0) continue;
                int distance = Math.abs(x - mx) + Math.abs(y - my);
                if (distance < bestDistance
                        || (distance == bestDistance
                        && (y < bestY || (y == bestY && x < bestX)))) {
                    bestX = x;
                    bestY = y;
                    bestDistance = distance;
                }
            }
        }
        return bestX >= 0 ? new int[]{bestX, bestY} : null;
    }
}
