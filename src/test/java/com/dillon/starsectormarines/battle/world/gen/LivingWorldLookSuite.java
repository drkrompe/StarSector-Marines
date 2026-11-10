package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.nature.Flora;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;

/**
 * The living worlds' looks ({@link LivingWorld}) through the look preset: a small colony's map on each, the
 * temperate look first as the control (same seed, same profile but for the look), each whole, then its thickest woods
 * at site and play zoom and its look's own ground (swamp, snow, shore) at play zoom. For the owner judging whether a
 * jungle, a tundra and an ocean world each read as their own place. Opt-in.
 *
 * <p>{@code -Dsnapshot.living.looks=TEMPERATE,JUNGLE,TUNDRA,OCEAN} and {@code -Dsnapshot.living.seeds=7,9301} narrow
 * it. Each map prints its walkable share and its shares of water, wade, snow and sand, and its crowns by flora.
 */
public final class LivingWorldLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080, MAP_W = 280, MAP_H = 168;
    private static final float PLAY_ZOOM_CELLS = 60f, SITE_CELLS = 120f;

    @Override public String id() { return "living-world-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Living worlds: temperate, jungle, tundra and ocean looks"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String looks = System.getProperty("snapshot.living.looks", "TEMPERATE,JUNGLE,TUNDRA,OCEAN");
        String seeds = System.getProperty("snapshot.living.seeds", "7,9301");
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            for (String s : seeds.split(",")) {
                long seed = Long.parseLong(s.trim());
                for (String name : looks.split(",")) {
                    LivingWorld look = LivingWorld.valueOf(name.trim().toUpperCase(Locale.ROOT));
                    TargetProfile profile = new TargetProfile(3, 3, 1, 0, "independent",
                            EnumSet.of(EconomicFunction.HABITATION), SurfacePalette.VERDANT, SettlementLink.ROAD)
                            .withLivingWorld(look);
                    MapResult map = new BspCityGenerator().generate(MAP_W, MAP_H, seed, null, profile);
                    try (BattleSimulation sim = BattleSetup.buildMap(map, List.of(), List.of(), seed).sim()) {
                        sim.setSurface(SurfacePalette.VERDANT);
                        shots(context, out, sim, look, "look/seed" + seed + "-" + look.name().toLowerCase(Locale.ROOT) + "-");
                    }
                }
            }
        }
        return out;
    }

    private static void shots(SnapshotContext context, List<SnapshotArtifact> out, BattleSimulation sim,
                              LivingWorld look, String tag) throws Exception {
        CellTopology t = sim.getTopology();
        int w = t.getWidth(), h = t.getHeight();
        BattleReviewFrameRenderer frame = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                w / 2f, h / 2f, w);
        out.add(new SnapshotArtifact(tag + "1-whole-map.png", frame.render(sim, "")));
        boolean[] crowns = new boolean[w * h];
        for (Doodad d : sim.getDoodads()) {
            if (d.definitionId() == null || !d.definitionId().startsWith("forest.")) continue;
            int x = d.cellX + d.footprintCellsX / 2, y = d.cellY + d.footprintCellsY / 2;
            if (x >= 0 && y >= 0 && x < w && y < h) crowns[y * w + x] = true;
        }
        int[] woods = densest(w, h, (int) PLAY_ZOOM_CELLS, (x, y) -> crowns[y * w + x]);
        if (woods != null) {
            frame.refocus(woods[0], woods[1], SITE_CELLS);
            out.add(new SnapshotArtifact(tag + "2-woods-site.png", frame.render(sim, "")));
            frame.refocus(woods[0], woods[1], PLAY_ZOOM_CELLS);
            out.add(new SnapshotArtifact(tag + "3-woods-play-zoom.png", frame.render(sim, "")));
        }
        BiPredicate<Integer, Integer> own = switch (look) {
            case JUNGLE -> (x, y) -> t.isShallowWater(x, y) || t.getGroundKind(x, y) == GroundKind.WATER;
            case TUNDRA -> (x, y) -> t.getGroundKind(x, y) == GroundKind.SNOW;
            case OCEAN -> (x, y) -> t.getGroundKind(x, y) == GroundKind.SAND || t.isShallowWater(x, y);
            case TEMPERATE -> (x, y) -> t.getGroundKind(x, y) == GroundKind.WATER;
        };
        int[] ground = densest(w, h, (int) PLAY_ZOOM_CELLS, own);
        if (ground != null) {
            frame.refocus(ground[0], ground[1], PLAY_ZOOM_CELLS);
            out.add(new SnapshotArtifact(tag + "4-ground-play-zoom.png", frame.render(sim, "")));
        }
        int walkable = 0, water = 0, wade = 0, snow = 0, sand = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (sim.getGrid().isWalkable(x, y)) walkable++;
            GroundKind kind = t.getGroundKind(x, y);
            if (kind == GroundKind.WATER) water++;
            if (t.isShallowWater(x, y)) wade++;
            if (kind == GroundKind.SNOW) snow++;
            if (kind == GroundKind.SAND) sand++;
        }
        int[] byFlora = new int[Flora.values().length];
        for (Doodad d : sim.getDoodads()) {
            String id = d.definitionId();
            if (id == null || !id.startsWith("forest.")) continue;
            Flora flora = Flora.TEMPERATE;
            for (Flora f : Flora.values()) if (!f.suffix.isEmpty() && id.contains(f.suffix)) flora = f;
            byFlora[flora.ordinal()]++;
        }
        double n = w * h / 100.0;
        System.out.printf(Locale.ROOT, "living-world-look %s walkable %.1f%%, water %.1f%%, wade %.1f%%, snow %.1f%%, "
                        + "sand %.1f%%; growth temperate %d, dry %d, jungle %d, taiga %d%n", tag, walkable / n, water / n,
                wade / n, snow / n, sand / n, byFlora[0], byFlora[1], byFlora[2], byFlora[3]);
    }

    /** The middle of the {@code size}-cell square holding the most cells {@code counts}, or null where there are none. */
    private static int[] densest(int w, int h, int size, BiPredicate<Integer, Integer> counts) {
        int step = size / 4, best = 0;
        int[] at = null;
        for (int x0 = 0; x0 + size <= w; x0 += step) {
            for (int y0 = 0; y0 + size <= h; y0 += step) {
                int count = 0;
                for (int x = x0; x < x0 + size; x++) for (int y = y0; y < y0 + size; y++) if (counts.test(x, y)) count++;
                if (count > best) {
                    best = count;
                    at = new int[]{x0 + size / 2, y0 + size / 2};
                }
            }
        }
        return at;
    }
}
