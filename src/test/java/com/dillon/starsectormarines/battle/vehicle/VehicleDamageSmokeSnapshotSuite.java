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
 * Damage smoke on live hulls ({@code VehicleDamageFxSystem}): four breakthrough tanks on real ground, west to east
 * a hull worn nearly through its plates (thin engine-deck smoke), one whose own structure is failing (heavier), one
 * crippled (the running gear bursts, then smokes) and one whose turret has been shot away (a burst on the ring,
 * then a smoulder), played for six seconds at play zoom and close. Owner-facing look frames only; there is no verdict.
 */
public final class VehicleDamageSmokeSnapshotSuite implements SnapshotSuite {

    private static final float SPACING = 6.5f;
    private static final float FACING = 20f;

    @Override public String id() { return "vehicle-damage-smoke"; }

    @Override public String label() { return "Vehicle damage smoke"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    static LookReview.Shot shot() {
        float centreX = 22f;
        float centreY = 8f;
        return new LookReview.Shot("tank-smoke", LookGround.verdant(),
                44, 16, placement -> {
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            for (int i = 0; i < 4; i++) {
                float[] at = placement.openNear(centreX + (i - 1.5f) * SPACING, centreY);
                long tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.MARINE,
                        VehicleMission.deployed(at[0], at[1]));
                sim.convoy().body(tank).teleport(at[0], at[1], FACING);
                HullLocations locations = sim.convoy().locations(tank);
                float hp = sim.world().hp(tank);
                switch (i) {
                    case 0 -> locations.stageArmorShare(0.15f);
                    case 1 -> {
                        locations.stageArmorShare(0.33f);
                        float hullMax = sim.world().maxHp(tank) - locations.maxDrive()
                                - (locations.turret() != null ? locations.turret().maxStructure() : 0f);
                        sim.world().setHp(tank, hp - 0.8f * hullMax);
                    }
                    case 2 -> {
                        locations.stageDriveLost();
                        sim.world().setHp(tank, hp - locations.maxDrive());
                    }
                    default -> {
                        locations.turret().stageDestroyed();
                        sim.world().setHp(tank, hp - locations.turret().maxStructure());
                    }
                }
                placement.subject("tank-" + i, tank);
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "tank-0", "tank-1", "tank-2", "tank-3"),
                LookReview.View.follow("close", placement, 30f, "tank-0", "tank-1", "tank-2", "tank-3")),
                180, 3, 2);
    }
}
