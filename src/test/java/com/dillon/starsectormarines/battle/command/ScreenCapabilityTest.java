package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.Armament.Delivery;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A squad's screen is rated by how long its cloud holds, read off the item it carries, so a plan can tell a short screen from a long one. */
class ScreenCapabilityTest {

    @Test
    void aSquadKnowsHowLongItsCloudHoldsFromTheItemItCarries() {
        SmokeGrenadeSpec spec = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID).smokeGrenadeSpec();
        Armament grenade = Armament.ofSpecial(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID), 2);
        assertEquals(Delivery.SCREEN, grenade.delivery());

        CapabilityProfile withGrenades = CapabilityProfile.of(List.of(grenade), 6, 6);
        assertEquals(spec.coverSeconds(), withGrenades.screenSeconds(), 1e-4f);
        assertTrue(withGrenades.has(AssaultCapability.SMOKE_SCREEN));
    }

    @Test
    void aCompanyHasTheLongestCloudAnySquadOfItCanStand() {
        Armament grenade = Armament.ofSpecial(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID), 2);
        Armament longScreen = new Armament(Delivery.SCREEN, 9f, 1, 1f, 0f, 0f, 0, 2, 60f, 3f);
        CapabilityProfile company = CapabilityProfile.of(List.of(grenade), 6, 6)
                .plus(CapabilityProfile.of(List.of(longScreen), 6, 6));
        assertEquals(60f, company.screenSeconds(), 1e-4f);
        assertEquals(4f, company.reserve(AssaultCapability.SMOKE_SCREEN));
    }

    @Test
    void aSquadWithNoSmokeStandsNoCloud() {
        Armament rifle = new Armament(Delivery.DIRECT, 18f, 1, 0.5f, 6f, 2f, 0, Armament.UNLIMITED);
        assertEquals(0f, CapabilityProfile.of(List.of(rifle), 6, 6).screenSeconds());
        assertEquals(0f, CapabilityProfile.NONE.screenSeconds());
    }
}
