package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * The fire-support APC scene's frames, from the same run that produced its verdicts: both loops at 1920x1080 and
 * 100% scale, whole map plus a full-size close view framing the mortar carrier, the position and the troop
 * carrier's landing, with impact effects drawn so the shells are visible landing. Every fourth close frame is also written as a
 * PNG still.
 *
 * <p>Those loops are verdict evidence. The owner is shown {@code look/apc-shelling-*}: the mortar carrier shelling a
 * militia picket on real generated ground through the look preset, at play zoom, on the picket and close on the
 * carrier's turret; {@code look/carrier-idle-*}: the carrier parked, its head turned and quiet, so the shape is judged
 * with nothing drawn over it; and {@code look/carrier-wrecks-*}: the mortar carrier, the LRM carrier and the SRM technical
 * knocked out side by side, so their wreck turrets can be read against each other.
 */
public final class FireSupportApcSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public FireSupportApcSceneSnapshotSuite() {
        super(new FireSupportApcScene(15), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected float[] closeView(String loopId) {
        return new float[] {
                (FireSupportApcScene.FIRE_SUPPORT_X + FireSupportApcScene.POSITION_X) / 2f + 1f,
                (FireSupportApcScene.FIRE_SUPPORT_Y + FireSupportApcScene.LZ_Y) / 2f + 1f,
                44f};
    }

    @Override protected float closeViewScale() { return 1f; }

    /**
     * The mortar carrier deployed at the west of a 64x36 window and an eight-strong militia picket strung out 30 cells
     * east of it (inside the mortar's 38, beyond the picket's guns): the exchange at play zoom, and the picket
     * close as the shells land.
     */
    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(idle(), shelling(), armor(), wrecks(), fitted(), fittedWrecks(), fittedRotary(),
                lightWeapons(), lightWeaponsFire(), laserParts(), laserArms());
    }

    private static LookReview.Shot shelling() {
        return LookReview.Shot.of("apc-shelling", 64, 36, placement -> {
            float[] picket = placement.openNear(42f, 18f);
            int px = (int) picket[0] - placement.site().x0();
            int py = (int) picket[1] - placement.site().y0();
            SceneBuilder builder = placement.holdouts(placement.builder());
            // Strung out three cells apart down the window, so one bomb takes one man and the shelling lasts.
            builder.squad("picket").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(8).inFile(px, py - 10, 0, 3)
                    .primary("weapon.smg").stationary().done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            float[] at = placement.openNear(12.5f, 18.5f);
            long apc = sim.convoy().spawn(VehicleType.FIRE_SUPPORT_APC, Faction.MARINE,
                    VehicleMission.deployed(at[0], at[1]));
            sim.convoy().body(apc).teleport(at[0], at[1], 0f);
            placement.subject("apc", apc);
            placement.subject("picket", world.members("picket")[0]);
            return sim;
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "apc", "picket"),
                LookReview.View.follow("picket", placement, 24f, "picket"),
                LookReview.View.follow("turret", placement, 9f, "apc")), 1500);
    }

    /**
     * The mortar carrier parked with nothing to shoot at, its turret turned off the hull's axis: the head's shape with
     * no flash or smoke over it, close and at play zoom.
     */
    private static LookReview.Shot idle() {
        return LookReview.Shot.of("carrier-idle", 24, 14, placement -> {
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            float[] at = placement.openNear(12f, 7f);
            long apc = sim.convoy().spawn(VehicleType.FIRE_SUPPORT_APC, Faction.MARINE,
                    VehicleMission.deployed(at[0], at[1]));
            sim.convoy().body(apc).teleport(at[0], at[1], 0f);
            sim.convoy().turret(apc).facingDeg = 35f;
            placement.subject("apc", apc);
            return sim;
        }, placement -> List.of(LookReview.View.follow("close", placement, 6f, "apc"),
                LookReview.View.followAtPlay(placement, "apc")), 60).withStills(2);
    }

    /** The mortar carrier shelling a parked enemy APC 30 cells east: what its bombs do to a machine. */
    private static LookReview.Shot armor() {
        return LookReview.Shot.of("apc-shelling-armor", 64, 36, placement -> {
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            float[] at = placement.openNear(12.5f, 18.5f);
            long apc = sim.convoy().spawn(VehicleType.FIRE_SUPPORT_APC, Faction.MARINE,
                    VehicleMission.deployed(at[0], at[1]));
            sim.convoy().body(apc).teleport(at[0], at[1], 0f);
            float[] there = placement.openNear(42.5f, 18.5f);
            long target = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.DEFENDER,
                    VehicleMission.deployed(there[0], there[1]));
            sim.convoy().body(target).teleport(there[0], there[1], 180f);
            placement.subject("apc", apc);
            placement.subject("target", target);
            return sim;
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "apc", "target"),
                LookReview.View.follow("target", placement, 14f, "target")), 1500);
    }

    /**
     * Six hulls each carrying a mech weapon its gun card takes, parked with their turrets turned off the hull's axis:
     * how a fitted weapon is drawn, close and at play zoom.
     */
    private static LookReview.Shot fitted() {
        return LookReview.Shot.of("fitted-turrets", 32, 14, placement -> {
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            VehicleType[] types = FITTED_TYPES;
            MechWeaponComponent[] guns = FITTED_GUNS;
            for (int i = 0; i < types.length; i++) {
                float[] at = placement.openNear(5f + 4.4f * i, 7f);
                long hull = sim.convoy().spawn(types[i], VehiclePlating.FACTORY, VehicleEngine.STOCK, guns[i],
                        Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
                sim.convoy().body(hull).teleport(at[0], at[1], 0f);
                sim.convoy().turret(hull).facingDeg = 25f - 10f * i;
                placement.subject("hull" + i, hull);
            }
            return sim;
        }, placement -> List.of(LookReview.View.follow("close", placement, 30f, "hull0", "hull1", "hull2", "hull3",
                "hull4", "hull5"), LookReview.View.follow("tank", placement, 8f, "hull0", "hull1")), 60).withStills(2);
    }

    /** The six fitted hulls destroyed on the first tick: a wreck keeps the weapon it carried, charred, on its ring. */
    private static LookReview.Shot fittedWrecks() {
        return LookReview.Shot.of("fitted-wrecks", 32, 14, placement -> {
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            long[] hulls = new long[FITTED_TYPES.length];
            for (int i = 0; i < hulls.length; i++) {
                float[] at = placement.openNear(5f + 4.4f * i, 7f);
                hulls[i] = sim.convoy().spawn(FITTED_TYPES[i], VehiclePlating.FACTORY, VehicleEngine.STOCK,
                        FITTED_GUNS[i], Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
                sim.convoy().body(hulls[i]).teleport(at[0], at[1], 0f);
                sim.convoy().turret(hulls[i]).facingDeg = 25f - 10f * i;
                placement.subject("hull" + i, hulls[i]);
            }
            boolean[] done = {false};
            placement.beforeTick(s -> {
                if (done[0]) return;
                done[0] = true;
                for (long hull : hulls) s.convoy().destroy(hull);
            });
            return sim;
        }, placement -> List.of(LookReview.View.follow("close", placement, 30f, "hull0", "hull1", "hull2", "hull3",
                "hull4", "hull5"), LookReview.View.follow("tank", placement, 8f, "hull0", "hull1")), 1500)
                .withStills(150, 249);
    }

    private static final VehicleType[] FITTED_TYPES = {VehicleType.BREAKTHROUGH_TANK, VehicleType.BREAKTHROUGH_TANK,
            VehicleType.LASER_TANK, VehicleType.LRM_CARRIER, VehicleType.SRM_TECHNICAL, VehicleType.FIRE_SUPPORT_APC};
    private static final MechWeaponComponent[] FITTED_GUNS = {MechWeaponComponent.HC_20,
            MechWeaponComponent.HC_10, MechWeaponComponent.LL_3, MechWeaponComponent.SRM_5,
            MechWeaponComponent.LRM_5, MechWeaponComponent.CHAINGUN};

    private static final VehicleType[] LIGHT_TYPES = {VehicleType.HEAVY_APC, VehicleType.HEAVY_APC,
            VehicleType.MILITIA_TECHNICAL, VehicleType.SRM_TECHNICAL, VehicleType.HOVER_SCOUT,
            VehicleType.DOZER_BREACHER};
    private static final MechWeaponComponent[] LIGHT_GUNS = {MechWeaponComponent.LIGHT_MACHINE_GUN,
            MechWeaponComponent.BL_1, MechWeaponComponent.SRM_3_QS,
            MechWeaponComponent.LIGHT_MACHINE_GUN, MechWeaponComponent.BL_1,
            MechWeaponComponent.SRM_3_QS};

    /** Six small hulls each carrying a light weapon in its small omni card, parked with turrets turned off axis. */
    private static LookReview.Shot lightWeapons() {
        return LookReview.Shot.of("light-weapons", 32, 14, placement -> {
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            for (int i = 0; i < LIGHT_TYPES.length; i++) {
                float[] at = placement.openNear(5f + 4.4f * i, 7f);
                long hull = sim.convoy().spawn(LIGHT_TYPES[i], VehiclePlating.FACTORY, VehicleEngine.STOCK,
                        LIGHT_GUNS[i], Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
                sim.convoy().body(hull).teleport(at[0], at[1], 0f);
                sim.convoy().turret(hull).facingDeg = 25f - 10f * i;
                placement.subject("hull" + i, hull);
            }
            return sim;
        }, placement -> List.of(LookReview.View.follow("close", placement, 30f, "hull0", "hull1", "hull2", "hull3",
                "hull4", "hull5"), LookReview.View.follow("technical", placement, 8f, "hull2", "hull3")), 60)
                .withStills(2);
    }

    /** Three small hulls with a light weapon each, a stationary picket 18 cells east: each fires as the part does. */
    private static LookReview.Shot lightWeaponsFire() {
        return LookReview.Shot.of("light-weapons-fire", 48, 24, placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            int px = (int) placement.openNear(32.5f, 12.5f)[0] - placement.site().x0();
            int py = (int) placement.openNear(32.5f, 12.5f)[1] - placement.site().y0();
            builder.squad("hostile").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6)
                    .inFile(px, py - 6, 0, 2).primary("weapon.smg").stationary().done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            for (int i = 0; i < 3; i++) {
                float[] at = placement.openNear(12.5f, 7.5f + 5f * i);
                long hull = sim.convoy().spawn(LIGHT_TYPES[i], VehiclePlating.FACTORY, VehicleEngine.STOCK,
                        LIGHT_GUNS[i], Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
                sim.convoy().body(hull).teleport(at[0], at[1], 0f);
                placement.subject("hull" + i, hull);
            }
            return sim;
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "hull0", "hull1", "hull2")), 300);
    }

    /** The laser ladder, heaviest first: the hull's own gun, then the two heavy parts, the two medium, the two light. */
    private static final MechWeaponComponent[] LASER_GUNS = {null, MechWeaponComponent.PL_3,
            MechWeaponComponent.PL_3_SWEEP, MechWeaponComponent.PL_2, MechWeaponComponent.BL_2,
            MechWeaponComponent.PL_1, MechWeaponComponent.BL_1};

    /**
     * Seven laser tanks in two columns, the hull's own gun and each laser part of the ladder, firing at a stationary
     * picket 24 cells east: the heavy parts as the tank's own white and red heads, the rest as their own drawings.
     */
    private static LookReview.Shot laserParts() {
        return LookReview.Shot.of("laser-parts", 48, 24, placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            int px = (int) placement.openNear(34.5f, 12.5f)[0] - placement.site().x0();
            int py = (int) placement.openNear(34.5f, 12.5f)[1] - placement.site().y0();
            builder.squad("hostile").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6)
                    .inFile(px, py - 6, 0, 2).primary("weapon.smg").stationary().done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            for (int i = 0; i < LASER_GUNS.length; i++) {
                boolean second = i >= 3;
                float[] at = second ? placement.openNear(17.5f, 4.5f + 4.6f * (i - 3))
                        : placement.openNear(10.5f, 7.5f + 5f * i);
                long hull = sim.convoy().spawn(VehicleType.LASER_TANK, VehiclePlating.FACTORY,
                        VehicleEngine.STOCK, LASER_GUNS[i], Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
                sim.convoy().body(hull).teleport(at[0], at[1], 0f);
                placement.subject("hull" + i, hull);
            }
            return sim;
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "hull0", "hull1", "hull2", "hull3", "hull4",
                "hull5", "hull6"),
                LookReview.View.follow("tanks", placement, 22f, "hull0", "hull1", "hull2", "hull3", "hull4", "hull5",
                        "hull6")), 300);
    }

    /** The four lasers a side arm takes (the heavy two are nose guns), a pair on each arm of four Bulwarks. */
    private static LookReview.Shot laserArms() {
        return mechLook("laser-arms", MechVariant.BULWARK, new MechWeaponComponent[] {MechWeaponComponent.BL_2,
                MechWeaponComponent.BL_1, null, MechWeaponComponent.PL_2,
                MechWeaponComponent.PL_1, null});
    }

    /**
     * Mechs of {@code variant} in two rows of three, row-major, each carrying its gun (a null leaves the place empty),
     * firing at a stationary picket: one close view a row, named after the shot.
     */
    private static LookReview.Shot mechLook(String name, MechVariant variant, MechWeaponComponent[] guns) {
        return mechLook(name, variant, guns, false);
    }

    /** As above; with {@code closeUps}, also one close view a mech, named {@code mech0} on. */
    private static LookReview.Shot mechLook(String name, MechVariant variant, MechWeaponComponent[] guns,
            boolean closeUps) {
        return LookReview.Shot.of(name, 48, 24, placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            int px = (int) placement.openNear(36.5f, 12.5f)[0] - placement.site().x0();
            int py = (int) placement.openNear(36.5f, 12.5f)[1] - placement.site().y0();
            builder.squad("hostile").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6)
                    .inFile(px, py - 6, 0, 2).primary("weapon.smg").stationary().done();
            for (int i = 0; i < guns.length; i++) {
                if (guns[i] == null) continue;
                float[] at = placement.openNear(8f + 6.5f * (i % 3), 7f + 8f * (i / 3));
                int x = (int) at[0] - placement.site().x0();
                int y = (int) at[1] - placement.site().y0();
                int holdX = (int) at[0];
                int holdY = (int) at[1];
                MechDeploymentSpec spec = new MechDeploymentSpec(variant, MechRole.BALANCED,
                        MissileReplenisherComponent.STANDARD, guns[i], MechWeaponComponent.SRM_5, MechWeaponComponent.SRM_5);
                builder.squad("mech" + i).faction(Faction.MARINE).size(1).at(x, y).mech(spec)
                        .assigned(id -> ObjectiveAssignment.defendArea(id, holdX, holdY)).done();
            }
            SceneWorld world = builder.build();
            for (int i = 0; i < guns.length; i++) {
                if (guns[i] != null) placement.subject("mech" + i, world.members("mech" + i)[0]);
            }
            return world.sim();
        }, placement -> {
            List<LookReview.View> views = new ArrayList<>(List.of(
                    LookReview.View.follow("first", placement, 16f, rowOf(guns, 0)),
                    LookReview.View.follow("second", placement, 16f, rowOf(guns, 1))));
            if (closeUps) {
                for (int i = 0; i < guns.length; i++) {
                    if (guns[i] != null) views.add(LookReview.View.follow("mech" + i, placement, 6f, "mech" + i));
                }
            }
            return views;
        }, 300);
    }

    private static String[] rowOf(MechWeaponComponent[] guns, int row) {
        List<String> names = new ArrayList<>();
        for (int i = row * 3; i < row * 3 + 3; i++) if (guns[i] != null) names.add("mech" + i);
        return names.toArray(new String[0]);
    }

    /** A tank on its own with a chaingun in the gun card, the enemy 20 cells off: it spins up and fires on its own. */
    private static LookReview.Shot fittedRotary() {
        return LookReview.Shot.of("fitted-chaingun", 48, 24, placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            builder.squad("hostile").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6)
                    .inFile((int) placement.openNear(30.5f, 12.5f)[0] - placement.site().x0(),
                            (int) placement.openNear(30.5f, 12.5f)[1] - placement.site().y0() - 6, 0, 2)
                    .primary("weapon.smg").stationary().done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            float[] at = placement.openNear(10.5f, 12.5f);
            long tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, VehiclePlating.FACTORY,
                    VehicleEngine.STOCK, MechWeaponComponent.CHAINGUN, Faction.MARINE,
                    VehicleMission.deployed(at[0], at[1]));
            sim.convoy().body(tank).teleport(at[0], at[1], 0f);
            placement.subject("tank", tank);
            return sim;
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "tank")), 300);
    }

    /** Three hulls with roof launchers or tubes, destroyed on the first tick and left to smoke. */
    private static LookReview.Shot wrecks() {
        return LookReview.Shot.of("carrier-wrecks", 24, 14, placement -> {
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            VehicleType[] types = {VehicleType.FIRE_SUPPORT_APC, VehicleType.LRM_CARRIER, VehicleType.SRM_TECHNICAL};
            long[] hulls = new long[types.length];
            for (int i = 0; i < types.length; i++) {
                float[] at = placement.openNear(8f + 3.5f * i, 7f);
                hulls[i] = sim.convoy().spawn(types[i], Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
                sim.convoy().body(hulls[i]).teleport(at[0], at[1], 0f);
                sim.convoy().turret(hulls[i]).facingDeg = 20f - 25f * i;
                placement.subject("hull" + i, hulls[i]);
            }
            boolean[] done = {false};
            placement.beforeTick(s -> {
                if (done[0]) return;
                done[0] = true;
                for (long hull : hulls) s.convoy().destroy(hull);
            });
            return sim;
        }, placement -> List.of(LookReview.View.follow("close", placement, 10f, "hull0", "hull1", "hull2")), 1500)
                // Late, once the roof fires have burned out and the smoke has thinned off the turrets.
                .withStills(150, 200, 240, 249);
    }

    @Override protected ReviewAnnotations annotations(BattleSimulation simulation) {
        int px = (int) FireSupportApcScene.POSITION_X;
        int py = (int) FireSupportApcScene.POSITION_Y;
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder()
                .box(px - 4, py - 4, px + 4, py + 4, "POSITION", ReviewStyle.OBJECTIVE)
                .label(FireSupportApcScene.LZ_X, FireSupportApcScene.LZ_Y + 2.5f, "LZ", ReviewStyle.LANDING)
                .label(FireSupportApcScene.SPOTTER_X - 1.5f, FireSupportApcScene.SPOTTER_Y - 1.5f, "SPOTTERS",
                        ReviewStyle.NOTE);
        for (long id : simulation.getConvoyVehicleIds()) {
            if (simulation.convoy().vehicleType(id) == VehicleType.FIRE_SUPPORT_APC) {
                marks.label(FireSupportApcScene.FIRE_SUPPORT_X, FireSupportApcScene.FIRE_SUPPORT_Y - 2.5f,
                        "FIRE SUPPORT APC", ReviewStyle.NOTE);
            }
        }
        return marks.build();
    }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        int[] all = new int[20];
        for (int i = 0; i < all.length; i++) all[i] = i * 4;
        return all;
    }
}
