package com.dillon.starsectormarines.campaign.ruins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsVaultPrizesTest {

    private static final long SEED = 0x5EC7012L;
    private static final int SITES = 3000;
    private static final List<String> CORES =
            List.of(RuinsVaultPrize.GAMMA_CORE, RuinsVaultPrize.BETA_CORE, RuinsVaultPrize.ALPHA_CORE);

    private static RuinsVaultPrize prize(int site, RuinsGrade grade, RuinsOccupant occupant) {
        return RuinsVaultPrizes.draw(SEED, "site_" + site, grade, occupant);
    }

    @Test
    void aSiteHoldsTheSamePrizeEveryTime() {
        for (int i = 0; i < 200; i++) {
            assertEquals(prize(i, RuinsGrade.VAST, RuinsOccupant.SCAVENGERS),
                    prize(i, RuinsGrade.VAST, RuinsOccupant.SCAVENGERS));
        }
    }

    @Test
    void missingFactsDrawNothing() {
        assertNull(RuinsVaultPrizes.draw(SEED, null, RuinsGrade.VAST, RuinsOccupant.SCAVENGERS));
        assertNull(RuinsVaultPrizes.draw(SEED, "site", null, RuinsOccupant.SCAVENGERS));
        assertNull(RuinsVaultPrizes.draw(SEED, "site", RuinsGrade.VAST, null));
    }

    @Test
    void theThemeAlwaysTellsTheFamilyAndTheItem() {
        for (RuinsGrade grade : RuinsGrade.values()) {
            for (int i = 0; i < 500; i++) {
                RuinsVaultPrize prize = prize(i, grade, RuinsOccupant.DOMAIN_SECURITY);
                assertEquals(prize.family(), prize.theme().family);
                switch (prize.family()) {
                    case COLONY_ITEM -> assertEquals(prize.theme().itemId, prize.itemId());
                    case AI_CORE -> assertTrue(CORES.contains(prize.itemId()));
                    default -> assertNull(prize.itemId());
                }
            }
        }
    }

    @Test
    void aScatteredSiteHoldsNoColonyItemAndOnlyAGammaCore() {
        for (int i = 0; i < SITES; i++) {
            RuinsVaultPrize prize = prize(i, RuinsGrade.SCATTERED, RuinsOccupant.DOMAIN_SECURITY);
            assertFalse(prize.family() == RuinsVaultPrize.Family.COLONY_ITEM);
            if (prize.family() == RuinsVaultPrize.Family.AI_CORE) {
                assertEquals(RuinsVaultPrize.GAMMA_CORE, prize.itemId());
            }
        }
    }

    @Test
    void theRavKeepNoCoresAndOnlyLivingOrDeepItems() {
        for (int i = 0; i < SITES; i++) {
            RuinsVaultPrize prize = prize(i, RuinsGrade.VAST, RuinsOccupant.RAV);
            assertFalse(prize.family() == RuinsVaultPrize.Family.AI_CORE);
            if (prize.family() == RuinsVaultPrize.Family.COLONY_ITEM) assertTrue(prize.theme().ravGround);
        }
    }

    @Test
    void onlyDerelictSecurityKeepsADroneReplicator() {
        for (int i = 0; i < SITES; i++) {
            assertFalse(prize(i, RuinsGrade.VAST, RuinsOccupant.SCAVENGERS).theme()
                    == RuinsVaultTheme.DRONE_REPLICATOR);
        }
    }

    @Test
    void derelictSecurityLeansToCores() {
        int derelict = 0;
        int scavengers = 0;
        for (int i = 0; i < SITES; i++) {
            if (prize(i, RuinsGrade.EXTENSIVE, RuinsOccupant.DOMAIN_SECURITY).family()
                    == RuinsVaultPrize.Family.AI_CORE) derelict++;
            if (prize(i, RuinsGrade.EXTENSIVE, RuinsOccupant.SCAVENGERS).family()
                    == RuinsVaultPrize.Family.AI_CORE) scavengers++;
        }
        assertTrue(derelict > scavengers * 1.5, derelict + " vs " + scavengers);
    }

    @Test
    void stakesRiseWithThePrize() {
        assertEquals(0, new RuinsVaultPrize(RuinsVaultPrize.Family.GOODS, RuinsVaultTheme.DEPOT, null, 0).stakes());
        for (int i = 0; i < SITES; i++) {
            RuinsVaultPrize prize = prize(i, RuinsGrade.VAST, RuinsOccupant.DOMAIN_SECURITY);
            int expected = switch (prize.family()) {
                case GOODS -> 0;
                case TECH -> 1;
                case AI_CORE -> CORES.indexOf(prize.itemId()) + 1;
                case COLONY_ITEM -> prize.theme() == RuinsVaultTheme.PRISTINE_NANOFORGE ? 3 : 2;
            };
            assertEquals(expected, prize.stakes());
        }
    }

    @Test
    void aCoreHintIsLikelyRightAndNeverMoreThanOneStepOff() {
        int cores = 0;
        int right = 0;
        for (int i = 0; i < SITES; i++) {
            RuinsVaultPrize prize = prize(i, RuinsGrade.VAST, RuinsOccupant.DOMAIN_SECURITY);
            String hint = RuinsVaultPrizes.hintedCore(SEED, "site_" + i, prize);
            if (prize.family() != RuinsVaultPrize.Family.AI_CORE) {
                assertNull(hint);
                continue;
            }
            assertNotNull(hint);
            cores++;
            if (hint.equals(prize.itemId())) right++;
            assertTrue(Math.abs(CORES.indexOf(hint) - CORES.indexOf(prize.itemId())) <= 1);
        }
        double share = right / (double) cores;
        assertTrue(share > 0.68 && share < 0.82, "hint right " + share);
    }

    @Test
    void onlyCoresAndItemsBreakWhenForced() {
        for (RuinsVaultTheme theme : RuinsVaultTheme.values()) {
            RuinsVaultPrize prize = new RuinsVaultPrize(theme.family, theme, theme.itemId, 0);
            boolean valuable = theme.family == RuinsVaultPrize.Family.AI_CORE
                    || theme.family == RuinsVaultPrize.Family.COLONY_ITEM;
            assertEquals(valuable, prize.breaksWhenForced());
        }
    }

    @Test
    void tellKeysAreCamelCase() {
        assertEquals("ruinsTellServerHall", RuinsVaultTheme.SERVER_HALL.tellKey());
        assertEquals("ruinsTellCryoarithmeticEngine", RuinsVaultTheme.CRYOARITHMETIC_ENGINE.tellKey());
    }
}
