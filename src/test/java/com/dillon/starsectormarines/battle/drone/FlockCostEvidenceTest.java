package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What derelict flocks cost a tick (`derelict-drone-flocks.md`, slice 5): four flocks of sixteen on a vast map
 * (280x168, the vast ruins plan's size) laid out as blocks of roofed buildings with streets between, two flocks
 * probing long routes back and forth through the blocks and two striking durable markers, one in a street and one
 * inside a building (held at its threshold). The control is the same map and markers with no flocks. Nothing shoots
 * the flocks, so they stay whole: the worst case for their cost. Also times the pieces the serial part of the tick
 * builds: a member field, a map-wide threshold search, and the outside's labelling.
 *
 * <p>Evidence, not a unit test: {@code gradlew profileFlockCost}; writes
 * {@code build/reports/performance/flock-cost/summary.md}.
 */
@Tag("flock-cost-evidence")
class FlockCostEvidenceTest {

    static final int WIDTH = 280, HEIGHT = 168;
    static final int BLOCK = 12, STREET = 8;
    static final int FLOCKS = 4, MEMBERS = 16;
    static final int WARMUP = Integer.getInteger("battle.flockCost.warmupTicks", 300);
    static final int MEASURE = Integer.getInteger("battle.flockCost.measureTicks", 1500);

    @Test
    void flocksCostLittleBesideTheirBattle() throws Exception {
        double[] control = run(false);
        double[] flocks = run(true);
        double[] pieces = pieces();
        double meanDelta = flocks[0] - control[0];
        StringBuilder md = new StringBuilder();
        md.append("# Flock cost\n\n");
        md.append(String.format(Locale.ROOT, "Map %dx%d, %d flocks of %d, %d warmup + %d measured ticks, serial "
                + "unit update.%n%n", WIDTH, HEIGHT, FLOCKS, MEMBERS, WARMUP, MEASURE));
        md.append("| run | mean ms/tick | p95 ms/tick | max ms/tick |\n|---|---|---|---|\n");
        md.append(String.format(Locale.ROOT, "| control (no flocks) | %.3f | %.3f | %.3f |%n", control[0], control[1],
                control[2]));
        md.append(String.format(Locale.ROOT, "| four flocks of sixteen | %.3f | %.3f | %.3f |%n", flocks[0],
                flocks[1], flocks[2]));
        md.append(String.format(Locale.ROOT, "%nFlocks add %.3f ms a tick on the mean (%.3f ms a flock).%n%n",
                meanDelta, meanDelta / FLOCKS));
        md.append("| piece | ms |\n|---|---|\n");
        md.append(String.format(Locale.ROOT, "| member field, radius %d | %.3f |%n", FlockSystem.FIELD_RADIUS,
                pieces[0]));
        md.append(String.format(Locale.ROOT, "| threshold search from a building's middle, map-wide | %.3f |%n",
                pieces[1]));
        md.append(String.format(Locale.ROOT, "| the outside labelled, whole map | %.3f |%n", pieces[2]));
        Path out = Path.of(System.getProperty("battle.flockCost.outputDir", "build/reports/performance/flock-cost"));
        Files.createDirectories(out);
        Files.writeString(out.resolve("summary.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.println(md);
        assertTrue(flocks[0] > 0.0);
    }

    /** {@code {mean, p95, max}} milliseconds a tick. */
    private static double[] run(boolean withFlocks) {
        SceneBuilder b = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        List<int[]> blocks = new ArrayList<>();
        for (int y = STREET; y + BLOCK < HEIGHT - STREET; y += BLOCK + STREET) {
            for (int x = STREET; x + BLOCK < WIDTH - STREET; x += BLOCK + STREET) {
                b.wall(x, y, x + BLOCK - 1, y).wall(x, y + BLOCK - 1, x + BLOCK - 1, y + BLOCK - 1)
                        .wall(x, y, x, y + BLOCK - 1).wall(x + BLOCK - 1, y, x + BLOCK - 1, y + BLOCK / 2 - 2)
                        .wall(x + BLOCK - 1, y + BLOCK / 2 + 1, x + BLOCK - 1, y + BLOCK - 1);
                blocks.add(new int[]{x, y});
            }
        }
        int[] inside = blocks.get(blocks.size() / 2);
        b.unit("street-marker", Faction.MARINE, UnitType.MARINE, WIDTH - STREET / 2 - 2, HEIGHT / 2,
                spec -> spec.moveSpeed(0f).health(1.0e7f));
        b.unit("inside-marker", Faction.MARINE, UnitType.MARINE, inside[0] + BLOCK / 2, inside[1] + BLOCK / 2,
                spec -> spec.moveSpeed(0f).health(1.0e7f));
        b.unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, 2, 2, spec -> spec.moveSpeed(0f));
        SceneWorld world = b.build();
        BattleSimulation sim = world.sim();
        CellTopology topology = sim.getTopology();
        int id = 1;
        for (int[] block : blocks) {
            for (int y = block[1]; y < block[1] + BLOCK; y++) {
                for (int x = block[0]; x < block[0] + BLOCK; x++) topology.setBuildingId(x, y, id);
            }
            id++;
        }
        List<Flock> flocks = new ArrayList<>();
        float streetY = STREET / 2f;
        if (withFlocks) {
            for (int f = 0; f < FLOCKS; f++) {
                float x = STREET / 2f + f * 3f, y = HEIGHT / 2f + (f % 2 == 0 ? -1 : 1) * (BLOCK + STREET) / 2f;
                flocks.add(sim.flocks().launch(sim, Faction.DEFENDER, x, y, MEMBERS, FlockWeights.WASP, 1000f));
            }
            flocks.get(0).probe(WIDTH - 6f, streetY, WIDTH - 6f, HEIGHT - streetY, 6f, HEIGHT - streetY, 6f, streetY);
            flocks.get(1).probe(WIDTH / 2f, HEIGHT - streetY, 6f, HEIGHT / 2f, WIDTH - 6f, HEIGHT / 2f);
            flocks.get(2).strike(world.unit("street-marker"));
            flocks.get(3).strike(world.unit("inside-marker"));
        }
        double[] ms = new double[MEASURE];
        for (int t = 0; t < WARMUP + MEASURE; t++) {
            long start = System.nanoTime();
            int before = sim.getSimTickIndex();
            sim.advance(BattleSimulation.TICK_DT);
            if (sim.getSimTickIndex() == before) throw new IllegalStateException("the battle stopped ticking");
            if (t >= WARMUP) ms[t - WARMUP] = (System.nanoTime() - start) / 1e6;
        }
        if (withFlocks) {
            int up = 0;
            for (Flock flock : flocks) up += flock.size();
            System.out.printf("    [flock-cost] %d of %d members still up at the end%n", up, FLOCKS * MEMBERS);
        }
        sim.close();
        double[] sorted = ms.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(ms).average().orElse(0);
        return new double[]{mean, sorted[(int) (sorted.length * 0.95)], sorted[sorted.length - 1]};
    }

    /** {@code {field, threshold, outside}} milliseconds, each the mean of repeated builds on a city map. */
    private static double[] pieces() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        FlockField.Open open = (x, y) -> {
            if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) return false;
            int bx = (x - STREET) % (BLOCK + STREET), by = (y - STREET) % (BLOCK + STREET);
            return x < STREET || y < STREET || bx >= BLOCK || by >= BLOCK;
        };
        FlockField.Open walk = (x, y) -> x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT;
        int reps = 50;
        for (int i = 0; i < 10; i++) FlockField.build(WIDTH / 2, STREET / 2, FlockSystem.FIELD_RADIUS, open);
        long t0 = System.nanoTime();
        for (int i = 0; i < reps; i++) FlockField.build(WIDTH / 2, STREET / 2, FlockSystem.FIELD_RADIUS, open);
        double field = (System.nanoTime() - t0) / 1e6 / reps;
        int mx = STREET + BLOCK / 2, my = STREET + BLOCK / 2;
        long t1 = System.nanoTime();
        for (int i = 0; i < reps; i++) FlockField.threshold(mx, my, Math.max(WIDTH, HEIGHT), walk, open);
        double threshold = (System.nanoTime() - t1) / 1e6 / reps;
        long t2 = System.nanoTime();
        for (int i = 0; i < reps; i++) FlockSystem.outside(WIDTH, HEIGHT, open);
        double outside = (System.nanoTime() - t2) / 1e6 / reps;
        assertTrue(grid.getWidth() == WIDTH);
        return new double[]{field, threshold, outside};
    }
}
