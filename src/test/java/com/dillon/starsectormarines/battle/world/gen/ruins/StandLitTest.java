package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandLitTest {

    private static final Area HALL = new Area(10, 10, 49, 85);
    private static final Cell CENTRE = new Cell(HALL.centreX(), HALL.centreY());

    private static boolean lit(List<Area> areas, int x, int y) {
        return areas.stream().anyMatch(a -> a.contains(x, y));
    }

    @Test
    void theTransferAisleIsLitWallToWallAndNothingOutsideTheHall() {
        for (boolean forge : new boolean[]{false, true}) {
            List<Area> lit = RuinsDelveStage.standLit(HALL, CENTRE, new Cell(HALL.right() - 2, CENTRE.y()), forge,
                    CENTRE.x() - 5, CENTRE.x() + 4);
            for (int x = HALL.left(); x <= HALL.right(); x++) assertTrue(lit(lit, x, CENTRE.y()), "aisle " + x);
            for (Area a : lit) {
                assertTrue(HALL.contains(a.left(), a.bottom()) && HALL.contains(a.right(), a.top()), a.toString());
            }
        }
    }

    @Test
    void aForgeLightsItsPoolAndPulpitAndAWorksHallItsBays() {
        Cell pulpit = new Cell(HALL.right() - 4, HALL.top() - 8);
        List<Area> forge = RuinsDelveStage.standLit(HALL, CENTRE, pulpit, true, CENTRE.x() - 5, CENTRE.x() + 4);
        assertTrue(lit(forge, CENTRE.x() - 5, CENTRE.y() - 6), "pool south of the bridge");
        assertTrue(lit(forge, pulpit.x(), pulpit.y()), "pulpit");
        assertEquals(2, RuinsDelveStage.standLit(HALL, CENTRE, pulpit, true, 0, -1).size(), "no pool, no pool area");
        List<Area> works = RuinsDelveStage.standLit(HALL, CENTRE, new Cell(HALL.right() - 2, CENTRE.y()), false, 0, -1);
        assertTrue(lit(works, HALL.right() - 3, CENTRE.y() + 2 + HallFrame.BAY), "the bay north of the aisle");
        assertFalse(lit(works, HALL.left() + 2, CENTRE.y() + 6), "the far side's bays stay dark");
    }
}
