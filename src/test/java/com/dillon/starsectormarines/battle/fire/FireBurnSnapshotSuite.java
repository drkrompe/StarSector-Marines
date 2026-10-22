package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Burn at its peak (`fire-nouns.md`, Look and Smoke): the heart mission's burn variant at seed 202, the whole
 * battle played to {@link #PEAK_SECONDS}, framed on the burning cell nearest the company. Play zoom and close up by
 * day, play zoom at night with no lamps, and a short GIF at play zoom: what a big grass-and-creep fire looks like in
 * the mission it was built for, and how much smoke it gives off ({@code -Dbattle.fire.lowSmoke}).
 */
public final class FireBurnSnapshotSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/heart-burn-v1.json";
    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final float PLAY_CELLS = 60f, CLOSE_CELLS = 20f;
    private static final int PEAK_SECONDS = Integer.getInteger("battle.scene.fireBurn.seconds", 200);
    private static final int GIF_FRAMES = 30, GIF_STEP = 2, GIF_DELAY_MS = 66;
    private static final NightLighting NIGHT = new NightLighting(0.78f, List.of(), Set.of());

    @Override public String id() { return "fire-burn"; }

    @Override public String label() { return "The Burn at its peak: flames and smoke over creep and grass, day and night"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        try (BattleSimulation sim = BattleFixtureJson.fromJson(new JSONObject(resource())).build()) {
            sim.setMissionCompletionEnabled(false);
            int peak = Math.round(PEAK_SECONDS / BattleSimulation.TICK_DT);
            while (sim.getSimTickIndex() < peak) sim.advance(BattleSimulation.TICK_DT);
            float[] at = frontNearCompany(sim);
            BattleReviewFrameRenderer play = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, at[0], at[1], PLAY_CELLS);
            BattleReviewFrameRenderer close = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, at[0], at[1], CLOSE_CELLS);
            String smoke = "low smoke " + FireSmoke.LOW_SMOKE.name().toLowerCase(Locale.ROOT)
                    + ", smoke density " + System.getProperty("battle.render.smokeDensity", "3");
            out.add(still(whole, sim, "01-whole-map.png", "whole map, " + smoke));
            out.add(still(play, sim, "02-play.png", "play zoom at the front, " + smoke));
            out.add(still(close, sim, "03-close.png", "close up at the front, " + smoke));
            sim.getTopology().setNightLighting(NIGHT);
            out.add(still(play, sim, "04-night-play.png", "night, play zoom, " + smoke));
            sim.getTopology().setNightLighting(null);
            List<BufferedImage> frames = new ArrayList<>();
            for (int k = 0; k < GIF_FRAMES; k++) {
                for (int s = 0; s < GIF_STEP; s++) sim.advance(BattleSimulation.TICK_DT);
                frames.add(play.atClock(sim.getSimTickIndex() / 30f).render(sim, caption("play zoom at the front, " + smoke, sim)));
            }
            out.add(SnapshotArtifact.colorAnimation("05-play.gif", frames, GIF_DELAY_MS));
        }
        return out;
    }

    private static SnapshotArtifact still(BattleReviewFrameRenderer renderer, BattleSimulation sim, String file, String view)
            throws Exception {
        return new SnapshotArtifact(file, renderer.atClock(sim.getSimTickIndex() / 30f).render(sim, caption(view, sim)));
    }

    /** The burning cell nearest the company's centroid: where the fire meets the men. */
    private static float[] frontNearCompany(BattleSimulation sim) {
        World world = sim.world();
        double sx = 0, sy = 0;
        int n = 0;
        for (long id : sim.getRoster().factionDenseArray(Faction.MARINE)) {
            if (!world.isAlive(id)) continue;
            sx += world.x(id);
            sy += world.y(id);
            n++;
        }
        FireField fire = sim.fireField();
        int w = fire.width();
        float cx = n == 0 ? w / 2f : (float) (sx / n), cy = n == 0 ? fire.height() / 2f : (float) (sy / n);
        float best = Float.MAX_VALUE, bx = cx, by = cy;
        for (int i = 0, c = fire.burningCount(); i < c; i++) {
            int idx = fire.burningCell(i);
            float x = idx % w + 0.5f, y = idx / w + 0.5f;
            float d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
            if (d < best) { best = d; bx = x; by = y; }
        }
        return new float[]{bx, by};
    }

    private static String caption(String view, BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        return String.format(Locale.ROOT, "The Burn, seed 202 | %s | %d:%02d | %d cells burning",
                view, tick / 1800, (tick % 1800) / 30, sim.fireField().burningCount());
    }

    private static String resource() throws Exception {
        try (InputStream stream = FireBurnSnapshotSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
