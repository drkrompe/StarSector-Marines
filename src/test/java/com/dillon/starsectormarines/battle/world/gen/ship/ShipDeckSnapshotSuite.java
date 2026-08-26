package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic plan views of generated ship decks — the readable evidence for
 * whether hull shape, circulation, and compartment zoning came out as intended.
 *
 * <p>Where the installed game is available the decks are traced from real
 * vanilla hull sprites and sized from those hulls' crew and cargo, so the
 * evidence shows the family against genuine proportions rather than against a
 * curve chosen to flatter it. Without the install it falls back to the synthetic
 * taper so the suite still produces evidence.
 */
public final class ShipDeckSnapshotSuite implements SnapshotSuite {

    /** Hulls chosen to span the size range: a frigate, a personnel transport, a capital, a freighter. */
    private static final String[] HULLS = { "wolf", "valkyrie", "conquest", "atlas" };
    private static final int CELL = 8;
    private static final long SEED = 42L;

    private static final Color HULL = new Color(0x10, 0x16, 0x1e);
    private static final Color STRUCTURE = new Color(0x28, 0x31, 0x3d);
    private static final Color CORRIDOR = new Color(0x8a, 0x99, 0xa8);
    private static final Color UNKNOWN_ROOM = new Color(0x5a, 0x5f, 0x6a);
    private static final Color GRID_LINE = new Color(0x00, 0x00, 0x00, 40);
    private static final Color LABEL = new Color(0xe4, 0xec, 0xf4);
    private static final Color ZONE_LINE = new Color(0xf2, 0xd0, 0x6b, 0xcc);

    /**
     * One colour per kind of room, because that is the question these plans are
     * read to answer. Tinting by zone instead split any room straddling a zone
     * boundary down the middle, which reads exactly like two rooms with the wall
     * missing between them — the one defect this evidence exists to catch. Zones
     * are drawn as lines now, which is also closer to how the tiers consume
     * them: a cut across the hull, not a wash over it.
     */
    private static final Map<RoomPurpose, Color> ROOM_COLORS = Map.ofEntries(
            Map.entry(RoomPurpose.CONTROL_ROOM, new Color(0x8d, 0x6f, 0xc9)),
            Map.entry(RoomPurpose.BARRACKS, new Color(0x3f, 0x7f, 0xc4)),
            Map.entry(RoomPurpose.ARMORY, new Color(0xc4, 0x4b, 0x4b)),
            Map.entry(RoomPurpose.VEHICLE_BAY, new Color(0xd8, 0x8b, 0x2f)),
            Map.entry(RoomPurpose.STOCKROOM, new Color(0x9a, 0x7a, 0x45)),
            Map.entry(RoomPurpose.PRODUCTION_FLOOR, new Color(0x4d, 0x9c, 0x5f)),
            Map.entry(RoomPurpose.PARTS_CAGE, new Color(0x3f, 0x9a, 0x93)),
            Map.entry(RoomPurpose.SERVER_ROOM, new Color(0x4a, 0xb5, 0xd6)),
            Map.entry(RoomPurpose.GENERIC, new Color(0x6f, 0x76, 0x84)),
            Map.entry(RoomPurpose.HANGAR, new Color(0xd8, 0x5c, 0x9a)),
            Map.entry(RoomPurpose.MESS_HALL, new Color(0xc9, 0xa2, 0x5e)),
            Map.entry(RoomPurpose.FIRING_RANGE, new Color(0x9c, 0x4d, 0x2f)),
            Map.entry(RoomPurpose.WASHROOM, new Color(0x53, 0x6a, 0x8c)),
            Map.entry(RoomPurpose.CONFERENCE_ROOM, new Color(0xb0, 0x8a, 0xd6)),
            Map.entry(RoomPurpose.PATIENT_WARD, new Color(0xe0, 0xe4, 0xea)),
            Map.entry(RoomPurpose.LOADING_BAY, new Color(0x6b, 0x8c, 0x3f)));

    @Override
    public String id() {
        return "ship-decks";
    }

    @Override
    public String label() {
        return "Ship deck plans";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        VanillaHullSilhouettes vanilla = new VanillaHullSilhouettes(context.starsectorCore());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        if (vanilla.available()) {
            for (String hullId : HULLS) {
                VanillaHullSilhouettes.Hull hull = vanilla.read(hullId);
                if (hull == null || !hull.hullClass().boardable()) continue;
                artifacts.add(plan(hull.id(), hull.silhouette(),
                        DeckSizing.planFor(hull.hullClass(), hull.role(), hull.minCrew(),
                                hull.maxCrew(), hull.cargo(), hull.silhouette().aspect()),
                        hull.role().name().toLowerCase().replace('_', ' ')
                                + ", " + hull.minCrew() + "/" + hull.maxCrew() + " crew, "
                                + hull.lift() + " lift, " + hull.cargo() + " cargo"));
            }
        }
        if (artifacts.isEmpty()) {
            artifacts.add(plan("synthetic", null,
                    new DeckSizing.DeckPlan(96, 28, List.of()), "no game install"));
        }
        return List.copyOf(artifacts);
    }

    private static SnapshotArtifact plan(String name, HullSilhouette silhouette,
                                         DeckSizing.DeckPlan deckPlan, String complement) {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult map = generator.generateDeck(deckPlan, SEED, silhouette);
        BufferedImage image = renderPlan(map, generator.getLastDeckProfile(),
                generator.getLastDeckGraph(), name, deckPlan, complement);
        return new SnapshotArtifact("ship-deck-" + name + ".png", image);
    }

    private static BufferedImage renderPlan(MapResult map, DeckProfile profile, DeckGraph graph,
                                            String name, DeckSizing.DeckPlan deckPlan,
                                            String complement) {
        int width = deckPlan.frames();
        int height = deckPlan.height();
        int margin = 12;
        int caption = 42;
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        String summary = name + "  (" + complement + ")"
                + "   deck " + width + "x" + height
                + "   program " + deckPlan.rooms().size()
                + "   placed " + graph.compartmentCount()
                + "   unplaced " + graph.unplaced().size()
                + "   widest opening " + widestOpening(map, width, height);

        // A short deck is narrower than its own caption, so the canvas has to
        // fit whichever is wider or the legend silently truncates.
        int captionWidth = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
                .createGraphics().getFontMetrics(font).stringWidth(summary);
        int canvasWidth = Math.max(width * CELL, captionWidth) + margin * 2;

        BufferedImage image = new BufferedImage(
                canvasWidth, height * CELL + margin * 2 + caption,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(HULL);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = cellColor(map, profile, x, y);
                if (color == null) continue;
                g.setColor(color);
                g.fillRect(margin + x * CELL, margin + y * CELL, CELL, CELL);
                g.setColor(GRID_LINE);
                g.drawRect(margin + x * CELL, margin + y * CELL, CELL, CELL);
            }
        }

        drawZoneCuts(g, profile, font, margin, width, height);
        markSpawn(g, margin, map.marineSpawnX, map.marineSpawnY, new Color(0x66, 0xd9, 0xef));
        markSpawn(g, margin, map.defenderSpawnX, map.defenderSpawnY, new Color(0xef, 0x5f, 0x5f));

        g.setColor(LABEL);
        g.setFont(font);
        g.drawString(summary, margin, margin + height * CELL + 18);
        drawLegend(g, map, font, margin, margin + height * CELL + 34, width, height);
        g.dispose();
        return image;
    }

    /**
     * Where the deck stops being the bow and starts being the waist, and again
     * where it becomes the stern — drawn as cuts across the hull with the zone
     * named beside each stretch, because that is what a tier asking "is this
     * forward?" actually gets.
     */
    private static void drawZoneCuts(Graphics2D g, DeckProfile profile, Font font,
                                     int margin, int width, int height) {
        g.setFont(font);
        int start = 0;
        for (int x = 1; x <= width; x++) {
            boolean end = x == width || profile.zone(x) != profile.zone(start);
            if (!end) continue;
            if (x < width) {
                g.setColor(ZONE_LINE);
                g.fillRect(margin + x * CELL - 1, margin, 2, height * CELL);
            }
            g.setColor(LABEL);
            g.drawString(profile.zone(start).name().toLowerCase(),
                    margin + start * CELL + 4, margin + 12);
            start = x;
        }
    }

    /** Swatches for the kinds of room this deck actually contains, in a single row. */
    private static void drawLegend(Graphics2D g, MapResult map, Font font,
                                   int margin, int top, int width, int height) {
        Set<RoomPurpose> present = EnumSet.noneOf(RoomPurpose.class);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!map.grid.isWalkable(x, y)) continue;
                RoomPurpose purpose = map.topology.getRoomPurpose(x, y);
                if (purpose != null) present.add(purpose);
            }
        }
        g.setFont(font);
        int x = margin;
        for (RoomPurpose purpose : present) {
            g.setColor(purpose == RoomPurpose.CORRIDOR
                    ? CORRIDOR
                    : ROOM_COLORS.getOrDefault(purpose, UNKNOWN_ROOM));
            g.fillRect(x, top - 9, 10, 10);
            g.setColor(LABEL);
            String name = purpose.name().toLowerCase().replace('_', ' ');
            g.drawString(name, x + 14, top);
            x += 14 + g.getFontMetrics().stringWidth(name) + 14;
        }
    }

    /**
     * The longest unbroken stretch where a room stands open to a corridor.
     *
     * <p>This is the number that catches a passage eating the bulkhead it runs
     * alongside. A share of open cells cannot: one compartment stripped of a
     * whole wall is a couple of percent of a large deck and hides in the
     * average. A door is two cells, so anything above that is a room that has
     * lost part of its wall, and the figure says how much.
     */
    private static int widestOpening(MapResult map, int width, int height) {
        int widest = 0;
        // Horizontal walls: openings stacked along x, one row of room above or below.
        for (int y = 0; y < height; y++) {
            widest = Math.max(widest, longestRun(map, width, y, true, 1));
            widest = Math.max(widest, longestRun(map, width, y, true, -1));
        }
        for (int x = 0; x < width; x++) {
            widest = Math.max(widest, longestRun(map, height, x, false, 1));
            widest = Math.max(widest, longestRun(map, height, x, false, -1));
        }
        return widest;
    }

    /** Longest run along one line where a corridor cell faces a room cell on the given side. */
    private static int longestRun(MapResult map, int span, int line, boolean horizontal, int side) {
        int longest = 0;
        int run = 0;
        for (int i = 0; i < span; i++) {
            int x = horizontal ? i : line;
            int y = horizontal ? line : i;
            int nx = horizontal ? x : x + side;
            int ny = horizontal ? y + side : y;
            if (isCorridor(map, x, y) && isRoom(map, nx, ny)) {
                longest = Math.max(longest, ++run);
            } else {
                run = 0;
            }
        }
        return longest;
    }

    private static boolean isCorridor(MapResult map, int x, int y) {
        return inside(map, x, y) && map.grid.isWalkable(x, y)
                && map.topology.getRoomPurpose(x, y) == RoomPurpose.CORRIDOR;
    }

    private static boolean isRoom(MapResult map, int x, int y) {
        return inside(map, x, y) && map.grid.isWalkable(x, y)
                && map.topology.getRoomPurpose(x, y) != RoomPurpose.CORRIDOR;
    }

    private static boolean inside(MapResult map, int x, int y) {
        return x >= 0 && y >= 0 && x < map.grid.getWidth() && y < map.grid.getHeight();
    }

    /** Null means leave the hull backdrop showing through. */
    private static Color cellColor(MapResult map, DeckProfile profile, int x, int y) {
        if (!map.grid.isWalkable(x, y)) {
            return profile.containsCell(x, y) ? STRUCTURE : null;
        }
        RoomPurpose purpose = map.topology.getRoomPurpose(x, y);
        if (purpose == RoomPurpose.CORRIDOR) return CORRIDOR;
        return ROOM_COLORS.getOrDefault(purpose, UNKNOWN_ROOM);
    }

    private static void markSpawn(Graphics2D g, int margin, int x, int y, Color color) {
        g.setColor(color);
        g.fillOval(margin + x * CELL - 1, margin + y * CELL - 1, CELL + 2, CELL + 2);
    }
}
