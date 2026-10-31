package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification.GateYard;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A square place's truck yard inside its gate ({@link GateYardLayout}), and the wall leaving that gate open. */
class GateYardLayoutTest {

    private static final int W = 40, H = 40, WHO = 0;
    /** The yard's gate on the south side: nine wide, a yard thirteen by nine, seven of staging beside it. */
    private static final GateYard SPEC = new GateYard(Side.SOUTH, 9, 9, 13, 7);

    /** A square claim over 5..34 each way; {@code own} is it less the glacis, 8..31. */
    private static int[][] square(int inset) {
        int[][] claim = new int[W][H];
        for (int[] column : claim) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        for (int x = 5 + inset; x <= 34 - inset; x++) for (int y = 5 + inset; y <= 34 - inset; y++) claim[x][y] = WHO;
        return claim;
    }

    @Test
    void theYardRunsInFromTheWallBehindTheGateAndTheStagingGroundStandsBesideItWhereThereIsMoreRoom() {
        // The gate off-centre toward the east wall, so the west hand has more room for the staging ground.
        GateYardLayout.Laid yard = GateYardLayout.lay(square(0), square(Fortification.GLACIS), WHO, W, H, SPEC, Side.SOUTH, 22, 5);
        assertNotNull(yard);
        assertArrayEquals(new int[]{22, 8}, yard.gate(), "on the wall's line, in across the glacis");
        assertArrayEquals(new int[]{22, 9}, yard.entry());
        assertArrayEquals(new int[]{22, 13}, yard.turn());
        Set<Long> yardCells = keys(yard.yard());
        for (int[] c : yard.yard()) {
            assertTrue(c[1] >= 9 && c[1] <= 17 && Math.abs(c[0] - 22) <= 6, "inside the wall, behind the gate");
        }
        assertEquals(13 * 9, yard.yard().size());
        assertTrue(yardCells.contains(key(yard.stop()[0], yard.stop()[1])), "a truck stops in the yard");
        for (int[] c : yard.staging()) {
            assertFalse(yardCells.contains(key(c[0], c[1])), "beside the yard, never on it");
            assertTrue(c[0] < 16, "on the west hand, where the east wall leaves less room");
        }
        assertEquals(16 - 9, yard.staging().size() / 9, "seven cells across, inside the wall");
        assertTrue(yard.stop()[0] < 22, "the truck stops on the staging ground's side");
    }

    @Test
    void theWallLeavesTheYardsGateOpenItsFullWidth() {
        int[][] claim = square(0), own = square(Fortification.GLACIS);
        // A road five wide comes up from the south edge to the place, as the corridor's road does.
        int[][] road = new int[W][H];
        for (int[] column : road) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        for (int x = 18; x <= 22; x++) for (int y = 0; y <= 9; y++) road[x][y] = 1;
        GateYardLayout.Laid yard = GateYardLayout.lay(claim, own, WHO, W, H, SPEC, Side.SOUTH, 20, 5);
        assertNotNull(yard);
        for (int[] c : yard.apron()) if (road[c[0]][c[1]] == GrownTrunkPlan.UNOWNED) road[c[0]][c[1]] = 1;
        Fortification wallOf = new Fortification(2, 110).laidSquare().withYard(SPEC);
        boolean[][] wall = PrecinctBoundary.wall(own, road, WHO, W, H, wallOf.gates(), wallOf.gateWidth());
        int open = 0;
        for (int x = 8; x <= 31; x++) if (!wall[x][8]) open++;
        assertEquals(9, open, "nine wide where the road alone left five");
        for (int x = 16; x <= 24; x++) assertFalse(wall[x][8]);
    }

    @Test
    void aGateThatLeadsToNoGroundOfThePlaceLaysNoYard() {
        assertNull(GateYardLayout.lay(square(0), square(Fortification.GLACIS), WHO, W, H, SPEC, Side.SOUTH, 2, 2));
    }

    @Test
    void aYardsGateStaysNarrowEnoughToReadAsAGateAndOnlyASquarePlaceTakesOne() {
        assertThrows(IllegalArgumentException.class, () -> new GateYard(Side.SOUTH, 11, 9, 13, 7));
        assertThrows(IllegalArgumentException.class, () -> new GateYard(Side.SOUTH, 9, 9, 7, 7));
        assertThrows(IllegalArgumentException.class, () -> new Fortification(2, 110).withYard(SPEC));
        assertEquals(9, new Fortification(2, 110).laidSquare().withYard(SPEC).gateWidth());
        assertEquals(Fortification.SQUARE_GATE_WIDTH, new Fortification(2, 110).laidSquare().gateWidth());
        assertEquals(Integer.MAX_VALUE, new Fortification(2, 110).gateWidth());
    }

    private static Set<Long> keys(Iterable<int[]> cells) {
        Set<Long> out = new HashSet<>();
        for (int[] c : cells) out.add(key(c[0], c[1]));
        return out;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
