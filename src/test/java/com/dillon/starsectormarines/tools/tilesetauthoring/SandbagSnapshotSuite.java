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

/** Production prop rendering: closed ring, open-backed bay and four rotated elbows. */
public final class SandbagSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "sandbags"; }
    @Override public String label() { return "Rounded directional sandbag cover"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        NavigationGrid grid = new NavigationGrid(21, 13);
        CellTopology topology = new CellTopology(21, 13);
        List<Doodad> props = new ArrayList<>();
        for (int y = 0; y < 13; y++) for (int x = 0; x < 21; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, x < 10 ? CellTopology.GroundKind.STONE : CellTopology.GroundKind.GRASS);
        }
        for (int x = 3; x <= 5; x++) {
            add(props, x, 10, "straight-n", 0); add(props, x, 6, "straight-s", 0);
            add(props, x + 7, 10, "straight-n", 0);
        }
        for (int y = 7; y <= 9; y++) {
            add(props, 2, y, "straight-w", 0); add(props, 6, y, "straight-e", 0);
            add(props, 9, y, "straight-w", 0); add(props, 13, y, "straight-e", 0);
        }
        add(props, 2, 10, "corner-nw", 0); add(props, 6, 10, "corner-ne", 0);
        add(props, 2, 6, "corner-sw", 0); add(props, 6, 6, "corner-se", 0);
        add(props, 9, 10, "corner-nw", 0); add(props, 13, 10, "corner-ne", 0);
        for (int turn = 0; turn < 4; turn++) {
            int cx = 3 + turn * 5, cy = 3;
            add(props, cx, cy, "corner-nw", turn);
            int dx = 1, dy = 0, vx = 0, vy = -1;
            for (int i = 0; i < turn; i++) {
                int oldX = dx; dx = -dy; dy = oldX;
                oldX = vx; vx = -vy; vy = oldX;
            }
            add(props, cx + dx, cy + dy, "straight-n", turn);
            add(props, cx + vx, cy + vy, "straight-w", turn);
        }
        // Standalone facings keep their authored edge position too.
        String[] faces = {"n", "e", "s", "w"};
        for (int i = 0; i < 4; i++) add(props, 16 + i, 9, "straight-" + faces[i], 0);
        for (Doodad prop : props) {
            grid.setWalkable(prop.cellX, prop.cellY, false);
            grid.setSeeThrough(prop.cellX, prop.cellY, true);
            topology.setFixture(prop.cellX, prop.cellY, true);
        }
        MapResult map = new MapResult(grid, topology, 0, 0, 20, 12, List.of(), props);
        return List.of(new SnapshotArtifact("sandbag-reskin-template.png", SandbagReskinTemplate.render()),
                new SnapshotArtifact("sandbag-layouts.png",
                        new HeadlessBattleMapRenderer(context.modRoot()).render(map, 42, 48)));
    }

    private static void add(List<Doodad> props, int x, int y, String suffix, int turns) {
        props.add(new Doodad(x, y, TileRegistry.installed().doodad("doodad.sandbag-" + suffix), turns));
    }
}
