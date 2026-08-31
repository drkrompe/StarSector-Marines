package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
// Compound, DistrictMap, BiomeMap, StationGraph live in the bsp sub-package (same as this test).
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dev tool dressed as a test. Generates a small batch of maps from
 * {@link BspCityGenerator} and renders each {@link MapResult} to a PNG so a
 * human can eyeball the segmentation shape, the block-kind labels, and the
 * overlays (walls / doodads / POIs / spawn anchors) without launching the
 * game. Also asserts basic connectivity — every walkable cell must be
 * reachable from any other walkable cell via 4-neighbor traversal.
 *
 * <p>Output: {@code build/map-previews/seed-NNNN.png} (one per seed) plus
 * a 3×2 contact sheet at {@code build/map-previews/contact.png}. Run via:
 * <pre>
 *   gradlew :test --tests "*BspMapPreviewTest*"
 * </pre>
 * Re-run after editing fillers to see the visual delta.
 *
 * <p>The actual per-cell rendering (ground colors, wall/doorway/doodad/POI
 * overlays, district/biome/compound/tactical/road-graph overlays) lives in
 * {@link MapPreviewRenderer}, shared with other preview tests so the color
 * scheme and overlay logic aren't duplicated per test class.
 */
public class BspMapPreviewTest {

    private static final int GRID_W = 80;
    private static final int GRID_H = 80;
    private static final int CELL_PX = 8;
    private static final long[] SEEDS = { 1L, 42L, 100L, 777L, 1234L, 9999L };

    private static final int CONQUEST_W = 240;
    private static final int CONQUEST_H = 160;
    private static final int CONQUEST_CELL_PX = 5;
    private static final long[] CONQUEST_SEEDS = { 1L, 42L, 100L, 777L };

    private static final Path OUT_DIR = Paths.get("build/map-previews");

    /**
     * Conquest-mode preview. Generates 240×160 maps with a
     * {@link TraversalAxis#SOUTH_TO_NORTH} biome layout — beach at the south
     * edge, fortress district at the north edge, harbor and city in between.
     * The contact sheet shows the four-biome banding and where the marine
     * spawn lands (south beach) vs. the defender (north fortress).
     */
    @Test
    void renderConquestBatch() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();

        BufferedImage[] perSeed = new BufferedImage[CONQUEST_SEEDS.length];
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (int i = 0; i < CONQUEST_SEEDS.length; i++) {
            long seed = CONQUEST_SEEDS[i];
            MapResult map = gen.generate(CONQUEST_W, CONQUEST_H, seed, TraversalAxis.SOUTH_TO_NORTH);
            BufferedImage img = MapPreviewRenderer.renderMap(map, captionFor(map, seed), null, gen.getLastBiomeMap(),
                    gen.getLastCompounds(), gen.getLastTacticalMap(), CONQUEST_CELL_PX);
            perSeed[i] = img;
            Path out = OUT_DIR.resolve(String.format("conquest-seed-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
            try {
                assertConnected(map, seed);
            } catch (AssertionError ae) {
                failures.add(ae.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("\n", failures));
        }
        BufferedImage contact = MapPreviewRenderer.composeContactSheet(perSeed, 2);
        Path contactPath = OUT_DIR.resolve("conquest-contact.png");
        ImageIO.write(contact, "PNG", contactPath.toFile());
        System.out.println("  wrote " + contactPath.toAbsolutePath());
    }

    @Test
    void renderPreviewBatch() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();

        BufferedImage[] perSeed = new BufferedImage[SEEDS.length];
        List<String> failures = new java.util.ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            MapResult map = gen.generate(GRID_W, GRID_H, seed);
            BufferedImage img = MapPreviewRenderer.renderMap(map, captionFor(map, seed), gen.getLastDistrictMap(), null,
                    gen.getLastCompounds(), gen.getLastTacticalMap(), CELL_PX);
            perSeed[i] = img;
            Path out = OUT_DIR.resolve(String.format("seed-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
            try {
                assertConnected(map, seed);
            } catch (AssertionError ae) {
                failures.add(ae.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("\n", failures));
        }

        BufferedImage contact = MapPreviewRenderer.composeContactSheet(perSeed, 3);
        Path contactPath = OUT_DIR.resolve("contact.png");
        ImageIO.write(contact, "PNG", contactPath.toFile());
        System.out.println("  wrote " + contactPath.toAbsolutePath());
    }

    /**
     * Station-interior preview — the inverted (solid-default) rooms-and-corridors
     * map type. Hull renders black, carved rooms in beige ({@code INDOOR}),
     * corridors in striped-yellow ({@code STRIPED}) tinted cyan by the
     * {@code CORRIDOR} room-purpose overlay. Eyeball: rooms are discrete, every
     * room is reached through a corridor, no floating islands, a few loop
     * alternates visible. Connectivity is hard-asserted (same oracle as the city
     * batches).
     */
    @Test
    void renderStationBatch() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();

        BufferedImage[] perSeed = new BufferedImage[SEEDS.length];
        List<String> failures = new java.util.ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            MapResult map = gen.generateStation(GRID_W, GRID_H, seed);
            BufferedImage img = MapPreviewRenderer.renderMap(map, captionFor(map, seed), null, null,
                    gen.getLastCompounds(), gen.getLastTacticalMap(), CELL_PX);
            perSeed[i] = img;
            Path out = OUT_DIR.resolve(String.format("station-seed-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
            try {
                assertConnected(map, seed);
            } catch (AssertionError ae) {
                failures.add(ae.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("\n", failures));
        }

        BufferedImage contact = MapPreviewRenderer.composeContactSheet(perSeed, 3);
        Path contactPath = OUT_DIR.resolve("station-contact.png");
        ImageIO.write(contact, "PNG", contactPath.toFile());
        System.out.println("  wrote " + contactPath.toAbsolutePath());
    }

    /**
     * Concentric "onion" station preview — the defense-station layout: nested
     * defensive rings (beige room bands) around a central control core, breached
     * by gated ring walls. Eyeball: visible concentric rings, a central core, the
     * marine spawn (green) in the outer ring and the defender (red) in the core,
     * and the inward spiral of gates. Connectivity hard-asserted.
     */
    @Test
    void renderConcentricStationBatch() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();

        BufferedImage[] perSeed = new BufferedImage[SEEDS.length];
        List<String> failures = new java.util.ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            MapResult map = gen.generateConcentricStation(GRID_W, GRID_H, seed);
            BufferedImage img = MapPreviewRenderer.renderMap(map, captionFor(map, seed), null, null,
                    gen.getLastCompounds(), gen.getLastTacticalMap(), CELL_PX);
            perSeed[i] = img;
            Path out = OUT_DIR.resolve(String.format("concentric-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
            try {
                assertConnected(map, seed);
            } catch (AssertionError ae) {
                failures.add(ae.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("\n", failures));
        }

        BufferedImage contact = MapPreviewRenderer.composeContactSheet(perSeed, 3);
        Path contactPath = OUT_DIR.resolve("concentric-contact.png");
        ImageIO.write(contact, "PNG", contactPath.toFile());
        System.out.println("  wrote " + contactPath.toAbsolutePath());
    }

    /**
     * Diamond defense-station preview — cardinal ports converging inward. Eyeball:
     * dead map corners (diamond/cruciform footprint), 4 cardinal ports at the
     * edge-midpoints, straight cardinal corridors spoking inward to a connective
     * ring, then the core. Connectivity hard-asserted.
     */
    @Test
    void renderDiamondStationBatch() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();

        BufferedImage[] perSeed = new BufferedImage[SEEDS.length];
        List<String> failures = new java.util.ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            MapResult map = gen.generateDiamondStation(GRID_W, GRID_H, seed);
            BufferedImage img = MapPreviewRenderer.renderMap(map, captionFor(map, seed), null, null,
                    gen.getLastCompounds(), gen.getLastTacticalMap(), CELL_PX);
            perSeed[i] = img;
            Path out = OUT_DIR.resolve(String.format("diamond-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
            try {
                assertConnected(map, seed);
            } catch (AssertionError ae) {
                failures.add(ae.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("\n", failures));
        }

        BufferedImage contact = MapPreviewRenderer.composeContactSheet(perSeed, 3);
        Path contactPath = OUT_DIR.resolve("diamond-contact.png");
        ImageIO.write(contact, "PNG", contactPath.toFile());
        System.out.println("  wrote " + contactPath.toAbsolutePath());
    }

    /** Diamond station topological-roles preview — radial depth gradient (green ports → red core), spoke + port bridges, the single connective-ring loop. */
    @Test
    void renderDiamondRolesBatch() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();

        BufferedImage[] perSeed = new BufferedImage[SEEDS.length];
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            MapResult map = gen.generateDiamondStation(GRID_W, GRID_H, seed);
            BufferedImage img = renderStationRoles(map, gen.getLastStationGraph(), seed, CELL_PX);
            perSeed[i] = img;
            Path out = OUT_DIR.resolve(String.format("diamond-roles-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
        }

        BufferedImage contact = MapPreviewRenderer.composeContactSheet(perSeed, 3);
        Path contactPath = OUT_DIR.resolve("diamond-roles-contact.png");
        ImageIO.write(contact, "PNG", contactPath.toFile());
        System.out.println("  wrote " + contactPath.toAbsolutePath());
    }

    /** Concentric station topological-roles preview — radial depth gradient (green outer ring → red core), gate bridges, ring loops. */
    @Test
    void renderConcentricRolesBatch() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();

        BufferedImage[] perSeed = new BufferedImage[SEEDS.length];
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            MapResult map = gen.generateConcentricStation(GRID_W, GRID_H, seed);
            BufferedImage img = renderStationRoles(map, gen.getLastStationGraph(), seed, CELL_PX);
            perSeed[i] = img;
            Path out = OUT_DIR.resolve(String.format("concentric-roles-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
        }

        BufferedImage contact = MapPreviewRenderer.composeContactSheet(perSeed, 3);
        Path contactPath = OUT_DIR.resolve("concentric-roles-contact.png");
        ImageIO.write(contact, "PNG", contactPath.toFile());
        System.out.println("  wrote " + contactPath.toAbsolutePath());
    }

    /**
     * Station <em>topological roles</em> preview — the foundation later placement
     * rules query, made visible. Rooms are filled by depth-from-entry (green at
     * the marine breach → red at the deep defender end); articulation (must-pass)
     * rooms get a white ring; corridors draw as center-to-center lines, red for
     * bridges (sole link, on-spine) and cyan for loop edges (alternate route).
     * Eyeball: the depth gradient should flow from the green spawn to the red
     * spawn, bridges should sit on the obvious chokepoints, and loops should be
     * the visibly redundant connections.
     */
    @Test
    void renderStationRolesBatch() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();

        BufferedImage[] perSeed = new BufferedImage[SEEDS.length];
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            MapResult map = gen.generateStation(GRID_W, GRID_H, seed);
            BufferedImage img = renderStationRoles(map, gen.getLastStationGraph(), seed, CELL_PX);
            perSeed[i] = img;
            Path out = OUT_DIR.resolve(String.format("station-roles-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
        }

        BufferedImage contact = MapPreviewRenderer.composeContactSheet(perSeed, 3);
        Path contactPath = OUT_DIR.resolve("station-roles-contact.png");
        ImageIO.write(contact, "PNG", contactPath.toFile());
        System.out.println("  wrote " + contactPath.toAbsolutePath());
    }

    /** Bottom-strip label text shared by every {@link MapPreviewRenderer#renderMap} call in this class. */
    private static String captionFor(MapResult map, long seed) {
        return String.format("seed=%d  %dx%d  POIs=%d  doodads=%d",
                seed, map.grid.getWidth(), map.grid.getHeight(),
                map.pointsOfInterest.size(), map.doodads.size());
    }

    private static BufferedImage renderStationRoles(MapResult map, StationGraph graph, long seed, int cellPx) {
        NavigationGrid grid = map.grid;
        int w = grid.getWidth(), h = grid.getHeight();
        int imgW = w * cellPx;
        int imgH = h * cellPx + 24;
        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(15, 15, 18));
        g.fillRect(0, 0, imgW, imgH);

        int maxDepth = 1;
        for (StationGraph.Room r : graph.rooms()) maxDepth = Math.max(maxDepth, graph.depthFromEntry(r.id));

        // Carved corridor cells in neutral gray so the actual passages read.
        g.setColor(new Color(110, 110, 120));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (map.topology.getRoomPurpose(x, y) == RoomPurpose.CORRIDOR) {
                    g.fillRect(x * cellPx, (h - 1 - y) * cellPx, cellPx, cellPx);
                }
            }
        }

        // Rooms filled by depth gradient (entry green → deep red).
        for (StationGraph.Room r : graph.rooms()) {
            int depth = graph.depthFromEntry(r.id);
            float t = depth < 0 ? 1f : (float) depth / maxDepth;
            float hue = 0.33f * (1f - t);   // 0.33 green → 0.0 red
            g.setColor(Color.getHSBColor(hue, 0.55f, 0.80f));
            int sx = r.left * cellPx;
            int sy = (h - 1 - r.bottom) * cellPx;
            g.fillRect(sx, sy, (r.right - r.left + 1) * cellPx, (r.bottom - r.top + 1) * cellPx);
        }

        // Corridor edges: red = bridge (on-spine), cyan = loop.
        List<StationGraph.Corridor> corridors = graph.corridors();
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < corridors.size(); i++) {
            StationGraph.Corridor c = corridors.get(i);
            StationGraph.Room a = graph.room(c.roomA);
            StationGraph.Room b = graph.room(c.roomB);
            g.setColor(graph.isBridge(i) ? new Color(235, 70, 70) : new Color(70, 210, 235));
            g.drawLine(a.centerX * cellPx + cellPx / 2, (h - 1 - a.centerY) * cellPx + cellPx / 2,
                       b.centerX * cellPx + cellPx / 2, (h - 1 - b.centerY) * cellPx + cellPx / 2);
        }

        // Articulation rooms: white ring.
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        for (StationGraph.Room r : graph.rooms()) {
            if (!graph.isArticulation(r.id)) continue;
            int sx = r.left * cellPx;
            int sy = (h - 1 - r.bottom) * cellPx;
            g.drawRect(sx, sy, (r.right - r.left + 1) * cellPx - 1, (r.bottom - r.top + 1) * cellPx - 1);
        }

        MapPreviewRenderer.drawDiamond(g, new Color(80, 230, 110), map.marineSpawnX,   map.marineSpawnY,   h, cellPx);
        MapPreviewRenderer.drawDiamond(g, new Color(240, 80,  80), map.defenderSpawnX, map.defenderSpawnY, h, cellPx);

        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, h * cellPx, imgW, 24);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        int bridges = 0, arts = 0;
        for (int i = 0; i < graph.corridorCount(); i++) if (graph.isBridge(i)) bridges++;
        for (StationGraph.Room r : graph.rooms()) if (graph.isArticulation(r.id)) arts++;
        g.drawString(String.format("seed=%d  rooms=%d  bridges=%d  artic=%d  maxDepth=%d",
                seed, graph.roomCount(), bridges, arts, maxDepth), 6, h * cellPx + 16);

        g.dispose();
        return img;
    }

    /** Walks the walkable subgraph from one seed cell; fails if any walkable cell is unreached. */
    private static void assertConnected(MapResult map, long seed) {
        NavigationGrid grid = map.grid;
        int w = grid.getWidth(), h = grid.getHeight();
        boolean[][] visited = new boolean[w][h];

        int startX = -1, startY = -1;
        outer:
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (grid.isWalkable(x, y)) { startX = x; startY = y; break outer; }
            }
        }
        if (startX < 0) return; // pathological empty map; nothing to verify

        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});
        visited[startX][startY] = true;
        int reached = 1;
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int[][] nbrs = { {1,0}, {-1,0}, {0,1}, {0,-1} };
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (visited[nx][ny] || !grid.isWalkable(nx, ny)) continue;
                visited[nx][ny] = true;
                reached++;
                stack.push(new int[]{nx, ny});
            }
        }

        int totalWalkable = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (grid.isWalkable(x, y)) totalWalkable++;
            }
        }
        int finalReached = reached;
        int finalTotal = totalWalkable;
        if (reached != totalWalkable) {
            System.out.println("    FAIL seed=" + seed);
            int printed = 0;
            for (int y = 0; y < h && printed < 10; y++) {
                for (int x = 0; x < w && printed < 10; x++) {
                    if (grid.isWalkable(x, y) && !visited[x][y]) {
                        // Show what's around the unreached cell.
                        System.out.println("    unreached: " + x + "," + y
                                + " ground=" + map.topology.getGroundKind(x, y)
                                + " N=" + neighborState(map, x, y, 0, -1)
                                + " S=" + neighborState(map, x, y, 0, 1)
                                + " E=" + neighborState(map, x, y, 1, 0)
                                + " W=" + neighborState(map, x, y, -1, 0));
                        printed++;
                    }
                }
            }
        }
        assertTrue(reached == totalWalkable,
                () -> String.format("seed %d: walkable cells partitioned — reached %d of %d",
                        seed, finalReached, finalTotal));
    }

    private static String neighborState(MapResult map, int x, int y, int dx, int dy) {
        int nx = x + dx, ny = y + dy;
        if (nx < 0 || nx >= map.grid.getWidth() || ny < 0 || ny >= map.grid.getHeight()) return "OOB";
        return (map.grid.isWalkable(nx, ny) ? "w" : "W")
                + ":" + map.topology.getGroundKind(nx, ny);
    }
}
