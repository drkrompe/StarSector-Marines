package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hostility relation, stated once so nothing else has to guess it.
 *
 * <p>The whole matrix is here rather than the interesting corners of it,
 * because the defect this relation exists to prevent is exactly a case nobody
 * thought to write down: a chain of {@code != MARINE} tests reads an allied
 * militia as an enemy, and every site that grew such a chain believed it was
 * asking a two-sided question.
 */
class FactionTest {

    @Test
    void nothingIsHostileToItsOwnFaction() {
        for (Faction faction : Faction.values()) {
            assertFalse(faction.hostileTo(faction), faction + " fights itself");
            assertTrue(faction.friendlyTo(faction), faction + " disowns itself");
        }
    }

    @Test
    void theCompanyAndItsAlliesFightTheDefender() {
        assertTrue(Faction.MARINE.hostileTo(Faction.DEFENDER));
        assertTrue(Faction.DEFENDER.hostileTo(Faction.MARINE));
        assertTrue(Faction.ALLY.hostileTo(Faction.DEFENDER));
        assertTrue(Faction.DEFENDER.hostileTo(Faction.ALLY));
    }

    @Test
    void theCompanyAndItsAlliesDoNotFightEachOther() {
        assertFalse(Faction.MARINE.hostileTo(Faction.ALLY));
        assertFalse(Faction.ALLY.hostileTo(Faction.MARINE));
        assertTrue(Faction.MARINE.friendlyTo(Faction.ALLY));
        assertTrue(Faction.ALLY.friendlyTo(Faction.MARINE));
    }

    @Test
    void aCivilianIsHostileToNobodyAndNobodyIsHostileToIt() {
        for (Faction faction : Faction.values()) {
            assertFalse(Faction.CIVILIAN.hostileTo(faction),
                    "a civilian fights " + faction);
            assertFalse(faction.hostileTo(Faction.CIVILIAN),
                    faction + " fights a civilian");
        }
    }

    /**
     * Friendly is not merely "not hostile". A civilian is neither, which is the
     * distinction the protected-non-target rule and the kill-credit rule each
     * depend on: a defender round that kills a civilian is not friendly fire.
     */
    @Test
    void aCivilianIsFriendlyToNobodyButItself() {
        for (Faction faction : Faction.values()) {
            if (faction == Faction.CIVILIAN) continue;
            assertFalse(Faction.CIVILIAN.friendlyTo(faction));
            assertFalse(faction.friendlyTo(Faction.CIVILIAN));
        }
    }

    @Test
    void aDefenderIsFriendlyToNobodyButItself() {
        assertFalse(Faction.DEFENDER.friendlyTo(Faction.MARINE));
        assertFalse(Faction.DEFENDER.friendlyTo(Faction.ALLY));
        assertFalse(Faction.MARINE.friendlyTo(Faction.DEFENDER));
        assertFalse(Faction.ALLY.friendlyTo(Faction.DEFENDER));
    }

    /** No pair is both, whichever way round it is asked. */
    @Test
    void hostileAndFriendlyAreNeverBothTrueAndBothAreSymmetric() {
        for (Faction a : Faction.values()) {
            for (Faction b : Faction.values()) {
                assertFalse(a.hostileTo(b) && a.friendlyTo(b),
                        a + " is both to " + b);
                assertEquals(a.hostileTo(b), b.hostileTo(a),
                        "hostility is one-sided between " + a + " and " + b);
                assertEquals(a.friendlyTo(b), b.friendlyTo(a),
                        "friendship is one-sided between " + a + " and " + b);
            }
        }
    }

    /**
     * The ordinal form is the same answer. It exists for the spatial index's
     * denormalised {@code byte} faction column, which is walked per candidate
     * per query and has no enum to hand.
     */
    @Test
    void theOrdinalFormAgreesWithTheEnumFormEverywhere() {
        for (Faction a : Faction.values()) {
            for (Faction b : Faction.values()) {
                assertEquals(a.hostileTo(b),
                        Faction.hostile((byte) a.ordinal(), (byte) b.ordinal()),
                        "hostile(" + a + ", " + b + ")");
                assertEquals(a.friendlyTo(b),
                        Faction.friendly((byte) a.ordinal(), (byte) b.ordinal()),
                        "friendly(" + a + ", " + b + ")");
            }
        }
    }

    /** A missing identity is not a target and not one of ours. */
    @Test
    void aNullFactionIsNeither() {
        for (Faction faction : Faction.values()) {
            assertFalse(faction.hostileTo(null));
            assertFalse(faction.friendlyTo(null));
        }
    }

    /**
     * Appending is the contract, not a coincidence. {@code CommanderService}
     * pulses commanders in declaration order and {@link UnitSpatialIndex} sizes
     * its per-faction slices from the ordinal, so inserting a value renumbers
     * both silently.
     */
    @Test
    void theExistingOrdinalsAreUnchangedByTheNewValue() {
        assertEquals(0, Faction.MARINE.ordinal());
        assertEquals(1, Faction.DEFENDER.ordinal());
        assertEquals(2, Faction.CIVILIAN.ordinal());
        assertEquals(3, Faction.ALLY.ordinal());
    }
}
