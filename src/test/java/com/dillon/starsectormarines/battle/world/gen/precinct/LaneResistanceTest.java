package com.dillon.starsectormarines.battle.world.gen.precinct;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ladder a lane carries: derived from the objective's rung, stated when a
 * mission cares, and never below a picket.
 */
class LaneResistanceTest {

    /** Nothing on the approach is harder than the thing the approach leads to. */
    @Test
    void aDerivedLadderStepsDownFromTheObjective() {
        LaneResistance ladder = LaneResistance.derive(Fortification.Strength.CITADEL);
        assertEquals(Fortification.Strength.STRONGHOLD, ladder.at(1).strength());
        assertEquals(Fortification.Strength.GARRISON, ladder.at(2).strength());
        assertEquals(Fortification.Strength.PICKET, ladder.at(3).strength());
        for (LaneResistance.Rung rung : ladder.rungs()) {
            assertTrue(rung.strength().ordinal()
                            < Fortification.Strength.CITADEL.ordinal(),
                    "band " + rung.band() + " is as hard as the objective it leads to");
        }
    }

    /**
     * A rung below a picket is an empty field, and a lane with an empty rung in
     * it is not a ladder. {@link Fortification.Strength#nudged} floors it; this
     * is the assertion that the floor is relied on rather than accidental.
     */
    @Test
    void theLadderFloorsAtPicket() {
        LaneResistance ladder = LaneResistance.derive(Fortification.Strength.PICKET);
        for (LaneResistance.Rung rung : ladder.rungs()) {
            assertEquals(Fortification.Strength.PICKET, rung.strength(),
                    "band " + rung.band() + " stepped below a picket");
        }
        LaneResistance fromGarrison = LaneResistance.derive(Fortification.Strength.GARRISON);
        assertEquals(Fortification.Strength.PICKET, fromGarrison.at(1).strength());
        assertEquals(Fortification.Strength.PICKET, fromGarrison.at(2).strength());
    }

    /** The shape is fixed even when the strengths are not: a strongpoint, then outposts. */
    @Test
    void theInnermostRungIsWhatATrackHasToStopFor() {
        LaneResistance ladder = LaneResistance.derive(Fortification.Strength.STRONGHOLD);
        assertEquals(LaneResistance.Kind.STRONGPOINT, ladder.at(1).kind());
        assertEquals(LaneResistance.Kind.OUTPOST, ladder.at(2).kind());
        assertEquals(LaneResistance.Kind.OUTPOST, ladder.at(3).kind());
    }

    /** A mission that wrote its own ladder down gets it, whatever the objective is. */
    @Test
    void aStatedLadderWinsOverTheDerivedOne() {
        LaneResistance feint = LaneResistance.stated(
                Fortification.Strength.PICKET, Fortification.Strength.PICKET,
                Fortification.Strength.PICKET);
        assertEquals(Fortification.Strength.PICKET, feint.at(1).strength(),
                "a lane left at pickets is a feint and the derivation does not get a vote");

        LaneResistance grind = LaneResistance.stated(List.of(
                new LaneResistance.Rung(1, Fortification.Strength.CITADEL,
                        LaneResistance.Kind.STRONGPOINT),
                new LaneResistance.Rung(2, Fortification.Strength.STRONGHOLD,
                        LaneResistance.Kind.STRONGPOINT)));
        assertEquals(Fortification.Strength.CITADEL, grind.at(1).strength());
        assertEquals(LaneResistance.Kind.STRONGPOINT, grind.at(2).kind());
        assertNull(grind.at(3), "a band left out is a rung this lane does not have");
    }

    /** Every rung knows what it owes and what it stands behind. */
    @Test
    void aRungCarriesBothItsProgramAndItsFortification() {
        LaneResistance.Rung rung = LaneResistance.derive(
                Fortification.Strength.CITADEL).at(1);
        assertNotNull(rung.program());
        assertEquals(0, rung.program().airfields());
        assertEquals(Fortification.STRONGHOLD, rung.fortification());
    }

    @Test
    void aLadderIsNamedInwardOutAndOnlyOnce() {
        assertThrows(IllegalArgumentException.class, () -> LaneResistance.stated(List.of(
                new LaneResistance.Rung(2, Fortification.Strength.PICKET,
                        LaneResistance.Kind.OUTPOST),
                new LaneResistance.Rung(1, Fortification.Strength.PICKET,
                        LaneResistance.Kind.OUTPOST))));
        assertThrows(IllegalArgumentException.class, () -> new LaneResistance.Rung(
                0, Fortification.Strength.PICKET, LaneResistance.Kind.OUTPOST));
        assertThrows(IllegalArgumentException.class, () -> new LaneResistance.Rung(
                4, Fortification.Strength.PICKET, LaneResistance.Kind.OUTPOST));
        assertThrows(IllegalArgumentException.class, () -> LaneResistance.stated(List.of()));
    }
}
