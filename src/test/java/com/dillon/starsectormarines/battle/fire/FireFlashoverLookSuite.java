package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rooms flashing over (`fire-nouns.md`, Buildings), in the look preset: the biggest light-walled house near the
 * window, and a flamer charge's splash into it two seconds in, as it lands, without the squad. {@code one} puts one
 * charge into the room floor nearest the house's middle: that room flashes over and the rest of the house stands.
 * {@code three} puts a charge into the room floors nearest three of its corners: each of those rooms goes, and most
 * of the house with them. Stills at fixed moments (lit, flashed, at its hottest, burnt out), at play zoom and close.
 */
public class FireFlashoverLookSuite implements SnapshotSuite {

    private static final int WINDOW_W = 40, WINDOW_H = 24;
    private static final int SECONDS = 90, DROP_TICK = 60, EVERY_TICKS = 30;
    /** A flamer charge's splash: its blast radius and heat ({@code weapon.flamer}). */
    private static final float CHARGE_RADIUS = 1.3f;
    private static final int CHARGE_HEAT = 2200;
    /** How far from the window's middle a house may stand: its biggest has the most rooms to show. */
    private static final int SEARCH = 70;

    @Override public String id() { return "fire-flashover"; }

    @Override public String label() { return "Look: a flamer charge flashes one room over; charges in three rooms burn most of the house"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        out.addAll(LookReview.record(context.modRoot(), shot("one", 1)));
        out.addAll(LookReview.record(context.modRoot(), shot("three", 3)));
        return out;
    }

    private static LookReview.Shot shot(String name, int charges) {
        return new LookReview.Shot(name, LookGround.verdant(), WINDOW_W, WINDOW_H, placement -> {
            int[] house = houseFor(placement);
            List<int[]> aims = chargesInto(placement.map(), house, charges);
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            placement.beforeTick(s -> {
                if (s.getSimTickIndex() == DROP_TICK) {
                    for (int[] at : aims) s.fireField().addHeat(at[0] + 0.5f, at[1] + 0.5f, CHARGE_RADIUS, CHARGE_HEAT);
                }
                log(name, s, house);
            });
            return sim;
        }, placement -> {
            int[] house = houseFor(placement);
            float cx = (house[0] + house[2] + 1) / 2f, cy = (house[1] + house[3] + 1) / 2f;
            return List.of(LookReview.View.play(cx, cy), LookReview.View.close("close", cx, cy, 26f));
        }, 30 * SECONDS, EVERY_TICKS, 4).withStills(3, 12, 30, 85);
    }

    /** The house a shot burns, the same whenever it is asked of the same placement. */
    private static int[] houseFor(LookReview.Placement placement) {
        LookGround.Site site = placement.site();
        return nearestLightHouse(placement.map(), site.x(WINDOW_W / 2), site.y(WINDOW_H / 2));
    }

    /** The largest light-walled house with a doorway within {@link #SEARCH} cells of a point, with its walls: {x0, y0, x1, y1}. */
    private static int[] nearestLightHouse(MapResult map, int px, int py) {
        NavigationGrid grid = map.grid;
        CellTopology topo = map.topology;
        int w = grid.getWidth(), h = grid.getHeight(), max = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) max = Math.max(max, topo.getBuildingId(x, y));
        int[] minX = new int[max + 1], minY = new int[max + 1], maxX = new int[max + 1], maxY = new int[max + 1];
        boolean[] heavy = new boolean[max + 1], door = new boolean[max + 1];
        Arrays.fill(minX, Integer.MAX_VALUE);
        Arrays.fill(minY, Integer.MAX_VALUE);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int b = topo.getBuildingId(x, y);
            if (b <= 0) continue;
            minX[b] = Math.min(minX[b], x); minY[b] = Math.min(minY[b], y);
            maxX[b] = Math.max(maxX[b], x); maxY[b] = Math.max(maxY[b], y);
            if (grid.isDoorway(x, y)) door[b] = true;
            if (topo.getGroundKind(x, y) == CellTopology.GroundKind.STRIPED) heavy[b] = true;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (grid.inBounds(nx, ny) && !grid.isWalkable(nx, ny) && (topo.isWall(nx, ny) || topo.isWindow(nx, ny))
                        && grid.getWallHp(nx, ny) > BattleFire.LIGHT_WALL_HP) heavy[b] = true;
            }
        }
        int best = -1, bestArea = 0;
        for (int b = 1; b <= max; b++) {
            if (minX[b] == Integer.MAX_VALUE || heavy[b] || !door[b]) continue;
            int bw = maxX[b] - minX[b] + 1, bh = maxY[b] - minY[b] + 1;
            if (bw > 24 || bh > 24) continue;
            long dx = (minX[b] + maxX[b]) / 2 - px, dy = (minY[b] + maxY[b]) / 2 - py;
            if (dx * dx + dy * dy > (long) SEARCH * SEARCH) continue;
            if (bw * bh > bestArea) { bestArea = bw * bh; best = b; }
        }
        if (best < 0) throw new IllegalStateException("no light-walled house on the look ground");
        return new int[]{minX[best] - 1, minY[best] - 1, maxX[best] + 1, maxY[best] + 1};
    }

    /** Where the charges land: one on the room floor nearest the house's middle, several nearest its corners in turn. */
    private static List<int[]> chargesInto(MapResult map, int[] house, int charges) {
        float cx = (house[0] + house[2]) / 2f, cy = (house[1] + house[3]) / 2f;
        float[][] aims = charges == 1 ? new float[][]{{cx, cy}}
                : new float[][]{{house[0] + 2.5f, house[1] + 2.5f}, {house[2] - 2.5f, house[3] - 2.5f},
                        {house[2] - 2.5f, house[1] + 2.5f}, {house[0] + 2.5f, house[3] - 2.5f}};
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < charges && i < aims.length; i++) out.add(roomFloorNear(map, house, aims[i][0], aims[i][1]));
        return out;
    }

    private static int[] roomFloorNear(MapResult map, int[] house, float ax, float ay) {
        int[] best = null;
        float bestD = Float.MAX_VALUE;
        for (int y = house[1]; y <= house[3]; y++) for (int x = house[0]; x <= house[2]; x++) {
            if (!roomFloor(map, x, y)) continue;
            float d = (x + .5f - ax) * (x + .5f - ax) + (y + .5f - ay) * (y + .5f - ay);
            if (d < bestD) { bestD = d; best = new int[]{x, y}; }
        }
        if (best == null) throw new IllegalStateException("no room floor in the house");
        return best;
    }

    /** Indoor floor with indoor floor on every side: the middle of a room, not a doorway or a wall's foot. */
    private static boolean roomFloor(MapResult map, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int nx = x + dx, ny = y + dy;
            if (!map.grid.inBounds(nx, ny) || !map.grid.isWalkable(nx, ny)) return false;
            CellTopology.GroundKind g = map.topology.getGroundKind(nx, ny);
            if (g != CellTopology.GroundKind.INDOOR && g != CellTopology.GroundKind.TILE) return false;
        }
        return true;
    }

    /** Every five seconds: the house's cells burning and burnt. */
    private static void log(String name, BattleSimulation sim, int[] house) {
        if (sim.getSimTickIndex() % 150 != 0) return;
        FireField fire = sim.fireField();
        int burning = 0, burnt = 0;
        for (int y = house[1]; y <= house[3]; y++) for (int x = house[0]; x <= house[2]; x++) {
            if (fire.isBurning(x, y)) burning++;
            else if (fire.isBurnt(x, y)) burnt++;
        }
        System.out.printf("    [fire-flashover %s] t=%ds house cells burning %d, burnt %d%n",
                name, sim.getSimTickIndex() / 30, burning, burnt);
    }
}
