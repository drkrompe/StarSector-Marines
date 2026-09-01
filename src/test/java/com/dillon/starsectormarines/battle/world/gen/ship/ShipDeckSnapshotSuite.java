package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.Graphics2DTileSink;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileSink;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.InteriorChange;
import com.dillon.starsectormarines.ops.battleview.ShipInterior;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import org.json.JSONObject;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /**
     * Hulls a player might plausibly quarter a company on, chosen to span role
     * rather than size: the comparison is only interesting because a bigger
     * ship can be a worse home.
     */
    private static final String[] CANDIDATE_HULLS = {
            "valkyrie", "starliner", "eagle", "dominator", "apogee",
            "venture", "legion", "atlas", "conquest", "prometheus" };

    /** The places a marine company weighs a hull on, in the order they are read. */
    private static final RoomPurpose[] COMPARED = {
            RoomPurpose.BARRACKS, RoomPurpose.VEHICLE_BAY, RoomPurpose.ARMORY,
            RoomPurpose.FIRING_RANGE, RoomPurpose.HANGAR, RoomPurpose.PATIENT_WARD,
            RoomPurpose.MESS_HALL, RoomPurpose.STOCKROOM, RoomPurpose.CONFERENCE_ROOM };
    private static final int CELL = 8;
    private static final long SEED = 42L;

    private static final Color HULL = new Color(0x10, 0x16, 0x1e);
    private static final Color STRUCTURE = new Color(0x28, 0x31, 0x3d);
    private static final Color CORRIDOR = new Color(0x8a, 0x99, 0xa8);
    private static final Color UNKNOWN_ROOM = new Color(0x5a, 0x5f, 0x6a);
    private static final Color GRID_LINE = new Color(0x00, 0x00, 0x00, 40);
    private static final Color LABEL = new Color(0xe4, 0xec, 0xf4);
    private static final Color ZONE_LINE = new Color(0xf2, 0xd0, 0x6b, 0xcc);
    /** Doors on the close-up sheet, drawn over the deck rather than instead of it. */
    private static final Color DOOR_MARK = new Color(0x6b, 0xe0, 0xff, 0x9a);
    private static final Color FIXTURE = new Color(0x0d, 0x11, 0x17, 0xc4);
    private static final Color FIXTURE_EDGE = new Color(0xff, 0xff, 0xff, 0x2a);

    /** Sheets the fill draws from, loaded once and shared across every plan. */
    private static final Map<String, BufferedImage> SHEETS = new HashMap<>();

    /** The hull the refit comparison is drawn on: small enough to read three of side by side. */
    private static final String REFIT_HULL = "wolf";
    /** The hull whose rooms are shown close up; it carries the widest spread of purposes. */
    private static final String DETAIL_HULL = "valkyrie";
    /**
     * Cell size for the close-up sheet, matched to the source art.
     *
     * <p>Deliberately equal to the cell size of the sheets the fill draws from.
     * Twenty-two was chosen to keep the sheet small and quietly resampled every
     * thirty-two pixel sprite down by a third, so the evidence for a room
     * authoring pass was softer than anything the game would ever show. Art is
     * judged at its own resolution or it is not being judged.
     */
    private static final int DETAIL_CELL = 32;
    /** Working space kept around a berth in the fitting view, in cells. */
    private static final int FITTING_SURROUND = 3;

    /**
     * One colour per kind of room, because that is the question these plans are
     * read to answer. Tinting by zone instead split any room straddling a zone
     * boundary down the middle, which reads exactly like two rooms with the wall
     * missing between them — the one defect this evidence exists to catch. Zones
     * are drawn as lines now, which is also closer to how the tiers consume
     * them: a cut across the hull, not a wash over it.
     */
    private static final Map<RoomPurpose, Color> ROOM_COLORS = Map.ofEntries(
            Map.entry(RoomPurpose.BRIDGE, new Color(0x8d, 0x6f, 0xc9)),
            Map.entry(RoomPurpose.BARRACKS, new Color(0x3f, 0x7f, 0xc4)),
            Map.entry(RoomPurpose.CREW_QUARTERS, new Color(0x2f, 0x5a, 0x8c)),
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
            Map.entry(RoomPurpose.LOADING_BAY, new Color(0x6b, 0x8c, 0x3f)),
            Map.entry(RoomPurpose.ENGINE_ROOM, new Color(0x2f, 0x6f, 0x4e)));

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
        installTileRegistry();
        VanillaHullSilhouettes vanilla = new VanillaHullSilhouettes(context.starsectorCore());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        if (vanilla.available()) {
            for (String hullId : HULLS) {
                VanillaHullSilhouettes.Hull hull = vanilla.read(hullId);
                if (hull == null || !hull.hullClass().boardable()) continue;
                DeckSizing.DeckPlan deckPlan = DeckSizing.planFor(hull.hullClass(), hull.role(),
                        hull.minCrew(), hull.maxCrew(), hull.cargo(), hull.silhouette().aspect());
                String complement = hull.role().name().toLowerCase().replace('_', ' ')
                        + ", " + hull.minCrew() + "/" + hull.maxCrew() + " crew, "
                        + hull.lift() + " lift, " + hull.cargo() + " cargo";
                artifacts.add(plan(hull.id(), hull.silhouette(), deckPlan,
                        complement, RoomFit.STANDARD));
                if (hull.id().equals(DETAIL_HULL)) {
                    artifacts.add(roomDetail(context, hull.silhouette(), deckPlan));
                    artifacts.add(mechLab(context, hull.silhouette(), deckPlan));
                }
                if (hull.id().equals(REFIT_HULL)) {
                    // The same hull at three fittings, which is the upgrade
                    // chain: identical rooms, different capacity.
                    for (RoomFit refit : RoomFit.values()) {
                        artifacts.add(plan(hull.id() + "-" + refit.name().toLowerCase(),
                                hull.silhouette(), deckPlan, complement, refit));
                    }
                }
            }
        }
        if (vanilla.available()) {
            SnapshotArtifact comparison = candidates(vanilla);
            if (comparison != null) artifacts.add(comparison);
        }
        if (artifacts.isEmpty()) {
            artifacts.add(plan("synthetic", null,
                    new DeckSizing.DeckPlan(96, 28, List.of()), "no game install",
                    RoomFit.STANDARD));
        }
        return List.copyOf(artifacts);
    }

    /**
     * Every candidate hull side by side, as what would be aboard each.
     *
     * <p>The transfer decision, drawn. A ship list rated by tonnage cannot show
     * the trade the player is actually making, because the trade is between
     * hulls that hold different things: a liner out-berths a warship twice her
     * weight and has nowhere to service a walker, and a freighter has holds and
     * no reason for anybody to be aboard. The last column is the half that
     * matters most — a screen that only showed gains would be a worse screen
     * than none.
     *
     * <p>Read against the first row, which is the fleet's best home by lift and
     * therefore where a company would be quartered by default.
     */
    private static SnapshotArtifact candidates(VanillaHullSilhouettes vanilla) throws Exception {
        List<VanillaHullSilhouettes.Hull> hulls = new ArrayList<>();
        for (String hullId : CANDIDATE_HULLS) {
            VanillaHullSilhouettes.Hull hull = vanilla.read(hullId);
            if (hull != null && hull.hullClass().boardable()) hulls.add(hull);
        }
        if (hulls.isEmpty()) return null;
        hulls.sort(Comparator.comparingInt(VanillaHullSilhouettes.Hull::lift).reversed());

        List<ShipInterior> interiors = new ArrayList<>();
        for (VanillaHullSilhouettes.Hull hull : hulls) {
            interiors.add(ShipInterior.of(new CompanyShip(hull.hullClass(), hull.role(),
                    hull.minCrew(), hull.maxCrew(), hull.cargo(),
                    hull.silhouette()), SEED));
        }

        int margin = 24;
        int rowHeight = 30;
        int nameWidth = 150;
        int classWidth = 210;
        int liftWidth = 70;
        int cellWidth = 96;
        int lossWidth = 320;
        int width = margin * 2 + nameWidth + classWidth + liftWidth
                + cellWidth * COMPARED.length + lossWidth;
        int height = margin * 2 + rowHeight * (hulls.size() + 3) + 40;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(HULL);
        g.fillRect(0, 0, width, height);

        g.setColor(LABEL);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.drawString("Where the company could live", margin, margin + 6);

        Font head = new Font(Font.SANS_SERIF, Font.BOLD, 11);
        Font body = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        int y = margin + rowHeight + 6;

        g.setFont(head);
        g.setColor(CORRIDOR);
        int x = margin;
        g.drawString("HULL", x, y);
        x += nameWidth;
        g.drawString("CLASS AND ROLE", x, y);
        x += classWidth;
        g.drawString("LIFT", x, y);
        x += liftWidth;
        for (RoomPurpose purpose : COMPARED) {
            g.drawString(shortName(purpose), x, y);
            x += cellWidth;
        }
        g.drawString("MOVING HERE WOULD COST", x, y);

        g.setColor(STRUCTURE);
        g.drawLine(margin, y + 8, width - margin, y + 8);

        // Read against a hull a company would plausibly already be on. Comparing
        // against the fleet's largest makes every other row a pure gain and the
        // cost column empty, which is precisely the reading this sheet exists to
        // avoid.
        int home = 0;
        for (int row = 0; row < hulls.size(); row++) {
            if (hulls.get(row).role().landsGroundForces()) { home = row; break; }
        }
        ShipInterior best = interiors.get(home);
        for (int row = 0; row < hulls.size(); row++) {
            VanillaHullSilhouettes.Hull hull = hulls.get(row);
            ShipInterior interior = interiors.get(row);
            y += rowHeight;
            x = margin;

            g.setFont(body);
            g.setColor(LABEL);
            g.drawString(hull.id(), x, y);
            x += nameWidth;
            g.setColor(CORRIDOR);
            g.drawString(hull.hullClass().name().toLowerCase() + ", "
                    + hull.role().name().toLowerCase().replace('_', ' '), x, y);
            x += classWidth;
            g.drawString(String.valueOf(hull.lift()), x, y);
            x += liftWidth;

            for (RoomPurpose purpose : COMPARED) {
                ShipInterior.Facility facility = interior.facility(purpose);
                if (!facility.programmed()) {
                    // Absence is a fact worth reading at a glance: it is the
                    // reason to go shopping for a different hull.
                    g.setColor(STRUCTURE);
                    g.drawString("--", x, y);
                } else {
                    g.setColor(ROOM_COLORS.getOrDefault(purpose, LABEL));
                    String held = facility.capacity() > 0
                            ? String.valueOf(facility.capacity())
                            : "yes";
                    g.drawString(held + "  (" + facility.rooms() + ")", x, y);
                }
                x += cellWidth;
            }

            if (row == home) {
                g.setColor(CORRIDOR);
                g.drawString("- where they live now", x, y);
            } else {
                List<RoomPurpose> lost = new InteriorChange(best, interior).lost();
                g.setColor(lost.isEmpty() ? CORRIDOR : new Color(0xc4, 0x4b, 0x4b));
                g.drawString(lost.isEmpty() ? "nothing" : describe(lost), x, y);
            }
        }

        y += rowHeight + 18;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(CORRIDOR);
        g.drawString("Capacity is what the rooms hold - bunks, serviced hulls, hold units - "
                + "with the number of separate compartments in brackets. "
                + "\"--\" is no such place aboard. Losses are read against the "
                + "troop transport, and berthing against lift: a hull that berths "
                + "far fewer than she lifts could not fit the program she owes.",
                margin, y);

        g.dispose();
        return new SnapshotArtifact("ship-candidates.png", image);
    }

    private static String shortName(RoomPurpose purpose) {
        return switch (purpose) {
            case BARRACKS -> "BERTHING";
            case VEHICLE_BAY -> "MECH BAY";
            case PATIENT_WARD -> "SICK BAY";
            case CONFERENCE_ROOM -> "BRIEFING";
            case STOCKROOM -> "HOLD";
            case FIRING_RANGE -> "RANGE";
            case MESS_HALL -> "MESS";
            default -> purpose.name().replace('_', ' ');
        };
    }

    private static String describe(List<RoomPurpose> purposes) {
        StringBuilder text = new StringBuilder();
        for (RoomPurpose purpose : purposes) {
            if (text.length() > 0) text.append(", ");
            text.append(shortName(purpose).toLowerCase());
        }
        return text.toString();
    }

    private static SnapshotArtifact plan(String name, HullSilhouette silhouette,
                                         DeckSizing.DeckPlan deckPlan, String complement,
                                         RoomFit fit) {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult map = generator.generateDeck(deckPlan, SEED, silhouette, fit);
        BufferedImage image = renderPlan(map, generator.getLastDeckProfile(),
                generator.getLastDeckGraph(), name, deckPlan, complement, fit);
        return new SnapshotArtifact("ship-deck-" + name + ".png", image);
    }

    /**
     * One compartment of each kind, drawn large enough to see what is in it.
     *
     * <p>A deck plan answers where the rooms are; it cannot answer whether a
     * room looks like the thing it claims to be, because at deck scale a bunk
     * and a crate are the same four pixels. This is the sheet the fill is
     * actually judged on.
     */
    private static SnapshotArtifact roomDetail(SnapshotContext context,
                                               HullSilhouette silhouette,
                                               DeckSizing.DeckPlan deckPlan) {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult map = generator.generateDeck(deckPlan, SEED, silhouette, RoomFit.STANDARD);
        DeckGraph graph = generator.getLastDeckGraph();

        // One example of each purpose, largest first so the example is a
        // room of that kind rather than the smallest scrap of one.
        Map<RoomPurpose, DeckGraph.Compartment> byPurpose = new LinkedHashMap<>();
        List<DeckGraph.Compartment> ordered = new ArrayList<>(graph.compartments());
        ordered.sort(Comparator.comparingInt(DeckGraph.Compartment::area).reversed());
        for (DeckGraph.Compartment compartment : ordered) {
            byPurpose.putIfAbsent(compartment.purpose(), compartment);
        }

        int margin = 10;
        int caption = 20;
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        int columns = 3;
        int cellWidth = 0;
        int cellHeight = 0;
        for (DeckGraph.Compartment c : byPurpose.values()) {
            cellWidth = Math.max(cellWidth, (c.width() + 2) * DETAIL_CELL);
            cellHeight = Math.max(cellHeight, (c.depth() + 2) * DETAIL_CELL + caption);
        }
        int rows = (byPurpose.size() + columns - 1) / columns;
        BufferedImage image = new BufferedImage(
                margin * 2 + columns * (cellWidth + margin),
                margin * 2 + rows * (cellHeight + margin),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Java2D samples nearest-neighbour unless told otherwise, which drops
        // pixels unevenly on any sheet that is not drawn 1:1 here.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(HULL);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setFont(font);

        // The rooms are drawn by the game's own renderer rather than by a second
        // painter here. A room-authoring pass is only worth anything if what it
        // shows is what the deck will look like, and the surest way to hold that
        // is to leave no separate drawing code that can drift from it.
        HeadlessBattleSceneRenderer scenes =
                new HeadlessBattleSceneRenderer(context.modRoot());
        HeadlessUiRenderer drain = new HeadlessUiRenderer(
                    HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()), scenes);

        int index = 0;
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(map, SEED)) {
            // The vehicle bay on a home deck is the Mech Lab, so the machines
            // standing in it are the ones the company owns rather than props
            // chosen to fill the frame. A new company owns one, which is why
            // most berths in this evidence are empty.
            scene.occupyGantries(startingLance());
            for (Map.Entry<RoomPurpose, DeckGraph.Compartment> entry : byPurpose.entrySet()) {
                DeckGraph.Compartment c = entry.getValue();
                int ox = margin + (index % columns) * (cellWidth + margin);
                int oy = margin + (index / columns) * (cellHeight + margin);
                index++;

                g.setColor(LABEL);
                g.drawString(entry.getKey().name().toLowerCase().replace('_', ' ')
                        + "  " + c.width() + "x" + c.depth(), ox, oy + 13);
                int top = oy + caption;

                // One cell of surround, so the bulkhead the room was cut from is
                // visible and a door reads as a hole in something.
                int across = c.width() + 2;
                int down = c.depth() + 2;
                g.drawImage(drain.renderHostPass(
                        scene.pass(ShipDeckBattleScene.DeckView.over(
                                c.left() - 1, c.top() - 1, across, down, DETAIL_CELL)),
                        across * DETAIL_CELL, down * DETAIL_CELL), ox, top, null);

                // Doors are compartment-graph facts, not world art: the renderer
                // has no idea which opening is this room's way in. Marking them
                // over the render is the annotation this pass is read with.
                for (Doorway door : c.doors()) {
                    g.setColor(DOOR_MARK);
                    g.fillRect(ox + (door.x() - c.left() + 1) * DETAIL_CELL,
                            top + (door.y() - c.top() + 1) * DETAIL_CELL,
                            DETAIL_CELL, DETAIL_CELL);
                }
            }
        }
        g.dispose();
        return new SnapshotArtifact("ship-rooms-detail.png", image);
    }

    /**
     * The home deck's vehicle bay, framed the two ways the Mech Lab looks at it.
     *
     * <p>The lab is not a room built beside the ship; it is a camera on the
     * ship's own vehicle bay. So this evidence asks the deck where its bay is
     * and frames that compartment, rather than drawing a garage of its own —
     * which is the only way the screen and the deck can be held to the same
     * room as both keep changing.
     *
     * <p>Two panels because the screen has two poses: the whole bay, and one
     * berth close enough to fit a machine in. The close panel is drawn at
     * double the cell size rather than by resampling the wide one, so the
     * sprites stay on their own pixel grid.
     */
    private static SnapshotArtifact mechLab(SnapshotContext context,
                                            HullSilhouette silhouette,
                                            DeckSizing.DeckPlan deckPlan) {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult map = generator.generateDeck(deckPlan, SEED, silhouette, RoomFit.STANDARD);

        int margin = 10;
        int caption = 20;
        BufferedImage image;
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                map, generator.getLastDeckGraph(), SEED, null)) {
            scene.occupyGantries(startingLance());
            DeckGraph.Compartment bay = scene.room(RoomPurpose.VEHICLE_BAY);
            List<Gantry> berths = scene.berthsIn(bay);

            HeadlessBattleSceneRenderer scenes =
                    new HeadlessBattleSceneRenderer(context.modRoot());
            HeadlessUiRenderer drain = new HeadlessUiRenderer(
                    HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()), scenes);

            int wideAcross = bay.width() + 2;
            int wideDown = bay.depth() + 2;
            BufferedImage wide = drain.renderHostPass(
                    scene.pass(ShipDeckBattleScene.DeckView.over(bay, 1, DETAIL_CELL)),
                    wideAcross * DETAIL_CELL, wideDown * DETAIL_CELL);

            int fittingCell = DETAIL_CELL * 2;
            BufferedImage fitting = null;
            if (!berths.isEmpty()) {
                Gantry berth = berths.get(0);
                int across = berth.right() - berth.left() + 1 + FITTING_SURROUND * 2;
                int down = berth.top() - berth.bottom() + 1 + FITTING_SURROUND * 2;
                fitting = drain.renderHostPass(
                        scene.pass(ShipDeckBattleScene.DeckView.on(
                                berth, FITTING_SURROUND, fittingCell)),
                        across * fittingCell, down * fittingCell);
            }

            int fittingWidth = fitting == null ? 0 : fitting.getWidth();
            int fittingHeight = fitting == null ? 0 : fitting.getHeight();
            image = new BufferedImage(
                    margin * 2 + Math.max(wide.getWidth(), fittingWidth),
                    margin * 3 + caption * 2 + wide.getHeight() + fittingHeight,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(HULL);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

            g.setColor(LABEL);
            g.drawString("vehicle bay as the Mech Lab sees it  " + bay.width()
                    + "x" + bay.depth() + "  " + berths.size() + " berths, "
                    + startingLance().size() + " owned", margin, margin + 13);
            g.drawImage(wide, margin, margin + caption, null);

            int fittingTop = margin * 2 + caption + wide.getHeight();
            g.setColor(LABEL);
            g.drawString(fitting == null ? "no berths in this bay"
                    : "berth 1, fitting view", margin, fittingTop + 13);
            if (fitting != null) g.drawImage(fitting, margin, fittingTop + caption, null);
            g.dispose();
        }
        return new SnapshotArtifact("ship-deck-mech-lab.png", image);
    }

    /**
     * The lance a new company owns, straight from the campaign authority
     * that the Mech Lab screen reads.
     *
     * <p>Deliberately not a hand-written variant list. The point of showing
     * machines in the bay is to see the player's own, so the evidence takes them
     * from {@link MechBay}; a new campaign therefore photographs vacant berths.
     */
    private static List<MechVariant> startingLance() {
        CampaignMechSquad squad = new MechBay().activeSquad();
        if (squad == null) return List.of();
        return squad.mechs().stream().map(CampaignMech::variant).toList();
    }

    /**
     * The fill resolves fixtures through the tile registry, which nothing has
     * installed in a headless snapshot run. Load it from the shipped tilesets so
     * the rooms come out furnished rather than silently bare.
     */
    private static void installTileRegistry() throws Exception {
        if (TileRegistry.installed() == null) {
            TileRegistry registry = new TileRegistry();
            for (String path : TileRegistry.BUILTIN_TILESETS) {
                registry.ingestSheet(new JSONObject(Files.readString(Paths.get("mod/" + path))));
            }
            registry.validateReferences();
            TileRegistry.install(registry);
        }
        // The floor a cell shows is a mapping decision, not a render one, so the
        // preview reads the same mapping the game does rather than inventing a
        // second answer.
        if (GenMappingRegistry.installed() == null) {
            GenMappingRegistry mapping = new GenMappingRegistry();
            for (String path : GenMappingRegistry.BUILTIN_MAPPINGS) {
                mapping.ingest(new JSONObject(Files.readString(Paths.get("mod/" + path))));
            }
            mapping.validateReferences();
            GenMappingRegistry.install(mapping);
        }
    }

    private static BufferedImage renderPlan(MapResult map, DeckProfile profile, DeckGraph graph,
                                            String name, DeckSizing.DeckPlan deckPlan,
                                            String complement, RoomFit fit) {
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
                + "   widest opening " + widestOpening(map, width, height)
                + "   refit " + fit.name().toLowerCase()
                + "   fixtures " + map.doodads.size();

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
        // Java2D samples nearest-neighbour unless told otherwise, which drops
        // pixels unevenly on any sheet that is not drawn 1:1 here.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
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

        drawFixtures(g, map, margin, CELL);
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
     * Fixtures, drawn as the sprites they actually are.
     *
     * <p>Blocks were enough to check that a room was furnished and useless for
     * checking whether it looks like a room. Drawing the real art through the
     * same {@link FixedGridTileDrawer} the game uses is what makes the fill
     * judgeable — a bunk that reads as a bed, a rack that reads as a rack, and
     * a prop borrowed from the wrong set that reads as exactly that.
     *
     * <p>Falls back to a block where a sheet is missing, so evidence still comes
     * out on a machine without the art rather than failing the whole suite.
     */
    private static void drawFixtures(Graphics2D g, MapResult map, int margin, int cellPx) {
        for (Doodad doodad : map.doodads) {
            int w = Math.max(1, doodad.footprintCellsX) * cellPx;
            int h = Math.max(1, doodad.footprintCellsY) * cellPx;
            int x = margin + doodad.cellX * cellPx;
            int y = margin + doodad.cellY * cellPx;
            BufferedImage sheet = sheet(doodad.sheetPath);
            if (sheet == null) {
                g.setColor(FIXTURE);
                g.fillRect(x + 1, y + 1, w - 2, h - 2);
                g.setColor(FIXTURE_EDGE);
                g.drawRect(x + 1, y + 1, w - 2, h - 2);
                continue;
            }
            TileSink sink = new Graphics2DTileSink(g, sheet);
            new FixedGridTileDrawer(doodad.sourceCellPx).drawSpan(
                    sink, doodad.tile, doodad.footprintCellsX, doodad.footprintCellsY,
                    x + w / 2f, y + h / 2f, w, h, 1f, FixedGridTileDrawer.OVERLAY_INSET_PX);
        }
    }

    private static BufferedImage sheet(String sheetPath) {
        return SHEETS.computeIfAbsent(sheetPath, path -> {
            try {
                Path file = Paths.get("mod").resolve(path);
                return Files.isRegularFile(file) ? ImageIO.read(file.toFile()) : null;
            } catch (Exception missing) {
                return null;
            }
        });
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
