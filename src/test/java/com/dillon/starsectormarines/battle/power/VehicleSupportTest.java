package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VehicleSupportTest {

    private static final VehicleDeploymentSpec APC =
            new VehicleDeploymentSpec("v1", "Mule", VehicleType.HEAVY_APC);
    private static final VehicleDeploymentSpec TANK =
            new VehicleDeploymentSpec("v2", "Ox", VehicleType.BREAKTHROUGH_TANK);

    @Test
    void eachChargeBringsDownTheNextBayVehicleInGantryOrder() {
        VehicleSupport power = VehicleSupport.configured(List.of(APC, TANK));

        assertEquals(2, power.maxCharges);
        assertEquals(APC, power.deploymentForRemainingCharges(1));
        assertEquals(TANK, power.deploymentForRemainingCharges(0));
    }

    @Test
    void anUnconfiguredGrantCarriesNoCompanyVehicle() {
        VehicleSupport power = new VehicleSupport();

        assertEquals(2, power.maxCharges);
        assertEquals(List.of(), power.deployments());
        assertNull(power.deploymentForRemainingCharges(1));
    }
}
