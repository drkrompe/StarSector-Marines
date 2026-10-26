package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lift queue's own rules: first in first out, a craft takes only what it lifts, and a cancel reaches only waiting jobs. */
class LiftQueueTest {

    private static final float[] ROUTE = {-8f, 20f, -12f, 20f};
    private static final ArmorLoad HOUND = ArmorLoad.of(MechDeploymentSpec.standard(MechVariant.HOUND));
    private static final ArmorLoad BULWARK = ArmorLoad.of(MechDeploymentSpec.standard(MechVariant.BULWARK));
    private static final ArmorLoad APC = ArmorLoad.of(VehicleDeploymentSpec.standard(VehicleType.HEAVY_APC));
    private static final FittedBoat CRADLED_BUFFALO = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, BoatFitting.LIFT_CRADLE, "buffalo-1");

    @Test
    void aCraftTakesTheOldestJobItCanLiftAndSkipsTheRest() {
        LiftQueue queue = new LiftQueue();
        LiftQueue.MachineDrop bulwark = queue.enqueue("mech_support", 1, BULWARK, 10f, 20f, ROUTE, null);
        LiftQueue.MachineDrop hound = queue.enqueue("mech_support", 0, HOUND, 12f, 20f, ROUTE, null);

        assertSame(hound, queue.takeFor(CRADLED_BUFFALO), "a light cradle passes the Bulwark by");
        assertNull(queue.takeFor(CRADLED_BUFFALO));
        assertNull(queue.takeFor(ShuttleType.AEROSHUTTLE), "a craft with no lift takes no machine");
        assertSame(bulwark, queue.takeFor(ShuttleType.VALKYRIE));
        assertTrue(queue.queued().isEmpty());
    }

    @Test
    void jobsAreTakenInTheOrderTheyWereQueued() {
        LiftQueue queue = new LiftQueue();
        LiftQueue.MachineDrop first = queue.enqueue("mech_support", 2, APC, 10f, 20f, ROUTE, null);
        queue.enqueue("mech_support", 0, HOUND, 12f, 20f, ROUTE, null);

        assertSame(first, queue.takeFor(ShuttleType.VALKYRIE));
    }

    @Test
    void onlyAWaitingJobCanBeCancelled() {
        LiftQueue queue = new LiftQueue();
        LiftQueue.MachineDrop hound = queue.enqueue("mech_support", 0, HOUND, 12f, 20f, ROUTE, null);
        LiftQueue.MachineDrop apc = queue.enqueue("mech_support", 2, APC, 10f, 20f, ROUTE, null);

        assertTrue(queue.isQueued("mech_support", 0));
        assertSame(hound, queue.takeFor(CRADLED_BUFFALO));
        assertNull(queue.cancel(hound.id()), "a taken job is in the air");
        assertFalse(queue.isQueued("mech_support", 0));
        assertSame(apc, queue.cancel(apc.id()));
        assertTrue(queue.queued().isEmpty());
    }

    @Test
    void aStandbyCraftLoadsExactlyOneMachineForItsLandingGround() {
        ShuttleMission mission = new ShuttleMission(40f, 40f, 0f, 0f, 0f, 0f, 0f, 6);
        mission.ferry = true;
        mission.currentCycle = 2;
        mission.squadId = 7;
        mission.arrivalGroupId = 3;
        LiftQueue.standBy(mission, 8f);

        assertTrue(mission.standby);
        assertEquals(ShuttleState.PENDING, mission.state);
        assertEquals(0, mission.marinesRemaining);
        assertEquals(3, mission.totalCycles, "no sortie of its own is left");
        assertEquals(8f, mission.pendingDelay);

        LiftQueue queue = new LiftQueue();
        LiftQueue.loadMachine(mission, queue.enqueue("mech_support", 0, HOUND, 12f, 21f, ROUTE, null));

        assertFalse(mission.standby);
        assertSame(ArmorDropPayload.INSTANCE, mission.payload);
        assertEquals(1, mission.armorDrop.length, "one machine per sortie");
        assertSame(HOUND, mission.armorDrop[0]);
        assertEquals(1, mission.marinesRemaining);
        assertEquals(Squad.NO_SQUAD, mission.squadId, "the machine lands as its own squad");
        assertEquals(-1, mission.arrivalGroupId);
        assertEquals(12f, mission.lzX);
        assertEquals(21f, mission.lzY);
        assertEquals(ROUTE[0], mission.entryX);
        assertEquals(ROUTE[3], mission.exitY);
        assertSame(MechVariant.HOUND, mission.mechVariant);
    }
}
