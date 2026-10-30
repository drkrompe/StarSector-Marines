package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehiclePlating;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.List;
import java.util.function.Function;

/**
 * {@link PdBeamScene}'s loops at 1920x1080, and the owner's pictures of the PD lasers' held beams on real generated
 * ground through the look preset (`mechs-nouns.md`, Beam weapons): the beam on a Bulwark's side arms
 * ({@code pd-beam-arm}), on a Warden's chin, the centreline nose mount ({@code pd-beam-nose}), and on a vehicle's
 * turret ring, a laser tank with the PD laser and an APC with the light PD laser ({@code pd-beam-turret}). Each fires
 * at a stationary militia picket fourteen cells east; the close view frames the shooter and the beam's whole length.
 * {@code pd-beam-crawl} records a Warden's chin beam on a stock Hound held in place fourteen cells off, a frame every
 * tick, so consecutive frames of its recording show the lance crawling over the hull ({@code BeamWander}).
 */
public final class PdBeamSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int TICKS = 300;
    private static final float PICKET_X = 30.5f;
    private static final float SHOOTER_X = 16.5f;

    public PdBeamSceneSnapshotSuite() {
        super(new PdBeamScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    /** {@code -Dsnapshot.pdBeam.shots=crawl} (comma-separated name suffixes) records only those look shots. */
    @Override protected List<LookReview.Shot> lookShots() {
        List<LookReview.Shot> all = List.of(
                mechShot("pd-beam-arm", MechVariant.BULWARK, MechWeaponComponent.BL_2),
                mechShot("pd-beam-nose", MechVariant.WARDEN, MechWeaponComponent.BL_2),
                turretShot(),
                crawlShot());
        String only = System.getProperty("snapshot.pdBeam.shots", "");
        if (only.isBlank()) return all;
        List<String> names = List.of(only.split(","));
        return all.stream().filter(s -> names.stream().anyMatch(n -> s.name().equals("pd-beam-" + n.trim())))
                .toList();
    }

    private static final int CRAWL_TICKS = 450;

    /** A Warden's chin PD laser on a stock Hound held in place, every tick recorded. */
    private static LookReview.Shot crawlShot() {
        Function<LookReview.Placement, BattleSimulation> stage = placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            float[] near = placement.openNear(PICKET_X, 11.5f);
            int tx = (int) near[0];
            int ty = (int) near[1];
            builder.squad("hound").faction(Faction.DEFENDER).size(1)
                    .at(tx - placement.site().x0(), ty - placement.site().y0())
                    .mech(new MechDeploymentSpec(MechVariant.HOUND, MechRole.BALANCED,
                            MissileReplenisherComponent.STANDARD))
                    .stationary()
                    .assigned(id -> ObjectiveAssignment.defendArea(id, tx, ty)).done();
            float[] at = placement.openNear(SHOOTER_X, 11.5f);
            int holdX = (int) at[0];
            int holdY = (int) at[1];
            builder.squad("mech").faction(Faction.MARINE).size(1)
                    .at(holdX - placement.site().x0(), holdY - placement.site().y0())
                    .mech(new MechDeploymentSpec(MechVariant.WARDEN, MechRole.BALANCED,
                            MissileReplenisherComponent.STANDARD, MechWeaponComponent.BL_2, null, null))
                    .stationary()
                    .assigned(id -> ObjectiveAssignment.defendArea(id, holdX, holdY)).done();
            SceneWorld world = builder.build();
            placement.subject("mech", world.members("mech")[0]);
            placement.subject("hound", world.members("hound")[0]);
            return world.sim();
        };
        Function<LookReview.Placement, List<LookReview.View>> views = placement -> List.of(
                LookReview.View.follow("close", placement, 18f, "mech", "hound"));
        return new LookReview.Shot("pd-beam-crawl", LookGround.verdant(), 44, 22, stage, views, CRAWL_TICKS, 1, 0,
                null);
    }

    /** One mech with {@code gun} on its arm mounts and nothing on its shoulders, holding against the picket. */
    private static LookReview.Shot mechShot(String name, MechVariant variant, MechWeaponComponent gun) {
        return LookReview.Shot.of(name, 44, 22, placement -> {
            SceneBuilder builder = picket(placement);
            float[] at = placement.openNear(SHOOTER_X, 11.5f);
            int holdX = (int) at[0];
            int holdY = (int) at[1];
            MechDeploymentSpec spec = new MechDeploymentSpec(variant, MechRole.BALANCED,
                    MissileReplenisherComponent.STANDARD, gun, null, null);
            builder.squad("mech").faction(Faction.MARINE).size(1)
                    .at(holdX - placement.site().x0(), holdY - placement.site().y0()).mech(spec)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, holdX, holdY)).done();
            SceneWorld world = builder.build();
            placement.subject("mech", world.members("mech")[0]);
            placement.subject("picket", world.members("picket")[0]);
            return world.sim();
        }, views("mech"), TICKS);
    }

    /** A laser tank with the PD laser and an APC with the light PD laser in its small omni card. */
    private static LookReview.Shot turretShot() {
        return LookReview.Shot.of("pd-beam-turret", 44, 22, placement -> {
            SceneWorld world = picket(placement).build();
            BattleSimulation sim = world.sim();
            VehicleType[] types = {VehicleType.LASER_TANK, VehicleType.HEAVY_APC};
            MechWeaponComponent[] guns = {MechWeaponComponent.BL_2, MechWeaponComponent.BL_1};
            for (int i = 0; i < types.length; i++) {
                float[] at = placement.openNear(SHOOTER_X, 8.5f + 6f * i);
                long hull = sim.convoy().spawn(types[i], List.of(), VehiclePlating.FACTORY, VehicleEngine.STOCK,
                        guns[i], Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
                sim.convoy().body(hull).teleport(at[0], at[1], 0f);
                placement.subject("hull" + i, hull);
            }
            placement.subject("picket", world.members("picket")[0]);
            return sim;
        }, views("hull0", "hull1"), TICKS);
    }

    private static SceneBuilder picket(LookReview.Placement placement) {
        SceneBuilder builder = placement.holdouts(placement.builder());
        float[] near = placement.openNear(PICKET_X, 11.5f);
        int px = (int) near[0] - placement.site().x0();
        int py = (int) near[1] - placement.site().y0();
        builder.squad("picket").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(8)
                .inFile(px, py - 7, 0, 2).primary("weapon.smg").stationary().done();
        return builder;
    }

    /** At play zoom on the shooters and the picket, and close on the shooters with the beam's length in frame. */
    private static Function<LookReview.Placement, List<LookReview.View>> views(String... shooters) {
        return placement -> {
            String[] all = new String[shooters.length + 1];
            System.arraycopy(shooters, 0, all, 0, shooters.length);
            all[shooters.length] = "picket";
            return List.of(LookReview.View.followAtPlay(placement, all),
                    LookReview.View.follow("close", placement, 22f, all));
        };
    }
}
