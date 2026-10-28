package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.FireGroups;
import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.combat.HeavyWeapons;
import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.combat.PointAimDispersion;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.combat.RangeFalloff;
import com.dillon.starsectormarines.battle.combat.TriggerPicks;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.control.MarineRightTrigger;
import com.dillon.starsectormarines.battle.infantry.CarriedSpecials;
import com.dillon.starsectormarines.battle.infantry.FlameJet;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.mech.MechHardpointGeometry;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.GroundVehicleTurretSystem;
import com.dillon.starsectormarines.battle.vehicle.MountedGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleFireGroups;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

/**
 * What each mouse button would do if clicked now, read at the cursor: one {@link Mark} per button from its
 * trigger's current pick. Cooldown, reload, burst and flux are clocks the panel's ammo strips own, so they never
 * change a mark; a mark answers only whether this point is one the pick can fire at, and how well. The reticle
 * carries a Marine's left clock and magazine beside the mark ({@link DirectControlLoad}), in brightness, never hue.
 */
public final class DirectControlFireMarks {
    private DirectControlFireMarks() {}

    /**
     * Ordered worst to best, so the best of several mounts is the greatest. {@link #NONE} is a button with nothing
     * on it, which draws no mark.
     */
    public enum Mark {
        /** Nothing on the button. */
        NONE,
        /** Grey: will not fire here. Too close, out of arc or alignment, barrel blocked, or out of ammunition. */
        HELD,
        /** Red: fires, but past maximum range, where the round is wasted. */
        WASTED,
        /** Yellow: fires, between optimal and maximum range, where accuracy is falling off. */
        FALLING_OFF,
        /** White: fires well, inside optimal range. */
        GOOD;

        public Mark best(Mark other) { return other.ordinal() > ordinal() ? other : this; }
    }

    /** The two buttons' marks. */
    public record Marks(Mark left, Mark right) {
        public static final Marks NONE = new Marks(Mark.NONE, Mark.NONE);
        public Mark of(FireTrigger trigger) { return trigger == FireTrigger.LEFT ? left : right; }
    }

    /**
     * One mount's or item's mark at a cursor {@code distance} cells away: {@link Mark#HELD} when it would not
     * fire, otherwise by where the distance falls against its optimal and maximum ranges.
     */
    public static Mark of(boolean fires, float distance, float optimalCells, float maximumCells) {
        if (!fires) return Mark.HELD;
        if (!Float.isFinite(distance) || distance <= optimalCells) return Mark.GOOD;
        return distance <= maximumCells ? Mark.FALLING_OFF : Mark.WASTED;
    }

    /** The marks for the controlled ground body, or none for a warship, a tender or no cursor. */
    public static Marks read(BattleSimulation sim) {
        if (sim == null || !sim.directControl().active() || sim.directControl().warshipControl()) return Marks.NONE;
        var aim = sim.directControl().intent();
        if (!Float.isFinite(aim.aimX()) || !Float.isFinite(aim.aimY())) return Marks.NONE;
        long id = sim.directControl().activeUnitId();
        if (!sim.world().isAlive(id)) return Marks.NONE;
        if (sim.directControl().controlledVehicleId() == id) return vehicle(sim, id, aim.aimX(), aim.aimY());
        if (sim.world().hasMechLoadout(id)) {
            return new Marks(mech(sim, id, FireTrigger.LEFT, aim.aimX(), aim.aimY()),
                    mech(sim, id, FireTrigger.RIGHT, aim.aimX(), aim.aimY()));
        }
        return marine(sim, id, aim.aimX(), aim.aimY());
    }

    /**
     * The cell point the controlled ground body fires from, for the reticle's line of fire: a vehicle's turret
     * ring, otherwise the body itself. Null with nothing controlled on the ground.
     */
    public static float[] fireOrigin(BattleSimulation sim) {
        if (sim == null || !sim.directControl().active() || sim.directControl().warshipControl()) return null;
        long id = sim.directControl().activeUnitId();
        if (sim.directControl().controlledVehicleId() == id) {
            var type = sim.convoy().vehicleType(id);
            var body = sim.convoy().body(id);
            if (type == null || body == null) return null;
            TurretMountGeometry.Point ring = GroundVehicleTurretSystem.mountPoint(type, body);
            return new float[]{ring.x(), ring.y()};
        }
        return sim.world().isAlive(id) ? new float[]{sim.world().renderX(id), sim.world().renderY(id)} : null;
    }

    /**
     * The best mark among the mounts the trigger's pick fires: LINKED is grey only when none of its mounts would
     * fire. Every gate is one the manual round itself applies before spending anything.
     */
    private static Mark mech(BattleSimulation sim, long id, FireTrigger trigger, float aimX, float aimY) {
        MechLoadoutComponent loadout = sim.world().mechLoadout(id);
        MechWeaponMount[] mounts = loadout.mounts();
        int mask = trigger == FireTrigger.LEFT ? sim.directControl().triggerPicks().leftMask(MechFireGroups.mounts(mounts))
                : sim.directControl().triggerPicks().rightMask(MechFireGroups.mounts(mounts));
        float x = sim.world().renderX(id);
        float y = sim.world().renderY(id);
        float distance = (float) Math.hypot(aimX - x, aimY - y);
        float hipFacing = sim.getEntityWorld().getFloat(id, sim.getRoster().components().MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        boolean aligned = loadout.isPointAimedAt(new PointFireAim(aimX, aimY), x, y, hipFacing);
        Mark best = Mark.NONE;
        for (MechWeaponMount mount : mounts) {
            if (mount == null || !MechFireGroups.contains(mask, mount)) continue;
            boolean empty = mount.component.ammoCapacity >= 0 && mount.ammo <= 0;
            boolean fires = aligned && !empty && !HeavyWeapons.tooClose(mount, distance)
                    && !(HeavyWeapons.firesFlat(mount) && barrelBlocked(sim, loadout, mount, x, y));
            best = best.best(of(fires, distance, HeavyWeapons.manualOptimalRangeCells(loadout, mount),
                    mount.weaponDef().range));
        }
        return best;
    }

    /** A flat-fire barrel whose muzzle sits beyond structure from the chassis cannot fire, as the round enforces. */
    private static boolean barrelBlocked(BattleSimulation sim, MechLoadoutComponent loadout, MechWeaponMount mount,
                                         float x, float y) {
        var muzzle = MechHardpointGeometry.muzzle(x, y, 0f, 0f, loadout.torsoFacingDegrees, loadout, mount,
                MechHardpointGeometry.nextReleaseIndex(mount));
        return DirectControlAimLane.trace(sim.getGrid(), x, y, muzzle.x(), muzzle.y()).blocked();
    }

    /**
     * The best mark among the hull's mounts each button's pick fires ({@link VehicleFireGroups}): the main turret
     * on the left, and on a hull with a second gun, that gun on the right.
     */
    private static Marks vehicle(BattleSimulation sim, long id, float aimX, float aimY) {
        var turret = sim.convoy().turret(id);
        var type = sim.convoy().vehicleType(id);
        var body = sim.convoy().body(id);
        if (turret == null || type == null || body == null || turret.structureFor(type) == null) return Marks.NONE;
        FireGroups.Mount[] mounts = VehicleFireGroups.mounts(type, turret);
        TriggerPicks picks = sim.directControl().triggerPicks();
        int left = picks.leftMask(mounts);
        int right = picks.rightMask(mounts);
        Mark leftMark = Mark.NONE, rightMark = Mark.NONE;
        for (int index = 0; index < mounts.length; index++) {
            if (!FireGroups.contains(left | right, index)) continue;
            Mark mark;
            if (index == VehicleFireGroups.MAIN) {
                mark = turret(sim, body, turret, turret.structureFor(type).mount,
                        GroundVehicleTurretSystem.mountPoint(type, body), aimX, aimY);
            } else {
                MountedGun gun = turret.secondaries.get(index - 1);
                mark = turret(sim, body, gun.turret(), gun.mount().structure().mount,
                        GroundVehicleTurretSystem.mountPoint(body, gun.mount().mountX(), gun.mount().mountY()),
                        aimX, aimY);
            }
            if (FireGroups.contains(left, index)) leftMark = leftMark.best(mark);
            if (FireGroups.contains(right, index)) rightMark = rightMark.best(mark);
        }
        return new Marks(leftMark, rightMark);
    }

    /** One turret's mark: grey when it is off the cursor's bearing, empty, blocked at the barrel or too close. */
    private static Mark turret(BattleSimulation sim, GroundBody body, GroundTurret turret, TurretMountDef mountDef,
                               TurretMountGeometry.Point ring, float aimX, float aimY) {
        WeaponDef weapon = mountDef.weapon;
        float distance = (float) Math.hypot(aimX - ring.x(), aimY - ring.y());
        boolean aligned = Math.abs(TurretAim.shortestAngleDelta(turret.facingDeg,
                TurretAim.bearingTo(ring.x(), ring.y(), aimX, aimY))) <= TurretAim.FIRE_ARC_DEG;
        boolean empty = mountDef.ammoCapacity > 0 && turret.ammo <= 0;
        var muzzle = TurretMountGeometry.muzzle(ring.x(), ring.y(), turret.facingDeg, mountDef, 0);
        boolean blocked = DirectControlAimLane.trace(sim.getGrid(), body.x, body.y, muzzle.x(), muzzle.y()).blocked();
        boolean fires = aligned && !empty && !blocked && !ManualPointFire.tooClose(weapon, distance);
        float optimal = ManualPointFire.optimalRangeCells(weapon);
        return of(fires, distance, optimal, weapon.range);
    }

    /**
     * The primary, or the special the left button is on, on the left; on the right, the right-button pick:
     * smoke, a hand grenade or the shield.
     * A magazine run dry is a reload, a clock, so the primary is never grey for it.
     */
    private static Marks marine(BattleSimulation sim, long id, float aimX, float aimY) {
        var world = sim.world();
        float distance = (float) Math.hypot(aimX - world.renderX(id), aimY - world.renderY(id));
        var combat = sim.combat();
        WeaponDef primary = combat.has(id) ? combat.primaryWeaponDef(id) : null;
        Mark left = Mark.NONE;
        SpecialEquipmentDef picked = sim.directControl().leftSpecial();
        if (picked != null && picked.isHeldStream()) {
            // The flamer's jet reaches its range and no further, and a wall at the nozzle or an empty tank holds it.
            boolean fires = world.secondaryAmmo(id) > 0 && FlameJet.point(sim.getGrid(), world.renderX(id),
                    world.renderY(id), aimX, aimY, picked.range()) != null;
            left = of(fires, distance, picked.range(), picked.range());
        } else if (picked != null) {
            // The left button on a special reads the special: its reach, its accuracy, and grey once it is empty.
            WeaponDef weapon = picked.weaponDef();
            boolean fires = world.secondaryAmmo(id) > 0 && !ManualPointFire.tooClose(weapon, distance);
            float optimal = ManualPointFire.optimalRangeCells(weapon.range, specialAccuracy(sim, id, picked), 0f,
                    weapon.aoeRadius, PointAimDispersion.CONTROLLED);
            left = of(fires, distance, optimal, weapon.range);
        } else if (primary != null && PointFireAim.supports(primary)) {
            float range = world.attackRange(id);
            float accuracy = RangeFalloff.accuracy(world.accuracy(id), primary.accuracyFalloff, range, range);
            float spread = InfantryCombatStats.spread(primary, combat.equipmentGrade(id), combat.soldierProfile(id));
            float optimal = ManualPointFire.optimalRangeCells(range, accuracy, spread, primary.aoeRadius,
                    PointAimDispersion.CONTROLLED);
            left = of(true, distance, optimal, range);
        }
        return new Marks(left, marineSecondary(sim, id, distance));
    }

    /** A carried special's accuracy in this Marine's hands, as its round is resolved with. */
    public static float specialAccuracy(BattleSimulation sim, long id, SpecialEquipmentDef special) {
        return Math.min(1f, special.accuracy()
                * InfantryCombatStats.shooterAccuracyMult(sim.combat().soldierProfile(id)));
    }

    private static Mark marineSecondary(BattleSimulation sim, long id, float distance) {
        MarineRightTrigger.Item pick = sim.directControl().marineRightPick();
        if (pick == null) return Mark.NONE;
        var world = sim.world();
        if (pick == MarineRightTrigger.Item.SHIELD) {
            var systems = sim.getRoster().integralSystems();
            return systems.spec(id).usesAmmunition() && systems.ammo(id) <= 0 ? Mark.HELD : Mark.GOOD;
        }
        // A throw past its reach lands short, at its range: thrown, but not where the cursor is.
        // The picked throw may ride in either special slot.
        SpecialEquipmentDef item = CarriedSpecials.item(world, id, pick::is);
        if (item == null) return Mark.NONE;
        return CarriedSpecials.ammo(world, id, pick::is) > 0
                ? of(true, distance, throwReach(item), throwReach(item)) : Mark.HELD;
    }

    /** How far a carried smoke canister or hand grenade can be thrown, in cells. */
    public static float throwReach(SpecialEquipmentDef special) {
        return special.activation() == SpecialActivation.UTILITY_SMOKE ? special.smokeGrenadeSpec().throwRange()
                : special.range();
    }
}
