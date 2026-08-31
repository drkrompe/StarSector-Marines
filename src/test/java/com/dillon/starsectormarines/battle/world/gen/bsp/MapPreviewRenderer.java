package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shared debug-render helpers for BSP/grown-road map-generation previews.
 * Moved out of {@link BspMapPreviewTest} so other preview tests (e.g. the
 * grown-road-graph comparison) can reuse the same readable colour scheme
 * and overlay drawing without duplicating it.
 *
 * <p>Color scheme is per-{@link GroundKind} (not the actual sprite art —
 * that needs full atlas blits) plus a small overlay set:
 * <ul>
 *   <li>walls = black</li>
 *   <li>vehicles = dark blue</li>
 *   <li>doorways = light gold dot</li>
 *   <li>doodads = white pixel</li>
 *   <li>POIs = magenta rect outline</li>
 *   <li>marine spawn = green diamond, defender spawn = red diamond</li>
 * </ul>
 */
final class MapPreviewRenderer {

    private MapPreviewRenderer() {}

    /** Color per GroundKind — picked to be visually distinct on screen. */
    private static final Map<GroundKind, Color> GROUND_COLORS = new EnumMap<>(GroundKind.class);
    static {
        GROUND_COLORS.put(GroundKind.INDOOR,    new Color(230, 215, 180));
        GROUND_COLORS.put(GroundKind.STREET,    new Color(110, 110, 110));
        GROUND_COLORS.put(GroundKind.COURTYARD, new Color( 60,  70,  95));
        GROUND_COLORS.put(GroundKind.GRASS,     new Color( 90, 150,  70));
        GROUND_COLORS.put(GroundKind.DIRT,      new Color(130, 100,  70));
        GROUND_COLORS.put(GroundKind.STONE,     new Color(170, 170, 170));
        GROUND_COLORS.put(GroundKind.SAND,      new Color(220, 200, 145));
        GROUND_COLORS.put(GroundKind.SNOW,      new Color(240, 240, 240));
        GROUND_COLORS.put(GroundKind.WATER,     new Color( 70, 110, 170));
        GROUND_COLORS.put(GroundKind.TILE,      new Color(190, 190, 210));  // commercial polished panel (fl-2)
        GROUND_COLORS.put(GroundKind.BRICK,     new Color(200, 180, 155));  // brick paving — plazas, future roofs (was SIDEWALK's color/role)
        GROUND_COLORS.put(GroundKind.SIDEWALK,  new Color(170, 160, 150));  // curb-side sidewalk strip (urban-tileset-3)
        GROUND_COLORS.put(GroundKind.STRIPED,   new Color(200, 190, 100));
        GROUND_COLORS.put(GroundKind.LZ_MARKER, new Color(255, 220,  50));
        GROUND_COLORS.put(GroundKind.RUBBLE,    new Color(120,  90,  80));
    }

    /** District-theme accent colors used in the boundary overlay. Picked to read as colored highlights against any ground. */
    private static final Map<MapDistrictTheme, Color> DISTRICT_COLORS = new EnumMap<>(MapDistrictTheme.class);
    static {
        DISTRICT_COLORS.put(MapDistrictTheme.RESIDENTIAL,   new Color(150, 220, 150));
        DISTRICT_COLORS.put(MapDistrictTheme.INDUSTRIAL,    new Color(220, 150,  80));
        DISTRICT_COLORS.put(MapDistrictTheme.CIVIC,         new Color(140, 200, 255));
        DISTRICT_COLORS.put(MapDistrictTheme.MIXED,         new Color(200, 200, 220));
        DISTRICT_COLORS.put(MapDistrictTheme.WATERFRONT,    new Color(120, 180, 255));
        DISTRICT_COLORS.put(MapDistrictTheme.OUTSKIRTS,     new Color(180, 160, 130));
        DISTRICT_COLORS.put(MapDistrictTheme.COASTAL_BEACH, new Color(230, 210, 110));
        DISTRICT_COLORS.put(MapDistrictTheme.HARBOR_PORT,   new Color(140, 180, 220));
        DISTRICT_COLORS.put(MapDistrictTheme.MILITARY_FORT, new Color(220, 110, 110));
    }

    /** Biome accent colors for the conquest-mode overlay. Picked to read as colored band labels against any ground. */
    private static final Map<BiomeKind, Color> BIOME_COLORS = new EnumMap<>(BiomeKind.class);
    static {
        BIOME_COLORS.put(BiomeKind.BEACH,             new Color(245, 225, 130, 220));
        BIOME_COLORS.put(BiomeKind.PORT,              new Color(120, 180, 220, 220));
        BIOME_COLORS.put(BiomeKind.CITY,              new Color(200, 200, 220, 220));
        BIOME_COLORS.put(BiomeKind.FORTRESS_DISTRICT, new Color(230, 100, 100, 230));
        BIOME_COLORS.put(BiomeKind.OUTSKIRTS,         new Color(180, 160, 130, 220));
    }

    /** Per-tactical-kind dot colors. Distinct from biome/district colors to read as a separate overlay layer. */
    private static final Map<TacticalNode.Kind, Color> TACTICAL_COLORS = new EnumMap<>(TacticalNode.Kind.class);
    static {
        TACTICAL_COLORS.put(TacticalNode.Kind.HEAVY_TOWER,        new Color(255, 100, 100));
        TACTICAL_COLORS.put(TacticalNode.Kind.MG_NEST,            new Color(255, 180,  80));
        TACTICAL_COLORS.put(TacticalNode.Kind.FORWARD_BUNKER,     new Color(220, 140, 220));
        TACTICAL_COLORS.put(TacticalNode.Kind.GATE,               new Color( 80, 220, 255));
        TACTICAL_COLORS.put(TacticalNode.Kind.COMMAND_POST,       new Color(255, 255, 100));
        TACTICAL_COLORS.put(TacticalNode.Kind.BARRACKS,           new Color(140, 255, 140));
        TACTICAL_COLORS.put(TacticalNode.Kind.ARMORY,             new Color(255, 200, 100));
        TACTICAL_COLORS.put(TacticalNode.Kind.GUARDPOST,          new Color(255, 140,  80));
        TACTICAL_COLORS.put(TacticalNode.Kind.INNER_POSITION,     new Color(200, 140, 255));
        TACTICAL_COLORS.put(TacticalNode.Kind.AIRBASE,            new Color(180, 220, 255));
        TACTICAL_COLORS.put(TacticalNode.Kind.BEACHHEAD,          new Color(100, 220, 100));
        TACTICAL_COLORS.put(TacticalNode.Kind.INFILTRATION_POINT, new Color(180, 100, 255));
        TACTICAL_COLORS.put(TacticalNode.Kind.OBJECTIVE,          new Color(255, 100, 255));
    }

    /** Per-link-kind line colors. Translucent so overlapping links still read separately. */
    private static final Map<TacticalNode.LinkKind, Color> LINK_COLORS = new EnumMap<>(TacticalNode.LinkKind.class);
    static {
        LINK_COLORS.put(TacticalNode.LinkKind.OVERWATCHES, new Color(255, 100, 100, 160));
        LINK_COLORS.put(TacticalNode.LinkKind.SUPPLIES,    new Color(140, 255, 140, 160));
        LINK_COLORS.put(TacticalNode.LinkKind.FALLBACK_TO, new Color(255, 200, 100, 160));
        LINK_COLORS.put(TacticalNode.LinkKind.GUARDS,      new Color( 80, 220, 255, 160));
    }

    /**
     * Renders one {@link MapResult} to a labelled debug image. {@code caption}
     * replaces the old fixed {@code "seed=%d ..."} label text — pass the same
     * formatted string existing callers used to keep their output identical.
     */
    static BufferedImage renderMap(MapResult map, String caption, DistrictMap districts,
                                    BiomeMap biomes, List<Compound> compounds,
                                    TacticalMap tactical, int cellPx) {
        NavigationGrid grid = map.grid;
        CellTopology topo = map.topology;
        int w = grid.getWidth(), h = grid.getHeight();
        int imgW = w * cellPx;
        int imgH = h * cellPx + 24; // bottom strip for seed label + counts

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Pass 1 — ground fill per cell.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = GROUND_COLORS.getOrDefault(topo.getGroundKind(x, y), Color.MAGENTA);
                g.setColor(c);
                g.fillRect(x * cellPx, (h - 1 - y) * cellPx, cellPx, cellPx);
            }
        }

        // Pass 2 — walls (overlay black).
        g.setColor(Color.BLACK);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (topo.isWall(x, y)) {
                    g.fillRect(x * cellPx, (h - 1 - y) * cellPx, cellPx, cellPx);
                }
            }
        }

        // Pass 3 — vehicles (dark blue with border).
        g.setColor(new Color(30, 40, 80));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (topo.isVehicle(x, y)) {
                    g.fillRect(x * cellPx, (h - 1 - y) * cellPx, cellPx, cellPx);
                }
            }
        }

        // Pass 3b — room-purpose labels (semi-transparent overlay on keep interiors).
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                RoomPurpose rp = topo.getRoomPurpose(x, y);
                if (rp == null) continue;
                Color rc;
                switch (rp) {
                    case KEEP_THRONE: rc = new Color(60, 90, 180); break;
                    case KEEP_INNER:  rc = new Color(200, 180, 60); break;
                    case KEEP_ENTRY:  rc = new Color(180, 60, 60); break;
                    case CORRIDOR:    rc = new Color(80, 200, 200); break;
                    default: continue;
                }
                g.setColor(rc);
                g.fillRect(x * cellPx, (h - 1 - y) * cellPx, cellPx, cellPx);
            }
        }
        g.setComposite(AlphaComposite.SrcOver);

        // Pass 4 — doorways (light gold dot).
        g.setColor(new Color(255, 220, 100));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (grid.isDoorway(x, y)) {
                    int cx = x * cellPx + cellPx / 2;
                    int cy = (h - 1 - y) * cellPx + cellPx / 2;
                    g.fillOval(cx - 2, cy - 2, 4, 4);
                }
            }
        }

        // Pass 5 — doodads (white pixels).
        g.setColor(Color.WHITE);
        for (Doodad d : map.doodads) {
            int x = d.cellX * cellPx;
            int y = (h - d.cellY - d.footprintCellsY) * cellPx;
            g.fillRect(x, y,
                    Math.max(2, d.footprintCellsX * cellPx),
                    Math.max(2, d.footprintCellsY * cellPx));
        }

        // Pass 6 — POI outlines (magenta).
        g.setColor(new Color(220, 80, 220));
        g.setStroke(new BasicStroke(1f));
        for (PointOfInterest p : map.pointsOfInterest) {
            int x0 = p.left * cellPx;
            int y0 = (h - 1 - p.bottom) * cellPx;
            int width  = (p.right  - p.left + 1) * cellPx;
            int height = (p.bottom - p.top  + 1) * cellPx;
            g.drawRect(x0, y0, width - 1, height - 1);
        }

        // Pass 7 — zoning overlay. Conquest mode renders BiomeMap bands;
        // legacy mode renders the DistrictMap grid. Drawn over the map but
        // under spawn markers so the partition shape stays readable.
        if (biomes != null) {
            drawBiomeOverlay(g, biomes, h, cellPx);
        } else if (districts != null) {
            drawDistrictOverlay(g, districts, h, imgW, cellPx);
        }

        // Pass 7b — compound bounding rects. A bright magenta outline traces
        // each compound's bounding box so the multi-leaf claim is visible at
        // a glance even when the wall ring blends with adjacent building art.
        if (compounds != null && !compounds.isEmpty()) {
            drawCompoundOverlay(g, compounds, h, cellPx);
        }

        // Pass 7c — tactical graph. Lines for OVERWATCHES/SUPPLIES/etc, then
        // dots for the nodes themselves so dots sit on top of lines. Drawn
        // before spawn markers so the spawn diamonds remain the most
        // prominent overlay.
        if (tactical != null && tactical.size() > 0) {
            drawTacticalOverlay(g, tactical, h, cellPx);
        }

        // Pass 7d — road graph. Edge cell-list polylines in cyan, junction
        // nodes as small dots, perimeter nodes (off-map convoy entry
        // candidates) in red. Drawn after tactical so road centerlines win
        // when both overlays cross — convoys are the next layer of work
        // and seeing the graph clearly is the point of the overlay.
        if (map.roadGraph != null && !map.roadGraph.nodes().isEmpty()) {
            drawRoadGraphOverlay(g, map.roadGraph, h, cellPx);
        }

        // Pass 8 — spawn anchors.
        drawDiamond(g, new Color(80, 230, 110), map.marineSpawnX,   map.marineSpawnY,   h, cellPx);
        drawDiamond(g, new Color(240, 80,  80), map.defenderSpawnX, map.defenderSpawnY, h, cellPx);

        // Pass 9 — bottom label strip.
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, h * cellPx, imgW, 24);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString(caption, 6, h * cellPx + 16);

        g.dispose();
        return img;
    }

    /**
     * Paint a thick translucent border around every district plus a small
     * theme glyph in the corner. Boundary color = theme accent so a coherent
     * cluster reads as one large bordered region.
     */
    private static void drawDistrictOverlay(Graphics2D g, DistrictMap districts,
                                             int gridH, int imgW, int cellPx) {
        int cellW = districts.districtCellWidth();
        int cellH = districts.districtCellHeight();
        g.setStroke(new BasicStroke(2f));
        for (int dx = 0; dx < districts.districtsX(); dx++) {
            for (int dy = 0; dy < districts.districtsY(); dy++) {
                MapDistrictTheme theme = districts.themeAtDistrict(dx, dy);
                Color base = DISTRICT_COLORS.getOrDefault(theme, Color.WHITE);
                Color line = new Color(base.getRed(), base.getGreen(), base.getBlue(), 200);

                int x0 = dx * cellW * cellPx;
                // Image-y is grid-y flipped — district at grid-y=0 lives at image-bottom.
                int y0 = (gridH - (dy + 1) * cellH) * cellPx;
                int w  = cellW * cellPx;
                int hPx = cellH * cellPx;

                g.setColor(line);
                g.drawRect(x0, y0, w - 1, hPx - 1);

                // Theme glyph: small filled square + first 3 letters of theme name.
                g.fillRect(x0 + 4, y0 + 4, 10, 10);
                g.setColor(Color.BLACK);
                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                g.drawString(theme.name().substring(0, Math.min(3, theme.name().length())),
                        x0 + 4, y0 + 22);
            }
        }
    }

    /**
     * Conquest-mode biome overlay. Draws a thin contrasting outline along the
     * boundary between biome regions and a translucent biome-color label band
     * at each biome's centroid so the four-stage progression
     * (beach → port → city → fortress) reads at a glance.
     *
     * <p>Only paints the per-cell boundary line — the underlying ground colors
     * remain visible, so individual leaf fills (SAND for beach, INDOOR for
     * buildings, etc.) still drive the visual.
     */
    private static void drawBiomeOverlay(Graphics2D g, BiomeMap biomes, int gridH, int cellPx) {
        int w = biomes.width();
        int h = biomes.height();

        // Per-cell boundary line: cells whose biome differs from the
        // neighbor on the perpendicular-to-axis side get a 1-pixel outline.
        g.setStroke(new BasicStroke(1.5f));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                BiomeKind here = biomes.biomeAt(x, y);
                BiomeKind below = (y > 0)     ? biomes.biomeAt(x, y - 1) : here;
                BiomeKind left  = (x > 0)     ? biomes.biomeAt(x - 1, y) : here;
                int sx = x * cellPx;
                int sy = (gridH - 1 - y) * cellPx;
                if (here != below) {
                    Color base = BIOME_COLORS.getOrDefault(here, Color.WHITE);
                    g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 230));
                    g.drawLine(sx, sy + cellPx, sx + cellPx, sy + cellPx);
                }
                if (here != left) {
                    Color base = BIOME_COLORS.getOrDefault(here, Color.WHITE);
                    g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 230));
                    g.drawLine(sx, sy, sx, sy + cellPx);
                }
            }
        }

        // Biome label at the centroid of each biome's footprint. Centroid
        // computed in a single linear pass over the grid.
        Map<BiomeKind, long[]> sums = new EnumMap<>(BiomeKind.class);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                BiomeKind b = biomes.biomeAt(x, y);
                long[] s = sums.computeIfAbsent(b, k -> new long[3]);
                s[0] += x;
                s[1] += y;
                s[2] += 1;
            }
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        for (Map.Entry<BiomeKind, long[]> e : sums.entrySet()) {
            long[] s = e.getValue();
            if (s[2] == 0) continue;
            int cx = (int) (s[0] / s[2]) * cellPx;
            int cy = (gridH - 1 - (int) (s[1] / s[2])) * cellPx;
            Color base = BIOME_COLORS.getOrDefault(e.getKey(), Color.WHITE);
            g.setColor(new Color(0, 0, 0, 180));
            String name = e.getKey().name();
            g.drawString(name, cx + 1, cy + 1);
            g.setColor(base);
            g.drawString(name, cx, cy);
        }
    }

    /**
     * Draw a colored outline around each compound's union bounding rect plus
     * a kind glyph in the top-left corner of the rect. Reads as "this cluster
     * of leaves was claimed as one compound" without interfering with the
     * underlying ground / wall / doorway art.
     */
    private static void drawCompoundOverlay(Graphics2D g, List<Compound> compounds, int gridH, int cellPx) {
        g.setStroke(new BasicStroke(2f));
        for (Compound c : compounds) {
            int x0 = c.left * cellPx - 1;
            int y0 = (gridH - 1 - c.bottom) * cellPx - 1;
            int wPx = c.width()  * cellPx + 2;
            int hPx = c.height() * cellPx + 2;
            Color outline = new Color(255, 80, 200, 230);
            g.setColor(outline);
            g.drawRect(x0, y0, wPx, hPx);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.setColor(new Color(255, 255, 255, 240));
            g.drawString(glyphFor(c.kind), x0 + 4, y0 + 12);
        }
    }

    private static String glyphFor(BlockKind kind) {
        switch (kind) {
            case MILITARY_BASE: return "MIL";
            case GATED_HOUSING: return "GH";
            case DENSE_QUARTER: return "DQ";
            case INDUSTRIAL_COMPOUND: return "IC";
            case MEDICAL_CAMPUS: return "MED";
            default:            return kind.name().substring(0, Math.min(3, kind.name().length()));
        }
    }

    /**
     * Renders the tactical graph as colored link lines + per-kind colored
     * dots over the map. Link lines use {@link #LINK_COLORS} (semi-transparent
     * so overlapping links still read separately); dots use
     * {@link #TACTICAL_COLORS} with a black outline so the dot reads against
     * any underlying ground.
     */
    private static void drawTacticalOverlay(Graphics2D g, TacticalMap tactical, int gridH, int cellPx) {
        // Lines first, dots on top.
        g.setStroke(new BasicStroke(1.5f));
        for (TacticalNode n : tactical.all()) {
            int x0 = n.anchorX * cellPx + cellPx / 2;
            int y0 = (gridH - 1 - n.anchorY) * cellPx + cellPx / 2;
            for (TacticalNode.Link l : n.links()) {
                Color c = LINK_COLORS.getOrDefault(l.kind, new Color(200, 200, 200, 160));
                g.setColor(c);
                int x1 = l.target.anchorX * cellPx + cellPx / 2;
                int y1 = (gridH - 1 - l.target.anchorY) * cellPx + cellPx / 2;
                g.drawLine(x0, y0, x1, y1);
            }
        }

        // Dots — size grows with priority so HOLD/COMMAND read larger than MG.
        for (TacticalNode n : tactical.all()) {
            int cx = n.anchorX * cellPx + cellPx / 2;
            int cy = (gridH - 1 - n.anchorY) * cellPx + cellPx / 2;
            int r = Math.max(3, cellPx / 2 + (n.priorityScore >= 80 ? 2 : 0));
            Color base = TACTICAL_COLORS.getOrDefault(n.kind, Color.WHITE);
            g.setColor(base);
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1f));
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
        }
    }

    /**
     * Renders the road graph as cyan edge polylines + per-degree colored
     * dots over the map. Edge cell-lists are drawn through cell centers as
     * 1-pixel polylines; nodes overlay them as small filled circles with a
     * black outline.
     *
     * <p>Color rules:
     * <ul>
     *   <li>Edge polyline: cyan, 60% alpha.</li>
     *   <li>Interior node (degree ≥ 3): orange dot.</li>
     *   <li>Dead-end node (degree 1): yellow dot.</li>
     *   <li>Perimeter node (any degree, on map edge): red dot — convoy
     *       entry/exit candidate.</li>
     * </ul>
     */
    private static void drawRoadGraphOverlay(Graphics2D g, RoadGraph graph, int gridH, int cellPx) {
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(120, 220, 240, 160));
        for (RoadGraph.Edge e : graph.edges()) {
            int n = e.length();
            int[] xs = new int[n];
            int[] ys = new int[n];
            for (int i = 0; i < n; i++) {
                xs[i] = e.cellsX[i] * cellPx + cellPx / 2;
                ys[i] = (gridH - 1 - e.cellsY[i]) * cellPx + cellPx / 2;
            }
            g.drawPolyline(xs, ys, n);
        }

        int r = Math.max(2, cellPx / 2);
        for (RoadGraph.Node node : graph.nodes()) {
            int cx = node.cellX * cellPx + cellPx / 2;
            int cy = (gridH - 1 - node.cellY) * cellPx + cellPx / 2;
            Color fill;
            if (node.perimeter) {
                fill = new Color(255, 100, 100);
            } else if (node.degree() <= 1) {
                fill = new Color(255, 235, 120);
            } else {
                fill = new Color(255, 170, 80);
            }
            g.setColor(fill);
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1f));
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
        }
    }

    static void drawDiamond(Graphics2D g, Color c, int cellX, int cellY, int gridH, int cellPx) {
        g.setColor(c);
        int cx = cellX * cellPx + cellPx / 2;
        int cy = (gridH - 1 - cellY) * cellPx + cellPx / 2;
        int r = cellPx;
        int[] xs = { cx, cx + r, cx, cx - r };
        int[] ys = { cy - r, cy, cy + r, cy };
        g.fillPolygon(xs, ys, 4);
        g.setColor(Color.BLACK);
        g.drawPolygon(xs, ys, 4);
    }

    static BufferedImage composeContactSheet(BufferedImage[] tiles, int cols) {
        int rows = (tiles.length + cols - 1) / cols;
        int tileW = tiles[0].getWidth();
        int tileH = tiles[0].getHeight();
        int gap = 8;
        BufferedImage sheet = new BufferedImage(
                cols * tileW + (cols + 1) * gap,
                rows * tileH + (rows + 1) * gap,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(20, 25, 32));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < tiles.length; i++) {
            int cx = i % cols;
            int cy = i / cols;
            int x = gap + cx * (tileW + gap);
            int y = gap + cy * (tileH + gap);
            g.drawImage(tiles[i], x, y, null);
        }
        g.dispose();
        return sheet;
    }
}
