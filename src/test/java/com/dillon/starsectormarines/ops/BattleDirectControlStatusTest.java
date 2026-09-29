package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.control.DirectControlAbility;
import com.dillon.starsectormarines.battle.infantry.SmokeThrowCommit;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.BreacherAssistSpec;
import com.dillon.starsectormarines.marine.ExposedUnderFireSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialResourceMode;
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
    void shieldReportsTheLivePoolAndBreakFactWithoutCallingRemainingBoostProtection() {
        var roster = roster();
        IntegralSystemDef def = shield(SpecialResourceMode.COOLDOWN, 40f);
        long id = roster.spawn(marine().integralSystem(def));
        var ready = BattleDirectControlStatus.marineAbilities(roster, id,
                ability -> roster.integralSystems().canActivate(id)).get(0);
        assertTrue(ready.ready());
        assertEquals(def.displayName(), ready.name());
        assertEquals(-1, ready.remaining(), "a cooldown shield has no ammunition model");
        assertEquals(-1, ready.capacity());
        assertEquals(0f, ready.activeSeconds());

        assertTrue(roster.integralSystems().activate(id));
        roster.mitigations().face(id, 63f);
        roster.mitigations().absorb(id, 10f);
        var active = BattleDirectControlStatus.marineAbilities(roster, id, ability -> false).get(0);
        assertEquals(3f, active.activeSeconds());
        assertEquals(3f, active.durationSeconds());
        assertEquals(12f, active.cooldownSeconds());
        assertEquals(12f, active.cooldownDurationSeconds());
        assertEquals(30f, active.soakRemaining());
        assertEquals(40f, active.soakCapacity());
        assertEquals(63f, active.facingDegrees());
        assertEquals(120f, active.arcDegrees());
        assertFalse(active.ready());
        assertFalse(active.broken());

        roster.mitigations().absorb(id, 100f);
        var broken = BattleDirectControlStatus.marineAbilities(roster, id, ability -> false).get(0);
        assertTrue(roster.integralSystems().isActive(id), "the boost can outlive the shield pool");
        assertTrue(broken.broken());
        assertEquals(0f, broken.activeSeconds(), "a shattered shield no longer protects");
        assertEquals(0f, broken.soakRemaining());
        assertEquals(0f, broken.soakCapacity());
        assertEquals(12f, broken.cooldownSeconds());
        assertEquals(30f, active.soakRemaining(), "an earlier projection is an immutable value");
        assertEquals(3f, roster.integralSystems().activeRemaining(id), "projection never advances clocks");
    }

    @Test
    void timedOutShieldIsNotMistakenForABreakAndAuthoredUsesStayFinite() {
        var roster = roster();
        IntegralSystemDef def = shield(SpecialResourceMode.AMMUNITION, 40f);
        long id = roster.spawn(marine().integralSystem(def));
        assertTrue(roster.integralSystems().activate(id));
        var active = BattleDirectControlStatus.marineAbilities(roster, id, ability -> false).get(0);
        assertEquals(1, active.remaining());
        assertEquals(2, active.capacity());
        roster.mitigations().tick(id, def.durationSeconds());
        var expired = BattleDirectControlStatus.marineAbilities(roster, id, ability -> false).get(0);
        assertFalse(expired.broken(), "only the real absorb-to-zero event marks a break");
        assertEquals(0f, expired.activeSeconds());
        assertEquals(0f, expired.soakRemaining());
        assertEquals(1, roster.integralSystems().ammo(id), "projection never consumes a use");
    }

    @Test
    void smokeReadsCarriedAmmoAndOnlyTheAcceptedManualThrowClock() {
        SpecialEquipmentDef smoke = smoke();
        var roster = roster();
        long id = roster.spawn(marine());
        roster.world().attachSpecialEquipment(id, smoke, 2);
        var ready = BattleDirectControlStatus.marineAbilities(roster, id, ability -> false).get(0);
        assertEquals(DirectControlAbility.SMOKE, ready.ability());
        assertEquals(smoke.displayName(), ready.name());
        assertEquals(2, ready.remaining());
        assertEquals(3, ready.capacity());
        assertFalse(ready.ready(), "availability comes from the session, not an ammo-only guess");
        assertEquals(0f, ready.cooldownSeconds());
        assertEquals(0f, ready.cooldownDurationSeconds());
        roster.world().setSecondaryActionTimer(id, .4f);
        roster.world().setSmokeThrowCommit(id, new SmokeThrowCommit(new PointFireAim(8, 8), false));
        assertEquals(0f, BattleDirectControlStatus.marineAbilities(roster, id, ability -> false)
                .get(0).activeSeconds(), "an AI commitment is not advertised as the manual throw");
        roster.world().setSmokeThrowCommit(id, new SmokeThrowCommit(new PointFireAim(8, 8), true));
        var throwing = BattleDirectControlStatus.marineAbilities(roster, id, ability -> false).get(0);
        assertEquals(.4f, throwing.activeSeconds());
        assertEquals(.8f, throwing.durationSeconds());
        roster.world().setSecondaryAmmo(id, 0);
        var lastThrow = BattleDirectControlStatus.marineAbilities(roster, id, ability -> false).get(0);
        assertEquals(0, lastThrow.remaining());
        assertEquals(.4f, lastThrow.activeSeconds(), "a committed last canister can still be throwing");
        assertEquals(.4f, roster.world().secondaryActionTimer(id));
        assertEquals(2, throwing.remaining());
    }

    @Test
    void abilitiesRequireTheCarriedCapabilityAndSnapshotCopiesBothCollections() {
        var roster = roster();
        long plain = roster.spawn(marine());
        assertTrue(BattleDirectControlStatus.marineAbilities(roster, plain, ability -> true).isEmpty());
        long noScreen = roster.spawn(marine().integralSystem(shield(SpecialResourceMode.COOLDOWN, 0f)));
        assertTrue(BattleDirectControlStatus.marineAbilities(roster, noScreen, ability -> true).isEmpty(),
                "an integral system without mitigation is not a shield ability");
        var abilities = new ArrayList<BattleDirectControlStatus.AbilityStatus>();
        var snapshot = new BattleDirectControlStatus.Snapshot(plain, "Marine",
                BattleDirectControlStatus.Carrier.MARINE,
                new BattleDirectControlStatus.Durability(100, 100, 0, 0, 0), List.of(), abilities);
        abilities.add(BattleDirectControlStatus.smokeAbility(smoke(), 2, null, 0, true));
        assertTrue(snapshot.abilities().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.abilities().clear());
    }

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(16, 16), null);
    }

    private static EntitySpec marine() {
        return new EntitySpec("Test marine", Faction.MARINE, UnitType.MARINE, 5, 5);
    }

    private static IntegralSystemDef shield(SpecialResourceMode resource, float soak) {
        return new IntegralSystemDef("system.test-screen", "Test breach screen", EquipmentGrade.SERVICE,
                "A finite frontal screen.", IntegralSystemEffect.BREACHER_ASSIST, resource,
                3f, resource == SpecialResourceMode.COOLDOWN ? 12f : 0f,
                resource == SpecialResourceMode.AMMUNITION ? 2 : 0,
                new BreacherAssistSpec(1.2f, soak, 120f), null, null, null,
                new ExposedUnderFireSpec(1f, 0f));
    }

    private static SpecialEquipmentDef smoke() {
        return new SpecialEquipmentDef("equipment.test-smoke", "Test smoke canister", "", "", null,
                SpecialActivation.UTILITY_SMOKE, null, SpecialResourceMode.AMMUNITION, 3,
                SpecialAiPolicy.SQUAD_SMOKE_SCREEN, null,
                new SmokeGrenadeSpec(8f, .8f, .5f, 1f, 2f, 10f),
                null, null, null, null);
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
