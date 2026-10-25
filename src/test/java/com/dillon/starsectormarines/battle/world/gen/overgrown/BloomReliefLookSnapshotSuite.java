package com.dillon.starsectormarines.battle.world.gen.overgrown;

import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * The Rav Bloom maps' raised ground through the look preset: Broad Front's and The Burn's hollow, cordon ridge and
 * brook valley, and a Cordon line's ridge, each at site zoom and play zoom, and each map whole, on the first verdant
 * pooled seed. Every camera is placed from the mission's own geometry (the workings' seed, the ridge's depth, the
 * brook's line, the redoubts' seeds), never from the elevation raised, so a run before a mapgen change and one after
 * frame the same ground. 1920x1080.
 */
public final class BloomReliefLookSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** Cells across a site frame: a whole hollow or a stretch of ridge between two lanes. */
    private static final float SITE_CELLS = 150f;

    @Override public String id() { return "bloom-relief-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Rav Bloom relief: hollow, cordon ridge, brook valley and Cordon line, site and play zoom"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
        List<SnapshotArtifact> out = new ArrayList<>();
        heart(out, look, StoryMap.HEART_FRONT, "broad-front");
        heart(out, look, StoryMap.HEART_BURN, "the-burn");
        cordon(out, look);
        return out;
    }

    private static void heart(List<SnapshotArtifact> out, BattleReviewFrameRenderer look, StoryMap map, String slug)
            throws Exception {
        Form form = BloomHeartSetup.formOf(map);
        StoryMapPool.Entry entry = StoryMapPools.pool(map).entries(SurfacePalette.VERDANT).get(0);
        BloomHeartSetup.Built built = BloomHeartSetup.country(form, SurfacePalette.VERDANT,
                BloomHeartSetup.densityOf(map), entry);
        try (BattleSimulation sim = built.sim()) {
            int w = form.scale.width, h = form.scale.height;
            frame(out, look, sim, slug + "/00-whole-map.png", w / 2f, h / 2f, Math.max(w, h * 16f / 9f));
            Precinct workings = HeartCountry.place(built.plan(), HeartCountry.WORKINGS);
            double facing = Math.toRadians((form.advancesAlongY() ? -90 : 180) + 40);
            float rimX = (float) (workings.seedX() + HeartCountry.HOLLOW_RADIUS * Math.cos(facing));
            float rimY = (float) (workings.seedY() + HeartCountry.HOLLOW_RADIUS * Math.sin(facing));
            frame(out, look, sim, slug + "/01-hollow-site.png", workings.seedX(), workings.seedY(), SITE_CELLS);
            frame(out, look, sim, slug + "/02-hollow-rim-play.png", rimX, rimY, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS);
            float depth = form.advancesAlongY() ? HeartCountry.BROAD_FRONT_RIDGE_DEPTH : HeartCountry.THE_BURN_RIDGE_DEPTH;
            int[] ridge = form.cellAt(depth, 0.62f);
            float half = HeartCountry.RIDGE_THICKNESS / 2f;
            float ridgeX = form.advancesAlongY() ? ridge[0] : ridge[0] + half;
            float ridgeY = form.advancesAlongY() ? ridge[1] + half : ridge[1];
            frame(out, look, sim, slug + "/03-cordon-ridge-site.png", ridgeX, ridgeY, SITE_CELLS);
            frame(out, look, sim, slug + "/04-cordon-ridge-play.png", ridgeX, ridgeY, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS);
            // The country's outcrops, where the landscape names any: the first one's middle.
            for (RidgePlanner.Band outcrop : built.plan().landscape().ridges()) {
                ElevationPlan.Point at = outcrop.path().get(outcrop.path().size() / 2);
                frame(out, look, sim, slug + "/07-outcrop-play.png", (float) at.x(), (float) at.y(),
                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS);
                break;
            }
            for (Landscape.Brook brook : built.plan().landscape().brooks()) {
                List<int[]> line = brook.line();
                int[] at = line.get(line.size() * 2 / 5);
                frame(out, look, sim, slug + "/05-brook-valley-site.png", at[0], at[1], SITE_CELLS);
                frame(out, look, sim, slug + "/06-brook-valley-play.png", at[0], at[1], BattleReviewFrameRenderer.PLAY_ZOOM_CELLS);
            }
        }
    }

    private static void cordon(List<SnapshotArtifact> out, BattleReviewFrameRenderer look) throws Exception {
        StoryMap map = StoryMap.CORDON_LINE;
        CordonVariant variant = CordonSetup.variantOf(map);
        StoryMapPool.Entry entry = StoryMapPools.pool(map).entries(SurfacePalette.VERDANT).get(0);
        CordonSetup.CordonMap cordon = CordonSetup.map(variant, CordonSetup.densityOf(map), SurfacePalette.VERDANT, entry);
        try (BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(), CordonSequence.Timetable.STANDARD)) {
            int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
            frame(out, look, sim, "cordon-line/00-whole-map.png", w / 2f, h / 2f, Math.max(w, h * 16f / 9f));
            PrecinctPlan plan = cordon.plan();
            Precinct a = HeartCountry.place(plan, CordonPlan.REDOUBT_PLACE + 2);
            Precinct b = HeartCountry.place(plan, CordonPlan.REDOUBT_PLACE + 3);
            float x = (a.seedX() + b.seedX()) / 2f, y = (a.seedY() + b.seedY()) / 2f;
            frame(out, look, sim, "cordon-line/01-line-ridge-site.png", x, y, SITE_CELLS);
            frame(out, look, sim, "cordon-line/02-line-ridge-play.png", x, y, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS);
            frame(out, look, sim, "cordon-line/03-redoubt-site.png", a.seedX(), a.seedY(), SITE_CELLS);
        }
    }

    private static void frame(List<SnapshotArtifact> out, BattleReviewFrameRenderer look, BattleSimulation sim,
                              String name, float x, float y, float cells) throws Exception {
        look.refocus(x + 0.5f, y + 0.5f, cells);
        out.add(new SnapshotArtifact(name, look.render(sim, "", ReviewAnnotations.NONE)));
    }
}
