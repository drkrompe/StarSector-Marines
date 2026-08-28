package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Renders the Conquest fortress district, so the wall and the district behind it
 * can be looked at together.
 *
 * <p>Baseline evidence for {@code compound-programs.md}: today the band comes
 * out as ordinary city — freestanding rectangles on a road grid — and the wall
 * is the only thing that says fortress. Re-run via
 * {@code gradlew :test --tests "*FortressPreviewTest*"}.
 */
public class FortressPreviewTest {

    private static final Path OUT_DIR = Paths.get("build/map-previews");
    private static final int CELL_PX = 18;
    private static final int MAP_W = 180;
    private static final int MAP_H = 140;
    /** Rows past the band's far edge, so the wall and its kill zone stay in frame. */
    private static final int BUFFER_ROWS = 20;

    private static HeadlessBattleMapRenderer battleMaps;

    @BeforeAll
    static void installRegistry() throws Exception {
        TileRegistry reg = new TileRegistry();
        for (String path : TileRegistry.BUILTIN_TILESETS) {
            reg.ingestSheet(new JSONObject(Files.readString(Paths.get("mod/" + path))));
        }
        reg.validateReferences();
        TileRegistry.install(reg);
        battleMaps = new HeadlessBattleMapRenderer(Paths.get("mod"));
    }

    @Test
    void renderFortressDistrict() throws Exception {
        Files.createDirectories(OUT_DIR);
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
            Path out = OUT_DIR.resolve("fortress-seed-" + seed + ".png");
            ImageIO.write(crop, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath()
                    + " (" + crop.getWidth() + "x" + crop.getHeight() + ")");
        }
    }
}
