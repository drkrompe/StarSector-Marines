package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * Gear around the walls, floor kept clear.
 *
 * <p>The arrangement for rooms whose whole purpose is the empty space in the
 * middle: a bay servicing a walker, a hangar a shuttle has to be moved through,
 * a range with a firing lane down it. Ranking fixtures across those the way a
 * berth is ranked would fill in the one thing they exist to provide, so they
 * were left bare instead — which read as unfinished rather than as deliberate.
 *
 * <p>Working the perimeter gives them what they should have had: benches,
 * lockers and drums against the bulkheads, and clear deck for the machine.
 */
public final class PerimeterFitting implements RoomFitting {

    private final RoomPurpose purpose;
    private final AisleFitting.FixtureGroup group;

    public PerimeterFitting(RoomPurpose purpose, AisleFitting.FixtureGroup group) {
        this.purpose = purpose;
        this.group = group;
    }

    @Override
    public RoomPurpose purpose() {
        return purpose;
    }

    @Override
    public void fit(RoomFloor floor) {
        // Everything that is not the wall band is lane, so nothing later can
        // encroach on the clearance this room exists to keep.
        int band = group.depth();
        floor.reserveLane(band, band,
                Math.max(0, floor.width() - 2 * band), Math.max(0, floor.height() - 2 * band));
        for (Doorway door : floor.localDoors()) {
            floor.reserveLane(door.x() - 1, door.y() - 1, 3, 3);
        }

        int pitch = group.width() + floor.fit().gap() + 1;
        for (int x = 0; x + group.width() <= floor.width(); x += pitch) {
            place(floor, x, 0);
            place(floor, x, floor.height() - group.depth());
        }
        for (int y = band; y + group.depth() <= floor.height() - band; y += pitch) {
            place(floor, 0, y);
            place(floor, floor.width() - group.width(), y);
        }
    }

    private void place(RoomFloor floor, int x, int y) {
        if (!place(floor, group.anchor(), x, y, group.affordance())) return;
        for (AisleFitting.FixtureGroup.Satellite satellite : group.satellites()) {
            place(floor, satellite.id(), x + satellite.along(), y + satellite.across(),
                    satellite.affordance());
        }
    }

    /**
     * One fixture, and the work at it where the group declared any.
     *
     * <p>A perimeter room is not only scenery round a clear middle. A boat bay's
     * stores are worked exactly as a hold's are; what makes the bay different is
     * that the deck between them has to stay clear for the boat, which is this
     * arrangement's whole subject and says nothing about whether the gear along
     * the sides is somebody's job.
     */
    private static boolean place(RoomFloor floor, String id, int x, int y,
                                 Affordance affordance) {
        return affordance == null
                ? floor.place(id, x, y)
                : floor.place(id, x, y, affordance);
    }
}
