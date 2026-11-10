package com.dillon.starsectormarines.battle.world.gen.raid;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The port hub's geometry ({@link PortHub}): bands outward from the lines in the order cargo moves, the warehouses
 * off the held bands, the staging broken at the cross streets, the pads along the outer edge. Rectangles only.
 */
class PortHubTest {

    @Test
    void theBandsRunOutwardFromTheLinesToThePadsInCargoOrder() {
        for (boolean north : new boolean[]{true, false}) {
            PortHub hub = PortHub.lay(150, north ? 67 : 2, 277, north ? 165 : 100, true, north, 40);
            assertNotNull(hub);
            // Outward: the lower a band's distance from the lines' middle-side edge, the nearer the map's middle.
            int edge = north ? hub.lines().y0() : hub.lines().y1();
            int highway = out(hub.highway(), edge, north), rail = out(hub.tracks().get(0), edge, north);
            int sheds = out(hub.sheds().get(0).at(), edge, north), boxes = out(hub.containers().get(0), edge, north);
            int main = out(hub.fuelMain(), edge, north), pads = out(hub.pads().get(0), edge, north);
            assertTrue(highway < rail && rail < sheds && sheds < boxes && boxes < main && main < pads,
                    "north " + north + ": " + List.of(highway, rail, sheds, boxes, main, pads));
            assertEquals(2, hub.sheds().stream().map(s -> s.at().y0()).distinct().count(), "two warehouse rows");
        }
    }

    @Test
    void warehousesStandBetweenTheHeldBandsOnTheirOwnGround() {
        PortHub hub = PortHub.lay(150, 67, 277, 165, true, true, 40);
        assertNotNull(hub);
        for (PortHub.Shed shed : hub.sheds()) {
            assertFalse(shed.at().overlaps(hub.lines()), "a warehouse is raised off the lines' held band");
            assertFalse(shed.at().overlaps(hub.apron()), "and off the apron's");
            for (PortHub.Shed other : hub.sheds()) {
                if (other != shed) assertFalse(shed.at().overlaps(other.at()));
            }
        }
        // The near row's doors face the lines (south here), the far row's the yard road.
        int nearY = hub.sheds().get(0).at().y0();
        for (PortHub.Shed shed : hub.sheds()) assertEquals(shed.at().y0() == nearY ? 0 : 2, shed.turns());
    }

    @Test
    void stagingBreaksAtTheCrossStreetsAndKeepsOffTheHeavyPad() {
        PortHub hub = PortHub.lay(150, 67, 277, 165, true, true, 40);
        assertNotNull(hub);
        assertNotNull(hub.heavyPad(), "a hub this long has its heavy pad at the deep end");
        assertEquals(277, hub.heavyPad().x1());
        // A cross street after every bay of two warehouses: no container stands on one.
        int cross0 = hub.sheds().get(1).at().x1() + 1, cross1 = cross0 + PortHub.CROSS - 1;
        for (PortHub.Rect box : hub.containers()) {
            assertFalse(box.x1() >= cross0 && box.x0() <= cross1, "box on the cross street: " + box);
            assertFalse(box.overlaps(hub.heavyPad()), "box on the heavy pad: " + box);
        }
        assertFalse(hub.cranes().isEmpty());
        for (PortHub.Rect pad : hub.pads()) assertFalse(pad.overlaps(hub.heavyPad()));
        assertEquals(hub.pads().size(), hub.blastWalls().size(), "a wall after each pad, the last before the heavy pad");
    }

    @Test
    void theLinesRunBackPastTheTerminalAndOutAtTheDeepEdge() {
        PortHub east = PortHub.lay(150, 67, 277, 165, true, true, 40);
        assertEquals(110, east.lines().x0());
        assertEquals(277, east.lines().x1());
        assertEquals(150, east.terminalX());
        PortHub west = PortHub.lay(2, 67, 129, 165, false, true, 40);
        assertEquals(2, west.lines().x0());
        assertEquals(169, west.lines().x1());
        assertEquals(129, west.terminalX());
        assertTrue(west.pads().get(0).x0() > west.heavyPad().x1(), "turned, the heavy pad is at the west edge");
    }

    @Test
    void shallowGroundHoldsLessAndTooShallowHoldsNoHub() {
        PortHub deep = PortHub.lay(150, 67, 277, 165, true, true, 40);
        PortHub shallow = PortHub.lay(150, 67, 277, 67 + PortHub.linesDepth() + PortHub.apronFixed()
                + PortHub.BLOCK + PortHub.SHED_DEEP, true, true, 40);
        assertNotNull(shallow);
        assertTrue(shallow.containers().size() < deep.containers().size());
        assertEquals(1, shallow.sheds().stream().map(s -> s.at().y0()).distinct().count(), "one warehouse row");
        assertNull(PortHub.lay(150, 67, 277, 67 + PortHub.linesDepth() + PortHub.apronFixed(), true, true, 40));
    }

    /** How far out from the lines' middle-side edge a band starts. */
    private static int out(PortHub.Rect r, int edge, boolean north) {
        return north ? r.y0() - edge : edge - r.y1();
    }
}
