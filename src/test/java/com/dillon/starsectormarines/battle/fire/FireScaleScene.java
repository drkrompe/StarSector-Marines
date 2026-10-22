package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Fire at the largest map size with fuel everywhere (`fire-nouns.md`, Laws: pays
 * for what burns): a 560x336 country of grass and groves with creep grown over
 * its middle, lit along its west edge in a fresh wind, and a bare four-cell
 * road across its east third. The verdict is the break holding at scale; the
 * metrics are what the fire costs the simulation thread each fire step (the
 * field, its harm and its route costing), its peak burning cells and how long
 * the country takes to burn.
 */
public final class FireScaleScene implements BehaviorScene {

    static final int WIDTH = 560, HEIGHT = 336, BREAK_X0 = 400, BREAK_X1 = 403;
    private static final long SEED = 20261023L;
    private static final int LIGHT_TICK = 600;
    private static final int TICKS = LIGHT_TICK + 30 * 240;

    @Override public String id() { return "fire-scale"; }

    @Override public String label() { return "A whole 560x336 country of grass, groves and creep burning in a wind"; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        List<Doodad> doodads = new ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                boolean road = x >= BREAK_X0 && x <= BREAK_X1;
                topology.setGroundKind(x, y, road ? CellTopology.GroundKind.STREET : CellTopology.GroundKind.GRASS);
            }
        }
        List<ForestField.Tree> trees = new ArrayList<>();
        TreeCanopy[] kinds = {TreeCanopy.OAK, TreeCanopy.PINE_LARGE, TreeCanopy.OAK_SMALL, TreeCanopy.CEDAR, TreeCanopy.BIRCH};
        Random rng = new Random(SEED);
        // Groves: blocks of close-set trees with glades between them.
        for (int gy = 4; gy + 40 < HEIGHT; gy += 48) {
            for (int gx = 20; gx + 40 < WIDTH; gx += 56) {
                if (gx + 40 >= BREAK_X0 - 4 && gx <= BREAK_X1 + 4) continue;
                for (int ty = gy; ty + 7 < gy + 40; ty += 7) {
                    for (int tx = gx; tx + 7 < gx + 40; tx += 7) {
                        TreeCanopy kind = kinds[rng.nextInt(kinds.length)];
                        trees.add(new ForestField.Tree(tx + rng.nextInt(2), ty + rng.nextInt(2), kind, 0));
                    }
                }
            }
        }
        ForestBrush.stampTrees(trees, grid, topology, doodads, null, false);
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false);
        builder.unit("far-a", Faction.MARINE, UnitType.MARINE, 2, 2);
        builder.unit("far-b", Faction.DEFENDER, UnitType.MARINE, WIDTH - 3, HEIGHT - 3);
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            for (Doodad d : doodads) sim.addDoodad(d);
            sim.creepField().putSource(1L, 200, HEIGHT / 2, new CreepGrowth(6f, 90f));
            sim.creepField().putSource(2L, 120, 80, new CreepGrowth(6f, 50f));
            int[] peak = {0}, lastBurning = {-1};
            int[] pastBreak = {0};
            SceneRun.of(world).observe((s, tick) -> {
                if (tick == LIGHT_TICK) {
                    FireField fire = s.fireField();
                    fire.setWind(1f, 0.2f, 0.7f);
                    for (int y = 0; y < HEIGHT; y++) fire.addHeat(1, y, 3000);
                }
                FireField fire = s.fireFieldIfPresent();
                if (fire == null) return;
                peak[0] = Math.max(peak[0], fire.burningCount());
                if (fire.burningCount() > 0) lastBurning[0] = tick;
                if (tick % 150 == 0) {
                    int crossed = 0;
                    for (int y = 0; y < HEIGHT; y++)
                        for (int x = BREAK_X1 + 1; x < WIDTH; x++)
                            if (fire.stateAt(y * WIDTH + x) != FireField.UNBURNT) crossed++;
                    pastBreak[0] = crossed;
                }
            }).frames(frames, "country", 300, tick -> "country / tick " + tick).run(TICKS);
            FireCost cost = sim.fireCostProfile();
            List<Verdict> verdicts = List.of(Verdict.of("the-break-holds-at-scale", pastBreak[0] == 0,
                    pastBreak[0] + " cells past the four-cell road caught"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("peakBurning", peak[0]);
            metrics.put("burntCells", sim.fireFieldIfPresent() == null ? 0 : sim.fireFieldIfPresent().burntCount());
            metrics.put("burnSeconds", lastBurning[0] < 0 ? -1 : (lastBurning[0] - LIGHT_TICK) / 30f);
            metrics.put("fireSteps", cost.steps());
            metrics.put("meanStepMs", cost.meanMillis());
            metrics.put("p99StepMs", cost.p99Millis());
            metrics.put("maxStepMs", cost.maxMillis());
            return List.of(new SceneReport(id(), "country", TICKS, verdicts, metrics));
        }
    }
}
