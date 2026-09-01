package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bay is the deck's way in and out, and now says where.
 *
 * <p>The recipe has always insisted a bay reach the ship's side — a bay buried
 * amidships opens onto the compartment next door — but nothing recorded
 * <em>which</em> side it reached, so the one room whose whole purpose is the
 * outside could not say where the outside was. A fitting cannot answer it
 * either: it sees a floor, a pose and some doors, and the placer is what pushed
 * the room against the hull.
 *
 * <p>The deck-level cases here are asked of a generated hull because that is
 * what the claim is about: the placer put the room somewhere, and the door has
 * to be on the side that turned out to face space.
 */
class ABayHasAWayOutTest {

    private static final long SEED = 11L;

    private record Deck(MapResult map, DeckGraph graph) { }

    private static Deck generate(HullClass hull, int minCrew, int maxCrew, int cargo) {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult map = generator.generateDeck(
                DeckSizing.planFor(hull, HullRole.TROOP_TRANSPORT, minCrew, maxCrew, cargo,
                        0.28f),
                SEED, null);
        return new Deck(map, generator.getLastDeckGraph());
    }

    /** Every bay the deck placed has a door, and it is on the outside of the ship. */
    @Test
    void everyBayHasADoorOntoSpace() {
        Deck deck = generate(HullClass.CRUISER, 10, 250, 50);

        int bays = 0;
        for (DeckGraph.Compartment room : deck.graph().compartments()) {
            if (room.purpose() != RoomPurpose.HANGAR) continue;
            bays++;
            BayAperture door = deck.graph().apertureOf(room.id());
            assertNotNull(door, "bay " + room.id() + " has no way off the ship");
            assertTrue(door.widthCells() > 0);
            assertEquals(1, Math.abs(door.outDx()) + Math.abs(door.outDy()),
                    "a door out of a hull faces one cardinal");
        }
        assertTrue(bays > 0, "the transport was generated without a boat bay");
        assertEquals(bays, deck.graph().apertures().size(),
                "the deck published a door for something that is not a bay");
    }

    /**
     * And it is on the side the fitting asked for, not merely on some side.
     *
     * <p>A boat bay names its outboard bulkhead, the placer puts that side on
     * the hull, and the fitting clears the deck behind it and ranks the boats
     * nosed at it. A door recovered from a longer run of vacuum along one of the
     * ends would be round the corner from the lane that leads to it, and the
     * bay's whole arrangement would be pointing at a bulkhead.
     */
    @Test
    void theDoorIsOnTheSideTheFittingNamed() {
        Deck deck = generate(HullClass.CRUISER, 10, 250, 50);
        int[] authored = RoomFittings.forPurpose(RoomPurpose.HANGAR).outboard();
        assertNotNull(authored, "a boat bay no longer names the side it opens through");

        int bays = 0;
        for (DeckGraph.Compartment room : deck.graph().compartments()) {
            if (room.purpose() != RoomPurpose.HANGAR) continue;
            BayAperture door = deck.graph().apertureOf(room.id());
            if (door == null) continue;
            bays++;
            int[] named = room.pose().mapDirection(authored[0], authored[1]);
            assertEquals(named[0] + "," + named[1], door.outDx() + "," + door.outDy(),
                    "bay " + room.id() + " opens through a bulkhead it was not built"
                            + " around");
        }
        assertTrue(bays > 0, "the transport was generated without a boat bay");
    }

    /**
     * The deck just inboard of a door is deck. It is where a boat is moved to
     * before it goes out and the first thing it stands on coming back, so a door
     * opening onto a bulkhead is a bay with a picture of a way out.
     */
    @Test
    void thereIsDeckInsideEveryDoor() {
        Deck deck = generate(HullClass.CRUISER, 10, 250, 50);

        for (BayAperture door : deck.graph().apertures()) {
            float[] step = door.inboardStep();
            int x = (int) step[0];
            int y = (int) step[1];
            assertTrue(deck.map().grid.inBounds(x, y)
                            && deck.map().grid.isWalkable(x, y),
                    "the cell inside the door at " + x + "," + y + " is not deck");
        }
    }

    /**
     * A transom is contact, not a door. An engine room has to sit against the
     * stern to be driving anything, and giving that the same treatment would
     * publish an opening onto space at the back of every ship in the fleet.
     */
    @Test
    void aTransomIsNotADoor() {
        Deck deck = generate(HullClass.CRUISER, 10, 250, 50);

        for (DeckGraph.Compartment room : deck.graph().compartments()) {
            if (room.purpose() == RoomPurpose.HANGAR) continue;
            assertNull(deck.graph().apertureOf(room.id()),
                    room.purpose() + " was given a door onto space");
        }
    }

    /** The smallest hull that carries a boat still gets a way to launch it. */
    @Test
    void aGigBayHasADoorToo() {
        Deck deck = generate(HullClass.FRIGATE, 8, 40, 20);

        assertEquals(1, deck.graph().apertures().size(),
                "a frigate's gig bay has " + deck.graph().apertures().size() + " doors");
    }

    /** Out is out: the offship point leaves the hull and the inboard step comes back in. */
    @Test
    void theDoorKnowsWhichWayIsOut() {
        BayAperture door = new BayAperture(3, 20f, 8f, 0, -1, 7);

        assertEquals(20f, door.offshipAt(6f)[0], 0.001f);
        assertEquals(2f, door.offshipAt(6f)[1], 0.001f, "six cells out is not out");
        assertEquals(9f, door.inboardStep()[1], 0.001f, "the step inside is outside");
    }

    /** A door that faces nowhere, or has no width, is not a door. */
    @Test
    void aDoorIsRefusedIfItIsNotOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new BayAperture(1, 4f, 4f, 1, 1, 5),
                "a door was accepted facing diagonally out of the hull");
        assertThrows(IllegalArgumentException.class,
                () -> new BayAperture(1, 4f, 4f, 1, 0, 0),
                "a door with no width was accepted");
    }
}
