package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSizing;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.gen.ship.VanillaHullSilhouettes;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * A ship's own boats: what is in her bays, the doors they leave by, and one of
 * them going out and coming back.
 *
 * <p>The airfield suites record a craft leaving a <em>place</em> — a hardstand,
 * a strip — and everything they show happens on one map. A ship's boat is the
 * other case: it leaves the map. So the frames here deliberately keep space
 * outside the hull in shot, because the interesting stretch is the one where
 * the boat is no longer on the deck and not yet anywhere else, and a camera
 * cropped to the compartment would show a shuttle that simply stops existing.
 *
 * <p>Drawn with the shuttle layer switched on, which the deck screens do not
 * use. A deck view is a picture of a ship's interior and a boat in flight is
 * not in it; this is evidence about the boats, so it asks for them.
 */
public final class ShipsBoatsSnapshotSuite implements SnapshotSuite {

    /** The hull the deck suites already use for detail, so the two show the same ship. */
    private static final String HULL = "valkyrie";
    private static final long SEED = 20260828L;

    /** Everything a deck draws, plus the craft a deck screen has no reason to. */
    private static final EnumSet<RenderLayer> BOAT_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.VEHICLES, RenderLayer.DOODADS,
            RenderLayer.UNITS, RenderLayer.SHUTTLES, RenderLayer.DRONES,
            RenderLayer.SMOKE, RenderLayer.HAZARDS);

    /** Black round the deck plate, so an arrow pointing off the hull has somewhere to go. */
    private static final int DECK_BORDER = 40;

    private static final int BAY_CELL = 20;
    private static final int DECK_CELL = 7;
    private static final int SORTIE_CELL = 13;

    /**
     * Hull around the bay in the sortie frames. Wider on the door's side so the
     * outboard bulkhead and the black beyond it are both in shot: a boat that
     * simply stopped being drawn in the middle of the room would read as a
     * rendering fault rather than as a departure.
     */
    private static final int SORTIE_SURROUND = 3;
    private static final int DOOR_SURROUND = 6;

    /** Four minutes of ship's time, which is three sorties at the field's own interval. */
    private static final int SORTIE_TICKS = 7200;
    private static final int FRAME_EVERY_TICKS = 45;
    private static final int FRAME_DELAY_MILLIS = 90;

    /** Enough ticks for the field to put its boats out before a still frame is taken. */
    private static final int PLACEMENT_TICKS = 30;

    private static final Color BACKDROP = new Color(0x10, 0x16, 0x1e);
    private static final Color LABEL = new Color(0xe4, 0xec, 0xf4);
    private static final Color DOOR = new Color(0x4a, 0xd6, 0xff);
    private static final Color BAY_OUTLINE = new Color(0xff, 0x8a, 0x3d);

    @Override public String id() { return "ships-boats"; }

    @Override public String label() {
        return "Ship's boats: the bays, the doors, and one boat going out and coming home";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration();
        VanillaHullSilhouettes vanilla = new VanillaHullSilhouettes(context.starsectorCore());
        if (!vanilla.available()) return List.of();
        VanillaHullSilhouettes.Hull hull = vanilla.read(HULL);
        if (hull == null || !hull.hullClass().boardable()) return List.of();

        DeckSizing.DeckPlan plan = DeckSizing.planFor(hull.hullClass(), hull.role(),
                hull.minCrew(), hull.maxCrew(), hull.cargo(), hull.silhouette().aspect());
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult map = generator.generateDeck(plan, SEED, hull.silhouette(), RoomFit.STANDARD);
        DeckGraph graph = generator.getLastDeckGraph();

        HeadlessUiRenderer drain = new HeadlessUiRenderer(
                HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));

        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(bay(drain, map, graph, hull, plan));
        artifacts.add(doors(drain, map, graph, hull, plan));
        artifacts.add(sortie(drain, map, graph, hull));
        return artifacts;
    }

    /**
     * One bay with her boats in it, drawn large enough to tell a shuttle from a
     * crate. This is the plate the fitting is judged on: a hangar that generates
     * as an empty pink room and a hangar with two boats backed onto a fuelling
     * run are the same room in a deck plan.
     */
    private SnapshotArtifact bay(HeadlessUiRenderer drain, MapResult map, DeckGraph graph,
                                 VanillaHullSilhouettes.Hull hull,
                                 DeckSizing.DeckPlan plan) throws Exception {
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(map, graph, SEED, null)) {
            scene.manDeck();
            settle(scene, 900);
            DeckGraph.Compartment bay = widestBay(graph);
            List<Gantry> berths = scene.berthsIn(bay);
            int across = bay.width() + 2;
            int down = bay.depth() + 2;
            BufferedImage room = drain.renderHostPass(
                    scene.pass(ShipDeckBattleScene.DeckView.over(bay, 1, BAY_CELL), BOAT_LAYERS),
                    across * BAY_CELL, down * BAY_CELL);

            BayAperture door = scene.bayDoor(bay);
            String caption = String.format(Locale.ROOT,
                    "%s  •  boat bay %dx%d  •  %d berths  •  %s  •  door %d cells wide, out %s",
                    hull.id(), bay.width(), bay.depth(), berths.size(),
                    plan.boats(), door == null ? 0 : door.widthCells(),
                    door == null ? "nowhere" : outward(door));
            return new SnapshotArtifact("boat-bay.png", captioned(room, caption));
        }
    }

    /**
     * The whole deck with every bay ringed and its door marked, because where a
     * bay sits is the placer's decision and the door follows from it. A bay in
     * the middle of the ship would be a bay that never launches, and this is the
     * frame that would show it.
     */
    private SnapshotArtifact doors(HeadlessUiRenderer drain, MapResult map, DeckGraph graph,
                                   VanillaHullSilhouettes.Hull hull,
                                   DeckSizing.DeckPlan plan) throws Exception {
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(map, graph, SEED, null)) {
            // The field puts its boats out on its first tick, so a deck drawn
            // before one has berths with nothing standing in them.
            settle(scene, PLACEMENT_TICKS);
            int width = map.grid.getWidth();
            int height = map.grid.getHeight();
            BufferedImage deck = drain.renderHostPass(
                    scene.pass(ShipDeckBattleScene.DeckView.over(0, 0, width, height, DECK_CELL),
                            BOAT_LAYERS),
                    width * DECK_CELL, height * DECK_CELL);

            // Room round the hull for the arrows, which point off the ship by
            // definition and would otherwise be clipped by the deck's own edge.
            BufferedImage board = new BufferedImage(deck.getWidth() + DECK_BORDER * 2,
                    deck.getHeight() + DECK_BORDER * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = board.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BACKDROP);
            g.fillRect(0, 0, board.getWidth(), board.getHeight());
            g.drawImage(deck, DECK_BORDER, DECK_BORDER, null);
            g.translate(DECK_BORDER, DECK_BORDER);
            int bays = 0;
            int boats = 0;
            for (DeckGraph.Compartment room : graph.compartments()) {
                if (room.purpose() != RoomPurpose.HANGAR) continue;
                bays++;
                boats += scene.berthsIn(room).size();
                g.setColor(BAY_OUTLINE);
                g.setStroke(new BasicStroke(2f));
                g.drawRect(room.left() * DECK_CELL,
                        rowOf(height, room.top() + room.depth() - 1, DECK_CELL),
                        room.width() * DECK_CELL, room.depth() * DECK_CELL);
                BayAperture aperture = scene.bayDoor(room);
                if (aperture != null) drawDoor(g, aperture, height, DECK_CELL, 7f);
            }
            g.dispose();

            String caption = String.format(Locale.ROOT,
                    "%s  •  deck %dx%d  •  %d boat bays  •  %d %s aboard  •  every bay on a flank, "
                            + "door out of the hull",
                    hull.id(), width, height, bays, boats, plan.boats());
            return new SnapshotArtifact("bay-doors.png", captioned(board, caption));
        }
    }

    /**
     * An arrow through the aperture, pointing the way out of the ship.
     *
     * <p>Drawn in image space, which runs the other way up from cell space: the
     * scene's camera puts increasing y up the frame, so a door facing forward
     * points down the picture. Everything on this overlay goes through
     * {@link #rowOf} for that reason — an earlier version marked the doors on
     * the berthing rooms opposite them, which looked plausible enough to ship.
     */
    private static void drawDoor(Graphics2D g, BayAperture door, int gridHeight,
                                 int cell, float outCells) {
        float x = (door.centerX() + 0.5f) * cell;
        float y = rowOf(gridHeight, door.centerY(), cell) + cell * 0.5f;
        float span = door.widthCells() * cell * 0.5f;
        float out = outCells * cell;
        int outX = door.outDx();
        int outY = -door.outDy();
        g.setColor(DOOR);
        g.setStroke(new BasicStroke(3f));
        // The opening itself, drawn across the direction it faces.
        g.drawLine(Math.round(x - outY * span), Math.round(y - outX * span),
                Math.round(x + outY * span), Math.round(y + outX * span));
        float tipX = x + outX * out;
        float tipY = y + outY * out;
        g.setStroke(new BasicStroke(2f));
        g.drawLine(Math.round(x), Math.round(y), Math.round(tipX), Math.round(tipY));
        Path2D head = new Path2D.Float();
        float back = 6f;
        float wide = 5f;
        head.moveTo(tipX, tipY);
        head.lineTo(tipX - outX * back - outY * wide, tipY - outY * back - outX * wide);
        head.lineTo(tipX - outX * back + outY * wide, tipY - outY * back + outX * wide);
        head.closePath();
        g.fill(head);
    }

    /**
     * One bay's boats, and what happens to a berth when one of them goes.
     *
     * <p><b>The boat does not appear outside.</b> A sortie is steered at a point
     * clear of the hull that is off the deck's own grid, which is the whole
     * point of a bay — off the ship is a place a deck has no coordinates for —
     * and the scene camera is clamped to the world it is drawing. So the
     * recording ends at the door and the caption carries the rest. Framed to
     * keep that door and the black outside it in shot, so a departure reads as
     * one.
     *
     * <p>What is worth watching is therefore the berth rather than the boat: two
     * hulls, one leaving, a stall standing empty for a stretch of ship's time,
     * and the crew working the bay throughout.
     */
    private SnapshotArtifact sortie(HeadlessUiRenderer drain, MapResult map, DeckGraph graph,
                                    VanillaHullSilhouettes.Hull hull) throws Exception {
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(map, graph, SEED, null)) {
            scene.manDeck();
            DeckGraph.Compartment bay = widestBay(graph);
            BayAperture door = scene.bayDoor(bay);
            int gridHeight = map.grid.getHeight();
            int lowMargin = door != null && door.outDy() < 0 ? DOOR_SURROUND : SORTIE_SURROUND;
            int highMargin = door != null && door.outDy() > 0 ? DOOR_SURROUND : SORTIE_SURROUND;
            int leftMargin = door != null && door.outDx() < 0 ? DOOR_SURROUND : SORTIE_SURROUND;
            int rightMargin = door != null && door.outDx() > 0 ? DOOR_SURROUND : SORTIE_SURROUND;
            int left = bay.left() - leftMargin;
            int top = bay.top() - lowMargin;
            int across = bay.width() + leftMargin + rightMargin;
            int down = bay.depth() + lowMargin + highMargin;

            AirfieldService bays = scene.simulation().getAirfieldService();
            BattleSimulation sim = scene.simulation();
            // Opening on an empty bay would be a frame of the field before it
            // has put anything out, which reads as a ship with no boats.
            settle(scene, PLACEMENT_TICKS);
            List<BufferedImage> frames = new ArrayList<>();
            for (int tick = 0; tick <= SORTIE_TICKS; tick++) {
                if (tick % FRAME_EVERY_TICKS == 0) {
                    BufferedImage frame = drain.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.over(
                                    left, top, across, down, SORTIE_CELL), BOAT_LAYERS),
                            across * SORTIE_CELL, down * SORTIE_CELL);
                    if (door != null) markDoor(frame, door, left, top, down, gridHeight);
                    frames.add(captioned(frame, caption(hull, bays, tick)));
                }
                sim.advance(BattleSimulation.TICK_DT);
            }
            return SnapshotArtifact.animation("boat-sortie.gif", frames, FRAME_DELAY_MILLIS);
        }
    }

    /**
     * Parked, away and turning round counted apart, because they are three
     * different things happening to a berth and only one of them is a sortie.
     * A boat home and being worked on is the crew's half of this feature; rolled
     * in with "away" it reads as a ship that keeps four boats in the air.
     */
    private static String caption(VanillaHullSilhouettes.Hull hull, AirfieldService bays,
                                  int tick) {
        int parked = 0;
        int away = 0;
        int turning = 0;
        for (AirfieldService.Berth berth : bays.berths()) {
            switch (berth.state) {
                case PARKED -> parked++;
                case AWAY -> away++;
                case REFITTING -> turning++;
                default -> { }
            }
        }
        return String.format(Locale.ROOT,
                "%s boat bay  •  %3ds  •  %d parked, %d away, %d turning round",
                hull.id(), Math.round(tick * BattleSimulation.TICK_DT),
                parked, away, turning);
    }

    /**
     * The aperture drawn onto a framed shot of the bay, in that frame's own
     * pixels rather than the whole deck's.
     */
    private static void markDoor(BufferedImage frame, BayAperture door,
                                 int viewLeft, int viewTop, int viewDown, int gridHeight) {
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // The frame is a window on the deck, so the door's row within it is its
        // row on the deck less the window's own top row.
        g.translate(-viewLeft * SORTIE_CELL,
                (viewTop + viewDown - gridHeight) * SORTIE_CELL);
        drawDoor(g, door, gridHeight, SORTIE_CELL, 3f);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        g.drawString("out", (door.centerX() + 1.2f) * SORTIE_CELL,
                rowOf(gridHeight, door.centerY(), SORTIE_CELL) + SORTIE_CELL);
        g.dispose();
    }

    /**
     * The top of cell row {@code cellY} in image pixels for a whole-grid frame.
     *
     * <p>The scene camera puts increasing y <em>up</em> the picture, so anything
     * drawn over a rendered frame from cell coordinates has to turn round here.
     */
    private static int rowOf(int gridHeight, float cellY, int cell) {
        return Math.round((gridHeight - 1 - cellY) * cell);
    }

    /**
     * The bay with the most floor. A hull carries several and they are not the
     * same shape; the roomiest is the one whose fitting is worth looking at.
     */
    private static DeckGraph.Compartment widestBay(DeckGraph graph) {
        DeckGraph.Compartment best = null;
        for (DeckGraph.Compartment room : graph.compartments()) {
            if (room.purpose() != RoomPurpose.HANGAR) continue;
            if (best == null || room.area() > best.area()) best = room;
        }
        if (best == null) throw new IllegalStateException("this hull has no boat bay");
        return best;
    }

    /** Run the deck on for a while so the crew are at work rather than at their spawn cells. */
    private static void settle(ShipDeckBattleScene scene, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            scene.simulation().advance(BattleSimulation.TICK_DT);
        }
    }

    private static String outward(BayAperture door) {
        if (door.outDx() != 0) return door.outDx() > 0 ? "starboard" : "port";
        return door.outDy() > 0 ? "aft-facing flank" : "forward-facing flank";
    }

    /** The frame with a band of text over it, in the shape the review frames use. */
    private static BufferedImage captioned(BufferedImage frame, String caption) {
        int band = 26;
        BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight() + band,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BACKDROP);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(frame, 0, band, null);
        g.setColor(LABEL);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString(caption, 10, 17);
        g.dispose();
        return image;
    }
}
