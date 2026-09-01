package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressInterior;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctBoundary;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctFill;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills and walls every programmed precinct on the map.
 *
 * <p>Runs after the ordinary fills, because a walled place's wall is drawn
 * around what its interior turned out to be — the order
 * {@code compound-programs.md} argues for, carried up from a compound to a
 * whole place. What it packs into is the precinct's own claimed shape, which is
 * irregular; the packer never wanted a rectangle, so nothing here has to make
 * one for it.
 *
 * <p>Unlike {@link FortressWardStage} this is not conquest-only and not one:
 * every programmed precinct in the plan is packed and walled, so a map with two
 * garrisons gets two.
 */
public final class PrecinctWardStage implements GenStage {

    /** Matches the fortress wall's own, so a breach reads the same either way. */
    private static final int WALL_HP = 240;

    /** Military floor, so a breached wall reads as one rather than as street. */
    private static final GroundKind WALL_GROUND = GroundKind.STRIPED;

    @Override
    public void run(GenContext ctx) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        int[][] road = ctx.get(BspKeys.PRECINCT_ROAD);
        if (plan == null || claim == null || road == null) return;

        Map<String, List<FortressBuilding>> unbuilt = new LinkedHashMap<>();
        Map<String, Integer> shortFields = new LinkedHashMap<>();
        for (int i = 0; i < plan.precincts().size(); i++) {
            Precinct precinct = plan.precincts().get(i);
            if (precinct.isProgrammed()) {
                PrecinctFill.Masks masks =
                        PrecinctFill.masks(claim, road, i, ctx.width, ctx.height);
                AirbaseLot.Facing facing = AirbaseLot.Facing.of(facing(precinct, ctx));
                // Lots first, buildings second. A lot claimed after packing gets
                // whatever shape the leftovers had, which is a shallow strip an
                // aircraft cannot use; taken from the middle it severs the
                // place. Reserved up front and from the far end, the packer
                // simply builds around it.
                List<Reserved> fields = reserveAirfields(precinct, masks, facing, ctx);
                if (fields.size() < precinct.program().airfields()) {
                    shortFields.put(precinct.name(),
                            precinct.program().airfields() - fields.size());
                }
                FortressInterior.Result result =
                        PrecinctFill.pack(ctx, precinct, masks, facing(precinct, ctx));
                for (Reserved field : fields) {
                    build(ctx, field, facing);
                }
                // Recorded rather than dropped. Ground is granted from the
                // program, but granted ground is not the same as ground the
                // packer can use: measured on a cramped map, a garrison owed
                // six barrack blocks, was given every cell it asked for, and
                // built three. Discarding this makes that indistinguishable
                // from a smaller garrison.
                if (!result.unplaced().isEmpty()) {
                    unbuilt.put(precinct.name(), List.copyOf(result.unplaced()));
                }
            }
            if (precinct.boundary() == Precinct.Boundary.WALLED) {
                stampWall(ctx, claim, road, i);
            }
        }
        ctx.put(BspKeys.UNPLACED_PROGRAM, Map.copyOf(unbuilt));
        ctx.put(BspKeys.UNPLACED_AIRFIELDS, Map.copyOf(shortFields));
    }

    /** A lot the precinct has set aside, and the size that fitted. */
    private record Reserved(int[] rect, AirbaseLot.Size size) { }

    /**
     * Ladder of sizes an airfield is tried at, largest first.
     *
     * <p>A place that cannot host a station may still host a strip, and a base
     * with one berth is an air arm that flies and a thing that can be taken to
     * stop it. Falling straight to none because the biggest did not fit would
     * throw away most of what the count was asking for.
     */
    private static final AirbaseLot.Size[] SIZES = {
            AirbaseLot.Size.STATION, AirbaseLot.Size.FIELD,
            AirbaseLot.Size.PAD, AirbaseLot.Size.STRIP };

    /**
     * Sets aside ground for each airfield the program owes, marking it
     * unbuildable so the packer works around it.
     *
     * <p>Placed toward the precinct's edge rather than its middle, for the
     * reason the shipped ward gives: a lot through the centre severs the spine
     * everything else has to cross. Ranked by distance from the centroid, so a
     * second airfield lands at a different end than the first rather than
     * beside it.
     */
    private static List<Reserved> reserveAirfields(Precinct precinct,
                                                   PrecinctFill.Masks masks,
                                                   AirbaseLot.Facing facing,
                                                   GenContext ctx) {
        List<Reserved> out = new ArrayList<>();
        int[] centre = centroid(masks.buildable(), ctx);
        for (int i = 0; i < precinct.program().airfields(); i++) {
            Reserved placed = null;
            for (AirbaseLot.Size size : SIZES) {
                placed = findLot(masks.buildable(), size, facing, centre, ctx);
                if (placed != null) break;
            }
            if (placed == null) break;
            for (int x = placed.rect()[0]; x <= placed.rect()[2]; x++) {
                for (int y = placed.rect()[1]; y <= placed.rect()[3]; y++) {
                    masks.buildable()[x][y] = false;
                }
            }
            out.add(placed);
        }
        return out;
    }

    /** The free rectangle of this size furthest from the middle of the place. */
    private static Reserved findLot(boolean[][] buildable, AirbaseLot.Size size,
                                    AirbaseLot.Facing facing, int[] centre, GenContext ctx) {
        int spanX = AirbaseLot.reservedSpanX(size, facing);
        int spanY = AirbaseLot.reservedSpanY(size, facing);
        int[] best = null;
        long bestDist = -1;
        for (int x = 0; x + spanX <= ctx.width; x++) {
            for (int y = 0; y + spanY <= ctx.height; y++) {
                if (!clear(buildable, x, y, spanX, spanY)) continue;
                long dx = x + spanX / 2 - centre[0];
                long dy = y + spanY / 2 - centre[1];
                long dist = dx * dx + dy * dy;
                if (dist > bestDist) {
                    bestDist = dist;
                    best = new int[]{x, y, x + spanX - 1, y + spanY - 1};
                }
            }
        }
        return best == null ? null : new Reserved(best, size);
    }

    private static boolean clear(boolean[][] buildable, int x0, int y0, int w, int h) {
        for (int x = x0; x < x0 + w; x++) {
            for (int y = y0; y < y0 + h; y++) {
                if (!buildable[x][y]) return false;
            }
        }
        return true;
    }

    private static int[] centroid(boolean[][] mask, GenContext ctx) {
        long cx = 0;
        long cy = 0;
        int n = 0;
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                if (!mask[x][y]) continue;
                cx += x;
                cy += y;
                n++;
            }
        }
        return n == 0 ? new int[]{ctx.width / 2, ctx.height / 2}
                : new int[]{(int) (cx / n), (int) (cy / n)};
    }

    /**
     * Authors the field on its reserved ground and closes it to what runs later.
     *
     * <p>The reservation the packer used is this stage's own array and dies with
     * it. Four stampers run afterwards and each asks the road reservation
     * whether it may close a cell; told nothing, they put guns on the runway.
     */
    private static void build(GenContext ctx, Reserved field, AirbaseLot.Facing facing) {
        int[] lot = field.rect();
        int clear = field.size().clearance();
        new AirbaseLot(lot[0] + clear, lot[1] + clear, lot[2] - clear, lot[3] - clear,
                facing, field.size()).author(ctx, ctx.rng);
        boolean[][] reservation = ctx.get(BspKeys.ROAD_RESERVATION);
        if (reservation == null) return;
        for (int x = Math.max(0, lot[0]); x <= Math.min(ctx.width - 1, lot[2]); x++) {
            for (int y = Math.max(0, lot[1]); y <= Math.min(ctx.height - 1, lot[3]); y++) {
                reservation[x][y] = true;
            }
        }
    }

    /**
     * Which way the place is arranged against.
     *
     * <p>A precinct has no traversal axis — that is the point of the model — but
     * its interior still packs into depth bands measured from an approach, so it
     * needs an answer. The long axis of the map is the one an attacker most
     * likely crosses, which is the same assumption conquest makes and no worse
     * here.
     */
    private static TraversalAxis facing(Precinct precinct, GenContext ctx) {
        return ctx.width >= ctx.height
                ? TraversalAxis.WEST_TO_EAST
                : TraversalAxis.SOUTH_TO_NORTH;
    }

    /**
     * Draws the wall on the precinct's outline, less its gates.
     *
     * <p>The exterior mask is simply "the neighbour is not mine". A precinct's
     * claim <em>is</em> its interior, so there is no courtyard rect to reason
     * about the way a stamped rectangular fortress needs — which is the same
     * simplification the whole model buys, arriving here.
     *
     * <p>A wall with no exterior face draws its block's transparent centre, so a
     * face on every open side is not decoration: without it the wall renders as
     * nothing at all.
     */
    private static void stampWall(GenContext ctx, int[][] claim, int[][] road, int who) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        boolean[][] wall =
                PrecinctBoundary.wall(claim, road, who, ctx.width, ctx.height);
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                if (!wall[x][y]) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, WALL_HP);
                topology.setWall(x, y, true);
                topology.setGroundKind(x, y, WALL_GROUND);
            }
        }
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                if (!wall[x][y]) continue;
                int mask = 0;
                if (exterior(claim, who, x, y + 1, ctx)) mask |= CellTopology.WALL_DIR_N;
                if (exterior(claim, who, x, y - 1, ctx)) mask |= CellTopology.WALL_DIR_S;
                if (exterior(claim, who, x + 1, y, ctx)) mask |= CellTopology.WALL_DIR_E;
                if (exterior(claim, who, x - 1, y, ctx)) mask |= CellTopology.WALL_DIR_W;
                topology.setWallDirMask(x, y, mask);
            }
        }
    }

    /** Off the map counts as outside, so an edge-hugging wall still gets a face. */
    private static boolean exterior(int[][] claim, int who, int x, int y, GenContext ctx) {
        if (x < 0 || x >= ctx.width || y < 0 || y >= ctx.height) return true;
        return claim[x][y] != who;
    }
}
