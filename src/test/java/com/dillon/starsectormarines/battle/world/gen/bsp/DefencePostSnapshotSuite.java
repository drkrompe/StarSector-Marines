package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.EmplacementWreck;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generated defence posts outside any fort: a defence nest (the light post), a medium post and a large post stood side by
 * side on bare ground, at 1920x1080 and game scale, so the earthwork round a permanent position can be read next to the
 * fort's own prepared positions. Presentation evidence, not a battle.
 */
public final class DefencePostSnapshotSuite implements SnapshotSuite {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 60;

    @Override
    public String id() {
        return "defence-posts";
    }

    @Override
    public String label() {
        return "Generated defence posts (nest, medium, large) on bare ground at game scale";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        try {
            NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
            CellTopology topology = new CellTopology(WIDTH, HEIGHT);
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    grid.setWalkableFloor(x, y);
                    topology.setGroundKind(x, y, GroundKind.DIRT);
                }
            }
            List<Doodad> doodads = new ArrayList<>();
            List<DefensePost> posts = new ArrayList<>();
            posts.add(DefensePostStamper.stampNest(grid, topology, doodads, 44, 30, TurretCatalogRegistry.VULCAN_STRUCTURE_ID));
            posts.add(DefensePostStamper.stampPost(grid, topology, doodads, DefensePostKind.MEDIUM, null, 60, 30, new Random(1)));
            posts.add(DefensePostStamper.stampPost(grid, topology, doodads, DefensePostKind.LARGE, DefensePostShape.LINE_H, 78, 30, new Random(1)));
            BattleSimulation sim = SceneBuilder.over(grid, topology).posts(posts).missionCompletion(false).build().sim();
            for (Doodad doodad : doodads) sim.addDoodad(doodad);
            BattleReviewFrameRenderer wide = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 61f, 30.5f, 44f);
            BattleReviewFrameRenderer close = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 60.5f, 30.5f, 16f);
            List<SnapshotArtifact> out = new ArrayList<>();
            out.add(new SnapshotArtifact("defence-posts-row.png", wide.render(sim, "defence posts / nest, medium, large")));
            out.add(new SnapshotArtifact("defence-post-medium-close.png", close.render(sim, "defence posts / medium post, close")));
            for (String state : new String[]{"damaged", "destroyed"}) {
                List<Doodad> shown = new ArrayList<>(doodads);
                if (state.equals("damaged")) EmplacementWreck.damageAround(shown, 60, 30, 1);
                else EmplacementWreck.destroyAround(shown, 60, 30, 1);
                BattleSimulation stateSim = SceneBuilder.over(grid, topology).posts(posts).missionCompletion(false).build().sim();
                for (Doodad doodad : shown) stateSim.addDoodad(doodad);
                out.add(new SnapshotArtifact("defence-post-medium-" + state + ".png",
                        close.render(stateSim, "defence posts / medium post, " + state)));
            }
            return out;
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }
}
