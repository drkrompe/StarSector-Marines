package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Conquest fortress district, so the wall and the district behind it
 * can be looked at together.
 *
 * <p>Baseline evidence for {@code compound-programs.md}: today the band comes
 * out as ordinary city — freestanding rectangles on a road grid — and the wall
 * is the only thing that says fortress. Re-run via
 * {@code gradlew createSnapshots -Psnapshot=fortress-preview}.
 */
public final class FortressPreviewSnapshotSuite implements SnapshotSuite {

    private static final int CELL_PX = 14;
    /** Production Conquest size, so the band is the depth it really is. */
    private static final int MAP_W = 240;
    private static final int MAP_H = 160;
    /** Rows past the band's far edge, so the wall and its kill zone stay in frame. */
    private static final int BUFFER_ROWS = 16;

    @Override public String id() { return "fortress-preview"; }
    @Override public String label() { return "Conquest fortress district crops"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessBattleMapRenderer battleMaps = new HeadlessBattleMapRenderer(context.modRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (long seed : new long[] { 1L, 5L, 9L }) {
            BspCityGenerator generator = new BspCityGenerator();
            MapResult map = generator.generate(MAP_W, MAP_H, seed, TraversalAxis.SOUTH_TO_NORTH);
            BiomeMap biomes = generator.getLastBiomeMap();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (int y = 0; y < MAP_H; y++) {
                for (int x = 0; x < MAP_W; x++) {
                    if (biomes.biomeAt(x, y) != BiomeKind.FORTRESS_DISTRICT) continue;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
            }
            System.out.println("  seed " + seed + " fortress band x " + minX + ".." + maxX
                    + " y " + minY + ".." + maxY);
            BufferedImage full = battleMaps.render(map, seed, CELL_PX);
            // The renderer draws cell y=0 along the bottom edge, so the band's
            // top row in map space is its lowest row in image space.
            int px = minX * CELL_PX, py = (MAP_H - 1 - maxY) * CELL_PX;
            int pw = Math.min((maxX - minX + 1) * CELL_PX, full.getWidth() - px);
            int ph = Math.min((maxY - minY + 1 + BUFFER_ROWS) * CELL_PX, full.getHeight() - py);
            BufferedImage crop = full.getSubimage(px, py, pw, ph);
            artifacts.add(new SnapshotArtifact("fortress-seed-" + seed + ".png", crop));
        }
        return artifacts;
    }
}
