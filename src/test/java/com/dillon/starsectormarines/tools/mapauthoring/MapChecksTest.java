package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Crossing;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.CrossingKind;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Drop;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Place;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Program;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Pt;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Role;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Settlement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Maps page's checks before save, on a 24 by 20 field cut across by one drop. */
class MapChecksTest {

    private static final int W = 24, H = 20;

    /** A drop across the field from west to east along y 10, 4 cells wide, with the given crossings. */
    private static MapSketch field(List<Crossing> crossings, List<Settlement> settlements) {
        Drop drop = new Drop("gorge", List.of(new Pt(0, 10), new Pt(24, 10)), 4, List.of(30f, 30f), 3, crossings);
        return new MapSketch("test", List.of(drop), List.of(), List.of(),
                List.of(new Place("lz", "Landing", 3, 3, Role.LANDING), new Place("site", "Far site", 20, 17, Role.SITE)),
                settlements);
    }

    /** An open grid with the sketch's drop cells closed, as the applier lays them. */
    private static NavigationGrid grid(SketchRaster.Cut cut) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            grid.setWalkableFloor(x, y);
            if (cut.drop()[y * W + x] || cut.rise()[y * W + x]) grid.setWalkable(x, y, false);
        }
        return grid;
    }

    @Test
    void aDropWithNoCrossingStrandsTheFarSite() {
        MapSketch sketch = field(List.of(), List.of());
        SketchRaster.Cut cut = SketchRaster.cut(W, H, sketch);
        List<String> problems = MapChecks.run(grid(cut), cut, null, sketch);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).startsWith("Far site"), problems.toString());
    }

    @Test
    void aBridgeJoinsTheBanksAndReachesTheSite() {
        MapSketch sketch = field(List.of(new Crossing(.5f, 4, CrossingKind.BRIDGE)), List.of());
        SketchRaster.Cut cut = SketchRaster.cut(W, H, sketch);
        assertEquals(List.of(), MapChecks.run(grid(cut), cut, null, sketch));
    }

    @Test
    void aCrossingOverGroundThatCannotBeWalkedFails() {
        MapSketch sketch = field(List.of(new Crossing(.5f, 4, CrossingKind.LAND_BRIDGE)), List.of());
        SketchRaster.Cut cut = SketchRaster.cut(W, H, sketch);
        NavigationGrid grid = grid(cut);
        grid.setWalkable(12, 10, false);
        List<String> problems = MapChecks.run(grid, cut, null, sketch);
        assertTrue(problems.stream().anyMatch(p -> p.contains("not walkable at 12,10")), problems.toString());
    }

    @Test
    void aSettlementOnACrossingIsBuiltOnAWay() {
        Settlement town = new Settlement("town", 10, 6, 6, 8, Program.TOWN, "");
        MapSketch sketch = field(List.of(new Crossing(.5f, 4, CrossingKind.BRIDGE)), List.of(town));
        SketchRaster.Cut cut = SketchRaster.cut(W, H, sketch);
        List<String> problems = MapChecks.run(grid(cut), cut, null, sketch);
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("Settlement town stands on")), problems.toString());
    }

    @Test
    void aMapWithNoLandingSaysSo() {
        MapSketch sketch = new MapSketch("test", List.of(), List.of(), List.of(), List.of(), List.of());
        SketchRaster.Cut cut = SketchRaster.cut(W, H, sketch);
        assertEquals(1, MapChecks.run(grid(cut), cut, null, sketch).size());
    }
}
