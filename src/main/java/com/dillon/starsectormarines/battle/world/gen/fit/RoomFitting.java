package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * How one kind of compartment is furnished.
 *
 * <p>A fitting owns the arrangement a room of its purpose takes — bunk rows
 * either side of an aisle, benches along a bulkhead, gantries with service
 * access — and produces that arrangement at whatever {@link RoomFit} it is
 * handed. It never decides how large the room is: the footprint arrives already
 * placed, and a refit changes what fits inside it.
 *
 * <p>Fittings are selected by {@link #purpose()} alone. A compartment says what
 * it is for, and that is the whole of the dispatch — no coordinate table, no
 * per-deck special case.
 */
public interface RoomFitting {

    /** The compartment purpose this fitting furnishes. */
    RoomPurpose purpose();

    /**
     * Furnish one compartment. Reserve circulation before placing anything, and
     * place fixtures in groups rather than as isolated points.
     */
    void fit(RoomFloor floor);

    /**
     * Where this arrangement can meet the deck, in the canonical frame — or
     * nothing, for a room that takes a door wherever the deck offers one.
     *
     * <p>Read before placement, not during the fill: a door is a constraint on
     * where a room may go, and a fitting that only found out afterwards had to
     * make room for it by throwing away part of its own arrangement. Alternatives
     * are tried in order, and a room whose hookups cannot be served anywhere is
     * placed with an ordinary door rather than left off the deck.
     */
    default List<Hookup> hookups(RoomShape canonical) {
        return List.of();
    }

    /**
     * Where a door may be <em>added</em> later, beyond the ones this room is
     * entered by — or nothing, for an arrangement that has no room for another.
     *
     * <p>A separate question from {@link #hookups}, and separate for a reason
     * that only shows on open ground. A hookup is how the room is entered, and
     * it is answered before placement because it decides where the room may go;
     * the placer scores a position by how many of the slots the deck around it
     * can serve. A ward, though, is packed into solid ground and opens its yard
     * out of whatever is left, so at the moment a building is placed there is
     * no yard for a second door to face — and the further ways in it earns are
     * cut afterwards, by a family that has ground to open. Folding those
     * positions into the hookups instead would make them part of the placement
     * score, which moves every room on every deck to satisfy doors nobody was
     * going to cut there.
     *
     * <p>Cells are on the bulkhead ring in the canonical frame, exactly as a
     * hookup's are. What a fitting states here it has to have arranged for: a
     * bay opens its stores aisle behind each of these, so the door leads
     * somewhere rather than onto the flank of a gantry.
     */
    default List<Hookup.DoorSlot> furtherDoors(RoomShape canonical) {
        return List.of();
    }

    /**
     * Whether this arrangement has a handedness — a front and a back that a
     * mirror image would reverse.
     *
     * <p>Separate from {@link #hookups} because they answer different
     * questions. A bay is handed: its shop is at one end and its doors at the
     * other, so flipping it is a different room and worth the four extra poses
     * to consider. Ranks either side of a centred aisle are not handed at all,
     * and enumerating their mirrors would quadruple the search for the most
     * numerous compartment on the deck in exchange for nothing.
     */
    default boolean handed() {
        return false;
    }
}
