package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * That the relief beside the {@code urban-tileset-3} atlas is this atlas's relief.
 */
class UrbanTileset3AlphaTest {

    private static final String TILESET = "data/tilesets/urban-tileset-3.tileset.json";

    /**
     * What the tile-map derivation writes where a sheet is not a tile.
     *
     * <p>{@code AtlasTileMapDeriver} fills everything outside a cell with flat
     * height and a straight-up normal, so the sheets' non-flat region is exactly
     * the union of the frames they were derived from. Restated here rather than
     * shared, because that class is on the build-time tool classpath and must
     * not reach the shipped jar.
     */
    private static final int FLAT_HEIGHT = 0x808080;
    private static final int FLAT_NORMAL = 0x8080FF;

    /**
     * The relief beside the atlas is <em>this</em> atlas's relief.
     *
     * <p>{@code GroundMicroHeightSampler} finds {@code _height} and {@code
     * _normal} by naming convention and samples them at the coordinates it
     * sliced off the albedo. Nothing connects the three files, so re-packing the
     * albedo without re-deriving them leaves the parallax reading the relief of
     * a sheet that no longer exists — silently, because a stale companion is a
     * valid PNG of the right size and every other test passes.
     *
     * <p>The derivation leaves the gutters flat, so the companions state their
     * own frame boxes and those must be the albedo's. That is a shape the fix
     * for a stale sheet cannot fake: nothing but re-running
     * {@code gradlew.bat :asset-pipeline:deriveTileMaps} moves them.
     */
    @Test
    void theHeightAndNormalSheetsAreDerivedFromThisAtlasFrames() throws Exception {
        BufferedImage atlas = atlas();
        SpriteSheetFrames frames = SpriteSheetSlicer.slice(atlas);
        for (String suffix : List.of("_height", "_normal")) {
            BufferedImage companion = image("mod/graphics/tilesets/urban-tileset-3" + suffix + ".png");
            assertEquals(atlas.getWidth(), companion.getWidth(), suffix + " is a different width");
            assertEquals(atlas.getHeight(), companion.getHeight(), suffix + " is a different height");
            int flat = "_height".equals(suffix) ? FLAT_HEIGHT : FLAT_NORMAL;
            for (int x = 0; x < companion.getWidth(); x++) {
                boolean derived = false;
                for (int y = 0; y < companion.getHeight() && !derived; y++) {
                    derived = (companion.getRGB(x, y) & 0xFFFFFF) != flat;
                }
                assertEquals(insideAFrame(frames, x), derived, "column " + x + " of " + suffix
                        + " is " + (derived ? "derived but sits in a gutter of the albedo"
                        : "flat but sits inside one of the albedo's frames")
                        + ", so the relief is sampled at pixels this atlas does not have there"
                        + " — re-run gradlew.bat :asset-pipeline:deriveTileMaps");
            }
        }
    }

    private static boolean insideAFrame(SpriteSheetFrames frames, int x) {
        for (SpriteSheetFrames.Frame frame : frames.frames) {
            if (x >= frame.x && x < frame.x + frame.w) return true;
        }
        return false;
    }


    private static BufferedImage atlas() throws Exception {
        return image("mod/" + new JSONObject(read(TILESET)).getString("sheet"));
    }

    private static BufferedImage image(String path) throws Exception {
        String[] parts = path.split("/");
        BufferedImage image = ImageIO.read(Paths.get(parts[0],
                Arrays.copyOfRange(parts, 1, parts.length)).toFile());
        assertNotNull(image, "cannot read " + path);
        return image;
    }


    private static String read(String modRelativePath) {
        Path path = Paths.get("mod", modRelativePath.split("/"));
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}
