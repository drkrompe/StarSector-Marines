package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The map-edge helm's margin, its momentum-aware pressure, who it exempts, and a side's way on and off the map. */
class WarshipEdgeHelmTest {

    private static final WarshipEdgeHelm MAP = new WarshipEdgeHelm(300f, 232f);

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    /** A Hound fighting a hull with its duel helm: the edge pressure is a rule of the hull fight. */
    private static Warship hound(float x, float y) throws Exception {
        Warship ship = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, x, y, 0f);
        ship.stationTo(x, y);
        fighting(ship);
        return ship;
    }

    private static void fighting(Warship ship) throws Exception {
        ship.duelHelm(true);
        ship.hullTarget(Warship.fit("wolf_hegemony", "wolf_hegemony_Rusk", Faction.ALLY, 0f, 0f, 0f));
    }

    @Test
    void aShipHoldingAStationItWasGivenIsLeftOnIt() throws Exception {
        Warship placed = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 5f, 116f, 0f);
        placed.stationTo(5f, 116f);
        assertFalse(WarshipEdgeHelm.applies(placed), "a mission's station near the edge is the mission's");
    }

    @Test
    void aShipThePressureLeavesAloneIsStillHeldOnTheMap() throws Exception {
        Warship placed = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 150f, 0f, 0f);
        placed.stationTo(150f, 116f);
        placed.y -= placed.centerY() + 2f;
        placed.velocityY = -placed.maxSpeed;
        assertFalse(WarshipEdgeHelm.applies(placed), "not fighting a hull: no pressure");
        MAP.contain(placed);
        assertEquals(0f, placed.centerY(), 1e-4f, "held on the edge it does not own");
        assertTrue(placed.velocityY >= 0f, "its way off cut");
    }

    @Test
    void theMarginIsATurningCircleOrAFewLengthsAndNeverMostOfTheMap() throws Exception {
        Warship hound = hound(150f, 116f);
        float margin = MAP.margin(hound);
        assertTrue(margin >= Math.min(WarshipEdgeHelm.MARGIN_MAP_SHARE * 232f,
                Math.max(WarshipEdgeHelm.turnRadius(hound), WarshipEdgeHelm.MARGIN_LENGTHS * hound.lengthCells)) - 1e-3f);
        assertTrue(new WarshipEdgeHelm(60f, 50f).margin(hound) <= WarshipEdgeHelm.MARGIN_MAP_SHARE * 50f + 1e-3f,
                "a small map keeps a middle");
    }

    @Test
    void aHullOverTheMiddleFeelsNothing() throws Exception {
        Warship hound = hound(150f, 116f);
        assertArrayEquals(new float[]{1f, -0.5f}, MAP.steer(hound, 1f, -0.5f), 1e-6f);
    }

    @Test
    void nearAnEdgeTheWayTowardItIsShedAndTheHullIsPushedBackIn() throws Exception {
        Warship hound = hound(0f, 116f);
        hound.x += 0.5f * hound.lengthCells + 8f - hound.centerX();
        float[] want = MAP.steer(hound, -hound.maxSpeed, 0.3f * hound.maxSpeed);
        assertTrue(want[0] > 0f, "turned back in off the west edge: " + want[0]);
        assertEquals(0.3f * hound.maxSpeed, want[1], 1e-4f, "way along the edge kept");
    }

    @Test
    void aHullClosingTheEdgeFastFeelsItSooner() throws Exception {
        Warship still = hound(0f, 116f);
        Warship closing = hound(0f, 116f);
        float at = 0.5f * still.lengthCells + MAP.margin(still) + 4f;
        still.x += at - still.centerX();
        closing.x += at - closing.centerX();
        closing.velocityX = -closing.maxSpeed;
        assertEquals(0f, MAP.pressure(still, MapEdge.WEST), 1e-6f, "outside the margin at rest");
        assertTrue(MAP.pressure(closing, MapEdge.WEST) > 0f, "the same place at speed toward the edge is inside it");
    }

    @Test
    void stationsAreKeptOutOfTheMargin() throws Exception {
        Warship hound = hound(150f, 116f);
        float keep = 0.5f * hound.lengthCells + MAP.margin(hound);
        assertArrayEquals(new float[]{keep, 232f - keep}, MAP.settle(hound, 2f, 231f), 1e-4f);
        assertArrayEquals(new float[]{150f, 116f}, MAP.settle(hound, 150f, 116f), 1e-4f);
    }

    @Test
    void aHullFlyingInAcrossItsEdgeStartsOffTheMapUnderWayAndIsExemptUntilClear() throws Exception {
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Rusk", Faction.ALLY, 0f, 0f, 0f);
        wolf.enterAcrossEdge(MapEdge.WEST, 90f, 270f, 300f, 232f);
        assertTrue(wolf.centerX() < 0f, "off the map: " + wolf.centerX());
        assertEquals(90f, wolf.centerY(), 1e-3f);
        assertEquals(wolf.maxSpeed, wolf.velocityX, 1e-4f);
        assertTrue(wolf.enteringAcrossEdge());
        assertFalse(WarshipEdgeHelm.applies(wolf));
        wolf.x += 0.5f * wolf.lengthCells + MAP.margin(wolf) + 1f - wolf.centerX();
        wolf.tickEdgeEntry(1f / 30f, MAP);
        assertFalse(wolf.enteringAcrossEdge(), "clear of its margin");
        fighting(wolf);
        assertTrue(WarshipEdgeHelm.applies(wolf));
        assertThrows(IllegalArgumentException.class, () -> Warship.fit("wolf_hegemony", "wolf_hegemony_Rusk",
                Faction.ALLY, 0f, 0f, 0f).enterAcrossEdge(MapEdge.WEST, 90f, 90f, 300f, 232f));
    }

    @Test
    void aHullLeavingAcrossItsEdgeFliesLevelForThatEdgeAndIsExempt() throws Exception {
        Warship hound = hound(150f, 116f);
        hound.leaveAcrossEdge(MapEdge.EAST, 300f, 232f);
        assertTrue(hound.departing());
        assertTrue(hound.leavingAcrossEdge());
        assertTrue(hound.escapingAcrossEdge());
        assertEquals(MapEdge.EAST, hound.exitEdge());
        assertEquals(0f, hound.climbSeconds(), "never drawn climbing into the deck");
        assertTrue(hound.stationX() > 300f, "for the east edge and past it: " + hound.stationX());
        assertEquals(116f, hound.stationY(), 1e-3f);
        assertFalse(WarshipEdgeHelm.applies(hound));
    }

    @Test
    void aHullLeavingAcrossItsEdgeIsHeldOffTheOthers() throws Exception {
        Warship hound = hound(290f, 116f);
        hound.leaveAcrossEdge(MapEdge.EAST, 300f, 232f);
        hound.y -= hound.centerY() + 2f;
        hound.x += 320f - hound.centerX();
        MAP.contain(hound);
        assertEquals(0f, hound.centerY(), 1e-4f, "held off the south edge");
        assertEquals(320f, hound.centerX(), 1e-4f, "free across its own");
    }

    @Test
    void aSideWithNoEdgeHasNoneToFly() {
        WarshipSystem system = new WarshipSystem();
        assertNull(system.edgeFor(Faction.ALLY));
        system.assignEdge(Faction.ALLY, MapEdge.WEST);
        assertEquals(MapEdge.WEST, system.edgeFor(Faction.ALLY));
        assertNull(system.edgeFor(Faction.DEFENDER));
    }

    @Test
    void edgesKnowInsideFromBeyond() {
        assertTrue(MapEdge.WEST.beyond(-1f, 5f, 300f, 232f));
        assertFalse(MapEdge.EAST.beyond(299f, 5f, 300f, 232f));
        assertEquals(32f, MapEdge.NORTH.inside(5f, 200f, 300f, 232f), 1e-6f);
        assertArrayEquals(new float[]{303f, 40f}, MapEdge.EAST.point(40f, 3f, 300f, 232f), 1e-6f);
    }
}
