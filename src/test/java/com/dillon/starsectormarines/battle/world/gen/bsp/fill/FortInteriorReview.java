package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import java.awt.image.BufferedImage;

/** Fort reviews must include structure entities: a terrain-only view omits every aspect core. */
final class FortInteriorReview {
    private FortInteriorReview() {}

    static BufferedImage render(SnapshotContext context, GenContext map, int x, int y,
                                int cellsAcross, String caption) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        try (var sim = SceneBuilder.over(map.grid, map.topology).posts(map.defensePosts)
                .missionCompletion(false).build().sim()) {
            for (var prop : map.doodads) sim.addDoodad(prop);
            return new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, x, y, cellsAcross)
                    .render(sim, caption + " / Java2D / 100% UI / no live OpenGL capture");
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }
}
