package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The autoplant's assembly line ({@link AssemblyLine}): its stations staged in the order the work passes, one heading
 * down the line, never on a road. Planned on a hand-drawn strip of open ground; no map is generated.
 */
class AssemblyLineTest {

    /** A strip of open ground beside a works: the assembly hall at its top end, dispatch at its foot. */
    private static final int W = 40, H = 120;

    private static final AssemblyLine.Model CRAFT = AssemblyLine.MODELS.get(0);

    private static FabricationYard.Hall hall(RoomPurpose step, int y0, int y1) {
        return new FabricationYard.Hall(step, 0, y0, 2, y1, List.of());
    }

    private static final FabricationYard.Hall ASSEMBLY = hall(LightIndustryWorks.ASSEMBLY, 100, 115);
    private static final FabricationYard.Hall DISPATCH = hall(LightIndustryWorks.DISPATCH, 2, 12);

    private static FabricationYard.Ground strip(int roadY) {
        return new FabricationYard.Ground() {
            @Override public int width() { return W; }

            @Override public int height() { return H; }

            @Override public boolean open(int x, int y) { return x > 2; }

            @Override public boolean road(int x, int y) { return y == roadY || y == roadY + 1; }

            @Override public boolean building(int x, int y) { return x <= 2; }

            @Override public boolean inWorks(int x, int y) { return true; }
        };
    }

    private static List<AssemblyLine.Station> plan(FabricationYard.Ground g) {
        return AssemblyLine.plan(g, null, new boolean[W * H], List.of(), ASSEMBLY, DISPATCH, CRAFT);
    }

    /** The line starts with the engine section on its jig and ends with a finished craft, never going back. */
    @Test
    void theStagesRunFromTheEngineSectionToTheFinishedCraft() {
        assertEquals(AssemblyLine.Stage.FITTING, AssemblyLine.stage(0, 1), "a lone station is a hull being fitted");
        for (int n = 2; n <= AssemblyLine.MAX_STATIONS; n++) {
            assertEquals(AssemblyLine.Stage.AFT, AssemblyLine.stage(0, n));
            assertEquals(AssemblyLine.Stage.FINISHED, AssemblyLine.stage(n - 1, n));
            for (int k = 1; k < n; k++) {
                assertTrue(AssemblyLine.stage(k, n).ordinal() >= AssemblyLine.stage(k - 1, n).ordinal());
            }
        }
    }

    /** From the assembly hall down to dispatch: each station a stage on, every craft heading the same way. */
    @Test
    void theLineRunsFromTheAssemblyHallTowardDispatch() {
        List<AssemblyLine.Station> line = plan(strip(-10));
        assertTrue(line.size() >= 3, "the strip holds a line, not a lone craft: " + line.size());
        assertEquals(AssemblyLine.Stage.AFT, line.get(0).stage());
        assertEquals(AssemblyLine.Stage.FINISHED, line.get(line.size() - 1).stage());
        for (int k = 1; k < line.size(); k++) {
            assertTrue(line.get(k).y0() < line.get(k - 1).y0(), "each station is further from assembly");
            assertEquals(line.get(0).turns(), line.get(k).turns(), "one heading down the line");
        }
        assertEquals(2, line.get(0).turns(), "noses toward dispatch, down the strip");
    }

    /** A road across the strip cuts the line short; it carries on across it, on the same heading. */
    @Test
    void aRoadCutsTheLineButNotItsHeading() {
        int roadY = 60;
        List<AssemblyLine.Station> line = plan(strip(roadY));
        boolean above = false, below = false;
        for (AssemblyLine.Station s : line) {
            assertFalse(s.y0() <= roadY + 1 && s.y1() >= roadY, "no station stands on the road: " + s);
            above |= s.y0() > roadY;
            below |= s.y1() < roadY;
            assertEquals(line.get(0).turns(), s.turns());
        }
        assertTrue(above && below, "the line carries on across the road");
    }

    /** A craft's piece stands at its station's aft end, inside the station, and only its body closes ground. */
    @Test
    void theCraftLiesInItsStationLeavingItsCornersOpen() {
        for (AssemblyLine.Station s : plan(strip(-10))) {
            assertTrue(s.x() >= s.x0() && s.y() >= s.y0() && s.x() + s.fx() - 1 <= s.x1()
                    && s.y() + s.fy() - 1 <= s.y1(), "the piece lies in its station: " + s);
            assertFalse(AssemblyLine.underHull(s, s.x(), s.y()), "a corner of the footprint is open ground");
            assertTrue(AssemblyLine.underHull(s, s.x() + s.fx() / 2, s.y() + s.fy() / 2), "its middle is hull");
        }
    }

    /** The line's ground is its depth of the apron's ground on the works' side, the works' height long. */
    @Test
    void theLinesGroundLiesAlongTheWorks() {
        Site apron = new Site("apron", 100, 10, 300, 200, SitePurpose.GROUND, true);
        Site east = LightIndustryWorks.lineSite(apron, false, 120, 50, 240);
        assertEquals(100, east.x0());
        assertEquals(100 + LightIndustryWorks.LINE_DEPTH - 1, east.x1());
        assertEquals(70, east.y0());
        assertEquals(170, east.y1());
        Site west = LightIndustryWorks.lineSite(apron, true, 120, 50, 240);
        assertEquals(300, west.x1(), "mirrored, the works is on the far side");
        assertEquals(300 - LightIndustryWorks.LINE_DEPTH + 1, west.x0());
    }
}
