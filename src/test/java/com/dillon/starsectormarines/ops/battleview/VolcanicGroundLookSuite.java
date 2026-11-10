package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A lava world's battle ground through the look preset ({@code world-surface-palette.md}, Volcanic): a small colony's
 * map on the same seed as bare rock and as volcanic, which are laid out alike, so each pair differs only in the
 * volcanic art (basalt ground, basalt hills and formations, ash drifts and glowing fissures). Whole map, site and play
 * zoom, framed on the wild ground holding the most glowing marks. Opt-in.
 *
 * <p>{@code -Dsnapshot.volcanic.seeds=7,19} picks the maps.
 */
public final class VolcanicGroundLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final float PLAY_ZOOM_CELLS = 60f, SITE_CELLS = 120f;

    @Override public String id() { return "volcanic-ground-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Lava world ground beside bare rock, same seed: whole, site and play zoom"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            for (String s : System.getProperty("snapshot.volcanic.seeds", "7,19").split(",")) {
                long seed = Long.parseLong(s.trim());
                float[] focus = null;
                for (SurfacePalette palette : List.of(SurfacePalette.VOLCANIC, SurfacePalette.ROCK)) {
                    MapResult map = LookGround.of(palette, seed).generate();
                    try (BattleSimulation sim = new BattleSimulation(map.grid, map.topology)) {
                        for (Doodad d : map.doodads) sim.addDoodad(d);
                        sim.setSurface(palette);
                        // Framed on the volcanic map's liveliest ground; the rock map is the same ground, so the same spot.
                        if (focus == null) focus = liveliest(map, (int) PLAY_ZOOM_CELLS);
                        String tag = "volcanic-look-seed" + seed + "-" + palette.name().toLowerCase(Locale.ROOT) + "-";
                        int w = map.topology.getWidth(), h = map.topology.getHeight();
                        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                                w / 2f, h / 2f, w);
                        out.add(new SnapshotArtifact(tag + "1-whole.png", look.render(sim, "")));
                        look.refocus(focus[0], focus[1], SITE_CELLS);
                        out.add(new SnapshotArtifact(tag + "2-site.png", look.render(sim, "")));
                        look.refocus(focus[0], focus[1], PLAY_ZOOM_CELLS);
                        out.add(new SnapshotArtifact(tag + "3-play-zoom.png", look.render(sim, "")));
                        float[] rim = raised(map, (int) PLAY_ZOOM_CELLS);
                        if (rim != null) {
                            look.refocus(rim[0], rim[1], PLAY_ZOOM_CELLS);
                            out.add(new SnapshotArtifact(tag + "4-crater-rim.png", look.render(sim, "")));
                        }
                        System.out.printf(Locale.ROOT, "volcanic-ground-look seed %d %s: %d doodads, focus %.0f,%.0f%n",
                                seed, palette, map.doodads.size(), focus[0], focus[1]);
                    }
                }
            }
        }
        return out;
    }

    /** The middle of the {@code size}-cell square with the most raised ground, or null where nothing is raised. */
    private static float[] raised(MapResult map, int size) {
        TerrainSurface terrain = map.topology.terrainSurface();
        if (terrain == null) return null;
        int w = map.topology.getWidth(), h = map.topology.getHeight(), step = size / 4, best = 0;
        float[] at = null;
        for (int x0 = 0; x0 + size <= w; x0 += step) {
            for (int y0 = 0; y0 + size <= h; y0 += step) {
                int count = 0;
                for (int x = x0; x < x0 + size; x++) for (int y = y0; y < y0 + size; y++) if (terrain.patch(x, y, 6) != null) count++;
                if (count > best) {
                    best = count;
                    at = new float[]{x0 + size / 2f, y0 + size / 2f};
                }
            }
        }
        return at;
    }

    /** The middle of the {@code size}-cell square with the most glowing marks and ash; the map's middle when none. */
    private static float[] liveliest(MapResult map, int size) {
        List<VolcanicGroundLook.Mark> marks = VolcanicGroundLook.marks(map.topology);
        int w = map.topology.getWidth(), h = map.topology.getHeight(), step = size / 4;
        int best = -1;
        float[] at = {w / 2f, h / 2f};
        for (int x0 = 0; x0 + size <= w; x0 += step) {
            for (int y0 = 0; y0 + size <= h; y0 += step) {
                int count = 0;
                for (VolcanicGroundLook.Mark m : marks) {
                    if (m.x() >= x0 && m.x() < x0 + size && m.y() >= y0 && m.y() < y0 + size) count++;
                }
                if (count > best) {
                    best = count;
                    at = new float[]{x0 + size / 2f, y0 + size / 2f};
                }
            }
        }
        System.out.println("volcanic-ground-look marks " + marks.size() + ", best window " + best);
        return at;
    }
}
