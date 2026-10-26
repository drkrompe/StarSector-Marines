package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftQueue;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An armor drop's own rules: one machine per order, free, paced by lift, and cancellable while it waits. */
class ArmorDropPowerTest {

    private static final float[] ROUTE = {-8f, 20f, -12f, 20f};
    private static final FittedBoat CRADLED_BUFFALO = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, BoatFitting.LIFT_CRADLE, "buffalo-1");
    private static final int HOUND = 0;
    private static final int BULWARK = 1;
    private static final int APC = 2;

    private static MechSupport bay(boolean heavyLift) {
        return MechSupport.configured(List.of(
                        MechDeploymentSpec.standard(MechVariant.HOUND),
                        MechDeploymentSpec.standard(MechVariant.BULWARK)),
                List.of(VehicleDeploymentSpec.standard(VehicleType.HEAVY_APC)),
                CRADLED_BUFFALO, heavyLift);
    }

    @Test
    void dropsAreFreeAndPacedOnlyByWhatIsAboard() {
        MechSupport power = bay(true);

        assertEquals(0f, power.cpCost);
        assertEquals(0f, power.cooldownSeconds);
        assertEquals(0, power.maxCharges);
        assertEquals(3, power.availableCalls());
    }

    @Test
    void anOrderSpendsOneLiftAndQueuesExactlyThatMachine() {
        MechSupport power = bay(false);
        LiftQueue queue = new LiftQueue();
        int lift = power.budget().remaining();

        LiftQueue.MachineDrop job = power.order(HOUND, 12f, 21f, ROUTE, null, queue);

        assertNotNull(job);
        assertEquals(HOUND, job.manifestIndex());
        assertSame(power.manifest().get(HOUND).load(), job.load());
        assertEquals(lift - 1, power.budget().remaining());
        assertTrue(queue.active(), "an order turns the ferry's standby on");
        assertEquals(ArmorDropPower.Status.QUEUED, power.status(HOUND, queue));
        assertEquals(ArmorDropPower.Status.ABOARD, power.status(APC, queue));
        assertNull(power.order(HOUND, 12f, 21f, ROUTE, null, queue), "a machine is ordered once");
        assertEquals(1, queue.queued().size());
    }

    @Test
    void aMachineNoCarrierLiftsIsRefusedWithItsReasonAndNothingIsSpent() {
        MechSupport power = bay(false);
        LiftQueue queue = new LiftQueue();
        int lift = power.budget().remaining();

        ArmorDropPower.PickerRow bulwark = power.picker(null, null).get(BULWARK);
        assertSame(LiftBudget.Refusal.NO_LIFT, bulwark.refusal());
        assertFalse(bulwark.callable());
        assertNull(power.order(BULWARK, 12f, 21f, ROUTE, null, queue));
        assertFalse(power.called(BULWARK));
        assertEquals(lift, power.budget().remaining());
        assertEquals(HOUND, power.defaultPick(null), "the picker opens on the first liftable machine");
    }

    @Test
    void cancellingAWaitingDropPutsTheMachineBackAboardAndRefundsItsLift() {
        MechSupport power = bay(false);
        LiftQueue queue = new LiftQueue();
        int lift = power.budget().remaining();
        LiftQueue.MachineDrop job = power.order(HOUND, 12f, 21f, ROUTE, null, queue);

        assertTrue(power.cancel(job.id(), queue));

        assertFalse(power.called(HOUND));
        assertEquals(lift, power.budget().remaining());
        assertTrue(queue.queued().isEmpty());
        assertEquals(ArmorDropPower.Status.ABOARD, power.status(HOUND, queue));
    }

    @Test
    void aDropAlreadyTakenByACraftCannotBeCancelled() {
        MechSupport power = bay(false);
        LiftQueue queue = new LiftQueue();
        LiftQueue.MachineDrop job = power.order(HOUND, 12f, 21f, ROUTE, null, queue);
        assertSame(job, queue.takeFor(CRADLED_BUFFALO));

        assertFalse(power.cancel(job.id(), queue));
        assertTrue(power.called(HOUND));
        assertEquals(ArmorDropPower.Status.CALLED, power.status(HOUND, queue));
    }

    @Test
    void aPickNamesExactlyOneMachine() {
        assertEquals(2, ArmorDropPower.single(List.of(2)));
        assertNull(ArmorDropPower.single(List.of(0, 2)));
        assertNull(ArmorDropPower.single(List.of()));
        assertNull(ArmorDropPower.single(null));
    }
}
