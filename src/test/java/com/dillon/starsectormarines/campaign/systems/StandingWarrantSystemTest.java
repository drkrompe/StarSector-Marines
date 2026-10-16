package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.marine.ArmorTradition;
import com.dillon.starsectormarines.marine.MilitaryWarrants;
import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingWarrantSystemTest {

    @Test
    void eachTraditionGrantsItsOwnWarrantOnItsOwnStanding() {
        CampaignState state = new CampaignState();
        state.playerMrbRep = 20;
        Map<String, RepLevel> standing = Map.of(
                "hegemony", RepLevel.COOPERATIVE,
                "tritachyon", RepLevel.FRIENDLY);

        new StandingWarrantSystem(id -> standing.getOrDefault(id, RepLevel.NEUTRAL)).tick(state, 3);

        assertTrue(MilitaryWarrants.holds(state, ArmorTradition.HEGEMONY));
        assertFalse(MilitaryWarrants.holds(state, ArmorTradition.TRITACHYON),
                "Friendly is one step short of the standing a tradition asks for");
        assertEquals(1, MilitaryWarrants.held(state).size());
    }

    @Test
    void closeStandingAloneIsNotEnough() {
        CampaignState state = new CampaignState();
        state.playerMrbRep = 19;

        new StandingWarrantSystem(id -> RepLevel.COOPERATIVE).tick(state, 3);

        assertEquals(0, state.militaryWarrantCount);
    }
}
