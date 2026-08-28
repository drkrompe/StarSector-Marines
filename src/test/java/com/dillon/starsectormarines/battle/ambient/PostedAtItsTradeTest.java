package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A room is one trade's posting, not a billet for everybody who could help out.
 *
 * <p>Secondary jobs are shared on purpose: half the trades aboard handle stores
 * at some point in their rotation. So a compartment that published stowage read
 * as a station for the storekeeper, the technician, the machinist, the medic,
 * the armourer and the engine watch at once, and a hull that fills its leftover
 * corners with spares pockets crewed a thousand engineers. Where somebody is
 * <em>stationed</em> is decided by whose room it is.
 */
class PostedAtItsTradeTest {

    private record Site(int id, RoomPurpose purpose, int centreX, int centreY)
            implements JobSite {
        @Override
        public boolean contains(int cellX, int cellY) {
            return true;
        }
    }

    @Test
    void aStoresPocketIsTheStorekeepersPostingAndNobodyElses() {
        Site pocket = new Site(1, RoomPurpose.PARTS_CAGE, 10, 10);
        List<FixtureTask> stores = List.of(
                FixtureTask.at(10, 10, Affordance.STOW, 11, 10),
                FixtureTask.at(12, 10, Affordance.STOW, 13, 10));

        List<CrewRole> posted = new ArrayList<>();
        for (CrewRole role : CrewRole.values()) {
            if (Shift.basedAt(role, pocket, stores, new boolean[0])) posted.add(role);
        }

        assertEquals(List.of(CrewRole.STOREKEEPER), posted,
                "a room that publishes stowage stationed every trade that touches stores");
    }

    /**
     * A bay's stores and terminals are half the trades' second job and none of
     * their first, so the people who fetch and read there are not stationed
     * there.
     *
     * <p>Its parts run <em>is</em> a storekeeper's own trade, so the bay is
     * genuinely two postings. That is the distinction the rule draws: a room is
     * a station for the trades whose work it is, however many that turns out to
     * be, and never for the ones that merely come through.
     */
    @Test
    void aBayStationsTheTradesWhoseWorkItIsAndNobodyPassingThrough() {
        Site bay = new Site(2, RoomPurpose.VEHICLE_BAY, 20, 20);
        List<FixtureTask> bayWork = List.of(
                FixtureTask.servingBerth(20, 20, 0, 21, 20),
                FixtureTask.at(22, 20, Affordance.STOW, 23, 20),
                FixtureTask.at(24, 20, Affordance.READOUT, 25, 20));
        boolean[] occupied = { true };

        assertTrue(Shift.basedAt(CrewRole.MECH_TECH, bay, bayWork, occupied),
                "the bay is not the technician's station");
        assertTrue(Shift.basedAt(CrewRole.STOREKEEPER, bay, bayWork, occupied),
                "the bay's parts run is a storekeeper's own trade");
        assertFalse(Shift.basedAt(CrewRole.MEDIC, bay, bayWork, occupied),
                "fetching a part in the bay made it the sick berth's station");
        assertFalse(Shift.basedAt(CrewRole.ARMOURER, bay, bayWork, occupied),
                "fetching a part in the bay made it the armourer's station");
        assertFalse(Shift.basedAt(CrewRole.ENGINE_WATCH, bay, bayWork, occupied),
                "reading a bay terminal made it the engine watch's station");
    }

    /**
     * The exception the rule exists around: a passenger complement has no trade
     * aboard, so its berthing is the one thing that can be its billet.
     */
    @Test
    void marinesAreBilletedByTheirRacksBecauseTheyHaveNoTradeAboard() {
        Site barracks = new Site(3, RoomPurpose.BARRACKS, 5, 5);
        List<FixtureTask> racks = List.of(FixtureTask.at(5, 5, Affordance.REST, 6, 5));

        assertTrue(Shift.basedAt(CrewRole.MARINE, barracks, racks, new boolean[0]));
        assertFalse(Shift.basedAt(CrewRole.STOREKEEPER, barracks, racks, new boolean[0]),
                "a berthing became a workplace for whoever squares kit away in it");
    }

    /** Every working trade names one, or it has no room of its own to be posted to. */
    @Test
    void everyWorkingTradeHasATradeToBePostedBy() {
        for (CrewRole role : CrewRole.values()) {
            boolean works = false;
            for (Affordance job : role.onWatch()) works = works || job.duty();
            assertEquals(works, role.trade() != null,
                    role + " disagrees about whether it has work aboard");
        }
    }
}
