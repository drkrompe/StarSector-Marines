package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.AlliedTool;
import com.dillon.starsectormarines.battle.cordon.CordonAlliedRules.Hull;
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
    void escortsComeOutOfTheSurplusOnePerSector() {
        List<Post> posts = List.of(
                new Post(1, true, false, 10, 3, true),
                new Post(2, true, false, 10, 1, true),
                new Post(3, true, true, 10, 2, true));
        List<Hull> free = List.of(
                new Hull(11, 0, 0, 1), new Hull(12, 1, 0, 1), new Hull(13, 2, 0, 1),
                new Hull(21, 0, 1, 2),
                new Hull(31, 5, 5, 3), new Hull(32, 6, 5, 3));
        List<Hull> out = CordonAlliedRules.escorts(0, 0, 3, free, posts, 1);
        assertEquals(List.of(11L, 31L), out.stream().map(Hull::id).toList());
    }

    @Test
    void aCallingRedoubtsLastMachineNeverEscorts() {
        List<Post> posts = List.of(new Post(1, true, true, 10, 1, true));
        assertTrue(CordonAlliedRules.escorts(0, 0, 1, List.of(new Hull(1, 0, 0, 1)), posts, 0).isEmpty());
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
        assertTrue(CordonAlliedRules.wantsAmmunition(true, 0.2f, 0.3f, 0, 0));
        assertTrue(CordonAlliedRules.wantsAmmunition(true, 0.9f, 0.3f, 2, 0));
        assertFalse(CordonAlliedRules.wantsAmmunition(true, 0.2f, 0.3f, 2, 1));
        assertFalse(CordonAlliedRules.wantsAmmunition(true, 0.3f, 0.3f, 0, 0));
        assertFalse(CordonAlliedRules.wantsAmmunition(false, 0f, 0.3f, 1, 0));
    }

    @Test
    void helpComesByCarrierOrColumn() {
        assertTrue(CordonAlliedRules.helpComing(true, false));
        assertTrue(CordonAlliedRules.helpComing(false, true));
        assertFalse(CordonAlliedRules.helpComing(false, false));
    }
}
