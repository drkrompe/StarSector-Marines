package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofCover;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Battle-map roofs in the look preset (`battle-render-nouns.md`, Roofs): the town's roofs in their styles, the same
 * roofs broken by what hit them, and how far a roof covers the walls under it.
 *
 * <p>{@code intact} frames the block of the look ground with the most buildings, at play zoom and close. {@code damaged}
 * plays the same block for a minute after, two seconds in, a shell salvo lands on one house (holes), a bomb on a
 * second (its middle falls in, its edge is holed), a flamer charge goes into a room of a third (the room flashes over,
 * its roof burns through, its light walls catch and their eaves burn) and an exterior wall of a fourth is knocked down
 * (the roof it carried falls in). {@code walls} is one close frame of an undamaged house, with its roof and without,
 * so the roof over each wall can be read against the wall itself: interior walls hidden, exterior walls showing past
 * the eave.
 */
public class RoofLookSuite implements SnapshotSuite {

    private static final int WINDOW_W = 44, WINDOW_H = 25;
    private static final int SECONDS = 60, HIT_TICK = 60, EVERY_TICKS = 30;
    private static final float CLOSE_CELLS = 26f;

    @Override public String id() { return "roofs"; }

    @Override public String label() { return "Look: roof styles intact, broken by shells, a bomb, fire and a fallen wall, and over walls"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        out.addAll(LookReview.record(context.modRoot(), shot("intact", false).withStills(1)));
        out.addAll(LookReview.record(context.modRoot(), shot("damaged", true).withStills(3, 6, 20, 60)));
        out.addAll(walls(context));
        return out;
    }

    private static LookReview.Shot shot(String name, boolean damage) {
        return new LookReview.Shot(name, LookGround.verdant(), 8, 8, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            if (damage) {
                int[] town = town(placement.map());
                List<int[]> houses = houses(placement.map(), town);
                placement.beforeTick(s -> {
                    if (s.getSimTickIndex() == HIT_TICK) strike(s, houses);
                });
            }
            return sim;
        }, placement -> {
            int[] town = town(placement.map());
            float cx = town[0] + WINDOW_W / 2f, cy = town[1] + WINDOW_H / 2f;
            return List.of(LookReview.View.play(cx, cy), LookReview.View.close("close", cx, cy, CLOSE_CELLS));
        }, damage ? 30 * SECONDS : 30, EVERY_TICKS, 4);
    }

    /** The damage: each blow through the path a battle's own would take. */
    private static void strike(BattleSimulation sim, List<int[]> houses) {
        int[] shelled = houses.get(0);
        float[][] salvo = {{0.3f, 0.35f}, {0.65f, 0.6f}, {0.45f, 0.75f}};
        for (float[] at : salvo) {
            sim.detonateNow(new PendingDetonation(CombatTelemetryService.NO_ATTACKER,
                    lerp(shelled[0], shelled[2], at[0]), lerp(shelled[1], shelled[3], at[1]), 0f,
                    1.6f, 60f, 10f, 40, Faction.DEFENDER, true));
        }
        int[] bombed = houses.get(1);
        sim.detonateNow(new PendingDetonation(CombatTelemetryService.NO_ATTACKER,
                (bombed[0] + bombed[2] + 1) / 2f, (bombed[1] + bombed[3] + 1) / 2f, 0f,
                3.6f, 80f, 16f, 160, Faction.DEFENDER, true, 1.2f, true, false));
        int[] room = null;
        for (int i = 2; i < houses.size() && room == null; i++) {
            if (i == 3) continue;
            room = roomFloor(sim, houses.get(i));
        }
        if (room != null) {
            // Three charges, as a flamer team would put into a house: one room alight is a small fire.
            for (int[] at : new int[][]{{0, 0}, {2, 0}, {0, 2}}) {
                sim.fireField().addHeat(room[0] + at[0] + 0.5f, room[1] + at[1] + 0.5f, 1.3f, 2200);
            }
        }
        int[] walled = houses.get(3);
        int[] wall = exteriorWall(sim.getTopology(), walled);
        if (wall != null) sim.mapEditor().damageWall(wall[0], wall[1], 100000);
        System.out.printf("    [roofs] shells on %s, bomb on %s, fire at %s, wall down at %s%n",
                Arrays.toString(shelled), Arrays.toString(bombed),
                Arrays.toString(room), Arrays.toString(wall));
    }

    /** One close frame of the town's first house that nothing hits, with its roof and without. */
    private static List<SnapshotArtifact> walls(SnapshotContext context) throws Exception {
        MapResult map = LookGround.verdant().generate();
        int[] town = town(map);
        int[] house = houses(map, town).get(4);
        LookGround ground = LookGround.verdant();
        LookGround.Site site = LookGround.openSite(map, 8, 8);
        LookReview.Placement placement = new LookReview.Placement(ground, map, site);
        BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
        try {
            // A second of play, so the roofs have faded in as a battle's do.
            for (int tick = 0; tick < 30; tick++) sim.advance(BattleSimulation.TICK_DT);
            float cx = (house[0] + house[2] + 1) / 2f, cy = (house[1] + house[3] + 1) / 2f;
            float across = Math.max(16f, (house[2] - house[0] + 1) * 1.8f);
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            List<SnapshotArtifact> out = new ArrayList<>();
            BattleReviewFrameRenderer roofed = BattleReviewFrameRenderer.look(context.modRoot(), LookReview.WIDTH,
                    LookReview.HEIGHT, cx, cy, across);
            out.add(new SnapshotArtifact("look/walls-roofed.png", roofed.render(sim, "", ReviewAnnotations.NONE, fx)));
            BattleReviewFrameRenderer bare = BattleReviewFrameRenderer.look(context.modRoot(), LookReview.WIDTH,
                    LookReview.HEIGHT, cx, cy, across).withoutRoofs();
            out.add(new SnapshotArtifact("look/walls-bare.png", bare.render(sim, "", ReviewAnnotations.NONE, fx)));
            return out;
        } finally {
            sim.close();
        }
    }

    /** The {@link #WINDOW_W} x {@link #WINDOW_H} window, {x0, y0}, holding the most whole buildings. */
    static int[] town(MapResult map) {
        CellTopology t = map.topology;
        int w = t.getWidth(), h = t.getHeight();
        int[] best = {0, 0};
        int bestCount = -1;
        for (int y0 = 0; y0 + WINDOW_H <= h; y0 += 3) {
            for (int x0 = 0; x0 + WINDOW_W <= w; x0 += 3) {
                int count = 0;
                for (int[] b : buildingBoxes(map)) {
                    if (b[0] > x0 && b[1] > y0 && b[2] < x0 + WINDOW_W - 1 && b[3] < y0 + WINDOW_H - 1) count++;
                }
                if (count > bestCount) {
                    bestCount = count;
                    best = new int[]{x0, y0};
                }
            }
        }
        return best;
    }

    /** Whole buildings inside the town window, largest first, as {x0, y0, x1, y1} of their floor. */
    static List<int[]> houses(MapResult map, int[] town) {
        List<int[]> out = new ArrayList<>();
        for (int[] b : buildingBoxes(map)) {
            if (b[0] > town[0] && b[1] > town[1] && b[2] < town[0] + WINDOW_W - 1 && b[3] < town[1] + WINDOW_H - 1) {
                out.add(b);
            }
        }
        out.sort((a, b) -> Integer.compare((b[2] - b[0] + 1) * (b[3] - b[1] + 1), (a[2] - a[0] + 1) * (a[3] - a[1] + 1)));
        if (out.size() < 5) throw new IllegalStateException("the town window holds " + out.size() + " buildings");
        return out;
    }

    private static List<int[]> cachedBoxes;
    private static MapResult cachedFor;

    private static List<int[]> buildingBoxes(MapResult map) {
        if (cachedFor == map) return cachedBoxes;
        CellTopology t = map.topology;
        int max = 0;
        for (int y = 0; y < t.getHeight(); y++) for (int x = 0; x < t.getWidth(); x++) max = Math.max(max, t.getBuildingId(x, y));
        int[][] boxes = new int[max + 1][];
        for (int y = 0; y < t.getHeight(); y++) for (int x = 0; x < t.getWidth(); x++) {
            int id = t.getBuildingId(x, y);
            if (id == 0) continue;
            int[] b = boxes[id];
            if (b == null) boxes[id] = new int[]{x, y, x, y};
            else {
                b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y);
                b[2] = Math.max(b[2], x); b[3] = Math.max(b[3], y);
            }
        }
        List<int[]> out = new ArrayList<>();
        for (int[] b : boxes) if (b != null) out.add(b);
        cachedBoxes = out;
        cachedFor = map;
        return out;
    }

    /** An indoor floor cell with indoor floor on each side of it, nearest the house's middle: where a charge lights a room. */
    private static int[] roomFloor(BattleSimulation sim, int[] house) {
        CellTopology t = sim.getTopology();
        float mx = (house[0] + house[2]) / 2f, my = (house[1] + house[3]) / 2f;
        int[] best = null;
        float bestD = Float.MAX_VALUE;
        for (int y = house[1]; y <= house[3]; y++) for (int x = house[0]; x <= house[2]; x++) {
            boolean open = true;
            for (int dy = -1; dy <= 1 && open; dy++) for (int dx = -1; dx <= 1 && open; dx++) {
                if (dx != 0 && dy != 0) continue;
                CellTopology.GroundKind g = t.getGroundKind(x + dx, y + dy);
                open = sim.getGrid().isWalkable(x + dx, y + dy)
                        && (g == CellTopology.GroundKind.INDOOR || g == CellTopology.GroundKind.TILE);
            }
            if (!open) continue;
            float d = (x - mx) * (x - mx) + (y - my) * (y - my);
            if (d < bestD) { bestD = d; best = new int[]{x, y}; }
        }
        return best;
    }

    /** A wall on the house's south side that the roof covers with an eave, nearest the middle of that side. */
    private static int[] exteriorWall(CellTopology t, int[] house) {
        int y = house[1] - 1;
        int mid = (house[0] + house[2]) / 2;
        for (int r = 0; r <= house[2] - house[0]; r++) {
            for (int x : new int[]{mid - r, mid + r}) {
                if (t.isWall(x, y) && RoofCover.piece(RoofCover.of(t, x, y)) == RoofCover.Piece.EAVE) return new int[]{x, y};
            }
        }
        return null;
    }

    private static float lerp(int a, int b, float t) {
        return a + (b + 1 - a) * t;
    }
}
