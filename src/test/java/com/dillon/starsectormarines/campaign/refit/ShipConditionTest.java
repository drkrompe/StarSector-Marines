package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipConditionTest {
    @Test void otherHullsAreNeutralOnEveryTrack() {
        for (RefitTrack track : RefitTrack.values()) assertEquals(RefitBand.RESTORED, ShipCondition.NONE.band(track));
        assertEquals(1f, ShipCondition.NONE.moraleRecovery());
        assertEquals(0f, ShipCondition.NONE.woundRecoveryExtraDays());
        assertEquals(1f, ShipCondition.NONE.hullMult());
        assertEquals(0, ShipCondition.NONE.burnDelta());
        assertEquals(EnumSet.allOf(ShipCore.class), ShipCondition.NONE.restoredCores());
    }

    @Test void theBatteredStartIsADeficitAndNeutralIsRestored() {
        var start = ShipCondition.battered();
        assertEquals(0.85f, start.moraleRecovery());
        assertEquals(1f, start.woundRecoveryExtraDays());
        assertTrue(start.hullMult() < 1f && start.fluxCapacityMult() < 1f && start.sensorStrengthMult() < 1f);
        assertEquals(-1, start.burnDelta());
        assertTrue(start.restoredCores().isEmpty());
        var restored = start;
        for (RefitTrack track : RefitTrack.values()) restored = restored.with(track, RefitBand.RESTORED);
        assertEquals(1f, restored.moraleRecovery());
        assertEquals(1f, restored.hullMult());
        assertEquals(1f, restored.fluxDissipationMult());
        assertEquals(0, restored.burnDelta());
    }

    @Test void messIsReBasedWithTheShippedValueAtItsTop() {
        var mess = ShipCondition.battered();
        assertEquals(1.15f, mess.with(RefitTrack.MESS, RefitBand.IMPROVED_I).moraleRecovery());
        assertEquals(1.25f, mess.with(RefitTrack.MESS, RefitBand.IMPROVED_II).moraleRecovery());
    }

    @Test void everyOrderAdvancesItsTrackByOneTierAndNamesOnlyOtherTracks() {
        for (RefitOrder order : RefitOrder.values()) {
            assertNotEquals(RefitBand.DEFICIT, order.band);
            assertFalse(order.requires.containsKey(order.track), order.name());
            assertSame(order, RefitOrder.after(order.track, RefitBand.values()[order.band.ordinal() - 1]));
        }
        for (RefitTrack track : RefitTrack.values())
            if (track != RefitTrack.FABRICATION) assertNotNull(RefitOrder.after(track, track.start()), track.name());
        assertNull(RefitOrder.after(RefitTrack.FABRICATION, RefitBand.DEFICIT));
    }

    @Test void theWholeTreeIsReachableFromTheBatteredStart() {
        var state = ShipCondition.battered().with(RefitTrack.FABRICATION, RefitBand.IMPROVED_I);
        for (boolean progress = true; progress; ) {
            progress = false;
            for (RefitTrack track : RefitTrack.values()) {
                RefitOrder next = state.next(track);
                if (next != null && state.missing(next) == null) { state = state.with(track, next.band); progress = true; }
            }
        }
        for (RefitTrack track : RefitTrack.values()) assertEquals(RefitOrder.ceiling(track), state.band(track), track.name());
    }

    @Test void combatTracksStayALiabilityBehindTheCores() {
        var cheap = ShipCondition.battered();
        for (RefitTrack core : new RefitTrack[]{RefitTrack.POWER, RefitTrack.LIFE_SUPPORT, RefitTrack.STRUCTURE, RefitTrack.COMPUTE})
            cheap = cheap.with(core, RefitBand.RESTORED);
        assertNotNull(cheap.missing(RefitOrder.FLUX_RESTORED), "flux waits on the reactor overhaul");
        assertNull(cheap.missing(RefitOrder.HULL_RESTORED));
        for (RefitOrder order : RefitOrder.values())
            if (order.track.group == RefitTrack.Group.COMBAT && order.band == RefitBand.RESTORED)
                assertFalse(order.requires.isEmpty(), order.name());
    }

    @Test void fabricationIsTheWorkshopChain() {
        assertEquals(RefitBand.DEFICIT, ShipCondition.fabrication(WorkshopState.BURIED));
        assertEquals(RefitBand.DEFICIT, ShipCondition.fabrication(WorkshopState.CLEARED));
        assertEquals(RefitBand.RESTORED, ShipCondition.fabrication(WorkshopState.REPAIRED));
        assertEquals(RefitBand.IMPROVED_I, ShipCondition.fabrication(WorkshopState.EQUIPPED));
    }

    @Test void receiptsCountOnlyWhileContiguousAndKeepTheOriginalOrderKeys() {
        Map<String, Object> data = new HashMap<>();
        data.put("sm_ship_restoration_swPOWER", true);
        data.put("sm_ship_restoration_swMESS_HALL", true);
        data.put("sm_ship_restoration_swMESS_COLD_STORE", true);
        var read = ShipRefits.read(data, "sw", RefitBand.DEFICIT);
        assertEquals(RefitBand.RESTORED, read.band(RefitTrack.POWER));
        assertEquals(RefitBand.RESTORED, read.band(RefitTrack.MESS), "a skipped galley cannot count the cold store");
        assertEquals(RefitBand.DEFICIT, read.band(RefitTrack.HULL));
        assertEquals(EnumSet.of(ShipCore.POWER), read.restoredCores());
    }
}
