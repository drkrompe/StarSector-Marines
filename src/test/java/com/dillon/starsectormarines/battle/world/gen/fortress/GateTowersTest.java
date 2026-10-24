package com.dillon.starsectormarines.battle.world.gen.fortress;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A gate tower stands proud of the wall beside its gate, opens on the yard, and is slit only where it looks out of the works. */
class GateTowersTest {

    /** A west-facing gate whose mouth is (10, 20): the wall stands at x 11 and 12, the gate passage at y 19 to 21. */
    private static final FortPlan.Gate WEST = new FortPlan.Gate(10, 20, -1, 0);

    @Test
    void aTowerStandsProudOfTheWallBesideItsGateWithItsDoorOnTheYard() {
        FortRoom tower = GateTowers.flanking(WEST).get(0);
        assertEquals(List.of(10, 15, 3, 3), List.of(tower.x(), tower.y(), tower.width(), tower.height()));
        assertEquals(Set.of("13,16"), keys(tower.doorCells()));
        assertEquals(Set.of("10,15", "10,16", "10,17"), keys(tower.standCells()));
    }

    @Test
    void slitsLookOutOfTheWorksAndNeverIntoTheYardNorThroughACorner() {
        List<FortRoom> towers = GateTowers.flanking(WEST);
        FortRoom tower = towers.get(0);
        List<int[]> footprint = GateTowers.footprint(tower);
        List<int[]> slits = GateTowers.slits(tower, (x, y) -> x >= 11
                || footprint.stream().anyMatch(c -> c[0] == x && c[1] == y));
        Set<String> got = new TreeSet<>();
        for (int[] s : slits) got.add(s[0] + "," + s[1] + ">" + s[2] + "," + s[3]);
        // Three over the approach, one along the wall face, one into the gate's mouth.
        assertEquals(new TreeSet<>(Set.of("9,15>-1,0", "9,16>-1,0", "9,17>-1,0", "10,14>0,-1", "10,18>0,1")), got);
    }

    private static Set<String> keys(List<int[]> cells) {
        Set<String> out = new TreeSet<>();
        for (int[] c : cells) out.add(c[0] + "," + c[1]);
        return out;
    }
}
