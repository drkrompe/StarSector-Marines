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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Moving rally posture used by the rescue commander before and during evacuation. */
public final class EscortAssignedCivilians implements Action {

    public static final EscortAssignedCivilians INSTANCE =
            new EscortAssignedCivilians();
    /** The lead squad must enter the shelter's physical relief trigger. */
    static final int RELIEF_RADIUS = 2;
    /** Each moving squad uses a 5x5 tactical pocket around its screen slot. */
    static final int ESCORT_RADIUS = 2;
    /** Each pickup squad uses the same 5x5 pocket around its star point. */
    static final int PICKUP_GUARD_RADIUS = 2;
    private static final String SLOT_PREFIX = "escort:";
    private static final String OVERFLOW_SLOT = SLOT_PREFIX + "overflow";
    private static final int COVER_PRIORITY = 1_536;
    private static final int SPACING_PRIORITY = 32;
    private static final int RADIUS_PRIORITY = 8;

    private record FormationCell(int dx, int dy, int cover, int variation) {}

    private EscortAssignedCivilians() {}

    @Override public String name() { return "EscortCivilians"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    /**
     * Binds each squadmate to one distinct cell around the squad's commander-
     * authored rally anchor. Slot identity is a relative offset rather than an
     * absolute cell, so the formation moves immediately when the escort screen
     * advances without waiting for the next periodic replan.
     */
    @Override
    public List<RoleAssigner.Slot<Long>> roles(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != AssignmentKind.ESCORT) {
            return List.of(new RoleAssigner.Slot<>(OVERFLOW_SLOT,
                    Math.max(1, squad.aliveMembers), candidate -> 0f));
        }
        int radius = standoffRadius(squad, sim);
        boolean tacticalPocket = usesTacticalPocket(squad, sim);
        List<FormationCell> cells = formationCells(assignment.targetCellX(),
                assignment.targetCellY(), radius, tacticalPocket, squad, sim);
        Set<String> selected = selectFormationSlots(cells,
                squad.aliveMembers);
        List<RoleAssigner.Slot<Long>> slots = new ArrayList<>(
                (radius * 2 + 1) * (radius * 2 + 1) + 1);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                String name = slotName(dx, dy);
                int cellX = assignment.targetCellX() + dx;
                int cellY = assignment.targetCellY() + dy;
                slots.add(new RoleAssigner.Slot<>(name,
                        selected.contains(name) ? 1 : 0,
                        candidate -> -cellDistanceSquared(candidate, cellX,
                                cellY, sim)));
            }
        }
        slots.add(new RoleAssigner.Slot<>(OVERFLOW_SLOT,
                Math.max(1, squad.aliveMembers), candidate -> -1_000_000f));
        return slots;
    }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
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
            rally = retainedFallbackCell(member, squad, tx, ty,
                    standoffRadius, sim);
        }
        if (rally == null) {
            rally = nearestOpenRallyCell(member, squad, tx, ty,
                    standoffRadius, sim);
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
        ObjectiveAssignment assignment = squad.assignmentForExecution();
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

    private static boolean usesTacticalPocket(Squad squad, BattleView sim) {
        return squad != null && (squad.rescuePickupGuard
                || sim.isCivilianEvacuationTriggered());
    }

    private static String slotName(int dx, int dy) {
        return SLOT_PREFIX + dx + ":" + dy;
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
        int[] offset = assignedOffset(member, squad);
        if (offset == null) return null;
        int dx = offset[0];
        int dy = offset[1];
        int x = tx + dx;
        int y = ty + dy;
        if (!insideFormationPocket(dx, dy, radius,
                usesTacticalPocket(squad, sim))
                || !sim.getGrid().inBounds(x, y)
                || !sim.getGrid().isWalkable(x, y)) {
            return null;
        }
        return new int[]{x, y};
    }

    private static int[] assignedOffset(long member, Squad squad) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null && !plan.isComplete()
                ? plan.currentStep() : null;
        String name = step != null ? step.slotOf(member) : null;
        if (name == null || !name.startsWith(SLOT_PREFIX)
                || OVERFLOW_SLOT.equals(name)) return null;
        String[] parts = name.substring(SLOT_PREFIX.length()).split(":", -1);
        if (parts.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Keeps an overflow/unslotted member's legal in-flight destination. */
    private static int[] retainedFallbackCell(long member, Squad squad,
                                               int tx, int ty, int radius,
                                               BattleView sim) {
        int[] path = sim.movement().path(member);
        if (sim.movement().pathIdx(member) >= Paths.cellCount(path)) return null;
        int x = Paths.destX(path);
        int y = Paths.destY(path);
        int dx = x - tx;
        int dy = y - ty;
        if (!insideFormationPocket(dx, dy, radius,
                usesTacticalPocket(squad, sim))) return null;
        return sim.getGrid().inBounds(x, y) && sim.getGrid().isWalkable(x, y)
                ? new int[]{x, y} : null;
    }

    /** Walkable cells in a stable per-squad, cover-aware tactical pocket. */
    private static List<FormationCell> formationCells(
            int tx, int ty, int radius, boolean tacticalPocket,
            Squad squad, BattleView sim) {
        List<FormationCell> cells = new ArrayList<>(
                (radius * 2 + 1) * (radius * 2 + 1));
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (!insideFormationPocket(dx, dy, radius,
                        tacticalPocket)) continue;
                int x = tx + dx;
                int y = ty + dy;
                if (!sim.getGrid().inBounds(x, y)
                        || !sim.getGrid().isWalkable(x, y)) continue;
                int cover = sim.getGrid().getCoverAt(x, y)
                        + sim.getDoodadCoverAt(x, y);
                cells.add(new FormationCell(dx, dy, cover,
                        deterministicVariation(squad.id, dx, dy)));
            }
        }
        return cells;
    }

    /**
     * Greedily selects distinct cells. Cover dominates, then separation, with
     * a small deterministic per-squad variation so open-ground formations do
     * not all stamp the same silhouette. The variation excludes the moving
     * anchor, keeping relative slots stable as the escort screen advances.
     */
    private static Set<String> selectFormationSlots(
            List<FormationCell> candidates, int memberCount) {
        List<FormationCell> remaining = new ArrayList<>(candidates);
        List<FormationCell> selectedCells = new ArrayList<>();
        Set<String> selected = new HashSet<>();
        int wanted = Math.min(memberCount, remaining.size());
        while (selectedCells.size() < wanted) {
            FormationCell best = null;
            int bestScore = Integer.MIN_VALUE;
            for (FormationCell candidate : remaining) {
                int minSpacing = minimumSpacingSquared(candidate,
                        selectedCells);
                int radiusSquared = candidate.dx() * candidate.dx()
                        + candidate.dy() * candidate.dy();
                int score = candidate.cover() * COVER_PRIORITY
                        + minSpacing * SPACING_PRIORITY
                        + radiusSquared * RADIUS_PRIORITY
                        + candidate.variation();
                if (best == null || score > bestScore
                        || (score == bestScore
                        && compareCell(candidate, best) < 0)) {
                    best = candidate;
                    bestScore = score;
                }
            }
            selectedCells.add(best);
            selected.add(slotName(best.dx(), best.dy()));
            remaining.remove(best);
        }
        return selected;
    }

    private static int minimumSpacingSquared(FormationCell candidate,
                                              List<FormationCell> selected) {
        if (selected.isEmpty()) return 0;
        int minimum = Integer.MAX_VALUE;
        for (FormationCell other : selected) {
            int dx = candidate.dx() - other.dx();
            int dy = candidate.dy() - other.dy();
            minimum = Math.min(minimum, dx * dx + dy * dy);
        }
        return minimum;
    }

    private static int compareCell(FormationCell left, FormationCell right) {
        int byY = Integer.compare(left.dy(), right.dy());
        return byY != 0 ? byY : Integer.compare(left.dx(), right.dx());
    }

    private static int deterministicVariation(int squadId, int dx, int dy) {
        int hash = squadId * 0x45D9F3B;
        hash ^= (dx + 17) * 0x119DE1F3;
        hash ^= (dy + 29) * 0x3449B1;
        hash ^= hash >>> 16;
        hash *= 0x45D9F3B;
        hash ^= hash >>> 16;
        return hash & 127;
    }

    private static boolean insideFormationPocket(int dx, int dy, int radius,
                                                   boolean tacticalPocket) {
        if (Math.abs(dx) > radius || Math.abs(dy) > radius) return false;
        return tacticalPocket || dx * dx + dy * dy <= radius * radius;
    }

    private static int[] nearestOpenRallyCell(long member, Squad squad,
                                               int tx, int ty, int radius,
                                               BattleView sim) {
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
                if (!insideFormationPocket(ex, ey, radius,
                        usesTacticalPocket(squad, sim))) continue;
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
