package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * One building a fortress owes: what it is, the footprint its function needs,
 * the ward it belongs in, and whether it has to reach the perimeter.
 *
 * <p>The compound answer to a deck's room recipe, and the point at which a
 * fortress stops being a district with a wall round it. A parcel-first
 * generator can only offer a purpose whatever rectangle the partition left, so
 * a motor pool ends up in whichever shed is smallest. Here the footprint comes
 * from the function — a vehicle shed is as long as the bays inside it, a
 * magazine is squat and thick, a barrack block is a slab — and the packer finds
 * it somewhere that shape fits.
 *
 * @param purpose what the building is, which is also what furnishes it
 * @param shape the floor its function needs, authored rather than inherited
 * @param ward how deep in the fortress it belongs
 * @param perimeter whether a placement that merely fits is still wrong
 * @param count how many of it a fortress owes
 */
public record FortressBuilding(RoomPurpose purpose, RoomShape shape, Ward ward,
                               RoomPacker.EdgeContact perimeter, int count) {

    public FortressBuilding(RoomPurpose purpose, RoomShape shape, Ward ward, int count) {
        this(purpose, shape, ward, RoomPacker.EdgeContact.NONE, count);
    }

    public int area() {
        return shape.area();
    }
}
