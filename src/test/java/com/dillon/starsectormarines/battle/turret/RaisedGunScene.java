package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A gun on a plinth fires over its own line (`ground-turret-tiers.md`).
 *
 * <p>A cannon stands at the west end of an open field with five of its side's men in a file seven cells east of it,
 * and four marines past them, in its reach and outside its dead zone. The men are bodies, not squads, so nobody but
 * the gun fires, and whatever the file loses came from it.
 *
 * <ul>
 *   <li>{@code plinth}: the cannon hardpoint, one tier up. Its rounds leave from four metres and pass over the file;
 *       the verdict asserts the file loses nothing while the gun fires (a mechanic: the geometry of the height).</li>
 *   <li>{@code ground}: the control, the same weapon on a one-cell emplacement at ground level behind the same
 *       file. Its crew looks down the lane, finds its own men in it and holds; how many rounds it gets off is a
 *       reading.</li>
 *   <li>{@code dead-zone}: the hardpoint with one marine at its foot, inside the distance its depression limit
 *       leaves it unable to reach; the verdict asserts it never fires.</li>
 * </ul>
 */
public final class RaisedGunScene implements BehaviorScene {

    private static final int W = 48;
    private static final int H = 31;
    private static final int GUN_X = 10;
    private static final int GUN_Y = 15;
    private static final int TICKS = 900;

    @Override public String id() { return "raised-gun"; }

    @Override public String label() { return "A gun on a plinth fires over its own line; at its foot it cannot reach"; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(overTheLine("plinth", "structure.hardpoint-cannon", true, frames));
        reports.add(overTheLine("ground", TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID, false, frames));
        reports.add(deadZone(frames));
        return reports;
    }

    private SceneReport overTheLine(String loop, String structureId, boolean asserted, FrameSink frames) {
        SceneBuilder builder = field(structureId);
        // Bodies, not squads: an unsquadded body cannot fire, so the gun is the only thing shooting.
        long[] line = new long[5];
        long[] targets = new long[4];
        for (int i = 0; i < line.length; i++) {
            builder.unit("line" + i, Faction.DEFENDER, UnitType.MARINE, GUN_X + 7, GUN_Y - 2 + i, RaisedGunScene::holdStill);
        }
        for (int i = 0; i < targets.length; i++) {
            builder.unit("target" + i, Faction.MARINE, UnitType.MARINE, GUN_X + 24, GUN_Y - 1 + i, RaisedGunScene::endure);
        }
        SceneWorld scene = builder.build();
        for (int i = 0; i < line.length; i++) line[i] = scene.unit("line" + i);
        for (int i = 0; i < targets.length; i++) targets[i] = scene.unit("target" + i);
        try (BattleSimulation sim = scene.sim()) {
            long gun = scene.structures()[0];
            float lineBefore = health(sim, line);
            float targetsBefore = health(sim, targets);
            for (int tick = 0; tick < TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                if (tick % 10 == 0) frames.frame(loop, sim, tick, "raised gun / " + loop + " / " + tick);
            }
            int rounds = sim.getRoster().telemetry().roundsFired(gun);
            float lineLost = lineBefore - health(sim, line);
            float targetsLost = targetsBefore - health(sim, targets);
            List<Verdict> verdicts = new ArrayList<>();
            if (asserted) {
                verdicts.add(Verdict.of("gun-fired", rounds > 0, rounds + " rounds fired past the file"));
                verdicts.add(Verdict.of("file-under-the-round", rounds == 0 || lineLost == 0f,
                        "the file lost " + lineLost + " health under " + rounds + " rounds"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("roundsFired", rounds);
            metrics.put("fileHealthLost", lineLost);
            metrics.put("targetHealthLost", targetsLost);
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private SceneReport deadZone(FrameSink frames) {
        SceneWorld scene = field("structure.hardpoint-cannon")
                .unit("foot", Faction.MARINE, UnitType.MARINE, GUN_X + 7, GUN_Y, RaisedGunScene::endure)
                .build();
        try (BattleSimulation sim = scene.sim()) {
            long gun = scene.structures()[0];
            for (int tick = 0; tick < TICKS / 3; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                if (tick % 10 == 0) frames.frame("dead-zone", sim, tick, "raised gun / dead zone / " + tick);
            }
            int rounds = sim.getRoster().telemetry().roundsFired(gun);
            StructureDef structure = TurretCatalogRegistry.requireStructure("structure.hardpoint-cannon");
            List<Verdict> verdicts = List.of(Verdict.of("holds-at-its-foot", rounds == 0,
                    rounds + " rounds at a marine 7 cells out, inside " + structure.minRangeCells() + " cells"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("roundsFired", rounds);
            metrics.put("deadZoneCells", structure.minRangeCells());
            return new SceneReport(id(), "dead-zone", TICKS / 3, verdicts, metrics);
        }
    }

    private static void holdStill(EntitySpec spec) {
        spec.moveSpeed = 0f;
    }

    /** A target that outlasts the run, so the gun keeps firing past the file for all of it. */
    private static void endure(EntitySpec spec) {
        spec.moveSpeed = 0f;
        spec.hp = spec.maxHp = 4000f;
    }

    /** Open field with the gun at the west end. */
    private static SceneBuilder field(String structureId) {
        List<DefensePost> posts = List.of(new DefensePost(DefensePostKind.LIGHT, GUN_X, GUN_Y,
                List.of(new DefensePost.TurretSpec(structureId, GUN_X, GUN_Y))));
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
            }
        }
        return SceneBuilder.over(grid, topology).seed(20261023L).missionCompletion(false).posts(posts);
    }

    private static float health(BattleSimulation sim, long[] ids) {
        float sum = 0f;
        for (long id : ids) if (sim.world().isAlive(id)) sum += sim.world().hp(id);
        return sum;
    }
}
