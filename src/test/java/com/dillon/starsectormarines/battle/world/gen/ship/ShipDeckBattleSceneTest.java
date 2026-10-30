package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.ambient.Shift;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.IdentityService;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a deck scene does with the berths and rooms a deck hands it, asked of a
 * hand-laid deck.
 *
 * <p>Nothing here generates a ship. The scene's own rules — which berth a parked
 * machine takes, how a machine faces, what a bay's work depends on, which berth
 * gets a boat — are properties of the scene given a deck, so a bare floor with
 * a few berths and rooms on it shows each of them. What a <em>generated</em>
 * ship does when crewed and run lives in {@code ManningEvidence}, behind
 * {@code crewEvidence}, because it costs minutes of wall clock.
 */
final class ShipDeckBattleSceneTest {

    private static final long SEED = 42L;
    private static final int WIDTH = 60;
    private static final int HEIGHT = 20;

    /** Where the three berths of {@link #threeBoatBerths()} stand. */
    private static final int[] BOAT_BERTH_CELLS = {5, 11, 17};

    /**
     * The bay stands the company's own machines, not a chosen number of them.
     *
     * <p>A home deck's vehicle bay is the Mech Lab, so what it holds is whatever
     * the campaign says the player owns. A new company owns no mech until the
     * player fabricates one, so every berth stays empty on purpose.
     */
    @Test
    void newCompanyBayDoesNotInventAStarterLance() {
        List<MechVariant> lance = new MechBay().activeSquad().mechs().stream()
                .map(CampaignMech::variant).toList();
        assertTrue(lance.isEmpty(), "a new company should found its machines from cargo");

        Deck deck = bay();
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck.map(), deck.graph(), SEED, null)) {
            assertTrue(!scene.gantries().isEmpty(), "the deck authored no berths");
            assertEquals(0, scene.occupyGantries(lance).length,
                    "the bay berthed a different number of machines than the company owns");
        }
    }

    /**
     * A screen names a room; the deck says where it is.
     *
     * <p>The berths it reports have to be the bay's own — a berth list gathered
     * by index would still look right on a deck with one bay and quietly frame
     * the wrong machines on a deck with two.
     */
    @Test
    void theDeckSaysWhereItsVehicleBayIs() {
        Deck deck = bay();
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck.map(), deck.graph(), SEED, null)) {
            DeckGraph.Compartment room = scene.room(RoomPurpose.VEHICLE_BAY);
            assertEquals(RoomPurpose.VEHICLE_BAY, room.purpose());

            List<Gantry> berths = scene.berthsIn(room);
            assertEquals(MACHINE_BERTHS_IN_BAY + 1, berths.size(), "four machine berths and the boat berth between them");
            for (Gantry berth : berths) {
                assertTrue(room.contains(berth.centerX, berth.centerY),
                        "a berth outside the bay was reported as one of its own");
            }
            assertEquals(MACHINE_BERTHS_IN_BAY + 2, scene.gantries().size(),
                    "the deck should also hold a berth that is not the bay's");
        }
    }

    /** Parked machines are centred and face cardinally along each berth's inboard axis. */
    @Test
    void berthedMachinesAreCentredAndFaceCardinallyIntoTheBay() {
        Deck deck = bay();
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck.map(), deck.graph(), SEED, null)) {
            List<Gantry> berths = machineBerths(scene.gantries());
            List<MechVariant> lance = new ArrayList<>();
            for (int index = 0; index < berths.size(); index++) lance.add(MechVariant.BULWARK);

            long[] machines = scene.occupyGantries(lance);
            assertEquals(berths.size(), machines.length, "not every machine berth was filled");

            EntityWorld entities = scene.simulation().getEntityWorld();
            BattleComponents components = scene.simulation().getBattleComponents();
            Set<Float> headings = new HashSet<>();
            for (int index = 0; index < machines.length; index++) {
                Gantry berth = berths.get(index);
                assertEquals(berth.worldCenterX(), scene.simulation().world().x(machines[index]),
                        0.001f, "the machine is not centred across its reserved footprint");
                assertEquals(berth.worldCenterY(), scene.simulation().world().y(machines[index]),
                        0.001f, "the machine is not centred down its reserved footprint");
                float expected = (berth.facing.degrees() % 360f + 360f) % 360f;
                float actual = entities.getFloat(machines[index],
                        components.MECH_LAYERED_ANIMATION,
                        BattleComponents.MECH_LAYERED_FACING_DEGREES);
                actual = (actual % 360f + 360f) % 360f;
                assertEquals(expected, actual, 0.01f,
                        "the machine in berth " + (index + 1)
                                + " is not facing down its authored inboard axis");
                assertEquals(0f, actual % 90f, 0.01f,
                        "the machine in berth " + (index + 1) + " is not cardinally aligned");
                headings.add(actual);
            }
            assertTrue(headings.size() > 1,
                    "every berth faced the same way, so the inboard test proves too little");
        }
    }

    @Test
    void liveGantriesAdoptCampaignWeaponsAndNewlyFabricatedChassis() {
        Deck deck = bay();
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck.map(), deck.graph(), SEED, null)) {
            long[] machines = scene.occupyGantries(List.of(MechVariant.BULWARK));
            scene.syncGantries(List.of(
                    new MechDeploymentSpec(MechVariant.BULWARK, MechRole.ARMORED_SUPPORT,
                            MissileReplenisherComponent.ACCELERATED_FEED,
                            MechWeaponComponent.PL_2_AP,
                            MechWeaponComponent.SRM_15,
                            MechWeaponComponent.LL_3),
                    MechDeploymentSpec.standard(MechVariant.HOUND)));

            for (MechMountSlot arm : MechVariant.BULWARK.armMounts()) {
                assertEquals(MechWeaponComponent.PL_2_AP,
                        scene.simulation().world().mechLoadout(machines[0]).mount(arm).component);
            }
            assertEquals(MechWeaponComponent.LL_3,
                    scene.simulation().world().mechLoadout(machines[0])
                            .mount(MechMountSlot.RIGHT_SHOULDER).component);
            assertEquals(2, scene.simulation().liveUnitCount());
        }
    }

    /**
     * What a technician does in the bay depends on what is parked in it.
     *
     * <p>The workshop fixtures are the room's and do not move, but servicing is
     * work on a <em>machine</em> — so it is not a job the bay has, it is a job
     * a berth has while something stands in it. An empty bay would otherwise put
     * technicians to work welding nothing, and a bay filling up would give them
     * no more to do than an empty one. The rest of the rotation is the room's
     * own, so a technician in an empty bay fetches parts and reads terminals.
     *
     * <p>Asked of the scene, whose part is to say which of the deck's berths a
     * parked machine occupies — the boat berth between the machine berths is
     * somebody else's, and a machine counted against it would publish servicing
     * on the wrong stand.
     */
    @Test
    void servicingWorkInTheBayScalesWithTheMachinesParkedInIt() {
        Deck deck = bay();

        Shift idle = bill(deck, 0);
        assertTrue(idle.jobs().containsAll(List.of(Affordance.STOW, Affordance.READOUT)),
                "an idle bay gave a technician no parts to run and nothing to read: " + idle.jobs());
        assertTrue(!idle.jobs().contains(Affordance.SERVICE),
                "an empty bay published servicing work");

        // Per berth rather than per machine: how many stands a berth has is the
        // bay fitting's business. What is asserted is that the number moves one
        // berth at a time.
        Shift one = bill(deck, 1);
        assertTrue(one.jobs().contains(Affordance.SERVICE),
                "a machine was parked and no technician's rotation included servicing it");
        assertEquals(STANDS_PER_BERTH, one.capacity());
        assertEquals(STANDS_PER_BERTH * MACHINE_BERTHS_IN_BAY, bill(deck, MACHINE_BERTHS_IN_BAY).capacity(),
                "a full bay does not offer every berth's servicing work");
    }

    /** The technician's watch bill for the bay with {@code parked} machines standing in it. */
    private static Shift bill(Deck deck, int parked) {
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck.map(), deck.graph(), SEED, null)) {
            scene.occupyGantries(Collections.nCopies(parked, MechVariant.BULWARK));
            return scene.watchBill(scene.room(RoomPurpose.VEHICLE_BAY), CrewRole.MECH_TECH);
        }
    }

    /**
     * The scene's clock only runs forwards, and running it twice to the same
     * instant changes nothing.
     *
     * <p>A host takes more than one render pass off a frame — a backdrop and an
     * actor pass have to agree about where everybody is standing — so advancing
     * has to be something a screen can ask for repeatedly.
     */
    @Test
    void aDeckClockIsMonotonicAndIdempotent() {
        Deck deck = bay();
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck.map(), deck.graph(), SEED, null)) {
            scene.occupyGantries(Collections.nCopies(2, MechVariant.BULWARK));
            long[] crew = scene.staff(scene.room(RoomPurpose.VEHICLE_BAY), CrewRole.MECH_TECH, 2);
            assertTrue(crew.length > 0, "nobody came aboard");

            scene.advanceTo(12f);
            float[] settledX = new float[crew.length];
            float[] settledY = new float[crew.length];
            for (int index = 0; index < crew.length; index++) {
                settledX[index] = scene.simulation().world().x(crew[index]);
                settledY[index] = scene.simulation().world().y(crew[index]);
            }

            scene.advanceTo(12f);
            scene.advanceTo(3f);
            assertEquals(12f, scene.simulatedSeconds(), 1e-4f,
                    "asking for an earlier time wound the deck back");
            for (int index = 0; index < crew.length; index++) {
                assertEquals(settledX[index], scene.simulation().world().x(crew[index]), 1e-4f,
                        "re-advancing to the same instant moved somebody");
                assertEquals(settledY[index], scene.simulation().world().y(crew[index]), 1e-4f,
                        "re-advancing to the same instant moved somebody");
            }
        }
    }

    /**
     * A ship's boat bays are a field: registered as an airfield, owned by the
     * ship, with a boat standing in every berth.
     */
    @Test
    void aShipsBoatBaysAreHerOwnFieldWithABoatInEveryBerth() {
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                threeBoatBerths(), null, SEED, null)) {
            AirfieldService bays = scene.simulation().getAirfieldService();
            assertEquals(3, bays.berths().size());
            assertEquals(Faction.MARINE, bays.owner(), "the ship's own bays are not hers");

            scene.advanceTo(1f);
            for (AirfieldService.Berth boat : bays.berths()) {
                assertTrue(boat.airframeId != 0L
                                && scene.simulation().world().isAlive(boat.airframeId),
                        "a bay was registered with nothing standing in it");
            }
        }
    }

    /**
     * A berth the company has no boat for stands empty.
     *
     * <p>The whole of what a lost boat does to the ship: the deck still has the
     * berth cut in it, and the bay simply does not put an aircraft out there.
     * Its own control, because "two berths" means nothing without knowing that
     * the same three berths register three when nothing is missing.
     */
    @Test
    void aBerthTheCompanyHasNoBoatForGetsNoBoat() {
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                threeBoatBerths(), null, SEED, null, new boolean[]{true, false, true})) {
            List<AirfieldService.Berth> berths =
                    scene.simulation().getAirfieldService().berths();
            assertEquals(2, berths.size(),
                    "a berth the company has no boat for was still given one");
            assertEquals(BOAT_BERTH_CELLS[0], berths.get(0).centerX);
            assertEquals(BOAT_BERTH_CELLS[2], berths.get(1).centerX,
                    "the wrong berth was left empty");
        }

        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                threeBoatBerths(), null, SEED, null)) {
            assertEquals(3, scene.simulation().getAirfieldService().berths().size(),
                    "a hull with every berth held did not stand three boats");
        }
    }

    /**
     * The marines aboard are the ones on the roster, and nobody else is.
     *
     * <p>The failure this guards is quiet and would look right: crewing the ship
     * posts a watch wherever a role has jobs, so a company mustered into a
     * bunkroom with more racks than names would be topped up by the general
     * crewing pass with marines who are on no muster roll. The player would read
     * a roster of nine names and walk into a ship carrying a hundred and sixty.
     */
    @Test
    void theMarinesAboardAreTheOnesOnTheRoster() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        List<MarineSoldier> company = roster.soldiers();
        int racks = company.size() + 4;

        NavigationGrid grid = openFloor(30, 14);
        List<FixtureTask> tasks = new ArrayList<>();
        for (int rack = 0; rack < racks; rack++) {
            tasks.add(FixtureTask.at(2 + rack % 12, 2 + 2 * (rack / 12), Affordance.REST,
                    2 + rack % 12, 1 + 2 * (rack / 12)));
        }
        DeckGraph.Compartment berthing = compartment(1, 1, 1, 28, 12, RoomPurpose.BARRACKS);
        MapResult map = new MapResult(grid, new CellTopology(30, 14), 1, 1, 28, 12,
                Collections.emptyList(), Collections.emptyList(),
                new TacticalMap(Collections.emptyList()), Buildings.EMPTY,
                Collections.emptyList(), RoadGraph.EMPTY, Collections.emptyList(),
                Collections.emptyList(), null, Collections.emptyList(), tasks);

        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                map, new DeckGraph(List.of(berthing), List.of()), SEED, null)) {
            long[] billeted = scene.muster(berthing, company);
            int mustered = 0;
            for (long actor : billeted) if (actor != 0L) mustered++;
            assertEquals(company.size(), mustered, "the company was not all billeted");

            scene.manDeck();

            IdentityService identity = scene.simulation().identity();
            Set<Long> onRoll = new HashSet<>();
            for (long actor : billeted) onRoll.add(actor);
            for (int index = 0; index < scene.simulation().getRoster().liveCount(); index++) {
                long actor = scene.simulation().getRoster().get(index);
                if (identity.type(actor) == CrewRole.MARINE.unit()) {
                    assertTrue(onRoll.contains(actor),
                            "crewing the ship added a marine who is on no muster roll");
                }
            }
        }
    }

    // ----- hand-laid decks -----

    private record Deck(MapResult map, DeckGraph graph) { }

    private static final int MACHINE_BERTHS_IN_BAY = 4;
    private static final int STANDS_PER_BERTH = 2;

    /**
     * A bare floor with a vehicle bay and a second room on it.
     *
     * <p>The bay holds four machine berths, one for each facing, with a boat
     * berth set between them; the second room holds a machine berth of its own
     * that the bay must not claim. Each machine berth has two servicing stands
     * and the bay a stow point and a terminal.
     */
    private static Deck bay() {
        NavigationGrid grid = openFloor(WIDTH, HEIGHT);
        List<Gantry> berths = new ArrayList<>();
        berths.add(Gantry.covering(3, 3, 3, 6, Gantry.Facing.NORTH, Gantry.Holds.MACHINE));
        berths.add(Gantry.covering(9, 3, 3, 6, Gantry.Facing.SOUTH, Gantry.Holds.MACHINE));
        berths.add(Gantry.covering(15, 12, 3, 3, Gantry.Facing.NORTH, Gantry.Holds.BOAT));
        berths.add(Gantry.covering(18, 3, 6, 3, Gantry.Facing.EAST, Gantry.Holds.MACHINE));
        berths.add(Gantry.covering(27, 3, 6, 3, Gantry.Facing.WEST, Gantry.Holds.MACHINE));
        berths.add(Gantry.covering(42, 3, 3, 6, Gantry.Facing.NORTH, Gantry.Holds.MACHINE));

        List<FixtureTask> tasks = new ArrayList<>();
        for (int berth : new int[]{0, 1, 3, 4}) {
            Gantry g = berths.get(berth);
            for (int stand = 0; stand < STANDS_PER_BERTH; stand++) {
                tasks.add(FixtureTask.servingBerth(g.left() + stand, g.top() + 1, berth,
                        g.centerX, g.centerY));
            }
        }
        tasks.add(FixtureTask.at(20, 14, Affordance.STOW, 20, 15));
        tasks.add(FixtureTask.at(24, 14, Affordance.READOUT, 24, 15));

        DeckGraph.Compartment bay = compartment(1, 1, 1, 36, 18, RoomPurpose.VEHICLE_BAY);
        DeckGraph.Compartment other = compartment(2, 38, 1, 20, 18, RoomPurpose.HANGAR);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) topology.setGroundKind(x, y, GroundKind.INDOOR);
        }
        MapResult map = new MapResult(grid, topology, 1, 1, WIDTH - 2, HEIGHT - 2,
                Collections.emptyList(), Collections.emptyList(),
                new TacticalMap(Collections.emptyList()), Buildings.EMPTY,
                Collections.emptyList(), RoadGraph.EMPTY, Collections.emptyList(),
                Collections.emptyList(), null, berths, tasks);
        return new Deck(map, new DeckGraph(List.of(bay, other), List.of()));
    }

    private static DeckGraph.Compartment compartment(int id, int x, int y, int width, int depth,
                                                     RoomPurpose purpose) {
        return new DeckGraph.Compartment(id, RoomShape.rectangle(width, depth), x, y,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS, purpose, List.of());
    }

    /** A bare deck with three boat berths on it and nothing else. */
    private static MapResult threeBoatBerths() {
        NavigationGrid grid = openFloor(24, 12);
        List<Gantry> berths = new ArrayList<>();
        for (int cell : BOAT_BERTH_CELLS) {
            berths.add(new Gantry(cell, 6, 1, 1, Gantry.Facing.NORTH, Gantry.Holds.BOAT));
        }
        return new MapResult(grid, new CellTopology(24, 12), 1, 1, 22, 10,
                Collections.emptyList(), Collections.emptyList(),
                new TacticalMap(Collections.emptyList()), Buildings.EMPTY,
                Collections.emptyList(), RoadGraph.EMPTY, Collections.emptyList(),
                Collections.emptyList(), null, berths, Collections.emptyList());
    }

    private static NavigationGrid openFloor(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    /** Only the berths a lance can be stood in. */
    private static List<Gantry> machineBerths(List<Gantry> berths) {
        return berths.stream().filter(berth -> berth.holds == Gantry.Holds.MACHINE).toList();
    }

}
