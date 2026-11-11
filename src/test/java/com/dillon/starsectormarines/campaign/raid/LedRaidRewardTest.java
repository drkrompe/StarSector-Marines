package com.dillon.starsectormarines.campaign.raid;

import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a won led raid pays ({@code raid-resolution.md}, law 9). */
class LedRaidRewardTest {

    @Test
    void aHarderMarketPaysMore() {
        assertTrue(LedRaidReward.premium(RaidSiteTemplate.Security.OPEN)
                < LedRaidReward.premium(RaidSiteTemplate.Security.GUARDED));
        assertTrue(LedRaidReward.premium(RaidSiteTemplate.Security.GUARDED)
                < LedRaidReward.premium(RaidSiteTemplate.Security.FORTIFIED));
        assertEquals(1f, LedRaidReward.premium(null), "no market's security reads: no premium");
    }

    @Test
    void aWonRaidIsTheFullRaidAtItsPremiumAndADisruptingOneAtTheShareOfTheWorksWrecked() {
        assertEquals(LedRaidReward.FULL_TOKENS, LedRaidReward.tokens(1f, false, 0f));
        assertEquals(15, LedRaidReward.tokens(1.5f, false, 0f));
        assertEquals(8, LedRaidReward.tokens(1.5f, true, 0.5f));
        assertEquals(1, LedRaidReward.tokens(1f, true, 0.01f), "any machine wrecked disrupts");
        assertEquals(0, LedRaidReward.tokens(1.5f, true, 0f), "nothing wrecked, nothing disrupted");
    }

    @Test
    void thePlayersChoiceIsFilledInProportionAndNeverCut() {
        assertArrayEquals(new int[]{10, 0, 5}, LedRaidReward.fill(new int[]{2, 0, 1}, 15),
                "an objective the player left empty stays empty");
        assertArrayEquals(new int[]{8, 7}, LedRaidReward.fill(new int[]{1, 1}, 15),
                "the odd token goes to the earlier objective on a tie");
        assertArrayEquals(new int[]{9, 9}, LedRaidReward.fill(new int[]{9, 9}, 15),
                "a raid already past the full one keeps what it had");
        assertArrayEquals(new int[]{0, 0}, LedRaidReward.fill(new int[]{0, 0}, 15));
    }
}
