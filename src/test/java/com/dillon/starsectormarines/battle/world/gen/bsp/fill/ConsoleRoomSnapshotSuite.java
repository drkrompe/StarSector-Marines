package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayout;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayoutSeed;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.roomauthoring.RoomDraft;
import com.dillon.starsectormarines.tools.roomauthoring.RoomGridView;
import com.dillon.starsectormarines.tools.roomauthoring.RoomPreview;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Production ship and building consumers, plus the actual room-editor grid. */
public final class ConsoleRoomSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "console-rooms"; }
    @Override public String label() { return "Console banks in rooms and command buildings"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(160, 64, 42);
        DeckGraph.Compartment bridge = generator.getLastDeckGraph().compartments().stream()
                .filter(c -> c.purpose() == RoomPurpose.BRIDGE).findFirst().orElseThrow();
        long modules = deck.doodads.stream().filter(d -> bridge.contains(d.cellX, d.cellY)
                && d.definitionId().startsWith("console-bank.")).count();
        if (modules != 26) throw new IllegalStateException("Ship bridge lost console banks: " + modules);
        BufferedImage board = board();
        Graphics2D g = board.createGraphics();
        text(g, "CONSOLE BANKS / IN SERVICE", 44, 54, 28);
        text(g, "Generated ship command center", 44, 105, 22);
        g.drawImage(renderer.renderView(deck, 42, bridge.originX() + bridge.width() / 2f,
                bridge.originY() + bridge.depth() / 2f, bridge.width() + 4, bridge.depth() + 4, 44), 30, 136, null);
        text(g, "Two L banks / two U banks / nine watch positions", 44, 957, 18);
        text(g, "Square command area / two-cell inner walkway / clear hatches.", 44, 988, 18);
        text(g, "Ground command facility", 1070, 105, 22);
        MapResult ground = building(26, 21, 42, BuildingPlacement.Side.BOTTOM);
        g.drawImage(renderer.render(ground, 42, 28), 1050, 166, null);
        text(g, "Wall-backed readouts / planning table / operations annex", 1070, 957, 17);
        text(g, "Banks leave two clear rows on their working side.", 1070, 988, 17);
        text(g, "Production map renderer / 1920 x 1080 / 100% UI scale", 44, 1046, 15);
        g.dispose();
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(new SnapshotArtifact("console-rooms.png", board));
        artifacts.add(new SnapshotArtifact("command-building-shapes.png", buildings(renderer)));
        artifacts.add(new SnapshotArtifact("building-readouts.png", readouts(renderer)));
        artifacts.add(new SnapshotArtifact("building-console-templates.png", consoleTemplates(renderer)));
        RoomLayout seed = RoomLayoutSeed.from(RoomFittings.forShipRoom(RoomPurpose.BRIDGE,
                RoomRecipe.COMMAND.shape(), RoomFit.STANDARD), RoomPurpose.BRIDGE,
                RoomRecipe.COMMAND.shape(), RoomFit.STANDARD);
        artifacts.add(new SnapshotArtifact("console-room-editor.png", editor(context, seed)));
        artifacts.add(new SnapshotArtifact("ship-cic.png", cic(context, seed)));
        return artifacts;
    }

    private static BufferedImage cic(SnapshotContext context, RoomLayout seed) throws Exception {
        BufferedImage image = board();
        BufferedImage detail, backdrop;
        try (RoomPreview preview = new RoomPreview(context.projectRoot())) {
            detail = preview.render(seed, 64);
            backdrop = preview.render(seed, 38);
        }
        SwingUtilities.invokeAndWait(() -> {
            RoomGridView grid = new RoomGridView();
            grid.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            grid.show(new RoomDraft(seed)); grid.cellSize(38); grid.backdrop(backdrop);
            grid.setSize(grid.getPreferredSize());
            Graphics2D g = image.createGraphics();
            text(g, "SHIP CIC / WRAPAROUND WATCH STATIONS", 44, 55, 28);
            text(g, "Two L-shaped navigation / systems banks and two U-shaped side stations", 44, 104, 21);
            g.drawImage(detail, 44, 152, null);
            text(g, "CREW + CIRCULATION", 1190, 185, 23);
            g.translate(1190, 222); grid.paint(g); g.translate(-1190, -222);
            text(g, "Green: nine watch stands + hatch sentry", 1190, 790, 18);
            text(g, "Cyan: protected walkways", 1190, 825, 18);
            text(g, "Square central plot / two-cell inner aisle", 1190, 890, 18);
            text(g, "Original crew positions and room footprint", 1190, 925, 18);
            text(g, "Editable paths in the room editor", 1190, 960, 18);
            text(g, "Production ship-room renderer + actual room-editor overlay / 1920 x 1080 / 100% UI scale", 44, 1045, 17);
            g.dispose();
        });
        return image;
    }

    private static BufferedImage editor(SnapshotContext context, RoomLayout seed) throws Exception {
        BufferedImage image = board();
        BufferedImage backdrop;
        try (RoomPreview preview = new RoomPreview(context.projectRoot())) { backdrop = preview.render(seed, 54); }
        RoomDraft draft = new RoomDraft(seed);
        SwingUtilities.invokeAndWait(() -> {
            RoomGridView grid = new RoomGridView();
            grid.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            grid.show(draft); grid.cellSize(54); grid.backdrop(backdrop);
            grid.setSize(grid.getPreferredSize());
            Graphics2D g = image.createGraphics();
            text(g, "ROOM EDITOR / CONNECTED CONSOLE BANK", 44, 55, 28);
            text(g, "The ship fitting opens as editable bank paths", 44, 100, 20);
            g.translate(44, 155); grid.paint(g); g.translate(-44, -155);
            text(g, "ROOMS > THE FIXTURES", 1110, 190, 23);
            text(g, "1. Select Connected console bank", 1110, 245, 20);
            text(g, "2. Click or drag rows; click each corner", 1110, 285, 20);
            text(g, "3. Choose the operator side", 1110, 325, 20);
            text(g, "4. Open bank or closed island", 1110, 365, 20);
            text(g, "5. Place bank, then compare and keep", 1110, 405, 20);
            text(g, "Yellow outlines: fixture cells", 1110, 505, 18);
            text(g, "Cyan hatching: reserved circulation", 1110, 540, 18);
            text(g, "Green circles: existing work positions", 1110, 575, 18);
            text(g, "Single fixture / erase removes a whole bank", 1110, 660, 18);
            text(g, "when you click any of its occupied cells.", 1110, 690, 18);
            text(g, "Actual RoomGridView over the production renderer / 54 px per cell", 44, 1025, 17);
            g.dispose();
        });
        return image;
    }

    private static BufferedImage buildings(HeadlessBattleMapRenderer renderer) {
        BufferedImage image = board(); Graphics2D g = image.createGraphics();
        text(g, "GROUND COMMAND / FOOTPRINT REVIEW", 44, 55, 28);
        int[][] sizes = {{26, 21}, {21, 26}, {19, 17}, {17, 19}};
        BuildingPlacement.Side[] sides = BuildingPlacement.Side.values();
        for (int i = 0; i < sizes.length; i++) {
            int x = 36 + i % 2 * 950, y = 100 + i / 2 * 455;
            MapResult map = building(sizes[i][0], sizes[i][1], 42 + i, sides[i]);
            text(g, sizes[i][0] + " x " + sizes[i][1] + " / entry " + sides[i], x, y, 20);
            g.drawImage(renderer.render(map, 42 + i, 13), x, y + 22, null);
            long count = map.doodads.stream().filter(d -> d.definitionId().startsWith("console-bank.")).count();
            text(g, count + " console modules", x + 500, y + 70, 17);
            text(g, "Doors, windows and", x + 500, y + 105, 17);
            text(g, "connected floor preserved", x + 500, y + 132, 17);
        }
        text(g, "Production shell + COMMAND_CENTER fitting / 1920 x 1080", 44, 1044, 16);
        g.dispose(); return image;
    }

    private static BufferedImage consoleTemplates(HeadlessBattleMapRenderer renderer) {
        BufferedImage image = board(); Graphics2D g = image.createGraphics();
        text(g, "AUTHORED BUILDINGS / L-SHAPED OPERATIONS DESKS", 36, 58, 28);
        BuildingStamp[] stamps = {BuildingStamp.T_WORKSHOP, BuildingStamp.FREIGHT_L, BuildingStamp.FREIGHT_T};
        for (int i = 0; i < stamps.length; i++) {
            BuildingStamp stamp = stamps[i];
            int w = stamp.width + 4, h = stamp.height + 4;
            NavigationGrid grid = new NavigationGrid(w, h); CellTopology topology = new CellTopology(w, h);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, GroundKind.STREET);
            }
            GenContext ctx = new GenContext(grid, topology, new Random(42), w, h, 42);
            stamp.place(ctx, 2, 2, 0); new FinalizeStage().run(ctx); verifyConnected(grid);
            MapResult map = new MapResult(grid, topology, 0, 0, 0, 0, ctx.pois, ctx.doodads);
            int x = 24 + i * 630;
            text(g, stamp.label, x, 145, 22);
            g.drawImage(renderer.render(map, 42, 26), x, 185, null);
            text(g, "Connected L bank + mess-hall stools", x, 865, 19);
            text(g, i == 0 ? "Used by procedural industrial lots" : "Used by authored story facilities", x, 905, 18);
            text(g, "Doors, windows and exterior cuts preserved", x, 945, 17);
        }
        text(g, "Production building templates and battle renderer / 1920 x 1080", 36, 1045, 17);
        g.dispose(); return image;
    }

    private static MapResult building(int w, int h, long seed, BuildingPlacement.Side frontage) {
        return building(w, h, seed, frontage, new BuildingShellCore.BuildingConfig(GroundKind.INDOOR, "SKY_PORT",
                PointOfInterest.Kind.COMMS, BuildingLayouts.LayoutRecipe.COMMAND_CENTER, BuildingKind.FORTIFIED,
                new RoomPurpose[]{RoomPurpose.KEEP_THRONE, RoomPurpose.KEEP_INNER, RoomPurpose.KEEP_ENTRY},
                TernaryPartitionStrategy.DEFAULT));
    }

    private static BufferedImage readouts(HeadlessBattleMapRenderer renderer) {
        BufferedImage image = board(); Graphics2D g = image.createGraphics();
        text(g, "BUILDING READOUTS / SEATED WORKSTATIONS", 44, 50, 28);
        String[] names = {"Command facility", "Industrial control room", "Civic server room", "Clinic monitoring"};
        BuildingShellCore.BuildingConfig[] configs = {null, BuildingIndustrialFiller.CONFIG,
                BuildingCivicFiller.CONFIG, MedicalCampusFiller.CLINIC_CONFIG};
        for (int i = 0; i < configs.length; i++) {
            int x = 36 + i % 2 * 950, y = 90 + i / 2 * 480;
            MapResult map = null;
            for (BuildingPlacement.Side side : BuildingPlacement.Side.values()) {
                MapResult candidate = configs[i] == null ? building(26, 21, 42, side)
                        : building(26, 21, 42, side, configs[i]);
                if (side == BuildingPlacement.Side.BOTTOM) map = candidate;
            }
            long modules = map.doodads.stream().filter(d -> d.definitionId().startsWith("console-bank.")).count();
            long seats = map.doodads.stream().filter(d -> d.definitionId().equals("mess-galley.stool")).count();
            if (modules == 0 || seats == 0) throw new IllegalStateException(names[i] + " has no seated console bank");
            text(g, names[i], x, y, 22);
            g.drawImage(renderer.render(map, 42, 17), x, y + 20, null);
            text(g, modules + " console modules", x + 550, y + 115, 19);
            text(g, seats + " mess-hall stools", x + 550, y + 150, 19);
            text(g, "Working-side seats", x + 550, y + 215, 18);
            text(g, "Clear service aisles", x + 550, y + 250, 18);
            text(g, "All four entrances checked", x + 550, y + 285, 18);
        }
        g.dispose(); return image;
    }

    private static MapResult building(int w, int h, long seed, BuildingPlacement.Side frontage,
                                      BuildingShellCore.BuildingConfig config) {
        int width = w + 4, height = h + 4;
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, GroundKind.STREET);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(seed), width, height, seed);
        BuildingShellCore.carve(new BlockLeaf(2, 2, w + 1, h + 1, false), grid, topology, ctx.doodads,
                new Random(seed), config, new BuildingPlacement(frontage, true));
        new FinalizeStage().run(ctx);
        verifyConnected(grid);
        for (var d : ctx.doodads) if (d.definitionId().startsWith("console-bank.")) {
            if (grid.isWalkable(d.cellX, d.cellY) || !topology.isFixture(d.cellX, d.cellY)
                    || topology.isWall(d.cellX, d.cellY)) throw new IllegalStateException("Console is not a fixture");
        }
        return new MapResult(grid, topology, 0, 0, 0, 0, ctx.pois, ctx.doodads);
    }

    private static void verifyConnected(NavigationGrid grid) {
        boolean[][] seen = new boolean[grid.getWidth()][grid.getHeight()];
        ArrayDeque<int[]> queue = new ArrayDeque<>(); queue.add(new int[]{0, 0}); seen[0][0] = true;
        int reached = 0, total = 0;
        for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++)
            if (grid.isWalkable(x, y)) total++;
        while (!queue.isEmpty()) {
            int[] c = queue.removeFirst(); reached++;
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int x = c[0] + d[0], y = c[1] + d[1];
                if (grid.inBounds(x, y) && !seen[x][y] && grid.canTraverseCellStep(c[0], c[1], x, y)) {
                    seen[x][y] = true; queue.add(new int[]{x, y});
                }
            }
        }
        if (total != reached) throw new IllegalStateException("Command building stranded " + (total - reached) + " floor cells");
    }

    private static BufferedImage board() {
        BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.setColor(new Color(0x111A22)); g.fillRect(0, 0, 1920, 1080); g.dispose();
        return image;
    }
    private static void text(Graphics2D g, String text, int x, int y, int size) {
        g.setColor(new Color(0xD4E3DF)); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size)); g.drawString(text, x, y);
    }
}
