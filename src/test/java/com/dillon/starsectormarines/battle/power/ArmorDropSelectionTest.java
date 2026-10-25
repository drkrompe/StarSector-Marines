package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.ArmorLoad;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calling armor is a choice from the frozen bay: subset, lift and cooldown. Asked of the
 * manifest, the call's commit and the cooldown; the picked machine landing while the rest
 * stay aboard is {@code ArmorDropScene}'s.
 */
class ArmorDropSelectionTest {

    private static final FittedBoat LIGHT_CRADLE = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE,
            BoatFitting.LIFT_CRADLE, "buffalo-1");
    private static final MechDeploymentSpec HOUND = new MechDeploymentSpec(MechVariant.HOUND,
            null, null, MechVariant.HOUND.arms, MechVariant.HOUND.leftShoulder,
            MechVariant.HOUND.rightShoulder, "Hammer");
    private static final MechDeploymentSpec BULWARK = MechDeploymentSpec.standard(MechVariant.BULWARK);
    private static final VehicleDeploymentSpec APC =
            new VehicleDeploymentSpec("v-1", "Mule", VehicleType.HEAVY_APC);
    private static final int HOUND_AT = 0;
    private static final int BULWARK_AT = 1;
    private static final int APC_AT = 2;

    @Test
    void theManifestIsFrozenAtLaunchMechsThenVehicles() {
        List<MechDeploymentSpec> mechs = new ArrayList<>(List.of(HOUND, BULWARK));
        MechSupport power = MechSupport.configured(mechs, List.of(APC), LIGHT_CRADLE, false);
        mechs.clear();

        BayManifest manifest = power.manifest();
        assertEquals(3, manifest.size());
        assertEquals("Hammer", manifest.get(HOUND_AT).callsign());
        assertTrue(manifest.get(APC_AT).isVehicle());
        assertEquals("Mule", manifest.get(APC_AT).callsign());
        assertThrows(UnsupportedOperationException.class,
                () -> manifest.entries().add(manifest.get(0)));
    }

    @Test
    void onlyThePickedMachineIsCommittedAndTheRestStayAboard() {
        MechSupport power = power();

        Map<LiftBudget.Carrier, List<ArmorLoad>> sorties = power.commitCall(List.of(HOUND_AT), true);

        assertEquals(1, sorties.size());
        LiftBudget.Carrier carrier = sorties.keySet().iterator().next();
        assertEquals(LIGHT_CRADLE, carrier.frame());
        assertEquals(1, sorties.get(carrier).size());
        assertSame(HOUND, sorties.get(carrier).get(0).mech());
        assertTrue(power.called(HOUND_AT));
        assertFalse(power.called(APC_AT));
        assertEquals(2, power.availableCalls(), "two machines still aboard");
    }

    @Test
    void aMachineNoCarrierLiftsIsRefusedWithItsReasonAndNothingIsSpent() {
        MechSupport power = power();
        int lift = power.budget().remaining();

        assertSame(LiftBudget.Refusal.NO_LIFT,
                power.picker(null, List.of()).get(BULWARK_AT).refusal());
        assertTrue(power.commitCall(List.of(BULWARK_AT), true).isEmpty());
        assertFalse(power.called(BULWARK_AT));
        assertEquals(lift, power.budget().remaining());
    }

    @Test
    void aCallStartsTheCooldownAndACalledMachineCannotComeAgain() {
        MechSupport power = MechSupport.configured(List.of(HOUND), List.of(APC), null, true);
        CommandPowerService service = new CommandPowerService();
        service.register(power);
        while (!service.canActivate(power)) service.regenCommandPoints(1f);

        assertTrue(service.commit(power));
        power.commitCall(List.of(0), true);
        assertTrue(service.getCooldownRemaining(power.id) > 0f);
        assertFalse(service.canActivate(power), "on cooldown");
        service.tickCooldowns(MechSupport.CALL_COOLDOWN_SECONDS);
        assertEquals(0f, service.getCooldownRemaining(power.id), 0f);

        assertTrue(power.picker(null, null).get(0).called());
        assertTrue(power.commitCall(List.of(0), true).isEmpty(), "the Hound has been called");
        assertEquals(List.of(1), power.defaultSelection(null));
    }

    private static MechSupport power() {
        return MechSupport.configured(List.of(HOUND, BULWARK), List.of(APC), LIGHT_CRADLE, false);
    }
}
