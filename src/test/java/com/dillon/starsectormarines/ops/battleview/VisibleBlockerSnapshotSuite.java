package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.*;
import java.util.ArrayList;
import java.util.List;

public final class VisibleBlockerSnapshotSuite implements SnapshotSuite {
    public String id() { return "visible-blockers"; }
    public String label() { return "Visible canopy surfaces, hidden forest interior"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        var frames = new ArrayList<SnapshotArtifact>();
        var renderer = new PlayerViewFrameRenderer(context.modRoot(), 1040, 720);
        try (var sim = VisibleBlockerScene.create(TreeCanopy.OAK, 0)) {
            sim.getGrid().setWalkable(7, 13, false);
            sim.getTopology().setFixture(7, 13, true);
            sim.addDoodad(new Doodad(7, 13, TileRegistry.installed().doodad("doodad.industrial-crate-stack")));
            VisibleBlockerScene.settle(sim);
            frames.add(new SnapshotArtifact("observed-tree.png", renderer.render(sim,
                    "The near crown is visible; the next tree and defender behind it remain fogged.")));
            sim.damageCell(10, 8, 1);
            frames.add(new SnapshotArtifact("damaged-tree.png", renderer.render(sim,
                    "Damaged foliage stays visible and continues to block sight.")));
            sim.damageCell(10, 8, 199);
            VisibleBlockerScene.settle(sim);
            frames.add(new SnapshotArtifact("felled-tree.png", renderer.render(sim,
                    "Felling opens sight: the defender and next crown can now be seen.")));
        }
        return frames;
    }
}
