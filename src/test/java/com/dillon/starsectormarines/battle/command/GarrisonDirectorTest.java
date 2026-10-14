package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.GarrisonDirector.Free;
import com.dillon.starsectormarines.battle.command.GarrisonDirector.Garrison;
import com.dillon.starsectormarines.battle.command.GarrisonDirector.Man;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hysteresis state machine, candidate selection and cost shape of {@link GarrisonDirector}, on hand-built trenches
 * and a hand-set pressure field: no world, no battle.
 *
 * <p>Layout: a 160x40 field, 8-cell blocks (20 x 5 of them). Garrisonable 0 is a 16-cell trench at x 10..25 (blocks
 * 1..3 of row 1), garrisonable 1 at x 70..85 (blocks 8..10), garrisonable 2 at x 130..145 (blocks 16..18).
 */
class GarrisonDirectorTest {

    private static final int W = 160;
    private static final int H = 40;
    private static final int BLOCK = 8;
    private static final int BW = 20;

    private final List<Doodad> props = new ArrayList<>();
    private GarrisonableRegistry registry;
    private final Map<Integer, Float> field = new HashMap<>();
    private GarrisonDirector director;
    private TickInnerProfile profile;
    private TickInnerProfile previousProfile;

    private void trench(int fromX, int toX, int y) {
        for (int x = fromX; x <= toX; x++) {
            DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                    1, 1, null, 0, null, FightingPositionRole.TRENCH);
            props.add(new Doodad(x, y, def));
        }
    }

    @BeforeEach
    void setUp() {
        trench(10, 25, 12);
        trench(70, 85, 12);
        trench(130, 145, 12);
        registry = GarrisonableRegistry.build(FightingPositionIndex.build(W, H, props, (x, y) -> true),
                W, H, BLOCK, new int[0]);
        director = new GarrisonDirector();
        field.clear();
        previousProfile = TickInnerProfile.currentIfBound();
        profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
    }

    @AfterEach
    void tearDown() {
        TickInnerProfile.setCurrent(previousProfile);
    }

    private void pressure(int garrisonable, float value) {
        for (int block : registry.coveredBlocks(garrisonable)) field.put(block, value);
    }

    private GarrisonDirector.Pressure pressureField() {
        return (bx, by) -> field.getOrDefault(by * BW + bx, 0f);
    }

    /** A believed contact in a place's first covered block. */
    private int[] contactAt(int... garrisonables) {
        int[] cells = new int[garrisonables.length * 2];
        for (int i = 0; i < garrisonables.length; i++) {
            cells[2 * i] = registry.centreX(garrisonables[i]);
            cells[2 * i + 1] = registry.centreY(garrisonables[i]);
        }
        return cells;
    }

    private List<Integer> evaluate(List<Garrison> held, int... contactsAt) {
        return director.evaluate(registry, pressureField(), contactAt(contactsAt), contactsAt.length, held);
    }

    private static Eligible always() {
        return new Eligible(true);
    }

    private record Eligible(boolean value) implements GarrisonDirector.Eligibility {
        @Override public boolean test(int squadId, int garrisonable) {
            return value;
        }
    }

    private static List<Free> patrols(int count) {
        List<Free> free = new ArrayList<>();
        for (int i = 0; i < count; i++) free.add(new Free(100 + i, 60 + 5 * i, 12));
        return free;
    }

    @Test
    void aQuietMapSendsNobodyAndPricesNothing() {
        List<Integer> release = evaluate(List.of());
        List<Man> sent = director.assign(patrols(5), always());

        assertTrue(release.isEmpty());
        assertTrue(sent.isEmpty());
    }

    @Test
    void aContactBelowTheStartPressureSendsNobody() {
        pressure(0, GarrisonDirector.START_PRESSURE - 0.1f);
        evaluate(List.of(), 0);

        assertTrue(director.assign(patrols(5), always()).isEmpty());
    }

    @Test
    void aPlaceAtTheStartPressureGetsTheNearestEligiblePatrol() {
        pressure(1, GarrisonDirector.START_PRESSURE);
        evaluate(List.of(), 1);
        // garrisonable 1 centres on x = 77; patrols stand at x = 60, 65, 70, 75 ...
        List<Man> sent = director.assign(patrols(6), always());

        assertEquals(1, sent.size());
        assertEquals(new Man(103, 1), sent.get(0), "the patrol at x = 75 is nearest x = 77");
    }

    @Test
    void anIneligiblePatrolIsPassedOverForTheNextNearest() {
        pressure(1, GarrisonDirector.START_PRESSURE);
        evaluate(List.of(), 1);
        List<Man> sent = director.assign(patrols(6), (squad, place) -> squad != 103);

        assertEquals(new Man(104, 1), sent.get(0), "103 is refused; 104 at x = 80 is the next nearest");
    }

    @Test
    void picketsComeBeforeMassingAndTheMostPressedIsServedFirst() {
        pressure(0, 4f);
        pressure(1, 9f);
        pressure(2, 5f);
        evaluate(List.of(), 0, 1, 2);
        List<Man> sent = director.assign(patrols(8), always());

        assertEquals(GarrisonDirector.MAX_NEW_PER_PULSE, sent.size());
        assertEquals(1, sent.get(0).garrisonable(), "pressure 9 first");
        assertEquals(2, sent.get(1).garrisonable(), "then 5, before the second squad pressure 9 would otherwise take");
    }

    @Test
    void noPlaceGetsASecondSquadWhileAnotherHotPlaceWaitsOnTheCap() {
        pressure(0, 20f);
        pressure(1, 4f);
        pressure(2, 4f);
        evaluate(List.of(), 0, 1, 2);
        List<Man> sent = director.assign(patrols(8), always());

        assertEquals(2, sent.size());
        assertEquals(List.of(0, 1), sent.stream().map(Man::garrisonable).toList(),
                "0 gets one, 1 gets one; 2 waits for next pulse and 0 is not given a second");
    }

    @Test
    void aSecondSquadGoesToAPlaceThatIsHotEnoughAndLargeEnoughOnceEveryPlaceHasOne() {
        pressure(0, GarrisonDirector.SECOND_SQUAD_PRESSURE);
        // garrisonable 0 is 16 cells: too small to hold two, so it never asks for a second
        evaluate(List.of(), 0);
        assertEquals(1, director.assign(patrols(8), always()).size());

        // a 30-cell run does ask
        props.clear();
        trench(10, 39, 12);
        registry = GarrisonableRegistry.build(FightingPositionIndex.build(W, H, props, (x, y) -> true),
                W, H, BLOCK, new int[0]);
        director = new GarrisonDirector();
        pressure(0, GarrisonDirector.SECOND_SQUAD_PRESSURE);
        evaluate(List.of(), 0);
        List<Man> sent = director.assign(patrols(8), always());
        assertEquals(2, sent.size());
        assertEquals(List.of(0, 0), sent.stream().map(Man::garrisonable).toList());
    }

    @Test
    void theReserveIsNeverEmptied() {
        pressure(0, 5f);
        pressure(1, 5f);
        evaluate(List.of(), 0, 1);

        List<Man> sent = director.assign(patrols(2), always());

        assertEquals(2 - GarrisonDirector.RESERVE_FLOOR, sent.size(), "two free patrols: one goes, one stays");
        assertTrue(director.assign(patrols(1), always()).isEmpty(), "and a lone free patrol is the reserve");
    }

    @Test
    void anOrderedSquadCountsAsHeldSoThePlaceIsNotMannedTwice() {
        pressure(1, 5f);
        evaluate(List.of(), 1);
        Man man = director.assign(patrols(6), always()).get(0);

        // next pulse: the ledger has not shown the order yet; the commander reports the squad as held
        List<Garrison> held = List.of(new Garrison(man.squadId(), 1, true));
        evaluate(held, 1);
        assertTrue(director.assign(patrols(6), always()).isEmpty(), "the place already has its picket");
    }

    @Test
    void aHeldPlaceReleasesOnlyAfterTheDwellBelowTheReleaseLevel() {
        pressure(1, GarrisonDirector.RELEASE_PRESSURE - 0.1f);
        List<Garrison> held = List.of(new Garrison(7, 1, true), new Garrison(8, 1, true));

        for (int pulse = 1; pulse < GarrisonDirector.DWELL_PULSES; pulse++) {
            assertTrue(evaluate(held).isEmpty(), "pulse " + pulse + " of the dwell holds");
        }
        assertEquals(List.of(7, 8), evaluate(held), "the dwell ends: both given back, in id order");
    }

    @Test
    void pressureBetweenTheLevelsHoldsTheSquadAndResetsTheDwell() {
        List<Garrison> held = List.of(new Garrison(7, 1, true));
        pressure(1, 0f);
        for (int pulse = 1; pulse < GarrisonDirector.DWELL_PULSES; pulse++) evaluate(held);

        // a pulse at a level between release and start: held, and the count starts over
        pressure(1, (GarrisonDirector.RELEASE_PRESSURE + GarrisonDirector.START_PRESSURE) / 2f);
        assertTrue(evaluate(held).isEmpty());
        pressure(1, 0f);
        for (int pulse = 1; pulse < GarrisonDirector.DWELL_PULSES; pulse++) {
            assertTrue(evaluate(held).isEmpty(), "the dwell restarted, so pulse " + pulse + " still holds");
        }
        assertEquals(List.of(7), evaluate(held));
    }

    @Test
    void aSquadInAFightIsNotReleasedAndKeepsItsPlaceCounted() {
        pressure(1, 0f);
        List<Garrison> held = List.of(new Garrison(7, 1, false), new Garrison(8, 1, true));
        for (int pulse = 1; pulse < GarrisonDirector.DWELL_PULSES; pulse++) evaluate(held);

        assertEquals(List.of(8), evaluate(held), "only the squad free to move is given back");
        // place 1 still has squad 7, so a re-heated place is not given a second picket from nowhere
        pressure(1, 5f);
        evaluate(List.of(new Garrison(7, 1, false)), 1);
        assertTrue(director.assign(patrols(6), always()).isEmpty());
    }

    @Test
    void aPlaceMustStayHotToHoldItsSquadsUntilItCools() {
        pressure(1, 5f);
        List<Garrison> held = List.of(new Garrison(7, 1, true));
        for (int pulse = 0; pulse < 10; pulse++) {
            assertTrue(evaluate(held, 1).isEmpty());
        }
    }

    @Test
    void workFollowsTheFightNotTheNumberOfPlaces() {
        // Three places, one contact: only that place is priced, and only its three blocks read.
        pressure(2, 5f);
        evaluate(List.of(), 2);

        assertEquals(1, director.assign(patrols(6), always()).size());
        assertEquals(1, lastConsidered());
        assertEquals(registry.coveredBlocks(2).length, lastBlockReads());
    }

    @Test
    void manyContactsInOneBlockAreOneVisit() {
        pressure(0, 5f);
        int[] cells = new int[40];
        for (int i = 0; i < 20; i++) {
            cells[2 * i] = registry.centreX(0);
            cells[2 * i + 1] = registry.centreY(0);
        }
        director.evaluate(registry, pressureField(), cells, 20, List.of());
        director.assign(patrols(4), always());

        assertEquals(1, lastConsidered(), "twenty contacts, one block, one place priced once");
    }

    @Test
    void aRebuiltRegistryDropsTheCoolingCountsOfTheOldNumbering() {
        List<Garrison> held = List.of(new Garrison(7, 1, true));
        pressure(1, 0f);
        for (int pulse = 1; pulse < GarrisonDirector.DWELL_PULSES; pulse++) evaluate(held);

        registry = GarrisonableRegistry.build(FightingPositionIndex.build(W, H, props, (x, y) -> true),
                W, H, BLOCK, new int[0]);
        assertTrue(evaluate(held).isEmpty(), "a new index instance starts the count over; ids may have moved");
    }

    @Test
    void anOrderNeverAdoptedExpiresAndAnAdoptedOneStopsBeingTracked() {
        pressure(1, 5f);
        evaluate(List.of(), 1);
        Man man = director.assign(patrols(6), always()).get(0);
        assertEquals(1, director.pendingCount());
        int[] cell = director.pendingCell(man.squadId());
        assertEquals(registry.centreX(1), cell[0]);

        director.adopted(man.squadId());
        assertEquals(0, director.pendingCount());

        // an order that is never adopted lapses after the pending window
        director.assign(patrols(0), always());
        evaluate(List.of(), 1);
        Man second = director.assign(patrols(6), always()).stream().findFirst().orElseThrow();
        for (int pulse = 0; pulse < GarrisonDirector.PENDING_PULSES; pulse++) evaluate(List.of());
        assertNull(director.pendingCell(second.squadId()));
    }

    @Test
    void aPatrolBeyondTheArrivalBoundIsNotSentAndTheRefusalIsCounted() {
        pressure(1, 5f);
        evaluate(List.of(), 1);
        // 120 cells off: well over the bound
        List<Free> far = List.of(new Free(200, registry.centreX(1) - 120, 12), new Free(201, registry.centreX(1) - 125, 12));
        assertTrue(director.assign(far, always()).isEmpty());
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.GARRISON_ETA_REFUSED));
    }

    @Test
    void hotPlacesAndWhetherTheyAreMannedAreReadableForFlow() {
        pressure(1, 5f);
        pressure(2, 2f);
        evaluate(List.of(), 1, 2);
        assertArrayEquals(new int[]{1}, director.hotPlaces(), "only the place at the start pressure is hot");
        assertTrue(!director.isManned(1));
        director.assign(patrols(6), always());
        assertTrue(director.isManned(1), "an ordered squad counts as manning");
    }

    @Test
    void aRefusalOnlyBecauseOfTheReserveFloorIsCountedApartFromAnArrivalRefusal() {
        pressure(1, 5f);
        evaluate(List.of(), 1);
        // One roving patrol, close: taking it would empty the reserve.
        director.assign(List.of(new Free(300, registry.centreX(1) - 5, 12)), always());
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.GARRISON_ETA_REFUSED));
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.GARRISON_FLOOR_REFUSED));

        evaluate(List.of(), 1);
        director.assign(List.of(new Free(301, registry.centreX(1) - 120, 12), new Free(302, registry.centreX(1) - 125, 12)),
                always());
        assertEquals(2, profile.countOf(TickInnerProfile.Bucket.GARRISON_ETA_REFUSED));
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.GARRISON_FLOOR_REFUSED), "too far is not the floor");
    }

    @Test
    void theArrivalEstimateIsOctileOverMarchingSpeedAndMonotone() {
        int straight = GarrisonDirector.etaTicks(0, 0, 40, 0);
        int diagonal = GarrisonDirector.etaTicks(0, 0, 40, 40);
        assertTrue(straight > 0 && diagonal > straight);
        assertTrue(GarrisonDirector.etaTicks(0, 0, 40, 0) <= GarrisonDirector.MAX_ETA_TICKS);
        assertTrue(GarrisonDirector.etaTicks(0, 0, 100, 0) > GarrisonDirector.MAX_ETA_TICKS);
    }

    @Test
    void aStagedPatrolIsSentWithoutSpendingTheRovingReserve() {
        pressure(1, 5f);
        evaluate(List.of(), 1);
        List<Free> pool = List.of(new Free(300, registry.centreX(1) - 5, 12, true), new Free(301, 10, 12));

        List<Man> sent = director.assign(pool, always());

        assertEquals(List.of(new Man(300, 1)), sent, "the staged patrol goes; the lone roving patrol stays as the reserve");
    }

    @Test
    void aSquadStillClosingIsHeldThroughALullThatWouldReleaseAnArrivedOne() {
        pressure(1, 0f);
        List<Garrison> held = List.of(new Garrison(7, 1, true, false, 8), new Garrison(8, 1, true, true, 8));
        List<Integer> released = List.of();
        for (int pulse = 0; pulse < GarrisonDirector.DWELL_PULSES; pulse++) released = evaluate(held);
        assertEquals(List.of(8), released, "the arrived squad goes after the dwell; the one still marching stays");
        for (int pulse = GarrisonDirector.DWELL_PULSES; pulse < GarrisonDirector.EN_ROUTE_DWELL_PULSES; pulse++) {
            released = evaluate(List.of(held.get(0)));
        }
        assertEquals(List.of(7), released, "and it is given back once the longer en-route dwell has passed");
    }

    @Test
    void aSquadThatNeverArrivesIsGivenBackEvenUnderPressure() {
        pressure(1, 5f);
        List<Garrison> held = List.of(new Garrison(7, 1, true, false, 8));
        List<Integer> released = List.of();
        for (int pulse = 0; pulse <= GarrisonDirector.MAX_EN_ROUTE_PULSES; pulse++) released = evaluate(held, 1);
        assertEquals(List.of(7), released);
    }

    @Test
    void aSquadMarchingInIsNeverReleasedWhileInAFight() {
        pressure(1, 0f);
        List<Garrison> held = List.of(new Garrison(7, 1, false, false, 8));
        for (int pulse = 0; pulse < GarrisonDirector.EN_ROUTE_DWELL_PULSES + 2; pulse++) {
            assertTrue(evaluate(held).isEmpty());
        }
    }

    @Test
    void anEnemyBelievedInsideTheRunMakesAnArrivedSquadFallBackAndLocksThePlace() {
        pressure(1, 9f);
        List<Garrison> held = List.of(new Garrison(7, 1, false, true, 8));
        evaluate(held, 1);

        assertEquals(List.of(7), director.fallingBack(), "a fight does not stop a fall back");
        assertTrue(director.lockedOut(registry.centreX(1), registry.centreY(1)));
        assertTrue(director.assign(patrols(6), always()).isEmpty(), "and nobody is sent into the breached run");
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.GARRISON_FALL_BACK));
    }

    @Test
    void aGarrisonBelowUsefulStrengthFallsBackWithoutALockout() {
        pressure(1, 9f);
        List<Garrison> held = List.of(new Garrison(7, 1, true, true, GarrisonDirector.MIN_USEFUL_MEMBERS - 1));
        evaluate(held);

        assertEquals(List.of(7), director.fallingBack());
        assertTrue(!director.lockedOut(registry.centreX(1), registry.centreY(1)));
    }

    @Test
    void aBreachLockoutLapses() {
        pressure(1, 9f);
        evaluate(List.of(new Garrison(7, 1, false, true, 8)), 1);
        for (int pulse = 0; pulse < GarrisonDirector.BREACH_LOCKOUT_PULSES; pulse++) evaluate(List.of());
        assertTrue(!director.lockedOut(registry.centreX(1), registry.centreY(1)));
    }

    @Test
    void warmPlacesAreThoseAboveTheFlowPressureEvenWhenBelowTheStart() {
        pressure(0, GarrisonDirector.FLOW_PRESSURE);
        pressure(1, GarrisonDirector.START_PRESSURE);
        evaluate(List.of(), 0, 1, 2);

        assertEquals(List.of(0, 1), Arrays.stream(director.warmPlaces()).boxed().sorted().toList());
    }

    @Test
    void aSquadReservedToThePlacesAreaIsDrawnBeforeANearerOne() {
        pressure(0, 9f);
        evaluate(List.of(), 0);
        // Squad 100 stands next to the trench; squad 101 is farther but reserved to its area.
        List<Free> free = List.of(new Free(100, 14, 12), new Free(101, 40, 12));

        List<Man> sent = director.assign(free, always(), (squad, place) -> squad == 101);

        assertEquals(List.of(new Man(101, 0)), sent);
    }

    @Test
    void aReservedSquadBeyondTheMarchBoundIsPassedOverForANearerOne() {
        pressure(2, 9f);
        evaluate(List.of(), 2);
        // Reserved squad 101 is past the arrival bound of the far trench; squad 100 is close.
        int cx = registry.centreX(2);
        List<Free> free = List.of(new Free(100, cx - 4, 12), new Free(101, 5, 12));

        List<Man> sent = director.assign(free, always(), (squad, place) -> squad == 101);

        assertEquals(List.of(new Man(100, 2)), sent);
    }

    @Test
    void aReservationDoesNotLetARovingPatrolBelowTheFloor() {
        pressure(0, 9f);
        evaluate(List.of(), 0);

        // The only free patrol is roving and reserved to the area: the reserve floor still keeps it.
        List<Man> sent = director.assign(List.of(new Free(100, 14, 12)), always(), (squad, place) -> true);

        assertTrue(sent.isEmpty());
    }

    @Test
    void noPreferenceIsTheOriginalNearestFirstOrder() {
        pressure(0, 9f);
        evaluate(List.of(), 0);
        List<Free> free = List.of(new Free(100, 14, 12), new Free(101, 40, 12), new Free(102, 60, 12));

        assertEquals(List.of(new Man(100, 0)), director.assign(free, always(), null));
    }

    private int lastConsidered() {
        return profile.countOf(TickInnerProfile.Bucket.GARRISON_CANDIDATE);
    }

    private int lastBlockReads() {
        return profile.countOf(TickInnerProfile.Bucket.GARRISON_BLOCK_READ);
    }
}
