package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A firing range is the one compartment whose empty deck is the point, and the
 * one whose hatches cannot go just anywhere.
 */
class FiringRangeFittingTest {

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

    private static List<DeckGraph.Compartment> rooms(DeckGraph graph, RoomPurpose purpose) {
        List<DeckGraph.Compartment> found = new ArrayList<>();
        for (DeckGraph.Compartment room : graph.compartments()) {
            if (room.purpose() == purpose) found.add(room);
        }
        return found;
    }

    private static List<FixtureTask> work(Deck deck, DeckGraph.Compartment room,
                                          Affordance affordance) {
        List<FixtureTask> found = new ArrayList<>();
        for (FixtureTask task : deck.map().fixtureTasks) {
            if (task.affordance() == affordance && room.contains(task.cellX(), task.cellY())) {
                found.add(task);
            }
        }
        return found;
    }

    /**
     * Nothing stands between a shooter and what they are shooting at.
     *
     * <p>The lane is walked cell by cell from the firing point to the butts. A
     * fixture anywhere along it is a round stopped early, and the fill has
     * several ways to put one there — the ready end works right up to the firing
     * line, and a hatch approach is cleared straight through whatever it meets.
     */
    @Test
    void aLaneIsClearFromTheFiringPointToTheButts() {
        Set<Long> blocked = new HashSet<>();
        int lanes = 0;
        for (long seed : SEEDS) {
            Deck deck = generate(seed);
            blocked.clear();
            for (Doodad doodad : deck.map().doodads) {
                for (int dx = 0; dx < Math.max(1, doodad.footprintCellsX); dx++) {
                    for (int dy = 0; dy < Math.max(1, doodad.footprintCellsY); dy++) {
                        blocked.add(key(doodad.cellX + dx, doodad.cellY + dy));
                    }
                }
            }
            for (DeckGraph.Compartment range : rooms(deck.graph(), RoomPurpose.FIRING_RANGE)) {
                List<FixtureTask> firing = work(deck, range, Affordance.PRACTICE);
                assertTrue(!firing.isEmpty(),
                        "seed " + seed + ": range " + range.id() + " offers nowhere to shoot");
                for (FixtureTask point : firing) {
                    lanes++;
                    int stepX = Integer.signum(point.fixtureX() - point.cellX());
                    int stepY = Integer.signum(point.fixtureY() - point.cellY());
                    // Skip the barrier the shooter fires over, which is the one
                    // fixture that belongs between them and the butts.
                    int x = point.cellX() + 2 * stepX;
                    int y = point.cellY() + 2 * stepY;
                    while (x != point.fixtureX() || y != point.fixtureY()) {
                        assertTrue(!blocked.contains(key(x, y)),
                                "seed " + seed + ": range " + range.id()
                                        + " has a fixture at " + x + "," + y
                                        + ", downrange of the firing point at "
                                        + point.cellX() + "," + point.cellY());
                        x += stepX;
                        y += stepY;
                    }
                }
            }
        }
        assertTrue(lanes > 0, "no deck generated a range, so nothing was checked");
    }

    /**
     * Every way in is behind the firing line.
     *
     * <p>The reason this room authors its hookups at all. A berth's authored
     * doors buy an arrangement and cost it capacity; a range's stop somebody
     * walking out of a hatch into the beaten zone, which is not a fill defect
     * but a room that is wrong about what it is.
     */
    @Test
    void everyWayInIsBehindTheFiringLine() {
        RoomShape canonical = RoomRecipe.RANGE.shape();
        Set<Long> authored = new HashSet<>();
        for (Hookup hookup : RoomFittings.forPurpose(RoomPurpose.FIRING_RANGE)
                .hookups(canonical)) {
            for (Hookup.DoorSlot slot : hookup.slots()) {
                for (int[] cell : slot.cells()) authored.add(key(cell[0], cell[1]));
            }
        }
        assertTrue(!authored.isEmpty(), "the range authored no hookups to check against");

        for (long seed : SEEDS) {
            Deck deck = generate(seed);
            for (DeckGraph.Compartment range : rooms(deck.graph(), RoomPurpose.FIRING_RANGE)) {
                assertTrue(!range.doors().isEmpty(),
                        "seed " + seed + ": range " + range.id() + " cannot be entered");
                for (DeckGraph.Compartment.Door door : range.doors()) {
                    int[] cell = range.pose().unmap(door.x() - range.left(), door.y() - range.top(),
                            canonical.width(), canonical.height());
                    assertTrue(authored.contains(key(cell[0], cell[1])),
                            "seed " + seed + ": a range hatch sits at " + cell[0] + ","
                                    + cell[1] + ", which is not behind the firing line");
                }
            }
        }
    }

    /**
     * A range comes out furnished on every seed.
     *
     * <p>A fill that would seal its compartment is thrown away entire, and this
     * room is the one most likely to earn that: the ready end works the deepest
     * bulkhead, which is exactly where its hatch is. The failure is silent —
     * a bare rectangle labelled "firing range" looks like a room nobody has got
     * to yet, not like one that was furnished and discarded.
     */
    @Test
    void aRangeIsFurnishedWhereverTheDeckPutsIt() {
        for (long seed : SEEDS) {
            Deck deck = generate(seed);
            List<DeckGraph.Compartment> ranges = rooms(deck.graph(), RoomPurpose.FIRING_RANGE);
            assertTrue(!ranges.isEmpty(), "seed " + seed + ": a troop transport with no range");
            for (DeckGraph.Compartment range : ranges) {
                int fixtures = 0;
                for (Doodad doodad : deck.map().doodads) {
                    if (range.contains(doodad.cellX, doodad.cellY)) fixtures++;
                }
                assertTrue(fixtures > 0,
                        "seed " + seed + ": range " + range.id()
                                + " came out as bare deck, which means its fill was discarded");
            }
        }
    }

    /** A mess seats people, and the seat is where the meal is rather than the table. */
    @Test
    void aMessPublishesItsSeats() {
        for (long seed : SEEDS) {
            Deck deck = generate(seed);
            List<DeckGraph.Compartment> messes = rooms(deck.graph(), RoomPurpose.MESS_HALL);
            assertTrue(!messes.isEmpty(), "seed " + seed + ": a troop transport with no mess");
            for (DeckGraph.Compartment mess : messes) {
                assertTrue(!work(deck, mess, Affordance.MESS).isEmpty(),
                        "seed " + seed + ": mess " + mess.id() + " is a hall, not a mess");
            }
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    /**
     * Nobody walks down the lane, and everybody can see and shoot along it.
     *
     * <p>Reserving the beaten zone only kept the fill out of it. What a range
     * needs is that the deck itself refuses the route — otherwise the shortest
     * way across the compartment runs between the firing line and the butts, and
     * the pathfinder will happily take it.
     *
     * <p>Shut is not walled, and the second half matters as much as the first.
     * The lane has to carry sight and rounds or the room does not work as a
     * range and, in a boarding action, does not work as the long open sightline
     * it ought to be.
     */
    @Test
    void nobodyWalksDownTheLaneAndEverybodyCanShootAlongIt() {
        int checked = 0;
        for (long seed : SEEDS) {
            Deck deck = generate(seed);
            for (DeckGraph.Compartment range : rooms(deck.graph(), RoomPurpose.FIRING_RANGE)) {
                for (FixtureTask point : work(deck, range, Affordance.PRACTICE)) {
                    assertTrue(deck.map().grid.isWalkable(point.cellX(), point.cellY()),
                            "seed " + seed + ": the firing point at " + point.cellX() + ","
                                    + point.cellY() + " is somewhere nobody can stand");
                    int stepX = Integer.signum(point.fixtureX() - point.cellX());
                    int stepY = Integer.signum(point.fixtureY() - point.cellY());
                    int x = point.cellX() + 2 * stepX;
                    int y = point.cellY() + 2 * stepY;
                    while (x != point.fixtureX() + stepX || y != point.fixtureY() + stepY) {
                        checked++;
                        assertTrue(!deck.map().grid.isWalkable(x, y),
                                "seed " + seed + ": " + x + "," + y
                                        + " is downrange and can be walked into");
                        assertTrue(deck.map().grid.isSeeThrough(x, y),
                                "seed " + seed + ": " + x + "," + y
                                        + " is downrange and stops sight, so the lane is a wall");
                        assertTrue(!deck.map().topology.isWall(x, y),
                                "seed " + seed + ": " + x + "," + y
                                        + " was tagged a wall, so the beaten zone renders as one");
                        assertEquals(0, deck.map().grid.getWallHp(x, y),
                                "seed " + seed + ": " + x + "," + y
                                        + " was given wall hit points, so the lane is shootable");
                        x += stepX;
                        y += stepY;
                    }
                }
            }
        }
        assertTrue(checked > 0, "no deck generated a lane, so nothing was checked");
    }
}
