package com.dillon.starsectormarines.battle.turret;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TurretImpactAudioTest {

    @Test
    void authoredMissileCueWinsAndExplosiveFallbackRemains() {
        TurretImpactAudio.Cue locust = TurretImpactAudio.resolve(
                TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.LOCUST_STRUCTURE_ID),
                "generic-explosion");
        assertEquals("marines_missile_impact", locust.soundId());
        assertEquals(0.55f, locust.volume(), 0f);

        TurretImpactAudio.Cue cannon = TurretImpactAudio.resolve(
                TurretCatalogRegistry.requireStructure(
                        TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID), "generic-explosion");
        assertEquals("generic-explosion", cannon.soundId());
        assertEquals(0.82f, cannon.volume(), 0f);

        assertNull(TurretImpactAudio.resolve(TurretCatalogRegistry.requireStructure(
                TurretCatalogRegistry.ARBALEST_STRUCTURE_ID), "generic-explosion"));
    }
}
