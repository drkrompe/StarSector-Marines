package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleDirectControlStatusTest {
    @Test
    void damagedArmorRemainsDistinctFromAnUnarmoredBodyAndFractionsStayFinite() {
        var damaged = new BattleDirectControlStatus.Durability(75f, 100f, 0f, 40f, 12f);
        assertEquals(.75f, damaged.hpFraction());
        assertTrue(damaged.hasArmor());
        assertEquals(0f, damaged.armorFraction());
        assertEquals(12f, damaged.armorRating());
        var unarmored = new BattleDirectControlStatus.Durability(0f, 0f, 0f, 0f, 0f);
        assertFalse(unarmored.hasArmor());
        assertEquals(0f, unarmored.hpFraction());
        assertEquals(0f, unarmored.armorFraction());
        assertEquals(1f, new BattleDirectControlStatus.Durability(120f, 100f, 0f, 0f, 0f).hpFraction());
        assertEquals(0f, new BattleDirectControlStatus.Durability(Float.NaN, 100f, 0f, 0f, 0f).hpFraction());
    }

    @Test
    void marineUsesActualCadenceAndDoesNotInventAnAmmunitionSupply() {
        var weapon = WeaponRegistry.require("weapon.pulse-rifle");
        var status = BattleDirectControlStatus.marineWeapon(weapon, EquipmentGrade.SERVICE,
                .3f, .6f, 0, 0f);
        assertEquals(BattleDirectControlStatus.FireKind.BURST, status.behavior().kind());
        assertEquals(3, status.behavior().projectilesPerTrigger());
        assertEquals(weapon.burstSpacing, status.behavior().burstSpacingSeconds());
        assertEquals(BattleDirectControlStatus.AmmoUnit.UNTRACKED, status.ammo().unit());
        assertFalse(status.ammo().unlimited());
        assertEquals(.6f, status.cycleDurationSeconds());
        assertEquals(.5f, status.cycleFraction());
        assertEquals(BattleDirectControlStatus.WeaponState.CYCLING, status.state());
        assertTrue(status.directEligible());
        assertEquals(weapon.catalogName(EquipmentGrade.SERVICE), status.name());
    }

    @Test
    void simultaneousShredderPacketIsNotPresentedAsAScheduledBurst() {
        var status = BattleDirectControlStatus.marineWeapon(WeaponRegistry.require("weapon.smg"),
                EquipmentGrade.SERVICE, 0f, .3f, 0, 0f);
        assertEquals(BattleDirectControlStatus.FireKind.PACKET, status.behavior().kind());
        assertEquals(1, status.behavior().projectilesPerTrigger());
        assertEquals(6, status.behavior().projectilesPerShot());
        assertEquals(BattleDirectControlStatus.WeaponState.LOADED, status.state());
    }

    @Test
    void mechKeepsStableHardpointNumbersAndIncludesUnsupportedIndirectEquipment() {
        var left = new MechWeaponMount(MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.SRM_15);
        var right = new MechWeaponMount(MechMountSlot.RIGHT_SHOULDER, MechWeaponComponent.LRM_15);
        left.ammo = 2;
        var weapons = BattleDirectControlStatus.mechWeapons(new MechWeaponMount[]{right, null, left});
        assertEquals(List.of(2, 3), weapons.stream().map(BattleDirectControlStatus.WeaponStatus::number).toList());
        assertEquals("LEFT_SHOULDER", weapons.get(0).slotKey());
        assertTrue(weapons.get(0).directEligible());
        assertEquals(2, weapons.get(0).ammo().remaining());
        assertEquals(6, weapons.get(0).ammo().capacity());
        assertEquals(BattleDirectControlStatus.AmmoUnit.TRIGGER_PACKS, weapons.get(0).ammo().unit());
        assertEquals(4, weapons.get(0).behavior().projectilesPerTrigger());
        assertFalse(weapons.get(1).directEligible());
        assertEquals(BattleDirectControlStatus.FireKind.INDIRECT, weapons.get(1).behavior().kind());
    }

    @Test
    void committedBurstCanFinishItsLastPackAndProjectionDoesNotMutateRuntimeState() {
        var mount = new MechWeaponMount(MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.SRM_5);
        mount.ammo = 0;
        mount.cooldown = 1f;
        mount.burstRemaining = 1;
        mount.burstTimer = .12f;
        mount.replenishmentProgressSeconds = 2f;
        var status = BattleDirectControlStatus.mechWeapons(new MechWeaponMount[]{mount}).get(0);
        assertEquals(BattleDirectControlStatus.WeaponState.BURST, status.state());
        assertEquals(1, status.burstRemaining());
        assertEquals(.12f, status.burstSeconds());
        assertEquals(0, mount.ammo);
        assertEquals(1f, mount.cooldown);
        assertEquals(2f, mount.replenishmentProgressSeconds);
        mount.clearBurst();
        mount.ammo = 1;
        assertEquals(0, status.ammo().remaining());
        assertEquals(1, status.burstRemaining());
        mount.ammo = 0;
        assertEquals(BattleDirectControlStatus.WeaponState.EMPTY,
                BattleDirectControlStatus.mechWeapons(new MechWeaponMount[]{mount}).get(0).state());
    }

    @Test
    void unlimitedMechFeedAndFiniteApcRoundsRetainTheirDifferentResourceUnits() {
        var arms = new MechWeaponMount(MechMountSlot.ARMS, MechWeaponComponent.DUAL_CHAINGUNS);
        var mech = BattleDirectControlStatus.mechWeapons(new MechWeaponMount[]{arms}).get(0);
        assertTrue(mech.ammo().unlimited());
        assertEquals(BattleDirectControlStatus.WeaponState.LOADED, mech.state());
        var mount = VehicleType.HEAVY_APC.turretStructure().mount;
        GroundTurret turret = new GroundTurret(mount.ammoCapacity - 4);
        turret.cooldownTimer = .5f;
        turret.burstRemaining = 2;
        turret.burstTimer = .1f;
        var apc = BattleDirectControlStatus.vehicleWeapon(turret, mount);
        assertFalse(apc.ammo().unlimited());
        assertEquals(BattleDirectControlStatus.AmmoUnit.ROUNDS, apc.ammo().unit());
        assertEquals(mount.ammoCapacity - 4, apc.ammo().remaining());
        assertEquals(mount.ammoCapacity, apc.ammo().capacity());
        assertEquals(BattleDirectControlStatus.WeaponState.BURST, apc.state());
        assertEquals(.5f, turret.cooldownTimer);
        assertEquals(2, turret.burstRemaining);
        assertTrue(apc.directEligible());
    }

    @Test
    void noSessionIsEmptyAndSnapshotEquipmentIsImmutable() {
        assertSame(BattleDirectControlStatus.Snapshot.EMPTY, BattleDirectControlStatus.snapshot(null));
        List<BattleDirectControlStatus.WeaponStatus> weapons = new ArrayList<>();
        var snapshot = new BattleDirectControlStatus.Snapshot(1L, null,
                BattleDirectControlStatus.Carrier.MARINE,
                new BattleDirectControlStatus.Durability(50f, 100f, 20f, 40f, 12f), weapons);
        weapons.add(BattleDirectControlStatus.marineWeapon(WeaponRegistry.require("weapon.field-rifle"),
                EquipmentGrade.SERVICE, 0f, 1f, 0, 0f));
        assertEquals("", snapshot.name());
        assertTrue(snapshot.weapons().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.weapons().clear());
    }
}
