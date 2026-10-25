package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
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
 * Mans the line ({@code cordon-nouns.md}, Field redoubt, Garrison): a redoubt
 * built as the allies' compound has its wall dressed the way the colony's
 * civil-defence compound is (a gun tower beside each gate, slits along the
 * runs, floodlights); one built from what the country had standing
 * ({@link CordonConstruction}) has a gun seated in each nest its builder
 * recorded, and its posts are its loopholes (the windows its buildings face
 * out of) and its sandbags. Either way three allied wall teams under garrison
 * authority stand at the posts, the side that faces the front first.
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
    public static List<CordonRedoubt> man(BattleSimulation sim, MapResult map, PrecinctPlan plan, RiskLevel risk,
                                          Random rng) {
        List<CordonRedoubt> out = new ArrayList<>();
        List<Precinct> redoubts = CordonPlan.redoubts(plan);
        int front = CordonFront.frontward(plan);
        for (int i = 0; i < redoubts.size(); i++) {
            CordonConstruction built = CordonConstructions.redoubt(map.layout, i + 1);
            CordonRedoubt redoubt = built.generated()
                    ? man(sim, redoubts.get(i), i + 1, front, risk, rng)
                    : manWorks(sim, map, i + 1, built, front, risk, rng);
            if (redoubt != null) out.add(redoubt);
        }
        return out;
    }

    /**
     * Where redoubt {@code number}'s guns will stand, read off the generated map before the battle: a compound's gun
     * towers once its wall is dressed (the plan {@link #man} dresses it by), or the nests a built redoubt's builder
     * recorded. Empty when neither stands there.
     */
    public static List<int[]> towers(MapResult map, Precinct place, int number, int front) {
        CordonConstruction built = CordonConstructions.redoubt(map.layout, number);
        if (!built.generated()) {
            Laid works = map.layout.find(CordonConstructions.WORKS + number);
            return works == null ? List.of() : works.nests();
        }
        return towers(map.grid, map.topology, map.doodads, place, front);
    }

    private static List<int[]> towers(NavigationGrid grid, CellTopology topology, List<Doodad> doodads, Precinct place,
                                      int front) {
        int[] box = perimeterBox(topology, place);
        if (box == null) return List.of();
        int centreX = (box[0] + box[2]) / 2, centreY = (box[1] + box[3]) / 2;
        int[] yard = nearestOpen(grid, centreX, centreY, box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1);
        if (yard == null) return List.of();
        return CivilDefenceCompound.gunTowers(grid, topology, doodads, centreX, centreY, yard[0], yard[1],
                centreX, centreY - front * REAR_REACH, WALL_REACH);
    }

    private static CordonRedoubt man(BattleSimulation sim, Precinct place, int number, int front,
                                     RiskLevel risk, Random rng) {
        int[] box = perimeterBox(sim.getTopology(), place);
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
        seatFlak(sim, number, guns, posts, left, top, right, bottom, centreX, centreY);
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
     * Mans a redoubt built from what the country had standing: a gun seated in each nest its builder recorded, and
     * three wall teams at its posts ({@link #worksPosts}), the side facing the front first, the rest of the seats in
     * its yard as for a short wall.
     */
    private static CordonRedoubt manWorks(BattleSimulation sim, MapResult map, int number, CordonConstruction built,
                                          int front, RiskLevel risk, Random rng) {
        Laid works = map.layout.find(CordonConstructions.WORKS + number);
        if (works == null || works.cells().size() != 2) return null;
        NavigationGrid grid = sim.getGrid();
        int left = works.cells().get(0)[0], top = works.cells().get(0)[1];
        int right = works.cells().get(1)[0], bottom = works.cells().get(1)[1];
        int centreX = (left + right) / 2, centreY = (top + bottom) / 2;
        List<Long> guns = new ArrayList<>();
        for (int[] gun : works.nests()) {
            if (!grid.isWalkable(gun[0], gun[1])) continue;
            guns.add(ColonyMilitia.seatEmplacement(sim, gun, "Redoubt " + number + " gun " + (guns.size() + 1)));
        }
        int seats = TEAMS * ColonyMilitia.POST_SIZE;
        List<int[]> posts = worksPosts(grid, map.doodads, works.nests(), left, top, right, bottom, front, seats);
        if (posts.size() < seats) return null;
        seatFlak(sim, number, guns, posts, left, top, right, bottom, centreX, centreY);
        List<Integer> teams = new ArrayList<>();
        int radius = Math.max(right - left, bottom - top) / 2 + 2;
        for (int t = 0; t < TEAMS; t++) {
            // Interleaved, so each team holds the whole front rather than one end of it.
            List<int[]> cells = new ArrayList<>();
            for (int k = t; k < seats; k += TEAMS) cells.add(posts.get(k));
            int squad = ColonyMilitia.seatTeam(sim, cells, "Redoubt " + number + " wall team " + (t + 1), risk, rng);
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, centreX, centreY, radius),
                    CommandAuthority.GARRISON, ISSUER, "hold redoubt " + number);
            teams.add(squad);
        }
        return new CordonRedoubt(number, left, top, right, bottom, teams, guns, seats, built);
    }

    /** The prefix of the sandbag pieces' doodad ids: a cell holding one is a post behind cover. */
    static final String SANDBAG = "doodad.fort-sandbag-";

    /**
     * A built redoubt's wall-team posts, at most {@code seats}: every open cell inside its bounds a window looks out of,
     * and every one inside them or the ring round them a sandbag piece stands on (a firing step along the pavement in
     * front of a block), a gun's own cell aside, nearest the front first; then,
     * while seats remain, the open ground inside, from the front row back. Pure over the grid and the props.
     *
     * @param front +1 where the front is north (high y), -1 where it is south
     */
    static List<int[]> worksPosts(NavigationGrid grid, List<Doodad> doodads, List<int[]> guns,
                                  int left, int top, int right, int bottom, int front, int seats) {
        List<int[]> posts = new ArrayList<>();
        // Loopholes inside the bounds: the cell outside a window is the street it looks out on.
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || taken(guns, x, y)) continue;
                for (Direction side : Direction.CARDINALS) {
                    SharedEdgeBarrier edge = grid.getEdgeBarrier(x, y, side);
                    if (edge != null && edge.kind() == SharedEdgeBarrier.Kind.WINDOW) {
                        posts.add(new int[]{x, y});
                        break;
                    }
                }
            }
        }
        for (Doodad d : doodads) {
            if (d.definitionId() == null || !d.definitionId().startsWith(SANDBAG)) continue;
            int x = d.cellX, y = d.cellY;
            if (x < left - 1 || x > right + 1 || y < top - 1 || y > bottom + 1) continue;
            if (!grid.isWalkable(x, y) || taken(posts, x, y) || taken(guns, x, y)) continue;
            posts.add(new int[]{x, y});
        }
        int frontRow = front > 0 ? bottom : top, centreX = (left + right) / 2;
        posts.sort(Comparator.<int[]>comparingInt(c -> Math.abs(c[1] - frontRow))
                .thenComparingInt(c -> Math.abs(c[0] - centreX)).thenComparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        if (posts.size() > seats) posts = new ArrayList<>(posts.subList(0, seats));
        for (int row = 0; row <= bottom - top && posts.size() < seats; row++) {
            int y = front > 0 ? bottom - row : top + row;
            for (int x = left; x <= right && posts.size() < seats; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || taken(posts, x, y)
                        || taken(guns, x, y)) continue;
                posts.add(new int[]{x, y});
            }
        }
        return posts;
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

    /** The bounds of a redoubt's perimeter wall as generated, {@code [left, top, right, bottom]}, or {@code null}. */
    static int[] perimeterBox(CellTopology topology, Precinct place) {
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

    /**
     * Seats the redoubt's flak position (`cordon-nouns.md`, Field redoubt) on the open yard cell nearest its centre
     * that no post takes and that leaves the ground round it walkable, and adds it to the redoubt's guns, so the
     * trucks rearm it with the rest. It is what meets the flier law over the line: without it no brood spire
     * launches (`colony-breach-nouns.md`, Brood spire). A yard with no such cell has none.
     */
    private static void seatFlak(BattleSimulation sim, int number, List<Long> guns, List<int[]> posts,
                                 int left, int top, int right, int bottom, int centreX, int centreY) {
        NavigationGrid grid = sim.getGrid();
        int reach = Math.max(right - left, bottom - top);
        for (int r = 0; r <= reach; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = centreX + dx, y = centreY + dy;
                    if (x < left || x > right || y < top || y > bottom || !grid.isWalkable(x, y)
                            || grid.isDoorway(x, y) || taken(posts, x, y)) continue;
                    int open = 0;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        if (grid.inBounds(x + d[0], y + d[1]) && grid.isWalkable(x + d[0], y + d[1])) open++;
                    }
                    if (open < 4) continue;
                    guns.add(ColonyMilitia.seatEmplacement(sim, new int[]{x, y}, "Redoubt " + number + " flak",
                            TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID));
                    return;
                }
            }
        }
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
