package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderCuesTest {

    private static final Faction SIDE = Faction.values()[0];

    @Test
    void flightIsSoundOnlyAndRetainsItsDroneAndBay() {
        TenderCues cues = new TenderCues();
        cues.flight(TenderCues.Kind.LAUNCH, SIDE, 2f, 3f, 9L, 100);
        cues.flight(TenderCues.Kind.DOCK, SIDE, 4f, 5f, 9L, 200);
        assertEquals(2, cues.cuesThisFrame().size());
        assertEquals(9L, cues.cuesThisFrame().get(0).id());
        assertEquals(4f, cues.cuesThisFrame().get(1).x());
        assertTrue(cues.recent().isEmpty(), "flight must not become a completion flourish");
        cues.beginFrame();
        assertTrue(cues.cuesThisFrame().isEmpty());
    }

    @Test
    void aWeldIsHeardOncePerGapNoMatterHowManyTorchesAsk() {
        TenderCues cues = new TenderCues();
        ConstructionOrder o = new ConstructionOrder(BuildOption.VULCAN_TURRET, 5.5f, 5.5f);
        assertTrue(cues.weld(o, SIDE, 100));
        assertFalse(cues.weld(o, SIDE, 100), "a second builder on the same tick adds nothing");
        assertFalse(cues.weld(o, SIDE, 100 + TenderCues.WELD_GAP_TICKS - 1));
        assertTrue(cues.weld(o, SIDE, 100 + TenderCues.WELD_GAP_TICKS));
        assertEquals(2, cues.cuesThisFrame().size());
    }

    @Test
    void eachSiteHasItsOwnWeldClock() {
        TenderCues cues = new TenderCues();
        ConstructionOrder a = new ConstructionOrder(BuildOption.VULCAN_TURRET, 5.5f, 5.5f);
        ConstructionOrder b = new ConstructionOrder(BuildOption.VULCAN_TURRET, 20.5f, 5.5f);
        assertTrue(cues.weld(a, SIDE, 10));
        assertTrue(cues.weld(b, SIDE, 10));
    }

    @Test
    void weldDueIsTrueForNoPriorOrAClockThatWentBackwards() {
        assertTrue(TenderCues.weldDue(null, 0));
        assertTrue(TenderCues.weldDue(500, 3));
        assertFalse(TenderCues.weldDue(10, 11));
    }

    @Test
    void soundRequestsLastOneFrameAndVisualEventsLastTheirWindow() {
        TenderCues cues = new TenderCues();
        cues.stun(SIDE, 1f, 2f, 7L, 100);
        cues.alert(SIDE, 3f, 4f, 100);
        cues.complete(SIDE, 3f, 4f, 100);
        assertEquals(3, cues.cuesThisFrame().size());
        assertEquals(2, cues.recent().size(), "an alert is a sound only");
        cues.beginFrame();
        assertTrue(cues.cuesThisFrame().isEmpty());
        cues.prune(100 + TenderCues.EFFECT_TICKS - 1);
        assertEquals(2, cues.recent().size());
        cues.prune(100 + TenderCues.EFFECT_TICKS);
        assertTrue(cues.recent().isEmpty());
    }
}
