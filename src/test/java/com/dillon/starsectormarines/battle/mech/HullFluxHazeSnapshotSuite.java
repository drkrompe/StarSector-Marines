package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;
import java.util.Locale;

/**
 * Flux on parked hulls ({@code FluxHaze}, off the vents {@code VehicleType} registers for each hull's art): a row each
 * of Onagers, Scorpions, Hegemony APCs and laser tanks on real ground, west to east at 0, 60 and 85% of their hull
 * reservoir and venting from full, held there at play zoom and close. It sits beside {@link MechFluxHazeSnapshotSuite}
 * because a hull's reservoir is a {@link MechFlux} and only this package can stage one. Owner-facing look frames only;
 * there is no verdict.
 */
public final class HullFluxHazeSnapshotSuite implements SnapshotSuite {

    private static final VehicleType[] ROWS = {
            VehicleType.BREAKTHROUGH_TANK, VehicleType.MEDIUM_TANK, VehicleType.HEAVY_APC, VehicleType.LASER_TANK};
    private static final float SPACING = 8f;
    private static final float ROW_SPACING = 5.5f;

    @Override public String id() { return "hull-flux"; }

    @Override public String label() { return "Hull flux haze"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    private static String key(int row, int i) {
        return ROWS[row].name().toLowerCase(Locale.ROOT) + "-" + i;
    }

    static LookReview.Shot shot() {
        float[] levels = MechFluxHazeSnapshotSuite.LEVELS;
        return new LookReview.Shot("hull-flux", LookGround.verdant(), 44, 28, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            for (int row = 0; row < ROWS.length; row++) {
                for (int i = 0; i < levels.length; i++) {
                    float[] at = placement.openNear(22f + (i - 1.5f) * SPACING, 5f + row * ROW_SPACING);
                    long id = sim.convoy().spawn(ROWS[row], Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
                    sim.convoy().body(id).teleport(at[0], at[1], -90f);
                    placement.subject(key(row, i), id);
                    GroundTurret turret = sim.convoy().turret(id);
                    MechFlux flux = turret.reservoir;
                    flux.stage(levels[i] * flux.capacity(), i == levels.length - 1);
                }
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, key(0, 0), key(ROWS.length - 1, 3)),
                LookReview.View.follow("close", placement, 30f, key(0, 0), key(1, 3))),
                120, 3, 2);
    }
}
