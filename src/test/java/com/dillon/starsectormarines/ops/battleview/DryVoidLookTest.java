package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DryVoidLookTest {

    static NaturalVoid drop(NaturalVoid.Style style) {
        return new NaturalVoid(style, List.of(new NaturalVoid.Step(20f, 1f)), 120f, null, 10f, 1);
    }

    @Test
    void aDryStyleHasItsPaletteAndADropWithWaterOrIceHasNone() {
        assertSame(DryVoidLook.CANYON, DryVoidLook.of(drop(NaturalVoid.Style.CANYON)));
        assertSame(DryVoidLook.REGOLITH, DryVoidLook.of(drop(NaturalVoid.Style.DRY_GORGE)));
        assertSame(DryVoidLook.TOXIC, DryVoidLook.of(drop(NaturalVoid.Style.TOXIC)));
        assertNull(DryVoidLook.of(drop(NaturalVoid.Style.GORGE_RIVER)));
        assertNull(DryVoidLook.of(drop(NaturalVoid.Style.ICE)));
    }

    @Test
    void backFromTheDropTheLipIsThePlainUnderTheBattlesOwnSun() {
        float[] rgb = new float[3];
        // In the field's shade, with its noise: back from the edge none of it shows, so no seam meets the tiles.
        for (DryVoidLook.Palette p : new DryVoidLook.Palette[]{DryVoidLook.CANYON, DryVoidLook.REGOLITH, DryVoidLook.TOXIC}) {
            DryVoidLook.rock(p, 0, .7f, -.8f, .4f, DryVoidLook.PLAIN_CELLS, rgb);
            assertEquals(p.plain().r(), rgb[0], 1e-5f);
            assertEquals(p.plain().g(), rgb[1], 1e-5f);
            assertEquals(p.plain().b(), rgb[2], 1e-5f);
        }
    }

    @Test
    void talusLiesAgainstTheWallsAndTheBedRunsBeyondIt() {
        assertEquals(1f, DryVoidLook.talus(0f), 1e-6f);
        assertEquals(0f, DryVoidLook.talus(DryVoidLook.TALUS_CELLS), 1e-6f);
        assertEquals(0f, DryVoidLook.drift(0f), 1e-6f);
        assertEquals(1f, DryVoidLook.drift(DryVoidLook.DRIFT_FULL), 1e-6f);
    }

    @Test
    void noStoneLiesInAChannelOrInTheTalusAgainstAWall() {
        int lies = 0;
        assertTrue(DryVoidFloor.stoneAt(lies, .3f, 3f, 0f));
        assertFalse(DryVoidFloor.stoneAt(lies, .3f, 3f, 1f), "in a channel");
        assertFalse(DryVoidFloor.stoneAt(lies, .3f, .5f, 0f), "against the wall");
        assertFalse(DryVoidFloor.stoneAt(1023, .3f, 3f, 0f), "a grid point the share leaves empty");
    }

    @Test
    void sludgeDriesToCrustAgainstTheWallsAndBubblesOnlyOutInIt() {
        assertEquals(1f, ToxicVoidFloor.crust(0f), 1e-6f);
        assertEquals(0f, ToxicVoidFloor.crust(ToxicVoidFloor.CRUST_CELLS), 1e-6f);
        float[] rgb = new float[3];
        ToxicVoidFloor.sludge(0f, .9f, 1f, rgb);
        assertEquals(ToxicVoidFloor.CRUST.r(), rgb[0], 1e-6f, "dry crust at the wall's foot, no scum on it");
        assertTrue(ToxicVoidFloor.bubbleAt(0, 3f));
        assertFalse(ToxicVoidFloor.bubbleAt(0, .2f), "no bubble in the crust");
    }
}
