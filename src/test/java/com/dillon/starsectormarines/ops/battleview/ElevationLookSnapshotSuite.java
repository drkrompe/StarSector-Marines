package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationBrush;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
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
 * Look study for raised ground: the shipped per-cell cliff tiles against
 * gradient shading of one smooth height surface ({@link ElevationGradientLook}),
 * with and without scree and ramp steps carrying the grade. The overlook with
 * tall south faces, at play zoom (30 px a cell) and a 2x close-up of the west
 * ramp, all 1920x1080. Sun and shading are CPU models of the composite.
 */
public final class ElevationLookSnapshotSuite implements SnapshotSuite {

    private static final long SEED = 11L;
    private static final int FACE = 1;

    @Override public String id() { return "elevation-look"; }

    @Override public String label() { return "Raised ground look: tiles against gradients and scree"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        ElevationPlan base = ElevationTestMaps.overlook(FACE);
        ElevationPlan plan = new ElevationPlan(base.width(), base.height(), FACE, 2, base.ruggedness(), base.seed(),
                base.plateaus(), base.ramps());
        MapResult tiled = ElevationTestMaps.meadow(CellTopology.GroundKind.GRASS);
        ElevationBrush.stamp(plan, tiled.grid, tiled.topology);
        MapResult flat = ElevationTestMaps.meadow(CellTopology.GroundKind.GRASS);
        ElevationGradientLook look = new ElevationGradientLook(ElevationLayout.resolve(plan), FACE);
        int h = ElevationTestMaps.HEIGHT;
        List<SnapshotArtifact> out = new ArrayList<>();
        // {cellPx, centre x, centre y, cells across, cells up}: the whole overlook, then the west ramp at 2x.
        float[][] views = {{30, 32, 18, 64, 36}, {60, 22, 19, 32, 18}};
        String[] viewNames = {"play-zoom", "close"};
        for (int v = 0; v < views.length; v++) {
            int px = (int) views[v][0];
            float cx = views[v][1], cy = views[v][2];
            int across = (int) views[v][3], up = (int) views[v][4];
            float left = cx - across / 2f, top = cy + up / 2f;
            BufferedImage tiles = renderer.renderView(tiled, SEED, cx, cy, across, up, px);
            BufferedImage meadow = renderer.renderView(flat, SEED, cx, cy, across, up, px);
            String view = viewNames[v];
            BufferedImage a = ElevationSnapshotSuite.sunlit(tiled, tiles, left, top, px);
            ElevationOptionsSnapshotSuite.caption(a, "A. Cliff tiles (shipped now)",
                    "Per-cell face art and a per-cell shadow.");
            out.add(new SnapshotArtifact(view + "/a-tiles.png", a));
            BufferedImage b = look.shade(meadow, left, top, px,
                    new ElevationGradientLook.Options(true, false, false, .6f));
            ElevationOptionsSnapshotSuite.caption(b, "B. Gradients",
                    "One smooth height surface: slope shading, rock on steep ground, dark foot, lit rim, soft shadow.");
            out.add(new SnapshotArtifact(view + "/b-gradient.png", b));
            BufferedImage c = look.shade(meadow, left, top, px,
                    new ElevationGradientLook.Options(true, true, true, .6f));
            ElevationOptionsSnapshotSuite.caption(c, "C. Gradients with scree and ramp steps (recommended)",
                    "As B, plus loose stones thickening on slopes and at the foot, and compacted steps across ramps.");
            out.add(new SnapshotArtifact(view + "/c-gradient-scree.png", c));
            BufferedImage d = look.shade(tiles, left, top, px,
                    new ElevationGradientLook.Options(false, true, true, 0f));
            ElevationOptionsSnapshotSuite.caption(d, "D. Cliff tiles under the gradients and scree",
                    "The shipped rock faces kept, softened by the same shading, stones and ramp steps.");
            out.add(new SnapshotArtifact(view + "/d-tiles-gradient-scree.png", d));
        }
        return out;
    }
}
