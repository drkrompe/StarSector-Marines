package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
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
        for (int i = 0; i < plan.precincts().size(); i++) {
            Precinct precinct = plan.precincts().get(i);
            if (precinct.isProgrammed()) {
                PrecinctFill.Masks masks =
                        PrecinctFill.masks(claim, road, i, ctx.width, ctx.height);
                FortressInterior.Result result =
                        PrecinctFill.pack(ctx, precinct, masks, facing(precinct, ctx));
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
