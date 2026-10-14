package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The read view a display takes of a pool: what it holds, when the next airframe is due, and that it only reads. */
class AirPoolViewTest {

    private static AirPoolService pool(int bombers, int cap, float regen) {
        return new AirPoolService(AirPoolDeclaration.NONE.with(Faction.MARINE,
                AirPoolAllowance.of(new AirPoolStock(bombers, cap, regen), new AirPoolStock(1, 1, regen), false)));
    }

    @Test
    void aSideWithNoPoolGetsTheEmptyView() {
        assertSame(AirPoolView.NONE, AirPoolService.NONE.view(Faction.MARINE, 0));
        assertSame(AirPoolView.NONE, pool(1, 1, 5f).view(Faction.DEFENDER, 0));
        assertFalse(AirPoolView.NONE.hasPool());
    }

    @Test
    void theViewReadsEachDeclaredKindInOrder() {
        AirPoolView view = pool(2, 3, 30f).view(Faction.MARINE, 0);
        assertTrue(view.hasPool());
        assertEquals(2, view.kinds().size());
        assertEquals(AirPoolKind.BOMBER, view.kinds().get(0).kind());
        assertEquals(2, view.kinds().get(0).available());
        assertEquals(3, view.kinds().get(0).cap());
        assertEquals(0, view.kinds().get(0).outFlying());
        assertTrue(view.sorties().isEmpty());
        assertEquals(0, view.callsGoingOut());
        assertEquals(0, view.recentLosses());
    }

    @Test
    void theNextAirframeIsDueOnlyWhileBelowTheEstablishmentAndReplacing() {
        assertEquals(30f, pool(2, 3, 30f).view(Faction.MARINE, 0).kinds().get(0).nextAirframeSeconds(), 0.001f);
        assertTrue(pool(3, 3, 30f).view(Faction.MARINE, 0).kinds().get(0).nextAirframeSeconds() < 0f,
                "at establishment nothing is being replaced");
        assertTrue(pool(1, 3, 0f).view(Faction.MARINE, 0).kinds().get(0).nextAirframeSeconds() < 0f,
                "a side that never replaces has no clock");
    }
}
