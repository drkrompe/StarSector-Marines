package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a generated berthing compartment owes: racks somebody can sleep in,
 * stowage they can square their kit away in, and hatches all on one side.
 */
class BerthingFittingTest {

    private static final long[] SEEDS = { 1L, 42L, 1337L };

    private record Deck(MapResult map, DeckGraph graph) { }

    private static Deck generate(long seed) {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult map = generator.generateDeck(
                DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                        10, 250, 50, 0.28f),
                seed, null);
        return new Deck(map, generator.getLastDeckGraph());
    }

    private static List<DeckGraph.Compartment> berths(DeckGraph graph) {
        List<DeckGraph.Compartment> berths = new ArrayList<>();
        for (DeckGraph.Compartment room : graph.compartments()) {
            if (room.purpose() == RoomPurpose.BARRACKS
                    || room.purpose() == RoomPurpose.CREW_QUARTERS) {
                berths.add(room);
            }
        }
        return berths;
    }

    private static int count(Deck deck, DeckGraph.Compartment room, Affordance affordance) {
        int total = 0;
        for (FixtureTask task : deck.map().fixtureTasks) {
            if (task.affordance() != affordance) continue;
            if (room.contains(task.cellX(), task.cellY())) total++;
        }
        return total;
    }

    /**
     * The number the program plans against is a floor the room actually meets.
     *
     * <p>Berthing is where a capacity nobody counted does the most damage: the
     * program sizes the whole ship's berthing off it, so a compartment that
     * holds fewer racks than it is credited with berths a company that does not
     * fit aboard. It went the other way here — the recipe claimed twelve for as
     * long as nothing was laid out inside one — which is why this asserts the
     * floor rather than the exact figure. Hatches served vary by deck; the
     * promise does not.
     */
    @Test
    void everyBerthHoldsAtLeastTheRacksItIsCreditedWith() {
        for (long seed : SEEDS) {
            Deck deck = generate(seed);
            List<DeckGraph.Compartment> berths = berths(deck.graph());
            assertTrue(!berths.isEmpty(), "seed " + seed + ": a troop transport with no berthing");
            for (DeckGraph.Compartment room : berths) {
                int credited = room.purpose() == RoomPurpose.BARRACKS
                        ? RoomRecipe.TROOP_BERTHING.provides()
                        : RoomRecipe.CREW_BERTHING.provides();
                assertTrue(count(deck, room, Affordance.REST) >= credited,
                        "seed " + seed + ": berth " + room.id() + " holds "
                                + count(deck, room, Affordance.REST)
                                + " racks and is credited with " + credited);
                assertTrue(count(deck, room, Affordance.STOW) > 0,
                        "seed " + seed + ": berth " + room.id()
                                + " gives its occupants nowhere to keep their kit");
            }
        }
    }

    /**
     * Both hatches on one bulkhead, at the cells the room named.
     *
     * <p>The side is the point. A berth entered from opposite sides is a passage
     * with bunks in it — the through route runs between the ranks, and everybody
     * asleep there gets walked past all watch. Keeping them together is what
     * leaves the far rank against unbroken hull.
     */
    @Test
    void berthHatchesShareOneBulkhead() {
        RoomShape canonical = RoomRecipe.TROOP_BERTHING.shape();
        Set<Long> authored = new HashSet<>();
        for (Hookup hookup : RoomFittings.forPurpose(RoomPurpose.BARRACKS).hookups(canonical)) {
            for (Hookup.DoorSlot slot : hookup.slots()) {
                for (int[] cell : slot.cells()) {
                    authored.add(((long) cell[0] << 32) ^ (cell[1] & 0xffffffffL));
                }
            }
        }
        assertTrue(!authored.isEmpty(), "berthing authored no hookups to check against");

        int twoHatched = 0;
        for (long seed : SEEDS) {
            Deck deck = generate(seed);
            for (DeckGraph.Compartment room : berths(deck.graph())) {
                if (room.purpose() != RoomPurpose.BARRACKS) continue;
                Set<Integer> sides = new HashSet<>();
                for (DeckGraph.Compartment.Door door : room.doors()) {
                    int[] cell = room.pose().unmap(door.x() - room.left(), door.y() - room.top(),
                            canonical.width(), canonical.height());
                    assertTrue(authored.contains(
                                    ((long) cell[0] << 32) ^ (cell[1] & 0xffffffffL)),
                            "seed " + seed + ": a berth hatch sits at " + cell[0] + ","
                                    + cell[1] + ", which is not a cell it hooks up on");
                    sides.add(cell[1]);
                }
                assertEquals(1, sides.size(),
                        "seed " + seed + ": berth " + room.id()
                                + " is entered from more than one bulkhead");
                if (room.doors().size() > 2) twoHatched++;
            }
        }
        assertTrue(twoHatched > 0,
                "no deck served a berth its second hatch, so nothing proves the pair works");
    }

    /**
     * A hatch takes one rack slot and not the one beside it as well.
     *
     * <p>A doorway is widened to two cells wherever it can be, and the widening
     * used to be free to leave the slot the room named — so a berth that
     * budgeted a rack for its hatch lost the neighbouring rack too, in a
     * different place on every deck. The authored cells are the whole doorway.
     */
    @Test
    void aHatchStaysInsideTheDoorwayTheRoomAuthored() {
        RoomShape canonical = RoomRecipe.TROOP_BERTHING.shape();
        for (long seed : SEEDS) {
            Deck deck = generate(seed);
            for (DeckGraph.Compartment room : berths(deck.graph())) {
                Set<Integer> columns = new HashSet<>();
                for (DeckGraph.Compartment.Door door : room.doors()) {
                    int[] cell = room.pose().unmap(door.x() - room.left(), door.y() - room.top(),
                            canonical.width(), canonical.height());
                    columns.add(cell[0]);
                }
                assertTrue(columns.size() <= 2 * 2,
                        "seed " + seed + ": berth " + room.id() + " spent "
                                + columns.size() + " rack slots on hatches");
            }
        }
    }
}
