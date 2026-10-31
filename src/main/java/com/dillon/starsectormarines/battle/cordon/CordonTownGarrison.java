package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.evacuation.FortifiedWorks;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Holds the Road's cut-off town ({@code cordon-nouns.md}, Cut-off town): its compound's wall dressed as a redoubt's
 * is (a gun tower beside each gate, firing slits along the runs, floodlights), and its defenders at the posts behind
 * the slits, the side facing the front first: what is left of the town militia (two teams, the ones beat R4 rides
 * out) and the town guard (three more), allied, under garrison authority, never in the player's command pool. Enough
 * to hold the opening and the first convoy's loading, not to outlast the tides alone: nobody reinforces the town,
 * so each tide bleeds it and the company is wanted there later.
 *
 * <p>The staging area inside the gate the convoys load at ({@link CordonStagingArea}) is found once the wall is
 * dressed and left clear: the trapped townspeople wait there and walk out to the trucks, and the defenders stand
 * round it.
 */
public final class CordonTownGarrison {

    /** The town militia's teams: what is left of the colony militia, ridden out by beat R4. */
    static final int MILITIA_TEAMS = 2;
    /** The town guard's teams, who stay at the wall whatever the militia do. */
    static final int GUARD_TEAMS = 3;
    /** Cells off the staging area a defender is seated at the nearest: round it, never on it. */
    static final int STAGING_CLEAR = 1;
    /** Cells round the corridor's end no defender is seated on: where the trucks pull up. */
    static final int ROAD_CLEAR = 3;
    /** How far from its plan seed the compound's wall is looked for, as a redoubt's. */
    private static final int WALL_REACH = CordonGarrisons.WALL_REACH;
    /** Cells into the compound past the gate the defenders stand where no wall could be read. */
    private static final int FALLBACK_DEPTH = 6;
    /** A team's cluster where no wall could be read: room for its four and a little more. */
    private static final int FALLBACK_CLUSTER = 6;

    private CordonTownGarrison() { }

    /** The town held, and the militia's squads among its defenders (for beat R4). */
    public record Held(CordonTown town, List<Integer> militia) {
        /** The staging area inside the loading gate. */
        public CordonStagingArea staging() { return town.staging(); }
    }

    /**
     * Dresses and mans the compound round {@code compound}, its loading gate the one nearest {@code gate} (the
     * corridor's last cell). Where no wall can be read the defenders stand in the yard past the gate as the militia
     * did before; never {@code null}.
     *
     * @param front +1 where the front is high y, -1 where it is low y
     */
    public static Held man(BattleSimulation sim, Precinct compound, int[] gate, int front, RiskLevel risk, Random rng) {
        NavigationGrid grid = sim.getGrid();
        int[] box = compound == null ? null : CordonGarrisons.perimeterBox(sim.getTopology(), compound);
        int teams = MILITIA_TEAMS + GUARD_TEAMS;
        int seats = teams * ColonyMilitia.POST_SIZE;
        if (box != null) {
            int centreX = (box[0] + box[2]) / 2, centreY = (box[1] + box[3]) / 2;
            int[] yard = CordonGarrisons.nearestOpen(grid, centreX, centreY, box[0] + 1, box[1] + 1, box[2] - 1,
                    box[3] - 1);
            if (yard != null) {
                CivilDefenceCompound.Dressed wall = CivilDefenceCompound.dress(sim, centreX, centreY, yard[0], yard[1],
                        gate[0], gate[1], WALL_REACH);
                int left = wall != null ? wall.left() + 1 : box[0] + 1, top = wall != null ? wall.top() + 1 : box[1] + 1;
                int right = wall != null ? wall.right() - 1 : box[2] - 1;
                int bottom = wall != null ? wall.bottom() - 1 : box[3] - 1;
                List<Long> guns = new ArrayList<>();
                for (int[] gun : wall != null ? wall.guns() : List.<int[]>of()) {
                    guns.add(ColonyMilitia.seatEmplacement(sim, gun, "Town gun tower " + (guns.size() + 1)));
                }
                int frontRow = front > 0 ? bottom : top;
                int[] loading = wall != null ? new int[]{wall.gateX(), wall.gateY()} : gate;
                List<int[]> open = new ArrayList<>();
                for (int row = 0; row <= bottom - top; row++) {
                    int y = front > 0 ? bottom - row : top + row;
                    for (int x = left; x <= right; x++) {
                        if (grid.isWalkable(x, y) && !grid.isDoorway(x, y)) open.add(new int[]{x, y});
                    }
                }
                CordonStagingArea staging = CordonStagingArea.of(
                        (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y),
                        left, top, right, bottom, loading[0], loading[1]);
                List<int[]> posts = seats(wall != null ? wall.posts() : List.of(), open, staging, gate, frontRow,
                        centreX, seats);
                FortifiedWorks.mark(grid, left - 1, top - 1, right + 1, bottom + 1);
                return seat(sim, posts, left, top, right, bottom, loading, staging, guns, risk, rng);
            }
        }
        // No wall to read: the staging area is the ground round the road's end, and the defenders stand past it.
        int reach = CordonStagingArea.REACH;
        CordonStagingArea staging = CordonStagingArea.of(
                (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y),
                gate[0] - reach, gate[1] - reach, gate[0] + reach, gate[1] + reach, gate[0], gate[1]);
        List<int[]> posts = new ArrayList<>();
        for (int t = 0; t < teams; t++) {
            List<int[]> cells = CordonGround.clusterNear(sim, gate[0],
                    gate[1] + front * (reach + FALLBACK_DEPTH + t * 3), FALLBACK_CLUSTER);
            for (int i = 0; i < Math.min(ColonyMilitia.POST_SIZE, cells.size()); i++) posts.add(cells.get(i));
        }
        int l = gate[0], t = gate[1], r = gate[0], b = gate[1];
        for (int[] p : posts) {
            l = Math.min(l, p[0]);
            t = Math.min(t, p[1]);
            r = Math.max(r, p[0]);
            b = Math.max(b, p[1]);
        }
        return seat(sim, posts, l, t, r, b, gate, staging, List.of(), risk, rng);
    }

    /**
     * Seats the defenders on {@code posts}, a team of four to each run of them in turn, interleaved so each team
     * holds the whole front wall rather than one end of it, and tells each to hold the compound under garrison
     * authority. The militia's teams first.
     */
    private static Held seat(BattleSimulation sim, List<int[]> posts, int left, int top, int right, int bottom,
                             int[] gate, CordonStagingArea staging, List<Long> guns, RiskLevel risk, Random rng) {
        int teams = Math.min(MILITIA_TEAMS + GUARD_TEAMS, posts.size() / ColonyMilitia.POST_SIZE);
        int centreX = (left + right) / 2, centreY = (top + bottom) / 2;
        int radius = Math.max(right - left, bottom - top) / 2 + 2;
        List<Integer> squads = new ArrayList<>();
        List<Integer> militia = new ArrayList<>();
        int garrison = 0;
        for (int t = 0; t < teams; t++) {
            List<int[]> cells = new ArrayList<>();
            for (int k = t; k < teams * ColonyMilitia.POST_SIZE; k += teams) cells.add(posts.get(k));
            boolean isMilitia = t < MILITIA_TEAMS;
            String name = isMilitia ? "Town militia " + (t + 1) : "Town guard " + (t - MILITIA_TEAMS + 1);
            int squad = ColonyMilitia.seatTeam(sim, cells, name, risk, rng);
            // A wall's posts are no run: the team holds the ground and fights from the slits.
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, centreX, centreY, radius),
                    CommandAuthority.GARRISON, CordonGarrisons.ISSUER, "hold the town");
            squads.add(squad);
            if (isMilitia) militia.add(squad);
            garrison += sim.squadMemberCount(squad);
        }
        return new Held(new CordonTown(left, top, right, bottom, gate[0], gate[1], staging, squads, guns, garrison),
                militia);
    }

    /**
     * Where the defenders are seated, at most {@code seats}: the posts behind the wall's slits, the side facing the
     * front first and then out along it from the middle, then the open yard from the front row back while seats
     * remain, never on or beside the staging area ({@link #STAGING_CLEAR}) nor within {@link #ROAD_CLEAR} cells of
     * the corridor's end. Pure.
     *
     * @param wallPosts the posts the dressing laid behind the slits
     * @param yard      the open ground inside the wall, front row first
     * @param frontRow  the row inside the wall nearest the front
     */
    static List<int[]> seats(List<int[]> wallPosts, List<int[]> yard, CordonStagingArea staging, int[] corridorEnd,
                             int frontRow, int centreX, int seats) {
        List<int[]> posts = new ArrayList<>();
        for (int[] p : wallPosts) if (!clear(p, staging, corridorEnd)) posts.add(p);
        posts.sort(Comparator.<int[]>comparingInt(c -> Math.abs(c[1] - frontRow))
                .thenComparingInt(c -> Math.abs(c[0] - centreX)).thenComparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        if (posts.size() > seats) posts = new ArrayList<>(posts.subList(0, seats));
        for (int[] c : yard) {
            if (posts.size() >= seats) break;
            if (clear(c, staging, corridorEnd) || taken(posts, c)) continue;
            posts.add(c);
        }
        return posts;
    }

    /** Whether a cell is kept clear: the staging area and the ground beside it, or where the trucks pull up. */
    private static boolean clear(int[] c, CordonStagingArea staging, int[] corridorEnd) {
        return staging.near(c[0], c[1], STAGING_CLEAR) || corridorEnd != null
                && Math.max(Math.abs(c[0] - corridorEnd[0]), Math.abs(c[1] - corridorEnd[1])) <= ROAD_CLEAR;
    }

    private static boolean taken(List<int[]> cells, int[] c) {
        for (int[] p : cells) if (p[0] == c[0] && p[1] == c[1]) return true;
        return false;
    }
}
