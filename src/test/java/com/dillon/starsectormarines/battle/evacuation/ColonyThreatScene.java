package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Both Rav Bloom colonies on a patch of creep, for how they read as armed ({@code colony-breach-nouns.md}, Spore colony
 * and Creep: Ground colony): a spore colony of each grade with nothing overhead, breathing its spores, and a ground
 * colony driving spines up under a file of Marines in its reach. The creep is grown before the recording starts and
 * the ground colony is set down then, so every recorded strike is a whole one. It is about the look; its one verdict is
 * that the ground colony struck.
 */
public final class ColonyThreatScene implements BehaviorScene {

    static final int W = 56, H = 32;
    static final int CREEP_X = 24, CREEP_Y = 16;
    static final int COLONY_X = 28, COLONY_Y = 16;
    static final int FILE_X = 33;
    private static final int WARM_TICKS = 900;
    private static final int TICKS = 330;
    private static final int FRAME_EVERY = 2;

    @Override public String id() { return "colony-threat"; }

    @Override public String label() {
        return "The Rav Bloom's colonies read as armed: spore colonies breathing spores, a ground colony's spines run under the creep and erupt";
    }

    @Override public int frameDelayMillis() { return 67; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(W, H).seed(20261022L).missionCompletion(false);
        builder.squad("file").faction(Faction.MARINE).size(4).at(FILE_X, COLONY_Y - 1).unarmed().health(4000f)
                .stationary().done();
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            sim.creepField().putSource(-1L, CREEP_X, CREEP_Y, new CreepGrowth(1.2f, 16f));
            sim.spawn(MapTurret.create("Spore Colony (fissure)", Faction.DEFENDER, "structure.spore-colony-fissure",
                    CREEP_X - 5, CREEP_Y + 5));
            sim.spawn(MapTurret.create("Spore Colony (bore)", Faction.DEFENDER, "structure.spore-colony-bore",
                    CREEP_X - 3, CREEP_Y - 5));
            sim.spawn(MapTurret.create("Spore Colony (maw)", Faction.DEFENDER, "structure.spore-colony-maw",
                    CREEP_X - 9, CREEP_Y));
            SceneRun run = SceneRun.of(world);
            run.run(WARM_TICKS);
            long colony = sim.spawn(MapTurret.create("Ground Colony", Faction.DEFENDER, GroundColonies.STRUCTURE,
                    COLONY_X, COLONY_Y));
            Strikes strikes = new Strikes(colony);
            run.observe(strikes)
                    .frames(frames, "threat", FRAME_EVERY, tick -> "colony threat / " + strikes.count
                            + " spine strikes / tick " + tick)
                    .run(TICKS);
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("strikes", strikes.count);
            return List.of(new SceneReport(id(), "threat", TICKS, List.of(
                    Verdict.of("ground-colony-strikes", strikes.count > 0,
                            strikes.count + " spine strikes on the Marines in reach")), metrics));
        }
    }

    private static final class Strikes implements TickObserver {
        private final long colony;
        int count;

        Strikes(long colony) { this.colony = colony; }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            for (ShotEvent shot : sim.getShotsThisFrame()) if (shot.shooterId == colony) count++;
        }
    }
}
