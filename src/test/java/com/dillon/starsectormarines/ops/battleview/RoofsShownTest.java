package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoofsShownTest {

    /**
     * A roof lifted about the view's centre stands out over the street on its far side: a point there shows the roof,
     * and a point the lift has drawn the roof back from, at the building's near edge, shows none.
     */
    @Test
    void aLiftedRoofIsShownOverTheStreetBesideIt() {
        int w = 40, h = 10;
        byte[] cells = new byte[w * h];
        int c = BuildingFaces.TWO;
        // A building on cells x 20..29, rows 3..6, its roof of class TWO.
        for (int y = 3; y <= 6; y++) for (int x = 20; x <= 29; x++) cells[x + y * w] = (byte) (c + 1);
        float[] lifts = new float[RoofsShown.CLASSES];
        Arrays.fill(lifts, 1f);
        lifts[c] = 1.1f;
        RoofsShown roofs = new RoofsShown();
        roofs.set(cells, w, h);
        roofs.view(0f, 5f, lifts);

        assertEquals(c, roofs.at(25.5f, 5.5f), "over the roof's middle");
        assertEquals(c, roofs.at(31.5f, 5.5f), "the street just past its far wall, under the lifted roof");
        assertEquals(-1, roofs.at(20.5f, 5.5f), "its near edge, the roof drawn back from it");
        assertEquals(-1, roofs.at(36.5f, 5.5f), "the street well past it");

        lifts[c] = 1f;
        roofs.view(0f, 5f, lifts);
        assertEquals(-1, roofs.at(31.5f, 5.5f), "flat, a roof lies over its own cells only");
        assertEquals(c, roofs.at(20.5f, 5.5f));
    }
}
