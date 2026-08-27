package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSizing;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A generated bay has to be somewhere a technician has business, and the shift
 * they walk has to come from what the room affords rather than from a list of
 * coordinates somebody typed.
 */
class CompartmentCrewTest {

    private record Bay(MapResult map, DeckGraph.Compartment compartment) { }

    private static Bay generateBay(long seed) {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult map = generator.generateDeck(
                DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                        10, 250, 50, 0.28f),
                seed, null);
        DeckGraph.Compartment bay = generator.getLastDeckGraph().compartments().stream()
                .filter(c -> c.purpose() == RoomPurpose.VEHICLE_BAY)
                .findFirst().orElseThrow();
        return new Bay(map, bay);
    }

    private static boolean[] allBerthed(MapResult map) {
        boolean[] occupied = new boolean[map.gantries.size()];
        Arrays.fill(occupied, true);
        return occupied;
    }

    @Test
    void aTechnicianCyclesTheJobsTheBayAffords() {
        Bay bay = generateBay(1L);
        AmbientTaskRoute shift = CompartmentCrew.shift(CrewRole.MECH_TECH, bay.compartment(),
                bay.map().fixtureTasks, allBerthed(bay.map()), 0,
                AmbientThreatPolicy.ANY_COMBATANT);
        assertNotNull(shift, "a furnished bay offered a technician no work at all");

        Set<String> groups = new HashSet<>();
        for (AmbientTaskRoute.Stop stop : shift.stops()) {
            assertNotNull(stop.pointGroup(),
                    "a generated stop must claim a group, not sit on a coordinate");
            assertTrue(groups.add(stop.pointGroup()),
                    "the shift comes back round to " + stop.pointGroup() + " twice in one loop");
        }
        assertTrue(shift.stops().size() > 1,
                "a single stop is a post, not a shift: nothing cycles");

        // Welding on a berthed machine is the work; the rest is what the work needs.
        assertTrue(groups.contains(CompartmentCrew.group(bay.compartment().id(),
                        Affordance.SERVICE)),
                "the technician never goes near the machines");
    }

    /**
     * The distinction the role model exists to make: a room affording something
     * is not the same fact as somebody's job being there.
     */
    @Test
    void aRoleTakesOnlyItsOwnJobs() {
        Bay bay = generateBay(1L);
        AmbientTaskRoute marine = CompartmentCrew.shift(CrewRole.MARINE, bay.compartment(),
                bay.map().fixtureTasks, allBerthed(bay.map()), 0,
                AmbientThreatPolicy.ANY_COMBATANT);
        assertNull(marine, "a marine was given a shift in the mech bay");

        AmbientTaskRoute machinist = CompartmentCrew.shift(CrewRole.MACHINIST, bay.compartment(),
                bay.map().fixtureTasks, allBerthed(bay.map()), 0,
                AmbientThreatPolicy.ANY_COMBATANT);
        assertNotNull(machinist, "the shop offered a machinist nothing");
        for (AmbientTaskRoute.Stop stop : machinist.stops()) {
            assertTrue(stop.pointGroup().endsWith("fabricate")
                            || stop.pointGroup().endsWith("stow"),
                    "a machinist was sent to " + stop.pointGroup());
        }
    }

    /**
     * Servicing exists only while something is parked. An empty bay is somewhere
     * to walk through, and a technician in one has parts to fetch and terminals
     * to read but nothing to weld.
     */
    @Test
    void anEmptyBayOffersNoServicingButStillOffersWork() {
        Bay bay = generateBay(1L);
        boolean[] empty = new boolean[bay.map().gantries.size()];

        List<FixtureTask> live = CompartmentCrew.live(bay.map().fixtureTasks,
                bay.compartment(), empty);
        for (FixtureTask task : live) {
            assertTrue(task.berth() == FixtureTask.NO_BERTH,
                    "an empty berth still published its servicing job");
        }

        AmbientTaskRoute shift = CompartmentCrew.shift(CrewRole.MECH_TECH, bay.compartment(),
                bay.map().fixtureTasks, empty, 0, AmbientThreatPolicy.ANY_COMBATANT);
        assertNotNull(shift, "an empty bay left its technician with nothing to do at all");
        for (AmbientTaskRoute.Stop stop : shift.stops()) {
            assertTrue(!stop.pointGroup().endsWith("service"),
                    "a technician was sent to weld on an empty berth");
        }
    }

    /** Every member of a watch starts somewhere different and on a different phase. */
    @Test
    void aWatchSpreadsAcrossTheRoom() {
        Bay bay = generateBay(1L);
        boolean[] berthed = allBerthed(bay.map());
        int capacity = CompartmentCrew.capacity(CrewRole.MECH_TECH, bay.compartment(),
                bay.map().fixtureTasks, berthed);
        assertTrue(capacity > 1, "the bay could keep only " + capacity + " technicians busy");

        Set<String> openings = new HashSet<>();
        Set<Float> phases = new HashSet<>();
        List<AmbientTaskRoute> watch = new ArrayList<>();
        for (int index = 0; index < capacity; index++) {
            AmbientTaskRoute shift = CompartmentCrew.shift(CrewRole.MECH_TECH,
                    bay.compartment(), bay.map().fixtureTasks, berthed, index,
                    AmbientThreatPolicy.ANY_COMBATANT);
            assertNotNull(shift, "member " + index + " of the watch got no shift");
            watch.add(shift);
            openings.add(shift.stops().get(0).pointGroup());
            phases.add(shift.phaseOffsetSeconds());
        }
        assertEquals(capacity, phases.size(), "two technicians are on the same phase");
        assertTrue(openings.size() > 1,
                "the whole watch starts at the same station, which is a queue");

        Set<String> ids = new HashSet<>();
        for (AmbientTaskRoute shift : watch) {
            assertTrue(ids.add(shift.id()), "two shifts share the id " + shift.id());
        }
    }

    /**
     * The claim service is what makes the count honest, so the published points
     * have to be real, walkable, and exclusive.
     */
    @Test
    void publishedJobsAreClaimableAndExclusive() {
        Bay bay = generateBay(1L);
        boolean[] berthed = allBerthed(bay.map());
        TaskPointService service = new TaskPointService(bay.map().grid);
        List<Affordance> offered = CompartmentCrew.publish(service, bay.map().fixtureTasks,
                bay.compartment(), berthed);
        assertTrue(offered.contains(Affordance.SERVICE), "no servicing was published");
        assertTrue(service.registeredCount() > 0, "nothing was published at all");

        String group = CompartmentCrew.group(bay.compartment().id(), Affordance.SERVICE);
        TaskPoint first = service.claimNearest(1L, group, 0f, 0f);
        TaskPoint second = service.claimNearest(2L, group, 0f, 0f);
        assertNotNull(first, "the first technician could not claim anywhere to weld");
        assertNotNull(second, "the second technician could not claim anywhere to weld");
        assertTrue(!first.id().equals(second.id()),
                "two technicians claimed the same place to stand");
    }

    /** The compartment of a given purpose on a generated deck, for the berthing cases. */
    private static Bay generateRoom(long seed, RoomPurpose purpose) {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult map = generator.generateDeck(
                DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                        10, 250, 50, 0.28f),
                seed, null);
        DeckGraph.Compartment room = generator.getLastDeckGraph().compartments().stream()
                .filter(c -> c.purpose() == purpose)
                .findFirst().orElseThrow();
        return new Bay(map, room);
    }

    /**
     * A marine off watch, in their own berthing: turn in, and square their kit
     * away. Both stops come from fixtures the fill placed, not from a waypoint
     * anybody typed.
     */
    @Test
    void aMarineRestsAndSquaresKitAwayInTheirOwnBerthing() {
        Bay barracks = generateRoom(1L, RoomPurpose.BARRACKS);
        AmbientTaskRoute shift = CompartmentCrew.shift(CrewRole.MARINE, barracks.compartment(),
                barracks.map().fixtureTasks, allBerthed(barracks.map()), 0,
                AmbientThreatPolicy.HOSTILE_COMBATANT);
        assertNotNull(shift, "a furnished barracks gave a marine nothing to do");

        Set<String> groups = new HashSet<>();
        for (AmbientTaskRoute.Stop stop : shift.stops()) {
            assertNotNull(stop.pointGroup(),
                    "a generated stop must claim a group, not sit on a coordinate");
            assertTrue(groups.add(stop.pointGroup()),
                    "the shift comes back round to " + stop.pointGroup() + " twice in one loop");
        }
        assertTrue(groups.contains(CompartmentCrew.group(barracks.compartment().id(),
                        Affordance.REST)),
                "a berth the marine never sleeps in");
        assertTrue(groups.contains(CompartmentCrew.group(barracks.compartment().id(),
                        Affordance.STOW)),
                "nowhere for the marine to keep their kit");
        assertTrue(shift.stops().size() > 1,
                "a single stop is a post, not a shift: nothing cycles");
    }

    /**
     * Berthing belongs to whoever sleeps in it, and to nobody else.
     *
     * <p>Both halves matter. A technician has no job in the marines' barracks
     * even though it stows things, because stowage there is somebody's own
     * locker rather than the parts run; and a marine has no job in the ratings'
     * quarters at all. Without the second half a ship carrying two populations
     * berths them in one another's compartments.
     */
    @Test
    void berthingBelongsToWhoeverSleepsInIt() {
        Bay barracks = generateRoom(1L, RoomPurpose.BARRACKS);
        assertNull(CompartmentCrew.shift(CrewRole.MECH_TECH, barracks.compartment(),
                        barracks.map().fixtureTasks, allBerthed(barracks.map()), 0,
                        AmbientThreatPolicy.HOSTILE_COMBATANT),
                "a technician was given a shift in the marines' berthing");
        assertEquals(0, CompartmentCrew.capacity(CrewRole.MECH_TECH, barracks.compartment(),
                        barracks.map().fixtureTasks, allBerthed(barracks.map())),
                "the marines' berthing counted itself as somewhere a technician works");

        Bay quarters = generateRoom(1L, RoomPurpose.CREW_QUARTERS);
        assertNull(CompartmentCrew.shift(CrewRole.MARINE, quarters.compartment(),
                        quarters.map().fixtureTasks, allBerthed(quarters.map()), 0,
                        AmbientThreatPolicy.HOSTILE_COMBATANT),
                "a marine turned in in the ship's own berthing");
        assertTrue(CompartmentCrew.capacity(CrewRole.MECH_TECH, quarters.compartment(),
                        quarters.map().fixtureTasks, allBerthed(quarters.map())) > 0,
                "the ship's own hands have nowhere of their own to sleep");
    }
}
