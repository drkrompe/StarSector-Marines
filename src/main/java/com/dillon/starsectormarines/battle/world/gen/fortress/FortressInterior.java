package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Packs a fortress's program into its envelope and cuts the roadways that reach
 * it.
 *
 * <p>This runs <b>before</b> any wall is drawn. A fortress whose wall is stamped
 * first can only ever enclose whatever the ordinary fill left inside it; packed
 * first, the wall has something to be drawn around, its gates can land where the
 * roadways already run out to meet them, and its kill zone is measured from real
 * structure. See {@code compound-programs.md}.
 *
 * <p>Roadways are not laid out in advance. The packer cuts a two-wide way to
 * every building that cannot otherwise be reached, through whatever the packing
 * left over, so the fortress ends up with a road network shaped by where its
 * buildings actually went. A grid ruled first would put the buildings in the
 * bays between the roads, which is the parcel-first mistake wearing a different
 * hat.
 */
public final class FortressInterior {

    /**
     * Fortress ground: buildings stand on paved interiors, roadways are stone,
     * and thresholds are marked so a doorway reads as one from outside.
     */
    private static final RoomPacker.Palette PALETTE = new RoomPacker.Palette(
            GroundKind.INDOOR, GroundKind.STONE, GroundKind.STRIPED);

    /** The open ground between the buildings: parade square, vehicle park, verge. */
    private static final GroundKind YARD = GroundKind.DIRT;

    /**
     * How well a garrison keeps its own buildings. A working arrangement rather
     * than a cramped one: this is a defended installation, not a prize hull
     * being run on a shoestring.
     */
    private static final RoomFit FIT = RoomFit.STANDARD;

    /** Where a fortress's buildings ended up, and what it could not find room for. */
    public record Result(List<RoomPacker.Placed> placed, List<FortressBuilding> unplaced) {

        public Result {
            placed = List.copyOf(placed);
            unplaced = List.copyOf(unplaced);
        }
    }

    private FortressInterior() {}

    /**
     * Lay the program into {@code ground}, approached from {@code axis}.
     *
     * @param ground every cell the ward owns: where its buildings go, and what
     *               becomes open yard wherever none did
     * @param muster the parade ground and approach that already exist, which the
     *               packing must leave alone and may open onto
     */
    public static Result pack(GenContext ctx, boolean[][] ground, boolean[][] muster,
                              TraversalAxis axis, List<FortressBuilding> program) {
        Bounds bounds = Bounds.of(ground, ctx.width, ctx.height);
        if (bounds == null) return new Result(List.of(), program);

        RoomPacker packer = new RoomPacker(ctx, footings(ctx, ground, muster), muster, PALETTE);
        List<RoomPacker.Placed> placed = new ArrayList<>();
        List<FortressBuilding> unplaced = new ArrayList<>();
        for (FortressBuilding building : FortressProgram.expanded(program)) {
            RoomPacker.Request request = new RoomPacker.Request(
                    building.purpose(), building.shape(),
                    wardAffinity(bounds, axis, building.ward()), building.perimeter());
            RoomPacker.Placed room = packer.place(request, true);
            if (room == null) {
                unplaced.add(building);
            } else {
                placed.add(room);
            }
        }
        metalYard(ctx, ground, placed);
        stampWalls(ctx, placed);
        furnish(ctx, placed);
        return new Result(placed, unplaced);
    }

    /**
     * Furnish each building with the fitting authored for its purpose.
     *
     * <p>The same fittings that furnish a deck. A magazine holds racks and ready
     * crates whether it is aboard a ship or against a fortress wall, and a
     * vehicle shed holds the same five-by-seven bays — which is the whole reason
     * the fittings stopped being shipboard. A purpose with no authored fitting
     * is left bare rather than scattered with something generic: an empty
     * building is honest about not being authored yet, one full of crates looks
     * finished and is not.
     *
     * <p>Runs after the yard is opened, not before. A fixture may take its cell
     * out of circulation, and the yard pass opens everything that is not
     * walkable and not wall — so furnishing first would have the yard quietly
     * re-open the cells the fill had just occupied.
     *
     * <p>The rollback is the deck's, for the deck's reason: a fill that seals
     * its own building is worse than no fill, because the building still stands
     * and still shows a door and cannot be entered. What is thrown away is
     * everything the fitting published, not merely what can be seen — a
     * rolled-back bay that kept its berths would offer to service machines on
     * empty floor and look entirely correct from outside.
     */
    private static void furnish(GenContext ctx, List<RoomPacker.Placed> placed) {
        for (RoomPacker.Placed room : placed) {
            RoomFitting fitting = RoomFittings.forPurpose(room.purpose());
            if (fitting == null) continue;

            int doodads = ctx.doodads.size();
            int berths = ctx.gantries.size();
            int work = ctx.fixtureTasks.size();
            RoomFloor floor = new RoomFloor(ctx, room, FIT);
            fitting.fit(floor);
            if (floor.circulationSurvives()) {
                floor.seal();
            } else {
                ctx.doodads.subList(doodads, ctx.doodads.size()).clear();
                ctx.gantries.subList(berths, ctx.gantries.size()).clear();
                ctx.fixtureTasks.subList(work, ctx.fixtureTasks.size()).clear();
            }
        }
    }

    /**
     * The ground a building's floor may actually stand on: the ward's own,
     * less the last cell at every edge of it.
     *
     * <p>A packed room is a floor and the ring around it, and the packer carves
     * only the floor — the ring is wall precisely because it is ground the
     * packing was told to leave alone. So the ring needs ground of the ward's
     * own underneath it. Let a floor reach the ward's last row and the ring
     * falls outside, onto a city street the ward does not own and must not
     * close, because the road graph published that street and a convoy is
     * entitled to drive it. Nothing then makes those cells solid, and the
     * building comes out open along its whole flank — walled by a pavement,
     * with its berths and bunks standing in the street.
     *
     * <p>Circulation is not an edge in that sense. A ring may not cross the
     * kept road at all — the packer rejects the placement outright — so a room
     * beside one is already as close as it is allowed to get, and treating the
     * road as an edge here would only push the packing a cell further off it
     * for nothing.
     */
    private static boolean[][] footings(GenContext ctx, boolean[][] ground, boolean[][] muster) {
        boolean[][] footings = new boolean[ctx.width][ctx.height];
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                if (!ground[x][y]) continue;
                boolean walled = true;
                for (int dx = -1; dx <= 1 && walled; dx++) {
                    for (int dy = -1; dy <= 1 && walled; dy++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        walled = nx >= 0 && ny >= 0 && nx < ctx.width && ny < ctx.height
                                && (ground[nx][ny] || muster[nx][ny]);
                    }
                }
                footings[x][y] = walled;
            }
        }
        return footings;
    }

    /**
     * Author the wall around every building the packer left as negative space.
     *
     * <p>The packer draws no walls. It carves a room out of solid ground, and
     * the ring around it is simply ground that was not carved — which is all a
     * hull needs, because a void inside a ship is structure whether or not
     * anything says so. On a map a wall is a thing in its own right: it is
     * drawn from a per-cell mask naming which of its faces are outside, and a
     * ring carrying no mask falls through to the blank fill reserved for a cell
     * buried inside a wall mass. That is a building with no wall on any face,
     * standing as an invisible obstruction with its furniture apparently out in
     * the open.
     *
     * <p>A face is exterior when the cell beyond it is not part of this
     * building's own footprint. That is the rule
     * {@link com.dillon.starsectormarines.battle.world.model.WallMasks#stampPerimeter}
     * applies to a rectangle, restated so that it also holds for a shape which
     * is not one. Faces are or-ed rather than assigned, so where two buildings
     * wedge tight enough to share a ring cell the shared wall comes out with a
     * face towards each of them.
     *
     * <p>Walkable ring cells are skipped. A doorway, or a cell some other
     * room's passage was cut through, is an opening rather than wall, and the
     * cells beside it keep the faces the footprint gives them: framing the gap
     * would draw the hole instead of the building. This matches what an
     * ordinary building does when its doorway is punched.
     */
    private static void stampWalls(GenContext ctx, List<RoomPacker.Placed> placed) {
        for (RoomPacker.Placed room : placed) {
            Set<Long> footprint = new HashSet<>();
            for (int[] cell : room.shape().filled()) {
                footprint.add(cellKey(room.originX() + cell[0], room.originY() + cell[1]));
            }
            for (int[] cell : room.shape().wall()) {
                footprint.add(cellKey(room.originX() + cell[0], room.originY() + cell[1]));
            }
            for (int[] cell : room.shape().filled()) {
                hint(ctx, room.originX() + cell[0], room.originY() + cell[1]);
            }
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0];
                int y = room.originY() + cell[1];
                if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) continue;
                if (ctx.grid.isWalkable(x, y)) continue;
                int mask = 0;
                if (!footprint.contains(cellKey(x, y + 1))) mask |= CellTopology.WALL_DIR_N;
                if (!footprint.contains(cellKey(x, y - 1))) mask |= CellTopology.WALL_DIR_S;
                if (!footprint.contains(cellKey(x + 1, y))) mask |= CellTopology.WALL_DIR_E;
                if (!footprint.contains(cellKey(x - 1, y))) mask |= CellTopology.WALL_DIR_W;
                ctx.topology.orWallDirMask(x, y, mask);
                hint(ctx, x, y);
            }
        }
    }

    /**
     * Claim a cell for a fortified building, so the pass that votes a kind per
     * flooded room reads a garrison building rather than falling through to the
     * unclassified default.
     */
    private static void hint(GenContext ctx, int x, int y) {
        if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) return;
        ctx.topology.setBuildingKindHint(x, y, BuildingKind.FORTIFIED);
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }

    /**
     * Open every scrap of ground the packing did not build on.
     *
     * <p>Where a hull's leftovers stay solid — a void inside a ship is
     * structure, and walking through it would be walking through the vessel —
     * a fortress's leftovers are its yard. Parade ground, vehicle park, the
     * space between a magazine and the wall: all of it is ground people cross,
     * and leaving it closed would produce a fortress that is mostly corridor
     * and cannot be fought through.
     *
     * <p>This is also what makes the roadways read as roadways. The packer cuts
     * them because a building had to be reached; against solid ground they are
     * the only way through, which is a warren. Against open yard they are the
     * made-up routes across it, and a squad can leave one and cross open ground
     * under fire — which is the choice a fortress assault should be about.
     */
    private static void metalYard(GenContext ctx, boolean[][] buildable,
                                  List<RoomPacker.Placed> placed) {
        boolean[][] structure = new boolean[ctx.width][ctx.height];
        for (RoomPacker.Placed room : placed) {
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0];
                int y = room.originY() + cell[1];
                if (x >= 0 && y >= 0 && x < ctx.width && y < ctx.height) structure[x][y] = true;
            }
        }
        // A doorway is cut through the ring, so it is not structure however the
        // ring was stamped; opening the yard must not close a building's door.
        for (RoomPacker.Placed room : placed) {
            for (Doorway door : room.doors()) {
                if (door.x() >= 0 && door.y() >= 0
                        && door.x() < ctx.width && door.y() < ctx.height) {
                    structure[door.x()][door.y()] = false;
                }
            }
        }
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                if (!buildable[x][y] || structure[x][y] || ctx.grid.isWalkable(x, y)) continue;
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, YARD);
                ctx.topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
            }
        }
    }

    /**
     * Whether a position sits at the depth this ward means, measured along the
     * attacker's approach.
     *
     * <p>Thirds rather than a distance falloff. A building is near the wall, in
     * the middle, or at the back, and the packer's affinity is a yes-or-no
     * anyway — grading it would be precision the packing cannot honour once
     * shapes start wedging against each other.
     */
    private static RoomPacker.Affinity wardAffinity(Bounds bounds, TraversalAxis axis, Ward ward) {
        return (centreX, centreY) -> depthBand(bounds, axis, centreX, centreY) == ward;
    }

    /** Which third of the approach this cell falls in, with FRONTAGE nearest the attacker. */
    private static Ward depthBand(Bounds bounds, TraversalAxis axis, int x, int y) {
        // The attacker enters at the low end of the traversal axis, so distance
        // from that end is distance from the wall they will breach.
        int span = axis == TraversalAxis.SOUTH_TO_NORTH ? bounds.height() : bounds.width();
        int from = axis == TraversalAxis.SOUTH_TO_NORTH ? y - bounds.top() : x - bounds.left();
        if (span <= 0) return Ward.YARD;
        int third = from * 3 / span;
        return third <= 0 ? Ward.FRONTAGE : third == 1 ? Ward.YARD : Ward.REAR;
    }

    /** The extent of the buildable envelope, so wards are thirds of the fortress. */
    private record Bounds(int left, int top, int right, int bottom) {

        static Bounds of(boolean[][] mask, int width, int height) {
            int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (!mask[x][y]) continue;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
            return right < left ? null : new Bounds(left, top, right, bottom);
        }

        int width() {
            return right - left + 1;
        }

        int height() {
            return bottom - top + 1;
        }
    }
}
