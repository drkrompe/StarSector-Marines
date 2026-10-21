package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The commander-tier machine guard: who is attached to which manned place, how many, and when they stand down. */
class MachineGuardDirectorTest {

    /** Place 1 at x 100, place 2 at x 200, both on row 50. */
    private static final IntBinaryOperator PLACE_AT = (x, y) -> x == 100 ? 1 : x == 200 ? 2 : -1;
    private static final MachineGuardDirector.Place HOT_ONE = new MachineGuardDirector.Place(1, 100, 50, true);
    private static final MachineGuardDirector.Place HOT_TWO = new MachineGuardDirector.Place(2, 200, 50, true);
    private static final MachineGuardDirector.Place COOL_ONE = new MachineGuardDirector.Place(1, 100, 50, false);

    private static MachineGuardDirector.Machine lance(int id, int x) {
        return new MachineGuardDirector.Machine(id, x, 50, 2, 2);
    }

    @Test
    void theCapIsHalfTheLancesRoundedUpAndNeverAboveTwo() {
        assertEquals(0, MachineGuardDirector.cap(0));
        assertEquals(1, MachineGuardDirector.cap(1), "a lone lance may guard");
        assertEquals(1, MachineGuardDirector.cap(2), "two keep one roving");
        assertEquals(2, MachineGuardDirector.cap(3));
        assertEquals(2, MachineGuardDirector.cap(9));
    }

    @Test
    void theNearestFreeLanceGoesToAPressedMannedPlaceOnePerPulse() {
        MachineGuardDirector director = new MachineGuardDirector();
        List<MachineGuardDirector.Attach> sent = director.attach(
                List.of(lance(7, 160), lance(5, 120), lance(9, 190)), List.of(HOT_ONE, HOT_TWO), 4, PLACE_AT);
        assertEquals(1, sent.size(), "one a pulse");
        assertEquals(5, sent.get(0).squadId(), "the nearest to the most pressed place");
        assertEquals(1, sent.get(0).garrisonable());
        assertTrue(director.holds(5));

        List<MachineGuardDirector.Attach> next = director.attach(
                List.of(lance(7, 160), lance(9, 190)), List.of(HOT_ONE, HOT_TWO), 4, PLACE_AT);
        assertEquals(1, next.size());
        assertEquals(2, next.get(0).garrisonable(), "one lance a place");
        assertEquals(9, next.get(0).squadId());

        assertTrue(director.attach(List.of(lance(7, 160)), List.of(HOT_ONE, HOT_TWO), 4, PLACE_AT).isEmpty(),
                "two on guard is the most at once");
    }

    @Test
    void aCoolPlaceAFarLanceOrAWeakLanceIsNotSent() {
        MachineGuardDirector director = new MachineGuardDirector();
        assertTrue(director.attach(List.of(lance(5, 120)), List.of(COOL_ONE), 2, PLACE_AT).isEmpty(),
                "manned but not pressed");
        assertTrue(director.attach(List.of(lance(5, 2000)), List.of(HOT_ONE), 2, PLACE_AT).isEmpty(),
                "too far to arrive while the pressure lasts");
        assertTrue(director.attach(List.of(new MachineGuardDirector.Machine(5, 120, 50, 1, 3)), List.of(HOT_ONE), 2,
                PLACE_AT).isEmpty(), "below useful strength");
        assertTrue(director.attach(List.of(lance(5, 120)), List.of(HOT_ONE), 0, PLACE_AT).isEmpty(),
                "a side with no lances has no cap");
    }

    @Test
    void aGuardStandsDownWhenThePlaceIsNoLongerMannedOrHasCooledOrItsLanceIsTakenAway() {
        MachineGuardDirector director = new MachineGuardDirector();
        director.attach(List.of(lance(5, 120)), List.of(HOT_ONE), 2, PLACE_AT);
        Map<Integer, MachineGuardDirector.Machine> guarding = Map.of(5, lance(5, 100));

        assertTrue(director.review(guarding, List.of(HOT_ONE), PLACE_AT).isEmpty(), "pressed and manned: stands");
        for (int pulse = 1; pulse < MachineGuardDirector.COOL_PULSES; pulse++) {
            assertTrue(director.review(guarding, List.of(COOL_ONE), PLACE_AT).isEmpty(), "cooling, within the dwell");
        }
        assertEquals(List.of(5), director.review(guarding, List.of(COOL_ONE), PLACE_AT), "cooled for the dwell");
        assertFalse(director.holds(5));

        director.attach(List.of(lance(5, 120)), List.of(HOT_ONE), 2, PLACE_AT);
        assertEquals(List.of(5), director.review(guarding, List.of(), PLACE_AT), "the garrison has gone");

        director.attach(List.of(lance(5, 120)), List.of(HOT_ONE), 2, PLACE_AT);
        assertEquals(List.of(5), director.review(Map.of(), List.of(HOT_ONE), PLACE_AT),
                "taken up by higher-value work: the guard is withdrawn");

        director.attach(List.of(lance(5, 120)), List.of(HOT_ONE), 2, PLACE_AT);
        assertEquals(List.of(5), director.review(Map.of(5, new MachineGuardDirector.Machine(5, 100, 50, 1, 4)),
                List.of(HOT_ONE), PLACE_AT), "below useful strength");
    }

    @Test
    void usefulIsHalfTheLanceAndOneAtLeast() {
        assertTrue(MachineGuardDirector.useful(1, 1));
        assertTrue(MachineGuardDirector.useful(1, 2));
        assertFalse(MachineGuardDirector.useful(1, 3));
        assertFalse(MachineGuardDirector.useful(0, 0));
    }

    /** A trench (stretch 0) on row 12 and a barracks (envelope 1) at 40..55 x 20..30 on a 100x40 field. */
    private static GarrisonableRegistry trenchAndBarracks(TacticalNode barracks) {
        List<Doodad> props = new ArrayList<>();
        DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        for (int x = 10; x <= 25; x++) props.add(new Doodad(x, 12, def));
        FightingPositionIndex index = FightingPositionIndex.build(100, 40, props, (x, y) -> true);
        return GarrisonableRegistry.build(index, 100, 40, 8, new int[0],
                List.of(new GarrisonableRegistry.Envelope(barracks, new int[]{47, 21, 42, 21})));
    }

    private static TacticalNode barracks() {
        return new TacticalNode(TacticalNode.Kind.BARRACKS, 47, 25, 40, 20, 55, 30, Faction.DEFENDER, 60, 0);
    }

    @Test
    void anOrderToManABuildingMansItsEnvelopeAsARunOrderMansItsStretch() {
        TacticalNode building = barracks();
        GarrisonableRegistry places = trenchAndBarracks(building);
        assertEquals(0, MachineGuardDirector.placeManned(
                ObjectiveAssignment.defendRun(3, 17, 12, 6, places.runOf(0)), places));
        assertEquals(1, MachineGuardDirector.placeManned(
                ObjectiveAssignment.holdEnvelope(3, barracks(), 47, 21, 6), places),
                "a copy of the building's node names the same envelope");
        assertEquals(1, MachineGuardDirector.placeManned(GarrisonDirector.orderFor(3, 1, places), places),
                "the manning director's own order for the envelope");
        assertEquals(-1, MachineGuardDirector.placeManned(ObjectiveAssignment.holdNode(3, building), places),
                "an ordinary hold is not an order to man a garrisonable");
        assertEquals(-1, MachineGuardDirector.placeManned(ObjectiveAssignment.attackMove(3, 47, 25), places));
        assertEquals(-1, MachineGuardDirector.placeManned(null, places));
    }

    @Test
    void aMannedPressedBuildingIsAPlaceALanceIsSentTo() {
        GarrisonableRegistry places = trenchAndBarracks(barracks());
        List<MachineGuardDirector.Place> manned = MachineGuardDirector.manned(Set.of(0, 1), new int[]{1}, places);
        assertEquals(2, manned.size());
        MachineGuardDirector.Place building = manned.get(0);
        assertEquals(1, building.garrisonable(), "the pressed building first");
        assertTrue(building.hot());
        assertEquals(1, places.idAt(building.cellX(), building.cellY()), "its cell is one of its posts, inside it");
        assertFalse(manned.get(1).hot(), "the quiet trench after");

        MachineGuardDirector director = new MachineGuardDirector();
        List<MachineGuardDirector.Attach> sent = director.attach(
                List.of(new MachineGuardDirector.Machine(5, 60, 25, 2, 2)), manned, 2, places::idAt);
        assertEquals(1, sent.size());
        assertEquals(1, sent.get(0).garrisonable(), "a lance stands guard by the manned building");
        assertTrue(director.review(Map.of(5, new MachineGuardDirector.Machine(5, 58, 25, 2, 2)), manned,
                places::idAt).isEmpty(), "and stays while it is manned and pressed");
    }
}
