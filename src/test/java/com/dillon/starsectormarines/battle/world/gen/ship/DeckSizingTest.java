package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.fit.BoatBayFitting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a hull owes in rooms, and in particular who its berths belong to.
 *
 * <p>A ship carries two populations: the hands who work her, and the ground
 * force she is carrying. Almost every room they need is shared — one mess, one
 * set of heads, one sick bay — and berths are the exception, because a bunk is
 * an assignment rather than an amenity.
 */
class DeckSizingTest {

    @Test void reservedBayConditionChangesLiftButNotRoomProgramOrDimensions() {
        CompanyShip damaged = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER,
                20, 68, 75, 0.75f, null, null, true, CompanyShip.BoatBayState.DAMAGED);
        CompanyShip restored = damaged.withBoatBay(CompanyShip.BoatBayState.RESTORED);
        assertEquals(damaged.deckPlan().rooms(), restored.deckPlan().rooms());
        assertEquals(damaged.deckPlan().frames(), restored.deckPlan().frames());
        assertEquals(damaged.deckPlan().height(), restored.deckPlan().height());
        assertEquals(1, ShipsBoats.aboard(damaged).size());
        assertEquals(2, ShipsBoats.aboard(restored).size());
    }

    @Test
    void aDefaultSubstantialHullKeepsAllSixBoatsInOneHangar() {
        for (HullClass hull : List.of(HullClass.DESTROYER, HullClass.CRUISER, HullClass.CAPITAL)) {
            List<RoomRecipe> bays = DeckSizing.programFor(hull,
                            HullRole.TROOP_TRANSPORT, 10, 250, 200).stream()
                    .filter(room -> room.purpose() == RoomPurpose.HANGAR).toList();
            assertEquals(1, bays.size());
            RoomRecipe bay = bays.get(0);
            assertEquals(6, BoatBayFitting.boatsIn(bay.shape().width(), bay.shape().height()));
        }
    }

    @Test
    void additionalLiftAddsCompleteSixBoatEstablishments() {
        assertEquals(1, count(transport(10, 340), RoomPurpose.HANGAR));
        assertEquals(2, count(transport(10, 341), RoomPurpose.HANGAR));
        List<RoomRecipe> frigate = DeckSizing.programFor(HullClass.FRIGATE,
                HullRole.WARSHIP, 10, 20, 30);
        RoomRecipe gig = frigate.stream().filter(room -> room.purpose() == RoomPurpose.HANGAR)
                .findFirst().orElseThrow();
        assertEquals(1, BoatBayFitting.boatsIn(gig.shape().width(), gig.shape().height()));
    }

    @Test
    void aNarrowHullStillHasBeamForTheFlankFacingHangar() {
        DeckSizing.DeckPlan plan = DeckSizing.planFor(HullClass.DESTROYER,
                HullRole.TROOP_TRANSPORT, 10, 250, 0, 0.1f);
        assertTrue(plan.height() >= 2 * RoomRecipe.SHUTTLE_BAY.shape().height() + 10,
                "minimum beam only fits a pose whose aperture faces fore or aft");
    }

    private static int count(List<RoomRecipe> program, RoomPurpose purpose) {
        return (int) program.stream().filter(room -> room.purpose() == purpose).count();
    }

    private static List<RoomRecipe> transport(int minCrew, int maxCrew) {
        return DeckSizing.programFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                minCrew, maxCrew, 250);
    }

    @Test
    void aTransportBerthsHerCrewAndHerPassengersSeparately() {
        List<RoomRecipe> program = transport(60, 400);
        assertTrue(count(program, RoomPurpose.CREW_QUARTERS) > 0,
                "the hands who fly the ship have nowhere to sleep");
        assertTrue(count(program, RoomPurpose.BARRACKS) > 0,
                "the ground force has nowhere to sleep");
        assertTrue(count(program, RoomPurpose.BARRACKS)
                        > count(program, RoomPurpose.CREW_QUARTERS),
                "a transport carrying five times her crew berthed them the other way round");
    }

    /**
     * The trade the model claims and, until berths were split, did not make.
     *
     * <p>A hull mod that raises minimum crew leaves maximum crew alone, so the
     * ship spends more of herself running herself and the ground force gets
     * less. Every figure derived from lift moved when that happened — boats,
     * armories, ranges — except the largest one, because berthing was sized from
     * the whole complement and therefore did not move at all.
     */
    @Test
    void raisingMinimumCrewTakesBerthsFromTheCompanyAndGivesThemToTheShip() {
        List<RoomRecipe> before = transport(60, 400);
        List<RoomRecipe> after = transport(160, 400);

        assertTrue(count(after, RoomPurpose.CREW_QUARTERS)
                        > count(before, RoomPurpose.CREW_QUARTERS),
                "the ship took on a hundred more hands and no more berths for them");
        assertTrue(count(after, RoomPurpose.BARRACKS)
                        < count(before, RoomPurpose.BARRACKS),
                "the company lost a hundred berths' worth of lift and gave up no barracks");
    }

    /**
     * Shared spaces stay shared. A passenger eats and washes, so those rooms
     * scale with everyone aboard and do not move when the split between the two
     * populations does.
     */
    @Test
    void messAndHeadsServeEverybodyAboard() {
        List<RoomRecipe> before = transport(60, 400);
        List<RoomRecipe> after = transport(160, 400);
        assertEquals(count(before, RoomPurpose.MESS_HALL), count(after, RoomPurpose.MESS_HALL),
                "the mess changed size because the crew split moved, which it should not");
        assertEquals(count(before, RoomPurpose.WASHROOM), count(after, RoomPurpose.WASHROOM),
                "the heads changed size because the crew split moved, which they should not");
    }

    /** A ship with no lift is carrying nobody, and needs no barracks at all. */
    @Test
    void aShipCarryingNobodyHasNoBarracks() {
        List<RoomRecipe> program = DeckSizing.programFor(HullClass.CRUISER,
                HullRole.WARSHIP, 400, 400, 200);
        assertEquals(0, count(program, RoomPurpose.BARRACKS),
                "a ship with no lift was given quarters for a ground force she cannot carry");
        assertTrue(count(program, RoomPurpose.CREW_QUARTERS) > 0,
                "her own four hundred hands have nowhere to sleep");
    }
}
