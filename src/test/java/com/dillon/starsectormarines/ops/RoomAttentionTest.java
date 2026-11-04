package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.ops.MarineOpsPageNav.Page;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomAttentionTest {
    @Test void aWoundedMarineMarksBarracksOnly() {
        var roster = new MarineRoster();
        var squad = roster.createSquad();
        roster.recruitToSquad(squad.id(), 12);
        assertFalse(RoomAttention.needed(Page.BARRACKS, roster, 0f));
        roster.applySoldierOutcome(Map.of(roster.squadMembers(squad).get(0).id(), MarineSoldierStatus.WIA), 0f, 1f);
        assertTrue(RoomAttention.needed(Page.BARRACKS, roster, 0f));
        assertFalse(RoomAttention.needed(Page.HQ, roster, 0f));
    }

    @Test void aGroundedShuttleOrNoShuttleMarksTheDeckButASpareBerthDoesNot() {
        var roster = new MarineRoster();
        var deck = roster.boatDeck();
        deck.reconcile("ship", ShuttleType.HERMES, 2);
        assertFalse(RoomAttention.needed(Page.BOAT_DECK, roster, 0f));
        var first = deck.boats().get(0).id();
        var second = deck.boats().get(1).id();
        deck.settleRecovery("battle", Map.of(first, 0.5f), List.of(), 0f);
        assertTrue(RoomAttention.needed(Page.BOAT_DECK, roster, 0f));
        assertFalse(RoomAttention.needed(Page.BOAT_DECK, roster, 2f), "Repaired by day two");
        deck.lose(List.of(first));
        assertFalse(RoomAttention.needed(Page.BOAT_DECK, roster, 2f), "One boat flies; the empty berth is a choice");
        deck.lose(List.of(second));
        assertTrue(RoomAttention.needed(Page.BOAT_DECK, roster, 2f));
    }

    @Test void noCompanyMeansNoMark() {
        assertFalse(RoomAttention.needed(Page.BARRACKS, null, 0f));
    }
}
