package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRefit;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The launch freezes the Mech Lab's upgrades into each owned mech and vehicle, and nowhere else. */
class DetachmentMechRefitTest {
    @Test void onlyTheCompanysOwnMechsCarryTheRefit() {
        MechRefit refit = new MechRefit(1.03f, 1.10f, 1.05f, 1f);
        MechDeploymentSpec stock = MechDeploymentSpec.standard(MechVariant.BULWARK);
        MechDeploymentSpec owned = new MechDeploymentSpec(stock.variant(), stock.role(), stock.missileReplenisher(),
                stock.arms(), stock.leftArm(), stock.rightArm(), stock.leftShoulder(), stock.rightShoulder(),
                "Anvil", stock.frame(), "mech-1");

        List<MechDeploymentSpec> frozen = DetachmentResolver.withShipRefit(List.of(owned, stock), refit);

        assertEquals(refit, frozen.get(0).refit());
        assertEquals("mech-1", frozen.get(0).campaignId());
        assertEquals(MechRefit.NEUTRAL, frozen.get(1).refit(), "a mech the company does not own stays neutral");
        List<MechDeploymentSpec> lance = List.of(owned);
        assertSame(lance, DetachmentResolver.withShipRefit(lance, MechRefit.NEUTRAL));
    }

    @Test void theCompanysOwnVehiclesCarryTheSameUpgrades() {
        MechRefit refit = new MechRefit(1.03f, 1.10f, 1.05f, 1.25f);
        VehicleDeploymentSpec owned = new VehicleDeploymentSpec("vehicle-1", "Mule", VehicleType.HEAVY_APC);
        VehicleDeploymentSpec lent = VehicleDeploymentSpec.standard(VehicleType.HEAVY_APC);

        List<VehicleDeploymentSpec> frozen = DetachmentResolver.withShipRefitOnVehicles(List.of(owned, lent), refit);

        assertEquals(refit, frozen.get(0).refit());
        assertEquals(MechRefit.NEUTRAL, frozen.get(1).refit(), "a vehicle the company does not own stays neutral");
    }
}
