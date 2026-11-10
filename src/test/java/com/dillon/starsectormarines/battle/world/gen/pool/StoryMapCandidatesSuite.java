package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The owner's seed picks for a story map ({@code story-map-pools.md}, one map per biome): for each biome, the pooled
 * entry and its {@code candidates} from the pool file, each whole through the look preset, the pooled one first (the
 * recommendation until the owner picks). A map with fewer than {@link #WANTED} candidates on a biome (Colony Breach)
 * is screened from seed 1 up: a seed that builds without failing its mission's acceptance is a candidate. Opt-in.
 * {@code -Dsnapshot.candidates.maps=colony-breach,cordon-road} picks the maps; the default is the rural five.
 */
public final class StoryMapCandidatesSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** Candidates shown per biome, the pooled one included. */
    static final int WANTED = 3;
    /** The highest seed screened for a missing candidate. */
    private static final int SCREEN_TO = 60;
    private static final List<SurfacePalette> BIOMES = List.of(SurfacePalette.VERDANT, SurfacePalette.ARID,
            SurfacePalette.ROCK, SurfacePalette.FROZEN, SurfacePalette.IRRADIATED, SurfacePalette.TOXIC);

    @Override public String id() { return "story-map-candidates"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Story map seed candidates per biome, look preset, pooled first"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        String asked = System.getProperty("snapshot.candidates.maps",
                "colony-breach,cordon-road,cordon-line,heart-burn,heart-front");
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String id : asked.split(",")) {
            StoryMap map = mapOf(id.trim());
            JSONObject file = new JSONObject(Files.readString(context.modRoot().resolve("data/world/pools/" + map.id + ".pool.json")));
            JSONObject candidates = file.optJSONObject("candidates");
            StoryMapPool pool = StoryMapPools.pool(map);
            for (SurfacePalette biome : BIOMES) {
                List<StoryMapPool.Entry> entries = new ArrayList<>(pool.entries(biome));
                JSONArray more = candidates == null ? null : candidates.optJSONArray(biome.name());
                for (int i = 0; more != null && i < more.length(); i++) {
                    JSONObject e = more.getJSONObject(i);
                    entries.add(new StoryMapPool.Entry(e.getLong("seed"), e.optString("note", ""),
                            MapPatch.read(e.optJSONObject("patch")), false));
                }
                int k = 0;
                for (StoryMapPool.Entry entry : entries) {
                    BufferedImage frame = frame(context, map, biome, entry);
                    if (frame == null) continue;
                    out.add(artifact(map, biome, ++k, entry, frame));
                }
                for (long seed = 1; k < WANTED && seed <= SCREEN_TO; seed++) {
                    long s = seed;
                    if (entries.stream().anyMatch(e -> e.seed() == s)) continue;
                    StoryMapPool.Entry entry = new StoryMapPool.Entry(seed, "", MapPatch.NONE, false);
                    BufferedImage frame = frame(context, map, biome, entry);
                    if (frame == null) continue;
                    out.add(artifact(map, biome, ++k, entry, frame));
                }
            }
        }
        return out;
    }

    private static SnapshotArtifact artifact(StoryMap map, SurfacePalette biome, int k, StoryMapPool.Entry entry,
                                             BufferedImage frame) {
        String b = biome.name().toLowerCase(Locale.ROOT);
        System.out.printf(Locale.ROOT, "CANDIDATE %s %s %d seed %d%s%n", map.id, b, k, entry.seed(),
                k == 1 ? " (pooled)" : "");
        return new SnapshotArtifact(String.format(Locale.ROOT, "%s/%s-%d-seed%d.png", map.id, b, k, entry.seed()), frame);
    }

    private static StoryMap mapOf(String id) {
        for (StoryMap map : StoryMap.values()) if (map.id.equals(id)) return map;
        throw new IllegalArgumentException("no story map " + id);
    }

    /** The map whole through the look preset, or null when the seed fails its mission's acceptance on this biome. */
    private static BufferedImage frame(SnapshotContext context, StoryMap map, SurfacePalette biome,
                                       StoryMapPool.Entry entry) throws Exception {
        try {
            switch (map) {
                case CORDON_LINE, CORDON_ROAD, CORDON_LINE_TOWN, CORDON_ROAD_TOWN, CORDON_LINE_URBAN, CORDON_ROAD_URBAN -> {
                    CordonVariant variant = CordonSetup.variantOf(map);
                    CordonSetup.CordonMap cordon = CordonSetup.map(variant, CordonSetup.densityOf(map), biome, entry);
                    try (BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(),
                            CordonSequence.Timetable.STANDARD)) {
                        return whole(context, sim);
                    }
                }
                case COLONY_BREACH -> {
                    try (BattleSimulation sim = BattleSetup.createPooledColonyBreach(biome, entry, List.of())) {
                        return whole(context, sim);
                    }
                }
                default -> {
                    BloomHeartSetup.Built built = BloomHeartSetup.country(BloomHeartSetup.formOf(map), biome,
                            BloomHeartSetup.densityOf(map), entry);
                    try (BattleSimulation sim = built.sim()) {
                        return whole(context, sim);
                    }
                }
            }
        } catch (IllegalStateException | IllegalArgumentException refused) {
            System.out.printf(Locale.ROOT, "REFUSED %s %s seed %d: %s%n", map.id, biome, entry.seed(), refused.getMessage());
            return null;
        }
    }

    private static BufferedImage whole(SnapshotContext context, BattleSimulation sim) throws Exception {
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        return BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H, w / 2f, h / 2f,
                Math.max(w, h * 16f / 9f) + 4f).render(sim, "");
    }
}
