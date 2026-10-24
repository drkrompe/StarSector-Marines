package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Mans the line ({@code cordon-nouns.md}, Field redoubt, Garrison): every
 * field redoubt's wall is dressed the way the colony's civil-defence compound
 * is (a gun tower beside each gate, slits along the runs, floodlights), and
 * three allied wall teams under garrison authority stand at the slits, the
 * wall that faces the front first.
 */
public final class CordonGarrisons {

    /** Who holds the redoubts, as the command log names it. */
    public static final String ISSUER = "cordon-garrison";
    /** Wall teams per redoubt. */
    static final int TEAMS = 3;
    /** How far from its plan seed a redoubt's wall is looked for. */
    static final int WALL_REACH = 30;
    /** Cells to the rear the main gate is looked for toward: the way relief comes. */
    private static final int REAR_REACH = 60;

    private CordonGarrisons() {}

    /** Mans every redoubt of the plan, west to east; a redoubt with no wall found is left out. */
    public static List<CordonRedoubt> man(BattleSimulation sim, PrecinctPlan plan, RiskLevel risk, Random rng) {
        List<CordonRedoubt> out = new ArrayList<>();
        List<Precinct> redoubts = CordonPlan.redoubts(plan);
        int front = CordonFront.frontward(plan);
        for (int i = 0; i < redoubts.size(); i++) {
            CordonRedoubt redoubt = man(sim, redoubts.get(i), i + 1, front, risk, rng);
            if (redoubt != null) out.add(redoubt);
        }
        return out;
    }

    /**
     * Where redoubt {@code place}'s gun towers will stand once its wall is dressed, read off the generated map
     * before the battle: the plan {@link #man} dresses it by. Empty when no closed wall stands there.
     */
    public static List<int[]> towers(NavigationGrid grid, CellTopology topology, List<Doodad> doodads, Precinct place,
                                     int front) {
        int[] box = wallBox(topology, place);
        if (box == null) return List.of();
        int centreX = (box[0] + box[2]) / 2, centreY = (box[1] + box[3]) / 2;
        int[] yard = nearestOpen(grid, centreX, centreY, box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1);
        if (yard == null) return List.of();
        return CivilDefenceCompound.gunTowers(grid, topology, doodads, centreX, centreY, yard[0], yard[1],
                centreX, centreY - front * REAR_REACH, WALL_REACH);
    }

    private static CordonRedoubt man(BattleSimulation sim, Precinct place, int number, int front,
                                     RiskLevel risk, Random rng) {
        int[] box = wallBox(sim.getTopology(), place);
        if (box == null) return null;
        NavigationGrid grid = sim.getGrid();
        int centreX = (box[0] + box[2]) / 2, centreY = (box[1] + box[3]) / 2;
        int[] yard = nearestOpen(grid, centreX, centreY, box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1);
        if (yard == null) return null;
        CivilDefenceCompound.Dressed wall = CivilDefenceCompound.dress(sim, centreX, centreY, yard[0], yard[1],
                centreX, centreY - front * REAR_REACH, WALL_REACH);
        // A wall the dressing cannot read as closed (a gap wider than a gate) still stands and is still
        // manned: its teams stand inside it with no towers or slits.
        int left = wall != null ? wall.left() + 1 : box[0] + 1, top = wall != null ? wall.top() + 1 : box[1] + 1;
        int right = wall != null ? wall.right() - 1 : box[2] - 1, bottom = wall != null ? wall.bottom() - 1 : box[3] - 1;
        List<Long> guns = new ArrayList<>();
        for (int[] gun : wall != null ? wall.guns() : List.<int[]>of()) {
            guns.add(ColonyMilitia.seatEmplacement(sim, gun, "Redoubt " + number + " gun tower " + (guns.size() + 1)));
        }
        // The slits facing the front first, then along the flanks: where the swarm comes at the wall.
        int frontRow = front > 0 ? bottom : top;
        List<int[]> posts = new ArrayList<>(wall != null ? wall.posts() : List.of());
        posts.sort(Comparator.<int[]>comparingInt(c -> Math.abs(c[1] - frontRow))
                .thenComparingInt(c -> Math.abs(c[0] - centreX)).thenComparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        int seats = TEAMS * ColonyMilitia.POST_SIZE;
        // A short wall seats the rest of its teams in the yard, nearest the front first.
        for (int row = 0; row <= bottom - top && posts.size() < seats; row++) {
            int y = front > 0 ? bottom - row : top + row;
            for (int x = left; x <= right && posts.size() < seats; x++) {
                if (!grid.isWalkable(x, y) || grid.isDoorway(x, y) || taken(posts, x, y)) continue;
                posts.add(new int[]{x, y});
            }
        }
        if (posts.size() < seats) return null;
        List<Integer> teams = new ArrayList<>();
        int radius = Math.max(right - left, bottom - top) / 2 + 2;
        for (int t = 0; t < TEAMS; t++) {
            // Interleaved, so each team holds the whole front wall rather than one end of it.
            List<int[]> cells = new ArrayList<>();
            for (int k = t; k < seats; k += TEAMS) cells.add(posts.get(k));
            int squad = ColonyMilitia.seatTeam(sim, cells, "Redoubt " + number + " wall team " + (t + 1), risk, rng);
            // A wall's posts are no run: the team holds the ground and fights from the slits.
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, centreX, centreY, radius),
                    CommandAuthority.GARRISON, ISSUER, "hold redoubt " + number);
            teams.add(squad);
        }
        return new CordonRedoubt(number, left, top, right, bottom, teams, guns, seats);
    }

    /**
     * Seats an allied gun in every nest the picket courses laid
     * ({@code cordon-nouns.md}, Picket line): the garrison's own light guns,
     * not the map's. Returns the guns, west to east by course.
     */
    public static List<Long> manPickets(BattleSimulation sim, LaidLayout layout) {
        List<Long> guns = new ArrayList<>();
        for (Laid laid : layout.pieces()) {
            if (laid.kind() != Laid.Kind.LINE || !laid.name().startsWith(CordonPlan.PICKET_LINE)) continue;
            String course = laid.name().substring(CordonPlan.PICKET_LINE.length());
            int k = 0;
            for (int[] nest : laid.nests()) {
                guns.add(ColonyMilitia.seatEmplacement(sim, nest, "Picket " + course + " nest " + (++k)));
            }
        }
        return guns;
    }

    /** The bounds of a redoubt's perimeter wall, {@code [left, top, right, bottom]}, or {@code null}. */
    static int[] wallBox(CellTopology topology, Precinct place) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (int y = Math.max(0, place.seedY() - WALL_REACH);
             y <= Math.min(topology.getHeight() - 1, place.seedY() + WALL_REACH); y++) {
            for (int x = Math.max(0, place.seedX() - WALL_REACH);
                 x <= Math.min(topology.getWidth() - 1, place.seedX() + WALL_REACH); x++) {
                if (!topology.isWall(x, y) || !InstallationWalls.PERIMETER.equals(topology.getSurfaceId(x, y))) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        if (right - left < 4 || bottom - top < 4) return null;
        return new int[]{left, top, right, bottom};
    }

    private static boolean taken(List<int[]> cells, int x, int y) {
        for (int[] c : cells) if (c[0] == x && c[1] == y) return true;
        return false;
    }

    /** The open cell inside the bounds nearest {@code (x, y)}. */
    private static int[] nearestOpen(NavigationGrid grid, int x, int y, int left, int top, int right, int bottom) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int cy = top; cy <= bottom; cy++) {
            for (int cx = left; cx <= right; cx++) {
                if (!grid.isWalkable(cx, cy) || grid.isDoorway(cx, cy)) continue;
                int distance = Math.abs(cx - x) + Math.abs(cy - y);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best;
    }
}
