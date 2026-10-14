package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who holds which {@link FightingPositionIndex fighting-position} cell. Claims are by marine,
 * exclusive across squads, and decided once per tick in the serial phase between the squad
 * replan and the parallel unit dispatch ({@link #tick}); during dispatch the maps are only
 * read, so the answer a marine gets does not depend on which worker asks first. The maps are
 * immutable snapshots published through volatile fields, and the registry belongs to one
 * simulation, so concurrent simulations in a JVM share nothing.
 *
 * <p>Only squads whose current plan step is a {@link DefendArea} on their standing player or
 * commander area order take part. Each squad's marines fill the free position cells nearest
 * the area's centre, preferring cells with cover toward the squad's believed threat, grown
 * outward from one anchor cell so the squad takes a contiguous block and fire teams (adjacent
 * in member order) stand together. A marine whose previous cell is still valid and unclaimed
 * keeps it, because moving would reset the dig-in clock; retention is settled for every squad
 * before anyone is given a new cell, so a lower-numbered squad cannot take a cell a neighbour
 * is already standing in. Marines left without a cell get no entry and use the ordinary
 * search. With {@link DigInSystem#isEnabled()} off, or no positions on the map, no claims
 * exist and {@link DefendArea} behaves exactly as it did.
 *
 * <p>See the fighting-positions section of {@code ai-nouns.md}.
 */
public final class FightingPositionClaims {

    /** What the allocator needs to know about the ground; a seam so it can be tested on a bare grid. */
    public interface Probe {
        boolean walkable(int x, int y);

        /** Cover at the cell against fire from {@code (fromDx, fromDy)}; {@code (0, 0)} asks for omnidirectional cover. */
        int cover(int x, int y, int fromDx, int fromDy);
    }

    /** One squad's ordered circle and the threat it believes in ({@code threatX < 0}: none). */
    public record Order(int centerX, int centerY, int radius, int threatX, int threatY) {
        boolean hasThreat() {
            return threatX >= 0 && threatY >= 0;
        }

        boolean inside(int x, int y) {
            int dx = x - centerX;
            int dy = y - centerY;
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    private volatile Map<Long, int[]> byMember = Map.of();
    private volatile Map<Integer, Long> byCell = Map.of();

    /** The cell this marine holds, or {@code null}. The array is shared; do not modify it. */
    public int[] cellOf(long member) {
        return byMember.get(member);
    }

    /** Whether a marine other than {@code member} holds this cell. */
    public boolean isClaimedByOther(int x, int y, int width, long member) {
        Long holder = byCell.get(y * width + x);
        return holder != null && holder != member;
    }

    public boolean isEmpty() {
        return byMember.isEmpty();
    }

    /** Recomputes every claim. Serial phase only: after the squad replan, before unit dispatch. */
    public void tick(BattleView sim) {
        FightingPositionIndex index = DigInSystem.isEnabled() ? sim.fightingPositions() : null;
        if (index == null || index.runCount() == 0) {
            publish(Map.of(), Map.of());
            return;
        }
        NavigationGrid grid = sim.getGrid();
        int width = grid.getWidth();
        Probe probe = new Probe() {
            @Override public boolean walkable(int x, int y) {
                return grid.inBounds(x, y) && grid.isWalkable(x, y);
            }

            @Override public int cover(int x, int y, int fromDx, int fromDy) {
                if (fromDx == 0 && fromDy == 0) {
                    return grid.getCoverAt(x, y) + sim.getDoodadCoverAt(x, y);
                }
                return grid.getCoverAt(x, y, fromDx, fromDy)
                        + sim.getDoodadCoverAt(x, y, fromDx, fromDy);
            }
        };

        List<Squad> squads = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.isDroneSquad() || squad.isMechSquad() || squad.aliveMembers <= 0) continue;
            if (defendArea(squad) != null) squads.add(squad);
        }
        if (squads.isEmpty()) {
            publish(Map.of(), Map.of());
            return;
        }
        squads.sort(Comparator.comparingInt(s -> s.id));

        Map<Long, int[]> previous = byMember;
        int n = squads.size();
        long[][] members = new long[n][];
        Order[] orders = new Order[n];
        int[][] cells = new int[n][];
        Set<Integer> claimed = new HashSet<>();
        for (int s = 0; s < n; s++) {
            Squad squad = squads.get(s);
            DefendArea area = defendArea(squad);
            SquadContactPicture picture = squad.contactPicture;
            boolean threat = picture != null && picture.hasContacts()
                    && picture.primaryCellX() >= 0 && picture.primaryCellY() >= 0;
            orders[s] = new Order(area.centerX(), area.centerY(), area.radius(),
                    threat ? picture.primaryCellX() : -1, threat ? picture.primaryCellY() : -1);
            List<Long> available = new ArrayList<>();
            for (int i = 0, count = sim.squadMemberCount(squad.id); i < count; i++) {
                long member = sim.squadMemberAt(squad.id, i);
                if (squad.availableToPlan(member, sim)) available.add(member);
            }
            int size = available.size();
            members[s] = new long[size];
            cells[s] = new int[size];
            int[] before = new int[size];
            for (int i = 0; i < size; i++) {
                long member = available.get(i);
                members[s][i] = member;
                int[] held = previous.get(member);
                before[i] = held == null ? -1 : held[1] * width + held[0];
            }
            retain(index, width, orders[s], probe, before, cells[s], claimed);
        }
        for (int s = 0; s < n; s++) {
            fill(index, width, orders[s], probe, cells[s], claimed);
        }

        Map<Long, int[]> nextByMember = new HashMap<>();
        Map<Integer, Long> nextByCell = new HashMap<>();
        for (int s = 0; s < n; s++) {
            for (int i = 0; i < members[s].length; i++) {
                int cell = cells[s][i];
                if (cell < 0) continue;
                nextByMember.put(members[s][i], new int[]{cell % width, cell / width});
                nextByCell.put(cell, members[s][i]);
            }
        }
        publish(nextByMember, nextByCell);
    }

    private void publish(Map<Long, int[]> members, Map<Integer, Long> cells) {
        byMember = members;
        byCell = cells;
    }

    /** The {@link DefendArea} the squad is executing on its standing area order, or {@code null}. */
    private static DefendArea defendArea(Squad squad) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != AssignmentKind.DEFEND_AREA
                || assignment.targetRadiusCells() < 1) {
            return null;
        }
        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) return null;
        SquadPlan.Step step = plan.currentStep();
        if (step == null || !(step.action instanceof DefendArea area)) return null;
        return area.centerX() == assignment.targetCellX()
                && area.centerY() == assignment.targetCellY()
                && area.radius() == assignment.targetRadiusCells() ? area : null;
    }

    /**
     * Keeps each member's previous cell ({@code cell index = y * width + x}, or -1) when it is
     * still a walkable position cell inside the circle, still has cover toward the believed
     * threat, and nobody else has it. Writes survivors into {@code cells} and {@code claimed}.
     */
    static void retain(FightingPositionIndex index, int width, Order order, Probe probe,
                       int[] previous, int[] cells, Set<Integer> claimed) {
        for (int i = 0; i < cells.length; i++) {
            cells[i] = -1;
            int cell = previous[i];
            if (cell < 0 || claimed.contains(cell)) continue;
            int x = cell % width;
            int y = cell / width;
            if (!index.isFightingPositionCell(x, y) || !order.inside(x, y) || !probe.walkable(x, y)) continue;
            if (order.hasThreat() && probe.cover(x, y, order.threatX() - x, order.threatY() - y) <= 0) continue;
            cells[i] = cell;
            claimed.add(cell);
        }
    }

    /**
     * Gives every member whose {@code cells} entry is -1 a free position cell, if there are
     * enough; the rest stay -1 (overflow). Candidates are covered cells before uncovered ones,
     * then nearest an anchor: the squad's first retained cell, else the best cell by cover and
     * nearness to the circle's centre. The chosen block is handed out in member order along a
     * nearest-neighbour chain that starts at one end of it.
     */
    static void fill(FightingPositionIndex index, int width, Order order, Probe probe,
                     int[] cells, Set<Integer> claimed) {
        int need = 0;
        int anchor = -1;
        for (int cell : cells) {
            if (cell < 0) need++;
            else if (anchor < 0) anchor = cell;
        }
        if (need == 0) return;

        List<int[]> candidates = new ArrayList<>();
        for (int y = order.centerY() - order.radius(); y <= order.centerY() + order.radius(); y++) {
            for (int x = order.centerX() - order.radius(); x <= order.centerX() + order.radius(); x++) {
                if (!order.inside(x, y) || !index.isFightingPositionCell(x, y)
                        || !probe.walkable(x, y)) continue;
                int cell = y * width + x;
                if (claimed.contains(cell)) continue;
                int cover = order.hasThreat()
                        ? probe.cover(x, y, order.threatX() - x, order.threatY() - y)
                        : probe.cover(x, y, 0, 0);
                candidates.add(new int[]{cell, cover});
            }
        }
        if (candidates.isEmpty()) return;

        int anchorX;
        int anchorY;
        if (anchor >= 0) {
            anchorX = anchor % width;
            anchorY = anchor / width;
        } else {
            int[] best = candidates.get(0);
            for (int[] candidate : candidates) {
                if (candidate[1] > best[1]
                        || (candidate[1] == best[1] && distanceToCentre(candidate[0], width, order)
                        < distanceToCentre(best[0], width, order))) {
                    best = candidate;
                }
            }
            anchorX = best[0] % width;
            anchorY = best[0] / width;
        }

        final int ax = anchorX;
        final int ay = anchorY;
        candidates.sort(Comparator
                .<int[]>comparingInt(c -> c[1] > 0 ? 0 : 1)
                .thenComparingInt(c -> distanceSq(c[0] % width, c[0] / width, ax, ay))
                .thenComparingInt(c -> c[0]));
        List<int[]> chosen = new ArrayList<>(candidates.subList(0, Math.min(need, candidates.size())));

        // Walk the block from one end so consecutive members are neighbours, not from the
        // anchor in the middle, whose nearest-neighbour hops jump from side to side.
        long sumX = 0;
        long sumY = 0;
        for (int[] c : chosen) {
            sumX += c[0] % width;
            sumY += c[0] / width;
        }
        int size = chosen.size();
        int curX = anchorX;
        int curY = anchorY;
        long farthest = -1;
        for (int[] c : chosen) {
            long dx = (long) (c[0] % width) * size - sumX;
            long dy = (long) (c[0] / width) * size - sumY;
            long d = dx * dx + dy * dy;
            if (d > farthest) {
                farthest = d;
                curX = c[0] % width;
                curY = c[0] / width;
            }
        }
        int member = 0;
        while (!chosen.isEmpty()) {
            int pick = 0;
            int pickDistance = Integer.MAX_VALUE;
            for (int i = 0; i < chosen.size(); i++) {
                int cell = chosen.get(i)[0];
                int d = distanceSq(cell % width, cell / width, curX, curY);
                if (d < pickDistance || (d == pickDistance && cell < chosen.get(pick)[0])) {
                    pick = i;
                    pickDistance = d;
                }
            }
            int cell = chosen.remove(pick)[0];
            curX = cell % width;
            curY = cell / width;
            while (cells[member] >= 0) member++;
            cells[member] = cell;
            claimed.add(cell);
        }
    }

    private static int distanceToCentre(int cell, int width, Order order) {
        return distanceSq(cell % width, cell / width, order.centerX(), order.centerY());
    }

    private static int distanceSq(int ax, int ay, int bx, int by) {
        int dx = ax - bx;
        int dy = ay - by;
        return dx * dx + dy * dy;
    }
}
