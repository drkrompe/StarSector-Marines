package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.modifier.UnitStat;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaggerServiceTest {

    @Test
    void aHoldRunsItsTicksAndALongerOneIsKept() {
        AtomicInteger tick = new AtomicInteger(10);
        StaggerService stagger = new StaggerService();
        stagger.setClock(tick::get);
        StaggerModifier modifier = new StaggerModifier(stagger);

        assertFalse(stagger.isStaggered(7L));
        stagger.stagger(7L, 30);
        assertTrue(stagger.isStaggered(7L));
        assertTrue(modifier.appliesTo(7L));
        assertEquals(0f, modifier.multiplier(7L, UnitStat.MOVE_SPEED));

        tick.set(20);
        stagger.stagger(7L, 5); // shorter than what is left: kept at 40
        assertEquals(20, stagger.ticksLeft(7L));
        assertEquals(10, stagger.sinceTick(7L));

        tick.set(40);
        assertFalse(stagger.isStaggered(7L));
        assertFalse(modifier.appliesTo(7L));
        stagger.stagger(7L, 3);
        assertEquals(40, stagger.sinceTick(7L), "a new hold starts its own clock");
    }
}
