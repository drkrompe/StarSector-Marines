package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end generation contract for campaign-backed civilian spaceports. */
class SpaceportDistrictGenerationTest {

    @Test
    void spaceportWorldPublishesUsableBerthsOnOrdinaryUrbanMap() {
        TargetProfile profile = new TargetProfile(5, 6, 1, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.SPACEPORT),
                SurfacePalette.ROCK, SettlementLink.ROAD);

        MapResult map = new BspCityGenerator().generate(80, 80, 42L, null, profile);
        assertTrue(map.landingPads.size() >= 4,
                "tier-one spaceport should provide deployment plus a civilian berth; got "
                        + map.landingPads.size());
        for (LandingPad pad : map.landingPads) {
            assertTrue(pad.isClear(map.grid, map.topology));
        }

        int apronArea = largestSpaceportApron(map);
        assertTrue(apronArea >= 180,
                "civilian spaceport should read as one open killing ground; got " + apronArea);
    }

    @Test
    void neutralWorldDoesNotForceCivilianSpaceportDistrict() {
        MapResult map = new BspCityGenerator().generate(
                80, 80, 42L, null, TargetProfile.NEUTRAL);
        // Random civic pads remain allowed. What matters is that the neutral
        // profile doesn't receive the guaranteed four-berth port contract.
        assertTrue(map.landingPads.size() < 3);
    }

    /**
     * The largest apron the campus reads as, hopping a single walkable cell.
     *
     * <p>A strict flood over apron ground can never span a campus, and that is
     * a property of the design rather than of any seed: a compound filler is
     * required to leave the one-cell road centerline between two members
     * drivable, so the tarmac either side of it is always two regions. Measured
     * strictly, a size-5 port met this on 34 seeds of 60 while the campus was
     * built correctly every time -- the assertion was reporting the size of
     * whichever member leaf happened to be biggest.
     *
     * <p>What the apron is for is an open killing ground: wide walkable tarmac
     * with long sightlines. A marine walks over a one-cell centerline and sees
     * across it, so it divides the ground kind without dividing the ground.
     * Hopping exactly one walkable cell is what separates that from a real
     * street, which is three cells wide and does divide. Measured that way the
     * same sweep passes on 58 of 60.
     */
    private static int largestSpaceportApron(MapResult map) {
        int w = map.grid.getWidth();
        int h = map.grid.getHeight();
        boolean[][] seen = new boolean[w][h];
        int largest = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int sy = 0; sy < h; sy++) {
            for (int sx = 0; sx < w; sx++) {
                if (seen[sx][sy] || !isApron(map, sx, sy)) continue;
                java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
                queue.add(new int[]{sx, sy});
                seen[sx][sy] = true;
                int area = 0;
                while (!queue.isEmpty()) {
                    int[] p = queue.removeFirst();
                    area++;
                    for (int[] d : dirs) {
                        int nx = p[0] + d[0];
                        int ny = p[1] + d[1];
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        if (!seen[nx][ny] && isApron(map, nx, ny)) {
                            seen[nx][ny] = true;
                            queue.addLast(new int[]{nx, ny});
                            continue;
                        }
                        // Hop the reserved centerline: one walkable cell, tarmac beyond.
                        int bx = p[0] + 2 * d[0];
                        int by = p[1] + 2 * d[1];
                        if (bx < 0 || bx >= w || by < 0 || by >= h || seen[bx][by]) continue;
                        if (isApron(map, nx, ny)) continue;
                        if (!map.grid.isWalkable(nx, ny) || map.topology.isWall(nx, ny)) continue;
                        if (!isApron(map, bx, by)) continue;
                        seen[bx][by] = true;
                        queue.addLast(new int[]{bx, by});
                    }
                }
                largest = Math.max(largest, area);
            }
        }
        return largest;
    }

    private static boolean isApron(MapResult map, int x, int y) {
        CellTopology.GroundKind kind = map.topology.getGroundKind(x, y);
        return kind == CellTopology.GroundKind.STRIPED
                || kind == CellTopology.GroundKind.LZ_MARKER
                || kind == CellTopology.GroundKind.BRICK;
    }
}
