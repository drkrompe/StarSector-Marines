package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DamageDollCanvasTest {

    @Test
    void svgPartsAreReadFromPartGroups() {
        SvgAsset asset = SvgAsset.parse("""
                <svg viewBox="0 0 10 10">
                  <g id="part-TURRET" fill="currentColor"><rect width="4" height="4"/></g>
                  <g id="part-HULL" fill="currentColor" stroke="#ffffff"><rect x="5" width="4" height="4"/></g>
                  <g id="detail"><rect y="5" width="4" height="4"/></g>
                </svg>
                """);
        assertEquals(List.of("TURRET", "HULL"), asset.parts());
    }

    @Test
    void colourWalksFromWholeThroughWornArmorToStructureAndDestroyed() {
        assertNull(DamageDollCanvas.color(false, false, true, 1f, 1f));
        assertEquals(DamageDollCanvas.INTACT, DamageDollCanvas.color(true, false, true, 1f, 1f));
        assertEquals(DamageDollCanvas.ARMOR_LOW, DamageDollCanvas.color(true, false, true, 0.01f, 1f));
        assertEquals(DamageDollCanvas.STRUCTURE_HIGH, DamageDollCanvas.color(true, false, true, 0f, 1f));
        assertEquals(DamageDollCanvas.STRUCTURE_LOW, DamageDollCanvas.color(true, false, true, 0f, 0f));
        assertEquals(DamageDollCanvas.DESTROYED, DamageDollCanvas.color(true, true, true, 0f, 0f));
        // A structure-only location reads whole as green, half as yellow.
        assertEquals(DamageDollCanvas.INTACT, DamageDollCanvas.color(true, false, false, 0f, 1f));
        assertEquals(DamageDollCanvas.ARMOR_LOW, DamageDollCanvas.color(true, false, false, 0f, .5f));
    }

    @Test
    void aMarinesLimbReadsItsWoundsFromOrangeToRedAtTheFloor() {
        assertNull(MarineDollCanvas.woundColor(0));
        assertEquals(DamageDollCanvas.STRUCTURE_HIGH, MarineDollCanvas.woundColor(1));
        assertEquals(DamageDollCanvas.STRUCTURE_LOW, MarineDollCanvas.woundColor(4), "four wounds reach the floor");
    }

    @Test
    void partNamesAreLocations() {
        assertEquals(DollLocation.TORSO_REAR, DollLocation.ofPart("TORSO_REAR"));
        assertNull(DollLocation.ofPart("WING"));
    }
}
