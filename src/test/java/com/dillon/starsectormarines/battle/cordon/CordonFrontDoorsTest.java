package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The doors a built redoubt's front and flanks brick up ({@link CordonBuilders#sealDoors}). */
class CordonFrontDoorsTest {

    // Row 198: open at x 89-90 (an archway), 96-97 (another) and 100-104 (open ground), wall everywhere else.
    private static boolean open(int x, int y) {
        return y == 198 && (x == 89 || x == 90 || x == 96 || x == 97 || x >= 100 && x <= 104);
    }

    private static List<int[]> row(int y, int x0, int x1) {
        List<int[]> out = new ArrayList<>();
        for (int x = x0; x <= x1; x++) out.add(new int[]{x, y});
        return out;
    }

    @Test
    void narrowOpeningsWalledAtBothEndsAreDoors() {
        CordonBuilders.CellTest open = CordonFrontDoorsTest::open;
        CordonBuilders.CellTest wall = (x, y) -> y == 198 && !open(x, y);
        List<int[]> doors = CordonBuilders.doors(open, wall, row(198, 81, 105));
        assertEquals(4, doors.size(), "the two archways, not the five-wide open ground");
        assertArrayEquals(new int[]{89, 198}, doors.get(0));
        assertArrayEquals(new int[]{97, 198}, doors.get(3));
    }

    @Test
    void anOpeningBesideARoadEntryIsTheGateNotADoor() {
        CordonBuilders.CellTest open = (x, y) -> x == 89 || x == 90;
        CordonBuilders.CellTest wall = (x, y) -> !(x == 89 || x == 90);
        List<int[]> line = row(198, 85, 95);
        line.set(6, null); // x 91: where a road comes in
        assertTrue(CordonBuilders.doors(open, wall, line).isEmpty());
    }

    @Test
    void aFlankIsReadDownItsColumnOnEitherFront() {
        CordonBuilders.Frame south = new CordonBuilders.Frame(93, 187, -1);
        List<int[]> west = CordonBuilders.line(south, List.of(), 'W', -CordonBuilders.R);
        assertArrayEquals(new int[]{93 + CordonBuilders.R, 187 + CordonBuilders.R}, west.get(0));
        List<int[]> withRoad = CordonBuilders.line(south, List.of(new CordonBuilders.Entry('W', -1, 1)), 'W',
                -CordonBuilders.R);
        assertEquals(null, withRoad.get(CordonBuilders.R));
    }
}
