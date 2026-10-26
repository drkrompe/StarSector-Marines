package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.ArmorDropPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drop picker's own rules: it opens on the first liftable machine, a chip picks only a
 * machine that can be ordered, a machine already placed this pause reads as queued, and the
 * pause it holds gives back the state it found.
 */
class CommandPowerTargetingPanelPickTest {

    private static final FittedBoat CRADLED_BUFFALO = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE,
            BoatFitting.LIFT_CRADLE, "buffalo-1");

    private static MechSupport bay() {
        return MechSupport.configured(List.of(
                        MechDeploymentSpec.standard(MechVariant.BULWARK),
                        MechDeploymentSpec.standard(MechVariant.HOUND)),
                List.of(VehicleDeploymentSpec.standard(VehicleType.HEAVY_APC)),
                CRADLED_BUFFALO, false);
    }

    @Test
    void thePickerOpensOnTheFirstLiftableMachineAndPicksOnlyCallableOnes() {
        List<ArmorDropPower.PickerRow> rows = bay().picker(null, null);

        Integer pick = CommandPowerTargetingPanel.firstCallable(rows);
        assertEquals(1, pick, "the Bulwark has no heavy lift");
        assertEquals(1, CommandPowerTargetingPanel.choose(rows, 0, pick), "a refused chip keeps the pick");
        assertEquals(2, CommandPowerTargetingPanel.choose(rows, 2, pick));
    }

    @Test
    void aMachinePlacedThisPauseReadsQueuedAndTheNextPickMovesOn() {
        List<ArmorDropPower.PickerRow> rows = CommandPowerTargetingPanel.withPlaced(
                bay().picker(null, 1), Set.of(1));

        assertEquals(ArmorDropPower.Status.QUEUED, rows.get(1).status());
        assertFalse(rows.get(1).selected());
        assertEquals(2, CommandPowerTargetingPanel.firstCallable(rows));
        assertNull(CommandPowerTargetingPanel.firstCallable(
                CommandPowerTargetingPanel.withPlaced(rows, Set.of(2))), "nothing left to place");
    }

    @Test
    void thePickerPausesARunningBattleAndResumesItWhenItCloses() {
        Clock clock = new Clock(false);
        PlacementPause pause = new PlacementPause(clock);

        pause.hold();
        assertTrue(clock.paused);
        pause.hold();
        pause.release();
        assertFalse(clock.paused);
        assertEquals(2, clock.changes);
    }

    @Test
    void aBattleAlreadyPausedStaysPausedWhenThePickerCloses() {
        Clock clock = new Clock(true);
        PlacementPause pause = new PlacementPause(clock);

        pause.hold();
        pause.release();
        assertTrue(clock.paused);
        assertEquals(0, clock.changes);
        pause.release();
        assertTrue(clock.paused, "a release without a hold changes nothing");
    }

    private static final class Clock implements PlacementPause.Clock {
        boolean paused;
        int changes;

        Clock(boolean paused) {
            this.paused = paused;
        }

        @Override public boolean paused() { return paused; }

        @Override public void setPaused(boolean paused) {
            this.paused = paused;
            changes++;
        }
    }
}
