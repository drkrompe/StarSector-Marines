package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A burning block on the colony's stood-up buildings ({@code fire-nouns.md}, Look; {@link StructureFire}): the
 * canonical Colony Breach colony, fire put into every room of the three burnable buildings nearest its middle as a
 * squad's flamer charges would, and only the fire field advanced. The look preset at play zoom as the rooms catch
 * ({@code 01-catching.png}), at the fire's height ({@code 02-peak.png}), and once it has burnt out
 * ({@code 03-burnt.png}); close on the block at its height ({@code 04-peak-close.png}) and burnt
 * ({@code 05-burnt-close.png}); the height again with the Settings page's 3D walls off, the drawing that stays
 * unchanged ({@code 06-peak-flat.png}); and a pan across the block at its height, {@code pan/frame-NNN.png}, for a
 * short film. Opt-in.
 */
public final class StructureFireLookSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    private static final int W = 1920, H = 1080;
    private static final float CLOSE_CELLS = 28f;
    private static final int MAX_TICKS = 30 * 300;
    /** How many buildings the fire is put into. */
    private static final int LIT = 3;
    /** How far apart the block's buildings may stand, cells, centre to centre. */
    private static final float NEAR = 16f;
    /** The pan: frames, one a tick (30 a second of battle), and how far it moves, cells. */
    private static final int PAN_FRAMES = Integer.getInteger("snapshot.fire.pan", 96);
    private static final float PAN_CELLS = 14f;

    @Override public String id() { return "structure-fire-look"; }

    @Override public String label() { return "A burning block on the stood-up buildings, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        new BattleReviewFrameRenderer(context.modRoot(), 64, 64);
        List<Building> block;
        int catching, peak, burnt;
        try (BattleSimulation probe = colony()) {
            block = pick(probe);
            if (block.isEmpty()) throw new IllegalStateException("no burnable building in the colony");
            FireField fire = light(probe, block);
            int[] marks = timeline(probe, fire, block);
            catching = marks[0];
            peak = marks[1];
            burnt = marks[2];
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Building b : block) {
            minX = Math.min(minX, b.minX); minY = Math.min(minY, b.minY);
            maxX = Math.max(maxX, b.maxX); maxY = Math.max(maxY, b.maxY);
            System.out.printf(Locale.ROOT, "    [structure-fire] building %d (%s) %d,%d to %d,%d%n", b.id, b.kind,
                    b.minX, b.minY, b.maxX, b.maxY);
        }
        System.out.printf(Locale.ROOT, "    [structure-fire] catching %d, peak %d, burnt %d%n", catching, peak, burnt);
        // Off the block toward the street, so its faces lean out into the frame.
        float vx = (minX + maxX + 1) / 2f - 9f, vy = (minY + maxY + 1) / 2f - 4f;
        Building first = block.get(0);
        float closeX = first.minX - 7f, closeY = (first.minY + first.maxY + 1) / 2f - 3f;
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = colony()) {
            FireField fire = light(sim, block);
            int tick = 0;
            tick = run(fire, tick, catching);
            out.add(shot("01-catching.png", context, vx, vy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, tick, sim));
            tick = run(fire, tick, peak);
            out.add(shot("02-peak.png", context, vx, vy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, tick, sim));
            out.add(shot("04-peak-close.png", context, closeX, closeY, CLOSE_CELLS, tick, sim));
            WallDepth.lookAs(Boolean.FALSE);
            try {
                out.add(shot("06-peak-flat.png", context, vx, vy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, tick, sim));
            } finally {
                WallDepth.lookAs(null);
            }
            // The pan: the fire burning on as the view slides across the block, close enough to see its faces.
            for (int k = 0; k < PAN_FRAMES; k++) {
                float along = PAN_CELLS * (k / (float) Math.max(1, PAN_FRAMES - 1) - .5f);
                out.add(shot(String.format(Locale.ROOT, "pan/frame-%03d.png", k), context, vx + along, vy + 3f,
                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * .7f, tick, sim));
                fire.tick(tick++);
            }
            tick = run(fire, tick, burnt);
            out.add(shot("03-burnt.png", context, vx, vy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, tick, sim));
            out.add(shot("05-burnt-close.png", context, closeX, closeY, CLOSE_CELLS, tick, sim));
        }
        return out;
    }

    private static SnapshotArtifact shot(String file, SnapshotContext context, float x, float y, float cells,
                                         int tick, BattleSimulation sim) throws Exception {
        return new SnapshotArtifact(file,
                BattleReviewFrameRenderer.look(context.modRoot(), W, H, x, y, cells).atClock(tick / 30f).render(sim, ""));
    }

    private static int run(FireField fire, int tick, int until) {
        for (; tick < until; tick++) fire.tick(tick);
        return tick;
    }

    /** The burnable buildings nearest the map's middle: those with floor the fire can take. */
    private static List<Building> pick(BattleSimulation sim) {
        FireField fire = sim.fireField();
        float mx = sim.getGrid().getWidth() / 2f, my = sim.getGrid().getHeight() / 2f;
        List<Building> burnable = new ArrayList<>();
        for (Building b : sim.getBuildings().all()) {
            if (burnableCells(sim, fire, b) >= 12) burnable.add(b);
        }
        burnable.sort(Comparator.comparingDouble(b -> Math.hypot((b.minX + b.maxX) / 2f - mx, (b.minY + b.maxY) / 2f - my)));
        Building first = burnable.isEmpty() ? null : burnable.get(0);
        if (first == null) return List.of();
        // The first and its nearest burnable neighbours, so the block burns together.
        burnable.sort(Comparator.comparingDouble(b -> Math.hypot((b.minX + b.maxX - first.minX - first.maxX) / 2f,
                (b.minY + b.maxY - first.minY - first.maxY) / 2f)));
        List<Building> block = new ArrayList<>();
        for (Building b : burnable) {
            float d = (float) Math.hypot((b.minX + b.maxX - first.minX - first.maxX) / 2f,
                    (b.minY + b.maxY - first.minY - first.maxY) / 2f);
            if (block.size() < LIT && d <= NEAR) block.add(b);
        }
        return block;
    }

    private static int burnableCells(BattleSimulation sim, FireField fire, Building b) {
        CellTopology topo = sim.getTopology();
        int n = 0;
        for (int y = b.minY; y <= b.maxY; y++) {
            for (int x = b.minX; x <= b.maxX; x++) {
                if (topo.getBuildingId(x, y) == b.id && fire.canBurn(x, y)) n++;
            }
        }
        return n;
    }

    /** One charge into each building, at the burnable cell nearest its middle, as a squad's flamer puts it in. */
    private static FireField light(BattleSimulation sim, List<Building> block) {
        FireField fire = sim.fireField();
        fire.setWind(1f, .2f, .35f);
        CellTopology topo = sim.getTopology();
        for (Building b : block) {
            float mx = (b.minX + b.maxX) / 2f, my = (b.minY + b.maxY) / 2f, best = Float.MAX_VALUE;
            int bx = -1, by = -1;
            for (int y = b.minY; y <= b.maxY; y++) {
                for (int x = b.minX; x <= b.maxX; x++) {
                    if (topo.getBuildingId(x, y) != b.id || !fire.canBurn(x, y)) continue;
                    float d = (x - mx) * (x - mx) + (y - my) * (y - my);
                    if (d < best) { best = d; bx = x; by = y; }
                }
            }
            if (bx >= 0) fire.addHeat(bx, by, 3000);
        }
        return fire;
    }

    /** The block's wall cells standing now. */
    private static int walls(CellTopology topo, List<Building> block) {
        int n = 0;
        for (Building b : block) {
            for (int y = b.minY - 1; y <= b.maxY + 1; y++) {
                for (int x = b.minX - 1; x <= b.maxX + 1; x++) if (topo.inBounds(x, y) && topo.isWall(x, y)) n++;
            }
        }
        return n;
    }

    /** Ticks when a quarter of the block's cells first burn, when most do, and when none do any longer. */
    private static int[] timeline(BattleSimulation sim, FireField fire, List<Building> block) {
        int catching = -1, peak = 0, burnt = MAX_TICKS;
        float peakScore = -1f;
        int peakCount = 0;
        int total = 0;
        for (Building b : block) total += Math.max(1, burnableCells(sim, fire, b));
        int w = fire.width();
        CellTopology topo = sim.getTopology();
        int standing0 = Math.max(1, walls(topo, block));
        for (int tick = 0; tick < MAX_TICKS; tick++) {
            fire.tick(tick);
            if (tick % 15 != 0) continue;
            int burning = 0;
            for (int i = 0, n = fire.burningCount(); i < n; i++) {
                int idx = fire.burningCell(i);
                int id = topo.getBuildingId(idx % w, idx / w);
                for (Building b : block) if (b.id == id) burning++;
            }
            if (catching < 0 && burning * 5 >= total) catching = tick;
            // The height: most of the block burning while most of its walls still stand to burn on.
            float stand = walls(topo, block) / (float) standing0;
            float score = burning * stand * stand;
            if (score > peakScore) { peakScore = score; peak = tick; peakCount = burning; }
            if (catching >= 0 && burning == 0) { burnt = tick + 60; break; }
        }
        System.out.printf(Locale.ROOT, "    [structure-fire] %d burnable cells, peak %d burning%n", total, peakCount);
        return new int[]{Math.max(0, catching < 0 ? peak / 2 : catching), peak, burnt};
    }

    private static BattleSimulation colony() throws Exception {
        String json;
        try (InputStream stream = StructureFireLookSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        BattleFixture parsed = BattleFixtureJson.fromJson(new JSONObject(json));
        return (parsed instanceof BattleLaunchFixture launch ? launch.construction() : parsed).build();
    }
}
