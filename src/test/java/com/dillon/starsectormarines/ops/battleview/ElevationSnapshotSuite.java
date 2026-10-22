package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationTestMaps;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Raised ground at play zoom (64x36 cells at 30 px, 1920x1080): the overlook
 * test map's plateaus, faces and ramps, unlit and under the battle's default
 * sun. Terrain shading is the <b>CPU model of the composite shader</b>
 * ({@link GroundSunShadowReference}), not the shader.
 */
public final class ElevationSnapshotSuite implements SnapshotSuite {

    private static final int CELL_PX = 30;
    private static final long SEED = 11L;

    @Override
    public String id() {
        return "elevation";
    }

    @Override
    public String label() {
        return "Raised ground: plateaus, cliffs and ramps";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        int order = 1;
        for (CellTopology.GroundKind ground : new CellTopology.GroundKind[]{
                CellTopology.GroundKind.GRASS, CellTopology.GroundKind.DIRT}) {
            String name = ground.name().toLowerCase();
            MapResult map = ElevationTestMaps.overlookMap(1, ground);
            BufferedImage plain = renderer.render(map, SEED, CELL_PX);
            out.add(new SnapshotArtifact(String.format("%02d-overlook-%s-unlit.png", order++, name), plain));
            out.add(new SnapshotArtifact(String.format("%02d-overlook-%s-sun.png", order++, name), sunlit(map, plain)));
        }
        return out;
    }

    static BufferedImage sunlit(MapResult map, BufferedImage ground) {
        MacroReliefField relief = new MacroReliefField(map.topology, map.grid, map.buildings,
                GenMappingRegistry.installed());
        int height = map.grid.getHeight();
        GroundSunShadowReference.PixelToWorld worldAt = new GroundSunShadowReference.PixelToWorld() {
            @Override
            public float worldX(int pixelX) {
                return (pixelX + 0.5f) / CELL_PX;
            }

            @Override
            public float worldY(int pixelY) {
                return height - (pixelY + 0.5f) / CELL_PX;
            }
        };
        return GroundSunShadowReference.shade(ground, relief::metersAt, worldAt,
                SunLight.DEFAULT_AZIMUTH_DEGREES, SunLight.DEFAULT_ELEVATION_DEGREES,
                SunLight.DEFAULT_SHADOW_STRENGTH, relief.tallestMeters());
    }
}
