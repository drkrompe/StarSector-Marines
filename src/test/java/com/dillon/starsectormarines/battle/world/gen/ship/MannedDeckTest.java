package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.sim.IdentityService;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole ship runs, not the room somebody is looking at.
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
final class MannedDeckTest {

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
     * The clock only runs forwards, and running it twice to the same instant
     * changes nothing.
     *
     * <p>A host takes more than one render pass off a frame — a backdrop and an
     * actor pass have to agree about where everybody is standing — so advancing
     * has to be something a screen can ask for repeatedly.
     */
    @Test
    void aDeckClockIsMonotonicAndIdempotent() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(transportPlan(), SEED, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), SEED, null)) {
            long[] crew = scene.manDeck(2);
            assertTrue(crew.length > 0, "nobody came aboard");

            scene.advanceTo(12f);
            World world = scene.simulation().world();
            float[] settledX = new float[crew.length];
            for (int index = 0; index < crew.length; index++) {
                settledX[index] = world.x(crew[index]);
            }

            scene.advanceTo(12f);
            scene.advanceTo(3f);
            assertEquals(12f, scene.simulatedSeconds(), 1e-4f,
                    "asking for an earlier time wound the deck back");
            for (int index = 0; index < crew.length; index++) {
                assertEquals(settledX[index], world.x(crew[index]), 1e-4f,
                        "re-advancing to the same instant moved somebody");
            }
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
        CompanyDeck ship = new CompanyDeck(CompanyShip.founding(), SEED);
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
                CompanyShip.founding(), SEED, null, null, () -> company);
        try {
            ShipDeckBattleScene scene = ship.scene();
            int billeted = 0;
            for (MarineSoldier soldier : company) {
                if (ship.marineFor(soldier.id()) != 0L) billeted++;
            }
            assertTrue(billeted > 0, "the ship billeted none of the company");

            IdentityService identity = scene.simulation().identity();
            int marines = 0;
            for (int index = 0; index < scene.simulation().getRoster().liveCount(); index++) {
                long actor = scene.simulation().getRoster().get(index);
                if (identity.type(actor) == CrewRole.MARINE.unit()) marines++;
            }
            assertEquals(billeted, marines,
                    "crewing the ship added marines who are on no muster roll");
        } finally {
            ship.dismiss();
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
