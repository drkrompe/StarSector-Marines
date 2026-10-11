package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/** Fence joins through production prop rendering, including deliberate gate gaps. */
public final class FenceSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "fences"; }
    @Override public String label() { return "Connected industrial fences"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        NavigationGrid grid = new NavigationGrid(24, 15);
        CellTopology topology = new CellTopology(24, 15);
        List<Doodad> props = new ArrayList<>();
        for (int y = 0; y < 15; y++) for (int x = 0; x < 24; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, x < 12 ? CellTopology.GroundKind.STONE : CellTopology.GroundKind.GRASS);
        }
        ring(props, 2, 6, 9, 12, false);
        ring(props, 14, 6, 21, 12, true);
        for (int turn = 0; turn < 4; turn++) {
            int cx = 3 + turn * 6, cy = 2;
            add(props, cx, cy, "corner-nw", turn);
            int dx = 1, dy = 0, vx = 0, vy = -1;
            for (int i = 0; i < turn; i++) {
                int oldX = dx; dx = -dy; dy = oldX;
                oldX = vx; vx = -vy; vy = oldX;
            }
            add(props, cx + dx, cy + dy, "straight-h", turn);
            add(props, cx + vx, cy + vy, "straight-v", turn);
        }
        for (Doodad prop : props) {
            grid.setWalkable(prop.cellX, prop.cellY, false);
            grid.setSeeThrough(prop.cellX, prop.cellY, true);
            topology.setFixture(prop.cellX, prop.cellY, true);
        }
        MapResult map = new MapResult(grid, topology, 0, 0, 23, 14, List.of(), props);
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        return List.of(new SnapshotArtifact("fence-reskin-template.png", FenceReskinTemplate.render()),
                new SnapshotArtifact("fence-layouts.png", renderer.render(map, 42, 48)),
                new SnapshotArtifact("fence-layouts-game-scale.png", renderer.render(map, 42, 32)));
    }

    private static void ring(List<Doodad> props, int left, int bottom, int right, int top, boolean gate) {
        for (int x = left + 1; x < right; x++) {
            add(props, x, top, "straight-h", 0);
            if (!gate || x < left + 3 || x > left + 4) add(props, x, bottom, "straight-h", 0);
        }
        for (int y = bottom + 1; y < top; y++) {
            add(props, left, y, "straight-v", 0);
            add(props, right, y, "straight-v", 0);
        }
        add(props, left, top, "corner-nw", 0);
        add(props, right, top, "corner-ne", 0);
        add(props, left, bottom, "corner-sw", 0);
        add(props, right, bottom, "corner-se", 0);
    }

    private static void add(List<Doodad> props, int x, int y, String suffix, int turn) {
        props.add(new Doodad(x, y, TileRegistry.installed().doodad("doodad.industrial-fence-" + suffix), turn));
    }
}
