package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lift queue readout's own rules: the ferry's squad loads come first, then machine drops oldest first, each timed on the craft that frees soonest. */
class LiftQueueReadoutTest {

    private static final float[] ROUTE = {0f, 0f, 0f, 0f};
    private static final ArmorLoad HOUND = ArmorLoad.of(MechDeploymentSpec.standard(MechVariant.HOUND));
    private static final ArmorLoad BULWARK = ArmorLoad.of(MechDeploymentSpec.standard(MechVariant.BULWARK));
    private static final FittedBoat CRADLED_BUFFALO = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, BoatFitting.LIFT_CRADLE, "buffalo-1");

    /** A cradled ferry Buffalo on its way down with the first of two squad loads. */
    private static LiftQueueReadout.Craft inbound() {
        ShuttleMission mission = new ShuttleMission(40f, 0f, 0f, 0f, 0f, 0f, 0f, 6);
        mission.ferry = true;
        mission.state = ShuttleState.INCOMING;
        mission.totalCycles = 2;
        return new LiftQueueReadout.Craft(1L, mission, CRADLED_BUFFALO, 20f, 0f, 10f);
    }

    private static LiftQueue.MachineDrop drop(long id, ArmorLoad load) {
        return new LiftQueue.MachineDrop(id, "mech_support", (int) id, load, 40f, 0f, ROUTE, null);
    }

    @Test
    void aQueuedMachineWaitsBehindTheCraftsOwnSquadLoads() {
        LiftQueueReadout.Reading reading = LiftQueueReadout.read(List.of(inbound()),
                List.of(drop(1L, HOUND), drop(2L, HOUND)));

        assertEquals(1, reading.flights().size());
        assertSame(LiftQueueReadout.Cargo.SQUAD, reading.flights().get(0).cargo());
        LiftQueueReadout.Waiting squads = reading.waiting().get(0);
        LiftQueueReadout.Waiting first = reading.waiting().get(1);
        LiftQueueReadout.Waiting second = reading.waiting().get(2);
        assertSame(LiftQueueReadout.Cargo.SQUAD, squads.cargo());
        assertEquals(1, squads.loads(), "one squad sortie is still to fly");
        assertTrue(first.eta() > squads.eta(), first.eta() + " after " + squads.eta());
        assertTrue(second.eta() > first.eta(), "the one craft flies the drops in order");
    }

    @Test
    void aMachineNoLiveCraftCanLiftHasNoEta() {
        LiftQueueReadout.Reading reading = LiftQueueReadout.read(List.of(inbound()), List.of(drop(1L, BULWARK)));

        assertTrue(Float.isNaN(reading.waiting().get(1).eta()), "a light cradle cannot lift a Bulwark");
        assertTrue(Float.isNaN(LiftQueueReadout.read(List.of(), List.of(drop(1L, HOUND)))
                .waiting().get(0).eta()));
    }
}
