package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofCover;
import com.dillon.starsectormarines.battle.world.model.RoofDamage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which frames of the roof sheet a cell draws, cut to what shape, and that the field repeats without a seam. */
class RoofTilesTest {

    private static final float D = RoofCover.EAVE_DEPTH;

    /** A walled 3x3 room: floor x 2..4, y 2..4 on a 7x7 map, walls round it. */
    private static CellTopology room() {
        CellTopology t = new CellTopology(7, 7);
        for (int y = 1; y <= 5; y++) for (int x = 1; x <= 5; x++) {
            if (x == 1 || x == 5 || y == 1 || y == 5) t.setWall(x, y, true);
            else t.setBuildingId(x, y, 1);
        }
        return t;
    }

    private record Quad(int column, int row, int turns, float x0, float y0, float x1, float y1) { }

    private static List<Quad> resolve(CellTopology t, int x, int y, RoofStyle style, int grain) {
        List<Quad> out = new ArrayList<>();
        RoofTiles.resolve(t, x, y, style, grain, (c, r, k, x0, y0, x1, y1) -> out.add(new Quad(c, r, k, x0, y0, x1, y1)));
        return out;
    }

    @Test
    void anIntactFloorDrawsItsFieldOverTheWholeCellInItsStylesRow() {
        List<Quad> quads = resolve(room(), 3, 3, RoofStyle.CORRUGATED, 0);
        assertEquals(List.of(new Quad(RoofTiles.FIELD + 1 + 2, RoofStyle.CORRUGATED.ordinal(), 0, 0f, 0f, 1f, 1f)),
                quads);
    }

    @Test
    void anIntactEaveIsTheFieldCutShortWithItsTrimTurnedToFaceOutside() {
        List<Quad> quads = resolve(room(), 3, 5, RoofStyle.CLAY, 0);
        assertEquals(2, quads.size());
        Quad field = quads.get(0);
        assertEquals(new Quad(RoofTiles.fieldColumn(3, 5, 0), 0, 0, 0f, 0f, 1f, D), field);
        assertEquals(new Quad(RoofTiles.TRIM_EAVE, 0, 0, 0f, 0f, 1f, 1f), quads.get(1));
        // The east wall's trim is the same north-facing frame a quarter turn round.
        assertEquals(1, resolve(room(), 5, 3, RoofStyle.CLAY, 0).get(1).turns());
    }

    @Test
    void aBrokenRoofDrawsWhatBrokeItCutToTheSameShapeWithNoTrim() {
        CellTopology t = room();
        t.setRoofDamage(3, 5, RoofDamage.BURNT);
        List<Quad> eave = resolve(t, 3, 5, RoofStyle.SLAB, 0);
        assertEquals(1, eave.size(), "a burnt eave has lost its edge");
        assertEquals(RoofTiles.BURNT, eave.get(0).column());
        assertEquals(D, eave.get(0).y1(), 1e-6f);

        t.setRoofDestroyed(3, 3, true);
        t.setRoofDamage(3, 3, RoofDamage.HOLED);
        assertEquals(RoofTiles.HOLED, resolve(t, 3, 3, RoofStyle.SLAB, 0).get(0).column());
        t.setRoofDamage(3, 3, RoofDamage.COLLAPSED);
        Quad collapsed = resolve(t, 3, 3, RoofStyle.SLAB, 1).get(0);
        assertEquals(RoofTiles.COLLAPSED, collapsed.column());
        assertEquals(1, collapsed.turns() & 1, "a remnant keeps the roof's grain");
    }

    @Test
    void aBlastReadsAsOneOpeningNotAHoleInEveryCell() {
        CellTopology t = room();
        for (int y = 2; y <= 4; y++) {
            for (int x = 2; x <= 3; x++) {
                t.setRoofDestroyed(x, y, true);
                t.setRoofDamage(x, y, RoofDamage.HOLED);
            }
        }
        // The middle of the patch is open: broken roof north and south, wall-top eaves east and west are intact.
        List<Quad> middle = resolve(t, 2, 3, RoofStyle.CLAY, 0);
        assertEquals(1, middle.size());
        assertEquals(RoofTiles.COLLAPSED, middle.get(0).column(), "torn roof hangs from the intact eave beside it");
        assertEquals(3, middle.get(0).turns(), "turned to the west, where the intact roof is");
        // A cell with intact roof on its east side hangs its remnant from there.
        Quad edge = resolve(t, 3, 3, RoofStyle.CLAY, 0).get(0);
        assertEquals(RoofTiles.COLLAPSED, edge.column());
        // With every neighbour broken there is nothing left to hang from.
        t.setRoofDamage(1, 3, RoofDamage.HOLED);
        assertEquals(List.of(), resolve(t, 2, 3, RoofStyle.CLAY, 0));
        // A fire's cells keep their rafters wherever they are.
        t.setRoofDamage(2, 3, RoofDamage.BURNT);
        assertEquals(RoofTiles.BURNT, resolve(t, 2, 3, RoofStyle.CLAY, 0).get(0).column());
    }

    @Test
    void theFieldRepeatsWithoutASeamWhicheverWayTheGrainRuns() {
        for (int grain = 0; grain < 2; grain++) {
            for (int y = -3; y < 3; y++) {
                for (int x = -3; x < 3; x++) {
                    // The point on a cell's east edge is the same point of the swatch as the one on the west edge of
                    // the cell east of it, and likewise north.
                    for (float t : new float[]{0.2f, 0.7f}) {
                        assertSameSwatchPoint(swatch(x, y, grain, 1f, t), swatch(x + 1, y, grain, 0f, t));
                        assertSameSwatchPoint(swatch(x, y, grain, t, 1f), swatch(x, y + 1, grain, t, 0f));
                    }
                }
            }
        }
    }

    @Test
    void aTurnedFrameCarriesItsCornersRoundClockwise() {
        // A north-facing frame's top-left corner (0, 1) lands top-right after one clockwise turn.
        assertEquals(0f, RoofTiles.frameU(1, 1f, 1f), 1e-6f);
        assertEquals(1f, RoofTiles.frameV(1, 1f, 1f), 1e-6f);
        for (int k = 0; k < 4; k++) {
            assertEquals(RoofTiles.frameU(k, 0.3f, 0.8f),
                    RoofTiles.frameU(k + 4, 0.3f, 0.8f), 1e-6f);
        }
    }

    @Test
    void aRoofFollowsTheBuildingUnderIt() {
        assertEquals(RoofStyle.SLAB, RoofStyle.ofWorks(EmplacementStyle.BRUTALIST));
        assertEquals(RoofStyle.PLATE, RoofStyle.ofWorks(EmplacementStyle.MODULAR));
        assertEquals(RoofStyle.CORRUGATED, RoofStyle.ofWorks(EmplacementStyle.EARTHWORK));
        assertEquals(RoofStyle.CLAY, RoofStyle.ofWallSurface("colony.masonry-wall"));
        assertEquals(RoofStyle.CORRUGATED, RoofStyle.ofWallSurface("colony.olive-wall"));
        assertEquals(null, RoofStyle.ofWallSurface("floors.dirt"));
    }

    /** The swatch point, mod 2, that cell {@code (x, y)} draws at its local point {@code (px, py)}. */
    private static float[] swatch(int x, int y, int grain, float px, float py) {
        int column = RoofTiles.fieldColumn(x, y, grain) - RoofTiles.FIELD;
        float u = (column & 1) + RoofTiles.frameU(grain, px, py);
        float v = (column >> 1) + RoofTiles.frameV(grain, px, py);
        return new float[]{u % 2f, v % 2f};
    }

    private static void assertSameSwatchPoint(float[] a, float[] b) {
        assertEquals(0f, wrapDistance(a[0], b[0]), 1e-5f);
        assertEquals(0f, wrapDistance(a[1], b[1]), 1e-5f);
    }

    private static float wrapDistance(float a, float b) {
        float d = Math.abs(a - b) % 2f;
        return Math.min(d, 2f - d);
    }
}
