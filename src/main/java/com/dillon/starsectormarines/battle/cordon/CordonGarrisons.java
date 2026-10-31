package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.evacuation.FortifiedWorks;
import com.dillon.starsectormarines.battle.evacuation.HoldoutLoopholes;
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
 * runs, floodlights), its front run slit every other panel and a gun on each
 * corner tower; one built from what the country had standing
 * ({@link CordonConstruction}) has a gun seated in each nest its builder
 * recorded and one at each corner, its face loopholed, and its posts are the
 * loopholes facing out (the windows its buildings look out of toward the front
 * and the flanks) and its sandbags in its front half. Either way three allied
 * wall teams under garrison authority stand at the posts: two holding the front
 * face side by side, each its own stretch, and the third the posts nearest the
 * front behind them.
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
    /**
     * The gun on each corner of a redoubt (owner, 2026-10-31: "turrets on the 4 corners of the bases"): the line's
     * heavy machine gun, the one its gate towers and picket nests mount, so the gap law's reach holds for it too.
     * UNTUNED: four more guns a redoubt; a lighter gun here is the lever if the line holds without the company.
     */
    static final String CORNER_GUN = ColonyMilitia.EMPLACEMENT_STRUCTURE;
    /** Cells from a built redoubt's corner its corner gun may stand, looked for nearest the corner first. */
    static final int CORNER_REACH = 5;

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
            if (redoubt == null) continue;
            // Its wall and everything inside it stand up to the swarm as works, a house wall it is built of too.
            FortifiedWorks.mark(sim.getGrid(), redoubt.left() - 1, redoubt.top() - 1, redoubt.right() + 1,
                    redoubt.bottom() + 1);
            out.add(redoubt);
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
                centreX, centreY - front * REAR_REACH, WALL_REACH, front);
        // A wall the dressing cannot read as closed (a gap wider than a gate) still stands and is still
        // manned: its teams stand inside it with no towers or slits.
        int left = wall != null ? wall.left() + 1 : box[0] + 1, top = wall != null ? wall.top() + 1 : box[1] + 1;
        int right = wall != null ? wall.right() - 1 : box[2] - 1, bottom = wall != null ? wall.bottom() - 1 : box[3] - 1;
        List<Long> guns = new ArrayList<>();
        for (int[] gun : wall != null ? wall.guns() : List.<int[]>of()) {
            guns.add(ColonyMilitia.seatEmplacement(sim, gun, "Redoubt " + number + " gun tower " + (guns.size() + 1)));
        }
        // A gun on each corner tower, covering the faces either side of it: the gate towers stand where the roads
        // cross the wall, and a redoubt whose roads come in at its flanks (Ain Tafrant's redoubt 1) had no gun on
        // its front at all.
        List<int[]> corners = wall != null ? wall.corners()
                : cornerCells(grid, left, top, right, bottom, List.of());
        int corner = 0;
        for (int[] gun : corners) {
            guns.add(ColonyMilitia.seatEmplacement(sim, gun, "Redoubt " + number + " corner gun " + (++corner),
                    CORNER_GUN));
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
        posts = new ArrayList<>(posts.subList(0, seats));
        seatFlak(sim, number, guns, posts, left, top, right, bottom, centreX, centreY);
        List<Integer> teams = new ArrayList<>();
        // Two teams on the front face, each its own stretch, the third behind them: interleaved across the whole
        // redoubt round its middle, three of the twelve stood on redoubt 1's front wall and the rest on its flanks.
        for (List<int[]> cells : frontTeams(posts, frontRow, TEAMS)) {
            int squad = ColonyMilitia.seatTeam(sim, cells, "Redoubt " + number + " wall team " + (teams.size() + 1),
                    risk, rng);
            int[] hold = hold(cells);
            // A wall's posts are no run: the team holds its stretch of the wall and fights from the slits.
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, hold[0], hold[1], hold[2] + 1),
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
        // Its face is buildings and yard wall with a window or two (a farm's front looks into its own yard): the
        // troops knock firing slits through it.
        HoldoutLoopholes.cutRow(sim, left, right, face(grid, left, top, right, bottom, front),
                front > 0 ? Direction.N : Direction.S);
        List<Long> guns = new ArrayList<>();
        for (int[] gun : works.nests()) {
            if (!grid.isWalkable(gun[0], gun[1])) continue;
            guns.add(ColonyMilitia.seatEmplacement(sim, gun, "Redoubt " + number + " gun " + (guns.size() + 1)));
        }
        int seats = TEAMS * ColonyMilitia.POST_SIZE;
        List<int[]> posts = worksPosts(grid, map.doodads, works.nests(), left, top, right, bottom, front, seats);
        if (posts.size() < seats) return null;
        // A gun at each corner of its ground, off the posts: a farm's nests stood at its rear and in its yard.
        int corner = 0;
        for (int[] gun : cornerCells(grid, sim.getTopology(), left, top, right, bottom, posts)) {
            guns.add(ColonyMilitia.seatEmplacement(sim, gun, "Redoubt " + number + " corner gun " + (++corner),
                    CORNER_GUN));
        }
        seatFlak(sim, number, guns, posts, left, top, right, bottom, centreX, centreY);
        List<Integer> teams = new ArrayList<>();
        int face = face(grid, left, top, right, bottom, front);
        // Each team its own stretch of the posts, held there: told to hold the whole redoubt round its middle, a
        // built redoubt's teams left its windows and sandbags for the yard before the swarm came (a compound's slits
        // keep them; a building's windows do not), and town Line arid 10's school fell inside a minute.
        for (List<int[]> cells : frontTeams(posts, front > 0 ? bottom : top, TEAMS)) {
            int squad = ColonyMilitia.seatTeam(sim, cells, "Redoubt " + number + " wall team " + (teams.size() + 1),
                    risk, rng);
            int[] hold = hold(cells);
            // Held no farther out than the face, or the team walks out through its windows into the street.
            int radius = Math.max(1, Math.min(hold[2], Math.abs(face - hold[1])));
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, hold[0], hold[1], radius),
                    CommandAuthority.GARRISON, ISSUER, "hold redoubt " + number);
            teams.add(squad);
        }
        return new CordonRedoubt(number, left, top, right, bottom, teams, guns, seats, built);
    }

    /**
     * The wall teams' posts, {@code teams} of equal size: the posts nearest the front row (ties west first) split
     * into {@code teams - 1} stretches along the face ({@link #stretches}), so those teams stand side by side on the
     * front, and the rest, the posts behind them, the last team's. One team takes every post.
     */
    static List<List<int[]>> frontTeams(List<int[]> posts, int frontRow, int teams) {
        int each = posts.size() / teams;
        if (teams <= 1) return stretches(posts, Math.max(1, teams));
        List<int[]> byFront = new ArrayList<>(posts);
        byFront.sort(Comparator.<int[]>comparingInt(c -> Math.abs(c[1] - frontRow)).thenComparingInt(c -> c[0])
                .thenComparingInt(c -> c[1]));
        List<List<int[]>> out = stretches(byFront.subList(0, each * (teams - 1)), teams - 1);
        out.add(new ArrayList<>(byFront.subList(each * (teams - 1), each * teams)));
        return out;
    }

    /**
     * Where a redoubt's corner guns stand ({@link #CORNER_GUN}): at each corner of its ground in turn, the
     * open cell inside it nearest the corner within {@link #CORNER_REACH}, never a doorway, a post, a cell inside a
     * building or one whose gun would cut the ground round it in two ({@link #keepsWay}). A corner with none has no
     * gun. Pure over the grid.
     */
    static List<int[]> cornerCells(NavigationGrid grid, CellTopology topology, int left, int top, int right,
                                   int bottom, List<int[]> taken) {
        List<int[]> out = new ArrayList<>();
        List<int[]> blocked = new ArrayList<>(taken);
        for (int[] corner : new int[][]{{left, bottom}, {right, bottom}, {left, top}, {right, top}}) {
            int inX = corner[0] == left ? 1 : -1, inY = corner[1] == top ? 1 : -1;
            int[] found = null;
            for (int d = 0; d <= CORNER_REACH && found == null; d++) {
                // Along the ring at d from the corner, the diagonal first, then out along each wall.
                for (int k = 0; k <= d && found == null; k++) {
                    for (int[] c : new int[][]{{corner[0] + inX * d, corner[1] + inY * k},
                            {corner[0] + inX * k, corner[1] + inY * d}}) {
                        int x = c[0], y = c[1];
                        if (x < left || x > right || y < top || y > bottom || !grid.inBounds(x, y)
                                || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || taken(blocked, x, y)
                                || topology != null && topology.getBuildingId(x, y) != 0
                                || !keepsWay((cx, cy) -> !(cx == x && cy == y) && grid.inBounds(cx, cy)
                                && grid.isWalkable(cx, cy) && !taken(blocked, cx, cy), x, y)) continue;
                        found = new int[]{x, y};
                        break;
                    }
                }
            }
            if (found == null) continue;
            out.add(found);
            blocked.add(found);
        }
        return out;
    }

    /** As {@link #cornerCells(NavigationGrid, CellTopology, int, int, int, int, List)} on a grid alone. */
    static List<int[]> cornerCells(NavigationGrid grid, int left, int top, int right, int bottom, List<int[]> taken) {
        return cornerCells(grid, null, left, top, right, bottom, taken);
    }

    /** A test on a cell. */
    interface Open {
        boolean at(int x, int y);
    }

    /**
     * Whether closing {@code (x, y)} leaves the open ground round it in one piece: the open cells of its eight
     * neighbours, walked round the ring (each neighbour a step from the next), form at most one run that touches a
     * side. Pure.
     */
    static boolean keepsWay(Open open, int x, int y) {
        int[][] ring = {{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}};
        boolean[] o = new boolean[8];
        int start = -1;
        for (int i = 0; i < 8; i++) {
            o[i] = open.at(x + ring[i][0], y + ring[i][1]);
            if (!o[i]) start = i;
        }
        if (start < 0) return true;
        int runs = 0;
        boolean inRun = false, sided = false;
        for (int step = 1; step <= 8; step++) {
            int i = (start + step) % 8;
            if (o[i]) {
                inRun = true;
                sided |= i % 2 == 0;
            } else if (inRun) {
                if (sided) runs++;
                inRun = false;
                sided = false;
            }
        }
        return runs <= 1;
    }

    /**
     * The posts split into {@code teams} stretches of equal size along the face, west to east (then south to north):
     * each team's posts side by side, so it holds its stretch rather than the yard.
     */
    static List<List<int[]>> stretches(List<int[]> posts, int teams) {
        List<int[]> along = new ArrayList<>(posts);
        along.sort(Comparator.<int[]>comparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
        List<List<int[]>> out = new ArrayList<>();
        int each = along.size() / teams;
        for (int t = 0; t < teams; t++) out.add(new ArrayList<>(along.subList(t * each, (t + 1) * each)));
        return out;
    }

    /** Where a team holds its stretch, {x, y, radius}: the post nearest the stretch's middle, out to its farthest post. */
    static int[] hold(List<int[]> stretch) {
        float mx = 0, my = 0;
        for (int[] c : stretch) {
            mx += c[0];
            my += c[1];
        }
        mx /= stretch.size();
        my /= stretch.size();
        int[] at = stretch.get(0);
        for (int[] c : stretch) if (Math.hypot(c[0] - mx, c[1] - my) < Math.hypot(at[0] - mx, at[1] - my)) at = c;
        int radius = 1;
        for (int[] c : stretch) radius = Math.max(radius, (int) Math.ceil(Math.hypot(c[0] - at[0], c[1] - at[1])));
        return new int[]{at[0], at[1], radius};
    }

    /** The prefix of the sandbag pieces' doodad ids: a cell holding one is a post behind cover. */
    static final String SANDBAG = "doodad.fort-sandbag-";

    /**
     * A built redoubt's wall-team posts, at most {@code seats}: every open cell inside its bounds a window looks out of
     * toward the front, or out across a flank from its front half, and every one in its front half, inside its bounds
     * or the ring round them, a sandbag piece stands on (a firing step along the pavement in front of a block), a gun's
     * own cell aside, nearest the front first: a farm's teams stood at its farmhouse's back windows over its own yard,
     * and in the sandbag ring of the gun nest at its rear (town Road arid 7's redoubt 3); then,
     * while seats remain, the open ground inside, from the front row back. No window or open ground forward of the
     * redoubt's face ({@link #face}): a school's courtyard block stands a row inside its bounds, and its wall teams
     * were seated on the street before it, outside the windows, where town Line seed 8's school lost all twelve in
     * half a minute. Pure over the grid and the props.
     *
     * @param front +1 where the front is north (high y), -1 where it is south
     */
    static List<int[]> worksPosts(NavigationGrid grid, List<Doodad> doodads, List<int[]> guns,
                                  int left, int top, int right, int bottom, int front, int seats) {
        List<int[]> posts = new ArrayList<>();
        int face = face(grid, left, top, right, bottom, front);
        int middleX = (left + right) / 2, middleY = (top + bottom) / 2;
        // Loopholes inside the bounds: the cell outside a window is the street it looks out on.
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || taken(guns, x, y)
                        || (y - face) * front > 0) continue;
                for (Direction side : Direction.CARDINALS) {
                    boolean out = side.dy == front
                            || side.dx != 0 && side.dx * (x - middleX) > 0 && (y - middleY) * front >= 0;
                    if (!out) continue;
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
            if (x < left - 1 || x > right + 1 || y < top - 1 || y > bottom + 1 || (y - middleY) * front < 0) continue;
            if (!grid.isWalkable(x, y) || taken(posts, x, y) || taken(guns, x, y)) continue;
            posts.add(new int[]{x, y});
        }
        int frontRow = front > 0 ? bottom : top, centreX = (left + right) / 2;
        posts.sort(Comparator.<int[]>comparingInt(c -> Math.abs(c[1] - frontRow))
                .thenComparingInt(c -> Math.abs(c[0] - centreX)).thenComparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        if (posts.size() > seats) posts = new ArrayList<>(posts.subList(0, seats));
        for (int row = 0; row <= bottom - top && posts.size() < seats; row++) {
            int y = front > 0 ? bottom - row : top + row;
            if ((y - face) * front > 0) continue;
            for (int x = left; x <= right && posts.size() < seats; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || taken(posts, x, y)
                        || taken(guns, x, y)) continue;
                posts.add(new int[]{x, y});
            }
        }
        return posts;
    }

    /**
     * The row of a redoubt's face: the frontmost row in the front half of its bounds at least half of which is wall, or
     * the bounds' front row where none is.
     */
    static int face(NavigationGrid grid, int left, int top, int right, int bottom, int front) {
        for (int row = 0; row <= (bottom - top) / 2; row++) {
            int y = front > 0 ? bottom - row : top + row;
            int walls = 0;
            for (int x = left; x <= right; x++) if (grid.inBounds(x, y) && !grid.isWalkable(x, y)) walls++;
            if (2 * walls >= right - left + 1) return y;
        }
        return front > 0 ? bottom : top;
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
    static int[] nearestOpen(NavigationGrid grid, int x, int y, int left, int top, int right, int bottom) {
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
