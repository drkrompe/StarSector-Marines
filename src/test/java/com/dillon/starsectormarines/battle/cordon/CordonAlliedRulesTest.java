package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.AlliedTool;
import com.dillon.starsectormarines.battle.cordon.CordonAlliedRules.Escort;
import com.dillon.starsectormarines.battle.cordon.CordonAlliedRules.Kind;
import com.dillon.starsectormarines.battle.cordon.CordonAlliedRules.Post;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordonAlliedRulesTest {

    @Test
    void theFloorIsAPairInACity() {
        assertEquals(1, CordonAlliedRules.coverFloor(CordonPlan.Density.RURAL));
        assertEquals(2, CordonAlliedRules.coverFloor(CordonPlan.Density.URBAN));
    }

    @Test
    void theNextMachineGoesUnderTheFloorUncoveredFirst() {
        List<Post> posts = List.of(
                new Post(1, true, true, 10, 1, true),
                new Post(2, true, false, 10, 0, true),
                new Post(3, true, true, 10, 0, false),
                new Post(4, false, false, 0, 0, true));
        assertTrue(CordonAlliedRules.uncovered(posts.get(1)));
        assertFalse(CordonAlliedRules.uncovered(posts.get(2)));
        assertFalse(CordonAlliedRules.uncovered(posts.get(3)));
        assertEquals(2, CordonAlliedRules.nextPost(posts, 1));
        assertEquals(-1, CordonAlliedRules.nextPost(List.of(new Post(1, false, false, 0, 0, true)), 1));
    }

    @Test
    void aConvoyIsEscortedOnlyByAWholeEscortAlongside() {
        assertTrue(CordonAlliedRules.convoyEscorted(3, 3, true));
        // A hull near a truck that the armour did not send whole: short, and the company walks.
        assertFalse(CordonAlliedRules.convoyEscorted(3, 1, true));
        assertFalse(CordonAlliedRules.convoyEscorted(3, 0, true));
        // Sent but not yet alongside.
        assertFalse(CordonAlliedRules.convoyEscorted(3, 3, false));
        assertFalse(CordonAlliedRules.convoyEscorted(0, 0, true));
    }

    @Test
    void escortsComeOutOfTheSurplusOnePerSector() {
        List<Post> posts = List.of(
                new Post(1, true, false, 10, 3, true),
                new Post(2, true, false, 10, 1, true),
                new Post(3, true, true, 10, 2, true));
        List<Escort> free = List.of(
                new Escort(11, 0, 0, 1), new Escort(12, 1, 0, 1), new Escort(13, 2, 0, 1),
                new Escort(21, 0, 1, 2),
                new Escort(31, 5, 5, 3), new Escort(32, 6, 5, 3));
        List<Escort> out = CordonAlliedRules.escorts(0, 0, 3, free, posts, 1);
        assertEquals(List.of(11L, 31L), out.stream().map(Escort::id).toList());
    }

    @Test
    void aCallingRedoubtsLastMachineNeverEscorts() {
        List<Post> posts = List.of(new Post(1, true, true, 10, 1, true));
        assertTrue(CordonAlliedRules.escorts(0, 0, 1, List.of(new Escort(1, 0, 0, 1)), posts, 0).isEmpty());
    }

    @Test
    void aCitysStreetTeamEscortsWhileItsMechPairHoldsTheFloor() {
        // Urban floor: the pair. Sector 1 holds its pair, sector 2 has lost a Hound, sector 3 has a relief pair too.
        List<Post> posts = List.of(
                new Post(1, true, false, 10, 2, true),
                new Post(2, true, false, 10, 1, true),
                new Post(3, true, false, 10, 4, true));
        List<Escort> free = List.of(
                new Escort(101, 0, 0, 1, Kind.MECH), new Escort(102, 1, 0, 1, Kind.TEAM),
                new Escort(201, 0, 1, 2, Kind.TEAM),
                new Escort(301, 9, 9, 3, Kind.MECH));
        // The pair is the floor, so no Hound of sector 1 goes, but its team does; sector 2's team is its last cover
        // above a broken pair and stays; sector 3's relief leaves a Hound to spare.
        assertEquals(List.of(102L, 301L),
                CordonAlliedRules.escorts(0, 0, 3, free, posts, 2).stream().map(Escort::id).toList());
    }

    @Test
    void aCallingRedoubtsStreetTeamStaysWhereNoMachineIsLeft() {
        List<Post> posts = List.of(new Post(1, true, true, 10, 0, true));
        assertTrue(CordonAlliedRules.escorts(0, 0, 1, List.of(new Escort(1, 0, 0, 1, Kind.TEAM)), posts, 0).isEmpty());
    }

    @Test
    void onlyACityTakesWalkingEscorts() {
        assertTrue(CordonAlliedRules.walkingEscorts(CordonPlan.Density.URBAN));
        assertFalse(CordonAlliedRules.walkingEscorts(CordonPlan.Density.TOWN));
        assertFalse(CordonAlliedRules.walkingEscorts(CordonPlan.Density.RURAL));
    }

    @Test
    void aCarrierTakesItsSquadToTheNeediest() {
        List<Post> posts = List.of(
                new Post(1, true, false, 2, 0, true),
                new Post(2, true, true, 8, 0, true),
                new Post(3, false, true, 0, 0, true));
        assertEquals(2, CordonAlliedRules.carrierDuty(posts));
        assertEquals(1, CordonAlliedRules.carrierDuty(posts.subList(0, 1)));
    }

    @Test
    void anUncoveredSectorAsksForCoverOnceAtATime() {
        assertTrue(CordonAlliedRules.wantsCover(true, 0));
        assertFalse(CordonAlliedRules.wantsCover(true, 1));
        assertFalse(CordonAlliedRules.wantsCover(false, 0));
        assertEquals(AlliedTool.MECH_LANCE, CordonAlliedRules.coverTool(CordonPlan.Density.URBAN));
        assertEquals(AlliedTool.ARMOUR_PAIR, CordonAlliedRules.coverTool(CordonPlan.Density.TOWN));
    }

    @Test
    void aLowRedoubtAsksForOneTruck() {
        assertTrue(CordonAlliedRules.wantsAmmunition(true, true, 0.2f, 0.3f, 0, 0));
        assertTrue(CordonAlliedRules.wantsAmmunition(true, true, 0.9f, 0.3f, 2, 0));
        assertFalse(CordonAlliedRules.wantsAmmunition(true, true, 0.2f, 0.3f, 2, 1));
        assertFalse(CordonAlliedRules.wantsAmmunition(true, true, 0.3f, 0.3f, 0, 0));
        assertFalse(CordonAlliedRules.wantsAmmunition(false, true, 0f, 0.3f, 1, 0));
        assertFalse(CordonAlliedRules.wantsAmmunition(true, false, 0f, 0.3f, 1, 0), "no truck where none can drive");
    }

    @Test
    void helpComesByCarrierOrColumn() {
        assertTrue(CordonAlliedRules.helpComing(true, false));
        assertTrue(CordonAlliedRules.helpComing(false, true));
        assertFalse(CordonAlliedRules.helpComing(false, false));
    }

    @Test
    void onlyATownsHullsKeepToTheStreetsAndNotWhereTheyAreTheConvoysRoad() {
        assertTrue(CordonAlliedRules.roadbound(CordonPlan.Density.TOWN, CordonVariant.LINE));
        assertFalse(CordonAlliedRules.roadbound(CordonPlan.Density.RURAL, CordonVariant.LINE));
        assertFalse(CordonAlliedRules.roadbound(CordonPlan.Density.URBAN, CordonVariant.LINE));
        assertFalse(CordonAlliedRules.roadbound(CordonPlan.Density.TOWN, CordonVariant.ROAD));
    }

    @Test
    void theShedStopsAtItsCapAndBuildsAgainAfterALoss() {
        CordonAlliedTable.Production cap = new CordonAlliedTable.Production(3, 1);
        assertTrue(CordonAlliedRules.shedBuilds(2, 0, cap));
        assertFalse(CordonAlliedRules.shedBuilds(3, 0, cap), "three out: held");
        assertTrue(CordonAlliedRules.shedBuilds(3 - 1, 0, cap), "one lost: building again");
    }

    @Test
    void aFinishedMachineWaitingAtTheDoorHoldsTheShedUntilItIsOut() {
        CordonAlliedTable.Production cap = new CordonAlliedTable.Production(10, 1);
        assertFalse(CordonAlliedRules.shedBuilds(1, 1, cap), "one waiting at the door: nothing more piles up behind it");
        assertTrue(CordonAlliedRules.shedBuilds(1, 0, cap), "sent out: the shed goes on");
    }
}
