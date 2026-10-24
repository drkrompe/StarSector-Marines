package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fort's wall stays closed once its nests, pads and missile mounts are stood: with the gates sealed, nothing outside
 * the works can walk to the open floor inside them, and no plan wall cell has been opened or has lost its wall flag.
 */
class FortWallClosedTest {

    private static final int W = 240;
    private static final int H = 160;

    private static GenContext field() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L), W, H, 7L);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, topology, null));
        ctx.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(topology, 7L));
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL);
        return ctx;
    }

    @Test
    void theWallIsClosedApartFromItsGatesAtEveryTurn() {
        for (FortPlan plan : new FortPlan[]{BatteryFort.PLAN, ShieldFort.PLAN}) {
            for (int turn = 0; turn < 4; turn++) {
                GenContext ctx = field();
                CompoundLayout.Placement at = plan.layout().at(60, 40, turn);
                FortStage.stamp(ctx, plan, at, 3, true);
                String label = plan.kind() + " turn " + turn;

                boolean[][] sealed = new boolean[W][H];
                for (FortPlan.Piece piece : plan.gatehouse()) {
                    if (piece.kind() != FortPlan.PieceKind.GATE) continue;
                    for (int lx = piece.x(); lx < piece.x() + piece.width(); lx++) {
                        for (int ly = piece.y(); ly < piece.y() + piece.height(); ly++) {
                            int[] c = at.cell(lx, ly);
                            sealed[c[0]][c[1]] = true;
                        }
                    }
                }
                for (int lx = 0; lx < plan.width(); lx++) {
                    for (int ly = 0; ly < plan.height(); ly++) {
                        if (!plan.isWall(lx, ly)) continue;
                        int[] c = at.cell(lx, ly);
                        if (sealed[c[0]][c[1]]) continue;
                        assertFalse(ctx.grid.isWalkable(c[0], c[1]), label + " wall cell " + lx + "," + ly + " is still solid");
                        assertTrue(ctx.topology.isWall(c[0], c[1]), label + " wall cell " + lx + "," + ly + " is still drawn as wall");
                    }
                }

                boolean[][] seen = new boolean[W][H];
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{1, 1});
                seen[1][1] = true;
                while (!queue.isEmpty()) {
                    int[] c = queue.poll();
                    // A step honours the grid's edges: a window is open floor with a barrier on its outer edge.
                    for (Direction d : Direction.CARDINALS) {
                        int x = c[0] + d.dx, y = c[1] + d.dy;
                        if (x < 0 || y < 0 || x >= W || y >= H || seen[x][y] || sealed[x][y] || !ctx.grid.isWalkable(x, y)) continue;
                        if (!ctx.grid.isEdgePassable(c[0], c[1], d)) continue;
                        seen[x][y] = true;
                        queue.add(new int[]{x, y});
                    }
                }
                for (int lx = 0; lx < plan.width(); lx++) {
                    for (int ly = 0; ly < plan.height(); ly++) {
                        if (!plan.isWorks(lx, ly) || plan.isWall(lx, ly)) continue;
                        int[] c = at.cell(lx, ly);
                        if (sealed[c[0]][c[1]] || !ctx.grid.isWalkable(c[0], c[1])) continue;
                        if (isNestSite(plan, lx, ly)) continue;
                        assertFalse(seen[c[0]][c[1]], label + " works floor " + lx + "," + ly + " is reachable from outside without a gate");
                    }
                }
            }
        }
    }

    @Test
    void noWallCellStandsOneThickWithBothFacesOpenBesideANest() {
        for (FortPlan plan : new FortPlan[]{BatteryFort.PLAN, ShieldFort.PLAN}) {
            for (int[] nest : plan.nests()) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        int x = nest[0] + dx, y = nest[1] + dy;
                        if (!plan.isWall(x, y)) continue;
                        boolean pinchedY = !plan.isWall(x, y + 1) && !plan.isWall(x, y - 1);
                        boolean pinchedX = !plan.isWall(x + 1, y) && !plan.isWall(x - 1, y);
                        assertFalse(pinchedX || pinchedY, plan.kind() + " wall cell " + x + "," + y + " beside the nest at "
                                + nest[0] + "," + nest[1] + " is one thick with both faces open, which draws as a hole");
                    }
                }
            }
        }
    }

    /** A nest's own notch cell is works ground on the wall's outer face; it is not the works' interior. */
    private static boolean isNestSite(FortPlan plan, int lx, int ly) {
        for (int[] n : plan.nests()) if (n[0] == lx && n[1] == ly) return true;
        return false;
    }
}
