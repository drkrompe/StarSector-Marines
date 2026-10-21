package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.component.BattleComponents;
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
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A mixed swarm (Runners, Brutes, Spitters) against an armed squad holding
 * open ground, on a map exactly one 1920-pixel screen wide at the game's
 * normal zoom, so its frames are the owner's battle-zoom art review of both
 * new bodies: walking, striking, lobbing, dying and lying where they fell.
 *
 * <p>Verdicts are mechanics: every strain moved off its spawn, the Spitters
 * lobbed, and every strain body that died left a corpse with a pose. Who won
 * and how fast is in the metrics.
 */
public final class SwarmStrainSkirmishScene implements BehaviorScene {

    /** 1920 px at the normal 17.14 px cell: one screen of map. */
    static final int WIDTH = 112;
    static final int HEIGHT = 63;
    private static final int TICKS = 1350;

    @Override public String id() { return "swarm-strains-skirmish"; }

    @Override public String label() {
        return "Swarm strains: Runners, Brutes and Spitters against a held squad at battle zoom";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        SceneWorld world = SceneBuilder.openGround(WIDTH, HEIGHT).seed(20261021L)
                .missionCompletion(false)
                .squad("marines").faction(Faction.MARINE).size(6).at(78, 31).stationary().done()
                .build();
        try (BattleSimulation sim = world.sim()) {
            List<long[]> spawned = new ArrayList<>();
            int n = 0;
            for (int i = 0; i < 8; i++) {
                spawned.add(new long[]{SwarmStrains.spawn(sim, "Runner " + i, UnitType.SWARM_RUNNER,
                        14 + (i % 4) * 3, 22 + (i / 4) * 16 + (i % 2) * 3), n++});
            }
            for (int i = 0; i < 2; i++) {
                spawned.add(new long[]{SwarmStrains.spawn(sim, "Brute " + i, UnitType.SWARM_BRUTE,
                        20, 27 + i * 9), n++});
            }
            for (int i = 0; i < 3; i++) {
                spawned.add(new long[]{SwarmStrains.spawn(sim, "Spitter " + i,
                        UnitType.SWARM_SPITTER, 8, 22 + i * 9), n++});
            }
            long[] bodies = spawned.stream().mapToLong(a -> a[0]).toArray();
            Trace trace = new Trace(bodies, sim);
            SceneRun.of(world).observe(trace)
                    .frames(frames, "main", 6, tick -> "swarm strains / tick " + tick)
                    .run(TICKS);

            Map<UnitType, Integer> corpses = corpsesWithPose(sim);
            List<Verdict> verdicts = new ArrayList<>();
            for (UnitType type : new UnitType[]{UnitType.SWARM_BRUTE, UnitType.SWARM_SPITTER}) {
                verdicts.add(Verdict.of(type + "-moves", trace.moved.getOrDefault(type, 0) > 0,
                        trace.moved.getOrDefault(type, 0) + " of them left their spawn cell"));
                int dead = trace.dead.getOrDefault(type, 0);
                int lying = corpses.getOrDefault(type, 0);
                verdicts.add(Verdict.of(type + "-leaves-corpses", lying == dead,
                        dead + " died, " + lying + " corpses with a pose"));
            }
            verdicts.add(Verdict.of("spitters-lob", trace.spitterLobs > 0,
                    trace.spitterLobs + " lobs"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            for (UnitType type : trace.dead.keySet()) {
                metrics.put(type + "Dead", trace.dead.get(type));
            }
            metrics.put("spitterLobs", trace.spitterLobs);
            metrics.put("marinesAlive", alive(world.members("marines"), sim));
            return List.of(new SceneReport(id(), "main", TICKS, verdicts, metrics));
        }
    }

    private static int alive(long[] ids, BattleSimulation sim) {
        int count = 0;
        for (long id : ids) if (sim.world().isAlive(id)) count++;
        return count;
    }

    private static Map<UnitType, Integer> corpsesWithPose(BattleSimulation sim) {
        BattleComponents c = sim.getBattleComponents();
        Map<UnitType, Integer> counts = new EnumMap<>(UnitType.class);
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.corpses)) {
            Object[] types = t.objects(c.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            int[] pose = t.ints(c.SPRITE, BattleComponents.SPRITE_INDEX).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                if (pose[r] >= 0) counts.merge((UnitType) types[r], 1, Integer::sum);
            }
        }
        return counts;
    }

    private static final class Trace implements TickObserver {
        private final long[] bodies;
        private final int[] spawnCells;
        private final boolean[] counted;
        private final boolean[] movedOnce;
        final Map<UnitType, Integer> moved = new EnumMap<>(UnitType.class);
        final Map<UnitType, Integer> dead = new EnumMap<>(UnitType.class);
        private final UnitType[] types;
        int spitterLobs;

        Trace(long[] bodies, BattleSimulation sim) {
            this.bodies = bodies;
            spawnCells = new int[bodies.length];
            counted = new boolean[bodies.length];
            movedOnce = new boolean[bodies.length];
            types = new UnitType[bodies.length];
            for (int i = 0; i < bodies.length; i++) {
                spawnCells[i] = sim.world().cellY(bodies[i]) * WIDTH + sim.world().cellX(bodies[i]);
                types[i] = sim.identity().type(bodies[i]);
            }
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (int i = 0; i < bodies.length; i++) {
                if (!sim.world().isAlive(bodies[i])) {
                    if (!counted[i]) {
                        counted[i] = true;
                        dead.merge(types[i], 1, Integer::sum);
                    }
                    continue;
                }
                int cell = sim.world().cellY(bodies[i]) * WIDTH + sim.world().cellX(bodies[i]);
                if (!movedOnce[i] && cell != spawnCells[i]) {
                    movedOnce[i] = true;
                    moved.merge(types[i], 1, Integer::sum);
                }
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                for (int i = 0; i < bodies.length; i++) {
                    if (shot.shooterId == bodies[i] && types[i] == UnitType.SWARM_SPITTER) {
                        spitterLobs++;
                    }
                }
            }
        }
    }
}
