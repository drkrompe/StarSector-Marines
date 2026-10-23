package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ground turrets where generation actually puts them: production-size Conquest maps, framed on each kind of site
 * (overwatch tower, defence post by tier, fort nest) at play zoom, 1920x1080 and 100% scale. The question it answers
 * is how a turret sits on the ground it was given, so it is drawn through the ordinary review renderer on the map's
 * own terrain, neighbours and all. Presentation evidence, not a battle.
 */
public final class GroundTurretSiteSnapshotSuite implements SnapshotSuite {

    private static final long[] SEEDS = {1L, 2L, 3L};
    /** Sites shown per kind per seed. */
    private static final int PER_KIND = 2;
    /** Cells across a frame: about the player's ordinary battle zoom. */
    private static final float CELLS_ACROSS = 40f;

    @Override public String id() { return "ground-turret-sites"; }
    @Override public String label() { return "Ground turrets at their generated sites on Conquest maps, at play zoom"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SnapshotArtifact> out = new ArrayList<>();
        try {
            int number = 1;
            for (long seed : SEEDS) {
                MapResult map = new BspCityGenerator().generate(BattleSetup.CONQUEST_GRID_W, BattleSetup.CONQUEST_GRID_H,
                        seed, TraversalAxis.SOUTH_TO_NORTH);
                BattleSimulation sim = SceneBuilder.over(map.grid, map.topology)
                        .posts(map.defensePosts).missionCompletion(false).seed(seed).build().sim();
                for (Doodad doodad : map.doodads) sim.addDoodad(doodad);
                Map<String, Integer> shown = new LinkedHashMap<>();
                for (DefensePost post : map.defensePosts) {
                    String kind = kindOf(post);
                    int count = shown.merge(kind, 1, Integer::sum);
                    if (count > PER_KIND) continue;
                    BattleReviewFrameRenderer frame = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                            post.anchorX + 0.5f, post.anchorY + 0.5f, CELLS_ACROSS);
                    String structure = post.turrets.isEmpty() ? "none" : post.turrets.get(0).structureId;
                    String stem = String.format("%02d-seed%d-%s-%d", number++, seed, kind, count);
                    out.add(new SnapshotArtifact(stem + ".png", frame.render(sim,
                            "ground turret sites / seed " + seed + " / " + kind + " / " + structure
                                    + " at (" + post.anchorX + "," + post.anchorY + ")")));
                }
            }
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
        return out;
    }

    /** Overwatch towers carry no layout; posts are named by tier; a fort's own posts by the fort. */
    private static String kindOf(DefensePost post) {
        if (post.fort != null) return "fort-" + post.tier.name().toLowerCase();
        if (post.layoutId == null) return "tower";
        return "post-" + post.tier.name().toLowerCase();
    }
}
