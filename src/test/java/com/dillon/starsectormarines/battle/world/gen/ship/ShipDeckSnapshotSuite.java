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
import java.util.List;

/**
 * Deterministic plan views of generated ship decks — the readable evidence for
 * whether a deck's hull, spine, cross-passages, and compartment zoning came out
 * as intended. Compartments are tinted by longitudinal zone so the fore/
 * midships/aft gradient is visible at a glance rather than inferred.
 *
 * <p>Pure geometry: no Starsector process, no OpenGL context, no tile art.
 */
public final class ShipDeckSnapshotSuite implements SnapshotSuite {

    private static final int WIDTH = 96;
    private static final int HEIGHT = 28;
    private static final int CELL = 9;
    private static final long[] SEEDS = { 1L, 42L, 90210L };

    private static final Color HULL = new Color(0x10, 0x16, 0x1e);
    private static final Color STRUCTURE = new Color(0x28, 0x31, 0x3d);
    private static final Color CORRIDOR = new Color(0x8a, 0x99, 0xa8);
    private static final Color FORE_ROOM = new Color(0x3f, 0x6f, 0xa8);
    private static final Color MIDSHIPS_ROOM = new Color(0x4d, 0x8c, 0x5a);
    private static final Color AFT_ROOM = new Color(0xa8, 0x6a, 0x3a);
    private static final Color GRID_LINE = new Color(0x00, 0x00, 0x00, 40);
    private static final Color LABEL = new Color(0xe4, 0xec, 0xf4);

    @Override
    public String id() {
        return "ship-decks";
    }

    @Override
    public String label() {
        return "Ship deck plans";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) {
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (long seed : SEEDS) {
            ShipDeckGenerator generator = new ShipDeckGenerator();
            MapResult map = generator.generateDeck(WIDTH, HEIGHT, seed);
            artifacts.add(new SnapshotArtifact("ship-deck-seed-" + seed + ".png",
                    renderPlan(map, generator.getLastDeckProfile(), generator.getLastDeckGraph(), seed)));
        }
        return List.copyOf(artifacts);
    }

    private static BufferedImage renderPlan(MapResult map, DeckProfile profile,
                                            DeckGraph graph, long seed) {
        int margin = 12;
        int legend = 34;
        BufferedImage image = new BufferedImage(
                WIDTH * CELL + margin * 2, HEIGHT * CELL + margin * 2 + legend,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(HULL);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                Color color = cellColor(map, profile, x, y);
                if (color == null) continue;
                g.setColor(color);
                g.fillRect(margin + x * CELL, margin + y * CELL, CELL, CELL);
                g.setColor(GRID_LINE);
                g.drawRect(margin + x * CELL, margin + y * CELL, CELL, CELL);
            }
        }

        markSpawn(g, margin, map.marineSpawnX, map.marineSpawnY, new Color(0x66, 0xd9, 0xef));
        markSpawn(g, margin, map.defenderSpawnX, map.defenderSpawnY, new Color(0xef, 0x5f, 0x5f));

        g.setColor(LABEL);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int baseline = margin + HEIGHT * CELL + 20;
        g.drawString("seed " + seed
                        + "   bow beam " + profile.beam(0)
                        + "  midships " + profile.beam(WIDTH / 2)
                        + "  stern " + profile.beam(WIDTH - 1)
                        + "   compartments " + graph.compartmentCount()
                        + "   cross-passages " + graph.corridorFrames().length
                        + "   blue = fore, green = midships, amber = aft",
                margin, baseline);
        g.dispose();
        return image;
    }

    /** Null means leave the hull backdrop showing through. */
    private static Color cellColor(MapResult map, DeckProfile profile, int x, int y) {
        if (!map.grid.isWalkable(x, y)) {
            return profile.containsCell(x, y) ? STRUCTURE : null;
        }
        RoomPurpose purpose = map.topology.getRoomPurpose(x, y);
        if (purpose == RoomPurpose.CORRIDOR) return CORRIDOR;
        return switch (profile.zone(x)) {
            case FORE -> FORE_ROOM;
            case MIDSHIPS -> MIDSHIPS_ROOM;
            case AFT -> AFT_ROOM;
        };
    }

    private static void markSpawn(Graphics2D g, int margin, int x, int y, Color color) {
        g.setColor(color);
        g.fillOval(margin + x * CELL - 1, margin + y * CELL - 1, CELL + 2, CELL + 2);
    }
}
