package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Later lifts join the squad already on the ground; a two-zone landing splits and says so. */
class CampaignSquadIndexTest {

    private final Map<Integer, Squad> squads = new HashMap<>();
    private int nextId = 0;

    @Test
    void aSquadCrossingInThreeLiftsIsOneSquadOnTheGround() {
        CampaignSquadIndex index = new CampaignSquadIndex(squads::get);
        CampaignSquadTag tag = new CampaignSquadTag("cs-1", "Squad 01", false, 12);

        int first = resolve(index, tag, 10, 20);
        int second = resolve(index, tag, 10, 20);
        int third = resolve(index, tag, 10, 20);

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

        int here = resolve(index, tag, 10, 20);
        assertEquals("Squad 01", squads.get(here).campaignLabel);

        int there = resolve(index, tag, 40, 60);

        assertNotEquals(here, there);
        // The first landing is suffixed retroactively — until the second one
        // happens there is nothing to distinguish it from.
        assertEquals("Squad 01 (A)", squads.get(here).campaignLabel);
        assertEquals("Squad 01 (B)", squads.get(there).campaignLabel);
    }

    @Test
    void differentCampaignSquadsAtOneZoneStayApart() {
        CampaignSquadIndex index = new CampaignSquadIndex(squads::get);

        int first = resolve(index, new CampaignSquadTag("cs-1", "Squad 01", false, 12), 10, 20);
        int second = resolve(index, new CampaignSquadTag("cs-2", "Squad 02", false, 12), 10, 20);

        assertNotEquals(first, second);
        assertEquals("Squad 01", squads.get(first).campaignLabel);
        assertEquals("Squad 02", squads.get(second).campaignLabel);
    }

    @Test
    void distinctBerthsInOneLogicalAreaDoNotSplitTheCampaignSquad() {
        CampaignSquadIndex index = new CampaignSquadIndex(squads::get);
        CampaignSquadTag tag = new CampaignSquadTag(
                "cs-1", "Squad 01", false, 12);

        int firstBerth = resolve(index, tag, "area:4");
        int secondBerth = resolve(index, tag, "area:4");

        assertEquals(firstBerth, secondBerth);
        assertEquals("Squad 01", squads.get(firstBerth).campaignLabel);
    }

    /** Mirrors {@code UnitRosterService.squadForCampaign}: look up, mint on miss, register. */
    private int resolve(CampaignSquadIndex index, CampaignSquadTag tag, int lzX, int lzY) {
        int existing = index.landed(tag.squadId, lzX, lzY);
        if (existing != Squad.NO_SQUAD) return existing;
        int minted = nextId++;
        squads.put(minted, new Squad(minted, Faction.MARINE));
        index.register(tag, lzX, lzY, minted);
        return minted;
    }

    private int resolve(CampaignSquadIndex index, CampaignSquadTag tag,
                        String landingKey) {
        int existing = index.landed(tag.squadId, landingKey);
        if (existing != Squad.NO_SQUAD) return existing;
        int minted = nextId++;
        squads.put(minted, new Squad(minted, Faction.MARINE));
        index.register(tag, landingKey, minted);
        return minted;
    }
}
