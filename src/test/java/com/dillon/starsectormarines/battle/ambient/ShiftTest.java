package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
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
 * A shift has to come from what a map affords rather than from a list of
 * coordinates somebody typed, and it has to be somebody's own work rather than
 * whatever happened to be nearest.
 *
 * <p>Exercised against generated ship compartments because that is the map
 * family that publishes work today. Nothing under test knows it is on a ship:
 * a {@link JobSite} is an id, a purpose and an extent, and a building interior
 * on a surface map answers all three.
 */
class ShiftTest {

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
        AmbientTaskRoute shift = Shift.of(CrewRole.MECH_TECH, List.of(bay.compartment()),
                        bay.map().fixtureTasks, allBerthed(bay.map()), AmbientThreatPolicy.ANY_COMBATANT)
                        .member(0);
        assertNotNull(shift, "a furnished bay offered a technician no work at all");

        // A loop that came back to the same group twice would be a stop that
        // resolves to a claim the walker is already holding: they arrive where
        // they stand and do the same job again. One bay is one site, so even the
        // defect list - which is a circuit and does call at several places -
        // contributes exactly one group here.
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
        assertTrue(groups.contains(JobBoard.group(bay.compartment().id(),
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
        AmbientTaskRoute marine = Shift.of(CrewRole.MARINE, List.of(bay.compartment()),
                        bay.map().fixtureTasks, allBerthed(bay.map()), AmbientThreatPolicy.ANY_COMBATANT)
                        .member(0);
        assertNull(marine, "a marine was given a shift in the mech bay");

        AmbientTaskRoute machinist = Shift.of(CrewRole.MACHINIST, List.of(bay.compartment()),
                        bay.map().fixtureTasks, allBerthed(bay.map()), AmbientThreatPolicy.ANY_COMBATANT)
                        .member(0);
        assertNotNull(machinist, "the shop offered a machinist nothing");
        // Repair joined the list when the bays started publishing a defect
        // backlog: making and mending parts is the same trade, and the machinist
        // is who a bay's snag list belongs to.
        for (AmbientTaskRoute.Stop stop : machinist.stops()) {
            assertTrue(stop.pointGroup().endsWith("fabricate")
                            || stop.pointGroup().endsWith("stow")
                            || stop.pointGroup().endsWith("repair"),
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

        List<FixtureTask> live = JobBoard.live(bay.map().fixtureTasks,
                bay.compartment(), empty);
        for (FixtureTask task : live) {
            assertTrue(task.berth() == FixtureTask.NO_BERTH,
                    "an empty berth still published its servicing job");
        }

        AmbientTaskRoute shift = Shift.of(CrewRole.MECH_TECH, List.of(bay.compartment()),
                        bay.map().fixtureTasks, empty, AmbientThreatPolicy.ANY_COMBATANT)
                        .member(0);
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
        Shift watchBill = Shift.of(CrewRole.MECH_TECH, List.of(bay.compartment()),
                bay.map().fixtureTasks, berthed, AmbientThreatPolicy.ANY_COMBATANT);
        int capacity = watchBill.capacity();
        assertTrue(capacity > 1, "the bay could keep only " + capacity + " technicians busy");

        Set<String> openings = new HashSet<>();
        Set<Float> phases = new HashSet<>();
        List<AmbientTaskRoute> watch = new ArrayList<>();
        for (int index = 0; index < capacity; index++) {
            AmbientTaskRoute shift = watchBill.member(index);
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
        List<Affordance> offered = JobBoard.publish(service, bay.map().fixtureTasks,
                bay.compartment(), berthed);
        assertTrue(offered.contains(Affordance.SERVICE), "no servicing was published");
        assertTrue(service.registeredCount() > 0, "nothing was published at all");

        String group = JobBoard.group(bay.compartment().id(), Affordance.SERVICE);
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
        AmbientTaskRoute shift = Shift.of(CrewRole.MARINE, List.of(barracks.compartment()),
                        barracks.map().fixtureTasks, allBerthed(barracks.map()), AmbientThreatPolicy.HOSTILE_COMBATANT)
                        .member(0);
        assertNotNull(shift, "a furnished barracks gave a marine nothing to do");

        Set<String> groups = new HashSet<>();
        for (AmbientTaskRoute.Stop stop : shift.stops()) {
            assertNotNull(stop.pointGroup(),
                    "a generated stop must claim a group, not sit on a coordinate");
            assertTrue(groups.add(stop.pointGroup()),
                    "the shift comes back round to " + stop.pointGroup() + " twice in one loop");
        }
        assertTrue(groups.contains(JobBoard.group(barracks.compartment().id(),
                        Affordance.REST)),
                "a berth the marine never sleeps in");
        assertTrue(groups.contains(JobBoard.group(barracks.compartment().id(),
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
        assertNull(Shift.of(CrewRole.MECH_TECH, List.of(barracks.compartment()),
                        barracks.map().fixtureTasks, allBerthed(barracks.map()), AmbientThreatPolicy.HOSTILE_COMBATANT)
                        .member(0),
                "a technician was given a shift in the marines' berthing");
        assertEquals(0, Shift.of(CrewRole.MECH_TECH, List.of(barracks.compartment()),
                        barracks.map().fixtureTasks, allBerthed(barracks.map()), AmbientThreatPolicy.HOSTILE_COMBATANT)
                        .capacity(),
                "the marines' berthing counted itself as somewhere a technician works");

        Bay quarters = generateRoom(1L, RoomPurpose.CREW_QUARTERS);
        assertNull(Shift.of(CrewRole.MARINE, List.of(quarters.compartment()),
                        quarters.map().fixtureTasks, allBerthed(quarters.map()), AmbientThreatPolicy.HOSTILE_COMBATANT)
                        .member(0),
                "a marine turned in in the ship's own berthing");
        assertTrue(Shift.of(CrewRole.MECH_TECH, List.of(quarters.compartment()),
                        quarters.map().fixtureTasks, allBerthed(quarters.map()), AmbientThreatPolicy.HOSTILE_COMBATANT)
                        .capacity() > 0,
                "the ship's own hands have nowhere of their own to sleep");
    }

    /**
     * A marine's four jobs are one rotation, not four postings.
     *
     * <p>They sleep and stow their kit in their own berthing, eat in the mess,
     * and shoot on the range — three compartments. A shift confined to one of
     * them produced a marine who never ate, or one who stood on a firing line
     * all watch, and no amount of further fitting was going to fix that: the
     * rooms were right and the shift was the wrong shape.
     */
    @Test
    void aMarinesShiftReachesTheMessAndTheRange() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(
                DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                        10, 250, 50, 0.28f),
                42L, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), 42L, null)) {
            DeckGraph.Compartment barracks = scene.room(RoomPurpose.BARRACKS);
            assertNotNull(barracks, "the deck generated no barracks");
            Shift bill = scene.watchBill(barracks, CrewRole.MARINE);

            assertTrue(bill.jobs().containsAll(List.of(Affordance.REST, Affordance.STOW,
                            Affordance.MESS, Affordance.PRACTICE)),
                    "a marine's rotation came out as " + bill.jobs());
            assertTrue(bill.spansSites(), "every one of a marine's jobs was in one room");

            AmbientTaskRoute route = bill.member(0);
            assertNotNull(route, "a furnished barracks gave a marine no shift");
            Set<String> sites = new HashSet<>();
            for (AmbientTaskRoute.Stop stop : route.stops()) {
                sites.add(stop.pointGroup().substring(0, stop.pointGroup().indexOf(':')));
            }
            assertTrue(sites.size() > 1,
                    "the shift claims in one site only: " + sites);
        }
    }

    /**
     * A shift is posted where it is posted, not where its first job happens to
     * be. A marine's job list begins with the mess, and a shift that took its
     * identity from that would be a barracks watch named after a room three
     * compartments away.
     */
    @Test
    void aShiftIsNamedForWhereItIsPosted() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(
                DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                        10, 250, 50, 0.28f),
                42L, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), 42L, null)) {
            DeckGraph.Compartment barracks = scene.room(RoomPurpose.BARRACKS);
            Shift bill = scene.watchBill(barracks, CrewRole.MARINE);
            assertEquals(barracks.id(), bill.base().id(),
                    "the barracks watch is based somewhere else");
            assertTrue(bill.member(0).id().contains("-" + barracks.id() + "-"),
                    "the route is named " + bill.member(0).id()
                            + ", which is not the room it is posted to");
        }
    }

    /**
     * A job shared with the rest of the ship does not cap a room's posting.
     *
     * <p>Within a room, scarcity is real and bounds the watch. Across rooms it
     * is not: the ship has two firing lanes and twenty-seven barracks, so
     * counting practice against a berthing posting would hold every one of them
     * to two marines and then send all fifty-four at the same two lanes.
     */
    @Test
    void aSharedJobDoesNotCapARoomsPosting() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(
                DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                        10, 250, 50, 0.28f),
                42L, null);
        DeckGraph graph = generator.getLastDeckGraph();
        DeckGraph.Compartment barracks = null;
        DeckGraph.Compartment range = null;
        for (DeckGraph.Compartment room : graph.compartments()) {
            if (barracks == null && room.purpose() == RoomPurpose.BARRACKS) barracks = room;
            if (range == null && room.purpose() == RoomPurpose.FIRING_RANGE) range = room;
        }
        assertNotNull(barracks, "no barracks");
        assertNotNull(range, "no firing range");

        boolean[] berthed = new boolean[deck.gantries.size()];
        int alone = Shift.of(CrewRole.MARINE, List.of(barracks), deck.fixtureTasks,
                berthed, AmbientThreatPolicy.HOSTILE_COMBATANT).capacity();
        Shift reaching = Shift.of(CrewRole.MARINE, List.of(barracks, range),
                deck.fixtureTasks, berthed, AmbientThreatPolicy.HOSTILE_COMBATANT);
        assertTrue(reaching.jobs().contains(Affordance.PRACTICE),
                "the range contributed no practice, so nothing is being tested");
        assertEquals(alone, reaching.capacity(),
                "reaching the ship's firing range changed how many marines a berth holds");
    }

}
