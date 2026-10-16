package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.Armament.Delivery;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A squad's screen is rated by how long its cloud holds, read off the item it carries, so a plan can tell a pot from a grenade. */
class ScreenCapabilityTest {

    @Test
    void aSquadKnowsHowLongItsCloudHoldsFromTheItemItCarries() {
        Armament grenade = Armament.ofSpecial(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID), 2);
        Armament pot = Armament.ofSpecial(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_POT_ID), 2);
        assertEquals(Delivery.SCREEN, pot.delivery());

        CapabilityProfile withGrenades = CapabilityProfile.of(List.of(grenade), 6, 6);
        CapabilityProfile withPots = CapabilityProfile.of(List.of(pot), 6, 6);
        assertEquals(12f, withGrenades.screenSeconds(), 1e-4f);
        assertTrue(withPots.screenSeconds() >= 45f, "a pot's screen holds long enough for a walk across a fort's approaches");
        assertTrue(withPots.has(AssaultCapability.SMOKE_SCREEN));
    }

    @Test
    void aCompanyHasTheLongestCloudAnySquadOfItCanStand() {
        Armament grenade = Armament.ofSpecial(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID), 2);
        Armament pot = Armament.ofSpecial(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_POT_ID), 2);
        CapabilityProfile company = CapabilityProfile.of(List.of(grenade), 6, 6).plus(CapabilityProfile.of(List.of(pot), 6, 6));
        assertEquals(CapabilityProfile.of(List.of(pot), 6, 6).screenSeconds(), company.screenSeconds(), 1e-4f);
        assertEquals(4f, company.reserve(AssaultCapability.SMOKE_SCREEN));
    }

    @Test
    void aSquadWithNoSmokeStandsNoCloud() {
        Armament rifle = new Armament(Delivery.DIRECT, 18f, 1, 0.5f, 6f, 2f, 0, Armament.UNLIMITED);
        assertEquals(0f, CapabilityProfile.of(List.of(rifle), 6, 6).screenSeconds());
        assertEquals(0f, CapabilityProfile.NONE.screenSeconds());
    }
}
