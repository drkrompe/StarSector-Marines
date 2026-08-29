package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskPose;
import com.dillon.starsectormarines.battle.sim.IdentityService;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a manned ship actually spends her time doing.
 *
 * <p>Evidence rather than a test, and opt-in for the reason the project always
 * gives: answering the question needs two whole hulls crewed and run for four
 * minutes of ship's time apiece, which is minutes of wall clock on every suite
 * run by every concurrent session forever. Reach for it when changing the
 * ambient model, the room program, or a fitting's published work; leave it out
 * of {@code :test}.
 *
 * <p>Run it with {@code gradlew.bat crewEvidence}.
 *
 * <p><b>The figure that matters is the idle share</b>, and the standing goal is
 * that it is nothing. An actor with nowhere to be and an actor sitting in the
 * lounge are the same picture from outside, and the whole ambient model exists
 * to make sure the second is what you are looking at: idleness a crew
 * <em>chose</em> reads as a ship people live on, and idleness imposed by a
 * rotation with nowhere to send them is a defect. So {@link AmbientActivity#IDLE}
 * is the defect and {@code SOCIALIZING}, {@code RESTING} and {@code EXERCISING}
 * are the goal, and the histogram keeps them apart.
 *
 * <p><b>It counts the dead, too, and that is not incidental.</b> A casualty has
 * no ambient pose, so every instrument that asks "is anybody standing about?"
 * counts a corpse as somebody standing about. That is exactly how the ship's own
 * firing range was caught quietly killing marines — it was manufacturing the
 * defect this measures and reading as one.
 *
 * <p>Readings that have been true here, for comparison: before the rotation was
 * paced by the worker, 82% of a transport crew's samples and 90% of a capital's
 * were somebody motionless with nothing to do, and every hand aboard idled
 * fifteen seconds or more in one stretch.
 */
@Tag("crew-liveliness")
class CrewLivelinessEvidence {

    /** Simulated seconds between ticks. The deck's own clock granularity. */
    private static final float STEP = 0.1f;

    /**
     * How long each hull is run. Long enough for a marine to cross a capital,
     * eat, and come back, which is the loop the measurement is about.
     */
    private static final float RUN = 240f;

    /** How often each actor is asked what it is doing. */
    private static final float SAMPLE = 0.5f;

    /**
     * How long a stretch of doing nothing has to be before it is worth a line of
     * its own. Below this an actor is between jobs; above it, something has gone
     * wrong for them.
     */
    private static final float TOO_LONG = 15f;

    @Test
    void measure() {
        report("transport", HullClass.CRUISER, HullRole.TROOP_TRANSPORT, 10, 250, 50, 0.28f);
        report("capital", HullClass.CAPITAL, HullRole.TROOP_TRANSPORT, 60, 600, 120, 0.3f);
    }

    private void report(String name, HullClass hullClass, HullRole role,
                        int crewMin, int crewMax, int cargo, float aspect) {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(DeckSizing.planFor(
                hullClass, role, crewMin, crewMax, cargo, aspect), 42L, null);
        DeckGraph graph = generator.getLastDeckGraph();

        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck, graph, 42L, null)) {
            long[] crew = scene.manDeck();
            Map<Affordance, Integer> jobs = new EnumMap<>(Affordance.class);
            for (FixtureTask task : scene.fixtureTasks()) {
                jobs.merge(task.affordance(), 1, Integer::sum);
            }

            Map<Long, Integer> idleSamples = new HashMap<>();
            Map<Long, Integer> longestIdle = new HashMap<>();
            Map<Long, Integer> currentIdle = new HashMap<>();
            Map<AmbientActivity, Integer> histogram = new EnumMap<>(AmbientActivity.class);
            int samples = 0;
            float nextSample = 0f;
            for (float second = STEP; second <= RUN; second += STEP) {
                scene.advanceTo(second);
                if (second < nextSample) continue;
                nextSample += SAMPLE;
                samples++;
                for (long hand : crew) {
                    AmbientTaskPose pose = scene.simulation().ambientTasks().pose(hand);
                    // No pose is not "resting". It is an actor the ambient
                    // service is not running - stood down, or dead.
                    AmbientActivity activity = pose == null
                            ? AmbientActivity.IDLE : pose.activity();
                    histogram.merge(activity, 1, Integer::sum);
                    if (activity == AmbientActivity.IDLE) {
                        idleSamples.merge(hand, 1, Integer::sum);
                        int run = currentIdle.merge(hand, 1, Integer::sum);
                        longestIdle.merge(hand, run, Math::max);
                    } else {
                        currentIdle.put(hand, 0);
                    }
                }
            }

            System.out.println();
            System.out.println("=== " + name + " ===");
            System.out.println("compartments=" + graph.compartments().size()
                    + " crew=" + crew.length
                    + " authoredJobs=" + scene.fixtureTasks().size());
            System.out.println("jobs by affordance: " + jobs);

            int totalIdle = 0;
            for (int count : idleSamples.values()) totalIdle += count;
            System.out.printf("idle share: %.2f%%  of %d actor-samples%n",
                    100.0 * totalIdle / Math.max(1, samples * crew.length),
                    samples * crew.length);
            System.out.println("activity histogram: " + histogram);

            int stuck = 0;
            int worst = 0;
            for (long hand : crew) {
                int longest = longestIdle.getOrDefault(hand, 0);
                worst = Math.max(worst, longest);
                if (longest * SAMPLE >= TOO_LONG) stuck++;
            }
            System.out.println("longest idle run: " + (worst * SAMPLE) + "s"
                    + "; actors idle " + TOO_LONG + "s+ in one stretch: "
                    + stuck + "/" + crew.length);

            int alive = 0;
            for (long hand : crew) {
                if (scene.simulation().getRoster().isLive(hand)) alive++;
            }
            System.out.println("still alive after " + RUN + "s: "
                    + alive + "/" + crew.length);

            IdentityService identity = scene.simulation().identity();
            Map<String, List<Long>> byType = new LinkedHashMap<>();
            for (long hand : crew) {
                byType.computeIfAbsent(identity.type(hand).name(), key -> new ArrayList<>())
                        .add(hand);
            }
            for (Map.Entry<String, List<Long>> entry : byType.entrySet()) {
                System.out.println("  " + entry.getKey() + " n=" + entry.getValue().size());
            }

            Map<RoomPurpose, int[]> manned = new EnumMap<>(RoomPurpose.class);
            for (DeckGraph.Compartment room : graph.compartments()) {
                int[] tally = manned.computeIfAbsent(room.purpose(), key -> new int[2]);
                tally[0]++;
                for (long hand : crew) {
                    if (room.contains(scene.simulation().world().cellX(hand),
                            scene.simulation().world().cellY(hand))) {
                        tally[1]++;
                        break;
                    }
                }
            }
            // A snapshot of one instant, so a room with a short job in it is
            // often empty at the moment it is read. Read it for whether a
            // purpose is ever occupied at all, not as an occupancy rate.
            System.out.println("compartments holding somebody at " + RUN + "s:");
            for (Map.Entry<RoomPurpose, int[]> entry : manned.entrySet()) {
                System.out.println("  " + entry.getKey() + " " + entry.getValue()[1]
                        + "/" + entry.getValue()[0]);
            }
        }
    }
}
