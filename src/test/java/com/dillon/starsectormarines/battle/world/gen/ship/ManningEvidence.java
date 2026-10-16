package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.sim.IdentityService;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evidence, not a test: the whole generated ship runs, not the room somebody is
 * looking at.
 *
 * <p>Each case generates a whole troop-transport deck, crews it and runs it for
 * minutes of ship's time, which cost ~35 s of wall clock inside {@code :test}.
 * The scene's own rules (berth occupancy, facing, what a bay's work depends on,
 * the monotonic clock, a lost boat's empty berth) are pinned on a hand-laid deck by
 * {@code ShipDeckBattleSceneTest}. Run with {@code gradlew.bat crewEvidence}.
 *
 * <p>A screen framed on a compartment is a camera, and a camera does not decide
 * what exists. Simulating only the framed room would make the ship's population
 * a fact about where the player is looking — walk from the bay to the berthing
 * and the technicians left behind would stop existing, the marines arrived at
 * would have been conjured on the way, and the passage between the two would be
 * empty because traffic in a corridor is what the compartments at both ends of
 * it produce.
 *
 * <p>So these tests never frame anything. They man the deck, run it through the
 * ordinary fixed-step battle clock, and ask what the ship did. Nothing here
 * loads a sprite, and nothing here should: the catalogs a deck sim reads are
 * installed for every test by {@code TileRegistryTestInstaller}, and standing a
 * headless renderer up to get at them would drag the whole battle sprite pack
 * into a test that draws nothing.
 */
@Tag("crew-liveliness")
final class ManningEvidence {

    private static final long SEED = 42L;
    private static final float TRANSPORT_ASPECT = 0.28f;

    /**
     * A manned deck is inhabited end to end, and stays that way once it is
     * running.
     *
     * <p>Two claims, and the second is the one that needs a clock. That the
     * ship is crewed in many compartments is a fact about manning; that
     * everybody is still on walkable floor after a few minutes of ordinary
     * simulation is a fact about the routes, the fills and the pathfinder
     * agreeing — and it is the check that a room-at-a-time scene could never
     * make, because it never had the rest of the ship to walk through.
     */
    @Test
    void aMannedDeckRunsEverywhereAtOnce() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        DeckGraph graph = generator.getLastDeckGraph();

        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck, graph, SEED, null)) {
            long[] crew = scene.manDeck(4);
            assertTrue(crew.length > 40,
                    "a whole transport was manned and produced " + crew.length + " hands");

            Set<Integer> inhabited = new HashSet<>();
            for (long hand : crew) {
                for (DeckGraph.Compartment room : graph.compartments()) {
                    if (room.contains(scene.simulation().world().cellX(hand),
                            scene.simulation().world().cellY(hand))) {
                        inhabited.add(room.id());
                    }
                }
            }
            assertTrue(inhabited.size() > 5,
                    "the whole crew started in " + inhabited.size() + " compartments");

            for (float second = 0.1f; second <= 180f; second += 0.1f) {
                scene.advanceTo(second);
            }

            World world = scene.simulation().world();
            for (long hand : crew) {
                int cellX = world.cellX(hand);
                int cellY = world.cellY(hand);
                assertTrue(deck.grid.isWalkable(cellX, cellY),
                        "after three minutes a hand is standing at " + cellX + "," + cellY
                                + ", which is not floor");
            }
        }
    }

    /**
     * Somebody nobody is looking at still goes to work.
     *
     * <p>The failure this guards is not a crash but a stage set: crew who move
     * only while framed, so the ship is busy wherever the player happens to be
     * and nowhere else. Nothing here frames anything, so if the deck only runs
     * under a camera, nobody moves at all.
     *
     * <p>Going to work is two facts and both are checked: that people cover
     * ground, and that somebody covering ground is <em>drawn</em> walking.
     * Ambient work authors the appearance as well as the position, and without
     * the second the crew slide around the deck at attention.
     */
    @Test
    void crewOutOfFrameGoToWork() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        DeckGraph graph = generator.getLastDeckGraph();

        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck, graph, SEED, null)) {
            long[] crew = scene.manDeck(4);
            World world = scene.simulation().world();
            float[] startX = new float[crew.length];
            float[] startY = new float[crew.length];
            for (int index = 0; index < crew.length; index++) {
                startX[index] = world.x(crew[index]);
                startY[index] = world.y(crew[index]);
            }

            boolean anyWalking = false;
            for (float second = 0.1f; second <= 120f; second += 0.1f) {
                scene.advanceTo(second);
                anyWalking = anyWalking || walking(scene, crew);
            }
            assertTrue(anyWalking, "nobody aboard was ever drawn walking");

            int moved = 0;
            for (int index = 0; index < crew.length; index++) {
                float dx = world.x(crew[index]) - startX[index];
                float dy = world.y(crew[index]) - startY[index];
                if (dx * dx + dy * dy > 1f) moved++;
            }
            assertTrue(moved > crew.length / 4,
                    "only " + moved + " of " + crew.length
                            + " hands went anywhere in two minutes of ship's time");
        }
    }

    /**
     * Every room view frames one ship, and she keeps running between them.
     *
     * <p>The question a shared scene answers and a per-screen one cannot is
     * where somebody <em>was</em> while the player was reading another page.
     * Two scenes make the two pages different ships; a scene that only runs
     * while its page is up puts everybody exactly where they were left, however
     * long the player was gone.
     */
    @Test
    void aCompanyDeckIsOneShipAcrossEveryRoomView() {
        CompanyDeck ship = new CompanyDeck(TestHulls.transport(), SEED);
        ShipDeckBattleScene fromBerthing = ship.scene();
        assertSame(fromBerthing, ship.scene(), "two room views got two different ships");
        assertSame(fromBerthing.simulation(), ship.scene().simulation(),
                "one ship is running two simulations");
        assertTrue(fromBerthing.simulation().getRoster().liveCount() > 0,
                "the ship was crewed with nobody");

        for (int frame = 0; frame < 600; frame++) ship.advance(1f / 60f);
        assertTrue(ship.elapsedSeconds() > 9f,
                "the ship only ran " + ship.elapsedSeconds() + " seconds");
        assertSame(fromBerthing, ship.scene(),
                "coming back to a room view rebuilt the ship");
    }

    /**
     * The marines aboard are the ones on the roster, and nobody else is.
     *
     * <p>The failure this guards is quiet and would look right: crewing the ship
     * posts a watch wherever a role has jobs, so a company mustered into one
     * bunkroom leaves every other berthing - and every room with a mess table -
     * to be filled with marines who are on no muster roll. The player would read
     * a roster of nine names and walk into a ship carrying a hundred and sixty.
     */
    @Test
    void theMarinesAboardAreTheOnesOnTheRoster() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        List<MarineSoldier> company = roster.soldiers();
        assertTrue(!company.isEmpty(), "the company has nobody in it");

        CompanyDeck ship = new CompanyDeck(
                TestHulls.transport(), SEED, null, null, () -> company, null);
        try {
            ShipDeckBattleScene scene = ship.scene();
            int billeted = 0;
            for (MarineSoldier soldier : company) {
                if (ship.marineFor(soldier.id()) != 0L) billeted++;
            }
            assertTrue(billeted > 0, "the ship billeted none of the company");

            IdentityService identity = scene.simulation().identity();
            Set<Long> mustered = new HashSet<>();
            for (MarineSoldier soldier : company) {
                long actor = ship.marineFor(soldier.id());
                if (actor != 0L) mustered.add(actor);
            }
            // The one marine-typed hand who is not on the roster is the ship's own
            // security rating at the command entrance (CrewRole.SECURITY).
            int unrostered = 0;
            for (int index = 0; index < scene.simulation().getRoster().liveCount(); index++) {
                long actor = scene.simulation().getRoster().get(index);
                if (identity.type(actor) == CrewRole.MARINE.unit() && !mustered.contains(actor)) unrostered++;
            }
            assertTrue(unrostered <= 1,
                    "crewing the ship added marines who are on no muster roll: " + unrostered);
        } finally {
            ship.dismiss();
        }
    }

    /**
     * A staffed bay is inhabited: technicians take jobs the room affords and
     * come round to a different one, without a single authored waypoint.
     *
     * <p>Advancing the clock is the whole test. A route that never moves anybody
     * off its first stop is indistinguishable from a static pose at the moment
     * of spawn, so this watches a technician's claim change rather than checking
     * that one was handed out.
     */
    @Test
    void staffedTechniciansCycleTheBaysJobs() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), SEED, null)) {
            DeckGraph.Compartment bay = scene.room(RoomPurpose.VEHICLE_BAY);
            List<MechVariant> lance = new ArrayList<>();
            for (Gantry berth : scene.berthsIn(bay)) lance.add(MechVariant.BULWARK);
            scene.occupyGantries(lance);
            long[] hands = scene.staff(bay, CrewRole.MECH_TECH, 5);
            assertTrue(hands.length > 1, "the bay took on " + hands.length + " technicians");

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
     * A ship's boat bays are a field, and her crew turn her boats round: a berth
     * is measurably better off after her crew have had time with it. Every boat
     * is sampled as it goes, since boats fly one after another and hull put back
     * on a boat that then launched is invisible to a single reading afterwards.
     */
    @Test
    void aShipsCrewTurnHerBoatsRound() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), SEED, null)) {
            AirfieldService bays = scene.simulation().getAirfieldService();
            assertTrue(bays.berths().size() > 1,
                    "a transport's boat bays registered " + bays.berths().size() + " berths");
            assertEquals(Faction.MARINE, bays.owner(), "the ship's own bays are not hers");

            scene.manDeck();
            scene.advanceTo(1f);
            Map<Long, Float> hurt = new LinkedHashMap<>();
            for (AirfieldService.Berth boat : bays.berths()) {
                float whole = scene.simulation().world().maxHp(boat.airframeId);
                scene.simulation().world().setHp(boat.airframeId, whole * 0.4f);
                hurt.put(boat.airframeId, whole);
            }

            boolean mended = false;
            for (float second = 11f; second <= 181f && !mended; second += 10f) {
                scene.advanceTo(second);
                for (Map.Entry<Long, Float> boat : hurt.entrySet()) {
                    if (!scene.simulation().world().isAlive(boat.getKey())) continue;
                    if (scene.simulation().world().hp(boat.getKey()) > boat.getValue() * 0.4f) {
                        mended = true;
                    }
                }
            }
            assertTrue(mended,
                    "three minutes of a ship's time put no hull back on any of her"
                            + " damaged boats");
        }
    }

    /**
     * Nobody is put down inside the ship.
     *
     * <p>A watch is spread across its loop on purpose, and the pose sampler draws
     * the in-between as a straight line from one stop to the next. On a generated
     * deck that line crosses bulkheads: a seeded sweep once found one shift in
     * seventy standing in one, an actor physically stuck in a wall for as long as
     * the ship exists. Hands now spawn on their first stop.
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
     * A detail on a generated range puts rounds downrange: the fitting bound each
     * firing point to the butts it faces, so the shift's own focus is what the
     * target is spawned on and what the shooter fires at.
     */
    @Test
    void aDetailOnAGeneratedRangeFiresDownIt() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), SEED, null)) {
            DeckGraph.Compartment range = scene.room(RoomPurpose.FIRING_RANGE);
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

    /** Whether anybody is carrying the moving flag this tick. */
    private static boolean walking(ShipDeckBattleScene scene, long[] crew) {
        BattleComponents components = scene.simulation().getBattleComponents();
        for (long hand : crew) {
            int flags = scene.simulation().getEntityWorld().getInt(hand,
                    components.LAYERED_ANIMATION, BattleComponents.LAYERED_FLAGS);
            if ((flags & LayeredAppearance.FLAG_MOVING) != 0) return true;
        }
        return false;
    }

    private static DeckSizing.DeckPlan transportPlan() {
        return DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                10, 250, 50, TRANSPORT_ASPECT);
    }
}
