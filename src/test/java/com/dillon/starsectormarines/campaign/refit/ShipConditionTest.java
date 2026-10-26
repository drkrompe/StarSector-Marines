package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.squad.HullWelfare;
import com.dillon.starsectormarines.battle.world.gen.ship.FacilityDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.marine.RecruitIntake;
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
        // Fabrication is the workshop chain's; the vehicle bay waits on deployable vehicles.
        for (RefitTrack track : RefitTrack.values())
            if (track != RefitTrack.FABRICATION && track != RefitTrack.VEHICLE_BAY)
                assertNotNull(RefitOrder.after(track, track.start()), track.name());
        assertNull(RefitOrder.after(RefitTrack.FABRICATION, RefitBand.DEFICIT));
    }

    @Test void theWholeTreeIsReachableFromTheBatteredStart() {
        var state = ShipCondition.battered().with(RefitTrack.FABRICATION, RefitBand.IMPROVED_II);
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
        assertEquals(RefitTrack.FABRICATION, cheap.missing(RefitOrder.HULL_RESTORED).getKey(), "new plate is cut in the workshop");
        assertNull(cheap.with(RefitTrack.FABRICATION, RefitBand.RESTORED).missing(RefitOrder.HULL_RESTORED));
        for (RefitOrder order : RefitOrder.values())
            if (order.track.group == RefitTrack.Group.COMBAT && order.band == RefitBand.RESTORED)
                assertFalse(order.requires.isEmpty(), order.name());
    }

    @Test void aSealedRoomIsNeutralAndEveryOtherHullReadsAsSealed() {
        for (ShipCondition neutral : new ShipCondition[]{ShipCondition.NONE, ShipCondition.battered()}) {
            assertEquals(1f, neutral.infantryAccuracy());
            assertEquals(1f, neutral.infantryMoveSpeed());
            assertEquals(1f, neutral.infantryMoraleDrain());
            assertEquals(0f, neutral.casualtiesSaved());
            assertEquals(RecruitIntake.NEUTRAL, neutral.recruitIntake());
        }
        assertEquals(HullWelfare.NEUTRAL, ShipCondition.NONE.hullWelfare());
        assertEquals(HullWelfare.mess(0.85f), ShipCondition.battered().hullWelfare());
    }

    @Test void eachRoomClimbsFromOpeningThroughBothImprovements() {
        var start = ShipCondition.battered();
        assertEquals(1f, start.with(RefitTrack.RANGE, RefitBand.RESTORED).infantryAccuracy(), "an open range trains aboard");
        assertEquals(1.03f, start.with(RefitTrack.RANGE, RefitBand.IMPROVED_I).infantryAccuracy());
        assertEquals(1.06f, start.with(RefitTrack.RANGE, RefitBand.IMPROVED_II).infantryAccuracy());
        assertEquals(1.02f, start.with(RefitTrack.GYM, RefitBand.RESTORED).infantryMoveSpeed());
        assertEquals(1.04f, start.with(RefitTrack.GYM, RefitBand.IMPROVED_I).infantryMoveSpeed());
        assertEquals(1.06f, start.with(RefitTrack.GYM, RefitBand.IMPROVED_II).infantryMoveSpeed());
        assertEquals(1f, start.with(RefitTrack.GYM, RefitBand.IMPROVED_I).infantryMoraleDrain());
        assertEquals(0.90f, start.with(RefitTrack.GYM, RefitBand.IMPROVED_II).infantryMoraleDrain());
        assertEquals(0.10f, start.with(RefitTrack.SICK_BAY, RefitBand.RESTORED).casualtiesSaved());
        assertEquals(0.25f, start.with(RefitTrack.SICK_BAY, RefitBand.IMPROVED_I).casualtiesSaved());
        assertEquals(0.40f, start.with(RefitTrack.SICK_BAY, RefitBand.IMPROVED_II).casualtiesSaved());
        var trained = start.with(RefitTrack.MESS, RefitBand.IMPROVED_I)
                .with(RefitTrack.RANGE, RefitBand.IMPROVED_II).with(RefitTrack.GYM, RefitBand.IMPROVED_I);
        assertEquals(new HullWelfare(1.15f, 1.06f, 1.04f, 1f), trained.hullWelfare());
    }

    @Test void theLoungeLiftsTheIntakeTierByTier() {
        var start = ShipCondition.battered();
        RecruitIntake previous = start.recruitIntake();
        for (RefitBand band : new RefitBand[]{RefitBand.RESTORED, RefitBand.IMPROVED_I, RefitBand.IMPROVED_II}) {
            RecruitIntake intake = start.with(RefitTrack.REC_LOUNGE, band).recruitIntake();
            assertEquals(SoldierAptitude.STEADY, intake.aptitude(99), "no Limited hand once the lounge is open");
            assertTrue(intake.exceptionalBelow() >= previous.exceptionalBelow() && intake.giftedBelow() >= previous.giftedBelow());
            assertNotEquals(previous, intake, band.name());
            previous = intake;
        }
    }

    @Test void facilityImprovementsNeedTwoCoresAndTheTopTierThree() {
        for (RefitOrder order : RefitOrder.values()) {
            if (order.track.reserve == null || order.band == RefitBand.RESTORED) continue;
            int cores = order.band == RefitBand.IMPROVED_II ? 3 : 2;
            assertEquals(cores, order.requires.size(), order.name());
            for (RefitTrack need : order.requires.keySet()) assertNotNull(need.core, order.name() + " names " + need);
        }
        for (RefitTrack track : new RefitTrack[]{RefitTrack.RANGE, RefitTrack.GYM, RefitTrack.SICK_BAY, RefitTrack.REC_LOUNGE})
            assertEquals(RefitBand.IMPROVED_II, RefitOrder.ceiling(track), track.name());
        assertEquals(RefitBand.RESTORED, RefitOrder.ceiling(RefitTrack.BRIG), "interrogation waits on contract intel");
    }

    @Test void facilitiesInExistingRoomsPayOnlyPastNeutral() {
        for (ShipCondition neutral : new ShipCondition[]{ShipCondition.NONE, ShipCondition.battered(),
                ShipCondition.battered().with(RefitTrack.WARDROOM, RefitBand.RESTORED)
                        .with(RefitTrack.HYDROPONICS, RefitBand.RESTORED).with(RefitTrack.HOLDS, RefitBand.RESTORED)}) {
            assertEquals(1f, neutral.officerXpMult());
            assertEquals(1f, neutral.suppliesPerMonthMult());
            assertEquals(0, neutral.cargoBonus());
        }
        var start = ShipCondition.battered();
        assertEquals(1.15f, start.with(RefitTrack.WARDROOM, RefitBand.IMPROVED_I).officerXpMult());
        assertEquals(1.30f, start.with(RefitTrack.WARDROOM, RefitBand.IMPROVED_II).officerXpMult());
        assertEquals(0.95f, start.with(RefitTrack.HYDROPONICS, RefitBand.IMPROVED_I).suppliesPerMonthMult());
        assertEquals(0.90f, start.with(RefitTrack.HYDROPONICS, RefitBand.IMPROVED_II).suppliesPerMonthMult());
        assertEquals(20, start.with(RefitTrack.HOLDS, RefitBand.IMPROVED_I).cargoBonus());
        assertEquals(40, start.with(RefitTrack.HOLDS, RefitBand.IMPROVED_II).cargoBonus());
        assertEquals(RefitBand.IMPROVED_II, RefitOrder.ceiling(RefitTrack.BRIEFING));
        assertFalse(ShipCondition.NONE.garrisonIntel(), "another hull has no holo-tank");
        assertFalse(start.with(RefitTrack.BRIEFING, RefitBand.IMPROVED_I).garrisonIntel(), "the plot table shows no garrison");
        assertTrue(start.with(RefitTrack.BRIEFING, RefitBand.IMPROVED_II).garrisonIntel());
    }

    @Test void roomlessFacilitiesNeedTwoCoresAndTheirTopTierThree() {
        var tracks = EnumSet.of(RefitTrack.HYDROPONICS, RefitTrack.WARDROOM, RefitTrack.BRIEFING, RefitTrack.HOLDS);
        for (RefitOrder order : RefitOrder.values()) {
            if (!tracks.contains(order.track)) continue;
            assertEquals(RefitTrack.Group.FACILITY, order.track.group);
            assertNull(order.track.reserve, "fitted into a room the deck already has");
            // The wardroom and briefing room are ends of the mess, so their opening needs the mess in service too.
            boolean messEnd = order.band == RefitBand.RESTORED
                    && (order.track == RefitTrack.WARDROOM || order.track == RefitTrack.BRIEFING);
            assertEquals(messEnd, order.requires.get(RefitTrack.MESS) == RefitBand.RESTORED, order.name());
            long cores = order.requires.keySet().stream().filter(t -> t.core != null).count();
            assertEquals(order.requires.size() - (messEnd ? 1 : 0), cores, order.name());
            assertTrue(cores >= (order.band == RefitBand.IMPROVED_II ? 3 : 2), order.name());
        }
    }

    @Test void theDeckIsDressedForTheFacilityTiersBought() {
        assertEquals(FacilityDressing.NONE, ShipCondition.NONE.facilityDressing(), "another hull carries none");
        assertEquals(FacilityDressing.NONE, ShipCondition.battered().facilityDressing());
        var built = ShipCondition.battered().with(RefitTrack.HYDROPONICS, RefitBand.IMPROVED_II)
                .with(RefitTrack.WARDROOM, RefitBand.RESTORED).with(RefitTrack.BRIEFING, RefitBand.RESTORED)
                .with(RefitTrack.HOLDS, RefitBand.IMPROVED_I);
        assertEquals(new FacilityDressing(3, 1, 1, 2), built.facilityDressing());
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
