package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ravs on a cannon tank: <b>does a tank with Ravs clawing at its hull still use its gun?</b> A deployed breakthrough
 * tank, a pack of Runners spawned at its side and a second wave coming on from inside the cannon's reach. The twin
 * cannon's burst would reach its own hull at claw range ({@link GunDeadZone}), so the crew holds any round at the
 * pack; the gun has to take the Ravs further off instead of keeping its aim on the nearest.
 *
 * <p>Verdict: Ravs die to the gun while the pack is on the hull. Before the dead zone the turret held the nearest
 * clawing Rav for the whole window and killed none. Rounds and the time to the first are readings.
 */
public final class RavsClawTankScene implements BehaviorScene {

    private static final int WIDTH = 60;
    private static final int HEIGHT = 30;
    private static final int WINDOW_TICKS = 900;
    private static final int SEEDS = 3;
    private static final int TANK_X = 14;
    private static final int TANK_Y = 15;
    private static final int PACK = 5;
    private static final int WAVE = 10;

    @Override public String id() { return "ravs-claw-tank"; }

    @Override public String label() { return "Ravs claw a cannon tank: the gun takes the Ravs it can hit"; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<Trace> traces = new ArrayList<>();
        for (long seed = 1; seed <= SEEDS; seed++) traces.add(playOnce(seed == 1 ? frames : FrameSink.NONE, seed));
        return List.of(report(traces));
    }

    private Trace playOnce(FrameSink frames, long seed) {
        SceneWorld world = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(seed).build();
        BattleSimulation sim = world.sim();
        long[] ravs = new long[PACK + WAVE];
        for (int i = 0; i < PACK; i++) ravs[i] = runner(sim, "Pack " + i, TANK_X + 3 + i % 2, TANK_Y - 2 + i);
        for (int i = 0; i < WAVE; i++) ravs[PACK + i] = runner(sim, "Wave " + i, TANK_X + 24 + i % 3, TANK_Y - 4 + i);
        float tx = TANK_X + .5f;
        float ty = TANK_Y + .5f;
        long tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.MARINE, VehicleMission.deployed(tx, ty));
        sim.convoy().body(tank).teleport(tx, ty, 0f);
        Trace trace = new Trace(tank, ravs);
        SceneRun run = SceneRun.of(world).observe(trace).frames(frames, id(), 10, trace::caption);
        try {
            while (run.tick() < WINDOW_TICKS && !trace.over(sim)) run.step();
            trace.ravsKilled = ravs.length - trace.alive(sim);
        } finally {
            sim.close();
        }
        trace.ticks = run.tick();
        return trace;
    }

    /** A Runner as the swarm spawns one: no kit, its own claws, pressing the nearest enemy. */
    private static long runner(BattleSimulation sim, String name, int x, int y) {
        return sim.spawn(new EntitySpec(name, Faction.DEFENDER, UnitType.SWARM_RUNNER, x, y)
                .role(UnitRole.SWARM_PRESSURE));
    }

    private static final class Trace implements TickObserver {
        final long tank;
        final long[] ravs;
        int ticks;
        int rounds;
        int firstShotTick = -1;
        int tankDeathTick = -1;
        int ravsKilled;

        Trace(long tank, long[] ravs) {
            this.tank = tank;
            this.ravs = ravs;
        }

        int alive(BattleSimulation sim) {
            int n = 0;
            for (long id : ravs) if (sim.getRoster().isAliveById(id)) n++;
            return n;
        }

        boolean over(BattleSimulation sim) {
            return tankDeathTick >= 0 || alive(sim) == 0;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (!(sim.convoy().isVehicle(tank) && sim.convoy().isPresent(tank))) {
                if (tankDeathTick < 0) tankDeathTick = tick;
                return;
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId != tank || shot.weaponDef() == null) continue;
                if (firstShotTick < 0) firstShotTick = tick;
                rounds++;
            }
        }

        String caption(int tick) {
            return String.format(Locale.ROOT, "ravs claw tank  t=%.1fs  rounds %d", tick / 30f, rounds);
        }
    }

    private SceneReport report(List<Trace> traces) {
        int ticks = 0;
        int rounds = 0;
        int killed = 0;
        int deaths = 0;
        float firstShot = 0f;
        for (Trace t : traces) {
            ticks += t.ticks;
            rounds += t.rounds;
            killed += t.ravsKilled;
            if (t.tankDeathTick >= 0) deaths++;
            firstShot += t.firstShotTick >= 0 ? t.firstShotTick / 30f : WINDOW_TICKS / 30f;
        }
        List<Verdict> verdicts = List.of(Verdict.of("gun-kills-ravs-while-clawed", killed > 0,
                killed + " Ravs killed by " + rounds + " rounds over " + traces.size() + " plays"));
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("plays", traces.size());
        m.put("ticks", ticks);
        m.put("ravsPerPlay", PACK + WAVE);
        m.put("ravsKilled", killed);
        m.put("rounds", rounds);
        m.put("meanFirstShotSeconds", firstShot / traces.size());
        m.put("tankDeaths", deaths);
        return new SceneReport(id(), "pack-and-wave", ticks, verdicts, m);
    }
}
