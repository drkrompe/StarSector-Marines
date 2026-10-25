package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A staffed building at work ({@code staffed-buildings.md}): a procedural
 * operation map's civilian warehouse, factory or shop, its crew at the stock,
 * seen through the look preset at play zoom and close over a few moments of one
 * battle, with a short run of close frames from the first map. One suite per
 * kind; opt-in, a look question rather than part of {@code all}.
 */
public abstract class StaffedBuildingLookSuite implements SnapshotSuite {

    static final long[] SEEDS = {607898L, 15312L, 4242L, 7L, 42L, 1001L, 31337L, 271828L};
    /** Maps shown: the first seeds that staffed one of this kind. */
    static final int MAPS = 2;
    static final float CLOSE_CELLS = 30f;
    /** Seconds of battle before the first still, so the crew are out on the floor. */
    static final float SETTLE_SECONDS = 12f;
    /** Seconds between stills, about one carry. */
    static final float STILL_GAP_SECONDS = 4f;
    static final int STILLS = 3;
    /** The first map also records a run of close frames, for a short clip of the crew at work. */
    static final int CLIP_FRAMES = 120;
    static final float CLIP_FRAME_SECONDS = 1f / 6f;

    private final BuildingWork.Kind kind;

    StaffedBuildingLookSuite(BuildingWork.Kind kind) {
        this.kind = kind;
    }

    @Override public String id() { return "staffed-" + kind.name().toLowerCase(Locale.ROOT) + "-look"; }

    @Override public String label() { return "Staffed " + kind.name().toLowerCase(Locale.ROOT) + ": a civilian crew at work"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        int maps = 0;
        for (long seed : SEEDS) {
            if (maps >= MAPS) break;
            try (BattleSimulation sim = BattleSetup.createPlaceholder(seed)) {
                float[] centre = crewCentre(sim, kind);
                System.out.println("[" + id() + "] seed " + seed + (centre == null ? ": none staffed"
                        : String.format(Locale.ROOT, ": crew at %.0f,%.0f", centre[0], centre[1])));
                if (centre == null) continue;
                maps++;
                run(sim, SETTLE_SECONDS);
                for (int still = 0; still < STILLS; still++) {
                    String name = String.format(Locale.ROOT, "seed%d-%02d", seed, still + 1);
                    if (still == 0) {
                        out.add(new SnapshotArtifact(name + "-play-roofed.png",
                                BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, centre[0], centre[1],
                                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, name)));
                        out.add(new SnapshotArtifact(name + "-play.png",
                                BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, centre[0], centre[1],
                                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs().render(sim, name)));
                    }
                    // The roof fades only for the player's own people inside, so the crew are seen under it.
                    out.add(new SnapshotArtifact(name + "-close.png",
                            BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, centre[0], centre[1],
                                    CLOSE_CELLS).withoutRoofs().render(sim, name)));
                    run(sim, STILL_GAP_SECONDS);
                }
                if (maps == 1) {
                    BattleReviewFrameRenderer clip = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                            centre[0], centre[1], CLOSE_CELLS).withoutRoofs();
                    for (int frame = 0; frame < CLIP_FRAMES; frame++) {
                        String name = String.format(Locale.ROOT, "clip-seed%d/frame-%03d.png", seed, frame);
                        out.add(new SnapshotArtifact(name, clip.render(sim, name)));
                        run(sim, CLIP_FRAME_SECONDS);
                    }
                }
            }
        }
        return out;
    }

    /** Where the first staffed building of this kind has its crew, or null on a map that staffed none. */
    static float[] crewCentre(BattleSimulation sim, BuildingWork.Kind kind) {
        String prefix = StaffedCrew.idPrefix(kind);
        float x = 0f, y = 0f;
        int count = 0;
        float firstX = Float.NaN, firstY = Float.NaN;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.CIVILIAN || !sim.ambientTasks().isControlling(unit)) continue;
            String name = sim.identity().name(unit);
            if (name == null || !name.startsWith(prefix)) continue;
            AmbientTaskPose pose = sim.ambientTasks().pose(unit);
            if (pose == null) continue;
            if (Float.isNaN(firstX)) { firstX = pose.worldX(); firstY = pose.worldY(); }
            // Only the first building's crew: everybody within a building's reach of its first hand.
            if (Math.abs(pose.worldX() - firstX) > 24f || Math.abs(pose.worldY() - firstY) > 24f) continue;
            x += pose.worldX(); y += pose.worldY(); count++;
        }
        return count == 0 ? null : new float[]{x / count, y / count};
    }

    private static void run(BattleSimulation sim, float seconds) {
        int ticks = Math.round(seconds / BattleSimulation.TICK_DT);
        for (int t = 0; t < ticks; t++) sim.advance(BattleSimulation.TICK_DT);
    }
}
