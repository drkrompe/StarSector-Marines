package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;

/**
 * Each story mission's one owned map under every world's skin ({@code story-map-pools.md}, Planet skin), built as the
 * campaign builds it and framed through the look preset at 1920x1080: the whole map per world
 * ({@code <map>/<nn>-<world>-1-whole.png}), and close frames on the same two spots in every world, where the land use
 * changes: the colony's fields ({@code -2-fields.png}) and its woods ({@code -3-woods.png}). Nothing is played. Opt-in.
 * {@code -Dsnapshot.planetSkin.maps=colony-breach,heart-burn} narrows the maps, {@code .worlds=frozen,toxic} the worlds.
 */
public final class PlanetSkinLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** Cells across a close frame: a field or a wood and what stands round it. */
    private static final float CLOSE_CELLS = 70f;
    /** The window a close frame's spot is the densest of. */
    private static final int WINDOW = 24;
    private static final List<StoryMap> DEFAULT_MAPS = List.of(StoryMap.COLONY_BREACH, StoryMap.CORDON_ROAD,
            StoryMap.CORDON_LINE, StoryMap.HEART_BURN, StoryMap.HEART_FRONT);

    @Override public String id() { return "planet-skin-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Each story mission's one map skinned for every world, look preset"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<String> maps = asked("snapshot.planetSkin.maps"), worlds = asked("snapshot.planetSkin.worlds");
        List<SnapshotArtifact> out = new ArrayList<>();
        for (StoryMap map : DEFAULT_MAPS) {
            if (!maps.isEmpty() && !maps.contains(map.id)) continue;
            StoryMapPool pool = StoryMapPools.pool(map);
            // The spots are found once on a world whose fields are beds and whose woods stand, and framed in every world.
            float[] fields, woods;
            try (BattleSimulation probe = sim(map, SurfacePalette.FROZEN, pool.owned())) {
                fields = densest(probe.getTopology(), (x, y) -> probe.getTopology().getGroundKind(x, y) == GroundKind.TILE);
                woods = densestDoodad(probe, "forest.");
            }
            for (SurfacePalette world : SurfacePalette.values()) {
                String tag = world.name().toLowerCase(Locale.ROOT);
                if (!worlds.isEmpty() && !worlds.contains(tag)) continue;
                String base = String.format("%s/%02d-%s-", map.id, world.ordinal() + 1, tag);
                try (BattleSimulation sim = sim(map, world, pool.owned())) {
                    int w = sim.getTopology().getWidth(), h = sim.getTopology().getHeight();
                    BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                            w / 2f, h / 2f, Math.max(w, h * 16f / 9f) + 4f);
                    out.add(new SnapshotArtifact(base + "1-whole.png", look.render(sim, "")));
                    if (fields != null) {
                        look.refocus(fields[0], fields[1], CLOSE_CELLS);
                        out.add(new SnapshotArtifact(base + "2-fields.png", look.render(sim, "")));
                    }
                    if (woods != null) {
                        look.refocus(woods[0], woods[1], CLOSE_CELLS);
                        out.add(new SnapshotArtifact(base + "3-woods.png", look.render(sim, "")));
                    }
                }
                System.out.printf(Locale.ROOT, "[%s] %s %s (home %s, seed %d)%n", id(), map.id, tag, pool.home(),
                        pool.owned().seed());
            }
        }
        return out;
    }

    private static List<String> asked(String property) {
        String v = System.getProperty(property, "").trim();
        return v.isEmpty() ? List.of() : List.of(v.split(","));
    }

    /** The mission's map on {@code world}, built by the campaign's own entry. */
    private static BattleSimulation sim(StoryMap map, SurfacePalette world, StoryMapPool.Entry entry) {
        return switch (map) {
            case CORDON_LINE, CORDON_ROAD, CORDON_LINE_TOWN, CORDON_ROAD_TOWN, CORDON_LINE_URBAN, CORDON_ROAD_URBAN -> {
                CordonVariant variant = CordonSetup.variantOf(map);
                yield CordonSetup.create(CordonSetup.map(variant, CordonSetup.densityOf(map), world, entry), variant,
                        List.of(), CordonSequence.Timetable.STANDARD);
            }
            case COLONY_BREACH -> BattleSetup.createPooledColonyBreach(world, entry, List.of());
            default -> BloomHeartSetup.country(BloomHeartSetup.formOf(map), world, BloomHeartSetup.densityOf(map),
                    entry).sim();
        };
    }

    /** The middle of the {@link #WINDOW}-cell square holding the most cells {@code hit} picks, or null for none. */
    private static float[] densest(CellTopology topology, BiPredicate<Integer, Integer> hit) {
        int w = topology.getWidth(), h = topology.getHeight();
        int[][] sum = new int[h + 1][w + 1];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            sum[y + 1][x + 1] = sum[y][x + 1] + sum[y + 1][x] - sum[y][x] + (hit.test(x, y) ? 1 : 0);
        return best(sum, w, h);
    }

    private static float[] densestDoodad(BattleSimulation sim, String prefix) {
        int w = sim.getTopology().getWidth(), h = sim.getTopology().getHeight();
        int[][] count = new int[h][w];
        for (Doodad d : sim.getDoodads()) {
            if (d.definitionId() != null && d.definitionId().startsWith(prefix)
                    && d.cellX >= 0 && d.cellY >= 0 && d.cellX < w && d.cellY < h) count[d.cellY][d.cellX]++;
        }
        int[][] sum = new int[h + 1][w + 1];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            sum[y + 1][x + 1] = sum[y][x + 1] + sum[y + 1][x] - sum[y][x] + count[y][x];
        return best(sum, w, h);
    }

    private static float[] best(int[][] sum, int w, int h) {
        int bestN = 0;
        float[] at = null;
        for (int y = 0; y + WINDOW <= h; y += 2) for (int x = 0; x + WINDOW <= w; x += 2) {
            int n = sum[y + WINDOW][x + WINDOW] - sum[y][x + WINDOW] - sum[y + WINDOW][x] + sum[y][x];
            if (n > bestN) {
                bestN = n;
                at = new float[]{x + WINDOW / 2f, y + WINDOW / 2f};
            }
        }
        return at;
    }
}
