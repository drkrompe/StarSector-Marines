package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.map.TileManifest;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.map.WallMasks;
import com.dillon.starsectormarines.battle.mapgen.MapResult;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sprites.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.sprites.Graphics2DTileSink;
import com.dillon.starsectormarines.battle.sprites.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.sprites.SpriteSheetSlicer;
import com.dillon.starsectormarines.battle.sprites.TileSink;
import com.dillon.starsectormarines.battle.sprites.UrbanTile3;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
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
 * Bridge between {@link BspMapPreviewTest} (real BSP generator, flat-color
 * per-{@link GroundKind} render) and {@link
 * com.dillon.starsectormarines.battle.sprites.StreetZonePreviewTest} (hand-
 * built topology, production sprite-picker render). Runs the real
 * {@link BspCityGenerator} end-to-end and renders the {@link MapResult}
 * through the same picker dispatch
 * {@link com.dillon.starsectormarines.ops.BattleScreen#renderTiledFloorsAndWalls}
 * uses in-game, so the PNG output is what'll appear on screen rather than a
 * color-coded debug view.
 *
 * <p>Lets us baseline the visual output before Phase 2B rewires
 * {@link TrunkPlan}/{@link Bsp} to emit 2-thick {@link GroundKind#SIDEWALK}
 * against wide trunks and {@link GroundKind#GRASS} between non-road-facing
 * buildings — the same test re-run after that work shows the visual delta
 * directly. Cell size is bumped to {@value #CELL_PX}px so the autotile
 * art is legible (vs. the 8px-per-cell color-block preview); the seed set
 * is correspondingly smaller to keep PNG sizes reasonable.
 *
 * <p>Outputs: {@code build/map-previews/sprite-seed-NNNN.png} (one per
 * seed). Re-run via
 * {@code gradlew :test --tests "*BspMapSpritePreviewTest*"}.
 */
public class BspMapSpritePreviewTest {

    private static final int GRID_W = 80;
    private static final int GRID_H = 80;
    /** Pixels per nav cell on the rendered PNG. 24px is large enough that 32px-source autotile art reads cleanly under the bilinear downscale; smaller values turn wall caps into one-pixel blurs. */
    private static final int CELL_PX = 24;
    private static final long[] SEEDS = { 1L, 42L, 777L };

    private static final Path OUT_DIR       = Paths.get("build/map-previews");
    private static final Path URBAN_SHEET   = Paths.get("mod/graphics/tilesets/urban-tileset.png");
    private static final Path ROAD_SHEET    = Paths.get("mod/graphics/tilesets/urban-tileset-2.png");
    private static final Path FLOORS_SHEET  = Paths.get("mod/graphics/tilesets/Floors_Tiles.png");
    private static final Path WATER_SHEET   = Paths.get("mod/graphics/tilesets/Water_tiles.png");
    private static final Path STREET3_SHEET = Paths.get("mod/graphics/tilesets/urban-tileset-3.png");
    private static final Path NATURE_SHEET  = Paths.get("mod/graphics/tilesets/nature-tiles.png");

    private static final Color BG          = new Color(0x10, 0x14, 0x1C);
    private static final Color WALL_CENTER = new Color(0x18, 0x18, 0x1C);
    private static final Color LABEL_BG    = new Color(0, 0, 0, 200);
    private static final Color LABEL_FG    = new Color(0xE0, 0xE8, 0xF4);
    private static final Color MARINE_FG   = new Color(80, 220, 100);
    private static final Color DEFENDER_FG = new Color(220, 80, 80);

    @Test
    void renderSpriteBatch() throws Exception {
        Files.createDirectories(OUT_DIR);

        BufferedImage urban   = ImageIO.read(Files.newInputStream(URBAN_SHEET));
        BufferedImage road    = ImageIO.read(Files.newInputStream(ROAD_SHEET));
        BufferedImage floors  = ImageIO.read(Files.newInputStream(FLOORS_SHEET));
        BufferedImage water   = ImageIO.read(Files.newInputStream(WATER_SHEET));
        BufferedImage street3 = ImageIO.read(Files.newInputStream(STREET3_SHEET));
        BufferedImage nature  = ImageIO.read(Files.newInputStream(NATURE_SHEET));
        assertNotNull(urban,   "failed to load " + URBAN_SHEET);
        assertNotNull(road,    "failed to load " + ROAD_SHEET);
        assertNotNull(floors,  "failed to load " + FLOORS_SHEET);
        assertNotNull(water,   "failed to load " + WATER_SHEET);
        assertNotNull(street3, "failed to load " + STREET3_SHEET);
        assertNotNull(nature,  "failed to load " + NATURE_SHEET);
        SpriteSheetFrames street3Frames = SpriteSheetSlicer.slice(street3);
        SpriteSheetFrames natureFrames  = SpriteSheetSlicer.slice(nature);

        BspCityGenerator gen = new BspCityGenerator();
        for (long seed : SEEDS) {
            MapResult map = gen.generate(GRID_W, GRID_H, seed);
            BufferedImage img = renderMapSprites(map, seed,
                    urban, road, floors, water, street3, street3Frames,
                    nature, natureFrames);
            Path out = OUT_DIR.resolve(String.format("sprite-seed-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
        }
    }

    /**
     * Mirrors {@link com.dillon.starsectormarines.ops.BattleScreen#renderTiledFloorsAndWalls}
     * — floor pass, then wall pass, then a marker overlay for the marine /
     * defender spawn anchors so the PNG carries the same context the in-game
     * pipeline would. Routes every {@link GroundKind} through the same picker
     * the production renderer dispatches to; only the pixel-push backend
     * differs (Graphics2D here vs. QuadBatch in-game).
     */
    private BufferedImage renderMapSprites(MapResult map, long seed,
                                            BufferedImage urban, BufferedImage road,
                                            BufferedImage floors, BufferedImage water,
                                            BufferedImage street3, SpriteSheetFrames street3Frames,
                                            BufferedImage nature, SpriteSheetFrames natureFrames) {
        NavigationGrid grid = map.grid;
        CellTopology topo = map.topology;
        int gw = grid.getWidth();
        int gh = grid.getHeight();
        int labelH = 24;
        BufferedImage img = new BufferedImage(gw * CELL_PX, gh * CELL_PX + labelH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(BG);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        TileSink urbanSink   = new Graphics2DTileSink(g, urban);
        TileSink roadSink    = new Graphics2DTileSink(g, road);
        TileSink floorsSink  = new Graphics2DTileSink(g, floors);
        TileSink waterSink   = new Graphics2DTileSink(g, water);
        TileSink street3Sink = new Graphics2DTileSink(g, street3);
        TileSink natureSink  = new Graphics2DTileSink(g, nature);
        FixedGridTileDrawer urbanDrawer  = new FixedGridTileDrawer(TileManifest.TILE_SIZE);
        FixedGridTileDrawer floorsDrawer = new FixedGridTileDrawer(TileManifest.FLOORS_TILE_SIZE);
        int urbanInset  = urbanDrawer.defaultGroundInsetPx();
        int floorsInset = floorsDrawer.defaultGroundInsetPx();

        // ---- Floor pass ----
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                if (topo.isWall(x, y)) continue;
                boolean nWall = isInBoundsWall(topo, x, y + 1);
                boolean sWall = isInBoundsWall(topo, x, y - 1);
                boolean eWall = isInBoundsWall(topo, x + 1, y);
                boolean wWall = isInBoundsWall(topo, x - 1, y);

                GroundKind kind = topo.getGroundKind(x, y);
                switch (kind) {
                    case STREET:
                        // STREET cells dispatch through urban-tileset-3 — same
                        // path BattleScreen takes when urbanTile3Batch is loaded.
                        // No legacy ROAD_SHEET fallback here: the test always
                        // has urban-3 available, so reproducing the fallback
                        // would be dead code that doesn't match what ships.
                        if (isSidewalkCell(grid, topo, x, y)) {
                            // Use isSidewalkLike on neighbors so an explicit
                            // GroundKind.SIDEWALK (trunk flank) butting up
                            // against a wall-adjacent STREET sidewalk reads
                            // as one continuous strip — matches BattleScreen.
                            boolean nNotSw = !isSidewalkLike(grid, topo, x, y + 1);
                            boolean sNotSw = !isSidewalkLike(grid, topo, x, y - 1);
                            boolean eNotSw = !isSidewalkLike(grid, topo, x + 1, y);
                            boolean wNotSw = !isSidewalkLike(grid, topo, x - 1, y);
                            UrbanTile3 f = TileManifest.pickStreet3SidewalkFrame(
                                    nNotSw, sNotSw, eNotSw, wNotSw);
                            stampSlicedFrame(street3Sink, street3Frames, f, x, y, gh);
                        } else {
                            stampSlicedFrame(street3Sink, street3Frames,
                                    UrbanTile3.STREET_SQUARE, x, y, gh);
                        }
                        break;
                    case BRICK:
                        stampFrame(floorsDrawer, floorsSink,
                                TileManifest.pickBrickTile(x, y), x, y, gh, floorsInset);
                        break;
                    case SIDEWALK: {
                        // Curb-side sidewalk strip — urban-tileset-3 with
                        // corner-aware picker. Mirrors BattleScreen's SIDEWALK
                        // case so the preview reflects production dispatch.
                        boolean nNotSw = !isSidewalkLike(grid, topo, x, y + 1);
                        boolean sNotSw = !isSidewalkLike(grid, topo, x, y - 1);
                        boolean eNotSw = !isSidewalkLike(grid, topo, x + 1, y);
                        boolean wNotSw = !isSidewalkLike(grid, topo, x - 1, y);
                        UrbanTile3 f = TileManifest.pickStreet3SidewalkFrame(
                                nNotSw, sNotSw, eNotSw, wNotSw);
                        stampSlicedFrame(street3Sink, street3Frames, f, x, y, gh);
                        break;
                    }
                    case GRASS:
                        stampNatureFrame(natureSink, natureFrames,
                                TileManifest.pickNatureGrassTile(x, y), x, y, gh);
                        break;
                    case DIRT:
                        stampNatureFrame(natureSink, natureFrames,
                                TileManifest.pickNatureDirtTile(x, y), x, y, gh);
                        break;
                    case STONE:
                        stampFrame(floorsDrawer, floorsSink,
                                TileManifest.pickStoneTile(false, false, false, false, x, y),
                                x, y, gh, floorsInset);
                        break;
                    case SAND:
                        stampFrame(floorsDrawer, floorsSink,
                                TileManifest.pickSandTile(false, false, false, false, x, y),
                                x, y, gh, floorsInset);
                        break;
                    case SNOW:
                        stampFrame(floorsDrawer, floorsSink,
                                TileManifest.pickSnowTile(false, false, false, false, x, y),
                                x, y, gh, floorsInset);
                        break;
                    case WATER:
                        // Same caveat as production: pickWaterTile returns a
                        // frame that's read off the Water_tiles sheet. Per the
                        // memory note water_uses_nature_tile.md the dedicated
                        // sheet is mostly orphaned now, but the picker still
                        // points there — render whatever the picker says.
                        stampFrame(floorsDrawer, waterSink,
                                TileManifest.pickWaterTile(false, false, false, false, x, y),
                                x, y, gh, floorsInset);
                        break;
                    case TILE:
                        stampFrame(urbanDrawer, roadSink,
                                TileManifest.pickTileGroundTile(x, y), x, y, gh, urbanInset);
                        break;
                    case COURTYARD:
                        stampFrame(urbanDrawer, roadSink,
                                TileManifest.pickCourtyardTile(nWall, sWall, eWall, wWall),
                                x, y, gh, urbanInset);
                        break;
                    case STRIPED:
                        stampFrame(urbanDrawer, roadSink,
                                TileManifest.pickStripedTile(nWall, sWall, eWall, wWall),
                                x, y, gh, urbanInset);
                        break;
                    case LZ_MARKER:
                        stampFrame(urbanDrawer, roadSink,
                                TileManifest.pickLzMarkerTile(), x, y, gh, urbanInset);
                        break;
                    case RUBBLE:
                        stampFrame(urbanDrawer, urbanSink,
                                TileManifest.pickRubbleTile(nWall, sWall, eWall, wWall),
                                x, y, gh, urbanInset);
                        break;
                    case INDOOR:
                    default:
                        stampFrame(urbanDrawer, urbanSink,
                                TileManifest.pickFloorTile(nWall, sWall, eWall, wWall),
                                x, y, gh, urbanInset);
                        break;
                }

                if (grid.isDoorway(x, y) && !topo.isRubble(x, y)) {
                    stampFrame(urbanDrawer, urbanSink, TileManifest.DOOR_OPEN, x, y, gh, 0);
                }
            }
        }

        // ---- Wall pass ---- identical line to BattleScreen + StreetZonePreviewTest.
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                if (!topo.isWall(x, y)) continue;
                int mask = topo.getWallDirMask(x, y);
                TileManifest.TileFrame tile = WallMasks.pickTileFromMask(mask);
                if (tile == null) {
                    g.setColor(WALL_CENTER);
                    g.fillRect(x * CELL_PX, (gh - 1 - y) * CELL_PX, CELL_PX, CELL_PX);
                } else {
                    stampFrame(urbanDrawer, urbanSink, tile, x, y, gh, 0);
                }
            }
        }

        // ---- Spawn markers ---- green = marine, red = defender.
        g.setColor(MARINE_FG);
        drawDiamond(g, map.marineSpawnX, map.marineSpawnY, gh);
        g.setColor(DEFENDER_FG);
        drawDiamond(g, map.defenderSpawnX, map.defenderSpawnY, gh);

        // ---- Label strip ----
        g.setColor(LABEL_BG);
        g.fillRect(0, gh * CELL_PX, img.getWidth(), labelH);
        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString(String.format(
                "BSP sprite preview — seed=%d  %dx%d cells  marine=green  defender=red",
                seed, gw, gh), 8, gh * CELL_PX + 17);

        g.dispose();
        return img;
    }

    private void stampFrame(FixedGridTileDrawer drawer, TileSink sink,
                            TileManifest.TileFrame frame,
                            int gridX, int gridY, int gridH, int inset) {
        if (frame == null) return;
        float cx = gridX * CELL_PX + CELL_PX / 2f;
        float cy = (gridH - 1 - gridY) * CELL_PX + CELL_PX / 2f;
        drawer.draw(sink, frame, cx, cy, CELL_PX, CELL_PX, 1f, inset);
    }

    private void stampNatureFrame(TileSink sink, SpriteSheetFrames frames,
                                   com.dillon.starsectormarines.battle.sprites.NatureTile tile,
                                   int gridX, int gridY, int gridH) {
        if (tile == null) return;
        int idx = tile.frameIndex();
        if (idx < 0 || idx >= frames.frames.length) return;
        SpriteSheetFrames.Frame f = frames.frames[idx];
        int inset = tile.isGround() ? 2 : 0;
        int srcX = f.x + inset;
        int srcY = f.y + inset;
        int srcW = Math.max(1, f.w - 2 * inset);
        int srcH = Math.max(1, f.h - 2 * inset);
        float cx = gridX * CELL_PX + CELL_PX / 2f;
        float cy = (gridH - 1 - gridY) * CELL_PX + CELL_PX / 2f;
        sink.drawSlice(srcX, srcY, srcW, srcH, cx, cy, CELL_PX, CELL_PX, 1f);
    }

    private void stampSlicedFrame(TileSink sink, SpriteSheetFrames frames,
                                  UrbanTile3 tile, int gridX, int gridY, int gridH) {
        if (tile == null) return;
        int idx = tile.frameIndex();
        if (idx < 0 || idx >= frames.frames.length) return;
        SpriteSheetFrames.Frame f = frames.frames[idx];
        // Same inset rule as SlicedTileDrawer — ground tiles take the 2px
        // default inset, overlays pass through at full bbox.
        int inset = tile.isGround() ? 2 : 0;
        int srcX = f.x + inset;
        int srcY = f.y + inset;
        int srcW = Math.max(1, f.w - 2 * inset);
        int srcH = Math.max(1, f.h - 2 * inset);
        float cx = gridX * CELL_PX + CELL_PX / 2f;
        float cy = (gridH - 1 - gridY) * CELL_PX + CELL_PX / 2f;
        sink.drawSlice(srcX, srcY, srcW, srcH, cx, cy, CELL_PX, CELL_PX, 1f);
    }

    /** Mirrors {@code BattleScreen.isInBoundsWall} — OOB cells are not walls. */
    private static boolean isInBoundsWall(CellTopology topo, int x, int y) {
        return topo.inBounds(x, y) && topo.isWall(x, y);
    }

    /** Mirrors {@code BattleScreen.isSidewalkCell} — STREET cell flanking at least one in-bounds wall. */
    private static boolean isSidewalkCell(NavigationGrid grid, CellTopology topo, int x, int y) {
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || !topo.isStreet(x, y)) return false;
        return isInBoundsWall(topo, x + 1, y)
                || isInBoundsWall(topo, x - 1, y)
                || isInBoundsWall(topo, x, y + 1)
                || isInBoundsWall(topo, x, y - 1);
    }

    /** Mirrors {@code BattleScreen.isSidewalkLikeCell} — explicit SIDEWALK kind or STREET-wall-adjacent. */
    private static boolean isSidewalkLike(NavigationGrid grid, CellTopology topo, int x, int y) {
        if (!topo.inBounds(x, y)) return false;
        if (topo.getGroundKind(x, y) == GroundKind.SIDEWALK) return true;
        return isSidewalkCell(grid, topo, x, y);
    }

    private void drawDiamond(Graphics2D g, int gridX, int gridY, int gridH) {
        int s = CELL_PX / 2;
        int cx = gridX * CELL_PX + CELL_PX / 2;
        int cy = (gridH - 1 - gridY) * CELL_PX + CELL_PX / 2;
        int[] xs = { cx, cx + s, cx, cx - s };
        int[] ys = { cy - s, cy, cy + s, cy };
        g.fillPolygon(xs, ys, 4);
    }
}
