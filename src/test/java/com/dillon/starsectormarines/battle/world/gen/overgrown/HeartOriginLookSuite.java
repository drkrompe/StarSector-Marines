package com.dillon.starsectormarines.battle.world.gen.overgrown;

import com.dillon.starsectormarines.battle.evacuation.HeartBores;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Density;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where each heart came up (`heart-origins.md`): one pooled rural map of each origin, the facility seen at play zoom
 * and close on its core building and first mother, through the look preset. Opt-in: a look question, not part of
 * {@code all}.
 */
public final class HeartOriginLookSuite implements SnapshotSuite {

    static final float CLOSE_CELLS = 44f, SITE_CELLS = 130f;

    @Override public String id() { return "heart-origin-look"; }

    @Override public String label() { return "Heart origins: the facility each heart came up in"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        int index = 1;
        for (Form form : Form.values()) {
            StoryMapPool pool = StoryMapPools.pool(BloomHeartSetup.storyMap(form, Density.RURAL));
            for (StoryMapPool.Entry entry : pool.entries(SurfacePalette.VERDANT)) {
                BloomHeartSetup.Built built = BloomHeartSetup.country(form, SurfacePalette.VERDANT, Density.RURAL, entry);
                try (BattleSimulation sim = built.sim()) {
                    int[] core = BloomHeartSetup.originCore(built.map(), built.plan());
                    HeartBores.Site first = built.heart().mothers().get(0);
                    float cx = core == null ? first.x() : (core[0] + first.x()) / 2f;
                    float cy = core == null ? first.y() : (core[1] + first.y()) / 2f;
                    String name = String.format(Locale.ROOT, "%02d-%s-%s-seed%d", index++,
                            HeartOrigin.of(HeartCountry.place(built.plan(), HeartCountry.WORKINGS)).name().toLowerCase(Locale.ROOT),
                            form.name().toLowerCase(Locale.ROOT).replace('_', '-'), entry.seed());
                    out.add(new SnapshotArtifact(name + "-site.png", BattleReviewFrameRenderer.look(context.modRoot(),
                            1920, 1080, cx, cy, SITE_CELLS).render(sim, name)));
                    out.add(new SnapshotArtifact(name + "-play.png", BattleReviewFrameRenderer.look(context.modRoot(),
                            1920, 1080, cx, cy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, name)));
                    out.add(new SnapshotArtifact(name + "-close.png", BattleReviewFrameRenderer.look(context.modRoot(),
                            1920, 1080, cx, cy, CLOSE_CELLS).render(sim, name)));
                }
            }
        }
        return out;
    }
}
