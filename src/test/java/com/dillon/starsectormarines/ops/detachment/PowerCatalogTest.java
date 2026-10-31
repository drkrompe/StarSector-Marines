package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.fixture.CommandPowerCommitment;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.CloseAirSupport;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.SpawnInOnslaught;
import com.dillon.starsectormarines.battle.power.WolfStrike;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class PowerCatalogTest {

    private static final List<VehicleDeploymentSpec> BAY =
            List.of(new VehicleDeploymentSpec("v1", "Mule", VehicleType.HEAVY_APC));

    @Test void everySupplyRouteBattleCarriesOnlyTheOwnedFitThroughTheFrozenPowerSeam() {
        for (int operation : new int[]{1, 2, 3, 4}) for (MissionSource source : new MissionSource[]{MissionSource.STORY, MissionSource.RECURRING}) {
            var mission = Mission.builder().id(BrokenSupplyRouteProgress.PREFIX + operation + "_test")
                    .name("Route").type(MissionType.ASSAULT).source(source).risk(RiskLevel.LOW).build();
            assertEquals(List.of(), PowerCatalog.resolve(List.of(), mission, List.of()));
            var powers = PowerCatalog.resolve(List.of(), mission, List.of(WaylineHound.deployment()));
            assertEquals(1, powers.size());
            MechSupport restored = (MechSupport) CommandPowerCommitment.capture(powers.get(0)).toPower();
            assertEquals(List.of(WaylineHound.deployment()), restored.deployments());
        }
    }


    @Test
    void everyWolfGrantsWolfStrikeAndNoOtherHullDoes() {
        assertEquals(List.of(WolfStrike.ID), PowerCatalog.contributedPowerIds("wolf", Set.of()));
        assertEquals(List.of(), PowerCatalog.contributedPowerIds("hound", Set.of()));
    }

    @Test
    void everyOnslaughtGrantsItsDropAndTheInvictusDoesNot() {
        assertEquals(List.of(OrbitalBarrage.ID, SpawnInOnslaught.ID),
                PowerCatalog.contributedPowerIds("onslaught", Set.of()));
        assertEquals(List.of(OrbitalBarrage.ID), PowerCatalog.contributedPowerIds("invictus", Set.of()));
    }

    @Test
    void valkyrieGrantsMechSupportForTheWholeBay() {
        assertEquals(List.of(MechSupport.ID), PowerCatalog.contributedPowerIds("valkyrie", Set.of()));
    }

    @Test
    void theBayVehiclesRideMechSupportAcrossTheFrozenSeam() {
        Mission mission = Mission.builder().id("bay-test").name("Bay Test").type(MissionType.ASSAULT)
                .source(MissionSource.GENERATED).risk(RiskLevel.LOW)
                .employerPowerIds(List.of(MechSupport.ID)).build();

        List<CommandPower> powers = PowerCatalog.resolve(List.of(), mission, List.of(), BAY);

        MechSupport drop = assertInstanceOf(MechSupport.class, powers.get(0));
        assertEquals(List.of(), drop.deployments());
        assertEquals(BAY, drop.vehicles());
        MechSupport restored = (MechSupport) CommandPowerCommitment.capture(drop).toPower();
        assertEquals(BAY, restored.vehicles());
    }

    @Test
    void anArrangedLiftStoryBattleGrantsMechSupportForAVehicleOnlyBay() {
        Mission mission = Mission.builder().id(BrokenSupplyRouteProgress.PREFIX + "1_test")
                .name("Route").type(MissionType.ASSAULT).source(MissionSource.STORY).risk(RiskLevel.LOW).build();

        List<CommandPower> powers = PowerCatalog.resolve(List.of(), mission, List.of(), BAY);

        assertEquals(1, powers.size());
        MechSupport drop = assertInstanceOf(MechSupport.class, powers.get(0));
        assertEquals(BAY, drop.vehicles());
        assertEquals(BAY.size(), drop.manifest().size());
    }

    @Test
    void aFrozenArmourSupportRestoresAsMechSupportCarryingItsVehicles() {
        CommandPower restored = new CommandPowerCommitment("vehicle_support", List.of(), List.of(), BAY)
                .toPower();

        assertEquals(BAY, assertInstanceOf(MechSupport.class, restored).vehicles());
    }

    @Test
    void aFrozenCommitmentToARemovedPowerRestoresToNothing() {
        for (String id : List.of("recon_ping", "emergency_resupply", "marine_insertion", "vehicle_support")) {
            assertNull(PowerCatalog.restore(id, List.of()), id);
        }
    }

    @Test
    void aMudskipperOfEitherMarkGrantsCloseAirSupportAndNothingElse() {
        assertEquals(List.of(CloseAirSupport.ID), PowerCatalog.contributedPowerIds("mudskipper", Set.of()));
        assertEquals(List.of(CloseAirSupport.ID), PowerCatalog.contributedPowerIds("mudskipper_mk2", Set.of()));
        assertEquals(List.of(), PowerCatalog.contributedPowerIds("hermes", Set.of()));
    }

    @Test
    void aFrozenCloseAirSupportCommitmentRestoresThroughTheSameConstructor() {
        assertEquals(CloseAirSupport.ID, PowerCatalog.restore(CloseAirSupport.ID, List.of()).id);
    }

    @Test
    void theDebugOnslaughtSurvivesTheLaunchCommitmentRoundTrip() {
        CommandPower restored = CommandPowerCommitment.capture(new SpawnInOnslaught()).toPower();
        assertEquals(SpawnInOnslaught.ID, restored.id);
    }

    @Test
    void groundSupportHullmodContributesMechAndOrbitalSupport() {
        assertEquals(List.of(MechSupport.ID, OrbitalBarrage.ID),
                PowerCatalog.contributedPowerIds("hammerhead",
                        Set.of("ground_support")));
    }

    @Test
    void bombardmentHullsGrantOrbitalBarrage() {
        assertEquals(List.of(OrbitalBarrage.ID),
                PowerCatalog.contributedPowerIds("invictus", Set.of()));
        assertEquals(List.of(), PowerCatalog.contributedPowerIds("atlas", Set.of("hiressensors")));
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
                MechDeploymentSpec.standard(MechVariant.WARDEN),
                MechDeploymentSpec.standard(MechVariant.HOUND),
                MechDeploymentSpec.standard(MechVariant.BULWARK));

        List<CommandPower> powers = PowerCatalog.resolve(List.of(), mission, configured);

        assertEquals(1, powers.size());
        assertEquals(MechSupport.ID, powers.get(0).id);
        assertEquals(5, powers.get(0).availableCalls(),
                "five owned mechs freeze into the manifest, all aboard");
    }
}
