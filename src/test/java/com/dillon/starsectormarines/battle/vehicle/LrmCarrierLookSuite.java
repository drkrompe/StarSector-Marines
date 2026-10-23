package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * How the LRM carrier looks in play, for the owner rather than for a verdict: the look preset on real generated
 * colony ground ({@link LookReview}), effects, contrails and shadows on, no markers or captions. Two shots:
 * {@code lrm-idle}, the carrier with nothing to shoot at and its pod lying flat along the hull; and
 * {@code lrm-salvo}, the carrier with a pinned tank 36 cells east that a scout car beside it is watching, so the
 * pod comes round, stands up and ripples its salvos. Each at play zoom and close on the carrier; the salvo also
 * close on the tank. Opt-in: a look study, not part of {@code all}. The verdicts are {@code missile-carriers}'.
 */
public final class LrmCarrierLookSuite implements SnapshotSuite {

    /** Cells across the close view on the carrier: the pod, its tubes and its launch smoke fill the frame. */
    static final float CLOSE_CELLS = 12f;
    static final int IDLE_TICKS = 180;
    static final int SALVO_TICKS = 900;

    @Override public String id() { return "lrm-carrier-look"; }

    @Override public String label() { return "LRM carrier look: idle and firing on real ground, play zoom and close"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        out.addAll(LookReview.record(context.modRoot(), idle()));
        out.addAll(LookReview.record(context.modRoot(), salvo()));
        return out;
    }

    static LookReview.Shot idle() {
        return LookReview.Shot.of("lrm-idle", 32, 20, placement -> {
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            float[] at = placement.openNear(16f, 10f);
            long carrier = sim.convoy().spawn(VehicleType.LRM_CARRIER, Faction.MARINE,
                    VehicleMission.deployed(at[0], at[1]));
            sim.convoy().body(carrier).teleport(at[0], at[1], -90f);
            sim.convoy().turret(carrier).facingDeg = -90f;
            return sim;
        }, placement -> {
            LookGround.Site site = placement.site();
            return List.of(LookReview.View.play(site.x(16f), site.y(10f)),
                    LookReview.View.close("carrier", site.x(16f), site.y(10f), CLOSE_CELLS));
        }, IDLE_TICKS);
    }

    static LookReview.Shot salvo() {
        return LookReview.Shot.of("lrm-salvo", 64, 36, placement -> {
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            float[] carrierAt = placement.openNear(8.5f, 18.5f);
            float[] tankAt = placement.openNear(44.5f, 18.5f);
            float[] scoutAt = placement.openNear(6.5f, 26.5f);
            long carrier = placement.subject("carrier", sim.convoy().spawn(VehicleType.LRM_CARRIER, Faction.MARINE,
                    VehicleMission.deployed(carrierAt[0], carrierAt[1])));
            sim.convoy().body(carrier).teleport(carrierAt[0], carrierAt[1], 0f);
            long tank = placement.subject("tank", sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.DEFENDER,
                    VehicleMission.deployed(tankAt[0], tankAt[1])));
            sim.convoy().body(tank).teleport(tankAt[0], tankAt[1], 180f);
            pin(sim, tank);
            long car = sim.convoy().spawn(VehicleType.SCOUT_CAR, Faction.MARINE,
                    VehicleMission.deployed(scoutAt[0], scoutAt[1]));
            sim.convoy().body(car).teleport(scoutAt[0], scoutAt[1], 0f);
            return sim;
        }, placement -> {
            return List.of(LookReview.View.followAtPlay(placement, "carrier", "tank"),
                    LookReview.View.follow("carrier", placement, CLOSE_CELLS, "carrier"),
                    LookReview.View.follow("target", placement, 24f, "tank"));
        }, SALVO_TICKS);
    }

    /** Holds a Defender hull where it stands: a paused authored route keeps engagement off it; its turret works. */
    private static void pin(BattleSimulation sim, long id) {
        VehicleMission mission = sim.convoy().mission(id);
        GroundBody body = sim.convoy().body(id);
        mission.authoredRouteX = new float[] {body.x};
        mission.authoredRouteY = new float[] {body.y};
        mission.authoredRoutePaused = true;
    }
}
