package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where water meets land ({@link WaterShore}), on real maps at tick zero: the
 * heart's brook on The Burn and a Conquest city's ponds and coast. For each
 * map, the whole map, then the stretch of shore with the most shoreline in it
 * at play zoom (32 px a cell) and closer (64 px a cell), then the next-best
 * stretch well away from the first, then the first ford (shallow water over
 * a stony bed) at play zoom and close. Look evidence, 1920x1080.
 *
 * <p>Render once per arm of {@code -Dbattle.render.shore=organic|diagonal|square}
 * into its own {@code -PsnapshotDir} to compare them. Run with
 * {@code -Dbattle.render.creep=false} to see the heart's banks under its creep,
 * and once per {@code -Dbattle.render.fordLook=pebbles|stones|both} to compare fords.
 * {@code -Dsnapshot.waterShore.burnSeeds=202} and
 * {@code -Dsnapshot.waterShore.conquestSeeds=4} pick the maps.
 */
public final class WaterShoreSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** Cells across at play zoom (32 px a cell) and close (64 px a cell). */
    private static final float PLAY_CELLS = 60f, CLOSE_CELLS = 30f;

    @Override public String id() { return "water-shore"; }

    @Override public String label() { return "Water shore: brooks, ponds and coast where water meets land"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        InstalledVanillaData.install();
        String mode = System.getProperty(WaterShore.PROPERTY, "organic");
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String token : System.getProperty("snapshot.waterShore.burnSeeds", "202").split(",")) {
            if (token.isBlank()) continue;
            long seed = Long.parseLong(token.trim());
            try (BattleSimulation sim = BloomHeartSetup.country(Form.THE_BURN, SurfacePalette.VERDANT, seed).sim()) {
                views(context, out, sim, "burn-seed" + seed, "The Burn seed " + seed + " | shore " + mode);
            }
        }
        TargetProfile profile = fixtureProfile();
        for (String token : System.getProperty("snapshot.waterShore.conquestSeeds", "4").split(",")) {
            if (token.isBlank()) continue;
            long seed = Long.parseLong(token.trim());
            System.out.println("[water-shore] conquest seed " + seed);
            try (BattleSimulation sim = BattleSetup.createConquest(seed,
                    List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6)), false,
                    OperationTier.FIRST_CONTRACT, RiskLevel.LOW, profile)) {
                views(context, out, sim, "conquest-seed" + seed, "Conquest seed " + seed + " | shore " + mode);
            }
        }
        return out;
    }

    private static void views(SnapshotContext context, List<SnapshotArtifact> out, BattleSimulation sim,
                              String tag, String head) throws Exception {
        CellTopology topology = sim.getTopology();
        int[] first = shoreliest(topology, null);
        System.out.println("[water-shore] " + tag + ": shoreliest window at " + (first == null ? "none" : first[0] + "," + first[1]));
        if (first == null) return;
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        out.add(new SnapshotArtifact(tag + "-01-whole-map.png", whole.render(sim, head + " | whole map")));
        BattleReviewFrameRenderer near = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
        near.refocus(first[0] + .5f, first[1] + .5f, PLAY_CELLS);
        out.add(new SnapshotArtifact(tag + "-02-shore-play.png", near.render(sim, head + " | play zoom")));
        near.refocus(first[0] + .5f, first[1] + .5f, CLOSE_CELLS);
        out.add(new SnapshotArtifact(tag + "-03-shore-close.png", near.render(sim, head + " | close")));
        int[] second = shoreliest(topology, first);
        if (second != null) {
            near.refocus(second[0] + .5f, second[1] + .5f, PLAY_CELLS);
            out.add(new SnapshotArtifact(tag + "-04-shore-elsewhere.png", near.render(sim, head + " | play zoom, elsewhere")));
        }
        int[] ford = ford(topology);
        if (ford == null) return;
        near.refocus(ford[0] + .5f, ford[1] + .5f, PLAY_CELLS);
        out.add(new SnapshotArtifact(tag + "-05-ford-play.png", near.render(sim, head + " | a ford, play zoom")));
        near.refocus(ford[0] + .5f, ford[1] + .5f, CLOSE_CELLS);
        out.add(new SnapshotArtifact(tag + "-06-ford-close.png", near.render(sim, head + " | a ford, close")));
    }

    /** The middle of the first ford found, row by row from the south: its shallow cells' mean; null without one. */
    private static int[] ford(CellTopology topology) {
        for (int y = 0; y < topology.getHeight(); y++) {
            for (int x = 0; x < topology.getWidth(); x++) {
                if (!topology.isShallowWater(x, y)) continue;
                long sx = 0, sy = 0, n = 0;
                for (int j = y; j < Math.min(topology.getHeight(), y + 12); j++) {
                    for (int i = Math.max(0, x - 12); i < Math.min(topology.getWidth(), x + 12); i++) {
                        if (topology.isShallowWater(i, j)) { sx += i; sy += j; n++; }
                    }
                }
                return new int[]{(int) (sx / n), (int) (sy / n)};
            }
        }
        return null;
    }

    /**
     * The centre of the close window holding the most shore cells, at least a
     * play-zoom window's width from {@code away}; null when no window holds any.
     */
    private static int[] shoreliest(CellTopology topology, int[] away) {
        int w = topology.getWidth(), h = topology.getHeight();
        int[] prefix = new int[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int shore = WaterShore.takesPart(topology, x, y) ? 1 : 0;
                prefix[(y + 1) * (w + 1) + x + 1] = shore + prefix[y * (w + 1) + x + 1]
                        + prefix[(y + 1) * (w + 1) + x] - prefix[y * (w + 1) + x];
            }
        }
        // Counted over the close frame, so the shore lands in the middle of the wider one too.
        int halfW = (int) (CLOSE_CELLS / 2), halfH = (int) (CLOSE_CELLS * FRAME_H / FRAME_W / 2);
        int[] best = null;
        int bestCount = 0;
        for (int cy = halfH; cy + halfH < h; cy += 2) {
            for (int cx = halfW; cx + halfW < w; cx += 2) {
                if (away != null && Math.abs(cx - away[0]) < PLAY_CELLS && Math.abs(cy - away[1]) < PLAY_CELLS) continue;
                int x0 = cx - halfW, y0 = cy - halfH, x1 = cx + halfW, y1 = cy + halfH;
                int count = prefix[y1 * (w + 1) + x1] - prefix[y0 * (w + 1) + x1]
                        - prefix[y1 * (w + 1) + x0] + prefix[y0 * (w + 1) + x0];
                if (count > bestCount) {
                    bestCount = count;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best;
    }

    /** The market a real Conquest is generated against, as the fort map suites take it, on a living world. */
    private static TargetProfile fixtureProfile() throws Exception {
        try (InputStream stream = WaterShoreSnapshotSuite.class.getResourceAsStream(
                "/battle-fixtures/conquest-reinforced-south-v3.json")) {
            BattleFixture loaded = BattleFixtureJson.fromJson(new JSONObject(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
            if (loaded instanceof BattleLaunchFixture launch) loaded = launch.construction();
            TargetProfile p = ((ConquestBattleFixture) loaded).targetProfile();
            // On a living world, which is the only kind that bears open water.
            return new TargetProfile(p.marketSize(), p.stability(), p.defenseLevel(), p.spaceportTier(), p.factionId(),
                    p.functions(), SurfacePalette.VERDANT, p.link(), p.groundDefence(), p.stationedStrength(),
                    p.groundDefences(), p.heavyBatteries(), p.planetaryShield(), p.declaredDefences(),
                    p.declaredMechDrops(), p.declaredAirPool(), p.declaredSupportKit(), p.declaredAssault(), p.garrison());
        }
    }
}
