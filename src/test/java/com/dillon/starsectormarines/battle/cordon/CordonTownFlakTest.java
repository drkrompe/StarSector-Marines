package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where Ain Tafrant's compound stands its flak at town density ({@code cordon-nouns.md}, Cut-off town). */
class CordonTownFlakTest {

    private static final int LEFT = 1, TOP = 1, RIGHT = 29, BOTTOM = 28;

    @Test
    void flakStandsOnTheOpenCourtyardClearOfTheYardTheGatesTheRoomsAndTheDefenders() {
        CordonStagingArea.Open inside = (x, y) -> x >= LEFT - 1 && x <= RIGHT + 1 && y >= TOP - 1 && y <= BOTTOM + 1;
        // A room the townspeople wait in.
        CordonStagingArea.Open indoors = (x, y) -> x >= 3 && x <= 8 && y >= 4 && y <= 9;
        CordonStagingArea.Open open = (x, y) -> inside.at(x, y) && !indoors.at(x, y);
        // The truck yard on the rear wall, its gate at (15, 29), and the staging area beside it.
        List<int[]> cells = new ArrayList<>();
        for (int y = 20; y <= 28; y++) for (int x = 9; x <= 21; x++) cells.add(new int[]{x, y});
        List<int[]> beside = new ArrayList<>();
        for (int y = 22; y <= 28; y++) for (int x = 22; x <= 25; x++) beside.add(new int[]{x, y});
        CordonGateYard yard = new CordonGateYard(cells, beside, new int[]{15, 29}, new int[]{15, 28},
                new int[]{15, 23}, new int[]{20, 25});
        CordonStagingArea staging = CordonStagingArea.of((x, y) -> open.at(x, y) && !yard.contains(x, y),
                LEFT, TOP, RIGHT, BOTTOM, yard.stopX(), yard.stopY());
        // The yard's gate, a postern on the east wall and one on the front.
        List<int[]> gates = List.of(new int[]{15, 29, 9}, new int[]{30, 14, 3}, new int[]{15, 0, 3});
        List<int[]> taken = List.of(new int[]{12, 12}, new int[]{20, 8}, new int[]{4, 18});

        // A paved way across the compound's middle.
        CordonStagingArea.Open street = (x, y) -> y == 14 || y == 15;
        List<int[]> sites = CordonTownGarrison.flakSites(open, indoors, street, LEFT, TOP, RIGHT, BOTTOM, gates, staging,
                yard, new int[]{15, 30}, taken);

        assertTrue(sites.size() >= CordonTownGarrison.FLAK_MIN && sites.size() <= CordonTownGarrison.FLAK_MAX,
                "a pair at least, never past the most: " + sites.size());
        for (int[] s : sites) {
            String at = "(" + s[0] + ", " + s[1] + ")";
            assertTrue(s[0] >= LEFT && s[0] <= RIGHT && s[1] >= TOP && s[1] <= BOTTOM, "inside the wall " + at);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    assertFalse(indoors.at(s[0] + dx, s[1] + dy), "never in or against a room " + at);
                    assertFalse(street.at(s[0] + dx, s[1] + dy), "never on the way through " + at);
                    assertFalse(yard.contains(s[0] + dx, s[1] + dy), "never where the trucks turn " + at);
                }
            }
            assertFalse(staging.near(s[0], s[1], CordonTownGarrison.STAGING_CLEAR), "off the people's ground " + at);
            for (int[] g : gates) {
                assertTrue(Math.max(Math.abs(g[0] - s[0]), Math.abs(g[1] - s[1])) > g[2] / 2 + CordonTownGarrison.GATE_CLEAR,
                        "never in a way in or the ground behind it " + at);
            }
            for (int[] t : taken) {
                assertTrue(Math.max(Math.abs(t[0] - s[0]), Math.abs(t[1] - s[1])) > 1, "off the defenders " + at);
            }
            for (int[] o : sites) {
                if (o != s) assertTrue(Math.max(Math.abs(o[0] - s[0]), Math.abs(o[1] - s[1])) > 1, "spread " + at);
            }
        }
    }

    @Test
    void aSmallCompoundStillHasItsPairAndOneWithNoCourtyardHasNone() {
        CordonStagingArea.Open open = (x, y) -> x >= -1 && x <= 9 && y >= -1 && y <= 9;
        CordonStagingArea.Open nowhere = (x, y) -> false;
        CordonStagingArea staging = CordonStagingArea.of(nowhere, 0, 0, 8, 8, 4, 9);
        List<int[]> sites = CordonTownGarrison.flakSites(open, nowhere, nowhere, 0, 0, 8, 8, List.of(), staging, null, null,
                List.of());
        assertEquals(CordonTownGarrison.FLAK_MIN, sites.size(), "covered by the first gun, but never one alone");
        assertTrue(CordonTownGarrison.flakSites(open, (x, y) -> true, nowhere, 0, 0, 8, 8, List.of(), staging, null, null,
                List.of()).isEmpty(), "all rooms: nowhere to stand a gun");
    }
}
