package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.CompressedFrameList;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * How the LRM carrier looks in play, for the owner rather than for a verdict: an LRM carrier against a tank on the
 * armor matchup's open ground, drawn as a player sees it ({@link BattleReviewFrameRenderer#asPresentation}: no
 * markers, no captions) with impact effects, contrails and shadows on. Two framings, both following the fight: play
 * zoom ({@value #PLAY_CELLS} cells across, carrier and target in one frame) and close ({@value #CLOSE_CELLS} cells
 * on the carrier). Stills of the carrier idle and mid-salvo, and the close view of its first salvo as a GIF.
 * Opt-in: a look study, not part of {@code all}.
 */
public final class LrmCarrierLookSuite implements SnapshotSuite {

    static final int WIDTH = 1920;
    static final int HEIGHT = 1080;
    static final float PLAY_CELLS = 40f;
    static final float CLOSE_CELLS = 12f;
    /** Ticks between GIF frames, and how long the GIF runs from the first salvo. */
    static final int GIF_EVERY_TICKS = 5;
    static final int GIF_LEAD_TICKS = 60;
    static final int GIF_TICKS = 300;
    /** Ticks after a salvo's last missile for the still of the pod standing raised in its own smoke. */
    static final int AFTER_SALVO_TICKS = 12;
    static final long SEED = 20261023L;

    @Override public String id() { return "lrm-carrier-look"; }

    @Override public String label() { return "LRM carrier look: idle and firing on real ground, play zoom and close"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        Watcher watcher = new Watcher(context.modRoot());
        ArmorPlay.play(ArmorScenarios.byId("open-ground"), ArmorSide.parse("lrm-carrier"),
                ArmorSide.parse("breakthrough-tank"), SEED, watcher);
        return watcher.artifacts();
    }

    private static final class Watcher implements FightWatcher {
        private final BattleReviewFrameRenderer play;
        private final BattleReviewFrameRenderer close;
        private final ReviewImpactFx playFx;
        private final ReviewImpactFx closeFx;
        private final List<SnapshotArtifact> stills = new ArrayList<>();
        private final List<BufferedImage> gif = new CompressedFrameList();
        private BufferedImage idlePlay, idleClose;
        private boolean salvoTaken;
        private int firstLaunch = -1;
        private int idleTick = -1;
        private int salvoEnd = -1;
        private boolean afterTaken;

        Watcher(Path modRoot) {
            play = new BattleReviewFrameRenderer(modRoot, WIDTH, HEIGHT, 0f, 0f, PLAY_CELLS)
                    .withImpactEffects().withUnitShadows().asPresentation();
            close = new BattleReviewFrameRenderer(modRoot, WIDTH, HEIGHT, 0f, 0f, CLOSE_CELLS)
                    .withImpactEffects().withUnitShadows().asPresentation();
            playFx = new ReviewImpactFx(modRoot);
            closeFx = new ReviewImpactFx(modRoot);
        }

        @Override
        public void observe(BattleSimulation sim, int tick, boolean damageDealt, String status, long[][] sides) {
            long carrier = find(sim, VehicleType.LRM_CARRIER);
            if (carrier == 0L) return;
            GroundTurret turret = sim.convoy().turret(carrier);
            // Idle: the carrier on the move before it has anything to shoot at, three seconds in so the launcher has
            // come round from its spawn bearing onto the hull's.
            if (idleTick < 0 && tick >= 90 && turret.targetId == 0L) {
                idleTick = tick;
                aim(sim, carrier, false);
                idlePlay = draw(play, sim, playFx);
                idleClose = draw(close, sim, closeFx);
            }
            if (firstLaunch < 0 && turret.burstRemaining > 0) firstLaunch = tick;
            int burst = sim.convoy().vehicleType(carrier).turretStructure().mount.weapon.burstCount;
            if (!salvoTaken && turret.burstRemaining > 0 && turret.burstRemaining <= burst / 2) {
                salvoTaken = true;
                aim(sim, carrier, true);
                stills.add(new SnapshotArtifact("02-play-salvo.png", draw(play, sim, playFx)));
                stills.add(new SnapshotArtifact("04-close-salvo.png", draw(close, sim, closeFx)));
            }
            if (salvoTaken && salvoEnd < 0 && turret.burstRemaining == 0) salvoEnd = tick;
            if (!afterTaken && salvoEnd >= 0 && tick >= salvoEnd + AFTER_SALVO_TICKS) {
                afterTaken = true;
                aim(sim, carrier, false);
                stills.add(new SnapshotArtifact("05-close-raised.png", draw(close, sim, closeFx)));
            }
            boolean inWindow = firstLaunch >= 0 ? tick < firstLaunch + GIF_TICKS : turret.targetId != 0L;
            if (inWindow && tick % GIF_EVERY_TICKS == 0 && (firstLaunch >= 0 || gif.size() < GIF_LEAD_TICKS
                    / GIF_EVERY_TICKS)) {
                aim(sim, carrier, false);
                gif.add(draw(close, sim, closeFx));
            }
        }

        @Override
        public void advanced(BattleSimulation sim) {
            playFx.feed(sim);
            closeFx.feed(sim);
        }

        @Override
        public void finish(BattleSimulation sim, int tick, String status, long[][] sides) { }

        /** Play zoom frames the carrier and the nearest tank together; close follows the carrier. */
        private void aim(BattleSimulation sim, long carrier, boolean withTarget) {
            float cx = sim.convoy().body(carrier).x;
            float cy = sim.convoy().body(carrier).y;
            long tank = find(sim, VehicleType.BREAKTHROUGH_TANK);
            float px = cx, py = cy;
            if (withTarget && tank != 0L) {
                px = (cx + sim.convoy().body(tank).x) / 2f;
                py = (cy + sim.convoy().body(tank).y) / 2f;
            }
            play.refocus(px, py, PLAY_CELLS);
            close.refocus(cx, cy, CLOSE_CELLS);
        }

        private static BufferedImage draw(BattleReviewFrameRenderer renderer, BattleSimulation sim,
                                          ReviewImpactFx fx) {
            try {
                return renderer.render(sim, "", null, fx);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        private static long find(BattleSimulation sim, VehicleType type) {
            for (long id : sim.getConvoyVehicleIds()) {
                if (sim.convoy().vehicleType(id) == type && sim.convoy().isTargetable(id)) return id;
            }
            return 0L;
        }

        List<SnapshotArtifact> artifacts() {
            List<SnapshotArtifact> out = new ArrayList<>();
            if (idlePlay != null) out.add(new SnapshotArtifact("01-play-idle.png", idlePlay));
            out.addAll(stills);
            if (idleClose != null) out.add(new SnapshotArtifact("03-close-idle.png", idleClose));
            if (!gif.isEmpty()) out.add(SnapshotArtifact.animation("06-close-salvo.gif", gif, 166));
            return out;
        }
    }
}
