package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.style.StyleProperty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.dillon.starsectormarines.ops.BriefingLift.Status.DROPS;
import static com.dillon.starsectormarines.ops.BriefingLift.Status.HELD_BACK;
import static com.dillon.starsectormarines.ops.BriefingLift.Status.LIFT_SPENT;
import static com.dillon.starsectormarines.ops.BriefingLift.Status.NOT_CALLED;
import static com.dillon.starsectormarines.ops.BriefingLift.Status.NO_DROP_CALL;
import static com.dillon.starsectormarines.ops.BriefingLift.Status.NO_LIFT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BriefingLiftTest {

    private static final MechDeploymentSpec HOUND = MechDeploymentSpec.standard(MechVariant.HOUND);
    private static final MechDeploymentSpec WARDEN = MechDeploymentSpec.standard(MechVariant.WARDEN);
    private static final List<FittedBoat> HERMES = List.of(FittedBoat.standard(ShuttleType.HERMES));
    private static final FittedBoat BUFFALO = new FittedBoat(ShuttleType.BUFFALO, BoatFitting.STANDARD_PLATING,
            BoatFitting.STANDARD_DRIVE, BoatFitting.LIFT_CRADLE, "buffalo-1");

    @Test
    void withoutADropCallTheWholeBayStaysAboard() {
        var readout = BriefingLift.read(HERMES, 2, List.of(), Set.of(), List.of(HOUND),
                List.of(new VehicleDeploymentSpec(null, "Mule", VehicleType.HEAVY_APC)), List.of(WARDEN));
        assertEquals(List.of(NO_DROP_CALL, NO_DROP_CALL, HELD_BACK), statuses(readout));
        assertFalse(readout.missionLift());
        assertFalse(readout.dropCall());
        assertEquals(LiftRating.NONE, readout.companyBest());
        assertEquals(0, readout.dropping());
    }

    @Test
    void missionHeavyLiftDropsTheBayOnlyOnceTheCallIsOnTheDeck() {
        List<CommandPower> powers = List.of(MechSupport.configured(List.of(HOUND, WARDEN)));
        assertEquals(List.of(NOT_CALLED, NOT_CALLED),
                statuses(BriefingLift.read(HERMES, 2, powers, Set.of(), List.of(), List.of(), List.of())));
        var called = BriefingLift.read(HERMES, 2, powers, Set.of(MechSupport.ID), List.of(), List.of(), List.of());
        assertEquals(List.of(DROPS, DROPS), statuses(called));
        assertTrue(called.missionLift());
    }

    @Test
    void aLightCradleAloneRefusesMediumAndRunsOutAfterTwoLifts() {
        List<CommandPower> powers = List.of(MechSupport.configured(List.of(HOUND, WARDEN, HOUND, HOUND),
                BUFFALO, false));
        var readout = BriefingLift.read(List.of(BUFFALO), 2, powers, Set.of(MechSupport.ID),
                List.of(), List.of(), List.of());
        // Refused first, then what drops, each in bay order.
        assertEquals(List.of(NO_LIFT, LIFT_SPENT, DROPS, DROPS), statuses(readout));
        assertEquals(LiftRating.LIGHT, readout.companyBest());
        assertEquals(BUFFALO, readout.companyCarrier());
        assertFalse(readout.missionLift());
    }

    private static List<BriefingLift.Status> statuses(BriefingLift.Readout readout) {
        return readout.machines().stream().map(BriefingLift.Machine::status).toList();
    }

    @Test
    void aLitWarningBreathesFromFullToDimAndBackOnOneSlowCycle() {
        UiElement warn = new UiElement("warn");
        float period = BriefingLift.WARN_PULSE_SECONDS;
        BriefingLift.pulse(List.of(warn), 0f);
        assertEquals(1f, ((Number) warn.authoredStyle().value(StyleProperty.OPACITY)).floatValue(), 1e-3f);
        BriefingLift.pulse(List.of(warn), period * .5f);
        assertEquals(.4f, ((Number) warn.authoredStyle().value(StyleProperty.OPACITY)).floatValue(), 1e-3f);
        BriefingLift.pulse(List.of(warn), period * 3f);
        assertEquals(1f, ((Number) warn.authoredStyle().value(StyleProperty.OPACITY)).floatValue(), 1e-3f);
    }
}
