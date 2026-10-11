package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.command.CommandGroupIdentity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Later lifts join the squad already on the ground; a two-zone landing splits and says so. */
class CampaignSquadIndexTest {

    private final Map<Integer, Squad> squads = new HashMap<>();
    private int nextId = 0;

    @Test
    void lateLiftsAndSplitLandingsRetainFrozenOfficerMembership() {
        CampaignSquadIndex index = new CampaignSquadIndex(squads::get);
        CommandGroupIdentity group = new CommandGroupIdentity("officer:o1", "o1", "Vance", true);
        CampaignSquadTag tag = new CampaignSquadTag("cs-1", "Squad 01", true, 12, 0, group);
        int first = resolve(index, tag, 0);
        int late = resolve(index, tag, 0);
        int split = resolve(index, tag, 1);
        assertEquals(first, late);
        assertEquals(group, squads.get(first).commandGroupIdentity);
        assertEquals(group, squads.get(split).commandGroupIdentity);
        int another = resolve(index, new CampaignSquadTag("cs-2", "Squad 02", true, 12, 0, group), 0);
        assertNotEquals(first, another);
        assertEquals(group, squads.get(another).commandGroupIdentity);
    }

    @Test
    void actingNcoLateArrivalsRemainIndependentFromOtherSquads() {
        CampaignSquadIndex index = new CampaignSquadIndex(squads::get);
        CampaignSquadTag tag = new CampaignSquadTag("cs-1", "Squad 01", true, 12);
        int first = resolve(index, tag, 0);
        assertEquals(first, resolve(index, tag, 0));
        int second = resolve(index, new CampaignSquadTag("cs-2", "Squad 02", true, 12), 0);
        assertEquals(tag.commandGroupIdentity, squads.get(first).commandGroupIdentity);
        assertNotEquals(squads.get(first).commandGroupIdentity, squads.get(second).commandGroupIdentity);
    }

    @Test
    void aSquadCrossingInThreeLiftsIsOneSquadOnTheGround() {
        CampaignSquadIndex index = new CampaignSquadIndex(squads::get);
        CampaignSquadTag tag = new CampaignSquadTag("cs-1", "Squad 01", false, 12);

        int first = resolve(index, tag, 0);
        int second = resolve(index, tag, 0);
        int third = resolve(index, tag, 0);

        assertEquals(first, second);
        assertEquals(first, third);
        assertEquals(1, squads.size());
        assertEquals("Squad 01", squads.get(first).campaignLabel);
        assertEquals("cs-1", squads.get(first).campaignSquadId);
    }

    @Test
    void twoLandingZonesSplitIntoTwoSquadsAndBothSaySo() {
        CampaignSquadIndex index = new CampaignSquadIndex(squads::get);
        CampaignSquadTag tag = new CampaignSquadTag("cs-1", "Squad 01", false, 12);

        int here = resolve(index, tag, 0);
        assertEquals("Squad 01", squads.get(here).campaignLabel);

        int there = resolve(index, tag, 1);

        assertNotEquals(here, there);
        // The first landing is suffixed retroactively — until the second one
        // happens there is nothing to distinguish it from.
        assertEquals("Squad 01 (A)", squads.get(here).campaignLabel);
        assertEquals("Squad 01 (B)", squads.get(there).campaignLabel);
    }

    @Test
    void differentCampaignSquadsAtOneZoneStayApart() {
        CampaignSquadIndex index = new CampaignSquadIndex(squads::get);

        int first = resolve(index, new CampaignSquadTag("cs-1", "Squad 01", false, 12), 0);
        int second = resolve(index, new CampaignSquadTag("cs-2", "Squad 02", false, 12), 0);

        assertNotEquals(first, second);
        assertEquals("Squad 01", squads.get(first).campaignLabel);
        assertEquals("Squad 02", squads.get(second).campaignLabel);
    }

    @Test
    void distinctBerthsInOneLogicalAreaDoNotSplitTheCampaignSquad() {
        CampaignSquadIndex index = new CampaignSquadIndex(squads::get);
        CampaignSquadTag tag = new CampaignSquadTag(
                "cs-1", "Squad 01", false, 12);

        int firstBerth = resolve(index, tag, 4);
        int secondBerth = resolve(index, tag, 4);

        assertEquals(firstBerth, secondBerth);
        assertEquals("Squad 01", squads.get(firstBerth).campaignLabel);
    }

    @Test
    void unconfiguredBoatsAtDifferentBerthsAndLaterSortiesKeepOneCampaignSquad() {
        CampaignSquadIndex index = new CampaignSquadIndex(squads::get);
        CampaignSquadTag tag = new CampaignSquadTag("cs-default", "Squad 01", true, 12);
        ShuttleMission first = new ShuttleMission(10, 20, -1, 20, -1, 20, 0, 6);
        ShuttleMission second = new ShuttleMission(40, 60, -1, 60, -1, 60, 4, 6);
        int squad = resolve(index, tag, first.landingAreaId);
        assertEquals(squad, resolve(index, tag, second.landingAreaId));
        second.currentCycle++;
        assertEquals(squad, resolve(index, tag, second.landingAreaId));
        assertEquals(1, squads.size());
        assertEquals(12, squads.get(squad).expectedSize);
        assertEquals("Squad 01", squads.get(squad).campaignLabel);
    }

    @Test
    void invalidAreaCannotSilentlyFallBackToBerthCoordinates() {
        CampaignSquadIndex index = new CampaignSquadIndex(squads::get);
        CampaignSquadTag tag = new CampaignSquadTag("cs-invalid", "Squad 01", false, 12);
        assertThrows(IllegalArgumentException.class, () -> index.landed(tag.squadId, -1));
        assertThrows(IllegalArgumentException.class, () -> index.register(tag, -1, 0));
        int squad = resolve(index, tag, 0);
        assertEquals("Squad 01", squads.get(squad).campaignLabel, "Invalid input must not record a split");
    }

    /** Mirrors the roster's mint-on-miss operation without constructing a battle. */
    private int resolve(CampaignSquadIndex index, CampaignSquadTag tag, int landingAreaId) {
        int existing = index.landed(tag.squadId, landingAreaId);
        if (existing != Squad.NO_SQUAD) return existing;
        int minted = nextId++;
        squads.put(minted, new Squad(minted, Faction.MARINE));
        index.register(tag, landingAreaId, minted);
        return minted;
    }
}
