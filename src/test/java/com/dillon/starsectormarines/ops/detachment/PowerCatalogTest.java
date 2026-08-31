package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.EmergencyResupply;
import com.dillon.starsectormarines.battle.power.MarineInsertion;
import com.dillon.starsectormarines.battle.power.VehicleSupport;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.ReconPing;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerCatalogTest {

    @Test
    void valkyrieCommitsEveryGroundSupportCapabilityItCarries() {
        // The heavy transport that brings a mech lance down also brings the
        // armour: same carrier, same delivery, so it grants all three.
        assertEquals(List.of(MechSupport.ID, MarineInsertion.ID, VehicleSupport.ID),
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

    @Test
    void sourcedMechPowerUsesConfiguredCampaignSquadSize() {
        Mission mission = Mission.builder()
                .id("mech-lab-test")
                .name("Mech Lab Test")
                .type(MissionType.ASSAULT)
                .source(MissionSource.GENERATED)
                .risk(RiskLevel.LOW)
                .employerPowerIds(List.of(MechSupport.ID))
                .build();
        List<MechDeploymentSpec> configured = List.of(
                MechDeploymentSpec.standard(MechVariant.BULWARK),
                MechDeploymentSpec.standard(MechVariant.HOUND),
                MechDeploymentSpec.standard(MechVariant.SIROCCO),
                MechDeploymentSpec.standard(MechVariant.HOUND),
                MechDeploymentSpec.standard(MechVariant.BULWARK));

        List<CommandPower> powers = PowerCatalog.resolve(List.of(), mission, configured);

        assertEquals(1, powers.size());
        assertEquals(MechSupport.ID, powers.get(0).id);
        assertEquals(2, powers.get(0).maxCharges,
                "five owned mechs should freeze as two physical support sorties");
    }
}
