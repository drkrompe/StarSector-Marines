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
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.Doodad;
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
            for (String state : new String[]{"worn", "damaged", "destroyed"}) {
                out.add(new SnapshotArtifact("defence-post-medium-" + state + ".png",
                        close.render(wornPost(state), "defence posts / medium post, " + state + " by structural hits")));
            }
            return out;
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }

    /**
     * A fresh medium post whose ring is hit through the ordinary structural channel ({@code MapEditor.damageWall}):
     * every piece by 25 ("damaged"), by 100 ("destroyed"), or each by a different amount ("worn").
     */
    private BattleSimulation wornPost(String state) throws Exception {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        List<Doodad> stamped = new ArrayList<>();
        List<DefensePost> posts = new ArrayList<>();
        posts.add(DefensePostStamper.stampPost(grid, topology, stamped, DefensePostKind.MEDIUM, null, 60, 30, new Random(1)));
        DoodadService service = new DoodadService(grid);
        for (Doodad doodad : stamped) service.addDoodad(doodad);
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            MapEditor editor = new MapEditor(navigation);
            editor.setDoodads(service);
            int i = 0;
            for (Doodad doodad : stamped) {
                int amount = state.equals("damaged") ? 25 : state.equals("destroyed") ? 100 : new int[]{0, 25, 100, 25, 0, 100, 25, 0}[i % 8];
                if (amount > 0) editor.damageWall(doodad.cellX, doodad.cellY, amount);
                i++;
            }
        }
        BattleSimulation sim = SceneBuilder.over(grid, topology).posts(posts).missionCompletion(false).build().sim();
        for (Doodad doodad : service.getDoodads()) sim.addDoodad(doodad);
        return sim;
    }
}
