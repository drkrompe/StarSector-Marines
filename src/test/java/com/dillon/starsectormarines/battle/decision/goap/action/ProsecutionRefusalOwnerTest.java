package com.dillon.starsectormarines.battle.decision.goap.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whose search an empty firing-position answer is: prosecution's whenever prosecution chose the anchor. */
class ProsecutionRefusalOwnerTest {

    @Test
    void prosecutionOwnsTheRefusalEvenWhileARouteThreatCommitIsAlsoStanding() {
        // Prosecution replaces the threat and the anchor, so the empty search was its own. The route commit being set
        // as well used to turn the refusal into "plant", which froze a squad beyond leash plus reach indefinitely.
        assertTrue(AbstractZoneAction.refusalBelongsToProsecution(true, false));
    }

    @Test
    void aHardHoldStillPlants() {
        assertFalse(AbstractZoneAction.refusalBelongsToProsecution(true, true));
    }

    @Test
    void noProsecutionMeansTheRefusalIsNotProsecutions() {
        assertFalse(AbstractZoneAction.refusalBelongsToProsecution(false, false));
        assertFalse(AbstractZoneAction.refusalBelongsToProsecution(false, true));
    }
}
