package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                long geometry = 1;
                int curvedCells = 0;
                var ground = new EnumMap<GroundKind, Integer>(GroundKind.class);
                for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++) {
                    geometry = 31 * geometry + (grid.isWalkable(x, y) ? 1 : 0);
                    geometry = 31 * geometry + (grid.isSeeThrough(x, y) ? 1 : 0);
                    geometry = 31 * geometry + grid.getWallHp(x, y);
                    geometry = 31 * geometry + (grid.isDoorway(x, y) ? 1 : 0);
                    ground.merge(topology.getGroundKind(x, y), 1, Integer::sum);
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
                        .append(" doodads=").append(sim.getDoodads().size()).append(" curvedRoadCells=").append(curvedCells)
                        .append(" ground=").append(ground).append('\n');
                frames.add(new SnapshotArtifact(surface.name().toLowerCase(Locale.ROOT) + ".png",
                        renderer.render(sim, "Installation / " + surface)));
            }
        }
        var output = context.projectRoot().resolve("build/reports/installation-ground.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report);
        return frames;
    }
}
