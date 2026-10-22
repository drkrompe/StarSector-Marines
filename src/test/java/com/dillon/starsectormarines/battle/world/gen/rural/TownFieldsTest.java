package com.dillon.starsectormarines.battle.world.gen.rural;

import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Plot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownFieldsTest {

    private static final int W = 160, H = 120;

    /** A town in the middle with one road leaving it east to the map edge, open ground everywhere else but a hill. */
    private static List<Plot> plan(boolean[][] open, boolean[][] town, boolean[][] road) {
        for (int x = 60; x < 100; x++) for (int y = 40; y < 80; y++) town[x][y] = true;
        for (int x = 100; x < W; x++) for (int y = 58; y <= 62; y++) road[x][y] = true;
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) open[x][y] = !town[x][y] && !road[x][y];
        for (int x = 20; x < 40; x++) for (int y = 20; y < 40; y++) open[x][y] = false;
        return TownFields.plan(W, H, open, town, road, 9L);
    }

    @Test
    void fieldsLieOnOpenGroundFrontingTheTownOrARoad() {
        boolean[][] open = new boolean[W][H], town = new boolean[W][H], road = new boolean[W][H];
        List<Plot> fields = plan(open, town, road);
        assertFalse(fields.isEmpty());
        int[][] fromTown = TownFields.distance(W, H, town);
        for (Plot field : fields) {
            boolean fronts = false;
            for (int x = field.x0(); x <= field.x1(); x++) {
                for (int y = field.y0(); y <= field.y1(); y++) {
                    assertTrue(open[x][y], field + " off open ground at " + x + "," + y);
                    assertTrue(fromTown[x][y] <= TownFields.REACH, field + " out of reach at " + x + "," + y);
                }
            }
            for (int x = field.x0() - TownFields.VERGE - 1; x <= field.x1() + TownFields.VERGE + 1; x++) {
                for (int y = field.y0() - TownFields.VERGE - 1; y <= field.y1() + TownFields.VERGE + 1; y++) {
                    if (x >= 0 && y >= 0 && x < W && y < H) fronts |= town[x][y] || road[x][y];
                }
            }
            assertTrue(fronts, field + " fronts nothing");
            assertTrue(field.x1() - field.x0() + 1 >= TownFields.MIN_SIDE && field.y1() - field.y0() + 1 >= TownFields.MIN_SIDE);
        }
    }

    @Test
    void fieldsKeepWildGroundBetweenThem() {
        boolean[][] open = new boolean[W][H], town = new boolean[W][H], road = new boolean[W][H];
        List<Plot> fields = plan(open, town, road);
        for (int i = 0; i < fields.size(); i++) {
            for (int j = i + 1; j < fields.size(); j++) {
                Plot a = fields.get(i), b = fields.get(j);
                boolean apart = a.x1() + TownFields.GAP < b.x0() || b.x1() + TownFields.GAP < a.x0()
                        || a.y1() + TownFields.GAP < b.y0() || b.y1() + TownFields.GAP < a.y0();
                assertTrue(apart, a + " against " + b);
            }
        }
    }
}
