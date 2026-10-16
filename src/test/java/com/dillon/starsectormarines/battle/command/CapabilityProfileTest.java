package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.Armament.Delivery;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityProfileTest {

    private static Armament cannon(int triggers) {
        return new Armament(Delivery.DIRECT, 26f, 1, 3f, 45f, 18f, 140, triggers);
    }

    private static Armament rocketRack(int triggers) {
        return new Armament(Delivery.DIRECT, 22f, 1, 4f, 60f, 14f, 90, triggers);
    }

    private static Armament rifle() {
        return new Armament(Delivery.DIRECT, 18f, 1, 0.5f, 6f, 2f, 0, Armament.UNLIMITED);
    }

    private static CapabilityProfile of(int walkers, Armament... arms) {
        return CapabilityProfile.of(List.of(arms), walkers, Math.max(walkers, 1));
    }

    @Test
    void cannonIsADistanceBreacherWithAnUnlimitedMagazine() {
        CapabilityProfile profile = of(0, cannon(Armament.UNLIMITED));
        assertTrue(profile.has(AssaultCapability.DISTANCE_BREACH));
        assertTrue(profile.has(AssaultCapability.DIRECT_STRUCTURE_FIRE));
        assertEquals(Float.POSITIVE_INFINITY, profile.reserve(AssaultCapability.DISTANCE_BREACH));
        assertEquals(26f, profile.distanceBreachRange(), 0.001f);
        assertFalse(profile.has(AssaultCapability.ASSAULT));
    }

    @Test
    void rocketsAreBothAntiStructureFireAndABreacher() {
        CapabilityProfile profile = of(4, rocketRack(6), rifle());
        assertTrue(profile.has(AssaultCapability.DIRECT_STRUCTURE_FIRE));
        assertTrue(profile.has(AssaultCapability.DISTANCE_BREACH));
        assertTrue(profile.has(AssaultCapability.SUPPRESSION));
        assertTrue(profile.canAssault());
        assertEquals(90f * 6, profile.reserve(AssaultCapability.DISTANCE_BREACH), 0.001f);
    }

    @Test
    void rifleOnlySquadAssaultsAndSuppressesButBreachesNothing() {
        CapabilityProfile profile = of(6, rifle());
        assertTrue(profile.has(AssaultCapability.SUPPRESSION));
        assertFalse(profile.has(AssaultCapability.DIRECT_STRUCTURE_FIRE));
        assertFalse(profile.canBreach());
        assertTrue(profile.canAssault());
    }

    @Test
    void chargesAndCuttersAreCloseBreachers() {
        Armament satchel = new Armament(Delivery.CHARGE, 1.5f, 1, 12f, 200f, 20f, 450, Armament.UNLIMITED);
        CapabilityProfile profile = of(4, satchel, rifle());
        assertTrue(profile.has(AssaultCapability.CLOSE_BREACH));
        assertFalse(profile.has(AssaultCapability.DISTANCE_BREACH));
        assertEquals(0f, profile.distanceBreachRange(), 0f);
    }

    @Test
    void remainingAmmunitionIsCountedAndDepletedKitContributesNothing() {
        CapabilityProfile full = of(0, rocketRack(6));
        CapabilityProfile low = of(0, rocketRack(1));
        CapabilityProfile empty = of(0, rocketRack(0));
        assertTrue(full.reserve(AssaultCapability.DISTANCE_BREACH) > low.reserve(AssaultCapability.DISTANCE_BREACH));
        assertFalse(empty.has(AssaultCapability.DISTANCE_BREACH));
        assertFalse(empty.has(AssaultCapability.DIRECT_STRUCTURE_FIRE));
    }

    @Test
    void catalogCannonMountOnTheSupportMechRatesAsADistanceBreacher() {
        MechLoadoutComponent sirocco = MechVariant.SIROCCO.createLoadout(null);
        List<Armament> arms = new ArrayList<>();
        arms.add(Armament.ofMount(sirocco.mount(MechMountSlot.ARMS)));
        CapabilityProfile profile = CapabilityProfile.of(arms, 0, 1);
        assertTrue(profile.has(AssaultCapability.DISTANCE_BREACH), profile.describe());
        assertTrue(profile.distanceBreachRange() >= CapabilityProfile.DISTANCE_BREACH_MIN_RANGE);
    }

    @Test
    void nothingAliveIsNothing() {
        assertEquals("nothing", CapabilityProfile.of(List.of(), 0, 0).describe());
    }

    @Test
    void aSmokeGrenadeIsAScreenWithAsManyThrowsAsGrenadesAndNothingElse() {
        Armament smoke = new Armament(Delivery.SCREEN, 8f, 1, 0.8f, 0f, 0f, 0, 2);
        CapabilityProfile profile = CapabilityProfile.of(List.of(smoke), 6, 6);
        assertTrue(profile.has(AssaultCapability.SMOKE_SCREEN));
        assertEquals(2f, profile.reserve(AssaultCapability.SMOKE_SCREEN));
        assertFalse(profile.has(AssaultCapability.SUPPRESSION), "a cloud does not suppress");
        assertFalse(profile.canBreach());
    }
}
