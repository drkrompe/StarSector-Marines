package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The port's site rules ({@link PortWorks}): the terminal laid in the order the cargo passes, the apron held at the
 * deep end beside it and brought in to its rear wall, never across a building. Plans and rectangles only.
 */
class PortWorksTest {

    private static final PortWorks PORT = new PortWorks();

    @Test
    void theTerminalRunsFromCustomsAtTheFrontToTheTowerAtTheApron() {
        FortressProgram program = PORT.works(4);
        int sheds = 0;
        for (FortressBuilding b : program.buildings()) {
            if (b.purpose() == PortWorks.CUSTOMS) assertEquals(Ward.FRONTAGE, b.ward(), "customs at the town's gate");
            if (b.purpose() == PortWorks.BONDED) {
                assertEquals(Ward.YARD, b.ward());
                sheds += b.count();
            }
            if (b.purpose() == PortWorks.PUMPS) assertEquals(Ward.REAR, b.ward(), "the pumps toward the apron");
        }
        assertEquals(4, sheds, "a bonded shed per machine");
        assertFalse(PORT.deepFeeder(), "the deep end is the apron's");
        assertFalse(PORT.yards());
        assertTrue(PORT.industryIds().contains("megaport"));
    }

    @Test
    void theApronIsHeldBesideTheTerminalAtTheDeepEnd() {
        Site east = PortWorks.apronSite(150, 110, 30, false, false, 280, 168);
        assertEquals(PortWorks.APRON, east.name());
        assertEquals(SitePurpose.GROUND, east.purpose(), "nothing grows on the apron");
        assertEquals(150 + 30 + PortWorks.APRON_GAP, east.x0());
        assertTrue(east.x1() > 260, "out toward the deep edge");
        Site west = PortWorks.apronSite(130, 110, 30, true, false, 280, 168);
        assertEquals(130 - 30 - PortWorks.APRON_GAP, west.x1());
        assertTrue(west.x0() < 20);
    }

    @Test
    void theApronComesInToTheTerminalsWallButNeverAcrossABuilding() {
        Site ground = new Site(PortWorks.APRON, 200, 90, 270, 150, SitePurpose.GROUND, true);
        // Open ground between the claim (ending at 180) and the held ground: brought in to the claim.
        assertEquals(180, PortWorks.clearEdge(180, true, ground, (x, y) -> false));
        // A shed grew at x 188..190 on the apron's rows: the apron starts past it.
        assertEquals(190, PortWorks.clearEdge(180, true, ground, (x, y) -> x >= 188 && x <= 190 && y == 120));
        // A building off the apron's rows does not stop it.
        assertEquals(180, PortWorks.clearEdge(180, true, ground, (x, y) -> x == 188 && y == 40));
        PortApron apron = PortWorks.apronOn(ground, true, 180, 168, 4);
        assertEquals(180 + 1 + PortWorks.REAR_ROAD, apron.paved().x0(), "the rear road between wall and apron");
        // Mirrored: the terminal east of its apron.
        Site westGround = new Site(PortWorks.APRON, 10, 90, 80, 150, SitePurpose.GROUND, true);
        assertEquals(100, PortWorks.clearEdge(100, false, westGround, (x, y) -> false));
        PortApron west = PortWorks.apronOn(westGround, false, 100, 168, 4);
        assertEquals(100 - 1 - PortWorks.REAR_ROAD, west.paved().x1());
        assertTrue(west.pads().get(0).x0() < west.fuelMain().x0(), "pads on the deep side of the main");
    }

    @Test
    void theFarmStandsAtTheEndAwayFromTheMapsMiddle() {
        Site north = new Site(PortWorks.APRON, 200, 100, 270, 160, SitePurpose.GROUND, true);
        PortApron a = PortWorks.apronOn(north, true, 180, 168, 3);
        assertTrue(a.fuelFarm().y0() > a.pads().get(a.pads().size() - 1).y1(), "north half: the farm outermost north");
        Site south = new Site(PortWorks.APRON, 200, 8, 270, 68, SitePurpose.GROUND, true);
        PortApron b = PortWorks.apronOn(south, true, 180, 168, 3);
        assertTrue(b.fuelFarm().y1() < b.pads().get(b.pads().size() - 1).y0(), "south half: the farm outermost south");
    }
}
