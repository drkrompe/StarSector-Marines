package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.fixture.CommandPowerCommitment;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.VehicleSupport;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleSupportCatalogTest {

    private static final List<VehicleDeploymentSpec> BAY =
            List.of(new VehicleDeploymentSpec("v1", "Mule", VehicleType.HEAVY_APC));

    @Test
    void aSourcedArmourSupportCarriesTheBayVehiclesAcrossTheFrozenSeam() {
        List<CommandPower> powers = PowerCatalog.resolve(List.of(), generated(), List.of(), BAY);

        VehicleSupport power = (VehicleSupport) powers.get(0);
        assertEquals(BAY, power.deployments());
        VehicleSupport restored = (VehicleSupport) CommandPowerCommitment.capture(power).toPower();
        assertEquals(BAY, restored.deployments());
    }

    @Test
    void aBayWithNoVehicleHasNoArmourSupportToCall() {
        List<CommandPower> powers = PowerCatalog.resolve(List.of(), generated(), List.of(), List.of());

        assertTrue(powers.stream().noneMatch(power -> power instanceof VehicleSupport));
    }

    @Test
    void withoutABayArmourSupportKeepsItsFallbackHull() {
        List<CommandPower> powers = PowerCatalog.resolve(List.of(), generated(), List.of(), null);

        assertEquals(List.of(), ((VehicleSupport) powers.get(0)).deployments());
    }

    @Test
    void anArrangedLiftStoryBattleBringsTheBayVehiclesDownBesideItsMechs() {
        Mission mission = Mission.builder().id(BrokenSupplyRouteProgress.PREFIX + "1_test")
                .name("Route").type(MissionType.ASSAULT).source(MissionSource.STORY).risk(RiskLevel.LOW).build();

        List<CommandPower> powers = PowerCatalog.resolve(List.of(), mission,
                List.of(WaylineHound.deployment()), BAY);

        assertEquals(2, powers.size());
        assertInstanceOf(MechSupport.class, powers.get(0));
        assertEquals(BAY, ((VehicleSupport) powers.get(1)).deployments());
    }

    private static Mission generated() {
        return Mission.builder().id("armour-test").name("Armour Test").type(MissionType.ASSAULT)
                .source(MissionSource.GENERATED).risk(RiskLevel.LOW)
                .employerPowerIds(List.of(VehicleSupport.ID)).build();
    }
}
