package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Fixed-grid counterpart to {@link NatureZonePreviewTest}. Generates small
 * scenes using {@link TileManifest}'s autotile pickers + the in-game inset
 * rule, then routes through {@link FixedGridTileDrawer} +
 * {@link Graphics2DTileSink} so the test's per-pixel output reflects what
 * {@link com.dillon.starsectormarines.ops.BattleScreen} will draw.
 *
 * <p>Each test method targets one or two sheets and one autotile-picker
 * family. Iteration loop:
 * <pre>
 *   gradlew :test --tests "*FixedGridZonePreviewTest*"
 *   open build/zone-previews/<scene>.png
 * </pre>
 *
 * <p>Scenes intentionally have hard quadrant boundaries (grass abutting
 * dirt, water surrounded by sand) so the autotile picker's edge cases fire
 * on every boundary cell — a misrouted picker / drifted sheet / busted
 * inset shows up as a visible discontinuity at the seam.
 */
public class FixedGridZonePreviewTest {

    private static final Path FLOORS_SHEET = Paths.get("mod/graphics/tilesets/Floors_Tiles.png");
    private static final Path NATURE_SHEET = Paths.get("mod/graphics/tilesets/nature-tiles.png");
    private static final Path OUT_DIR = Paths.get("build/zone-previews");

    /** Display cell size in output-image pixels. 3x upscale of the 16px source — large enough to read art clearly, small enough to keep PNGs lightweight. */
    private static final int DISPLAY_CELL_PX = 48;

    private static final Color CHECKER_A = new Color(0x18, 0x1F, 0x2A);
    private static final Color CHECKER_B = new Color(0x22, 0x2A, 0x36);
    private static final Color LABEL_BG  = new Color(0, 0, 0, 200);
    private static final Color LABEL_FG  = new Color(0xE0, 0xE8, 0xF4);

    /** Five ground kinds the Floors_Tiles sheet provides autotile pickers for. */
    private enum FloorsKind { GRASS, DIRT, SAND, SNOW, STONE }

    @Test
    void renderOutdoorSurfacesZone() throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage sheet = ImageIO.read(Files.newInputStream(FLOORS_SHEET));
        assertNotNull(sheet, "failed to load " + FLOORS_SHEET);

        int zoneW = 16;
        int zoneH = 12;

        // Four quadrants — grass NW, dirt NE, sand SW, snow SE — chosen so
        // every kind has neighbors on every side and the autotile picker
        // fires its corner + edge cases at the quadrant seams. Stone gets
        // its own zone test below (one quadrant ground per scene keeps the
        // boundary count high and the visual cluttered-enough to spot a
        // misrouted edge picker).
        FloorsKind[][] ground = new FloorsKind[zoneW][zoneH];
        int halfX = zoneW / 2;
        int halfY = zoneH / 2;
        for (int x = 0; x < zoneW; x++) {
            for (int y = 0; y < zoneH; y++) {
                boolean west  = x < halfX;
                boolean south = y < halfY;
                if      (west &&  !south) ground[x][y] = FloorsKind.GRASS;
                else if (!west && !south) ground[x][y] = FloorsKind.DIRT;
                else if (west &&  south)  ground[x][y] = FloorsKind.SAND;
                else                      ground[x][y] = FloorsKind.SNOW;
            }
        }

        BufferedImage img = newCanvas(zoneW, zoneH);
        Graphics2D g = configureGraphics(img);
        FixedGridTileDrawer drawer = new FixedGridTileDrawer(TileManifest.FLOORS_TILE_SIZE);
        TileSink sink = new Graphics2DTileSink(g, sheet);

        // Center variants only — see memory note "flat-edges-between-kinds".
        // Outdoor ground kinds never use the per-side edge autotile variants;
        // kinds meet with hard cell-boundary transitions.
        renderCheckerBackdrop(g, zoneW, zoneH);
        for (int x = 0; x < zoneW; x++) {
            for (int y = 0; y < zoneH; y++) {
                FloorsKind k = ground[x][y];
                TileManifest.TileFrame f;
                switch (k) {
                    case GRASS: f = TileManifest.pickGrassTile(false, false, false, false, x, y); break;
                    case DIRT:  f = TileManifest.pickDirtTile (false, false, false, false, x, y); break;
                    case SAND:  f = TileManifest.pickSandTile (false, false, false, false, x, y); break;
                    case SNOW:  f = TileManifest.pickSnowTile (false, false, false, false, x, y); break;
                    case STONE: f = TileManifest.pickStoneTile(false, false, false, false, x, y); break;
                    default: continue;
                }
                stampCell(drawer, sink, f, x, y, zoneH, drawer.defaultGroundInsetPx());
            }
        }

        drawLabel(g, zoneW, zoneH,
                "Floors_Tiles outdoor surfaces — NW grass · NE dirt · SW sand · SE snow (flat edges)");
        g.dispose();

        Path out = OUT_DIR.resolve("floors-outdoor-zone.png");
        ImageIO.write(img, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    @Test
    void renderStoneZone() throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage sheet = ImageIO.read(Files.newInputStream(FLOORS_SHEET));
        assertNotNull(sheet, "failed to load " + FLOORS_SHEET);

        // Stone island in the center, dirt border. Lets the stone autotile
        // close in on itself with edges + corners on every side rather than
        // running off the map edge.
        int zoneW = 14;
        int zoneH = 10;
        FloorsKind[][] ground = new FloorsKind[zoneW][zoneH];
        for (int x = 0; x < zoneW; x++) {
            for (int y = 0; y < zoneH; y++) {
                boolean inside = x >= 3 && x < zoneW - 3 && y >= 2 && y < zoneH - 2;
                ground[x][y] = inside ? FloorsKind.STONE : FloorsKind.DIRT;
            }
        }

        BufferedImage img = newCanvas(zoneW, zoneH);
        Graphics2D g = configureGraphics(img);
        FixedGridTileDrawer drawer = new FixedGridTileDrawer(TileManifest.FLOORS_TILE_SIZE);
        TileSink sink = new Graphics2DTileSink(g, sheet);

        renderCheckerBackdrop(g, zoneW, zoneH);
        for (int x = 0; x < zoneW; x++) {
            for (int y = 0; y < zoneH; y++) {
                FloorsKind k = ground[x][y];
                TileManifest.TileFrame f = (k == FloorsKind.STONE)
                        ? TileManifest.pickStoneTile(false, false, false, false, x, y)
                        : TileManifest.pickDirtTile (false, false, false, false, x, y);
                stampCell(drawer, sink, f, x, y, zoneH, drawer.defaultGroundInsetPx());
            }
        }

        drawLabel(g, zoneW, zoneH, "Floors_Tiles stone on dirt — flat edges (no autotile transition art)");
        g.dispose();

        Path out = OUT_DIR.resolve("floors-stone-zone.png");
        ImageIO.write(img, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    @Test
    void renderWaterZone() throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage floors = ImageIO.read(Files.newInputStream(FLOORS_SHEET));
        BufferedImage nature = ImageIO.read(Files.newInputStream(NATURE_SHEET));
        assertNotNull(floors, "failed to load " + FLOORS_SHEET);
        assertNotNull(nature, "failed to load " + NATURE_SHEET);

        // Water cells render through the nature-tiles sheet (WATER_1 / WATER_2
        // picked by per-cell hash) — see memory note "water-uses-nature-tile".
        // The dedicated Water_tiles.png sheet was orphaned by that decision +
        // the flat-edges rule. Sand cells still use the Floors_Tiles sheet.
        SpriteSheetFrames natureFrames = SpriteSheetSlicer.slice(nature);

        int zoneW = 14;
        int zoneH = 10;
        boolean[][] isWater = new boolean[zoneW][zoneH];
        for (int x = 0; x < zoneW; x++) {
            for (int y = 0; y < zoneH; y++) {
                isWater[x][y] = x >= 3 && x < zoneW - 3 && y >= 2 && y < zoneH - 2;
            }
        }

        BufferedImage img = newCanvas(zoneW, zoneH);
        Graphics2D g = configureGraphics(img);
        FixedGridTileDrawer fixedDrawer  = new FixedGridTileDrawer(TileManifest.FLOORS_TILE_SIZE);
        SlicedTileDrawer    natureDrawer = new SlicedTileDrawer(natureFrames);
        TileSink floorsSink = new Graphics2DTileSink(g, floors);
        TileSink natureSink = new Graphics2DTileSink(g, nature);

        renderCheckerBackdrop(g, zoneW, zoneH);
        for (int x = 0; x < zoneW; x++) {
            for (int y = 0; y < zoneH; y++) {
                if (isWater[x][y]) {
                    NatureTile water = pickWaterVariant(x, y);
                    float dstCx = x * DISPLAY_CELL_PX + DISPLAY_CELL_PX / 2f;
                    float dstCy = (zoneH - 1 - y) * DISPLAY_CELL_PX + DISPLAY_CELL_PX / 2f;
                    natureDrawer.draw(natureSink, water,
                            dstCx, dstCy, DISPLAY_CELL_PX, DISPLAY_CELL_PX, 1f);
                } else {
                    TileManifest.TileFrame f = TileManifest.pickSandTile(false, false, false, false, x, y);
                    stampCell(fixedDrawer, floorsSink, f, x, y, zoneH, fixedDrawer.defaultGroundInsetPx());
                }
            }
        }

        drawLabel(g, zoneW, zoneH, "Water (nature-tiles) pond on sand (Floors_Tiles) — flat edges");
        g.dispose();

        Path out = OUT_DIR.resolve("water-pond-zone.png");
        ImageIO.write(img, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    /** Per-cell hash pick between the two nature water variants for visual variety. Same shape the nature ground tiles use. */
    private static NatureTile pickWaterVariant(int x, int y) {
        int h = (x * 73856093) ^ (y * 19349663);
        return ((h & 1) == 0) ? NatureTile.WATER_1 : NatureTile.WATER_2;
    }

    // ---- helpers ----------------------------------------------------------

    private static void stampCell(FixedGridTileDrawer drawer, TileSink sink,
                                  TileManifest.TileFrame f, int gridX, int gridY, int zoneH,
                                  int insetPx) {
        float dstCx = gridX * DISPLAY_CELL_PX + DISPLAY_CELL_PX / 2f;
        float dstCy = (zoneH - 1 - gridY) * DISPLAY_CELL_PX + DISPLAY_CELL_PX / 2f;
        drawer.draw(sink, f, dstCx, dstCy, DISPLAY_CELL_PX, DISPLAY_CELL_PX, 1f, insetPx);
    }

    private static BufferedImage newCanvas(int zoneW, int zoneH) {
        int imgW = zoneW * DISPLAY_CELL_PX;
        int imgH = zoneH * DISPLAY_CELL_PX + 28;
        return new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D configureGraphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        // BILINEAR matches Starsector's default GL texture filter — the test
        // sees the same edge behavior the game does.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static void renderCheckerBackdrop(Graphics2D g, int zoneW, int zoneH) {
        for (int x = 0; x < zoneW; x++) {
            for (int y = 0; y < zoneH; y++) {
                g.setColor(((x + y) % 2 == 0) ? CHECKER_A : CHECKER_B);
                g.fillRect(x * DISPLAY_CELL_PX, (zoneH - 1 - y) * DISPLAY_CELL_PX,
                        DISPLAY_CELL_PX, DISPLAY_CELL_PX);
            }
        }
    }

    private static void drawLabel(Graphics2D g, int zoneW, int zoneH, String text) {
        int imgW = zoneW * DISPLAY_CELL_PX;
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(LABEL_BG);
        g.fillRect(0, zoneH * DISPLAY_CELL_PX, imgW, 28);
        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString(text, 8, zoneH * DISPLAY_CELL_PX + 19);
    }
}
