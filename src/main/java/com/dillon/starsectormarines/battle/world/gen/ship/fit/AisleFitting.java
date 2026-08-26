package com.dillon.starsectormarines.battle.world.gen.ship.fit;

import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;


import java.util.List;

/**
 * The arrangement most shipboard compartments actually take: a working aisle
 * down the long axis, with fixture groups ranked along both sides of it.
 *
 * <p>Berths, mess tables, weapon racks and stores all take this shape, so it is
 * built once and configured rather than written out per room. What a theme
 * supplies is the group — an anchor fixture and the satellites that make it read
 * as somewhere used — and how much floor that group needs. What the
 * {@link RoomFit} supplies is the spacing, which is the whole of the refit: the
 * same compartment ranks more groups when the aisle is cut to the width people
 * pass in and the gaps between blocks are closed up.
 *
 * <p>The aisle is reserved <em>before</em> anything is placed, and every door
 * gets a stub joining it to that aisle. Furnishing first and hoping a path
 * survives is how a room ends up with a bunk against its own hatch.
 */
public final class AisleFitting implements RoomFitting {

    private final RoomPurpose purpose;
    private final FixtureGroup group;

    /** Cells nobody may furnish, however tight the room. Two abreast, as everywhere else. */
    private static final int MIN_CLEAR = 2;

    public AisleFitting(RoomPurpose purpose, FixtureGroup group) {
        this.purpose = purpose;
        this.group = group;
    }

    @Override
    public RoomPurpose purpose() {
        return purpose;
    }

    @Override
    public void fit(CompartmentFloor floor) {
        boolean lengthwise = floor.width() >= floor.height();
        int across = lengthwise ? floor.height() : floor.width();
        int along = lengthwise ? floor.width() : floor.height();
        RoomFit fit = floor.fit();

        // Rank inboard from each bulkhead, then leave the rest as working floor.
        // Filling every band instead produces an even lattice wall to wall,
        // which is the fill defect this exists to avoid: a room reads as used
        // because its gear is against the sides and its middle is clear, not
        // because props are spread evenly over it.
        // Clear floor scales with the room rather than being a fixed aisle,
        // or a deep room just takes more ranks until it is furniture wall to
        // wall. But the floor is what people walk in, not a third of the room:
        // demanding a third left an eight-by-six berth with room for no ranks
        // at all, so the most numerous compartment on every deck came out
        // empty. A berth is small because bunks fill it.
        int clear = Math.max(MIN_CLEAR, across / 4);
        int affordable = (across - clear) / (2 * group.depth());
        int ranks = Math.min(fit.ranks(), affordable);
        if (ranks <= 0 && across >= group.depth() + MIN_CLEAR) {
            ranks = 1;      // one rank against one bulkhead still beats nothing
        }
        int banded = ranks * group.depth();

        int aisleStart = banded;
        int aisle = Math.max(0, across - 2 * banded);
        reserve(floor, lengthwise, 0, aisleStart, along, aisle);
        for (DeckGraph.Compartment.Door door : floor.localDoors()) {
            stubFromDoor(floor, lengthwise, door, aisleStart, Math.max(1, aisle));
        }

        for (int rank = 0; rank < ranks; rank++) {
            rankRow(floor, lengthwise, rank * group.depth(), along);
            int opposite = across - (rank + 1) * group.depth();
            // A room only deep enough for one rank gets one, not the same rank
            // laid twice on top of itself.
            if (opposite >= (rank + 1) * group.depth() + MIN_CLEAR) {
                rankRow(floor, lengthwise, opposite, along);
            }
        }
    }

    /** One row of groups running the length of the compartment. */
    private void rankRow(CompartmentFloor floor, boolean lengthwise, int across, int along) {
        int pitch = group.width() + floor.fit().gap();
        for (int offset = 0; offset + group.width() <= along; offset += pitch) {
            place(floor, lengthwise, offset, across);
        }
    }

    /** Lay one group down, anchor first, then whatever the anchor is used with. */
    private void place(CompartmentFloor floor, boolean lengthwise, int along, int across) {
        if (!floor.place(group.anchor(), x(lengthwise, along, across), y(lengthwise, along, across))) {
            return;
        }
        for (FixtureGroup.Satellite satellite : group.satellites()) {
            floor.place(satellite.id(),
                    x(lengthwise, along + satellite.along(), across + satellite.across()),
                    y(lengthwise, along + satellite.along(), across + satellite.across()));
        }
    }

    private void stubFromDoor(CompartmentFloor floor, boolean lengthwise,
                              DeckGraph.Compartment.Door door, int aisleStart, int aisle) {
        int doorAlong = lengthwise ? door.x() : door.y();
        int doorAcross = lengthwise ? door.y() : door.x();
        int from = Math.min(doorAcross, aisleStart);
        int span = Math.abs(doorAcross - aisleStart) + aisle;
        reserve(floor, lengthwise, clamp(doorAlong, 0, lengthwise ? floor.width() : floor.height()),
                from, 1, span);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max - 1, value));
    }

    private void reserve(CompartmentFloor floor, boolean lengthwise,
                         int along, int across, int alongSpan, int acrossSpan) {
        floor.reserveLane(
                lengthwise ? along : across,
                lengthwise ? across : along,
                lengthwise ? alongSpan : acrossSpan,
                lengthwise ? acrossSpan : alongSpan);
    }

    private static int x(boolean lengthwise, int along, int across) {
        return lengthwise ? along : across;
    }

    private static int y(boolean lengthwise, int along, int across) {
        return lengthwise ? across : along;
    }

    /**
     * An anchor fixture and the things that sit with it. A bunk is a bunk, its
     * footlocker, and the kit on top of the locker; a mess table is the table
     * and its benches. Placing the anchor alone is what makes a generated room
     * read as a diagram of a room rather than one people use.
     */
    public record FixtureGroup(String anchor, int width, int depth, List<Satellite> satellites) {

        public FixtureGroup {
            satellites = List.copyOf(satellites);
        }

        /** One member of a group, offset from the anchor in the aisle's own axes. */
        public record Satellite(String id, int along, int across) {}

        public static FixtureGroup of(String anchor, int width, int depth, Satellite... members) {
            return new FixtureGroup(anchor, width, depth, List.of(members));
        }
    }
}
