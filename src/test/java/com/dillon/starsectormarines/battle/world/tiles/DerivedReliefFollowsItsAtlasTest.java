package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every sheet that gets relief baked for it still has relief baked from
 * <em>itself</em>.
 *
 * <p>{@code GroundMicroHeightSampler} finds {@code <sheet>_height.png} and
 * {@code <sheet>_normal.png} by naming convention and samples them at the
 * coordinates it took off the albedo. Nothing connects the three files, so
 * re-packing a tileset without re-running
 * {@code gradlew.bat :asset-pipeline:deriveTileMaps} leaves the parallax
 * reading a sheet that no longer exists. It fails silently by construction: a
 * stale companion is a valid PNG, the loader never compares it to anything, and
 * every other test passes. Adding {@code floors.snow} took {@code Floors_Tiles}
 * from two rows to three while both companions stayed at two, and the whole
 * suite stayed green.
 *
 * <p>The sheets checked are read out of the derivation manifest rather than
 * listed here, because the manifest is the bake's own inclusion list: a sheet
 * added to it is covered the moment it is added, and a sheet dropped from it
 * stops being asserted about for the same reason it stops being baked. Nothing
 * to keep in step. {@link UrbanTileset3AlphaTest} states the far stronger
 * frame-by-frame version of this for the one strip whose frame boxes it already
 * knows; this is the part that can be said about any sheet.
 */
class DerivedReliefFollowsItsAtlasTest {

    private static final Path MANIFEST = Paths.get("asset-pipeline", "src", "tool", "resources",
            "tilemaps", "tilemaps.json");

    /**
     * What {@code AtlasTileMapDeriver} writes where a sheet is not a tile.
     * Restated rather than shared: that class is on the build-time tool
     * classpath and must not reach the shipped jar.
     */
    private static final int FLAT_HEIGHT = 0x808080;
    private static final int FLAT_NORMAL = 0x8080FF;

    @Test
    void everyBakedSheetHasBothCompanionsAtItsOwnSize() throws Exception {
        for (String tileset : bakedTilesets()) {
            BufferedImage albedo = albedo(tileset);
            for (String suffix : List.of("_height", "_normal")) {
                BufferedImage companion = companion(tileset, suffix);
                assertEquals(albedo.getWidth(), companion.getWidth(),
                        tileset + suffix + " is " + companion.getWidth() + " wide against an atlas "
                                + albedo.getWidth() + " wide, so the relief is sampled at pixels it "
                                + "does not have — re-run gradlew.bat :asset-pipeline:deriveTileMaps");
                assertEquals(albedo.getHeight(), companion.getHeight(),
                        tileset + suffix + " is " + companion.getHeight() + " tall against an atlas "
                                + albedo.getHeight() + " tall, so the relief is sampled at pixels it "
                                + "does not have — re-run gradlew.bat :asset-pipeline:deriveTileMaps");
            }
        }
    }

    /**
     * Nothing is flat where there is art.
     *
     * <p>Size alone is blind to a re-pack that kept the atlas the same shape and
     * moved the pieces around inside it. The derivation leaves everything
     * outside a frame box at the flat value, so a row or column the companion
     * left untouched is one the albedo had nothing in — and art that moved into
     * a former gutter shows up here as a stripe of dead relief through the
     * middle of a tile.
     *
     * <p>Only that direction is a law. The converse is not: a frame box may
     * carry a fully transparent edge column, which the derivation covers and the
     * albedo leaves clear, and {@code nature-tiles} has two of them today.
     */
    @Test
    void noCompanionIsFlatWhereItsAtlasHasArt() throws Exception {
        for (String tileset : bakedTilesets()) {
            BufferedImage albedo = albedo(tileset);
            for (String suffix : List.of("_height", "_normal")) {
                BufferedImage companion = companion(tileset, suffix);
                if (companion.getWidth() != albedo.getWidth()
                        || companion.getHeight() != albedo.getHeight()) {
                    continue; // the size test above owns this one
                }
                int flat = "_height".equals(suffix) ? FLAT_HEIGHT : FLAT_NORMAL;
                for (int x = 0; x < albedo.getWidth(); x++) {
                    if (!isFlatColumn(companion, x, flat)) continue;
                    assertTrue(isClearColumn(albedo, x), "column " + x + " of " + tileset + suffix
                            + " was never derived, but the atlas draws art there — the relief is a"
                            + " bake of an older packing of this sheet; re-run gradlew.bat"
                            + " :asset-pipeline:deriveTileMaps");
                }
                for (int y = 0; y < albedo.getHeight(); y++) {
                    if (!isFlatRow(companion, y, flat)) continue;
                    assertTrue(isClearRow(albedo, y), "row " + y + " of " + tileset + suffix
                            + " was never derived, but the atlas draws art there — the relief is a"
                            + " bake of an older packing of this sheet; re-run gradlew.bat"
                            + " :asset-pipeline:deriveTileMaps");
                }
            }
        }
    }

    private static boolean isFlatColumn(BufferedImage companion, int x, int flat) {
        for (int y = 0; y < companion.getHeight(); y++) {
            if ((companion.getRGB(x, y) & 0xFFFFFF) != flat) return false;
        }
        return true;
    }

    private static boolean isFlatRow(BufferedImage companion, int y, int flat) {
        for (int x = 0; x < companion.getWidth(); x++) {
            if ((companion.getRGB(x, y) & 0xFFFFFF) != flat) return false;
        }
        return true;
    }

    private static boolean isClearColumn(BufferedImage albedo, int x) {
        for (int y = 0; y < albedo.getHeight(); y++) {
            if ((albedo.getRGB(x, y) >>> 24) >= 128) return false;
        }
        return true;
    }

    private static boolean isClearRow(BufferedImage albedo, int y) {
        for (int x = 0; x < albedo.getWidth(); x++) {
            if ((albedo.getRGB(x, y) >>> 24) >= 128) return false;
        }
        return true;
    }

    /** The inclusion list the bake itself reads, by tileset document name. */
    private static List<String> bakedTilesets() throws Exception {
        JSONArray sheets = new JSONObject(withoutComments(Files.readString(MANIFEST)))
                .getJSONArray("sheets");
        assertTrue(sheets.length() > 0, MANIFEST + " lists no sheets to bake");
        List<String> tilesets = new ArrayList<>();
        for (int i = 0; i < sheets.length(); i++) {
            tilesets.add(sheets.getJSONObject(i).getString("tileset")
                    .replace(".tileset.json", ""));
        }
        return tilesets;
    }

    /**
     * The manifest is documented in {@code //} comments, which the bake reads
     * through Jackson with Java comments enabled. Jackson is on the build-time
     * tool classpath and this is not, and the {@code org.json} here is the
     * game's, which stops at the first slash.
     */
    private static String withoutComments(String json) {
        StringBuilder out = new StringBuilder(json.length());
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < json.length()) out.append(json.charAt(++i));
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '/' && i + 1 < json.length() && json.charAt(i + 1) == '/') {
                while (i < json.length() && json.charAt(i) != '\n') i++;
                if (i < json.length()) out.append('\n');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static BufferedImage albedo(String tileset) throws Exception {
        return image(Paths.get("mod", sheetPath(tileset).split("/")));
    }

    private static BufferedImage companion(String tileset, String suffix) throws Exception {
        return image(Paths.get("mod",
                sheetPath(tileset).replace(".png", suffix + ".png").split("/")));
    }

    private static String sheetPath(String tileset) throws Exception {
        Path document = Paths.get("mod", "data", "tilesets", tileset + ".tileset.json");
        assertTrue(Files.exists(document), MANIFEST + " bakes " + tileset
                + ", which has no tileset document at " + document);
        return new JSONObject(Files.readString(document)).getString("sheet");
    }

    private static BufferedImage image(Path path) throws Exception {
        assertTrue(Files.exists(path), path + " is missing — a sheet in " + MANIFEST
                + " has no relief baked for it; run gradlew.bat :asset-pipeline:deriveTileMaps");
        BufferedImage image = ImageIO.read(path.toFile());
        assertNotNull(image, "cannot read " + path);
        return image;
    }
}
