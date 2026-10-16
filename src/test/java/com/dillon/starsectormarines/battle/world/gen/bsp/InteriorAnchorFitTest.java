package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.InteriorAnchorFitStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fitting that keeps an objective's interior anchor standable, asked of the
 * fitting on a hand-laid grid.
 *
 * <p>That the guarantee survives the whole generation pipeline is a property of
 * finished maps and lives in {@code InteriorAnchorFitEvidence}; what each rule of
 * the fitting does is asked here.
 */
class InteriorAnchorFitTest {

    private static final int SIZE = 16;

    /** A grid of open floor, so each case blocks only the cells it is about. */
    private static NavigationGrid openFloor() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    /** A footprint 4,4..10,10 with the exterior anchor outside it at 2,2. */
    private static PointOfInterest poi(int interiorX, int interiorY) {
        return new PointOfInterest(PointOfInterest.Kind.DEPOT, 4, 4, 10, 10,
                2, 2, interiorX, interiorY);
    }

    private static List<PointOfInterest> listOf(PointOfInterest poi) {
        List<PointOfInterest> pois = new ArrayList<>();
        pois.add(poi);
        return pois;
    }

    @Test
    void anAnchorThatStillStandsIsLeftAlone() {
        PointOfInterest original = poi(7, 7);
        List<PointOfInterest> pois = listOf(original);

        InteriorAnchorFit.apply(pois, openFloor());

        assertSame(original, pois.get(0), "a good anchor was rewritten");
    }

    @Test
    void aBlockedAnchorMovesToTheNearestStandableCellOfItsFootprint() {
        NavigationGrid grid = openFloor();
        grid.setWalkable(7, 7, false);
        List<PointOfInterest> pois = listOf(poi(7, 7));

        InteriorAnchorFit.apply(pois, grid);

        PointOfInterest fitted = pois.get(0);
        int distance = Math.abs(fitted.interiorAnchorX - 7) + Math.abs(fitted.interiorAnchorY - 7);
        assertEquals(1, distance, "the anchor did not take the nearest open cell");
        assertTrue(grid.isWalkable(fitted.interiorAnchorX, fitted.interiorAnchorY));
        assertEquals(2, fitted.anchorCellX, "the exterior anchor must not move");
        assertEquals(2, fitted.anchorCellY, "the exterior anchor must not move");
    }

    /** A doorway is walkable but belongs to no zone, so it is no better than a wall to an objective. */
    @Test
    void aDoorwayIsNotAStandableAnchor() {
        NavigationGrid grid = openFloor();
        grid.setDoorway(7, 7, true);
        List<PointOfInterest> pois = listOf(poi(7, 7));

        InteriorAnchorFit.apply(pois, grid);

        PointOfInterest fitted = pois.get(0);
        assertTrue(!(fitted.interiorAnchorX == 7 && fitted.interiorAnchorY == 7),
                "the anchor stayed on a doorway");
        assertTrue(!grid.isDoorway(fitted.interiorAnchorX, fitted.interiorAnchorY));
    }

    /** The search starts at the old anchor, so a repair stays in the chamber the filler chose. */
    @Test
    void aRepairedAnchorStaysNearWhereTheFillerPutIt() {
        NavigationGrid grid = openFloor();
        // Only a pocket at the north-west corner of the footprint and one at the
        // south-east corner are open; the anchor sat beside the north-west one.
        for (int y = 4; y <= 10; y++) {
            for (int x = 4; x <= 10; x++) grid.setWalkable(x, y, false);
        }
        grid.setWalkable(4, 4, true);
        grid.setWalkable(10, 10, true);
        List<PointOfInterest> pois = listOf(poi(5, 5));

        InteriorAnchorFit.apply(pois, grid);

        assertEquals(4, pois.get(0).interiorAnchorX);
        assertEquals(4, pois.get(0).interiorAnchorY);
    }

    @Test
    void aFootprintWithNoOpenInteriorFallsBackToTheExteriorAnchor() {
        NavigationGrid grid = openFloor();
        for (int y = 4; y <= 10; y++) {
            for (int x = 4; x <= 10; x++) grid.setWalkable(x, y, false);
        }
        List<PointOfInterest> pois = listOf(poi(7, 7));

        InteriorAnchorFit.apply(pois, grid);

        assertEquals(2, pois.get(0).interiorAnchorX);
        assertEquals(2, pois.get(0).interiorAnchorY);
    }

    @Test
    void aFootprintOffTheGridFallsBackAndAnOverhangIsClamped() {
        NavigationGrid grid = openFloor();
        PointOfInterest off = new PointOfInterest(PointOfInterest.Kind.DEPOT,
                SIZE + 3, SIZE + 3, SIZE + 8, SIZE + 8, 1, 1, SIZE + 4, SIZE + 4);
        // Overhangs the south-east edge; its anchor is blocked, so the fit must
        // search only the cells that exist.
        PointOfInterest overhang = new PointOfInterest(PointOfInterest.Kind.DEPOT,
                SIZE - 3, SIZE - 3, SIZE + 4, SIZE + 4, 1, 1, SIZE - 2, SIZE - 2);
        grid.setWalkable(SIZE - 2, SIZE - 2, false);
        List<PointOfInterest> pois = new ArrayList<>(List.of(off, overhang));

        InteriorAnchorFit.apply(pois, grid);

        assertEquals(1, pois.get(0).interiorAnchorX, "an off-grid footprint keeps its exterior anchor");
        assertTrue(grid.inBounds(pois.get(1).interiorAnchorX, pois.get(1).interiorAnchorY));
        assertTrue(grid.isWalkable(pois.get(1).interiorAnchorX, pois.get(1).interiorAnchorY));
    }

    @Test
    void nullEntriesAreSkipped() {
        List<PointOfInterest> pois = new ArrayList<>();
        pois.add(null);
        InteriorAnchorFit.apply(pois, openFloor());
        assertEquals(1, pois.size());
    }

    /** The stage is the whole-map pass: it fits what the context's finished grid says. */
    @Test
    void theStageFitsTheContextsPointsAgainstItsGrid() {
        NavigationGrid grid = openFloor();
        grid.setWalkable(7, 7, false);
        GenContext context = new GenContext(grid, new CellTopology(SIZE, SIZE),
                new Random(1L), SIZE, SIZE, 1L);
        context.pois.add(poi(7, 7));

        new InteriorAnchorFitStage().run(context);

        PointOfInterest fitted = context.pois.get(0);
        assertTrue(grid.isWalkable(fitted.interiorAnchorX, fitted.interiorAnchorY));
    }
}
