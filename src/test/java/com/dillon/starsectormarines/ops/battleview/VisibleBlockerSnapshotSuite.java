package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.IndustrialYardCoverFixture;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.*;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;

public final class VisibleBlockerSnapshotSuite implements SnapshotSuite {
    public String id() { return "visible-blockers"; }
    public String label() { return "See-through crate cover versus opaque trees"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        var frames = new ArrayList<SnapshotArtifact>();
        var renderer = new PlayerViewFrameRenderer(context.modRoot(), 1040, 720);
        var comparisonRenderer = new PlayerViewFrameRenderer(context.modRoot(), 660, 450);
        try (var crates = VisibleBlockerScene.coverComparison(false);
             var tree = VisibleBlockerScene.coverComparison(true)) {
            verifyComparison(crates, tree);
            BufferedImage left = comparisonRenderer.render(crates, "CRATES: cover retained; defender visible");
            BufferedImage right = comparisonRenderer.render(tree, "TREE: sight blocked; defender hidden");
            BufferedImage comparison = new BufferedImage(1328, 488, BufferedImage.TYPE_INT_ARGB);
            var graphics = comparison.createGraphics();
            graphics.setColor(new Color(20, 23, 26)); graphics.fillRect(0, 0, 1328, 488);
            graphics.drawImage(left, 0, 0, null); graphics.drawImage(right, 668, 0, null);
            graphics.setColor(Color.WHITE); graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            graphics.drawString("Same positions in both panels: blue observer at (5,7), defender at (14,7), immediately behind the obstacle.", 16, 475);
            graphics.dispose();
            frames.add(new SnapshotArtifact("crates-versus-tree.png", comparison));
        }
        try (var sim = VisibleBlockerScene.create(TreeCanopy.OAK, 0)) {
            sim.addDoodad(IndustrialYardCoverFixture.placeCrates(sim.getGrid(), sim.getTopology(), 7, 13));
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

    /** Validate the claims against the same simulations that produce the pictures. */
    private static void verifyComparison(BattleSimulation crates, BattleSimulation tree) {
        for (var sim : List.of(crates, tree)) {
            for (int y = 0; y < sim.getGrid().getHeight(); y++) {
                for (int x = 0; x < sim.getGrid().getWidth(); x++) {
                    if (sim.getTopology().getGroundKind(x, y) != GroundKind.GRASS
                            || sim.getTopology().getSurface(x, y) != 0)
                        throw new IllegalStateException("Comparison props must preserve the grass beneath them");
                }
            }
        }
        int expectedCover = TileRegistry.installed().doodad("doodad.industrial-crate-stack").cover.level();
        if (crates.getRoster().liveCount() != 2 || tree.getRoster().liveCount() != 2)
            throw new IllegalStateException("Both comparison actors must remain alive");
        if (crates.getGrid().isWalkable(13, 7) || !crates.getGrid().isSeeThrough(13, 7)
                || expectedCover <= 0
                || crates.getGrid().getCellCoverAtFacing(14, 7, NavigationGrid.FACING_W) != expectedCover)
            throw new IllegalStateException("Production crates must provide see-through cover to the defender");
        if (!crates.getGrid().hasLineOfSight(5, 7, 14, 7)
                || !crates.getFogOfWar().isCellRevealed(14, 7)
                || crates.getFogOfWar().getUnitVisibility(1) != FogOfWarService.VIS_VISIBLE)
            throw new IllegalStateException("The defender behind the crates must be visible");
        if (tree.getGrid().hasLineOfSight(5, 7, 14, 7)
                || tree.getFogOfWar().isCellRevealed(14, 7)
                || tree.getFogOfWar().getUnitVisibility(1) != FogOfWarService.VIS_HIDDEN)
            throw new IllegalStateException("The defender behind the tree must be hidden");
    }
}
