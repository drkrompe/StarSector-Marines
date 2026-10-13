package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * First-pass command reasoning about ground-defence structures: a fortress battery's fire control and a shield
 * generator. Pure planning over frozen facts, shared by both Conquest perspectives, because nothing about a structure
 * is special to attacker or defender.
 *
 * <p>A side's commander treats a structure it <em>knows of</em> (see {@link ConquestCommandFacts#structures}) as work:
 * <ul>
 *   <li>{@link Role#DEFEND}: its own structure is being pressed (a contested fire control, a capture under way), so a
 *       squad stands to at it;</li>
 *   <li>{@link Role#RETAKE}: a structure built for this side is now held by an enemy;</li>
 *   <li>{@link Role#TAKE}: an enemy structure this side has seen. The squad is sent to the ground around it with an
 *       attack move, so it takes the structure by standing there or wrecks it by shooting it, whichever the fight
 *       allows.</li>
 * </ul>
 *
 * <p>The aspects of one fort are not each a task: {@link FortTactics} chooses which of them is work, in what order, and
 * whether the aim is to hold or to wreck, and every task carries its reason as a sentence for the command trace.
 *
 * <p>Deliberately modest. At most {@link #MAX_TASKS} squads are detached at once, one per structure, most pressing
 * first, and a squad that already carries a task for a structure keeps it for as long as the work stands. The orders are
 * the ordinary attack move and area defence: own-force destinations that name no hostile identity or position, which
 * is what keeps the commander inside its knowledge laws. Which squads may be offered is the caller's decision; it
 * never offers a squad doing work that should not be interrupted.
 */
public final class DefenceStructureTactics {

    /** The most squads detached to structure work at one time. UNTUNED first-pass figure. */
    public static final int MAX_TASKS = 2;

    private DefenceStructureTactics() {}

    /** Why a structure is work for this side. Declared in priority order. */
    public enum Role { DEFEND, RETAKE, TAKE }

    /**
     * A squad the caller is willing to detach, with the assignment it carries now.
     *
     * @param demolition whether the squad carries the means to destroy a building outright (a satchel kit with a charge
     *                   left, see {@code DemolitionKit}), read from the squad's own living members by the command frame.
     *                   A wreck is only ever given to such a squad
     */
    public record Candidate(int squadId, int anchorCellX, int anchorCellY, ObjectiveAssignment incumbent,
                            boolean demolition) {
        public Candidate(int squadId, int anchorCellX, int anchorCellY, ObjectiveAssignment incumbent) {
            this(squadId, anchorCellX, anchorCellY, incumbent, false);
        }
    }

    /**
     * One squad's structure task. {@code reason} says in a sentence why this structure and not another of the same
     * fort; {@code wreck} is true where the aim is to destroy it rather than hold it.
     */
    public record Task(int squadId, ObjectiveAssignment assignment, Role role, DefenceStructureFact structure,
                       String reason, boolean wreck) {
        public Task(int squadId, ObjectiveAssignment assignment, Role role, DefenceStructureFact structure) {
            this(squadId, assignment, role, structure, "structure work", false);
        }
    }

    /** Whether an assignment is one of this class's orders: an attack move or area defence ending within a known structure's reach. */
    public static boolean isTask(ObjectiveAssignment assignment, List<DefenceStructureFact> structures) {
        if (assignment == null || structures.isEmpty()) return false;
        if (assignment.kind() != AssignmentKind.ATTACK_MOVE && assignment.kind() != AssignmentKind.DEFEND_AREA) {
            return false;
        }
        for (DefenceStructureFact structure : structures) {
            if (near(assignment.targetCellX(), assignment.targetCellY(), structure)) return true;
        }
        return false;
    }

    /**
     * Chooses the squads to detach and their orders.
     *
     * @param perspective the planning side
     * @param structures  the structures this side knows of
     * @param candidates  squads that may be detached
     */
    public static List<Task> plan(Faction perspective, List<DefenceStructureFact> structures,
                                  List<Candidate> candidates, CommandTopology topology) {
        List<Task> tasks = new ArrayList<>();
        if (structures.isEmpty() || candidates.isEmpty()) return tasks;
        List<Wanted> wanted = new ArrayList<>();
        Map<Integer, List<DefenceStructureFact>> forts = new TreeMap<>();
        for (DefenceStructureFact structure : structures) {
            if (structure.ofFort()) {
                forts.computeIfAbsent(structure.fortId(), id -> new ArrayList<>()).add(structure);
                continue;
            }
            Role role = roleFor(perspective, structure);
            if (role != null) wanted.add(new Wanted(role, structure, 0, 1, false, role.name() + " structure"));
        }
        // A fort is one defence with several aspects: which of them is work, and in what order, is a choice made for
        // the effect wanted rather than one task per structure.
        for (List<DefenceStructureFact> fort : forts.values()) {
            for (FortTactics.Pick pick : FortTactics.choose(perspective, fort, candidates)) {
                wanted.add(new Wanted(pick.role(), pick.structure(), pick.rank(), pick.squads(), pick.wreck(),
                        "fort " + pick.structure().fortId() + " " + pick.structure().family().name().toLowerCase(Locale.ROOT)
                                + ": " + pick.reason()));
            }
        }
        wanted.sort(Comparator.<Wanted>comparingInt(w -> w.role.ordinal())
                .thenComparingInt(w -> w.rank)
                .thenComparingInt(w -> w.structure.cellX())
                .thenComparingInt(w -> w.structure.cellY()));
        Set<Integer> used = new HashSet<>();
        for (Wanted want : wanted) {
            for (int copy = 0; copy < want.squads && tasks.size() < MAX_TASKS; copy++) {
                Task task = incumbentTask(want, candidates, used, topology);
                if (task == null) task = nearestTask(want, candidates, used, topology);
                if (task == null) break;
                used.add(task.squadId());
                tasks.add(task);
            }
        }
        return tasks;
    }

    private record Wanted(Role role, DefenceStructureFact structure, int rank, int squads, boolean wreck,
                          String reason) {}

    private static Role roleFor(Faction perspective, DefenceStructureFact structure) {
        if (perspective.friendlyTo(structure.holder())) {
            return structure.threatened() ? Role.DEFEND : null;
        }
        if (!perspective.hostileTo(structure.holder())) return null;
        return perspective.friendlyTo(structure.originalHolder()) ? Role.RETAKE : Role.TAKE;
    }

    private static Task incumbentTask(Wanted want, List<Candidate> candidates, Set<Integer> used,
                                      CommandTopology topology) {
        AssignmentKind kind = kindFor(want.role);
        for (Candidate candidate : candidates) {
            ObjectiveAssignment held = candidate.incumbent();
            if (used.contains(candidate.squadId()) || held == null || held.kind() != kind
                    || (want.wreck && !candidate.demolition())
                    || !near(held.targetCellX(), held.targetCellY(), want.structure)) continue;
            if (!topology.reachable(candidate.anchorCellX(), candidate.anchorCellY(),
                    held.targetCellX(), held.targetCellY())) continue;
            return new Task(candidate.squadId(), held.withWreckAuthorised(want.wreck), want.role, want.structure,
                    want.reason, want.wreck);
        }
        return null;
    }

    private static Task nearestTask(Wanted want, List<Candidate> candidates, Set<Integer> used,
                                    CommandTopology topology) {
        Candidate best = null;
        int[] bestCell = null;
        long bestGap = Long.MAX_VALUE;
        for (Candidate candidate : candidates) {
            if (used.contains(candidate.squadId()) || (want.wreck && !candidate.demolition())) continue;
            long dx = candidate.anchorCellX() - want.structure.cellX();
            long dy = candidate.anchorCellY() - want.structure.cellY();
            long gap = dx * dx + dy * dy;
            if (gap > bestGap || (gap == bestGap && best != null && candidate.squadId() > best.squadId())) continue;
            int[] cell = approachCell(candidate, want.structure, topology);
            if (cell == null) continue;
            best = candidate;
            bestCell = cell;
            bestGap = gap;
        }
        if (best == null) return null;
        ObjectiveAssignment assignment = want.role == Role.DEFEND
                ? ObjectiveAssignment.defendArea(best.squadId(), bestCell[0], bestCell[1],
                        Math.max(2, want.structure.reachCells()))
                : ObjectiveAssignment.attackMove(best.squadId(), bestCell[0], bestCell[1])
                        .withWreckAuthorised(want.wreck);
        return new Task(best.squadId(), assignment, want.role, want.structure, want.reason, want.wreck);
    }

    private static AssignmentKind kindFor(Role role) {
        return role == Role.DEFEND ? AssignmentKind.DEFEND_AREA : AssignmentKind.ATTACK_MOVE;
    }

    /**
     * A walkable cell inside the structure's reach that the squad can get to: the nearest to the squad on the smallest
     * ring that has one. The structure's own footprint is sealed ground, so the order is to its doorstep.
     */
    private static int[] approachCell(Candidate squad, DefenceStructureFact structure, CommandTopology topology) {
        int reach = Math.max(2, structure.reachCells());
        for (int ring = 2; ring <= reach; ring++) {
            int[] best = null;
            long bestGap = Long.MAX_VALUE;
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
                    int x = structure.cellX() + dx;
                    int y = structure.cellY() + dy;
                    if (!topology.inBounds(x, y) || !topology.isWalkable(x, y)
                            || !topology.reachable(squad.anchorCellX(), squad.anchorCellY(), x, y)) continue;
                    long gx = x - squad.anchorCellX();
                    long gy = y - squad.anchorCellY();
                    long gap = gx * gx + gy * gy;
                    if (gap < bestGap) {
                        bestGap = gap;
                        best = new int[] {x, y};
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static boolean near(int cellX, int cellY, DefenceStructureFact structure) {
        int reach = Math.max(2, structure.reachCells());
        return cellX >= 0 && cellY >= 0
                && Math.max(Math.abs(cellX - structure.cellX()), Math.abs(cellY - structure.cellY())) <= reach;
    }
}
