package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.EmergencyResupply;
import com.dillon.starsectormarines.battle.power.MarineInsertion;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.ReconPing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerCatalogTest {

    @Test
    void valkyrieCommitsBothGroundSupportCapabilities() {
        assertEquals(List.of(MechSupport.ID, MarineInsertion.ID),
                PowerCatalog.contributedPowerIds("valkyrie", Set.of()));
    }

    @Test
    void groundSupportHullmodContributesMechAndOrbitalSupport() {
        assertEquals(List.of(MechSupport.ID, OrbitalBarrage.ID),
                PowerCatalog.contributedPowerIds("hammerhead",
                        Set.of("ground_support")));
    }

    @Test
    void hullAndHullmodSourcesDeduplicateInStableOrder() {
        assertEquals(List.of(ReconPing.ID),
                PowerCatalog.contributedPowerIds("apogee",
                        Set.of("hiressensors", "surveying_equipment")));
    }

    @Test
    void logisticsAndBombardmentHullFamiliesMapIndependently() {
        assertEquals(List.of(EmergencyResupply.ID),
                PowerCatalog.contributedPowerIds("atlas", Set.of()));
        assertEquals(List.of(OrbitalBarrage.ID),
                PowerCatalog.contributedPowerIds("invictus", Set.of()));
    }
}
