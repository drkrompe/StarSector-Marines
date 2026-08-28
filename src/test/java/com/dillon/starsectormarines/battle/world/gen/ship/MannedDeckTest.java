package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * ordinary fixed-step battle clock, and ask what the ship did.
 */
final class MannedDeckTest {

    private static final long SEED = 42L;
    private static final float TRANSPORT_ASPECT = 0.28f;

    /** Constructing the headless drain installs the tile catalogs the deck sim needs. */
    @BeforeAll
    static void installCatalogs() {
        Path modRoot = Paths.get("mod").toAbsolutePath().normalize();
        new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(modRoot), modRoot);
    }

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

            for (float second = 0.1f; second <= 120f; second += 0.1f) {
                scene.advanceTo(second);
            }

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

    private static DeckSizing.DeckPlan transportPlan() {
        return DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                10, 250, 50, TRANSPORT_ASPECT);
    }
}
