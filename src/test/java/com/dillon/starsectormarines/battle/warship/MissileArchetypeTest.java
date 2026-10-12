package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which feel each missile line gets, from the install's own weapons, and how the presets differ. */
class MissileArchetypeTest {

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    @Test
    void theLinesLandInTheirArchetypes() {
        InstalledVanillaData.install();
        assertEquals(MissileArchetype.MIRV_BLOOM, MissileArchetype.of(VanillaWeapons.get("hurricane")));
        assertEquals(MissileArchetype.MIRV_BLOOM, MissileArchetype.of(VanillaWeapons.get("sabot")));
        assertEquals(MissileArchetype.SWARM_LIGHT, MissileArchetype.of(VanillaWeapons.get("locust")));
        assertEquals(MissileArchetype.SWARM_LIGHT, MissileArchetype.of(VanillaWeapons.get("swarmer")));
        assertEquals(MissileArchetype.ROCKET_RACK, MissileArchetype.of(VanillaWeapons.get("annihilator")));
        assertEquals(MissileArchetype.ROCKET_RACK, MissileArchetype.of(VanillaWeapons.get("squall")));
        assertEquals(MissileArchetype.HEAVY, MissileArchetype.of(VanillaWeapons.get("reaper")));
        assertEquals(MissileArchetype.HEAVY, MissileArchetype.of(VanillaWeapons.get("harpoon")));
    }

    @Test
    void onlyTheHammerFamilyMaySplitWhenAskedAndTheTorpedoesNever() {
        InstalledVanillaData.install();
        assertTrue(MissileArchetype.splittable(VanillaWeapons.get("hammer")));
        assertFalse(MissileArchetype.splittable(VanillaWeapons.get("reaper")));
        assertFalse(MissileArchetype.splittable(VanillaWeapons.get("hurricane")));
        assertFalse(MissileArchetype.splittable(VanillaWeapons.get("locust")));
    }

    @Test
    void mirvWarheadsCarryTheSpecsFanSoundAndShareOfTheCarriersToughness() {
        InstalledVanillaData.install();
        MissileFlightSpec.Mirv hurricane = VanillaWeapons.get("hurricane").flight().mirv();
        MissileFlightSpec.Mirv sabot = VanillaWeapons.get("sabot").flight().mirv();
        assertEquals(180f, hurricane.arcDegrees(), 0f);
        assertEquals(20f, sabot.arcDegrees(), 0f);
        assertEquals("hurricane_mirv_split", hurricane.splitSound());
        assertEquals(250f / 7f, hurricane.warheadHitpoints(), 0.01f);
        assertEquals(100f, sabot.warheadHitpoints(), 0.01f);
    }

    @Test
    void thePresetsOrderFromLiteralToCinematic() {
        assertTrue(MissilePreset.FAITHFUL.arrivalMaxSeconds() < MissilePreset.FLAVOURED.arrivalMaxSeconds());
        assertTrue(MissilePreset.FLAVOURED.arrivalMaxSeconds() < MissilePreset.CINEMATIC.arrivalMaxSeconds());
        assertFalse(MissilePreset.FAITHFUL.telegraph());
        assertTrue(MissilePreset.FLAVOURED.telegraph());
        assertFalse(MissilePreset.FLAVOURED.syntheticSplit());
        assertTrue(MissilePreset.CINEMATIC.syntheticSplit());
        assertEquals(MissilePreset.FAITHFUL, MissilePreset.CINEMATIC.next());
    }
}
