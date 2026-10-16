package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The duel ledger's outcome rules and ground tallies, fed directly. */
class WarshipDuelLedgerTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    private static Warship hound() throws Exception {
        return Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 0f, 0f, 0f);
    }

    @Test
    void whyAHullBeganToLeaveIsWhyItLeft() throws Exception {
        WarshipDuelLedger ledger = new WarshipDuelLedger();
        Warship climber = hound();
        Warship withdrawn = hound();
        Warship timedOut = hound();
        Warship climbedButTimedOut = hound();
        Warship stays = hound();
        for (Warship s : new Warship[]{climber, withdrawn, timedOut, climbedButTimedOut, stays}) ledger.register(s);
        ledger.leaving(climber, false, true);
        ledger.leaving(climber, true, false);
        ledger.gone(climber, 10f, true);
        ledger.leaving(withdrawn, true, false);
        ledger.gone(withdrawn, 11f, true);
        ledger.leaving(timedOut, false, false);
        ledger.gone(timedOut, 12f, false);
        ledger.leaving(climbedButTimedOut, false, true);
        ledger.gone(climbedButTimedOut, 13f, false);
        assertEquals(WarshipDuelLedger.Outcome.ESCAPED, ledger.entry(climber).outcome());
        assertEquals(WarshipDuelLedger.Outcome.BROKE_OFF, ledger.entry(withdrawn).outcome());
        assertEquals(WarshipDuelLedger.Outcome.DEPARTED, ledger.entry(timedOut).outcome());
        assertEquals(WarshipDuelLedger.Outcome.DEPARTED, ledger.entry(climbedButTimedOut).outcome(),
                "a climb that never carried the hull off the map is not an escape");
        assertEquals(WarshipDuelLedger.Outcome.OVER_THE_BATTLE, ledger.entry(stays).outcome());
        assertEquals(5, ledger.entries().size());
    }

    @Test
    void aHullDestroyedWhileLeavingWasDestroyed() throws Exception {
        WarshipDuelLedger ledger = new WarshipDuelLedger();
        Warship s = hound();
        ledger.leaving(s, true, false);
        ledger.destroyed(s, 4f, 10f, 12f);
        ledger.gone(s, 5f, true);
        WarshipDuelLedger.Entry e = ledger.entry(s);
        assertEquals(WarshipDuelLedger.Outcome.DESTROYED, e.outcome());
        assertEquals(4f, e.seconds(), 1e-6f);
        assertTrue(Float.isNaN(e.struckX()), "the struck point waits for the wreck");
        ledger.struck(s, 14f, 9f);
        assertEquals(14f, ledger.entry(s).struckX(), 1e-6f);
    }

    @Test
    void groundCasualtiesAreKeptByCauseAndSide() {
        WarshipDuelLedger ledger = new WarshipDuelLedger();
        ledger.ground(WarshipDuelLedger.Cause.STRAY_ROUND, Faction.MARINE, false);
        ledger.ground(WarshipDuelLedger.Cause.STRAY_ROUND, Faction.MARINE, true);
        ledger.ground(WarshipDuelLedger.Cause.WRECKAGE, Faction.DEFENDER, true);
        assertEquals(1, ledger.hurt(WarshipDuelLedger.Cause.STRAY_ROUND, Faction.MARINE));
        assertEquals(1, ledger.killed(WarshipDuelLedger.Cause.STRAY_ROUND, Faction.MARINE));
        assertEquals(1, ledger.killed(WarshipDuelLedger.Cause.WRECKAGE, Faction.DEFENDER));
        assertEquals(0, ledger.hurt(WarshipDuelLedger.Cause.WRECKAGE, Faction.MARINE));
    }
}
