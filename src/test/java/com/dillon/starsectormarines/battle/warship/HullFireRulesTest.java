package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Hull-on-hull fire meets shields and armour by the base game's damage types, and missile tubes ripple. */
class HullFireRulesTest {

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    @Test
    void damageTypesMeetShieldsAndArmourAsTheBaseGamesDo() {
        assertEquals(2f, WarshipSystem.shieldMultiplier("KINETIC"));
        assertEquals(0.5f, WarshipSystem.armourMultiplier("KINETIC"));
        assertEquals(0.5f, WarshipSystem.shieldMultiplier("HIGH_EXPLOSIVE"));
        assertEquals(2f, WarshipSystem.armourMultiplier("HIGH_EXPLOSIVE"));
        assertEquals(0.25f, WarshipSystem.shieldMultiplier("FRAGMENTATION"));
        assertEquals(1f, WarshipSystem.shieldMultiplier("ENERGY"));
        assertEquals(1f, WarshipSystem.armourMultiplier("ENERGY"));
    }

    @Test
    void aCarriersWarheadsStrikeWithTheirOwnType() {
        InstalledVanillaData.install();
        // The sabot is a kinetic carrier; the harpoon is high explosive itself.
        assertEquals("KINETIC", WarshipSystem.hullDamageType(VanillaWeapons.get("sabot")));
        assertEquals("HIGH_EXPLOSIVE", WarshipSystem.hullDamageType(VanillaWeapons.get("harpoon")));
    }

    @Test
    void noTubeLaunchesFasterThanTheTubeInterval() {
        InstalledVanillaData.install();
        VanillaWeapon harpoon = VanillaWeapons.get("harpoon");
        assertEquals(MissilePacing.TUBE_INTERVAL_SECONDS, MissilePacing.roundDelay(harpoon, 0f), 1e-6f);
        assertEquals(1f, MissilePacing.roundDelay(harpoon, 1f), 1e-6f);
        VanillaWeapon gun = VanillaWeapons.get("heavyac");
        assertEquals(0.1f, MissilePacing.roundDelay(gun, 0.1f), 1e-6f, "guns keep their own burst");
    }
}
