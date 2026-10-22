package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Buildings burning by material on real ground (`fire-nouns.md`, Buildings): the
 * {@link FireBuildingsStage} houses, a light-walled one and one built fortified,
 * as a grass front reaches them, as the light house burns, and after, with a
 * close look at each house once the fire is out.
 */
public final class FireBuildingsSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final float PLAY_CELLS = 60f, CLOSE_CELLS = 24f;

    @Override public String id() { return "fire-buildings"; }

    @Override public String label() { return "A grass fire into a light-walled house and a fortified one on the green colony"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        // A renderer first: it installs the registries the colony is built from.
        new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        int wallsCaught = -1, roomsPeak = -1, end;
        int peakRooms = 0;
        float vx, vy;
        int[] light, fort;
        FireBuildingsStage first = FireBuildingsStage.build();
        try (var sim = first.sim) {
            light = first.light;
            fort = first.fortified;
            vx = first.viewX();
            vy = first.viewY();
            int w = sim.getGrid().getWidth(), tick = 0;
            for (; tick < FireBuildingsScene.MAX_TICKS; tick++) {
                first.fire.tick(tick);
                if (tick % FireField.STEP_TICKS != 0) continue;
                int rooms = 0;
                for (int i = 0, n = first.fire.burningCount(); i < n; i++) {
                    int idx = first.fire.burningCell(i);
                    if (!FireBuildingsStage.inside(light, idx % w, idx / w)) continue;
                    int mask = first.fire.burnedMaskAt(idx);
                    if (wallsCaught < 0 && FireFuel.STRUCTURE.in(mask)) wallsCaught = tick;
                    if (FireFuel.FURNISHING.in(mask)) rooms++;
                }
                if (rooms > peakRooms) { peakRooms = rooms; roomsPeak = tick; }
                if (tick > 300 && !first.fire.isActive()) break;
            }
            end = tick;
        }
        BattleReviewFrameRenderer play = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, vx, vy, PLAY_CELLS);
        BattleReviewFrameRenderer lightClose = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                FireBuildingsStage.cx(light), FireBuildingsStage.cy(light), CLOSE_CELLS);
        BattleReviewFrameRenderer fortClose = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                FireBuildingsStage.cx(fort), FireBuildingsStage.cy(fort), CLOSE_CELLS);
        record Shot(int tick, String file, BattleReviewFrameRenderer renderer, String view) { }
        List<Shot> shots = new ArrayList<>();
        shots.add(new Shot(Math.max(0, wallsCaught), "01-front-reaches-houses.png", play, "the front reaches both houses; the light one's walls catch"));
        shots.add(new Shot(Math.max(0, roomsPeak), "02-light-house-burning.png", play, "the light house burns room by room; the fortified one does not catch"));
        shots.add(new Shot(end + 30, "03-after.png", play, "after: the light house opened to rubble, the fortified one standing"));
        shots.add(new Shot(end + 30, "04-light-house-close.png", lightClose, "close: the light house, walls fallen, rooms burnt"));
        shots.add(new Shot(end + 30, "05-fortified-house-close.png", fortClose, "close: the fortified house (staged: walls raised to 150 HP), untouched"));
        shots.sort((a, b) -> Integer.compare(a.tick, b.tick));
        List<SnapshotArtifact> out = new ArrayList<>();
        FireBuildingsStage stage = FireBuildingsStage.build();
        try (var sim = stage.sim) {
            int tick = 0;
            for (Shot shot : shots) {
                for (; tick < shot.tick; tick++) stage.fire.tick(tick);
                out.add(new SnapshotArtifact(shot.file, shot.renderer.render(sim, caption(shot.view, tick))));
            }
        }
        out.sort((a, b) -> a.relativePath().compareTo(b.relativePath()));
        return out;
    }

    private static String caption(String view, int tick) {
        return String.format(Locale.ROOT, "fire, buildings | %s | %d:%02d", view, tick / 1800, (tick % 1800) / 30);
    }
}
