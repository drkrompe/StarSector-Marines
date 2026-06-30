package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract for {@link VisionService} — the per-component data owner for the
 * universal {@code VISION} sight stats (visionRange + airLosRadius) in the
 * archetype {@code EntityWorld}. Mirrors {@link WorldTest}: {@code allocate} seeds
 * the columns from the unit's write-only {@code seed*} fields, the by-id accessors
 * read/write that one world slot every reader sees, and VISION is live-only —
 * removed by the corpse transmute, so the accessors are fail-loud on a corpse.
 */
public class VisionServiceTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static Entity unit(String label) {
        return new Entity(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    @Test
    public void allocateSeedsTheVisionColumnsFromTheUnitSeeds() {
        UnitRosterService r = roster();
        Entity u = unit("u");
        long id = r.allocate(u);
        VisionService vision = r.vision();

        assertTrue(vision.has(id));
        // Seeded from the unit's write-only seed* fields (no Entity deref afterward).
        assertEquals(u.seedVisionRange, vision.visionRange(id), 1e-6f);
        assertEquals(u.seedAirLosRadius, vision.airLosRadius(id), 1e-6f);
    }

    @Test
    public void mutatorsHitTheSharedWorldSlot() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("u"));
        VisionService vision = r.vision();

        // The night-multiplier seam: setVisionRange writes the same column the
        // shadowcast reads back.
        vision.setVisionRange(id, 12.5f);
        assertEquals(12.5f, vision.visionRange(id), 1e-6f);

        vision.setAirLosRadius(id, 3f);
        assertEquals(3f, vision.airLosRadius(id), 1e-6f);
    }

    @Test
    public void isFailLoudOnceVisionIsGoneOrTheIdIsUnknown() {
        UnitRosterService r = roster();
        long id = r.allocate(unit("u"));
        VisionService vision = r.vision();

        // The corpse transmute removes VISION (a corpse does not see) — reads are
        // fail-loud from then on, as is any never-allocated id.
        r.entityWorld().removeComponent(id, r.components().VISION);
        assertFalse(vision.has(id));
        assertThrows(IllegalArgumentException.class, () -> vision.visionRange(id));   // corpse
        assertThrows(IllegalArgumentException.class, () -> vision.airLosRadius(999L)); // never allocated
    }
}
