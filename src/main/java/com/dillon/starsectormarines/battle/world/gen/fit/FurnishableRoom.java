package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * A room that has been placed and is ready to be furnished: where its floor is,
 * what shape that floor is, which way round it ended up, where people come in,
 * and what it is for.
 *
 * <p>This is the whole of what a {@link RoomFitting} is handed, and deliberately
 * so. Fitting out a room is the same job whether the room is a compartment
 * amidships or a shed inside a walled yard — reserve the circulation, place the
 * fixtures in groups, leave the machine somewhere to stand — and a fitting that
 * could ask which of those it was dealing with would grow a branch for each.
 * The two families differ in how a room comes to exist, not in how it is
 * furnished, so the seam is drawn here rather than at the fitting.
 *
 * <p>The {@link #pose()} is carried for the half of the story the mask cannot
 * tell. A flipped rectangle has the same mask as an unflipped one, so a fitting
 * that recovered its bearings from the footprint would lay its shop and its
 * doors at the wrong ends of half the rooms it was given. A fitting authors
 * facing one way and reads the pose to find out where that ended up.
 */
public interface FurnishableRoom {

    /** The floor this room owns, in its own local frame — not a bounding box. */
    RoomShape shape();

    /** Cell the shape's local origin sits on. */
    int originX();

    /** Cell the shape's local origin sits on. */
    int originY();

    /** How the shape was turned and flipped to get here. */
    RoomPose pose();

    /** What the room is for, which is the whole of the fitting dispatch. */
    RoomPurpose purpose();

    /**
     * Cells cut through this room's wall, in map coordinates.
     *
     * <p>Circulation inside a room is authored from its entries outward, so a
     * fill that does not know where people come in cannot leave them a way
     * through.
     */
    List<Doorway> doors();
}
