package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Cell;
import com.dillon.starsectormarines.battle.world.gen.OwnedMapRecipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Maps page's own checks, on a 24 by 20 field cut across by one drop with one crossing. */
class MapChecksTest {

    private static final int W = 24, H = 20;

    private static OwnedMap field(List<OwnedMap.Settlement> settlements) {
        OwnedMap.Drop drop = new OwnedMap.Drop("gorge", OwnedMap.DropRole.RIFT, List.of(new Cell(0, 10), new Cell(23, 10)), 4,
                List.of(new Cell(12, 10)), 4, List.of());
        return new OwnedMap("field", W, H, null, null, null, new Cell(2, 2), new Cell(20, 17), List.of(), List.of(),
                List.of(), settlements, List.of(), List.of(), List.of(drop), List.of(), List.of());
    }

    /** An open grid with the drop's cells closed and its deck left open, as the recipe lays them. */
    private static NavigationGrid grid(OwnedMap map) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        OwnedMapRecipe.Cut cut = OwnedMapRecipe.cut(map.drops().get(0), W, H);
        for (int i = 0; i < W * H; i++) if (cut.drop()[i] && !cut.decks()[i]) grid.setWalkable(i % W, i / W, false);
        return grid;
    }

    @Test
    void aDeckedCrossingJoinsItsBanks() {
        OwnedMap map = field(List.of());
        assertEquals(List.of(), MapChecks.run(grid(map), map, null));
    }

    @Test
    void aCrossingWithAClosedDeckFails() {
        OwnedMap map = field(List.of());
        NavigationGrid grid = grid(map);
        grid.setWalkable(12, 10, false);
        List<String> problems = MapChecks.run(grid, map, null);
        assertTrue(problems.stream().anyMatch(p -> p.contains("is closed at")), problems.toString());
    }

    @Test
    void aCrossingWalledAcrossDoesNotJoinItsBanks() {
        OwnedMap map = field(List.of());
        NavigationGrid grid = grid(map);
        for (int x = 0; x < W; x++) grid.setWalkable(x, 12, false);
        List<String> problems = MapChecks.run(grid, map, null);
        assertTrue(problems.stream().anyMatch(p -> p.contains("does not")), problems.toString());
    }

    @Test
    void aClosedGateIsBuiltOnAWay() {
        OwnedMap.Settlement town = new OwnedMap.Settlement("town", "market-town", OwnedMap.SettlementRole.TOWN,
                new OwnedMap.Box(2, 14, 10, 19), List.of(new Cell(6, 14)), List.of(), List.of(), null);
        OwnedMap map = field(List.of(town));
        NavigationGrid grid = grid(map);
        grid.setWalkable(6, 14, false);
        assertEquals(List.of("town's gate at 6,14 is closed."), MapChecks.run(grid, map, null));
    }
}
