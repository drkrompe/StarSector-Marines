package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.GarrisonDirector.Free;
import com.dillon.starsectormarines.battle.command.GarrisonDirector.Garrison;
import com.dillon.starsectormarines.battle.command.GarrisonDirector.Man;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.Faction;
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
import static org.junit.jupiter.api.Assertions.assertSame;
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

        // a 32-cell stretch (two rows of trench, one stretch long) does ask
        props.clear();
        trench(10, 25, 12);
        trench(10, 25, 13);
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

        // a pulse at a level between release and start, with a believed contact near to make it: held, and the count starts over
        pressure(1, (GarrisonDirector.RELEASE_PRESSURE + GarrisonDirector.START_PRESSURE) / 2f);
        assertTrue(evaluate(held, 1).isEmpty());
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
        // Five left of twelve is under half.
        List<Garrison> held = List.of(new Garrison(7, 1, true, true, 5,
                GarrisonDirector.NO_POSITION, GarrisonDirector.NO_POSITION, 12));
        evaluate(held);

        assertEquals(List.of(7), director.fallingBack());
        assertTrue(!director.lockedOut(registry.centreX(1), registry.centreY(1)));
    }

    @Test
    void anUnderstrengthSectionHoldsOnAtHalfItsStartingStrength() {
        pressure(1, 9f);
        List<Garrison> held = List.of(new Garrison(7, 1, true, true, 2,
                GarrisonDirector.NO_POSITION, GarrisonDirector.NO_POSITION, 4));
        evaluate(held);

        assertEquals(List.of(), director.fallingBack(), "two of a four-man section is still a garrison");
    }

    @Test
    void theUsefulFloorIsHalfTheStartingStrengthAndNeverBelowTwo() {
        assertEquals(6, GarrisonDirector.usefulFloor(12, 12));
        assertEquals(2, GarrisonDirector.usefulFloor(4, 3));
        assertEquals(2, GarrisonDirector.usefulFloor(3, 3));
        assertEquals(2, GarrisonDirector.usefulFloor(1, 1), "one marine is never a garrison");
        assertEquals(4, GarrisonDirector.usefulFloor(0, 8), "an unknown start reads as present strength");
        assertEquals(6, GarrisonDirector.usefulFloor(4, 12), "a squad grown past its start is judged by its size");
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

    @Test
    void aPatrolBelowUsefulStrengthIsNeverSent() {
        pressure(1, 5f);
        evaluate(List.of(), 1);
        // A lone survivor, and five of twelve, are too weak; two of a four-man section are not.
        List<Free> pool = List.of(new Free(100, registry.centreX(1) - 5, 12, false, 1, 4),
                new Free(101, registry.centreX(1) - 6, 12, true, 5, 12),
                new Free(102, registry.centreX(1) - 8, 12, true, 2, 4));

        assertEquals(List.of(new Man(102, 1)), director.assign(pool, always()),
                "the nearer squads are too weak to be a garrison, so the one that is strong enough goes");
    }

    @Test
    void aContactAtTheFarEndOfTheRunDoesNotBreachASquadHoldingItsMiddle() {
        pressure(1, 9f);
        int far = registry.centreX(1) + GarrisonDirector.BREACH_RANGE_CELLS + 1;
        List<Garrison> held = List.of(new Garrison(7, 1, true, true, 8, far, 12));
        evaluate(held, 1);

        assertTrue(director.fallingBack().isEmpty());
        assertTrue(!director.lockedOut(registry.centreX(1), registry.centreY(1)), "and the place is not locked out");
    }

    @Test
    void aContactInTheRunNearTheSquadBreachesIt() {
        pressure(1, 9f);
        int near = registry.centreX(1) + GarrisonDirector.BREACH_RANGE_CELLS;
        List<Garrison> held = List.of(new Garrison(7, 1, false, true, 8, near, 12));
        evaluate(held, 1);

        assertEquals(List.of(7), director.fallingBack());
        assertTrue(director.lockedOut(registry.centreX(1), registry.centreY(1)));
    }

    @Test
    void aContactBesideTheRunButNotInItIsNoBreach() {
        pressure(1, 9f);
        int x = registry.centreX(1);
        int y = 13;
        while (registry.idAt(x, y) >= 0) y++;
        assertTrue(registry.idAt(x, y - 1) >= 0, "the cell is adjacent to the run");
        List<Garrison> held = List.of(new Garrison(7, 1, false, true, 8, x, 12));
        director.evaluate(registry, pressureField(), new int[]{x, y}, 1, held);

        assertTrue(director.fallingBack().isEmpty());
        assertTrue(!director.lockedOut(registry.centreX(1), registry.centreY(1)));
    }

    @Test
    void aSquadStillClosingIsGivenBackWhenAnEnemyIsInTheRunHoweverFarItIsFromThem() {
        pressure(1, 9f);
        List<Garrison> held = List.of(new Garrison(7, 1, true, false, 8, 5, 12));
        List<Integer> released = evaluate(held, 1);

        assertEquals(List.of(7), released, "a march into a breached run is abandoned");
        assertTrue(director.lockedOut(registry.centreX(1), registry.centreY(1)));
    }

    @Test
    void aStretchNobodyIsNearCoolsThoughTheFieldStillPressesIt() {
        // The field reads 9 across place 1, but the only believed contact is at place 0, beyond reach of it.
        pressure(1, 9f);
        List<Garrison> held = List.of(new Garrison(7, 1, true));
        for (int pulse = 1; pulse < GarrisonDirector.DWELL_PULSES; pulse++) {
            assertTrue(evaluate(held, 0).isEmpty(), "pulse " + pulse + " of the dwell holds");
        }
        assertEquals(List.of(7), evaluate(held, 0), "nobody is believed near it: given back after the dwell");

        GarrisonableRegistry whole = GarrisonableRegistry.build(FightingPositionIndex.build(W, H, props, (x, y) -> true),
                W, H, BLOCK, new int[0], false);
        GarrisonDirector old = new GarrisonDirector();
        for (int pulse = 0; pulse < 2 * GarrisonDirector.DWELL_PULSES; pulse++) {
            assertTrue(old.evaluate(whole, pressureField(), contactAt(0), 1, held).isEmpty(),
                    "over whole runs the field alone holds it, as it always did");
        }
    }

    @Test
    void aFortsAlarmFloorHoldsItsStretchesOnlyWhileBeliefHasWokenNoneOfThem() {
        registry = GarrisonableRegistry.build(FightingPositionIndex.build(W, H, props, (x, y) -> true),
                W, H, BLOCK, new int[]{5, 50, 12});
        assertEquals(5, registry.fortId(0));
        assertEquals(5, registry.fortId(1));
        pressure(1, 5f);
        GarrisonDirector.Pressure withFloor = new GarrisonDirector.Pressure() {
            @Override public float hostileAt(int bx, int by) { return field.getOrDefault(by * BW + bx, 0f); }
            @Override public float floorAt(int bx, int by) { return GarrisonDirector.START_PRESSURE; }
        };
        List<Garrison> held = List.of(new Garrison(7, 0, true), new Garrison(8, 1, true));
        for (int pulse = 0; pulse < 2 * GarrisonDirector.DWELL_PULSES; pulse++) {
            assertTrue(director.evaluate(registry, withFloor, new int[0], 0, held).isEmpty(),
                    "an unseen attack: the alarm holds every stretch of the fort");
        }
        List<Integer> released = List.of();
        for (int pulse = 0; pulse < GarrisonDirector.DWELL_PULSES; pulse++) {
            released = director.evaluate(registry, withFloor, new int[]{registry.centreX(1), 14}, 1, held);
        }
        assertEquals(List.of(7), released, "seen at stretch 1, so stretch 0 is cool; stretch 1 is held by belief");
    }

    /**
     * A 64-cell trench along y 12, cut into four 16-cell stretches (x 10..25, 26..41, 42..57, 58..73); the squad stands
     * on the first, and believed pressure lies on blocks 4 and 5 (x 32..47).
     */
    private void longTrench() {
        props.clear();
        trench(10, 73, 12);
        registry = GarrisonableRegistry.build(FightingPositionIndex.build(W, H, props, (x, y) -> true),
                W, H, BLOCK, new int[0]);
        field.clear();
        // The attack faces the next stretch along (x 26..41, blocks 3 to 5), not the squad's own (blocks 1 to 3).
        field.put(BW + 4, 6f);
        field.put(BW + 5, 6f);
    }

    @Test
    void aGarrisonShiftsOntoTheNeighbouringStretchTheAttackHasMovedTo() {
        longTrench();
        int from = registry.idAt(15, 12);
        int to = registry.idAt(34, 12);
        assertEquals(4, registry.count());
        assertArrayEquals(new int[]{to}, Arrays.stream(registry.neighbours(from)).filter(n -> n == to).toArray());
        List<Garrison> held = List.of(new Garrison(7, from, true, true, 8, 15, 12));

        List<Integer> released = director.evaluate(registry, pressureField(), new int[]{52, 12}, 1, held);

        assertTrue(released.isEmpty());
        assertTrue(director.fallingBack().isEmpty());
        assertEquals(List.of(new GarrisonDirector.Shift(7, from, to)), director.shifting());
        assertTrue(director.isManned(to), "it counts as manning the new stretch at once");
        assertTrue(director.assign(patrols(6), always()).stream().noneMatch(m -> m.garrisonable() == to),
                "so nobody else is sent to it");
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.GARRISON_SHIFT));
    }

    @Test
    void noShiftWhileInAFightOntoAMannedStretchOrOverWholeRuns() {
        longTrench();
        int from = registry.idAt(15, 12);
        int to = registry.idAt(34, 12);
        director.evaluate(registry, pressureField(), new int[]{52, 12}, 1,
                List.of(new Garrison(7, from, false, true, 8, 15, 12)));
        assertTrue(director.shifting().isEmpty(), "a squad in a fight stays where it fights");

        director = new GarrisonDirector();
        director.evaluate(registry, pressureField(), new int[]{52, 12}, 1,
                List.of(new Garrison(7, from, true, true, 8, 15, 12), new Garrison(8, to, true, true, 8, 34, 12)));
        assertTrue(director.shifting().isEmpty(), "the neighbour already has its garrison");

        GarrisonableRegistry whole = GarrisonableRegistry.build(registry.source(), W, H, BLOCK, new int[0], false);
        director = new GarrisonDirector();
        director.evaluate(whole, pressureField(), new int[]{52, 12}, 1,
                List.of(new Garrison(7, 0, true, true, 8, 15, 12)));
        assertTrue(director.shifting().isEmpty(), "a whole run has no neighbour to shift to");
    }

    @Test
    void aGarrisonThatHasJustShiftedDoesNotShiftAgainAtOnce() {
        longTrench();
        int from = registry.idAt(15, 12);
        int to = registry.idAt(34, 12);
        int beyond = registry.idAt(50, 12);
        director.evaluate(registry, pressureField(), new int[]{52, 12}, 1,
                List.of(new Garrison(7, from, true, true, 8, 15, 12)));
        // The attack moves on at once: arrived on the new stretch, the squad still waits out its cooldown.
        field.clear();
        field.put(BW + 6, 6f);
        field.put(BW + 7, 6f);
        director.evaluate(registry, pressureField(), new int[]{66, 12}, 1,
                List.of(new Garrison(7, to, true, true, 8, 34, 12)));
        assertTrue(director.shifting().isEmpty());
        for (int pulse = 2; pulse < GarrisonDirector.SHIFT_COOLDOWN_PULSES; pulse++) {
            director.evaluate(registry, pressureField(), new int[]{66, 12}, 1,
                    List.of(new Garrison(7, to, false, true, 8, 34, 12)));
        }
        director.evaluate(registry, pressureField(), new int[]{66, 12}, 1,
                List.of(new Garrison(7, to, true, true, 8, 34, 12)));
        assertEquals(List.of(new GarrisonDirector.Shift(7, to, beyond)), director.shifting());
    }

    @Test
    void anEnemyInTheNextStretchOfTheRunIsABreach() {
        longTrench();
        int from = registry.idAt(15, 12);
        assertTrue(registry.idAt(28, 12) != from);
        director.evaluate(registry, pressureField(), new int[]{28, 12}, 1,
                List.of(new Garrison(7, from, true, true, 8, 20, 12)));
        assertEquals(List.of(7), director.fallingBack(), "eight cells along the same trench, past the stretch's end");
    }

    private int lastBlockReads() {
        return profile.countOf(TickInnerProfile.Bucket.GARRISON_BLOCK_READ);
    }

    /** The setup's three trenches and a barracks between the first two, whose envelope is place 3. */
    private void withBarracks() {
        TacticalNode barracks = new TacticalNode(TacticalNode.Kind.BARRACKS, 47, 24, 40, 20, 55, 30,
                Faction.DEFENDER, 60, 0);
        registry = GarrisonableRegistry.build(FightingPositionIndex.build(W, H, props, (x, y) -> true),
                W, H, BLOCK, new int[0], List.of(new GarrisonableRegistry.Envelope(barracks, new int[]{47, 20, 48, 20})));
    }

    @Test
    void aPressedBuildingIsMannedWithAHoldOfItsEnvelope() {
        withBarracks();
        assertTrue(registry.isEnvelope(3));
        pressure(3, GarrisonDirector.START_PRESSURE + 1f);
        evaluate(List.of(), 3);
        List<Man> sent = director.assign(patrols(3), always());

        assertEquals(1, sent.size());
        assertEquals(3, sent.get(0).garrisonable());
        ObjectiveAssignment order = GarrisonDirector.orderFor(sent.get(0).squadId(), 3, registry);
        assertTrue(order.isEnvelope() && order.mansPlace() && !order.isGarrison());
        assertSame(registry.building(3), order.targetNode());
        assertEquals(3, registry.idAt(order.targetCellX(), order.targetCellY()), "the order finds its place again");
    }

    @Test
    void aBuildingItsOwnGarrisonHoldsIsNotSentAPicket() {
        withBarracks();
        pressure(3, GarrisonDirector.START_PRESSURE + 1f);
        evaluate(List.of(), 3);
        director.heldByOwnGarrison(List.of(3));

        assertTrue(director.isManned(3));
        assertTrue(director.assign(patrols(3), always()).isEmpty());
    }

    @Test
    void anEnemyInsideTheBuildingBreachesItsEnvelope() {
        withBarracks();
        pressure(3, GarrisonDirector.START_PRESSURE + 1f);
        Garrison holder = new Garrison(7, 3, true, true, 6, 47, 21, 6);
        director.evaluate(registry, pressureField(), new int[]{50, 26}, 1, List.of(holder));

        assertEquals(List.of(7), director.fallingBack());
        assertTrue(director.lockedOut(registry.centreX(3), registry.centreY(3)));
    }
}
