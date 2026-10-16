package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.ReconPing;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The targeting panel's pick rules: arming starts from the default call, and toggles admit
 * only callable entries. Asked of the panel's own rules against a battle-free picker; the
 * armed call landing on the map is {@code ArmorDropScene}'s.
 */
class CommandPowerTargetingPanelPickTest {

    private static final FittedBoat CRADLED_BUFFALO = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE,
            BoatFitting.LIFT_CRADLE, "buffalo-1");

    @Test
    void armingMechSupportStartsFromTheDefaultPickAndTogglesOnlyCallableEntries() {
        MechSupport mech = MechSupport.configured(List.of(
                        MechDeploymentSpec.standard(MechVariant.HOUND),
                        MechDeploymentSpec.standard(MechVariant.BULWARK)),
                List.of(VehicleDeploymentSpec.standard(VehicleType.HEAVY_APC)),
                CRADLED_BUFFALO, false);

        List<Integer> selection = CommandPowerTargetingPanel.armedSelection(mech, null);
        assertEquals(List.of(0, 2), selection);
        assertEquals(3, mech.picker(null, selection).size());

        toggle(mech, selection, 1);
        assertEquals(List.of(0, 2), selection, "the Bulwark has no heavy lift");
        toggle(mech, selection, 2);
        assertEquals(List.of(0), selection);
        assertFalse(mech.picker(null, selection).get(2).selected());
        toggle(mech, selection, 2);
        assertEquals(List.of(0, 2), selection);
        toggle(mech, selection, 0);
        toggle(mech, selection, 2);
        assertEquals(List.of(), selection, "a picked entry can always be dropped");
    }

    @Test
    void aPowerWithoutAPickArmsWithNone() {
        assertNull(CommandPowerTargetingPanel.armedSelection(new ReconPing(), null));
    }

    private static void toggle(MechSupport mech, List<Integer> selection, int index) {
        CommandPowerTargetingPanel.togglePick(selection, mech.picker(null, selection), index);
    }
}
