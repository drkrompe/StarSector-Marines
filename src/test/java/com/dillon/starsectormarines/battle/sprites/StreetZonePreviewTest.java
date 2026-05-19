package com.dillon.starsectormarines.battle.sprites;

import com.dillon.starsectormarines.battle.TileManifest;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Dev preview for {@code urban-tileset-3.png} — the third urban sheet that
 * adds a square street tile, an irregular street alt, two sidewalk variants
 * (one suitable for straight runs, one for corners), a culvert doodad, and
 * two bench doodads (south-facing and east-facing).
 *
 * <p>The sheet is a single-row strip with transparent gutters between
 * frames, so we route through {@link SpriteSheetSlicer} the same way
 * {@link NatureZonePreviewTest} handles {@code nature-tiles.png}. Frame
 * indices line up with the sheet's left-to-right ordering:
 * <ol start="0">
 *   <li>{@link Frame#STREET_SQUARE} — uniform square paver, candidate for
 *       primary road surface.</li>
 *   <li>{@link Frame#STREET_IRREGULAR} — broken/cobbled alt; mutually
 *       exclusive with {@link Frame#STREET_SQUARE} per road run so they
 *       don't visually clash.</li>
 *   <li>{@link Frame#SIDEWALK} — plain sidewalk slab.</li>
 *   <li>{@link Frame#SIDEWALK_CORNER} — alt slab that reads better at
 *       perimeter corners (tighter joint, different highlight angle).</li>
 *   <li>{@link Frame#CULVERT} — drain grate doodad, lives at the curb.</li>
 *   <li>{@link Frame#BENCH_S} — bench facing south (player views back of
 *       seat from the north sidewalk side).</li>
 *   <li>{@link Frame#BENCH_E} — bench facing east; mirror at render time
 *       for a west-facing variant.</li>
 * </ol>
 *
 * <p>The slicer test below asserts the detected count matches the enum so
 * a re-export that drops or splits a frame fails loudly with the slicer
 * debug PNG showing what got merged. Iterate via:
 * <pre>
 *   gradlew :test --tests "*StreetZonePreviewTest*"
 *   open build/zone-previews/street-*.png
 * </pre>
 */
public class StreetZonePreviewTest {

    private static final Path URBAN_SHEET   = Paths.get("mod/graphics/tilesets/urban-tileset.png");
    private static final Path ROAD_SHEET    = Paths.get("mod/graphics/tilesets/urban-tileset-2.png");
    private static final Path FLOORS_SHEET  = Paths.get("mod/graphics/tilesets/Floors_Tiles.png");
    private static final Path STREET3_SHEET = Paths.get("mod/graphics/tilesets/urban-tileset-3.png");
    private static final Path OUT_DIR       = Paths.get("build/zone-previews");

    /** Display cell size; matches the existing zone previews so contact sheets read at the same scale. */
    private static final int DISPLAY_CELL_PX = 48;

    private static final Color BG          = new Color(0x12, 0x18, 0x22);
    private static final Color CHECKER_A   = new Color(0x18, 0x1F, 0x2A);
    private static final Color CHECKER_B   = new Color(0x22, 0x2A, 0x36);
    private static final Color WALL_CENTER = new Color(0x18, 0x18, 0x1C);
    private static final Color LABEL_BG    = new Color(0, 0, 0, 200);
    private static final Color LABEL_FG    = new Color(0xE0, 0xE8, 0xF4);

    /**
     * Symbolic name for each frame the slicer pulls off urban-tileset-3.
     * Ordinal == slicer index — matches the {@link NatureTile} convention.
     * Kept inside the test for now: the production wiring (a real enum +
     * picker + integration into the map renderer) only makes sense once
     * the preview confirms the art fits, so we don't pay enum/wiring cost
     * up front for a sheet that might still need re-art.
     */
    private enum Frame {
        STREET_SQUARE   (Layer.GROUND,  "street (square paver)"),
        STREET_IRREGULAR(Layer.GROUND,  "street (irregular paver)"),
        SIDEWALK        (Layer.GROUND,  "sidewalk"),
        SIDEWALK_CORNER (Layer.GROUND,  "sidewalk (corner)"),
        CULVERT         (Layer.OVERLAY, "culvert"),
        BENCH_S         (Layer.OVERLAY, "bench (south-facing)"),
        BENCH_E         (Layer.OVERLAY, "bench (east-facing)");

        enum Layer { GROUND, OVERLAY }
        final Layer layer;
        final String label;
        Frame(Layer layer, String label) { this.layer = layer; this.label = label; }
        boolean isGround()  { return layer == Layer.GROUND; }
        boolean isOverlay() { return layer == Layer.OVERLAY; }
    }

    /** Inset only applied to ground frames — the sliced-bbox convention from {@link SlicedTileDrawer}. */
    private static final int GROUND_INSET_PX = SlicedTileDrawer.DEFAULT_GROUND_INSET_PX;

    /**
     * Asserts the slicer detects exactly {@link Frame#values()} frames and
     * writes a debug PNG with per-frame bounding boxes labeled. Same shape
     * as {@link NatureZonePreviewTest#slicerReturnsExpectedFrameCount}.
     */
    @Test
    void slicerReturnsExpectedFrameCount() throws Exception {
        BufferedImage sheet = ImageIO.read(Files.newInputStream(STREET3_SHEET));
        assertNotNull(sheet, "failed to load " + STREET3_SHEET);
        SpriteSheetFrames sliced = SpriteSheetSlicer.slice(sheet);

        Files.createDirectories(OUT_DIR);
        Path debug = OUT_DIR.resolve("street-slicer-debug.png");
        ImageIO.write(renderSlicerDebug(sheet, sliced), "PNG", debug.toFile());
        System.out.println("  wrote " + debug.toAbsolutePath());

        assertEquals(Frame.values().length, sliced.frames.length,
                () -> "slicer detected " + sliced.frames.length
                        + " frames but Frame enum expects " + Frame.values().length
                        + " (see " + debug + " for per-frame bboxes)");
    }

    /**
     * Renders every frame at large scale with its enum name + slicer bbox
     * underneath. The cell is the max-bbox uniform size used by the zone
     * renderer, so the gallery shows each frame at exactly the proportion
     * it'll have when stamped into a nav cell.
     */
    @Test
    void renderUrbanTile3Gallery() throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage sheet = ImageIO.read(Files.newInputStream(STREET3_SHEET));
        assertNotNull(sheet, "failed to load " + STREET3_SHEET);
        SpriteSheetFrames sliced = SpriteSheetSlicer.slice(sheet);

        int cellW = 0, cellH = 0;
        for (SpriteSheetFrames.Frame f : sliced.frames) {
            if (f.w > cellW) cellW = f.w;
            if (f.h > cellH) cellH = f.h;
        }
        // 4x display upscale so the gallery reads at the same physical size as nature-tile-gallery.
        int displayW = cellW * 4;
        int displayH = cellH * 4;

        int cols = 4;
        int rows = (sliced.frames.length + cols - 1) / cols;
        int gap = 18;
        int labelH = 40;
        int titleH = 40;
        int imgW = cols * (displayW + gap) + gap;
        int imgH = titleH + rows * (displayH + labelH + gap) + gap;

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(BG);
        g.fillRect(0, 0, imgW, imgH);

        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("urban-tileset-3 — " + sliced.frames.length + " frames sliced, "
                + Frame.values().length + " enum entries", gap, 28);

        Frame[] frames = Frame.values();
        TileSink sink = new Graphics2DTileSink(g, sheet);
        for (int i = 0; i < frames.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int x0 = gap + col * (displayW + gap);
            int y0 = titleH + gap + row * (displayH + labelH + gap);

            drawCheckerCell(g, x0, y0, displayW, displayH);

            SpriteSheetFrames.Frame f = sliced.frames[i];
            // Ground frames render with the standard inset so the gallery
            // reflects how they'll appear under bilinear when stamped at
            // the same display size in the scene render below.
            int inset = frames[i].isGround() ? GROUND_INSET_PX : 0;
            int srcX = f.x + inset;
            int srcY = f.y + inset;
            int srcW = Math.max(1, f.w - 2 * inset);
            int srcH = Math.max(1, f.h - 2 * inset);
            sink.drawSlice(srcX, srcY, srcW, srcH,
                    x0 + displayW / 2f, y0 + displayH / 2f,
                    displayW, displayH, 1f);

            g.setColor(new Color(0x4A, 0x6B, 0x8C));
            g.drawRect(x0, y0, displayW, displayH);

            g.setColor(LABEL_FG);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.drawString(i + ": " + frames[i].name() + "  (" + frames[i].label + ")",
                    x0, y0 + displayH + 16);

            g.setColor(new Color(0xA8, 0xC0, 0xE0));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString(frames[i].layer.name() + " · bbox " + f.w + "x" + f.h + "px",
                    x0, y0 + displayH + 30);
        }

        g.dispose();
        Path out = OUT_DIR.resolve("street-tile-gallery.png");
        ImageIO.write(img, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    /**
     * Renders four variants of a small "buildings facing a street" scene,
     * one per (street, sidewalk) combination so the four candidate
     * compositions can be eyeballed side by side. Each panel is the same
     * carved layout — only the street + sidewalk frame indices differ.
     *
     * <p>Buildings use the existing urban-tileset wall/floor art so the new
     * street/sidewalk tiles are evaluated against the actual neighbors
     * they'll have in a real city render — not in isolation.
     */
    @Test
    void renderStreetScenes() throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage urban    = ImageIO.read(Files.newInputStream(URBAN_SHEET));
        BufferedImage road     = ImageIO.read(Files.newInputStream(ROAD_SHEET));
        BufferedImage floors   = ImageIO.read(Files.newInputStream(FLOORS_SHEET));
        BufferedImage street3  = ImageIO.read(Files.newInputStream(STREET3_SHEET));
        assertNotNull(urban,   "failed to load " + URBAN_SHEET);
        assertNotNull(road,    "failed to load " + ROAD_SHEET);
        assertNotNull(floors,  "failed to load " + FLOORS_SHEET);
        assertNotNull(street3, "failed to load " + STREET3_SHEET);
        SpriteSheetFrames sliced = SpriteSheetSlicer.slice(street3);

        // 2x2 contact sheet exhaustively covers (square|irregular) × (plain|corner-alt).
        // Naming the panels by the two-frame combo makes the legend self-explanatory.
        Combo[] combos = {
                new Combo(Frame.STREET_SQUARE,    Frame.SIDEWALK,        "square street · plain sidewalk"),
                new Combo(Frame.STREET_IRREGULAR, Frame.SIDEWALK,        "irregular street · plain sidewalk"),
                new Combo(Frame.STREET_SQUARE,    Frame.SIDEWALK_CORNER, "square street · alt sidewalk"),
                new Combo(Frame.STREET_IRREGULAR, Frame.SIDEWALK_CORNER, "irregular street · alt sidewalk"),
        };

        BufferedImage[] panels = new BufferedImage[combos.length];
        for (int i = 0; i < combos.length; i++) {
            panels[i] = renderStreetScene(combos[i], urban, floors, sliced, street3);
        }

        BufferedImage contact = composeContactSheet(panels, 2);
        Path out = OUT_DIR.resolve("street-scenes-contact.png");
        ImageIO.write(contact, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    /**
     * Renders a sidewalk perimeter ring around an indoor floor, using
     * {@link Frame#SIDEWALK_CORNER} at the four corners and
     * {@link Frame#SIDEWALK} on every edge. Lets us see whether the two
     * sidewalk variants visually distinguish corner vs straight runs — if
     * they're indistinguishable, the corner pick is decorative noise; if
     * they read clearly, the placement rule is worth wiring up.
     */
    @Test
    void renderSidewalkCornerScene() throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage street3 = ImageIO.read(Files.newInputStream(STREET3_SHEET));
        BufferedImage urban   = ImageIO.read(Files.newInputStream(URBAN_SHEET));
        assertNotNull(street3, "failed to load " + STREET3_SHEET);
        assertNotNull(urban,   "failed to load " + URBAN_SHEET);
        SpriteSheetFrames sliced = SpriteSheetSlicer.slice(street3);

        int zoneW = 12;
        int zoneH = 9;

        BufferedImage img = newCanvas(zoneW, zoneH);
        Graphics2D g = configureGraphics(img);
        TileSink street3Sink = new Graphics2DTileSink(g, street3);
        renderCheckerBackdrop(g, zoneW, zoneH);

        // Outer ring (one cell thick) is sidewalk; interior is street tile —
        // gives the renderer a visible square of "open plaza" framed by
        // SIDEWALK / SIDEWALK_CORNER. We bias the corner variant to the
        // four convex corners only; everything else is the plain variant.
        for (int x = 0; x < zoneW; x++) {
            for (int y = 0; y < zoneH; y++) {
                boolean edgeX = (x == 0) || (x == zoneW - 1);
                boolean edgeY = (y == 0) || (y == zoneH - 1);
                boolean onRing = edgeX || edgeY;
                Frame frame;
                if (onRing) {
                    frame = (edgeX && edgeY) ? Frame.SIDEWALK_CORNER : Frame.SIDEWALK;
                } else {
                    frame = Frame.STREET_SQUARE;
                }
                stampFrame(street3Sink, sliced, frame, x, y, zoneH);
            }
        }

        drawLabel(g, zoneW, zoneH,
                "sidewalk ring — corners=SIDEWALK_CORNER, edges=SIDEWALK, interior=STREET_SQUARE");
        g.dispose();
        Path out = OUT_DIR.resolve("street-sidewalk-corners.png");
        ImageIO.write(img, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    // ---- scene render -----------------------------------------------------

    /** One {street, sidewalk} pairing rendered as a single panel. */
    private static final class Combo {
        final Frame street;
        final Frame sidewalk;
        final String label;
        Combo(Frame street, Frame sidewalk, String label) {
            this.street = street;
            this.sidewalk = sidewalk;
            this.label = label;
        }
    }

    private static BufferedImage renderStreetScene(Combo combo,
                                                   BufferedImage urban,
                                                   BufferedImage floors,
                                                   SpriteSheetFrames sliced,
                                                   BufferedImage street3) {
        // Grid layout (rendered y-up; row 0 = bottom of image):
        //   y=13..11  north building (wall ring + indoor floor)
        //   y=10      sidewalk strip
        //   y=9..4    road slab (6 cells wide)
        //   y=3       sidewalk strip
        //   y=2..0    south building
        int gridW = 22;
        int gridH = 14;
        int northWallTop    = gridH - 1;       // 13
        int northWallBottom = gridH - 3;       // 11
        int northSidewalkY  = gridH - 4;       // 10
        int roadTopY        = gridH - 5;       // 9
        int roadBottomY     = gridH - 10;      // 4
        int southSidewalkY  = gridH - 11;      // 3
        int southWallTop    = 2;
        int southWallBottom = 0;

        BufferedImage img = newCanvas(gridW, gridH);
        Graphics2D g = configureGraphics(img);
        renderCheckerBackdrop(g, gridW, gridH);

        TileSink urbanSink   = new Graphics2DTileSink(g, urban);
        TileSink floorsSink  = new Graphics2DTileSink(g, floors);
        TileSink street3Sink = new Graphics2DTileSink(g, street3);
        FixedGridTileDrawer urbanDrawer  = new FixedGridTileDrawer(TileManifest.TILE_SIZE);
        FixedGridTileDrawer floorsDrawer = new FixedGridTileDrawer(TileManifest.FLOORS_TILE_SIZE);

        // Wall mask: true on every wall cell of either building. Used to
        // pick floor / wall edge variants the same way the in-game pipeline
        // does (pickWallTile / pickFloorTile read neighbor-is-wall flags).
        boolean[][] wall = new boolean[gridW][gridH];
        for (int x = 0; x < gridW; x++) {
            // North building outer wall ring (rows 11..13), with one door at x=8.
            for (int y = northWallBottom; y <= northWallTop; y++) {
                boolean perimeter = y == northWallTop || y == northWallBottom || x == 0 || x == gridW - 1;
                wall[x][y] = perimeter;
            }
            // South building outer wall ring (rows 0..2), with one door at x=14.
            for (int y = southWallBottom; y <= southWallTop; y++) {
                boolean perimeter = y == southWallTop || y == southWallBottom || x == 0 || x == gridW - 1;
                wall[x][y] = perimeter;
            }
        }
        // Carve doors.
        wall[8 ][northWallBottom] = false;
        wall[14][southWallTop]    = false;

        // ---- Pass 1: streets ---------------------------------------------
        for (int x = 0; x < gridW; x++) {
            for (int y = roadBottomY; y <= roadTopY; y++) {
                stampFrame(street3Sink, sliced, combo.street, x, y, gridH);
            }
        }

        // ---- Pass 2: sidewalks (north + south strips, full width) --------
        for (int x = 0; x < gridW; x++) {
            stampFrame(street3Sink, sliced, combo.sidewalk, x, northSidewalkY, gridH);
            stampFrame(street3Sink, sliced, combo.sidewalk, x, southSidewalkY, gridH);
        }

        // ---- Pass 3: indoor floors ---------------------------------------
        for (int x = 0; x < gridW; x++) {
            for (int y = northWallBottom; y <= northWallTop; y++) {
                if (wall[x][y]) continue;
                boolean nWall = isWall(wall, x, y + 1, gridW, gridH);
                boolean sWall = isWall(wall, x, y - 1, gridW, gridH);
                boolean eWall = isWall(wall, x + 1, y, gridW, gridH);
                boolean wWall = isWall(wall, x - 1, y, gridW, gridH);
                TileManifest.TileFrame f = TileManifest.pickFloorTile(nWall, sWall, eWall, wWall);
                stampCell(urbanDrawer, urbanSink, f, x, y, gridH, urbanDrawer.defaultGroundInsetPx());
            }
            for (int y = southWallBottom; y <= southWallTop; y++) {
                if (wall[x][y]) continue;
                boolean nWall = isWall(wall, x, y + 1, gridW, gridH);
                boolean sWall = isWall(wall, x, y - 1, gridW, gridH);
                boolean eWall = isWall(wall, x + 1, y, gridW, gridH);
                boolean wWall = isWall(wall, x - 1, y, gridW, gridH);
                TileManifest.TileFrame f = TileManifest.pickFloorTile(nWall, sWall, eWall, wWall);
                stampCell(urbanDrawer, urbanSink, f, x, y, gridH, urbanDrawer.defaultGroundInsetPx());
            }
        }

        // ---- Pass 4: walls -----------------------------------------------
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                if (!wall[x][y]) continue;
                boolean nWall = isWall(wall, x, y + 1, gridW, gridH);
                boolean sWall = isWall(wall, x, y - 1, gridW, gridH);
                boolean eWall = isWall(wall, x + 1, y, gridW, gridH);
                boolean wWall = isWall(wall, x - 1, y, gridW, gridH);
                TileManifest.TileFrame tile = TileManifest.pickWallTile(nWall, sWall, eWall, wWall);
                if (tile == null) {
                    g.setColor(WALL_CENTER);
                    g.fillRect(x * DISPLAY_CELL_PX, (gridH - 1 - y) * DISPLAY_CELL_PX,
                            DISPLAY_CELL_PX, DISPLAY_CELL_PX);
                } else {
                    stampCell(urbanDrawer, urbanSink, tile, x, y, gridH,
                            FixedGridTileDrawer.OVERLAY_INSET_PX);
                }
            }
        }

        // ---- Pass 5: doodads ---------------------------------------------
        // Mix all three doodads from urban-tileset-3 across both sidewalks
        // and the road shoulders so each one's fit is visible against the
        // chosen street/sidewalk pairing.
        // North sidewalk: two south-facing benches + one culvert at curb.
        stampFrame(street3Sink, sliced, Frame.BENCH_S,  4,  northSidewalkY, gridH);
        stampFrame(street3Sink, sliced, Frame.BENCH_S, 12,  northSidewalkY, gridH);
        stampFrame(street3Sink, sliced, Frame.CULVERT, 17,  northSidewalkY, gridH);
        // South sidewalk: east-facing bench + culvert + a second east bench.
        stampFrame(street3Sink, sliced, Frame.BENCH_E,  3,  southSidewalkY, gridH);
        stampFrame(street3Sink, sliced, Frame.CULVERT,  9,  southSidewalkY, gridH);
        stampFrame(street3Sink, sliced, Frame.BENCH_E, 18,  southSidewalkY, gridH);

        drawLabel(g, gridW, gridH, combo.label);
        // Unused-floor reference: keeps the warning off without the import
        // disappearing if someone adds a fl-tile sample to this scene later.
        if (floors == null || floorsDrawer == null) drawLabel(g, gridW, gridH, "<no-op>");

        g.dispose();
        return img;
    }

    // ---- helpers ----------------------------------------------------------

    private static boolean isWall(boolean[][] wall, int x, int y, int gridW, int gridH) {
        // OOB treated as not-wall — matches the floor/road picker semantics
        // in BattleScreen.isInBoundsWall, so building edges at the panel
        // boundary don't pick up a phantom wall neighbor.
        if (x < 0 || x >= gridW || y < 0 || y >= gridH) return false;
        return wall[x][y];
    }

    private static void stampCell(FixedGridTileDrawer drawer, TileSink sink,
                                   TileManifest.TileFrame f, int gridX, int gridY, int gridH,
                                   int insetPx) {
        if (f == null) return;
        float dstCx = gridX * DISPLAY_CELL_PX + DISPLAY_CELL_PX / 2f;
        float dstCy = (gridH - 1 - gridY) * DISPLAY_CELL_PX + DISPLAY_CELL_PX / 2f;
        drawer.draw(sink, f, dstCx, dstCy, DISPLAY_CELL_PX, DISPLAY_CELL_PX, 1f, insetPx);
    }

    private static void stampFrame(TileSink sink, SpriteSheetFrames sliced,
                                    Frame frame, int gridX, int gridY, int gridH) {
        int idx = frame.ordinal();
        if (idx < 0 || idx >= sliced.frames.length) return;
        SpriteSheetFrames.Frame f = sliced.frames[idx];
        int inset = frame.isGround() ? GROUND_INSET_PX : 0;
        int srcX = f.x + inset;
        int srcY = f.y + inset;
        int srcW = Math.max(1, f.w - 2 * inset);
        int srcH = Math.max(1, f.h - 2 * inset);
        float dstCx = gridX * DISPLAY_CELL_PX + DISPLAY_CELL_PX / 2f;
        float dstCy = (gridH - 1 - gridY) * DISPLAY_CELL_PX + DISPLAY_CELL_PX / 2f;
        sink.drawSlice(srcX, srcY, srcW, srcH, dstCx, dstCy, DISPLAY_CELL_PX, DISPLAY_CELL_PX, 1f);
    }

    private static BufferedImage newCanvas(int gridW, int gridH) {
        int imgW = gridW * DISPLAY_CELL_PX;
        int imgH = gridH * DISPLAY_CELL_PX + 28;
        return new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D configureGraphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static void renderCheckerBackdrop(Graphics2D g, int gridW, int gridH) {
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                g.setColor(((x + y) % 2 == 0) ? CHECKER_A : CHECKER_B);
                g.fillRect(x * DISPLAY_CELL_PX, (gridH - 1 - y) * DISPLAY_CELL_PX,
                        DISPLAY_CELL_PX, DISPLAY_CELL_PX);
            }
        }
    }

    private static void drawCheckerCell(Graphics2D g, int x0, int y0, int w, int h) {
        for (int cy = 0; cy < h; cy += 8) {
            for (int cx = 0; cx < w; cx += 8) {
                g.setColor(((cx / 8 + cy / 8) % 2 == 0) ? CHECKER_A : CHECKER_B);
                g.fillRect(x0 + cx, y0 + cy, Math.min(8, w - cx), Math.min(8, h - cy));
            }
        }
    }

    private static void drawLabel(Graphics2D g, int gridW, int gridH, String text) {
        int imgW = gridW * DISPLAY_CELL_PX;
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(LABEL_BG);
        g.fillRect(0, gridH * DISPLAY_CELL_PX, imgW, 28);
        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString(text, 8, gridH * DISPLAY_CELL_PX + 19);
    }

    private static BufferedImage renderSlicerDebug(BufferedImage sheet, SpriteSheetFrames sliced) {
        int scale = 2;
        int padTop = 24;
        BufferedImage img = new BufferedImage(
                sheet.getWidth() * scale,
                sheet.getHeight() * scale + padTop,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x10, 0x14, 0x1C));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("urban-tileset-3 slicer — " + sliced.frames.length
                + " frames detected (enum expects " + Frame.values().length + ")", 8, 16);

        g.drawImage(sheet, 0, padTop,
                sheet.getWidth() * scale, sheet.getHeight() * scale + padTop, null);

        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < sliced.frames.length; i++) {
            SpriteSheetFrames.Frame f = sliced.frames[i];
            int sx = f.x * scale;
            int sy = f.y * scale + padTop;
            int sw = f.w * scale;
            int sh = f.h * scale;
            g.setColor(new Color(0x6A, 0xFF, 0x88, 220));
            g.drawRect(sx, sy, sw, sh);
            g.setColor(Color.BLACK);
            g.drawString(Integer.toString(i), sx + 3, sy + 14);
            g.setColor(new Color(0xFF, 0xE0, 0x70));
            g.drawString(Integer.toString(i), sx + 2, sy + 13);
        }
        g.dispose();
        return img;
    }

    private static BufferedImage composeContactSheet(BufferedImage[] panels, int cols) {
        int rows = (panels.length + cols - 1) / cols;
        int maxW = 0, maxH = 0;
        for (BufferedImage p : panels) {
            maxW = Math.max(maxW, p.getWidth());
            maxH = Math.max(maxH, p.getHeight());
        }
        int gap = 12;
        BufferedImage sheet = new BufferedImage(
                cols * maxW + (cols + 1) * gap,
                rows * maxH + (rows + 1) * gap,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x15, 0x18, 0x1F));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < panels.length; i++) {
            int cx = i % cols;
            int cy = i / cols;
            int x = gap + cx * (maxW + gap);
            int y = gap + cy * (maxH + gap);
            g.drawImage(panels[i], x, y, null);
        }
        g.dispose();
        return sheet;
    }
}
