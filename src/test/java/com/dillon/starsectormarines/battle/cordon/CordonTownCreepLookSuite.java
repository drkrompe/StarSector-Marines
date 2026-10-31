package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.tools.snapshot.FfmpegVideo;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ain Tafrant's town swallowed ({@code cordon-nouns.md}, Cut-off town): the Cordon Road on the town pool's arid seed 7
 * played as the evidence plays it, the camera held through the look preset on the compound and its warren from
 * setup to {@code -Dsnapshot.townCreep.minutes} (14) as one time-lapse, {@code look/town-creep.mp4}, a frame every
 * {@link #EVERY} ticks at 30 fps (about 24 seconds). The town starts held clear of the front's creep, which retakes it
 * as its bores are fed, and the town's own bores open on it (seven minutes and the final surge). Stills at each
 * minute in {@link #STILL_MINUTES} ({@code look/town-creep-mMM.png}); prints, each minute, the share of the warren's
 * open ground under living creep and the town bores' states. Opt-in: it plays fourteen minutes of battle.
 */
public final class CordonTownCreepLookSuite implements SnapshotSuite {

    /** Ticks between frames of the time-lapse. */
    static final int EVERY = 36;
    static final int[] STILL_MINUTES = {0, 3, 5, 7, 9, 12, 14};
    static final int WIDTH = 1920;
    static final int HEIGHT = 1080;

    @Override public String id() { return "cordon-town-creep"; }

    @Override public String label() { return "Cordon Road, Ain Tafrant: the town under the creep, setup to the surge"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.townCreep.seed", "7").trim());
        int minutes = Integer.parseInt(System.getProperty("snapshot.townCreep.minutes", "14").trim());
        if (FfmpegVideo.executable() == null) throw new IllegalStateException("cordon-town-creep needs ffmpeg");
        Path scratch = context.projectRoot().resolve("build/tmp/town-creep");
        Files.createDirectories(scratch);
        BattleSimulation sim = HeadlessBattles.build("cordon:variant=road,density=town,biome=arid,seed=" + seed);
        try {
            CordonTown town = sim.getCordon().town();
            if (town == null) throw new IllegalStateException("no town on this Road");
            CordonConvoys convoys = sim.getCordon().convoys();
            int[] roadStart = convoys.corridor().get(Math.max(0, Math.min(convoys.entry(), convoys.corridor().size() - 1)));
            int front = town.centreY() > roadStart[1] ? 1 : -1;
            int[] box = warren(town, front);
            float across = Math.max(box[2] - box[0] + 16f, (box[3] - box[1] + 10f) * WIDTH / HEIGHT);
            System.out.printf(Locale.ROOT, "    [town-creep] seed %d: town %d,%d to %d,%d, front %+d, warren box %d,%d to"
                            + " %d,%d, %.0f cells across%n", seed, town.left(), town.top(), town.right(), town.bottom(),
                    front, box[0], box[1], box[2], box[3], across);
            BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), WIDTH, HEIGHT,
                    (box[0] + box[2]) / 2f + 0.5f, (box[1] + box[3]) / 2f + 0.5f, across);
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            Path video = scratch.resolve("town-creep.mp4");
            List<SnapshotArtifact> out = new ArrayList<>();
            int end = Math.round(minutes * 60f / BattleSimulation.TICK_DT);
            int perMinute = Math.round(60f / BattleSimulation.TICK_DT);
            try (FfmpegVideo encoder = FfmpegVideo.open(video, WIDTH, HEIGHT, "30/1")) {
                while (true) {
                    int tick = sim.getSimTickIndex();
                    boolean still = tick % perMinute == 0 && wanted(tick / perMinute);
                    if (tick % EVERY == 0 || still) {
                        BufferedImage frame = look.render(sim, "", ReviewAnnotations.NONE, fx);
                        if (tick % EVERY == 0) encoder.add(frame);
                        if (still) {
                            out.add(new SnapshotArtifact(String.format("look/town-creep-m%02d.png", tick / perMinute),
                                    copy(frame)));
                        }
                    }
                    if (tick % perMinute == 0) report(sim, box, tick / perMinute);
                    if (tick >= end) break;
                    sim.advance(BattleSimulation.TICK_DT);
                    BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), BattleSimulation.TICK_DT);
                    fx.feed(sim);
                }
                encoder.finish();
            }
            out.add(0, SnapshotArtifact.video("look/town-creep.mp4", video, List.of()));
            return out;
        } finally {
            sim.close();
        }
    }

    /** The warren's ground round the compound: its east side and its front, as {@code CordonTownWarren} builds it. */
    static int[] warren(CordonTown town, int front) {
        int flank = CordonTownWarren.FLANK + CordonTownWarren.GLACIS;
        int left = town.left() - flank, right = town.right() + CordonTownWarren.EAST_REACH;
        int top = front > 0 ? town.top() - flank : town.top() - CordonTownWarren.FRONT_REACH;
        int bottom = front > 0 ? town.bottom() + CordonTownWarren.FRONT_REACH : town.bottom() + flank;
        return new int[]{left, top, right, bottom};
    }

    private static boolean wanted(int minute) {
        for (int m : STILL_MINUTES) if (m == minute) return true;
        return false;
    }

    /** The share of the box's open ground under living creep, and the town bores as they stand. */
    private static void report(BattleSimulation sim, int[] box, int minute) {
        CreepField creep = sim.creepFieldIfPresent();
        int open = 0, creeped = 0;
        for (int y = Math.max(0, box[1]); y <= Math.min(sim.getGrid().getHeight() - 1, box[3]); y++) {
            for (int x = Math.max(0, box[0]); x <= Math.min(sim.getGrid().getWidth() - 1, box[2]); x++) {
                if (!sim.getGrid().isWalkable(x, y)) continue;
                open++;
                if (creep != null && creep.isCreeped(x, y)) creeped++;
            }
        }
        StringBuilder bores = new StringBuilder();
        CordonBoreEscalation escalation = sim.getCordon().escalation();
        if (escalation != null) {
            for (CordonBoreEscalation.Reading r : escalation.readings(sim)) {
                if (r.site().town()) bores.append(' ').append(r.site().kind()).append('=').append(r.status());
            }
            if (escalation.surging()) bores.append(" SURGE");
        }
        System.out.printf(Locale.ROOT, "    [town-creep] minute %d: %.1f%% of the warren's open ground under creep (%d of"
                + " %d);%s%n", minute, open == 0 ? 0f : 100f * creeped / open, creeped, open, bores);
    }

    private static BufferedImage copy(BufferedImage frame) {
        BufferedImage out = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(frame, 0, 0, null);
        return out;
    }
}
