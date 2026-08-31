package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.air.AirScale;
import com.dillon.starsectormarines.battle.air.ParkedAircraft;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Bridge between {@link BspMapPreviewTest} (real BSP generator, flat-color
 * per-{@link GroundKind} render) and {@link
 * com.dillon.starsectormarines.battle.world.tiles.StreetZonePreviewTest} (hand-
 * built topology, production sprite-picker render). Runs the real
 * {@link BspCityGenerator} end-to-end and renders the {@link MapResult}
 * through the ordinary headless battle scene. The generated map enters battle
 * setup and the production ground and doodad render systems collect it; only
 * the final graphics drain differs from live play.
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
 * seed), plus close generated-compound crops at
 * {@code build/map-previews/sprite-conquest-military-compound.png} and
 * {@code build/map-previews/sprite-gated-housing-compound.png}, and a generated
 * administrative interior at
 * {@code build/map-previews/sprite-civic-headquarters.png}. The authored
 * shared-edge firing windows receive their own close production-path crop at
 * {@code build/map-previews/sprite-forward-bunker-window.png}. Re-run via
 * {@code gradlew :test --tests "*BspMapSpritePreviewTest*"}.
 */
public class BspMapSpritePreviewTest {

    private static final int GRID_W = 80;
    private static final int GRID_H = 80;
    /** Pixels per nav cell on the rendered PNG. 24px is large enough that 32px-source autotile art reads cleanly under the bilinear downscale; smaller values turn wall caps into one-pixel blurs. */
    private static final int CELL_PX = 24;
    private static final long[] SEEDS = { 1L, 42L, 777L };

    private static final Path OUT_DIR       = Paths.get("build/map-previews");
    private static final Color LABEL_BG    = new Color(0, 0, 0, 200);
    private static final Color LABEL_FG    = new Color(0xE0, 0xE8, 0xF4);
    private static final Color MARINE_FG   = new Color(80, 220, 100);
    private static final Color DEFENDER_FG = new Color(220, 80, 80);
    private static final Color MED_COVER_FG = new Color(255, 210, 70, 225);
    private static final Color HEAVY_COVER_FG = new Color(255, 75, 205, 235);

    private static HeadlessBattleMapRenderer battleMaps;

    @BeforeAll
    static void installRegistry() throws Exception {
        // NatureZoneFiller reads TileRegistry.installed() during gen. Install a
        // disk-loaded registry so overlays are stamped (not silently skipped).
        TileRegistry reg = new TileRegistry();
        for (String path : TileRegistry.BUILTIN_TILESETS) {
            String text = Files.readString(Paths.get("mod/" + path));
            reg.ingestSheet(new JSONObject(text));
        }
        reg.validateReferences();
        TileRegistry.install(reg);
        battleMaps = new HeadlessBattleMapRenderer(Paths.get("mod"));
    }

    @Test
    void renderSpriteBatch() throws Exception {
        Files.createDirectories(OUT_DIR);

        BspCityGenerator gen = new BspCityGenerator();
        for (long seed : SEEDS) {
            MapResult map = gen.generate(GRID_W, GRID_H, seed);
            BufferedImage img = renderMapSprites(map, seed);
            Path out = OUT_DIR.resolve(String.format("sprite-seed-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
        }
    }

    /**
     * Close production-path render of a generated Conquest compound. The crop
     * changes only the evidence framing; every visible cell comes from the
     * same complete-map draw-command stream used by {@link #renderSpriteBatch()}.
     */
    @Test
    void renderConquestCompound() throws Exception {
        Files.createDirectories(OUT_DIR);
        long seed = 1L;
        BspCityGenerator generator = new BspCityGenerator();
        MapResult map = generator.generate(240, 160, seed,
                TraversalAxis.SOUTH_TO_NORTH);
        Compound compound = generator.getLastCompounds().stream()
                .filter(candidate -> candidate.kind == BlockKind.MILITARY_BASE)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Conquest preview seed must contain a military compound"));

        int cellPx = 16;
        BufferedImage full = battleMaps.render(map, seed, cellPx);
        BufferedImage crop = cropCompound(full, map, compound, cellPx, 4);

        Path out = OUT_DIR.resolve("sprite-conquest-military-compound.png");
        ImageIO.write(crop, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    /** Close production-path render of the shared-edge windows on a forward bunker. */
    @Test
    void renderForwardBunkerWindow() throws Exception {
        Files.createDirectories(OUT_DIR);
        // Whichever of these seeds lays down a bunker. A fixed seed pins this
        // render to one map's luck: not every Conquest seed has a forward
        // bunker on it, so a resize that shuffles the layout fails the test for
        // having generated a different city rather than a broken window.
        MapResult map = null;
        TacticalNode bunker = null;
        long seed = 0L;
        for (long candidateSeed : new long[]{ 777L, 3L, 5L, 7L, 11L, 42L }) {
            MapResult candidate = new BspCityGenerator().generate(
                    BattleSetup.CONQUEST_GRID_W,
                    BattleSetup.CONQUEST_GRID_H,
                    candidateSeed, TraversalAxis.SOUTH_TO_NORTH);
            List<TacticalNode> bunkers =
                    candidate.tacticalMap.ofKind(TacticalNode.Kind.FORWARD_BUNKER);
            if (bunkers.isEmpty()) continue;
            map = candidate;
            bunker = bunkers.get(0);
            seed = candidateSeed;
            break;
        }
        if (bunker == null) {
            throw new AssertionError("no Conquest seed in the sweep laid down a forward bunker");
        }

        int margin = 3;
        int widthCells = bunker.right - bunker.left + 1 + margin * 2;
        int heightCells = bunker.bottom - bunker.top + 1 + margin * 2;
        float centerX = (bunker.left + bunker.right + 1) * 0.5f;
        float centerY = (bunker.top + bunker.bottom + 1) * 0.5f;
        BufferedImage crop = battleMaps.renderView(map, seed,
                centerX, centerY, widthCells, heightCells, 40);

        Path out = OUT_DIR.resolve("sprite-forward-bunker-window.png");
        ImageIO.write(crop, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    /** Production-path crop of the first deterministic generated housing compound. */
    @Test
    void renderGatedHousingCompound() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator generator = new BspCityGenerator();
        MapResult map = null;
        Compound compound = null;
        long previewSeed = -1L;
        for (long seed = 0; seed < 60 && compound == null; seed++) {
            MapResult candidateMap = generator.generate(GRID_W, GRID_H, seed);
            Compound candidateCompound = generator.getLastCompounds().stream()
                    .filter(candidate -> candidate.kind == BlockKind.GATED_HOUSING)
                    .findFirst()
                    .orElse(null);
            if (candidateCompound != null) {
                map = candidateMap;
                compound = candidateCompound;
                previewSeed = seed;
            }
        }
        if (compound == null || map == null) {
            throw new AssertionError("Preview seed range must contain gated housing");
        }

        int cellPx = 20;
        BufferedImage full = battleMaps.render(map, previewSeed, cellPx);
        BufferedImage crop = cropCompound(full, map, compound, cellPx, 4);
        Path out = OUT_DIR.resolve("sprite-gated-housing-compound.png");
        ImageIO.write(crop, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    /** Production-path crop of a generated civic headquarters and its room equipment. */
    @Test
    void renderCivicHeadquarters() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator generator = new BspCityGenerator();
        MapResult map = null;
        PointOfInterest headquarters = null;
        long previewSeed = -1L;
        for (long seed = 0; seed < 30 && headquarters == null; seed++) {
            MapResult candidateMap = generator.generate(GRID_W, GRID_H, seed);
            PointOfInterest candidate = candidateMap.pointsOfInterest.stream()
                    .filter(poi -> poi.kind == PointOfInterest.Kind.ADMINISTRATIVE)
                    .findFirst()
                    .orElse(null);
            if (candidate != null) {
                map = candidateMap;
                headquarters = candidate;
                previewSeed = seed;
            }
        }
        if (headquarters == null || map == null) {
            throw new AssertionError("Preview seed range must contain a civic headquarters");
        }

        int cellPx = 24;
        BufferedImage full = battleMaps.render(map, previewSeed, cellPx);
        BufferedImage crop = cropBounds(full, map,
                headquarters.left, headquarters.top,
                headquarters.right, headquarters.bottom, cellPx, 4);
        Path out = OUT_DIR.resolve("sprite-civic-headquarters.png");
        ImageIO.write(crop, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    private static BufferedImage cropCompound(BufferedImage full, MapResult map,
                                              Compound compound, int cellPx,
                                              int margin) {
        return cropBounds(full, map, compound.left, compound.top,
                compound.right, compound.bottom, cellPx, margin);
    }

    private static BufferedImage cropBounds(BufferedImage full, MapResult map,
                                            int boundsLeft, int boundsTop,
                                            int boundsRight, int boundsBottom,
                                            int cellPx, int margin) {
        int left = Math.max(0, boundsLeft - margin);
        int top = Math.max(0, boundsTop - margin);
        int right = Math.min(map.grid.getWidth() - 1, boundsRight + margin);
        int bottom = Math.min(map.grid.getHeight() - 1, boundsBottom + margin);
        int imageY = (map.grid.getHeight() - 1 - bottom) * cellPx;
        return full.getSubimage(left * cellPx, imageY,
                (right - left + 1) * cellPx,
                (bottom - top + 1) * cellPx);
    }

    /** Full-map look at the campaign-backed civilian port district and its authored berths. */
    @Test
    void renderCivilianSpaceportDistrict() throws Exception {
        Files.createDirectories(OUT_DIR);
        TargetProfile profile = new TargetProfile(5, 6, 1, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.SPACEPORT),
                SurfacePalette.ROCK);
        MapResult map = new BspCityGenerator().generate(GRID_W, GRID_H, 42L, null, profile);
        BufferedImage img = renderMapSprites(map, 42L);

        gMarkLandingPads(img, map);
        Path out = OUT_DIR.resolve("civilian-spaceport-district.png");
        ImageIO.write(img, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());

        BufferedImage cover = copyOf(img);
        drawDoodadCoverOverlay(cover, map.doodads, map.grid.getHeight());
        Path coverOut = OUT_DIR.resolve("civilian-spaceport-district-cover.png");
        ImageIO.write(cover, "PNG", coverOut.toFile());
        System.out.println("  wrote " + coverOut.toAbsolutePath());
    }

    /** Final setup preview: three marine berths reserved, surplus civilian craft parked. */
    @Test
    void renderOccupiedCivilianSpaceport() throws Exception {
        Files.createDirectories(OUT_DIR);
        TargetProfile profile = new TargetProfile(5, 6, 1, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.SPACEPORT),
                SurfacePalette.ROCK);
        List<ShuttleAssignment> manifest = List.of(
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));
        BattleSimulation sim = BattleSetup.createPlaceholder(42L, manifest,
                false, RiskLevel.LOW, MissionType.ASSAULT, profile);
        MapResult finalMap = new MapResult(sim.getGrid(), sim.getTopology(),
                0, 0, sim.getGrid().getWidth() - 1, sim.getGrid().getHeight() - 1,
                List.of(), sim.getDoodads());
        BufferedImage img = renderMapSprites(finalMap, 42L, false);
        drawParkedAircraft(img, sim.getParkedAircraft(), sim.getGrid().getHeight());

        Path out = OUT_DIR.resolve("civilian-spaceport-occupied.png");
        ImageIO.write(img, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());

        BufferedImage cover = copyOf(img);
        drawDoodadCoverOverlay(cover, finalMap.doodads, finalMap.grid.getHeight());
        Path coverOut = OUT_DIR.resolve("civilian-spaceport-occupied-cover.png");
        ImageIO.write(cover, "PNG", coverOut.toFile());
        System.out.println("  wrote " + coverOut.toAbsolutePath());
    }

    private static void drawParkedAircraft(BufferedImage img,
                                           List<ParkedAircraft> aircraft,
                                           int gridH) throws Exception {
        String install = System.getProperty("starsectorDir");
        assertNotNull(install, "starsectorDir is required for vanilla hull previews");
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (ParkedAircraft parked : aircraft) {
            Path hullPath = Paths.get(install, "starsector-core")
                    .resolve(parked.type.spritePath);
            BufferedImage hull = ImageIO.read(Files.newInputStream(hullPath));
            float lengthCells = hull.getHeight() * AirScale.METERS_PER_PX;
            int drawH = Math.max(CELL_PX, Math.round(lengthCells * CELL_PX));
            int drawW = Math.max(CELL_PX,
                    Math.round(drawH * ((float) hull.getWidth() / hull.getHeight())));
            int cx = Math.round((parked.centerX + 0.5f) * CELL_PX);
            int cy = Math.round((gridH - parked.centerY - 0.5f) * CELL_PX);

            AffineTransform before = g.getTransform();
            g.translate(cx, cy);
            g.rotate(Math.toRadians(-parked.facingDegrees));
            g.drawImage(hull, -drawW / 2, -drawH / 2, drawW, drawH, null);
            g.setTransform(before);

            g.setColor(new Color(0, 0, 0, 190));
            g.fillRoundRect(cx - 31, cy + drawH / 2 + 3, 62, 17, 5, 5);
            g.setColor(new Color(220, 235, 240));
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.drawString(parked.type.name(), cx - 27, cy + drawH / 2 + 15);
        }
        g.dispose();
    }

    private static void gMarkLandingPads(BufferedImage img, MapResult map) {
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(80, 220, 255, 210));
        for (com.dillon.starsectormarines.battle.world.gen.LandingPad pad : map.landingPads) {
            int x = pad.left() * CELL_PX;
            int y = (map.grid.getHeight() - 1 - pad.top()) * CELL_PX;
            int w = (pad.right() - pad.left() + 1) * CELL_PX;
            int h = (pad.top() - pad.bottom() + 1) * CELL_PX;
            g.drawRect(x, y, w - 1, h - 1);
        }
        g.dispose();
    }

    private BufferedImage renderMapSprites(MapResult map, long seed) {
        return renderMapSprites(map, seed, true);
    }

    private BufferedImage renderMapSprites(MapResult map, long seed,
                                            boolean drawSpawnMarkers) {
        int gridWidth = map.grid.getWidth();
        int gridHeight = map.grid.getHeight();
        BufferedImage scene = battleMaps.render(map, seed, CELL_PX);
        BufferedImage image = new BufferedImage(scene.getWidth(), scene.getHeight() + 24,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.drawImage(scene, 0, 0, null);
        if (drawSpawnMarkers) {
            graphics.setColor(MARINE_FG);
            drawDiamond(graphics, map.marineSpawnX, map.marineSpawnY, gridHeight);
            graphics.setColor(DEFENDER_FG);
            drawDiamond(graphics, map.defenderSpawnX, map.defenderSpawnY, gridHeight);
        }
        graphics.setColor(LABEL_BG);
        graphics.fillRect(0, scene.getHeight(), scene.getWidth(), 24);
        graphics.setColor(LABEL_FG);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 14));
        long mediumCover = map.doodads.stream()
                .filter(doodad -> doodad.cover == Doodad.COVER_MED).count();
        long heavyCover = map.doodads.stream()
                .filter(doodad -> doodad.cover == Doodad.COVER_HEAVY).count();
        graphics.drawString(String.format(
                "BSP battle render — seed=%d  %dx%d  doodads=%d  med=%d  heavy=%d",
                seed, gridWidth, gridHeight, map.doodads.size(), mediumCover, heavyCover),
                8, scene.getHeight() + 17);
        graphics.dispose();
        return image;
    }

    private static void drawDiamond(Graphics2D graphics, int gridX, int gridY,
                                    int gridHeight) {
        int radius = CELL_PX / 2;
        int centerX = gridX * CELL_PX + CELL_PX / 2;
        int centerY = (gridHeight - 1 - gridY) * CELL_PX + CELL_PX / 2;
        int[] xPoints = {centerX, centerX + radius, centerX, centerX - radius};
        int[] yPoints = {centerY - radius, centerY, centerY + radius, centerY};
        graphics.fillPolygon(xPoints, yPoints, 4);
    }

    private static BufferedImage copyOf(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return copy;
    }

    /** Yellow = medium cover, magenta = heavy cover. The clean preview remains unmarked. */
    private static void drawDoodadCoverOverlay(BufferedImage img, List<Doodad> doodads, int gridH) {
        Graphics2D g = img.createGraphics();
        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        for (Doodad d : doodads) {
            if (d.cover < Doodad.COVER_MED) continue;
            g.setColor(d.cover == Doodad.COVER_HEAVY ? HEAVY_COVER_FG : MED_COVER_FG);
            int x = d.cellX * CELL_PX + 1;
            int y = (gridH - d.cellY - d.footprintCellsY) * CELL_PX + 1;
            int width = d.footprintCellsX * CELL_PX - 3;
            int height = d.footprintCellsY * CELL_PX - 3;
            g.drawRect(x, y, width, height);
            g.drawString(d.cover == Doodad.COVER_HEAVY ? "H" : "M", x + 3, y + 10);
        }
        g.setColor(new Color(0, 0, 0, 215));
        g.fillRoundRect(8, 8, 248, 22, 6, 6);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(MED_COVER_FG);
        g.drawString("M medium", 17, 23);
        g.setColor(HEAVY_COVER_FG);
        g.drawString("H heavy doodad cover", 99, 23);
        g.dispose();
    }

}
