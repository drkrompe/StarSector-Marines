package com.dillon.starsectormarines.tools.forestauthoring;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.setup.ConquestForestFixture;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.CounterattackDepot;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import com.dillon.starsectormarines.battle.world.gen.LastLiftCompound;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ReliefCheckpoint;
import com.dillon.starsectormarines.battle.world.gen.RelayNineCompound;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/** Production map matrix with a same-seed no-landscape control, outside the ordinary test suite. */
public final class OutdoorForestSnapshotSuite implements SnapshotSuite {
    private static final String CONTROL = "battle.mapgen.outdoorLandscape";
    public String id() { return "outdoor-forests"; }
    public String label() { return "Outdoor forests, wooded hills and preserved mission access"; }

    public List<SnapshotArtifact> render(SnapshotContext c) throws Exception {
        DiskRegistries.installMapGeneration(c.projectRoot());
        var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(c.modRoot()), c.modRoot());
        List<SnapshotArtifact> frames = new ArrayList<>();
        int cases = 0, crowns = 0, summitCrowns = 0, refusedControls = 0;
        String previous = System.getProperty(CONTROL);
        try {
            for (SurfacePalette surface : SurfacePalette.values()) {
                TargetProfile profile = new TargetProfile(4, 5, 2, 1, "independent", Set.of(), surface, SettlementLink.ROAD);
                for (String family : List.of("relief", "counterattack", "junction", "last-lift", "relay-nine", "thunder-run",
                        "city", "conquest-west", "conquest-south", "precinct")) {
                    long seed = 9301;
                    Supplier<MapResult> generate = () -> generate(family, seed, profile);
                    System.setProperty(CONTROL, "false"); MapResult before = generate.get();
                    System.setProperty(CONTROL, "true"); MapResult after = generate.get();
                    verify(before, after, surface);
                    int count = (int)after.doodads.stream().filter(OutdoorForestSnapshotSuite::tree).count();
                    int summits = (int)after.doodads.stream().filter(OutdoorForestSnapshotSuite::tree)
                            .filter(d -> ForestBrush.isGrassySummit(after.grid, after.topology,
                                    d.cellX + d.footprintCellsX / 2, d.cellY + d.footprintCellsY / 2)).count();
                    System.out.println("[outdoor-forests] " + family + " / " + surface + ": " + count
                            + " crowns, " + summits + " on summits; no new islands, required places and berths retained");
                    cases++; crowns += count; summitCrowns += summits;
                    if (surface == SurfacePalette.VERDANT && count == 0)
                        throw new IllegalStateException("Living outdoor family has no forests: " + family);
                    if (surface == SurfacePalette.VERDANT || family.equals("junction")) {
                        try (var scene = new ShipDeckBattleScene(after, 0)) {
                            frames.add(new SnapshotArtifact(family + "-" + surface.name().toLowerCase() + ".png",
                                    renderer.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.fitting(0, 0,
                                            after.grid.getWidth(), after.grid.getHeight(), 1920, 1080)), 1920, 1080)));
                            if (family.equals("junction") && surface == SurfacePalette.VERDANT)
                                frames.add(new SnapshotArtifact("wooded-hills.png", renderer.renderHostPass(scene.pass(
                                        ShipDeckBattleScene.DeckView.fitting(0, 62, 96, 54, 1920, 1080)), 1920, 1080)));
                        }
                    }
                }
            }
            // More shapes of living terrain without paying to render all of them.
            for (long seed : new long[]{9302, 9303}) {
                var profile = new TargetProfile(4, 5, 2, 1, "independent", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
                for (String family : List.of("junction", "thunder-run", "precinct")) {
                    System.setProperty(CONTROL, "false");
                    MapResult before;
                    try { before = generate(family, seed, profile); }
                    catch (IllegalArgumentException ex) {
                        // An input that fails without the feature is not a passing comparison.
                        System.out.println("[outdoor-forests] BASELINE REFUSED " + family + " seed " + seed + ": " + ex.getMessage());
                        refusedControls++; continue;
                    }
                    System.setProperty(CONTROL, "true"); MapResult after = generate(family, seed, profile);
                    verify(before, after, SurfacePalette.VERDANT); cases++;
                }
            }
            var living = new TargetProfile(7, 5, 6, 1, "independent", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
            for (var axis : TraversalAxis.values()) {
                System.setProperty(CONTROL, "false"); MapResult before = ConquestForestFixture.generate(living, axis);
                System.setProperty(CONTROL, "true"); MapResult after = ConquestForestFixture.generate(living, axis);
                verify(before, after, SurfacePalette.VERDANT); cases++;
                if (after.lanes != null) for (var lane : after.lanes) for (var cell : lane.route())
                    if (!after.grid.isWalkable(cell.x(), cell.y())) throw new IllegalStateException("Conquest lane blocked");
                try (var scene = new ShipDeckBattleScene(after, 0)) {
                    frames.add(new SnapshotArtifact("full-conquest-" + axis.name().toLowerCase() + ".png", renderer.renderHostPass(
                            scene.pass(ShipDeckBattleScene.DeckView.fitting(0, 0, 560, 336, 1920, 1080)), 1920, 1080)));
                }
                System.out.println("[outdoor-forests] Full Conquest " + axis + ": routes, berths and components retained");
            }
            MapResult station = new BspCityGenerator().generateStation(96, 64, 9301);
            if (station.doodads.stream().anyMatch(OutdoorForestSnapshotSuite::tree) || station.topology.terrainSurface() != null)
                throw new IllegalStateException("Outdoor landscape leaked into a station");
            if (summitCrowns == 0) throw new IllegalStateException("No wooded hilltops in the matrix");
            System.out.println("[outdoor-forests] PASS: " + cases + " paired outdoor maps + station control; "
                    + crowns + " crowns including " + summitCrowns + " summit crowns in the rendered matrix; "
                    + refusedControls + " extra seed(s) refused by baseline building generation.");
        } finally {
            if (previous == null) System.clearProperty(CONTROL); else System.setProperty(CONTROL, previous);
        }
        return frames;
    }

    static MapResult generate(String family, long seed, TargetProfile profile) {
        return switch (family) {
            case "relief" -> ReliefCheckpoint.generate(ReliefCheckpoint.WIDTH, ReliefCheckpoint.HEIGHT, seed, profile).map();
            case "counterattack" -> CounterattackDepot.generate(seed, profile).map();
            case "junction" -> JunctionCompound.generate(seed, profile).map();
            case "last-lift" -> LastLiftCompound.generate(seed, profile, 12).map();
            case "relay-nine" -> RelayNineCompound.generate(seed, profile).map();
            case "thunder-run" -> ThunderRunCompound.generate(seed, profile).map();
            case "city" -> new BspCityGenerator().generate(192, 112, seed, null, profile);
            case "conquest-west" -> new BspCityGenerator().generate(280, 168, seed, TraversalAxis.WEST_TO_EAST, profile);
            case "conquest-south" -> new BspCityGenerator().generate(280, 168, seed, TraversalAxis.SOUTH_TO_NORTH, profile);
            case "precinct" -> new BspCityGenerator().generate(280, 168, seed, null, profile,
                    PrecinctPlan.derive(profile, 280, 168, new Random(seed)));
            default -> throw new IllegalArgumentException(family);
        };
    }

    private static boolean tree(Doodad d) { return d.sheetPath.endsWith("forest-canopies.png"); }

    static void verify(MapResult before, MapResult after, SurfacePalette surface) {
        int w = before.grid.getWidth(), h = before.grid.getHeight();
        int[] oldLabels = components(before.grid), newLabels = components(after.grid);
        int[] mapped = new int[w * h]; Arrays.fill(mapped, -1);
        for (int i = 0; i < oldLabels.length; i++) if (newLabels[i] >= 0 && oldLabels[i] >= 0) {
            int prior = mapped[oldLabels[i]];
            if (prior >= 0 && prior != newLabels[i]) throw new IllegalStateException("Landscape split a walkable component at " + i % w + "," + i / w);
            mapped[oldLabels[i]] = newLabels[i];
        }
        List<int[]> anchors = new ArrayList<>();
        anchors.add(new int[]{before.marineSpawnX, before.marineSpawnY});
        anchors.add(new int[]{before.defenderSpawnX, before.defenderSpawnY});
        for (var poi : before.pointsOfInterest) {
            anchors.add(new int[]{poi.anchorCellX, poi.anchorCellY});
            anchors.add(new int[]{poi.interiorAnchorX, poi.interiorAnchorY});
        }
        for (var pad : after.landingPads) {
            if (pad.isClear(before.grid, before.topology) && !pad.isClear(after.grid, after.topology))
                throw new IllegalStateException("Landscape obstructed a previously clear landing pad");
            anchors.add(new int[]{pad.centerX, pad.centerY});
            int l = pad.left() - 4, r = pad.right() + 4, b = pad.bottom() - 4, t = pad.top() + 4;
            switch (pad.approach) {
                case WEST -> l = 0; case EAST -> r = w - 1; case SOUTH -> b = 0; case NORTH -> t = h - 1;
            }
            for (int y = Math.max(0, b); y <= Math.min(h - 1, t); y++)
                for (int x = Math.max(0, l); x <= Math.min(w - 1, r); x++)
                    if (before.grid.isWalkable(x, y) != after.grid.isWalkable(x, y))
                        throw new IllegalStateException("Landing approach narrowed");
        }
        for (int[] a : anchors) if (before.grid.isWalkable(a[0], a[1]) && !after.grid.isWalkable(a[0], a[1]))
            throw new IllegalStateException("Required position covered at " + a[0] + "," + a[1]);
        for (var edge : before.roadGraph.edges()) for (int i = 0; i < edge.cellsX.length; i++)
            if (before.grid.isWalkable(edge.cellsX[i], edge.cellsY[i]) && !after.grid.isWalkable(edge.cellsX[i], edge.cellsY[i]))
                throw new IllegalStateException("Road blocked");
        if (surface != SurfacePalette.VERDANT && after.doodads.stream().anyMatch(OutdoorForestSnapshotSuite::tree))
            throw new IllegalStateException("Trees on a nonliving world");
    }

    private static int[] components(NavigationGrid grid) {
        int w = grid.getWidth(), h = grid.getHeight();
        int[] labels = new int[w * h], queue = new int[w * h]; Arrays.fill(labels, -1);
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] >= 0 || !grid.isWalkable(i % w, i / w)) continue;
            int head = 0, tail = 0; labels[i] = i; queue[tail++] = i;
            while (head < tail) {
                int at = queue[head++], x = at % w, y = at / w;
                for (Direction d : Direction.ALL) if (grid.canTraverseCellStep(x, y, d)) {
                    int next = (y + d.dy) * w + x + d.dx;
                    if (labels[next] >= 0) continue;
                    labels[next] = i; queue[tail++] = next;
                }
            }
        }
        return labels;
    }
}
