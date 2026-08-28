package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated deck draws through the game's own renderer, and its berths hold
 * the company's own machines.
 *
 * <p>Guards the seams rather than the picture. A world layer may emit a command
 * that only a live GL context can replay — the decal pass does — and asking for
 * such a layer fails loudly at draw time. That is the right behaviour, but until
 * this test existed the only thing that exercised it was running the snapshot
 * task by hand, so the failure surfaced as a broken evidence run instead of a
 * broken build.
 */
final class ShipDeckBattleSceneTest {

    private static final long SEED = 42L;
    private static final int CELL_PX = 16;
    /** Long and shallow, the way a hull that carries people and machines is. */
    private static final float TRANSPORT_ASPECT = 0.28f;

    /**
     * Draws the deck to an image, which is what the fill assertions below read.
     *
     * <p>Here because these tests render, not to install anything: the catalogs
     * a deck scene needs are installed for every test by
     * {@code TileRegistryTestInstaller}, and standing this up for them would
     * load the whole battle sprite pack to get at two JSON files. Shared so
     * neither test depends on the other having run first.
     */
    private static HeadlessUiRenderer drain;

    @BeforeAll
    static void openDrain() {
        Path modRoot = Paths.get("mod").toAbsolutePath().normalize();
        drain = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(modRoot), modRoot);
    }

    @Test
    void deckDrawsThroughTheBattleRendererWithoutALiveContext() {
        MapResult deck = deck();

        BufferedImage image;
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck, SEED)) {
            int across = 24;
            int down = 16;
            // Framed on the middle of the deck, where the spine and the
            // compartments hung off it are. A window near a corner is outside
            // the hull, and asking whether the world drew there answers whether
            // something paints the empty space around a ship rather than
            // whether the ship drew.
            image = drain.renderHostPass(
                    scene.pass(ShipDeckBattleScene.DeckView.over(
                            (deck.grid.getWidth() - across) / 2,
                            (deck.grid.getHeight() - down) / 2,
                            across, down, CELL_PX)),
                    across * CELL_PX, down * CELL_PX);
        }

        assertNotNull(image, "the deck view produced no image");
        assertTrue(painted(image) > 0.5f,
                "the deck view came out mostly blank, so the world layers drew nothing: "
                        + painted(image));
    }

    /**
     * The bay stands the company's own machines, not a chosen number of them.
     *
     * <p>A home deck's vehicle bay is the Mech Lab, so what it holds is whatever
     * the campaign says the player owns. A new company owns one mech, and the
     * berths past it stay empty on purpose — filling them to make the room look
     * busy would be showing the player equipment they do not have.
     */
    @Test
    void berthsHoldTheCompanysOwnLance() {
        CampaignMechSquad squad = new MechBay().activeSquad();
        List<MechVariant> lance = squad.mechs().stream().map(CampaignMech::variant).toList();
        assertTrue(!lance.isEmpty(), "a new company should start with at least one machine");

        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck(), SEED)) {
            assertTrue(!scene.gantries().isEmpty(), "the deck authored no berths");
            assertEquals(Math.min(lance.size(), scene.gantries().size()),
                    scene.occupyGantries(lance).length,
                    "the bay berthed a different number of machines than the company owns");
        }
    }

    /**
     * A staffed bay is inhabited: technicians take jobs the room affords and
     * come round to a different one, without a single authored waypoint.
     *
     * <p>Advancing the clock is the whole test. A route that never moves anybody
     * off its first stop is indistinguishable from a static pose at the moment
     * of spawn, and that is precisely the failure a generated room is prone to —
     * so this watches a technician's claim change rather than checking that one
     * was handed out.
     */
    @Test
    void staffedTechniciansCycleTheBaysJobs() {
        CampaignMechSquad squad = new MechBay().activeSquad();
        List<MechVariant> lance = squad.mechs().stream().map(CampaignMech::variant).toList();

        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), SEED, null)) {
            scene.occupyGantries(lance);
            DeckGraph.Compartment bay = scene.room(RoomPurpose.VEHICLE_BAY);
            long[] hands = scene.staff(bay, CrewRole.MECH_TECH, 3);
            assertTrue(hands.length > 0, "the bay took on nobody at all");

            TaskPointService points = scene.simulation().taskPoints();
            Set<String> visitedByFirst = new HashSet<>();
            Set<Long> everWorking = new HashSet<>();
            for (int step = 0; step < 600; step++) {
                scene.simulation().ambientTasks().advance(0.1f);

                // Nobody may be standing where somebody else is standing, at any
                // instant. Claims are exclusive or the capacity is a fiction.
                Set<String> heldNow = new HashSet<>();
                for (long hand : hands) {
                    TaskPoint claim = points.claimedPoint(hand);
                    if (claim == null) continue;
                    everWorking.add(hand);
                    assertTrue(heldNow.add(claim.id()),
                            "two technicians claimed " + claim.id() + " at once");
                }
                TaskPoint first = points.claimedPoint(hands[0]);
                if (first != null) visitedByFirst.add(first.id());
            }
            assertEquals(hands.length, everWorking.size(),
                    "a technician was taken on and never given anything to do");
            assertTrue(visitedByFirst.size() > 1,
                    "a technician never left the station they started at, so nothing cycles");
        }
    }

    /**
     * A screen names a room; the deck says where it is.
     *
     * <p>This is what lets the Mech Lab be a view of the ship rather than a
     * second garage kept in step with it by hand. The berths it reports have to
     * be the bay's own — a berth list gathered by index would still look right
     * on a deck with one bay and quietly frame the wrong machines on a deck
     * with two.
     */
    @Test
    void theDeckSaysWhereItsVehicleBayIs() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), SEED, null)) {
            DeckGraph.Compartment bay = scene.room(RoomPurpose.VEHICLE_BAY);
            assertEquals(RoomPurpose.VEHICLE_BAY, bay.purpose());

            List<Gantry> berths = scene.berthsIn(bay);
            assertTrue(!berths.isEmpty(), "the vehicle bay reported no berths");
            for (Gantry berth : berths) {
                assertTrue(bay.contains(berth.centerX, berth.centerY),
                        "a berth outside the bay was reported as one of its own");
            }
        }
    }

    /**
     * A berthed machine faces the way its berth says it leaves.
     *
     * <p>Every berth is filled here rather than only the ones the company owns,
     * because the berths on one rank of a bay face out one way and those on the
     * other rank face out the other. Checking the first berth alone would pass
     * on a machine that had simply kept the heading every unit spawns with.
     */
    @Test
    void berthedMachinesFaceTheWayOut() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), SEED, null)) {
            List<Gantry> berths = scene.gantries();
            List<MechVariant> lance = new ArrayList<>();
            for (int index = 0; index < berths.size(); index++) lance.add(MechVariant.BULWARK);

            long[] machines = scene.occupyGantries(lance);
            assertEquals(berths.size(), machines.length, "not every berth was filled");

            EntityWorld world = scene.simulation().getEntityWorld();
            BattleComponents components = scene.simulation().getBattleComponents();
            Set<Float> headings = new HashSet<>();
            for (int index = 0; index < machines.length; index++) {
                float expected = berths.get(index).facing.degrees();
                float actual = world.getFloat(machines[index],
                        components.MECH_LAYERED_ANIMATION,
                        BattleComponents.MECH_LAYERED_FACING_DEGREES);
                assertEquals(expected, actual, 0.01f,
                        "the machine in berth " + (index + 1) + " is not facing its way out");
                headings.add(actual);
            }
            assertTrue(headings.size() > 1,
                    "every berth faced the same way, so this proves nothing about facing");
        }
    }

    /**
     * What a technician does in the bay depends on what is parked in it.
     *
     * <p>The workshop fixtures are the room's and do not move, but servicing is
     * work on a <em>machine</em> — so it is not a job the bay has, it is a job
     * a berth has while something stands in it. That is the whole reason a
     * berth's job is published as live rather than authored: an empty bay would
     * otherwise put technicians to work welding nothing, and a bay filling up
     * would give them no more to do than an empty one.
     *
     * <p>The rest of the rotation is the room's own and stays whatever the fill
     * laid down, so a technician in an empty bay fetches parts and reads
     * terminals, which is in fact what a technician in an empty bay does.
     */
    @Test
    void servicingWorkInTheBayScalesWithTheMachinesParkedInIt() {
        MechVariant variant = new MechBay().activeSquad().mechs().get(0).variant();
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        DeckGraph graph = generator.getLastDeckGraph();
        int berths = deck.gantries.size();
        assertTrue(berths > 1, "the bay authored " + berths + " berths, so nothing scales");

        List<Affordance> idle = bayRotation(deck, graph, List.of());
        assertTrue(idle.containsAll(List.of(Affordance.STOW, Affordance.READOUT)),
                "an idle bay gave a technician no parts to run and nothing to read: " + idle);
        assertTrue(!idle.contains(Affordance.SERVICE),
                "an empty bay put a technician to work servicing a machine that is not there");

        assertEquals(List.of(), servicing(deck, graph, List.of()),
                "an empty bay published servicing work");
        // Per berth rather than per machine: the fitting stands a technician on
        // more than one side of a parked machine, and how many is the bay
        // fitting's business. What is asserted here is that the number moves one
        // berth at a time.
        int perBerth = servicing(deck, graph, List.of(variant)).size();
        assertTrue(perBerth > 0, "a machine was parked and nobody could get at it");
        assertEquals(berths * perBerth,
                servicing(deck, graph, Collections.nCopies(berths, variant)).size(),
                "a full bay does not offer every berth's servicing work");
        assertTrue(bayRotation(deck, graph, List.of(variant)).contains(Affordance.SERVICE),
                "a machine was parked and no technician's rotation included servicing it");
    }

    /** The jobs a technician posted to this deck's bay comes round to. */
    private static List<Affordance> bayRotation(MapResult deck, DeckGraph graph,
                                                List<MechVariant> lance) {
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck, graph, SEED, null)) {
            scene.occupyGantries(lance);
            return scene.watchBill(scene.room(RoomPurpose.VEHICLE_BAY),
                    CrewRole.MECH_TECH).jobs();
        }
    }

    /** The bay's live servicing jobs with that lance parked. */
    private static List<FixtureTask> servicing(MapResult deck, DeckGraph graph,
                                               List<MechVariant> lance) {
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck, graph, SEED, null)) {
            scene.occupyGantries(lance);
            DeckGraph.Compartment bay = scene.room(RoomPurpose.VEHICLE_BAY);
            boolean[] berthed = new boolean[deck.gantries.size()];
            for (int index = 0; index < Math.min(lance.size(), berthed.length); index++) {
                berthed[index] = true;
            }
            return JobBoard.live(deck.fixtureTasks, bay, berthed).stream()
                    .filter(task -> task.affordance() == Affordance.SERVICE)
                    .toList();
        }
    }

    /**
     * Nobody is put down inside the ship.
     *
     * <p>A watch is spread across its loop on purpose, so at any given instant
     * most of it is between jobs — and the pose sampler draws that as the
     * straight line from one stop to the next. That is a deliberate bypass of
     * collision for scenes which freeze time and only want a picture, and it was
     * also, for a while, how staffing chose where to spawn people. On a
     * generated deck the line crosses bulkheads: a seeded sweep found one shift
     * in seventy standing in one, which is not a rendering blemish but an actor
     * physically stuck in a wall for as long as the ship exists.
     */
    @Test
    void aStaffedDeckPutsNobodyInsideABulkhead() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), SEED, null)) {
            World world = scene.simulation().world();
            int placed = 0;
            for (DeckGraph.Compartment room : generator.getLastDeckGraph().compartments()) {
                for (CrewRole role : CrewRole.values()) {
                    for (long hand : scene.staff(room, role, 4)) {
                        placed++;
                        int cellX = world.cellX(hand);
                        int cellY = world.cellY(hand);
                        assertTrue(deck.grid.isWalkable(cellX, cellY),
                                role + " posted to " + room.purpose() + " was put down at "
                                        + cellX + "," + cellY + ", which is not floor");
                    }
                }
            }
            assertTrue(placed > 20,
                    "only " + placed + " hands were placed, so this is not testing much");
        }
    }

    /**
     * A troop transport's deck, sized to its own program.
     *
     * <p>Deliberately not a fixed width and height. A vehicle bay is a large
     * room, and forcing it onto an arbitrary deck means it sometimes fails to
     * place — which turns a berth test into a test that quietly skips. Sizing
     * the deck from the program is also what the game does.
     */
    private static MapResult deck() {
        return new ShipDeckGenerator().generateDeck(transportPlan(), SEED, null);
    }

    private static DeckSizing.DeckPlan transportPlan() {
        return DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                10, 250, 50, TRANSPORT_ASPECT);
    }

    /** Fraction of pixels the renderer actually put something opaque into. */
    private static float painted(BufferedImage image) {
        int opaque = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 0x80) opaque++;
            }
        }
        return opaque / (float) (image.getWidth() * image.getHeight());
    }

    /**
     * A detail on a generated range puts rounds downrange.
     *
     * <p>The last of the four jobs a marine has, and the only one that resolves
     * against the simulation rather than only posing. Nothing here is authored:
     * the fitting bound each firing point to the butts it faces, so the shift's
     * own focus is what the target is spawned on and what the shooter fires at.
     *
     * <p>Campaign personnel and inventory stay untouched — the shooters are
     * simulation-owned marines on the deck's own roster, and the target is a
     * frame the scene spawned, not a soldier anybody recruited.
     */
    @Test
    void aDetailOnAGeneratedRangeFiresDownIt() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), SEED, null)) {
            DeckGraph.Compartment range = scene.room(RoomPurpose.FIRING_RANGE);
            assertNotNull(range, "the deck generated no firing range to shoot on");
            long[] detail = scene.staff(range, CrewRole.MARINE, 4);
            assertTrue(detail.length > 0, "the range took nobody on");

            Set<Long> shooters = new HashSet<>();
            for (long actor : detail) shooters.add(actor);

            int rounds = 0;
            for (int step = 0; step < 400 && rounds == 0; step++) {
                scene.simulation().advance(0.1f);
                for (ShotEvent shot : scene.simulation().getShotsThisFrame()) {
                    if (shooters.contains(shot.shooterId)) rounds++;
                }
            }
            assertTrue(rounds > 0,
                    "the detail stood on the firing line and never fired a round");
        }
    }
}
