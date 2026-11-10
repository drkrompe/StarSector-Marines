package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultTheme;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsWorksTwistTest {

    @Test
    void everyThemeBendsTheWorksAndNoPrizeLeavesThemPlain() {
        Set<RuinsWorksTwist> used = EnumSet.noneOf(RuinsWorksTwist.class);
        for (RuinsVaultTheme theme : RuinsVaultTheme.values()) {
            RuinsWorksTwist twist = RuinsWorksTwist.of(theme);
            assertTrue(twist != RuinsWorksTwist.NONE, theme + " has a twist");
            used.add(twist);
        }
        assertEquals(EnumSet.complementOf(EnumSet.of(RuinsWorksTwist.NONE)), used, "every twist is some theme's");
        assertEquals(RuinsWorksTwist.NONE, RuinsWorksTwist.of((RuinsVaultTheme) null));
    }

    @Test
    void plainWorksChangeNothing() {
        RuinsWorksTwist none = RuinsWorksTwist.NONE;
        assertEquals(1, none.stores(1, 3));
        assertEquals(5, none.capacity(4, 5));
        assertEquals(VaultAccess.TWO_KEYS, none.access(VaultAccess.TWO_KEYS, true));
        assertEquals(RuinsWorks.RESTART_SECONDS, none.restartSeconds(RuinsWorks.RESTART_SECONDS));
        assertTrue(none.throwable(Branch.STORE, Branch.ONWARD));
        assertTrue(none.powerOpensStores());
        assertTrue(none.openPhase(25f));
        assertFalse(none.hardenedStores() || none.hallStartsRunning() || none.recordsStartRead() || none.loudForcing());
    }

    @Test
    void aDepotSealsTwoStoresWhereTheLayerHasTheRooms() {
        assertEquals(2, RuinsWorksTwist.STACKED_STORES.stores(1, 3));
        assertEquals(1, RuinsWorksTwist.STACKED_STORES.stores(1, 1), "never more than its rooms");
    }

    @Test
    void anInterlockedJunctionThrownToItsStoreStaysThere() {
        assertTrue(RuinsWorksTwist.INTERLOCKED_LINE.throwable(Branch.ONWARD, Branch.STORE));
        assertFalse(RuinsWorksTwist.INTERLOCKED_LINE.throwable(Branch.STORE, Branch.ONWARD));
    }

    @Test
    void coolantCarriesOnlyTheWayOnAndTheShaftCrossesTheVaultsFeed() {
        assertEquals(4, RuinsWorksTwist.COOLANT_LOAD.capacity(4, 5));
        assertEquals(1, RuinsWorksTwist.COOLANT_LOAD.capacity(0, 2), "a hall always carries one");
        assertEquals(VaultAccess.CROSSED_FEED, RuinsWorksTwist.SHAFT_FEED.access(VaultAccess.TWO_KEYS, true));
        assertEquals(VaultAccess.TWO_KEYS, RuinsWorksTwist.SHAFT_FEED.access(VaultAccess.TWO_KEYS, false),
                "only where the seal can cross");
    }

    @Test
    void magnetGatesCycleOpenThenShut() {
        RuinsWorksTwist gates = RuinsWorksTwist.MAGNET_GATES;
        assertTrue(gates.openPhase(0f));
        assertTrue(gates.openPhase(RuinsWorksTwist.MAGNET_OPEN_SECONDS - 0.1f));
        assertFalse(gates.openPhase(RuinsWorksTwist.MAGNET_OPEN_SECONDS + 0.1f));
        assertTrue(gates.openPhase(RuinsWorksTwist.MAGNET_OPEN_SECONDS + RuinsWorksTwist.MAGNET_SHUT_SECONDS + 0.1f));
    }

    @Test
    void storesStayOpenableOneWayOrTheOther() {
        // Nothing is locked for good: a store power cannot open is forcible, and a bunkered one keeps its power.
        for (RuinsWorksTwist twist : RuinsWorksTwist.values()) {
            assertFalse(twist.hardenedStores() && !twist.powerOpensStores(), twist + " locks its stores for good");
        }
    }

    @Test
    void eachTwistNamesItsLine() {
        assertEquals("ruinsTwistMagnetGates", RuinsWorksTwist.MAGNET_GATES.lineKey());
        assertEquals(null, RuinsWorksTwist.NONE.lineKey());
    }
}
