package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.fortress.MaintenanceOutpost;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Locale;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in production installation renders across the world's surface families. */
public final class InstallationGroundSnapshotSuite implements SnapshotSuite {
    public String id() { return "installation-ground"; }
    public String label() { return "Installation yards on four worlds"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        InstalledVanillaData.install();
        var renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080);
        var frames = new ArrayList<SnapshotArtifact>();
        var report = new StringBuilder();
        var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 7, 6));
        for (SurfacePalette surface : SurfacePalette.values()) {
            var fixture = CorvusChapter.COLD_ACCOUNT.fixture(201827786L, manifest,
                    CorvusChapter.COLD_ACCOUNT.profile(surface));
            try (var sim = fixture.build()) {
                var grid = sim.getGrid();
                var topology = sim.getTopology();
                var site = MaintenanceOutpost.layout(true).at(7, (grid.getHeight() - 58) / 2, 0);
                var clearance = VehicleClearance.erode(grid, 1);
                var vehicleGrid = new NavigationGrid(grid.getWidth(), grid.getHeight());
                for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++)
                    if (clearance.isPassable(x, y)) vehicleGrid.setWalkableFloor(x, y);
                int[] approach = site.cell(73, 29);
                for (int[] local : new int[][]{{3, 31}, {25, 31}, {39, 46}}) {
                    int[] target = site.cell(local[0], local[1]);
                    assertTrue(GridPathfinder.findPath(vehicleGrid, approach[0], approach[1], target[0], target[1]).length > 0,
                            "finished gate, service yard and bay approach must carry a three-cell vehicle");
                }
                for (var room : site.buildings()) for (var door : room.doors()) {
                    assertTrue(grid.isDoorway(door.x(), door.y()), "authored doorway lost its identity");
                    assertTrue(GridPathfinder.findPath(grid, approach[0], approach[1], door.x(), door.y()).length > 0,
                            "furnishing/defenses must preserve every facility entrance");
                }
                var evidence = sim.getColdAccount().layout();
                assertTrue(topology.getRoomPurpose(evidence.evidenceX(), evidence.evidenceY()) == RoomPurpose.CIVIC_OFFICE,
                        "pirate records belong in dispatch, not a random gatehouse");
                long geometry = 1;
                int curvedCells = 0;
                var walls = new TreeMap<String, Integer>();
                var ground = new EnumMap<GroundKind, Integer>(GroundKind.class);
                for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++) {
                    geometry = 31 * geometry + (grid.isWalkable(x, y) ? 1 : 0);
                    geometry = 31 * geometry + (grid.isSeeThrough(x, y) ? 1 : 0);
                    geometry = 31 * geometry + grid.getWallHp(x, y);
                    geometry = 31 * geometry + (grid.isDoorway(x, y) ? 1 : 0);
                    ground.merge(topology.getGroundKind(x, y), 1, Integer::sum);
                    if (topology.isWall(x, y) && !grid.isWalkable(x, y)
                            && !topology.isFixture(x, y) && !grid.hasTag(x, y, CellTag.NATURAL_SOLID)) {
                        String material = topology.getSurfaceId(x, y);
                        assertNotNull(material, "installation walls must own their material");
                        var skin = TileRegistry.installed().block(material);
                        assertNotNull(skin, material);
                        assertNotNull(skin.rubbleBlockId, "installation wall lacks matching rubble: " + material + " at " + x + "," + y + " hp=" + grid.getWallHp(x, y));
                        walls.merge(material, 1, Integer::sum);
                    }
                    if (topology.roadSurface() != null) {
                        var patch = topology.roadSurface().patch(x, y, 0);
                        if (patch != null && patch.second() != null) curvedCells++;
                    }
                    if (surface != SurfacePalette.VERDANT)
                        assertFalse(topology.getGroundKind(x, y) == GroundKind.GRASS
                                && topology.getSurface(x, y) == 0 && !topology.isWall(x, y),
                                "Unplanted installation grass on " + surface + " at " + x + "," + y);
                }
                assertTrue(curvedCells > 0, "generated road curves must reach the production renderer");
                report.append(surface).append(" geometry=").append(geometry)
                        .append(" vehicleRoutes=3 facilityDoors=reachable records=dispatch")
                        .append(" doodads=").append(sim.getDoodads().size()).append(" curvedRoadCells=").append(curvedCells)
                        .append(" walls=").append(walls).append(" ground=").append(ground).append('\n');
                frames.add(new SnapshotArtifact(surface.name().toLowerCase(Locale.ROOT) + ".png",
                        renderer.render(sim, "Installation / " + surface)));
                if (surface == SurfacePalette.FROZEN) {
                    var labels = ReviewAnnotations.builder();
                    String[] names = {"Mech bay", "Workshop", "Magazine", "Command", "Power", "Mess hall",
                            "Barracks court", "Dispatch records", "Service entrance"};
                    int[][] anchors = {{17, 49}, {10, 22}, {26, 21}, {9, 7}, {22, 8}, {35, 8},
                            {52, 14}, {52, 39}, {70, 29}};
                    for (int i = 0; i < names.length; i++) {
                        int[] at = site.cell(anchors[i][0], anchors[i][1]);
                        labels.label(at[0], at[1], names[i], ReviewStyle.NOTE);
                    }
                    frames.add(new SnapshotArtifact("frozen-layout.png",
                            renderer.render(sim, "Maintenance outpost / layout review", labels.build())));
                    // Diagnostic cuts through the ordinary combat wall path, not a mission replay.
                    for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++) {
                        if (topology.isWall(x, y) && !topology.isFixture(x, y)
                                && !grid.hasTag(x, y, CellTag.NATURAL_SOLID)
                                && ((x % 13 < 3) || (y % 17 < 2))) sim.damageCell(x, y, 1200);
                    }
                    frames.add(new SnapshotArtifact("frozen-breaches.png",
                            renderer.render(sim, "Controlled wall-breach material preview / FROZEN")));
                }
            }
        }
        var output = context.projectRoot().resolve("build/reports/installation-ground.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report);
        return frames;
    }
}
