package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressInterior;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.road.VehicleCorridor;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Step 3b'' (conquest only) — lay the fortress ward out from what a garrison
 * needs, before the wall is drawn around it.
 *
 * <p>Without this the fortress district is ordinary city: freestanding
 * rectangles on a road grid, sized by whatever the partition left, and the wall
 * is the only thing that says fortress. Here a program-sized ward is packed
 * from authored building footprints — sheds and gatehouse against the approach,
 * barracks and workshops in the middle, magazines at the back — with roadways
 * cut to reach them and the rest opened as parade ground. See
 * {@code compound-programs.md}.
 *
 * <p><b>The ward overwrites rather than claims.</b> It runs after the fill
 * dispatch and replaces what the leaf fillers put inside its footprint, which
 * is the same bargain {@code FortressWallStamper} already makes one stage
 * later and for the same reason: claiming leaves instead would need the
 * inter-leaf road frames absorbed to keep the ward connected, and would still
 * leave every straddling leaf half filled. Overwriting costs some wasted fill
 * and obliges this stage to take the old contents out with it — a doodad or a
 * point of interest left behind would sit inside a building that no longer
 * exists.
 *
 * <p>The keep compound is not overwritten. It was seeded as the one canonical
 * fortress base, it carries the mission's command post, and the ward is packed
 * around it as its citadel — which is why the ward's own program has no keep in
 * it.
 */
public final class FortressWardStage implements GenStage {

    /**
     * Cells kept between the ward and the wall's nominal line on the sides the
     * wall actually runs, so it never has to cut through a building the ward
     * placed. Wider than the wall's own setback because the wall may expand
     * outward to wrap the keep, never inward.
     */
    private static final int WALL_CLEARANCE = 14;

    /**
     * Cells left at the fortress's back. The wall is only drawn on three sides —
     * the back abuts the impassable map edge — so there is nothing there for the
     * ward to stand clear of, and charging it the full clearance on both ends
     * spends most of a forty-row band on nothing.
     */
    private static final int BACK_MARGIN = 2;

    /** Cells kept clear around the citadel, so the ward does not build against its wall. */
    private static final int CITADEL_CLEARANCE = 3;

    /**
     * Shallowest band worth packing: a vehicle shed is seven cells deep, and it
     * needs its apron, its wall ring, and a roadway to reach it. Below this the
     * band cannot hold the buildings that make a fortress read as one, and the
     * ward is skipped rather than filled with whatever still fits.
     */
    private static final int MIN_DEPTH = 14;

    /** Cells the ward widens by while looking for enough buildable ground. */
    private static final int LATERAL_STEP = 6;

    /**
     * The airbases a fortress ward will build, largest first.
     *
     * <p>A ward on the largest map has the ground for the whole installation,
     * which is the one place on a map that does. Every other map is smaller,
     * and asking only for the installation is how a garrison ends up with no
     * air arm at all: the ward tried to reserve forty-four by twenty-four,
     * could not, and skipped the base without a word. It fitted at the one size
     * the map-gen tests run, so nothing said otherwise — the enemy simply had
     * no airfield, on every operation below full strength.
     *
     * <p>So the ward ladders down exactly as a city claim does. A smaller map
     * gets a smaller airbase, and a base with one berth is still an air arm
     * that flies and still a thing that can be taken to stop it.
     */
    private static final AirbaseLot.Size[] WARD_AIRBASES = {
            AirbaseLot.Size.STATION, AirbaseLot.Size.FIELD,
            AirbaseLot.Size.PAD, AirbaseLot.Size.STRIP };

    /** An airbase reservation and the size that fitted it. */
    record WardAirbase(int[] rect, AirbaseLot.Size size) { }

    /** Ward cells the packer may not build on but must be able to cross. */
    private static final int APPROACH_WIDTH = 2;

    @Override
    public void run(GenContext ctx) {
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        TraversalAxis axis = ctx.get(BspKeys.AXIS);
        if (biomeMap == null || axis == null) return;

        int[] band = fortressBand(biomeMap, ctx.width, ctx.height);
        if (band == null) return;

        // What the ward cannot build on has to be known before it is sized, not
        // after: roads crossing the band and the citadel standing in it take
        // ground out of the middle, and a ward sized as though they were not
        // there comes up short by exactly what they occupy.
        Compound citadel = findCitadel(ctx.get(BspKeys.COMPOUNDS));
        boolean[][] roadCells = ctx.get(BspKeys.ROAD_CELLS);
        int[] ward = wardRect(band, axis, ctx.width, ctx.height, citadel, roadCells);
        if (ward == null) return;

        VehicleCorridor corridor = ctx.get(BspKeys.VEHICLE_CORRIDOR);
        boolean[][] roads = corridor != null
                ? wardCorridor(ctx, ward, corridor)
                : wardRoads(ctx, ward, axis);
        clearWard(ctx, ward, citadel, roads);
        boolean[][] buildable = buildable(ctx, ward, citadel, roads);
        boolean[][] circulation = hasAny(roads) ? roads : approach(ctx, ward, axis);

        // The airbase takes its lot before anything is packed, and out of the
        // ward's own end rather than its middle. Claimed after packing it would
        // get whatever shape the leftovers had, which is a shallow strip; taken
        // from the middle it would sever the ward's spine, which is how an
        // earlier reservation left a ward every building could reach and no
        // convoy could cross.
        WardAirbase base = airbaseLot(ward, axis, citadel, roads);
        if (base != null) {
            for (int x = base.rect()[0]; x <= base.rect()[2]; x++) {
                for (int y = base.rect()[1]; y <= base.rect()[3]; y++) buildable[x][y] = false;
            }
        }

        FortressInterior.Result result = FortressInterior.pack(
                ctx, buildable, circulation, axis, FortressProgram.ward());
        if (base != null) {
            int[] lot = base.rect();
            int clear = base.size().clearance();
            new AirbaseLot(lot[0] + clear, lot[1] + clear,
                    lot[2] - clear, lot[3] - clear,
                    AirbaseLot.Facing.of(axis), base.size()).author(ctx, ctx.rng);
            emitAirbaseNode(ctx, lot);
            reserveAgainstLaterStampers(ctx, lot);
        }
        ctx.put(BspKeys.FORTRESS_WARD, ward);
        emitTacticalNodes(ctx, result);
    }

    /**
     * The ward's footprint: the full depth the band can spare, and only as much
     * width as the program needs.
     *
     * <p>Depth is taken rather than chosen because the band is the shallow axis
     * — a quarter of the map along the approach — and a fortress wants every row
     * of it. Width is sized from the program, because the band is long and an
     * envelope wider than its contents pins every building to the boundary and
     * leaves a hole in the middle; see {@code compound-programs.md} for the
     * measurement. What the ward does not take stays ordinary city, which is
     * the approach the attacker fights through to reach it.
     */
    private static int[] wardRect(int[] band, TraversalAxis axis, int mapW, int mapH,
                                  Compound citadel, boolean[][] roadCells) {
        boolean alongY = axis == TraversalAxis.SOUTH_TO_NORTH;
        // Front is the side the attacker arrives on, which is the low end of the
        // traversal axis; back is the map edge behind the fortress.
        int frontLo = alongY ? band[1] : band[0];
        int backHi = alongY ? band[3] : band[2];
        int depthLo = frontLo + WALL_CLEARANCE;
        int depthHi = backHi - BACK_MARGIN;
        int depth = depthHi - depthLo + 1;
        if (depth < MIN_DEPTH) return null;

        int lateralLo = (alongY ? band[0] : band[1]) + WALL_CLEARANCE;
        int lateralHi = (alongY ? band[2] : band[3]) - WALL_CLEARANCE;
        int lateralRoom = lateralHi - lateralLo + 1;
        if (lateralRoom < MIN_DEPTH) return null;

        int ground = FortressProgram.envelopeArea(FortressProgram.ward());
        int lateral = Math.max(MIN_DEPTH, ceilDiv(ground, depth));
        while (lateral < lateralRoom) {
            int[] candidate = rect(alongY, depthLo, depthHi,
                    lateralLo + (lateralRoom - lateral) / 2, lateral);
            if (buildableCells(candidate, citadel, roadCells) >= ground) break;
            lateral += LATERAL_STEP;
        }
        lateral = Math.min(lateral, lateralRoom);

        int[] ward = rect(alongY, depthLo, depthHi,
                lateralLo + (lateralRoom - lateral) / 2, lateral);
        if (ward[0] < 1 || ward[1] < 1 || ward[2] > mapW - 2 || ward[3] > mapH - 2) return null;
        return ward;
    }

    /**
     * The airbase's rectangle inside the ward, or null when the ward is too
     * small to hold one without eating the buildings.
     *
     * <p>Pinned to the lateral end furthest from the citadel: the fortress's
     * own centre of gravity stays clear, and a base at the end of the ward
     * takes width off one side rather than cutting the ward in two. Depth is
     * taken from the back, so the runway ends up along the ward's front where
     * an aircraft has an open run at it.
     *
     * <p><b>Preferring an end is not the same as staying off the citadel.</b>
     * The keep is the one thing in the ward this stage does not demolish, so a
     * lot laid across it repaves ground whose building, objective and garrison
     * node all survive — a capture marker and a bare roof standing on the apron
     * with no walls under them. The lot is therefore refused over the keep the
     * same way it is refused over the ward's kept road, and for the same
     * reason: structure yields to a place the mission depends on, and an
     * airbase has three smaller sizes and a second end to fall back on.
     */
    static WardAirbase airbaseLot(int[] ward, TraversalAxis axis,
                                         Compound citadel, boolean[][] roadCells) {
        for (AirbaseLot.Size size : WARD_AIRBASES) {
            // The far end from the citadel first, then the near one. A base
            // wants the end the fortress is not centred on, but "wants" is not
            // "must": preferring one end and giving up when a road crosses it
            // is how a ward with room at the other end ends up with no air arm.
            for (boolean farEnd : new boolean[]{ true, false }) {
                int[] rect = lotFor(size, ward, axis, citadel, roadCells, farEnd);
                if (rect != null) return new WardAirbase(rect, size);
            }
        }
        return null;
    }

    /** The reservation one size would take, or null when the ward cannot spare it. */
    private static int[] lotFor(AirbaseLot.Size size, int[] ward, TraversalAxis axis,
                                Compound citadel, boolean[][] roadCells, boolean farEnd) {
        boolean alongY = axis == TraversalAxis.SOUTH_TO_NORTH;
        // The reservation is the lot plus the clear ground kept outside its
        // fence. Reserving only the lot lets a building pack flush against the
        // fence, and the way past the base is then whatever the packing left.
        AirbaseLot.Facing facing = AirbaseLot.Facing.of(axis);
        int spanX = AirbaseLot.reservedSpanX(size, facing);
        int spanY = AirbaseLot.reservedSpanY(size, facing);
        int wardW = ward[2] - ward[0] + 1;
        int wardH = ward[3] - ward[1] + 1;
        if (spanX > wardW || spanY > wardH) return null;

        // What the buildings still need after the lot is taken. Below the
        // packing slack they start going unplaced, and a base is not worth a
        // third of the fortress.
        int remaining = wardW * wardH - spanX * spanY;
        if (remaining < FortressProgram.buildingGround(FortressProgram.ward())) return null;

        boolean citadelLow = citadel != null
                && (alongY ? (citadel.left + citadel.right) / 2 < (ward[0] + ward[2]) / 2
                           : (citadel.top + citadel.bottom) / 2 < (ward[1] + ward[3]) / 2);
        boolean high = farEnd == citadelLow;
        int left = alongY
                ? (high ? ward[2] - spanX + 1 : ward[0])
                : ward[0];
        int bottom = alongY
                ? ward[1]
                : (high ? ward[3] - spanY + 1 : ward[1]);
        int[] lot = { left, bottom, left + spanX - 1, bottom + spanY - 1 };
        if (lot[0] < ward[0] || lot[1] < ward[1]
                || lot[2] > ward[2] || lot[3] > ward[3]) return null;
        if (roadCells != null && crossesRoad(lot, roadCells)) return null;
        if (overlapsCitadel(lot, citadel)) return null;
        return lot;
    }

    /**
     * Whether this rectangle touches the keep or the ground kept clear round
     * it. The keep is the one thing the ward does not clear, so paving it is
     * how a lot ends up holding somebody else's objective.
     */
    private static boolean overlapsCitadel(int[] rect, Compound citadel) {
        if (citadel == null) return false;
        return rect[0] <= citadel.right + CITADEL_CLEARANCE
                && rect[2] >= citadel.left - CITADEL_CLEARANCE
                && rect[1] <= citadel.bottom + CITADEL_CLEARANCE
                && rect[3] >= citadel.top - CITADEL_CLEARANCE;
    }

    /** Whether a kept road runs through this rectangle. The ward keeps exactly one, and the base does not get to sever it. */
    private static boolean crossesRoad(int[] rect, boolean[][] roadCells) {
        for (int x = rect[0]; x <= rect[2]; x++) {
            for (int y = rect[1]; y <= rect[3]; y++) {
                if (x < roadCells.length && y < roadCells[x].length && roadCells[x][y]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The airbase as one position to take, the same shape the old airfield published. */
    private static void emitAirbaseNode(GenContext ctx, int[] lot) {
        ctx.tactical.add(new TacticalNode(TacticalNode.Kind.AIRBASE,
                (lot[0] + lot[2]) / 2, (lot[1] + lot[3]) / 2,
                lot[0], lot[1], lot[2], lot[3],
                Faction.DEFENDER, 65, 3, false));
    }

    /** Assemble a ward rectangle from its depth run and its lateral run. */
    private static int[] rect(boolean alongY, int depthLo, int depthHi,
                              int lateralStart, int lateral) {
        return alongY
                ? new int[]{ lateralStart, depthLo, lateralStart + lateral - 1, depthHi }
                : new int[]{ depthLo, lateralStart, depthHi, lateralStart + lateral - 1 };
    }

    /** Ground inside this rectangle a building could actually stand on. */
    private static int buildableCells(int[] ward, Compound citadel, boolean[][] roadCells) {
        int count = 0;
        for (int x = ward[0]; x <= ward[2]; x++) {
            for (int y = ward[1]; y <= ward[3]; y++) {
                if (inCitadel(citadel, x, y, CITADEL_CLEARANCE)) continue;
                if (roadCells != null && x < roadCells.length && y < roadCells[x].length
                        && roadCells[x][y]) {
                    continue;
                }
                count++;
            }
        }
        return count;
    }

    private static int ceilDiv(int a, int b) {
        return b <= 0 ? a : (a + b - 1) / b;
    }

    /**
     * The stretch of the map's vehicle corridor that runs through this ward:
     * the one road the fortress keeps, at the width a vehicle needs.
     *
     * <p>Not every road crossing the band. Keeping the whole street grid was the
     * first attempt and it subdivided the ward into blocks smaller than the
     * buildings meant to stand in it — 2647 cells of buildable ground, and not
     * one clear pocket for a fifteen-by-nine vehicle shed. A fortress does not
     * inherit a city's streets; it has a road in and a road out, and its own
     * circulation is cut by the packing.
     *
     * <p><b>The ward is told which road that is rather than working it out.</b>
     * {@link #wardRoads} worked it out — shortest path over whatever road cells
     * crossed the band, then dilated one cell — and the result was a lane two
     * cells wide whenever that path hugged the edge of the street carrying it,
     * because the dilation could only keep cells the old road mask already
     * held. Two cells is walkable and not drivable, which is the whole defect:
     * the fortress kept a road nothing with wheels could use, and every check
     * that asked whether a road was preserved said yes. Clipping an authored
     * band has the property the derivation could not — its width is stated up
     * front rather than inherited from geometry that may or may not have been
     * wide enough.
     *
     * <p>The kept route still reaches the wall's line, so the gate has
     * somewhere obvious to be: where the fortress's own traffic runs out to
     * meet it.
     */
    private static boolean[][] wardCorridor(GenContext ctx, int[] ward, VehicleCorridor corridor) {
        boolean[][] kept = new boolean[ctx.width][ctx.height];
        for (int x = ward[0]; x <= ward[2]; x++) {
            for (int y = ward[1]; y <= ward[3]; y++) kept[x][y] = corridor.contains(x, y);
        }
        return kept;
    }

    /**
     * How the ward chose its through route before the corridor was authored:
     * the shortest run of road across the band, dilated by a cell.
     *
     * <p>Retained as the fallback for a map with no corridor bound — which in
     * practice means a control run with
     * {@code -Dbattle.mapgen.vehicleCorridor=false}, so that switch produces
     * the fortress exactly as it generated before this layer existed rather
     * than one with no road at all. See {@link #wardCorridor} for why the
     * derivation was not good enough to keep as the production path.
     */
    private static boolean[][] wardRoads(GenContext ctx, int[] ward, TraversalAxis axis) {
        boolean[][] kept = new boolean[ctx.width][ctx.height];
        boolean[][] source = ctx.get(BspKeys.ROAD_CELLS);
        if (source == null) return kept;

        List<int[]> route = throughRoute(ward, axis, source);
        if (route == null) {
            // No road crosses the ward. Keep whatever road cells it holds rather
            // than silently cutting the network; a ward with no route through is
            // the packer's problem, not the road graph's.
            for (int x = ward[0]; x <= ward[2]; x++) {
                for (int y = ward[1]; y <= ward[3]; y++) kept[x][y] = source[x][y];
            }
            return kept;
        }
        // The street carrying the route, not a one-cell line through it: a
        // convoy has width, and a road narrowed to a footpath is a road the
        // vehicles that need it can no longer use.
        for (int[] cell : route) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int x = cell[0] + dx;
                    int y = cell[1] + dy;
                    if (x < ward[0] || x > ward[2] || y < ward[1] || y > ward[3]) continue;
                    if (source[x][y]) kept[x][y] = true;
                }
            }
        }
        return kept;
    }

    /**
     * The shortest run of road across the ward along the approach, or null when
     * no road spans it. Breadth-first over road cells so the kept route is the
     * straightest one available rather than whichever the graph listed first.
     */
    private static List<int[]> throughRoute(int[] ward, TraversalAxis axis, boolean[][] roads) {
        boolean alongY = axis == TraversalAxis.SOUTH_TO_NORTH;
        int w = ward[2] - ward[0] + 1;
        int h = ward[3] - ward[1] + 1;
        int[][] cameFrom = new int[w][h];
        for (int[] column : cameFrom) Arrays.fill(column, -1);

        Deque<int[]> queue = new ArrayDeque<>();
        for (int i = 0; i < (alongY ? w : h); i++) {
            int x = alongY ? ward[0] + i : ward[0];
            int y = alongY ? ward[1] : ward[1] + i;
            if (!roads[x][y]) continue;
            cameFrom[x - ward[0]][y - ward[1]] = -2;
            queue.add(new int[]{ x, y });
        }
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            boolean atBack = alongY ? cell[1] == ward[3] : cell[0] == ward[2];
            if (atBack) return trace(cameFrom, ward, cell);
            for (int[] step : new int[][]{ { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } }) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (nx < ward[0] || nx > ward[2] || ny < ward[1] || ny > ward[3]) continue;
                if (!roads[nx][ny] || cameFrom[nx - ward[0]][ny - ward[1]] != -1) continue;
                cameFrom[nx - ward[0]][ny - ward[1]] =
                        (cell[0] - ward[0]) * h + (cell[1] - ward[1]);
                queue.add(new int[]{ nx, ny });
            }
        }
        return null;
    }

    private static List<int[]> trace(int[][] cameFrom, int[] ward, int[] end) {
        int h = ward[3] - ward[1] + 1;
        List<int[]> path = new ArrayList<>();
        int[] cell = end;
        while (cell != null) {
            path.add(cell);
            int prev = cameFrom[cell[0] - ward[0]][cell[1] - ward[1]];
            cell = prev < 0 ? null : new int[]{ ward[0] + prev / h, ward[1] + prev % h };
        }
        return path;
    }

    private static boolean hasAny(boolean[][] mask) {
        for (boolean[] column : mask) {
            for (boolean cell : column) {
                if (cell) return true;
            }
        }
        return false;
    }

    /**
     * Take the leaf fill back out of the ward.
     *
     * <p>Both the cells and the things generation recorded about them. A doodad
     * inside a demolished building would be debris floating on the parade
     * ground, and a point of interest or a tactical node still naming a building
     * that is gone would send a squad to hold a place that no longer exists.
     */
    private static void clearWard(GenContext ctx, int[] ward, Compound citadel,
                                  boolean[][] roads) {
        for (int x = ward[0]; x <= ward[2]; x++) {
            for (int y = ward[1]; y <= ward[3]; y++) {
                if (inCitadel(citadel, x, y, CITADEL_CLEARANCE) || roads[x][y]) continue;
                // Before the cell stops being walkable: a barrier is an edge
                // feature, and a window left behind by a demolished building is
                // scenery with nothing to belong to — and worse, an authored
                // edge the next stage cannot build on.
                ctx.grid.removeEdgeBarrier(x, y, Direction.E);
                ctx.grid.removeEdgeBarrier(x, y, Direction.N);
                ctx.grid.removeEdgeBarrier(x, y, Direction.W);
                ctx.grid.removeEdgeBarrier(x, y, Direction.S);
                ctx.grid.setWalkable(x, y, false);
                ctx.grid.setDoorway(x, y, false);
                ctx.topology.setWallDirMask(x, y, 0);
                ctx.topology.setBuildingKindHint(x, y, null);
                ctx.topology.setBuildingId(x, y, 0);
                ctx.topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
                ctx.topology.setGroundKind(x, y, GroundKind.DIRT);
                ctx.topology.setTag(x, y, Tag.WALL, false);
                ctx.topology.setTag(x, y, Tag.VEHICLE, false);
                ctx.topology.setTag(x, y, Tag.CROSSWALK, false);
            }
        }
        ctx.doodads.removeIf(d -> inWard(ward, d.cellX, d.cellY)
                && !inCitadel(citadel, d.cellX, d.cellY, CITADEL_CLEARANCE));
        ctx.pois.removeIf(poi -> inWard(ward, poi.anchorCellX, poi.anchorCellY)
                && !inCitadel(citadel, poi.anchorCellX, poi.anchorCellY, CITADEL_CLEARANCE));
        ctx.tactical.removeIf(node -> inWard(ward, node.anchorX, node.anchorY)
                && !inCitadel(citadel, node.anchorX, node.anchorY, CITADEL_CLEARANCE));
    }

    private static boolean[][] buildable(GenContext ctx, int[] ward, Compound citadel,
                                         boolean[][] roads) {
        boolean[][] mask = new boolean[ctx.width][ctx.height];
        for (int x = ward[0]; x <= ward[2]; x++) {
            for (int y = ward[1]; y <= ward[3]; y++) {
                mask[x][y] = !inCitadel(citadel, x, y, CITADEL_CLEARANCE) && !roads[x][y];
            }
        }
        return mask;
    }

    /**
     * The way in, from the side the attacker comes from. It stands in for the
     * gate the wall will be given, so the packer has somewhere to hang its
     * roadways off and the ward is entered rather than merely surrounded.
     */
    private static boolean[][] approach(GenContext ctx, int[] ward, TraversalAxis axis) {
        boolean[][] mask = new boolean[ctx.width][ctx.height];
        if (axis == TraversalAxis.SOUTH_TO_NORTH) {
            int midX = (ward[0] + ward[2]) / 2;
            for (int x = midX; x < midX + APPROACH_WIDTH && x <= ward[2]; x++) {
                for (int y = ward[1]; y <= Math.min(ward[1] + 6, ward[3]); y++) mask[x][y] = true;
            }
        } else {
            int midY = (ward[1] + ward[3]) / 2;
            for (int y = midY; y < midY + APPROACH_WIDTH && y <= ward[3]; y++) {
                for (int x = ward[0]; x <= Math.min(ward[0] + 6, ward[2]); x++) mask[x][y] = true;
            }
        }
        return mask;
    }

    /**
     * Give the ward's strongpoints tactical identity, so a garrison holds the
     * magazine and the gatehouse rather than treating the whole ward as open
     * ground. The citadel keeps its own command post; nothing here competes
     * with it.
     */
    private static void emitTacticalNodes(GenContext ctx, FortressInterior.Result result) {
        for (RoomPacker.Placed room : result.placed()) {
            TacticalNode.Kind kind = switch (room.purpose()) {
                case ARMORY -> TacticalNode.Kind.ARMORY;
                case BARRACKS -> TacticalNode.Kind.BARRACKS;
                case VEHICLE_BAY -> TacticalNode.Kind.ARMORY;
                default -> null;
            };
            if (kind == null) continue;
            int[] stand = standCell(ctx, room);
            if (stand == null) continue;
            ctx.tactical.add(new TacticalNode(kind, stand[0], stand[1],
                    room.originX(), room.originY(),
                    room.originX() + room.shape().width() - 1,
                    room.originY() + room.shape().height() - 1,
                    Faction.DEFENDER,
                    kind == TacticalNode.Kind.ARMORY ? 70 : 60, 3, false));
        }
    }

    /** A walkable cell the building actually owns, never a wall or a doorway. */
    private static int[] standCell(GenContext ctx, RoomPacker.Placed room) {
        for (int[] cell : room.shape().filled()) {
            int x = room.originX() + cell[0];
            int y = room.originY() + cell[1];
            if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) continue;
            if (ctx.grid.isWalkable(x, y) && !ctx.grid.isDoorway(x, y)) return new int[]{ x, y };
        }
        return null;
    }

    private static boolean inWard(int[] ward, int x, int y) {
        return x >= ward[0] && x <= ward[2] && y >= ward[1] && y <= ward[3];
    }

    private static boolean inCitadel(Compound citadel, int x, int y, int margin) {
        return citadel != null
                && x >= citadel.left - margin && x <= citadel.right + margin
                && y >= citadel.top - margin && y <= citadel.bottom + margin;
    }

    private static Compound findCitadel(List<Compound> compounds) {
        if (compounds == null) return null;
        for (Compound compound : compounds) {
            if (compound.kind == BlockKind.MILITARY_BASE
                    && compound.biome == BiomeKind.FORTRESS_DISTRICT) return compound;
        }
        return null;
    }

    private static int[] fortressBand(BiomeMap biomeMap, int w, int h) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        int top = Integer.MAX_VALUE, bot = Integer.MIN_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (biomeMap.biomeAt(x, y) != BiomeKind.FORTRESS_DISTRICT) continue;
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
                top = Math.min(top, y);
                bot = Math.max(bot, y);
            }
        }
        return lo == Integer.MAX_VALUE ? null : new int[]{ lo, top, hi, bot };
    }

    /**
     * Closes the airfield to everything stamped after it.
     *
     * <p>The lot is marked unbuildable while the ward packs itself, but that
     * array is the packer's and dies with the stage. Four stampers run later —
     * the fortress wall, the defence posts, the compound-perimeter defenders
     * and the overwatch towers — and each asks the reservation mask whether it
     * may close a cell. Told nothing, they put guns on the runway: measured
     * across the conquest matrix, thirty-three of forty-eight maps had a
     * guardpost standing on the airfield, sixty-four in all.
     *
     * <p>Widening {@link BspKeys#ROAD_RESERVATION} rather than adding a second
     * mask, which is the shape {@code VehicleCorridorStage} already
     * established: a stamper asking "may I close this cell" should need one
     * answer, not a list of exemptions that grows every time somebody reserves
     * something. The whole rect goes in, clearance included — the clearance is
     * there so nothing crowds the field, which is exactly what a gun on its
     * edge would do.
     *
     * <p>Ordering is what makes this safe. Every <em>filler</em> that reads the
     * mask has already run by the time the ward lays its lot, so widening it
     * here cannot change how the city was built; only the stampers that follow
     * see it.
     */
    private static void reserveAgainstLaterStampers(GenContext ctx, int[] lot) {
        boolean[][] reservation = ctx.get(BspKeys.ROAD_RESERVATION);
        if (reservation == null) return;
        int w = reservation.length;
        int h = w == 0 ? 0 : reservation[0].length;
        for (int x = Math.max(0, lot[0]); x <= Math.min(w - 1, lot[2]); x++) {
            for (int y = Math.max(0, lot[1]); y <= Math.min(h - 1, lot[3]); y++) {
                reservation[x][y] = true;
            }
        }
    }
}