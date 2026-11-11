package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fallen hull's strippers, haulers, stake trains and haul roads. */
class HullWorkersTest {

    private static Wreck wreck() {
        return new Wreck(1L, Wreck.Kind.WALKER, 0, 0f, 0f, DerelictWalkerFrame.LINE, 3, false);
    }

    @Test
    void aStripperCutsTheNearestCuttableSalvageAndWaitsFullForAHauler() {
        Salvage near = new Salvage(1L, Salvage.Kind.STRUCTURE, 3f, 0f, 500f, null);
        Salvage far = new Salvage(2L, Salvage.Kind.STRUCTURE, 20f, 0f, 500f, null);
        StripperWork stripper = new StripperWork();
        StripperWork.Goal goal = stripper.decide(0f, 0f, 0f, 0f, List.of(far, near));
        assertEquals(StripperWork.Kind.GO, goal.kind());
        assertEquals(1L, goal.salvageId());
        assertEquals(StripperWork.Kind.CUT, stripper.decide(2f, 0f, 0f, 0f, List.of(far, near)).kind());
        stripper.cut(1000f, near);
        assertEquals(StripperWork.CAPACITY, stripper.load(), 1e-4f, "it cuts no more than it can hold");
        assertEquals(StripperWork.Kind.FULL, stripper.decide(2f, 0f, 0f, 0f, List.of(far, near)).kind());
        assertEquals(StripperWork.CAPACITY, stripper.handOver(1000f), 1e-4f);
        assertEquals(StripperWork.Kind.STAND_BY, stripper.decide(2f, 0f, 9f, 9f, List.of()).kind());
    }

    @Test
    void aWreckCutToNothingIsNeverRaisedAndAScrappedOneIsDenied() {
        Wreck lying = wreck();
        Salvage salvage = new Salvage(1L, Salvage.Kind.WRECK, 0f, 0f, 10f, lying);
        new StripperWork().cut(100f, salvage);
        assertFalse(lying.raisable());
        assertFalse(salvage.cuttable());
        Wreck scrapped = wreck();
        scrapped.blast(0f, 0f);
        assertFalse(new Salvage(2L, Salvage.Kind.WRECK, 0f, 0f, 10f, scrapped).cuttable());
    }

    @Test
    void aHaulerTakesFromTheFullestStripperAndCarriesHomeWhenFull() {
        HaulerWork hauler = new HaulerWork();
        List<HaulerWork.Holder> holders = List.of(new HaulerWork.Holder(7, 5f, 0f, 20f),
                new HaulerWork.Holder(8, 30f, 0f, 50f));
        List<HaulerWork.Store> stores = List.of(new HaulerWork.Store(ClaimLattice.HULL, 0f, 0f),
                new HaulerWork.Store(4, 40f, 0f));
        HaulerWork.Goal goal = hauler.decide(0f, 0f, 0f, 0f, holders, stores);
        assertEquals(HaulerWork.Kind.GO, goal.kind());
        assertEquals(8, goal.with());
        hauler.takeOn(HaulerWork.CAPACITY);
        HaulerWork.Goal home = hauler.decide(30f, 0f, 0f, 0f, holders, stores);
        assertEquals(4, home.with(), "the nearest store whose chain holds");
        assertEquals(HaulerWork.Kind.UNLOAD, hauler.decide(39f, 0f, 0f, 0f, holders, stores).kind());
        Feedstock store = new Feedstock();
        hauler.unload(store);
        assertEquals(HaulerWork.CAPACITY, store.metal());
    }

    @Test
    void aHaulerDestroyedLoadedLosesItsLoad() {
        HaulerWork hauler = new HaulerWork();
        hauler.takeOn(40f);
        assertEquals(40f, hauler.lose());
        assertEquals(0f, hauler.load());
        assertEquals(40f, hauler.lost());
    }

    @Test
    void aLoadedHaulerWithNoStoreLinkedStandsBy() {
        HaulerWork hauler = new HaulerWork();
        hauler.takeOn(10f);
        assertEquals(HaulerWork.Kind.STAND_BY, hauler.decide(3f, 3f, 1f, 1f, List.of(), List.of()).kind());
        assertEquals(10f, hauler.load(), "it keeps what it carries");
    }

    private static StakeTrain train() {
        return new StakeTrain(new ClaimLattice.Plan(new ClaimLattice.Point(2, 20, 20), ClaimLattice.HULL), 5, List.of(6));
    }

    @Test
    void aTrainWhoseHaulerDiesBeforePlantingLeavesScrap() {
        StakeTrain train = train();
        assertNull(train.step(true, 0f, 0f));
        assertEquals(StakeTrain.State.SCRAP, train.step(false, 0f, 0f));
        assertNull(train.step(true, 20.5f, 20.5f), "scrap plants nothing");
    }

    @Test
    void aTrainPlantsItsStakeOnReachingItsPoint() {
        assertEquals(StakeTrain.State.PLANTED, train().step(true, 21f, 19.5f));
    }

    @Test
    void aWalkNearAHaulRoadFollowsItAndAnyOtherGoesStraight() {
        HaulRoads roads = new HaulRoads(List.of(new int[] {0, 0, 10, 0, 20, 0, 30, 0, 40, 0, 50, 0, 60, 0, 70, 0,
                80, 0, 90, 0, 100, 0, 110, 0, 120, 0, 130, 0}));
        // Road cells are every ten along x here, so each cell is its own waypoint step.
        List<int[]> route = roads.route(2, 3, 128, 4);
        assertArrayEquals(new int[] {0, 0}, route.get(0));
        assertArrayEquals(new int[] {128, 4}, route.get(route.size() - 1));
        assertTrue(route.size() > 2, "it walks the road between");
        List<int[]> straight = roads.route(2, 40, 128, 40);
        assertEquals(1, straight.size());
        HaulRoads.Leg leg = new HaulRoads.Leg(route);
        assertArrayEquals(new int[] {0, 0}, leg.toward(10f, 10f));
        assertArrayEquals(route.get(1), leg.toward(0.5f, 0.5f), "it moves on once there");
    }
    @Test
    void aHaulerStandsBesideWhatItReachesNeverOnIt() {
        // From the east, on open ground: two cells short of the stripper on the line, within reach.
        int[] at = HaulerWork.approach(10.5f, 10.5f, 30.5f, 10.5f, (x, y) -> true);
        assertArrayEquals(new int[] {12, 10}, at);
        // That cell blocked: the open cell round the target at the same distance nearest the hauler.
        at = HaulerWork.approach(10.5f, 10.5f, 30.5f, 10.5f, (x, y) -> !(x == 12 && y == 10));
        assertEquals(2, Math.max(Math.abs(at[0] - 10), Math.abs(at[1] - 10)));
        assertEquals(12, at[0], "on the hauler's side");
        // Nothing round it open: the target's own cell.
        assertArrayEquals(new int[] {10, 10}, HaulerWork.approach(10.5f, 10.5f, 30.5f, 10.5f, (x, y) -> false));
    }
}
