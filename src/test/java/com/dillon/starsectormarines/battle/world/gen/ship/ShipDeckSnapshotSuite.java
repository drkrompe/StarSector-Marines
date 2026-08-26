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
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        VanillaHullSilhouettes vanilla = new VanillaHullSilhouettes(context.starsectorCore());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        if (vanilla.available()) {
            for (String hullId : HULLS) {
                VanillaHullSilhouettes.Hull hull = vanilla.read(hullId);
                if (hull == null || !hull.hullClass().boardable()) continue;
                artifacts.add(plan(hull.id(), hull.silhouette(),
                        DeckSizing.planFor(hull.hullClass(), hull.maxCrew(), hull.cargo(),
                                hull.silhouette().aspect()),
                        hull.hullClass() + ", " + hull.maxCrew() + " crew, "
                                + hull.cargo() + " cargo"));
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
        int legend = 34;
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        String caption = name + "  (" + complement + ")"
                + "   deck " + width + "x" + height
                + "   program " + deckPlan.rooms().size()
                + "   placed " + graph.compartmentCount()
                + "   unplaced " + graph.unplaced().size()
                + "   blue fore / green midships / amber aft";

        // A short deck is narrower than its own caption, so the canvas has to
        // fit whichever is wider or the legend silently truncates.
        int captionWidth = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
                .createGraphics().getFontMetrics(font).stringWidth(caption);
        int canvasWidth = Math.max(width * CELL, captionWidth) + margin * 2;

        BufferedImage image = new BufferedImage(
                canvasWidth, height * CELL + margin * 2 + legend,
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

        markSpawn(g, margin, map.marineSpawnX, map.marineSpawnY, new Color(0x66, 0xd9, 0xef));
        markSpawn(g, margin, map.defenderSpawnX, map.defenderSpawnY, new Color(0xef, 0x5f, 0x5f));

        g.setColor(LABEL);
        g.setFont(font);
        g.drawString(caption, margin, margin + height * CELL + 20);
        g.dispose();
        return image;
    }

    /** Null means leave the hull backdrop showing through. */
    private static Color cellColor(MapResult map, DeckProfile profile, int x, int y) {
        if (!map.grid.isWalkable(x, y)) {
            return profile.containsCell(x, y) ? STRUCTURE : null;
        }
        if (map.topology.getRoomPurpose(x, y) == RoomPurpose.CORRIDOR) return CORRIDOR;
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
