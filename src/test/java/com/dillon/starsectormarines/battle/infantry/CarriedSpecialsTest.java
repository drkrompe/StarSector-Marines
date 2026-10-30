package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A Marine's two special slots: finding an item in either, drawing a belt throw into the hand, and putting it back. */
class CarriedSpecialsTest {
    private static final SpecialEquipmentDef FRAG = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID);
    private static final SpecialEquipmentDef SMOKE = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
    private static final SpecialEquipmentDef ROCKET = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SRM_1_ID);

    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(10, 10), null);
    private final World world = roster.world();

    @Test
    void onlyThrowsRideTheBelt() {
        assertTrue(FRAG.isThrownItem());
        assertTrue(SMOKE.isThrownItem());
        assertFalse(ROCKET.isThrownItem());
        assertFalse(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FLAMER_ID).isThrownItem());
    }

    @Test
    void aBeltItemIsFoundReadyAndDrawnIntoTheHand() {
        long marine = spawn(ROCKET, FRAG);
        assertEquals(CarriedSpecials.Slot.HAND, CarriedSpecials.find(world, marine, SpecialEquipmentDef::isDirectFireWeapon));
        assertEquals(CarriedSpecials.Slot.BELT, CarriedSpecials.find(world, marine, SpecialEquipmentDef::isThrownGrenade));
        assertNull(CarriedSpecials.find(world, marine, SpecialEquipmentDef::isSmokeThrow));
        assertEquals(FRAG.startingAmmo(), CarriedSpecials.ammo(world, marine, SpecialEquipmentDef::isThrownGrenade));
        assertTrue(CarriedSpecials.ready(world, marine, SpecialEquipmentDef::isThrownGrenade));

        world.setSecondaryActionTimer(marine, 1f);
        assertFalse(CarriedSpecials.ready(world, marine, SpecialEquipmentDef::isThrownGrenade), "a busy hand cannot swap");
        assertFalse(CarriedSpecials.draw(world, marine, SpecialEquipmentDef::isThrownGrenade));
        world.setSecondaryActionTimer(marine, 0f);

        world.setSecondaryCooldownTimer(marine, 4f);
        assertTrue(CarriedSpecials.draw(world, marine, SpecialEquipmentDef::isThrownGrenade));
        assertSame(FRAG, world.specialEquipment(marine));
        assertEquals(FRAG.startingAmmo(), world.secondaryAmmo(marine));
        assertSame(ROCKET, world.beltEquipment(marine));
        assertEquals(4f, world.beltCooldownTimer(marine), "an item's cooldown travels with it");
    }

    @Test
    void aNonThrowGoesBackToTheHandOnlyOnceTheHandIsIdle() {
        long marine = spawn(ROCKET, FRAG);
        CarriedSpecials.draw(world, marine, SpecialEquipmentDef::isThrownGrenade);
        world.setSecondaryActionTimer(marine, .5f);
        CarriedSpecials.stowBack(world, marine);
        assertSame(FRAG, world.specialEquipment(marine), "not mid-throw");
        world.setSecondaryActionTimer(marine, 0f);
        CarriedSpecials.stowBack(world, marine);
        assertSame(ROCKET, world.specialEquipment(marine));
        assertSame(FRAG, world.beltEquipment(marine));
    }

    @Test
    void twoThrowsStayWhereverTheyAre() {
        long marine = spawn(SMOKE, FRAG);
        CarriedSpecials.draw(world, marine, SpecialEquipmentDef::isThrownGrenade);
        CarriedSpecials.stowBack(world, marine);
        assertSame(FRAG, world.specialEquipment(marine));
        assertSame(SMOKE, world.beltEquipment(marine));
        assertTrue(CarriedSpecials.ready(world, marine, SpecialEquipmentDef::isSmokeThrow));
    }

    private long spawn(SpecialEquipmentDef hand, SpecialEquipmentDef belt) {
        return roster.spawn(new EntitySpec("Marine", Faction.MARINE, UnitType.MARINE, 3, 5)
                .primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID))
                .specialEquipment(hand, hand.startingAmmo())
                .beltEquipment(belt, belt.startingAmmo()));
    }
}
