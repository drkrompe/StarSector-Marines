package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the briefing says about who lifts a drop. */
class LiftReadoutTest {

    private static final FittedBoat CRADLED = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, BoatFitting.LIFT_CRADLE, "b");

    @Test
    void namesTheCompanyCarrierAndEachMachineItCannotLift() {
        MechSupport power = MechSupport.configured(List.of(
                MechDeploymentSpec.standard(MechVariant.HOUND),
                MechDeploymentSpec.standard(MechVariant.BULWARK)), CRADLED, false);

        List<String> lines = LiftReadout.lines(power);

        assertEquals(2, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("Buffalo"), lines.get(0));
        assertTrue(lines.get(1).contains(MechVariant.BULWARK.displayName())
                && lines.get(1).contains("Heavy"), lines.get(1));
    }

    @Test
    void aSyntheticCarrierAloneSaysNothing() {
        assertTrue(LiftReadout.lines(new MechSupport()).isEmpty());
    }

    @Test
    void theLandingCardCountsCradledBoats() {
        assertNull(LiftReadout.cradledNote(List.of(FittedBoat.standard(ShuttleType.BUFFALO))));
        assertTrue(LiftReadout.cradledNote(List.of(CRADLED)).contains("Light"));
    }
}
