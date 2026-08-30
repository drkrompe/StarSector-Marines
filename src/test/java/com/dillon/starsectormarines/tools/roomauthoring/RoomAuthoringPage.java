package com.dillon.starsectormarines.tools.roomauthoring;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayout;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayoutCheck;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayoutCatalog;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayoutSeed;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayouts;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.authoring.wizard.LambdaStep;
import com.dillon.starsectormarines.tools.authoring.wizard.Wizard;
import com.dillon.starsectormarines.tools.authoring.wizard.WizardStep;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Editing one shipboard room: its footprint, its deck, and what stands on it.
 *
 * <p>Opens on a question rather than on a workspace — <b>which room?</b> — and
 * then walks numbered screens, each holding only the controls its own step
 * needs. That shape is borrowed from the Tilesets page and for the same reason:
 * a single toolbar carrying every command at once is a palette for somebody who
 * already knows the procedure and nothing at all for somebody who does not.
 *
 * <p>No room ever starts from an empty grid. Opening one runs the procedural
 * fitting that owns it and records what it did, so the first thing an author
 * sees is the room that already ships, and the first edit is a change to it
 * rather than a reconstruction of it.
 *
 * <p>The last screen is the one that matters: the same hull at the same seed,
 * generated twice and rendered through the game's own renderer, so what is being
 * accepted is the actual difference the edit makes rather than a promise about
 * it.
 */
public final class RoomAuthoringPage implements AuthoringPage {

    private static final String CHOOSER = "chooser";
    private static final String WIZARD = "wizard";

    private final AuthoringPageContext context;
    private final JPanel root = new JPanel(new BorderLayout(0, 8));
    private final JPanel screens = new JPanel(new CardLayout());
    private final Wizard wizard;

    private final RoomChooserGrid rooms = new RoomChooserGrid();
    private final JComboBox<RoomFit> level = new JComboBox<>(RoomFit.values());

    private final RoomGridView grid = new RoomGridView();
    private final ImagePanel before = new ImagePanel("as it ships");
    private final ImagePanel after = new ImagePanel("with this edit");
    private final JLabel tally = new JLabel(" ");

    private final JComboBox<String> fixtureId = new JComboBox<>();
    private final JComboBox<Affordance> affordance = new JComboBox<>();
    /**
     * The deck a shipboard room can be painted as.
     *
     * <p>A short list rather than every {@code GroundKind}, because most of that
     * vocabulary is outdoor ground: a room whose floor was set to GRASS would be
     * accepted by everything here and be a lawn in the middle of a warship.
     */
    private final JComboBox<GroundKind> groundKind = new JComboBox<>(new GroundKind[]{
            GroundKind.INDOOR, GroundKind.STRIPED, GroundKind.TILE, GroundKind.BRICK });
    /**
     * What this room's bulkhead is made of.
     *
     * <p>Offers only blocks that can actually be a wall. A variant pool cannot
     * resolve a corner, so a room pointed at one draws the deck's own wall on
     * every cell and looks exactly like a room that never asked — a failure with
     * nothing anywhere to say it happened.
     */
    private final JComboBox<String> bulkhead = new JComboBox<>();

    /**
     * What a run of this room's deck is drawn from.
     *
     * <p>Offers every block that is not a wall, which is the honest test: a
     * vent plate, hazard striping and a variant pool of floor tiles are all
     * things a deck can be, and only a corner-resolving wall is not.
     */
    private final JComboBox<String> floorBlock = new JComboBox<>();

    private final JSpinner footprintWidth = new JSpinner(new SpinnerNumberModel(8, 1, 64, 1));
    private final JSpinner footprintHeight = new JSpinner(new SpinnerNumberModel(6, 1, 64, 1));

    private DeckWorkshop workshop;
    /** Draws the room on its own, for the backdrop the editing grid marks up. */
    private final RoomPreview preview;
    /**
     * Bumped on every edit; a finished render whose stamp is stale is dropped.
     *
     * <p>Clicks arrive far faster than a room draws, so without this the grid
     * would flicker between the picture for the click before last and the one
     * before that.
     */
    private int editStamp;
    private RoomDraft draft;
    private boolean dirty;
    /** Set while the ship is being generated, so a second request does not stack. */
    private boolean loading;

    public RoomAuthoringPage(AuthoringPageContext context) throws Exception {
        this.context = context;
        this.wizard = new Wizard(this::backToChooser, context::reportStatus);

        // The catalogs the game installs at application load. Nothing installs
        // them in the workbench, and without them a fixture resolves to nothing
        // — so this page would open on a list of rooms it could not furnish.
        DiskRegistries.install(context.projectRoot());
        this.preview = new RoomPreview(context.projectRoot());
        // What has already been authored, so a room opens on the arrangement
        // that was last kept rather than on the one its fitting produces.
        RoomLayouts.install(AuthoredRooms.read(context.projectRoot()));

        affordance.addItem(null);
        for (Affordance value : Affordance.values()) affordance.addItem(value);
        for (String id : placeableIds()) fixtureId.addItem(id);
        bulkhead.addItem(DECK_BULKHEAD);
        for (String id : wallBlockIds()) bulkhead.addItem(id);
        for (String id : floorBlockIds()) floorBlock.addItem(id);
        bulkhead.addActionListener(e -> {
            if (draft == null) return;
            Object picked = bulkhead.getSelectedItem();
            draft.bulkhead(DECK_BULKHEAD.equals(picked) ? null : (String) picked);
            touched();
        });

        rooms.onOpen(purpose -> openSelectedRoom());

        screens.add(chooserScreen(), CHOOSER);
        screens.add(wizard, WIZARD);
        root.add(screens, BorderLayout.CENTER);
        showChooser();
    }

    /**
     * Every doodad the catalog actually has, because a fitting may only ever
     * name an id the registry already holds.
     *
     * <p>A missing id makes placement return false silently, so a room whose kit
     * half-exists comes out bare with nothing to say it went wrong. Offering the
     * catalog rather than a text field is how that is made unreachable instead
     * of merely documented.
     */
    private static List<String> placeableIds() {
        List<String> ids = new ArrayList<>();
        for (DoodadDef def : TileRegistry.installed().doodads()) ids.add(def.id);
        ids.sort(String::compareTo);
        return ids;
    }

    /** The entry that means "whatever the rest of the ship uses". */
    private static final String DECK_BULKHEAD = "(the deck's own)";

    /**
     * Every block in the catalog that can be a wall.
     *
     * <p>Shape rather than name: a wall is a block whose layout resolves a
     * corner, which is what {@code WALL_3X3} means. Listing by id would have to
     * guess from spelling, and {@code road.embankment} is a wall.
     */
    private static List<String> wallBlockIds() {
        List<String> ids = new ArrayList<>();
        for (GridBlockDef block : TileRegistry.installed().blocks()) {
            if (block.layout == GridLayout.WALL_3X3) ids.add(block.id);
        }
        ids.sort(String::compareTo);
        return ids;
    }

    /**
     * Every block a deck can be drawn from: everything that is not a wall.
     *
     * <p>Stated as the complement rather than as a list of floor layouts,
     * because the ways of being a floor keep growing — a variant pool, a
     * hollow perimeter, a striped run, a single tile — and a picker that
     * enumerated them would quietly omit the next one somebody authored.
     */
    private static List<String> floorBlockIds() {
        List<String> ids = new ArrayList<>();
        for (GridBlockDef block : TileRegistry.installed().blocks()) {
            if (block.layout != GridLayout.WALL_3X3) ids.add(block.id);
        }
        ids.sort(String::compareTo);
        return ids;
    }

    // ---- screen zero: which room? -------------------------------------------

    private JComponent chooserScreen() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel heading = new JLabel("What are you editing?");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        JLabel blurb = new JLabel("<html>Pick a room aboard the ship. Each one is drawn as it "
                + "generates today — its own fitting, plus whatever has been authored for it — "
                + "so opening one confirms what you can already see. Double-click to open.</html>");

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(heading);
        head.add(Box.createVerticalStrut(4));
        head.add(blurb);
        panel.add(head, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(rooms);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel foot = new JPanel(new BorderLayout(8, 0));
        JPanel levelRow = new JPanel();
        levelRow.add(new JLabel("Fitted at:"));
        levelRow.add(level);
        level.addActionListener(e -> {
            rooms.clear();
            loadRooms();
        });
        foot.add(levelRow, BorderLayout.WEST);

        JButton open = new JButton("Open this room");
        open.addActionListener(e -> openSelectedRoom());
        foot.add(open, BorderLayout.EAST);
        panel.add(foot, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Fill the room list from the ship herself, so it lists rooms that exist.
     *
     * <p>Off the event thread, because filling it means generating a whole deck.
     * Doing that inline froze the window on the way in, before the page had
     * drawn anything to explain itself.
     */
    private void loadRooms() {
        if (loading) return;
        loading = true;
        rooms.clear();
        context.reportStatus("Generating the ship to see what rooms she has\u2026");
        RoomFit fit = (RoomFit) level.getSelectedItem();
        new Thread(() -> {
            DeckWorkshop built = null;
            String failed = null;
            try {
                built = new DeckWorkshop(context.projectRoot(), context.starsectorCoreRoot(), fit);
                List<RoomPurpose> aboard = built.purposesAboard();
                DeckWorkshop ready = built;
                SwingUtilities.invokeLater(() -> {
                    if (workshop != null) workshop.close();
                    workshop = ready;
                    rooms.expect(aboard);
                    context.reportStatus("The ship carries " + aboard.size()
                            + " kinds of room. Drawing them\u2026");
                });
                // Each picture is handed over as it is finished, so the grid
                // fills in rather than staying empty until the last one.
                built.eachRoom(TILE_CELL_PX, 1, (purpose, picture) ->
                        SwingUtilities.invokeLater(() -> rooms.show(new RoomChooserGrid.Tile(
                                purpose, picture, sizeOf(purpose), isAuthored(purpose, fit)))));
            } catch (Exception failure) {
                failed = failure.getMessage() == null
                        ? failure.toString() : failure.getMessage();
            }
            String why = failed;
            SwingUtilities.invokeLater(() -> {
                loading = false;
                if (why != null) context.reportStatus("Could not generate the ship: " + why);
                else context.reportStatus("Pick a room, or double-click to open it.");
            });
        }, "room-list").start();
    }

    /** How big a tile's picture is drawn; small enough that twenty fit, large enough to read. */
    private static final int TILE_CELL_PX = 10;

    /** The size the ship laid this room down at, for the tile's caption. */
    private String sizeOf(RoomPurpose purpose) {
        return workshop == null ? "" : workshop.sizeOf(purpose);
    }

    /** Whether somebody has already authored this room at this level. */
    private static boolean isAuthored(RoomPurpose purpose, RoomFit fit) {
        for (RoomLayout layout : RoomLayouts.installed().all()) {
            if (layout.purpose() == purpose && layout.fit() == fit) return true;
        }
        return false;
    }

    private void openSelectedRoom() {
        RoomPurpose purpose = rooms.picked();
        if (purpose == null) {
            context.reportStatus("Pick a room to open.");
            return;
        }
        if (workshop == null) {
            context.reportStatus("The ship is still being generated.");
            return;
        }
        RoomShape shape = shapeOf(purpose);
        if (shape == null) {
            context.reportStatus("The ship has no " + purpose + " to copy.");
            return;
        }
        RoomLayout seed = RoomLayoutSeed.from(purpose, shape, (RoomFit) level.getSelectedItem());
        if (seed == null) {
            context.reportStatus(purpose + " has no fitting to copy, so there is nothing to seed.");
            return;
        }
        draft = new RoomDraft(seed);
        dirty = false;
        String wall = draft.bulkhead();
        bulkhead.setSelectedItem(wall == null ? DECK_BULKHEAD : wall);
        footprintWidth.setValue(draft.width());
        footprintHeight.setValue(draft.height());
        grid.show(draft);
        grid.backdrop(null);
        redrawBackdrop();
        wizard.start(walkthrough());
        ((CardLayout) screens.getLayout()).show(screens, WIZARD);
    }

    /**
     * The footprint this room is generated at today, or null when the ship has
     * no such room.
     *
     * <p>Null rather than a stand-in size. A layout is bound to its footprint,
     * so seeding one against a made-up ten by eight would produce a document
     * that silently matches no room on any deck.
     */
    private RoomShape shapeOf(RoomPurpose purpose) {
        return workshop == null ? null : workshop.footprintOf(purpose);
    }

    private void showChooser() {
        if (rooms.picked() == null) loadRooms();
        ((CardLayout) screens.getLayout()).show(screens, CHOOSER);
    }

    private void backToChooser() {
        ((CardLayout) screens.getLayout()).show(screens, CHOOSER);
    }

    // ---- the walkthrough -----------------------------------------------------

    private List<WizardStep> walkthrough() {
        return List.of(
                new LambdaStep("The footprint",
                        "Click a cell to add or remove deck. This is the shape the packer "
                                + "lays into the hull, so changing it changes where the room "
                                + "can go — and whether it still fits.",
                        this::footprintBody)
                        .onEnter(() -> grid.onClick(this::toggleCell)),

                new LambdaStep("The deck",
                        "Paint the floor, and reserve the lanes people walk down. A lane is "
                                + "authored before furniture and nothing may be placed on it, "
                                + "which is what keeps a furnished room walkable from its door.",
                        this::deckBody)
                        .onEnter(() -> grid.onClick(this::paintCell)),

                new LambdaStep("The fixtures",
                        "Click to stand the chosen fixture on a cell, or right of the grid "
                                + "pick another. A fixture that affords work publishes it, and "
                                + "the count of fixtures is what the room provides.",
                        this::fixtureBody)
                        .onEnter(() -> {
                            grid.onClick(this::placeFixture);
                            updateTally();
                        }),

                new LambdaStep("Compare, then keep it",
                        "The same hull at the same seed, generated twice. Everything that "
                                + "differs between these two pictures is your edit.",
                        this::compareBody)
                        .onEnter(() -> {
                            updateTally();
                            renderComparison();
                        })
                        .nextLabel("Done")
                        .last());
    }

    private JComponent footprintBody() {
        JPanel side = column();
        side.add(new JLabel("Bounding box"));
        JPanel size = new JPanel();
        size.add(new JLabel("w"));
        size.add(footprintWidth);
        size.add(new JLabel("h"));
        size.add(footprintHeight);
        side.add(size);

        JButton resize = new JButton("Resize");
        resize.addActionListener(e -> {
            draft.resize((Integer) footprintWidth.getValue(), (Integer) footprintHeight.getValue());
            touched();
            grid.show(draft);
        });
        side.add(resize);
        side.add(Box.createVerticalStrut(8));
        side.add(new JLabel("<html><i>A room drawn larger than the one it replaces "
                + "may no longer fit the hull. The last screen is where you find "
                + "that out.</i></html>"));
        return split(side);
    }

    private JComponent deckBody() {
        JPanel side = column();
        side.add(new JLabel("Paint this cell with"));
        side.add(floorBlock);
        side.add(new JLabel("<html><i>What the deck is drawn from. Flavour only — "
                + "a vent run and a striped run are the same floor to everything "
                + "that is not the renderer.</i></html>"));
        side.add(Box.createVerticalStrut(10));

        side.add(new JLabel("…and mark its kind as"));
        side.add(groundKind);
        side.add(new JLabel("<html><i>This one is real topology, which consumers "
                + "read. Leave it alone unless the deck genuinely changed.</i></html>"));
        side.add(Box.createVerticalStrut(10));

        JButton kind = new JButton("Set the kind here");
        kind.addActionListener(e -> {
            grid.onClick((x, y) -> {
                draft.paintGround(x, y, 1, 1, (GroundKind) groundKind.getSelectedItem());
                touched();
            });
            context.reportStatus("Clicks now set the ground kind. Pick the floor block "
                    + "above to go back to painting.");
        });
        floorBlock.addActionListener(e -> grid.onClick(this::paintCell));
        side.add(kind);
        side.add(Box.createVerticalStrut(10));

        JButton lane = new JButton("Reserve a lane across");
        lane.addActionListener(e -> {
            draft.reserveLane(0, draft.height() / 2, draft.width(), 2);
            touched();
            grid.show(draft);
        });
        side.add(lane);

        JButton clear = new JButton("Clear reserved lanes");
        clear.addActionListener(e -> {
            draft.clearLanes();
            touched();
            grid.show(draft);
            context.reportStatus("The room's reserved circulation is back to open deck. "
                    + "Nothing can be placed on a lane, so this is how you make room.");
        });
        side.add(clear);
        side.add(Box.createVerticalStrut(12));

        side.add(new JLabel("Bulkhead"));
        side.add(bulkhead);
        side.add(new JLabel("<html><i>The wall all the way round this room. A shared "
                + "bulkhead is one wall, so where this room backs onto another the "
                + "later one wins the cells between them.</i></html>"));
        side.add(Box.createVerticalStrut(8));
        side.add(new JLabel("<html><i>Circulation is two abreast on both axes — "
                + "a hall widened only across its direction of travel pinches back "
                + "to one cell at every corner.</i></html>"));
        return split(side);
    }

    private JComponent fixtureBody() {
        JPanel side = column();
        side.add(new JLabel("Fixture"));
        side.add(fixtureId);
        side.add(Box.createVerticalStrut(6));
        side.add(new JLabel("Work done at it"));
        side.add(affordance);
        side.add(Box.createVerticalStrut(8));

        side.add(new JLabel("<html><i>Clicking a cell that already carries "
                + "something takes it away, so one control both places and "
                + "corrects.</i></html>"));
        return split(side);
    }

    private JComponent compareBody() {
        JPanel pair = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        pair.add(before);
        pair.add(after);

        // The save lives here rather than on Next, because this is the screen
        // where the thing being agreed to is actually on display. A walkthrough
        // whose last button committed from somewhere else would be asking for
        // consent to something the operator had not been shown.
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(pair, BorderLayout.CENTER);

        JPanel foot = new JPanel(new BorderLayout(8, 0));
        JButton keep = new JButton("Keep this room");
        keep.addActionListener(e -> save());
        JButton again = new JButton("Draw it again");
        again.addActionListener(e -> renderComparison());
        JPanel buttons = new JPanel();
        buttons.add(again);
        buttons.add(keep);
        foot.add(tally, BorderLayout.WEST);
        foot.add(buttons, BorderLayout.EAST);
        panel.add(foot, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel column() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));
        side.setPreferredSize(new Dimension(260, 100));
        return side;
    }

    private JComponent split(JComponent side) {
        JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(grid), side);
        pane.setResizeWeight(1.0);
        return pane;
    }

    // ---- what a click means on each screen -----------------------------------

    private void toggleCell(int x, int y) {
        draft.toggleCell(x, y);
        touched();
    }

    /**
     * Painting a cell draws it from the chosen block.
     *
     * <p>The block and not the kind, because that is what a person painting a
     * floor means. Changing the kind is a separate, deliberate act — it moves
     * something the game reads rather than something it shows.
     */
    private void paintCell(int x, int y) {
        draft.floor(x, y, 1, 1, (String) floorBlock.getSelectedItem());
        touched();
    }

    private void placeFixture(int x, int y) {
        // Clicking a cell that already carries something takes it away, so one
        // control both places and corrects rather than needing a mode switch.
        if (!draft.removeAt(x, y)) {
            if (draft.isLane(x, y)) {
                context.reportStatus("That cell is reserved circulation — a fixture put there "
                        + "is refused when the ship generates. Clear the lanes first.");
                return;
            }
            draft.addFixture(x, y, (String) fixtureId.getSelectedItem(),
                    (Affordance) affordance.getSelectedItem());
        }
        touched();
        updateTally();
    }

    /**
     * What this room would actually do, rather than what was asked of it.
     *
     * <p>Both ways an authored room fails are silent: a fixture whose cell is
     * taken is refused and the room comes out sparser, and an arrangement that
     * severs its own circulation has its whole fill thrown away. A count of what
     * was clicked would report fifteen fixtures for a room that generates empty
     * — which is exactly what happened the first time this was tried.
     */
    private void updateTally() {
        RoomLayout layout = draft.layout();
        RoomLayoutCheck.Report report = RoomLayoutCheck.replay(layout);
        List<RoomPose> poses = layout.posesThatFit();

        StringBuilder said = new StringBuilder("<html><b>").append(report.placed())
                .append("</b> fixtures stand up — this room's capacity.");
        if (poses.size() < RoomPose.all().size()) {
            said.append("<br>Survives ").append(poses.size()).append(" of ")
                    .append(RoomPose.all().size()).append(" poses — a prop's footprint does "
                            + "not turn with the room, so this one comes out sparser on "
                            + "some decks.");
        }
        for (String complaint : report.complaints()) {
            said.append("<br><b>·</b> ").append(complaint);
        }
        tally.setText(said.append("</html>").toString());
    }

    private void touched() {
        dirty = true;
        context.stateChanged();
        grid.repaint();
        redrawBackdrop();
    }

    /**
     * Draw the room again behind the marks.
     *
     * <p>Off the event thread, and dropped if another edit landed while it was
     * drawing — the grid keeps the last good picture rather than blanking, so
     * editing stays continuous while the art catches up.
     */
    private void redrawBackdrop() {
        if (draft == null) return;
        int stamp = ++editStamp;
        RoomLayout snapshot = draft.layout();
        int cellPx = grid.cellPx();
        new Thread(() -> {
            BufferedImage picture;
            try {
                picture = preview.render(snapshot, cellPx);
            } catch (Exception failure) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (stamp == editStamp) grid.backdrop(picture);
            });
        }, "room-backdrop").start();
    }

    // ---- the comparison, and keeping it --------------------------------------

    /**
     * Draw the room both ways.
     *
     * <p>Off the event thread, because each side is a whole ship generated and
     * rendered. Doing it inline froze the window for as long as a deck takes.
     */
    private void renderComparison() {
        before.waiting();
        after.waiting();
        RoomLayout edited = draft.layout();
        RoomPurpose purpose = draft.purpose();
        new Thread(() -> {
            BufferedImage shipped = workshop.render(null, purpose, 24, 1);
            BufferedImage changed = workshop.render(edited, purpose, 24, 1);
            SwingUtilities.invokeLater(() -> {
                before.show(shipped);
                after.show(changed);
                if (changed == null) {
                    context.reportStatus("This room no longer fits the hull — "
                            + "the deck was generated without it.");
                }
            });
        }, "room-comparison").start();
    }

    /**
     * Write the room.
     *
     * <p>Atomically, and validated first, as the other pages do: a half-written
     * document is a startup failure rather than a bad edit, and the file being
     * replaced is one the game loads.
     */
    private void save() {
        try {
            RoomLayout layout = draft.layout();
            for (String id : layout.doodadIds()) {
                if (TileRegistry.installed().doodad(id) == null) {
                    context.reportStatus("Refused: the catalog has no '" + id + "'.");
                    return;
                }
            }
            RoomLayoutCheck.Report report = RoomLayoutCheck.replay(layout);
            if (report.badBulkhead() != null) {
                context.reportStatus("Refused: " + report.badBulkhead());
                return;
            }
            if (!report.circulationSurvives()) {
                JOptionPane.showMessageDialog(root,
                        "This arrangement severs the room's own circulation, so the ship "
                                + "would discard the whole fill and generate bare deck. "
                                + "Nothing was written.",
                        "The room would come out empty", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String name = AuthoredRooms.write(context.projectRoot(), layout);
            // Installed as well as written, so the grid's picture and the next
            // comparison reflect what was just kept rather than what it replaced.
            RoomLayouts.install(AuthoredRooms.read(context.projectRoot()));

            dirty = false;
            context.stateChanged();
            context.reportStatus("Kept " + name + " — " + layout.provides()
                    + " fixtures. The ship will generate it from now on.");
        } catch (Exception failure) {
            JOptionPane.showMessageDialog(root, "Could not write the room: " + failure.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** One of the two comparison pictures, or a word about why there is none. */
    private static final class ImagePanel extends JComponent {

        private final String caption;
        private BufferedImage image;
        private String note = "";

        ImagePanel(String caption) {
            this.caption = caption;
        }

        void waiting() {
            image = null;
            note = "generating the ship…";
            repaint();
        }

        void show(BufferedImage image) {
            this.image = image;
            this.note = image == null ? "this room did not fit the hull" : "";
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(new java.awt.Color(0x10, 0x16, 0x1e));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new java.awt.Color(0xe4, 0xec, 0xf4));
            g.drawString(caption, 8, 16);
            if (image == null) {
                g.drawString(note, 8, 36);
                return;
            }
            // Fitted rather than cropped: the two pictures are read side by side
            // and a room that changed size has to stay comparable.
            double scale = Math.min((getWidth() - 16.0) / image.getWidth(),
                    (getHeight() - 32.0) / image.getHeight());
            scale = Math.min(1.0, Math.max(0.05, scale));
            int w = (int) (image.getWidth() * scale);
            int h = (int) (image.getHeight() * scale);
            g.drawImage(image, (getWidth() - w) / 2, 24 + (getHeight() - 24 - h) / 2, w, h, null);
        }
    }

    // ---- page lifecycle ------------------------------------------------------

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public boolean hasUnsavedChanges() {
        return dirty;
    }

    @Override
    public void close() {
        if (workshop != null) workshop.close();
        preview.close();
    }

    /** For the test that walks the screens without a window. */
    Wizard wizard() {
        return wizard;
    }

    /** For the test, and for the Save button the last step's Next becomes. */
    void saveNow() {
        save();
    }

    RoomDraft draft() {
        return draft;
    }
}
