package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RenderPositionService} — the entity-id-keyed render
 * position store decomposed out of {@link UnitRegistry}. Covers the float
 * paired/single-axis API, absent-entity reads, presence, and that entries
 * persist (the "survives release" property the corpse relies on is just "we
 * never auto-remove").
 */
public class RenderPositionServiceTest {

    @Test
    public void absentEntityReadsZeroAndHasNothing() {
        RenderPositionService s = new RenderPositionService();
        assertEquals(0, s.size());
        assertFalse(s.has(42L));
        assertEquals(0f, s.getX(42L), 1e-6f);
        assertEquals(0f, s.getY(42L), 1e-6f);
    }

    @Test
    public void setThenGetBothAxes() {
        RenderPositionService s = new RenderPositionService();
        s.set(42L, 3.5f, 7.25f);
        assertTrue(s.has(42L));
        assertEquals(3.5f, s.getX(42L), 1e-6f);
        assertEquals(7.25f, s.getY(42L), 1e-6f);
        assertEquals(1, s.size());
    }

    @Test
    public void setMutatesInPlaceWithoutGrowing() {
        RenderPositionService s = new RenderPositionService();
        s.set(42L, 1f, 2f);
        s.set(42L, 9f, 8f);
        assertEquals(9f, s.getX(42L), 1e-6f);
        assertEquals(8f, s.getY(42L), 1e-6f);
        assertEquals(1, s.size(), "re-setting the same entity does not grow the store");
    }

    @Test
    public void singleAxisSettersPreserveTheOtherAxis() {
        RenderPositionService s = new RenderPositionService();
        s.set(42L, 4f, 5f);
        s.setX(42L, 40f);
        assertEquals(40f, s.getX(42L), 1e-6f);
        assertEquals(5f, s.getY(42L), 1e-6f);
        s.setY(42L, 50f);
        assertEquals(40f, s.getX(42L), 1e-6f);
        assertEquals(50f, s.getY(42L), 1e-6f);
    }

    @Test
    public void singleAxisSetterOnAbsentEntitySeedsOtherAxisToZero() {
        RenderPositionService s = new RenderPositionService();
        s.setX(42L, 4f);
        assertEquals(4f, s.getX(42L), 1e-6f);
        assertEquals(0f, s.getY(42L), 1e-6f);
    }

    @Test
    public void removeDetaches() {
        RenderPositionService s = new RenderPositionService();
        s.set(42L, 1f, 2f);
        s.remove(42L);
        assertFalse(s.has(42L));
        assertEquals(0, s.size());
    }

    @Test
    public void entriesPersistUntilExplicitlyRemoved() {
        // The corpse relies on this: nothing auto-evicts a dead entity's render
        // position, so a released id still resolves where it fell.
        RenderPositionService s = new RenderPositionService();
        s.set(1L, 5f, 6f);
        s.set(2L, 7f, 8f);
        // No release / decay path touches the store.
        assertEquals(2, s.size());
        assertEquals(5f, s.getX(1L), 1e-6f);
        assertEquals(8f, s.getY(2L), 1e-6f);
    }
}
