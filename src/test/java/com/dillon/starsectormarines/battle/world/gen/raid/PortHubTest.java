package com.dillon.starsectormarines.battle.world.gen.raid;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The port hub's geometry ({@link PortHub}): bands outward from the lines in the order cargo moves, roads from the
 * highway through every band to the pads, the warehouses on their own built ground, few pads sized for freighters.
 * Rectangles only.
 */
class PortHubTest {

    private static PortHub east() {
        return PortHub.lay(150, 67, 277, 165, true, true, 40);
    }

    @Test
    void theBandsRunOutwardFromTheLinesToThePadsInCargoOrder() {
        for (boolean north : new boolean[]{true, false}) {
            PortHub hub = PortHub.lay(150, north ? 67 : 2, 277, north ? 165 : 100, true, north, 40);
            assertNotNull(hub);
            int edge = north ? hub.lines().y0() : hub.lines().y1();
            int highway = out(hub.highway(), edge, north), rail = out(hub.tracks().get(0), edge, north);
            int sheds = out(hub.sheds().get(0).at(), edge, north), boxes = out(hub.containers().get(0), edge, north);
            int main = out(hub.fuelMain(), edge, north), pads = out(hub.pads().get(0), edge, north);
            assertTrue(highway < rail && rail < sheds && sheds < boxes && boxes < main && main < pads,
                    "north " + north + ": " + List.of(highway, rail, sheds, boxes, main, pads));
        }
    }

    @Test
    void everyArterialRunsFromTheHighwayThroughEveryBandToThePads() {
        PortHub hub = east();
        assertNotNull(hub);
        assertTrue(hub.arterials().size() >= 3, "the truck gate's road and an arterial at each bay's end");
        assertEquals(hub.gateRoad(), hub.arterials().get(0));
        assertEquals(hub.paved().x0(), hub.gateRoad().x0(), "the truck gate at the terminal's end");
        for (PortHub.Rect a : hub.arterials()) {
            assertEquals(hub.highway().y1() + 1, a.y0(), "off the highway");
            assertEquals(hub.pads().get(0).y0() - 1, a.y1(), "to the service lane before the pads");
            for (PortHub.Shed shed : hub.sheds()) assertFalse(a.overlaps(shed.at()), "no warehouse on a road");
            for (PortHub.Rect box : hub.containers()) assertFalse(a.overlaps(box), "no container on a road");
            for (PortHub.Rect track : hub.tracks()) assertTrue(a.overlaps(track), "crosses the rail line");
        }
        // The roads along the hub reach its whole length, so each arterial joins the others.
        long along = hub.roads().stream().filter(r -> r.x0() == hub.paved().x0() && r.x1() == 277).count();
        assertEquals(3, along, "row lane, yard road, service lane");
        // The rail road runs back with the lines past the terminal, where the town's streets meet it.
        assertTrue(hub.roads().contains(hub.railRoad()));
        assertEquals(hub.lines().x0(), hub.railRoad().x0());
    }

    @Test
    void warehousesStandOnTheirBuiltGroundWithTheMarshallingParkInTheNearRowsFirstBay() {
        PortHub hub = east();
        for (PortHub.Shed shed : hub.sheds()) {
            assertFalse(shed.at().overlaps(hub.lines()), "off the lines' band");
            assertFalse(shed.at().overlaps(hub.apron()), "off the apron");
            assertTrue(shed.at().y0() >= hub.built().y0() && shed.at().y1() <= hub.built().y1(), "on built ground");
            assertFalse(shed.at().overlaps(hub.marshalling()), "the park is not a warehouse's");
            for (PortHub.Shed other : hub.sheds()) if (other != shed) assertFalse(shed.at().overlaps(other.at()));
        }
        assertNotNull(hub.marshalling());
        assertEquals(hub.gateRoad().x1() + 1, hub.marshalling().x0(), "beside the truck gate's road");
        int nearY = hub.marshalling().y0();
        for (PortHub.Shed shed : hub.sheds()) assertEquals(shed.at().y0() == nearY ? 0 : 2, shed.turns());
    }

    @Test
    void fewPadsEachSizedForAColossusWithAWallBetweenEachPair() {
        PortHub hub = east();
        assertTrue(hub.pads().size() >= PortHub.MIN_PADS && hub.pads().size() <= 5);
        for (PortHub.Rect pad : hub.pads()) {
            // A Colossus is 15 cells long and 12 across as it draws, inside a landing ring.
            assertTrue(pad.height() >= 19 && pad.width() >= 16, "pad " + pad);
            assertTrue(pad.x1() <= 277);
            assertFalse(pad.overlaps(hub.fuelFarm()), "the farm takes the row's terminal end");
        }
        assertEquals(hub.pads().size() - 1, hub.blastWalls().size());
        for (int i = 0; i + 1 < hub.pads().size(); i++) {
            int wx = hub.blastWalls().get(i).x0();
            assertTrue(wx > hub.pads().get(i).x1() && wx < hub.pads().get(i + 1).x0());
        }
    }

    @Test
    void theLinesRunBackPastTheTerminalAndOutAtTheDeepEdge() {
        PortHub hub = east();
        assertEquals(110, hub.lines().x0());
        assertEquals(277, hub.lines().x1());
        assertEquals(150, hub.terminalX());
        PortHub west = PortHub.lay(2, 67, 129, 165, false, true, 40);
        assertEquals(169, west.lines().x1());
        assertEquals(129, west.terminalX());
        assertTrue(west.pads().get(0).x1() < 129 - PortHub.FARM, "turned, the pads run west from the farm");
    }

    @Test
    void shallowGroundHoldsLessAndTooShallowHoldsNoHub() {
        PortHub deep = PortHub.lay(150, 67, 277, 200, true, true, 40);
        PortHub shallow = PortHub.lay(150, 67, 277, 67 + PortHub.linesDepth() + PortHub.apronFixed()
                + PortHub.BLOCK + PortHub.SHED_DEEP, true, true, 40);
        assertNotNull(shallow);
        assertTrue(shallow.containers().size() < deep.containers().size());
        assertEquals(1, shallow.sheds().stream().map(s -> s.at().y0()).distinct().count(), "one warehouse row");
        assertNull(PortHub.lay(150, 67, 277, 67 + PortHub.linesDepth() + PortHub.apronFixed() + PortHub.BLOCK,
                true, true, 40));
    }

    /** How far out from the lines' middle-side edge a band starts. */
    private static int out(PortHub.Rect r, int edge, boolean north) {
        return north ? r.y0() - edge : edge - r.y1();
    }
}
