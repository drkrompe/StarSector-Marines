package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationTestMaps;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.tools.forestauthoring.OutdoorForestSnapshotSuite;
import com.dillon.starsectormarines.battle.nav.ElevationMap;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        // The generator's own plateaus, asked for on missions that never ask.
        String previous = System.getProperty(PLATEAUS);
        try {
            System.setProperty(PLATEAUS, "3");
            for (String family : GENERATED) {
                MapResult map = OutdoorForestSnapshotSuite.generate(family, 9301,
                        new TargetProfile(4, 5, 2, 1, "independent", Set.of(), SurfacePalette.VERDANT,
                                SettlementLink.ROAD));
                ElevationMap elevation = map.grid.elevation();
                if (elevation == null) {
                    System.out.println("[elevation] " + family + ": no room for a plateau");
                    continue;
                }
                int[] focus = firstRamp(elevation);
                // The renderer keeps the camera on the map; frame where it will.
                focus[0] = Math.max(32, Math.min(elevation.width() - 32, focus[0]));
                focus[1] = Math.max(18, Math.min(elevation.height() - 18, focus[1]));
                System.out.println("[elevation] " + family + ": plateaus placed, ramp at " + focus[0] + "," + focus[1]);
                BufferedImage view = renderer.renderView(map, SEED, focus[0], focus[1], 64, 36, CELL_PX);
                out.add(new SnapshotArtifact(String.format("%02d-generated-%s-sun.png", order++, family),
                        sunlit(map, view, focus[0] - 32f, focus[1] + 18f)));
            }
        } finally {
            if (previous == null) System.clearProperty(PLATEAUS); else System.setProperty(PLATEAUS, previous);
        }
        return out;
    }

    private static final String PLATEAUS = "battle.mapgen.plateaus";
    private static final List<String> GENERATED = List.of("relief", "counterattack", "thunder-run", "city");

    /** The middle of the first ramp found, to frame a generated plateau at play zoom. */
    private static int[] firstRamp(ElevationMap elevation) {
        int w = elevation.width(), h = elevation.height();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            if (elevation.isRampAt(y * w + x)) return new int[]{x, y};
        return new int[]{w / 2, h / 2};
    }

    static BufferedImage sunlit(MapResult map, BufferedImage ground) {
        return sunlit(map, ground, 0f, map.grid.getHeight());
    }

    /** {@code left} and {@code top} are the world coordinates of the image's top-left corner. */
    static BufferedImage sunlit(MapResult map, BufferedImage ground, float left, float top) {
        return sunlit(map, ground, left, top, CELL_PX);
    }

    static BufferedImage sunlit(MapResult map, BufferedImage ground, float left, float top, int cellPx) {
        MacroReliefField relief = new MacroReliefField(map.topology, map.grid, map.buildings,
                GenMappingRegistry.installed());
        GroundSunShadowReference.PixelToWorld worldAt = new GroundSunShadowReference.PixelToWorld() {
            @Override
            public float worldX(int pixelX) {
                return left + (pixelX + 0.5f) / cellPx;
            }

            @Override
            public float worldY(int pixelY) {
                return top - (pixelY + 0.5f) / cellPx;
            }
        };
        return GroundSunShadowReference.shade(ground, relief::metersAt, worldAt,
                SunLight.DEFAULT_AZIMUTH_DEGREES, SunLight.DEFAULT_ELEVATION_DEGREES,
                SunLight.DEFAULT_SHADOW_STRENGTH, relief.tallestMeters());
    }
}
