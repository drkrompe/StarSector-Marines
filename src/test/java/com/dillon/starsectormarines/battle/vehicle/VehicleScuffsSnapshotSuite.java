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

import java.util.List;

/**
 * Worn plate on a live hull ({@code body-scuffs.md}): four breakthrough tanks side by side on real ground, every plate
 * at 100, 66, 33 and 10% of its armour, at play zoom and close. Owner-facing look frames only; there is no verdict.
 */
public final class VehicleScuffsSnapshotSuite implements SnapshotSuite {

    /** Each tank's plates, as a share of their armour, west to east. */
    static final float[] SHARES = {1f, 0.66f, 0.33f, 0.10f};
    private static final float SPACING = 6.5f;
    private static final float FACING = 20f;

    @Override public String id() { return "vehicle-scuffs"; }

    @Override public String label() { return "Vehicle scuffs (worn plate)"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    static LookReview.Shot shot() {
        float centreX = 22f;
        float centreY = 8f;
        return new LookReview.Shot("tank-scuffs", LookGround.verdant(),
                44, 16, placement -> {
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            for (int i = 0; i < SHARES.length; i++) {
                float[] at = placement.openNear(centreX + (i - 1.5f) * SPACING, centreY);
                long tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.MARINE,
                        VehicleMission.deployed(at[0], at[1]));
                sim.convoy().body(tank).teleport(at[0], at[1], FACING);
                sim.convoy().locations(tank).stageArmorShare(SHARES[i]);
                placement.subject("tank-" + i, tank);
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "tank-0", "tank-1", "tank-2", "tank-3"),
                LookReview.View.follow("close", placement, 30f, "tank-0", "tank-1", "tank-2", "tank-3")),
                6, 6, 1);
    }
}
