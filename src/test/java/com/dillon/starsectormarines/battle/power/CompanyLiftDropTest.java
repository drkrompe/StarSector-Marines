package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.ArmorLoad;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who flies a drop: the company's cradled boat when it can, the synthetic Valkyrie when it
 * cannot. Asked of the call's commit and the carrier choice; the carrier actually flying in
 * and unloading is {@code HeavyLiftScene}'s.
 */
class CompanyLiftDropTest {

    private static final FittedBoat CRADLED_BUFFALO = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE,
            BoatFitting.LIFT_CRADLE, "buffalo-1");
    private static final MechDeploymentSpec HOUND = MechDeploymentSpec.standard(MechVariant.HOUND);
    private static final MechDeploymentSpec BULWARK = MechDeploymentSpec.standard(MechVariant.BULWARK);

    @Test
    void theCompanyBoatFliesACallItCanLiftAndTheSyntheticCarrierTheRest() {
        List<LiftClass> hounds = List.of(LiftClass.LIGHT, LiftClass.LIGHT);
        List<LiftClass> mixed = List.of(LiftClass.LIGHT, LiftClass.HEAVY);

        assertSame(CRADLED_BUFFALO, AirDeliveryPower.chooseCarrier(
                hounds, CRADLED_BUFFALO, true, true, ShuttleType.VALKYRIE));
        assertSame(ShuttleType.VALKYRIE, AirDeliveryPower.chooseCarrier(
                mixed, CRADLED_BUFFALO, true, true, ShuttleType.VALKYRIE));
        assertSame(ShuttleType.VALKYRIE, AirDeliveryPower.chooseCarrier(
                hounds, CRADLED_BUFFALO, false, true, ShuttleType.VALKYRIE),
                "a boat already lost or in the air does not fly again");
        assertNull(AirDeliveryPower.chooseCarrier(
                mixed, CRADLED_BUFFALO, true, false, ShuttleType.VALKYRIE));
    }

    @Test
    void aCradledBuffaloFliesOneHoundPerSortie() {
        MechSupport power = MechSupport.configured(List.of(HOUND, HOUND), CRADLED_BUFFALO, false);

        Map<LiftBudget.Carrier, List<ArmorLoad>> sorties =
                power.commitCall(power.defaultSelection(null), true);

        assertEquals(1, sorties.size());
        LiftBudget.Carrier carrier = sorties.keySet().iterator().next();
        assertEquals(CRADLED_BUFFALO, carrier.frame(),
                "the company's own boat flies, so its loss is the company's");
        assertTrue(carrier.company());
        ShuttleMission mission = new ShuttleMission(15f, 15f, 0f, 0f, 0f, 0f, 0f, 0);
        MechSupport.loadArmor(mission, sorties.get(carrier), carrier.company());
        assertEquals(2, mission.totalCycles, "one trip per machine");
        assertEquals(1, mission.marinesRemaining);
    }

    @Test
    void aBulwarkIsRefusedWithoutHeavyLiftAndNothingIsSpent() {
        MechSupport power = MechSupport.configured(List.of(BULWARK), CRADLED_BUFFALO, false);
        int lift = power.budget().remaining();

        assertSame(LiftBudget.Refusal.NO_LIFT, power.picker(null, List.of()).get(0).refusal());
        assertTrue(power.commitCall(List.of(0), true).isEmpty());
        assertFalse(power.called(0));
        assertEquals(1, power.availableCalls(), "the Bulwark is still aboard");
        assertEquals(lift, power.budget().remaining());
    }

    @Test
    void withShipLiftBehindItABulwarkStillComesDownInTheValkyrie() {
        MechSupport power = MechSupport.configured(List.of(BULWARK), CRADLED_BUFFALO, true);

        Map<LiftBudget.Carrier, List<ArmorLoad>> sorties = power.commitCall(List.of(0), true);

        assertEquals(1, sorties.size());
        LiftBudget.Carrier carrier = sorties.keySet().iterator().next();
        assertSame(ShuttleType.VALKYRIE, carrier.frame());
        assertFalse(carrier.company());
    }

    @Test
    void armourSupportFliesTheHeavyApcInACradledBoat() {
        VehicleSupport power = VehicleSupport.configured(List.of(), CRADLED_BUFFALO, true);

        assertSame(CRADLED_BUFFALO, AirDeliveryPower.chooseCarrier(power.nextLoad(null),
                CRADLED_BUFFALO, true, power.syntheticLift(), ShuttleType.VALKYRIE));
    }
}
