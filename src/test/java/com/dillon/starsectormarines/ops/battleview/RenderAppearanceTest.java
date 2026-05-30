package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.battleview.RenderAppearance.SpriteKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@link RenderAppearance} flyweight's capability tags against the sim
 * {@link UnitType} they derive from — the table replaces an
 * {@code instanceof}/{@code combatant}/{@code deathPoseIdx} ladder, so these
 * assert the derivation stays faithful as types are added.
 */
public class RenderAppearanceTest {

    @Test
    public void everyTypeHasADescriptorWithMatchingRenderScaleAndFrameLayout() {
        for (UnitType t : UnitType.values()) {
            RenderAppearance app = RenderAppearance.of(t);
            assertNotNull(app, "no descriptor for " + t);
            assertEquals(t.renderScale, app.renderScale, 0f, "renderScale for " + t);
            assertEquals(t.frameLayout, app.frameLayout, "frameLayout for " + t);
        }
    }

    @Test
    public void footprintTracksWholeSpriteAndEveryNonStructuralTypeIsSheet() {
        for (UnitType t : UnitType.values()) {
            RenderAppearance app = RenderAppearance.of(t);
            // Footprint pads are exactly the whole-sprite structures (turret + hub).
            assertEquals(app.spriteKind == SpriteKind.WHOLE_SPRITE, app.drawsFootprint,
                    "drawsFootprint should track WHOLE_SPRITE for " + t);
            // Everything outside the three structural/aerial types is sheet-drawn
            // infantry/civilians — guards against a future type misrouted into a
            // WHOLE_SPRITE/NONE arm.
            boolean structural = t == UnitType.TURRET
                    || t == UnitType.DRONE_HUB_STRUCTURE
                    || t == UnitType.DRONE;
            if (!structural) {
                assertEquals(SpriteKind.SHEET, app.spriteKind, "non-structural type should be SHEET: " + t);
                assertFalse(app.drawsFootprint, "non-structural type draws no footprint: " + t);
            }
        }
    }

    @Test
    public void hpBarFollowsCombatantExceptDrones() {
        for (UnitType t : UnitType.values()) {
            boolean expected = t.combatant && t != UnitType.DRONE;
            assertEquals(expected, RenderAppearance.of(t).drawsHpBar, "drawsHpBar for " + t);
        }
    }

    @Test
    public void deathPoseFollowsPresenceOfACorpseSheet() {
        for (UnitType t : UnitType.values()) {
            assertEquals(t.deadSpritePath != null, RenderAppearance.of(t).hasDeathPose,
                    "hasDeathPose for " + t);
        }
    }

    @Test
    public void turretsAndHubsAreWholeSpriteFootprintDrawers() {
        for (UnitType t : new UnitType[]{UnitType.TURRET, UnitType.DRONE_HUB_STRUCTURE}) {
            RenderAppearance app = RenderAppearance.of(t);
            assertEquals(SpriteKind.WHOLE_SPRITE, app.spriteKind, "spriteKind for " + t);
            assertTrue(app.drawsFootprint, "drawsFootprint for " + t);
            assertTrue(app.drawsHpBar, "drawsHpBar for " + t);
            assertFalse(app.hasDeathPose, "hasDeathPose for " + t);
        }
    }

    @Test
    public void dronesAreNotDrawnByTheUnitsSystem() {
        RenderAppearance app = RenderAppearance.of(UnitType.DRONE);
        assertEquals(SpriteKind.NONE, app.spriteKind);
        assertFalse(app.drawsHpBar, "drones bar themselves in the DRONES layer");
        assertFalse(app.drawsFootprint);
    }

    @Test
    public void infantryAreSheetDrawnHpBarredCorpses() {
        RenderAppearance marine = RenderAppearance.of(UnitType.MARINE);
        assertEquals(SpriteKind.SHEET, marine.spriteKind);
        assertFalse(marine.drawsFootprint);
        assertTrue(marine.drawsHpBar);
        assertTrue(marine.hasDeathPose);
        assertEquals(UnitType.FrameLayout.WNES_WEAPON_UP, marine.frameLayout);

        // The mech is sheet-drawn too but uses the 8-way layout and overhangs its cell.
        RenderAppearance mech = RenderAppearance.of(UnitType.HEAVY_MECH);
        assertEquals(SpriteKind.SHEET, mech.spriteKind);
        assertEquals(UnitType.FrameLayout.EIGHT_WAY_NO_WEAPON_UP, mech.frameLayout);
        assertTrue(mech.renderScale > 1f, "mech overhangs its cell");
    }

    @Test
    public void noncombatantCiviliansHaveNoBarAndNoCorpse() {
        for (UnitType t : new UnitType[]{UnitType.CIVILIAN, UnitType.ENGINEER, UnitType.SCIENTIST}) {
            RenderAppearance app = RenderAppearance.of(t);
            assertEquals(SpriteKind.SHEET, app.spriteKind, "spriteKind for " + t);
            assertFalse(app.drawsHpBar, "drawsHpBar for " + t);
            assertFalse(app.hasDeathPose, "hasDeathPose for " + t);
        }
    }
}
