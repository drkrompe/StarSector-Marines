package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The directional sun, on a real city.
 *
 * <p>The unit tests pin the march's arithmetic and the pixel oracle shows it on
 * a synthetic scene with one square building. Neither answers the question this
 * suite exists for: whether the shadows read as depth on ground the generator
 * actually produces — scattered walls, rubble, water, buildings at every
 * orientation and spacing. A shadow that looks like architecture on a diagram
 * can look like dirt on a map.
 *
 * <p>Every panel is <b>the same map at the same seed</b>, rendered once and
 * shaded differently, so the only thing that varies between them is the sun.
 * The first panel of each sheet is the map with no sun at all, because "the
 * shadows look right" means nothing without the picture they are being
 * compared against.
 *
 * <p><b>This shades through {@link GroundSunShadowReference}, a CPU model of
 * the composite's shader — not the shader.</b> It is evidence about the
 * geometry and the look, and is not verification that the GLSL compiles or
 * binds. See that class.
 */
public final class SunShadowSnapshotSuite implements SnapshotSuite {

    /** Production Conquest dimensions, so the city has the density and the road grid it really has. */
    private static final int MAP_W = 240;
    private static final int MAP_H = 160;
    private static final long SEED = 7L;
    private static final TraversalAxis AXIS = TraversalAxis.SOUTH_TO_NORTH;

    /**
     * Big enough that a 3 m wall's shadow is several pixels and reads as a
     * band rather than a fringe, small enough that a four-panel sheet of the
     * crop below stays a file somebody will actually open.
     */
    private static final int CELL_PX = 12;

    /** Cells of map in one panel. A shadow is a few cells long, so the window has to be tight to show one. */
    private static final int WINDOW_W = 84;
    private static final int WINDOW_H = 56;

    /** The breach panels frame one building instead of a block of city, so they zoom in and cover less. */
    private static final int BREACH_CELL_PX = 24;
    private static final int BREACH_VIEW_W = 40;
    private static final int BREACH_VIEW_H = 28;
    /** Roughly what a detonation takes out, and comfortably inside the rim's reach at the default sun. */
    private static final int BREACH_RADIUS_CELLS = 3;

    @Override
    public String id() {
        return "sun-shadows";
    }

    @Override
    public String label() {
        return "Sun shadows over a generated city";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());

        MapResult map = new BspCityGenerator().generate(MAP_W, MAP_H, SEED, AXIS);
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        BufferedImage whole = renderer.render(map, SEED, CELL_PX);
        Window window = densestBuiltWindow(map.topology);
        BufferedImage ground = crop(whole, window);

        GenMappingRegistry mapping = GenMappingRegistry.installed();
        MacroReliefField relief = new MacroReliefField(map.topology, map.grid, map.buildings, mapping);
        float tallest = relief.tallestMeters();
        GroundSunShadowReference.HeightField field = relief::metersAt;
        System.out.println("[sun-shadows] " + relief);
        GroundSunShadowReference.PixelToWorld worldAt = pixelToWorld(window);

        float azimuth = SunLight.DEFAULT_AZIMUTH_DEGREES;
        float strength = SunLight.DEFAULT_SHADOW_STRENGTH;

        List<Panel> ladder = new ArrayList<>();
        ladder.add(new Panel(ground, "no sun (control)"));
        for (float elevation : new float[]{20f, SunLight.DEFAULT_ELEVATION_DEGREES, 65f}) {
            ladder.add(new Panel(
                    GroundSunShadowReference.shade(ground, field, worldAt,
                            azimuth, elevation, strength, tallest),
                    String.format(Locale.ROOT, "%.0f deg elevation | reach %.1f cells",
                            elevation, reachCells(tallest, elevation))));
        }

        List<Panel> bearings = new ArrayList<>();
        bearings.add(new Panel(ground, "no sun (control)"));
        for (float bearing : new float[]{45f, 135f, 315f}) {
            bearings.add(new Panel(
                    GroundSunShadowReference.shade(ground, field, worldAt,
                            bearing, SunLight.DEFAULT_ELEVATION_DEGREES,
                            strength, tallest),
                    String.format(Locale.ROOT, "%.0f deg bearing", bearing)));
        }

        return List.of(
                new SnapshotArtifact("roof-breach.png", sheet(breachPanels(map, renderer),
                        "A roof is what a building casts with. Same map, same "
                                + (int) azimuth + " deg / "
                                + (int) SunLight.DEFAULT_ELEVATION_DEGREES
                                + " deg sun, seed " + SEED + ".")),
                new SnapshotArtifact("elevation-ladder.png", sheet(ladder,
                        "Sun elevation sets reach. Same map, same bearing "
                                + (int) azimuth + " deg, seed " + SEED + ".")),
                new SnapshotArtifact("bearing.png", sheet(bearings,
                        "Bearing sets direction. Same map, same "
                                + (int) SunLight.DEFAULT_ELEVATION_DEGREES
                                + " deg elevation, seed " + SEED + ".")));
    }

    /**
     * The building the sun is actually for: intact, and with a hole in its roof.
     *
     * <p>Roof destruction already happens in play — a detonation cracks a roof
     * and an adjacent wall collapse takes the cells beside it — and until now it
     * changed only what the roof pass drew. It is the clearest case the height
     * model has: a solid block, then daylight through the hole and the intact
     * roof's own shadow lying across it.
     */
    private List<Panel> breachPanels(MapResult map, HeadlessBattleMapRenderer renderer) {
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        Building target = largestBuildingIn(map, densestBuiltWindow(map.topology));
        if (target == null) return List.of();

        // Framed on the one building at twice the city's zoom. At twelve pixels
        // a cell a hole a few cells across is a smudge; the whole point is the
        // rim's own shadow lying inside it, and that has to be looked at.
        float centerX = (target.minX + target.maxX + 1) * 0.5f;
        float centerY = (target.minY + target.maxY + 1) * 0.5f;
        BufferedImage ground = renderer.renderView(map, SEED, centerX, centerY,
                BREACH_VIEW_W, BREACH_VIEW_H, BREACH_CELL_PX);
        GroundSunShadowReference.PixelToWorld worldAt = viewPixelToWorld(centerX, centerY);

        MacroReliefField intact = new MacroReliefField(map.topology, map.grid, map.buildings, mapping);
        int breached = breachRoof(map.topology, target);
        MacroReliefField holed = new MacroReliefField(map.topology, map.grid, map.buildings, mapping);

        List<Panel> panels = new ArrayList<>();
        panels.add(new Panel(shade(ground, intact, worldAt),
                "roof intact | the building casts as one solid block"));
        panels.add(new Panel(shade(ground, holed, worldAt),
                breached + " roof cells caved in | the rim casts into its own hole"));
        return panels;
    }

    /** Where a pixel of a {@code renderView} frame sits in world cells. */
    private static GroundSunShadowReference.PixelToWorld viewPixelToWorld(float centerCellX,
                                                                         float centerCellY) {
        return new GroundSunShadowReference.PixelToWorld() {
            @Override
            public float worldX(int pixelX) {
                return centerCellX + (pixelX + 0.5f - BREACH_VIEW_W * BREACH_CELL_PX * 0.5f)
                        / BREACH_CELL_PX;
            }

            @Override
            public float worldY(int pixelY) {
                return centerCellY - (pixelY + 0.5f - BREACH_VIEW_H * BREACH_CELL_PX * 0.5f)
                        / BREACH_CELL_PX;
            }
        };
    }

    private BufferedImage shade(BufferedImage ground, MacroReliefField relief,
                                GroundSunShadowReference.PixelToWorld worldAt) {
        return GroundSunShadowReference.shade(ground, relief::metersAt, worldAt,
                SunLight.DEFAULT_AZIMUTH_DEGREES,
                SunLight.DEFAULT_ELEVATION_DEGREES,
                SunLight.DEFAULT_SHADOW_STRENGTH, relief.tallestMeters());
    }

    /** The biggest roof in frame, so the hole is large enough to read at this zoom. */
    private static Building largestBuildingIn(MapResult map, Window window) {
        Building best = null;
        for (Building building : map.buildings.all()) {
            if (building.maxX < window.minX || building.minX >= window.minX + WINDOW_W) continue;
            if (building.maxY < window.minY || building.minY >= window.maxY() + 1) continue;
            if (best == null || building.cellCount() > best.cellCount()) best = building;
        }
        return best;
    }

    /**
     * Cave in a patch in the middle of a roof, the size a detonation actually
     * takes out. Not half the building: a hole wider than the rim's own reach
     * is mostly lit floor, which shows that a lower cell is lighter and not
     * that the roof around it is still standing between that floor and the sun.
     */
    private static int breachRoof(CellTopology topology, Building building) {
        int centerX = (building.minX + building.maxX) / 2;
        int centerY = (building.minY + building.maxY) / 2;
        int breached = 0;
        for (int i = 0, n = building.cellCount(); i < n; i++) {
            int dx = building.cellsX[i] - centerX;
            int dy = building.cellsY[i] - centerY;
            if (dx * dx + dy * dy > BREACH_RADIUS_CELLS * BREACH_RADIUS_CELLS) continue;
            topology.setRoofDestroyed(building.cellsX[i], building.cellsY[i], true);
            breached++;
        }
        return breached;
    }

    /** What a wall of the tallest authored height lays down, in cells, at this elevation. */
    private static float reachCells(float tallestMeters, float elevationDegrees) {
        return tallestMeters / (float) Math.tan(Math.toRadians(elevationDegrees));
    }

    /**
     * Where a cropped pixel sits in world cells.
     *
     * <p>The renderer draws cell {@code y=0} along the image's bottom edge, so
     * image rows run opposite to world rows. Getting this flip wrong mirrors
     * every shadow and still produces a plausible-looking picture, which is
     * exactly the kind of wrong that survives review.
     */
    private static GroundSunShadowReference.PixelToWorld pixelToWorld(Window window) {
        return new GroundSunShadowReference.PixelToWorld() {
            @Override
            public float worldX(int pixelX) {
                return window.minX + (pixelX + 0.5f) / CELL_PX;
            }

            @Override
            public float worldY(int pixelY) {
                return window.maxY() + 1f - (pixelY + 0.5f) / CELL_PX;
            }
        };
    }

    /**
     * The window with the most wall in it.
     *
     * <p>Picked rather than centred because a fixed crop of a generated city is
     * a coin flip between a dense block and an empty field, and an empty field
     * makes a shadow suite that shows nothing. Deterministic for a seed: a
     * summed-area scan, first window wins a tie.
     */
    private static Window densestBuiltWindow(CellTopology topology) {
        int w = topology.getWidth();
        int h = topology.getHeight();
        int[][] sum = new int[h + 1][w + 1];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                sum[y + 1][x + 1] = (topology.isWall(x, y) ? 1 : 0)
                        + sum[y][x + 1] + sum[y + 1][x] - sum[y][x];
            }
        }
        int bestX = 0;
        int bestY = 0;
        int best = -1;
        for (int y = 0; y + WINDOW_H <= h; y++) {
            for (int x = 0; x + WINDOW_W <= w; x++) {
                int walls = sum[y + WINDOW_H][x + WINDOW_W] - sum[y][x + WINDOW_W]
                        - sum[y + WINDOW_H][x] + sum[y][x];
                if (walls > best) {
                    best = walls;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return new Window(bestX, bestY);
    }

    private static BufferedImage crop(BufferedImage whole, Window window) {
        // Image rows run opposite to world rows, so the window's TOP row in
        // world space is its first row in the image.
        int px = window.minX * CELL_PX;
        int py = whole.getHeight() - (window.maxY() + 1) * CELL_PX;
        return whole.getSubimage(px, py, WINDOW_W * CELL_PX, WINDOW_H * CELL_PX);
    }

    private record Window(int minX, int minY) {
        int maxY() {
            return minY + WINDOW_H - 1;
        }
    }

    private record Panel(BufferedImage image, String caption) {}

    private static BufferedImage sheet(List<Panel> panels, String heading) {
        int panelW = panels.get(0).image().getWidth();
        int panelH = panels.get(0).image().getHeight();
        int columns = 2;
        int rows = (panels.size() + columns - 1) / columns;
        int captionH = 30;
        int headingH = 36;
        int gap = 8;

        BufferedImage sheet = new BufferedImage(
                columns * panelW + (columns + 1) * gap,
                headingH + rows * (panelH + captionH) + (rows + 1) * gap,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x10151D));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        g.setColor(new Color(0xD8E2F0));
        g.drawString(heading, gap, 24);

        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        for (int i = 0; i < panels.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            int x = gap + column * (panelW + gap);
            int y = headingH + gap + row * (panelH + captionH + gap);
            g.drawImage(panels.get(i).image(), x, y, null);
            g.setColor(new Color(0xD8E2F0));
            g.drawString(panels.get(i).caption(), x + 4, y + panelH + 20);
        }
        g.dispose();
        return sheet;
    }
}
