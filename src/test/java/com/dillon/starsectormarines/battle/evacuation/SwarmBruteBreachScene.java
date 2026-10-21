package com.dillon.starsectormarines.battle.evacuation;

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
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Brute against a held colony building.
 *
 * <p>Three marines hold a walled building with two windows on the west wall,
 * the side the Brute comes from. The marines are squadless and so hold their
 * fire, and the Brute is given a large health pool: this scene is about what
 * the Brute does to the building, not about who wins.
 * <ul>
 *   <li>{@code far-door}: the only door is on the far side. The Brute breaks in
 *       through the near wall or a window, then goes through the hole.</li>
 *   <li>{@code near-door}: a door on the near side. The Brute uses it and
 *       breaks nothing.</li>
 *   <li>{@code fortified}: the same building at fortified wall strength, door on
 *       the far side. Nothing is light enough to break; the Brute walks round.</li>
 *   <li>{@code emplacement}: the far-door building plus an anti-air
 *       emplacement in the open. The Brute goes for the emplacement first.</li>
 * </ul>
 */
public final class SwarmBruteBreachScene implements BehaviorScene {

    private static final int TICKS = 1500;
    private static final int COLONY_WALL_HP = 100;
    private static final int FORTIFIED_WALL_HP = 240;

    @Override public String id() { return "swarm-brute-breach"; }

    @Override public String label() {
        return "Swarm Brute: breaks into a held building where no door is near, spares heavy walls,"
                + " goes for emplacements";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("far-door", "near-door", "fortified", "emplacement")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean fortified = loop.equals("fortified");
        boolean emplacement = loop.equals("emplacement");
        SwarmStrainGround ground = new SwarmStrainGround(48, 28, 22, 10, 29, 17,
                fortified ? FORTIFIED_WALL_HP : COLONY_WALL_HP)
                .window(21, 12).window(21, 15);
        if (loop.equals("near-door")) ground.door(21, 13);
        else ground.door(30, 14);

        SceneWorld world = SceneBuilder.over(ground.grid, ground.topology).seed(20261021L)
                .missionCompletion(false)
                .unit("m1", Faction.MARINE, UnitType.MARINE, 23, 12, spec -> spec.health(5000f))
                .unit("m2", Faction.MARINE, UnitType.MARINE, 23, 15, spec -> spec.health(5000f))
                .unit("m3", Faction.MARINE, UnitType.MARINE, 26, 14, spec -> spec.health(5000f))
                .build();
        try (BattleSimulation sim = world.sim()) {
            long turret = emplacement
                    ? sim.spawn(MapTurret.create("aa", Faction.MARINE, "structure.aa-locust", 12, 22))
                    : 0L;
            long brute = sim.spawn(SwarmStrains.spec("Brute", UnitType.SWARM_BRUTE, 6, 13)
                    .health(5000f));
            long[] marines = {world.unit("m1"), world.unit("m2"), world.unit("m3")};
            Trace trace = new Trace(brute, turret, marines, ground, sim);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 30, tick -> loop + " / brute / tick " + tick)
                    .run(TICKS);
            return report(loop, trace, sim, ground, turret);
        }
    }

    private SceneReport report(String loop, Trace trace, BattleSimulation sim,
                               SwarmStrainGround ground, long turret) {
        SwarmStrikeSystem strikes = sim.swarmStrikes();
        int breaches = 0;
        for (int y = ground.minY - 1; y <= ground.maxY + 1; y++) {
            for (int x = ground.minX - 1; x <= ground.maxX + 1; x++) {
                if (ground.onRing(x, y) && trace.wasClosed(x, y)
                        && ground.grid.isWalkable(x, y)) breaches++;
            }
        }
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "far-door" -> {
                verdicts.add(Verdict.of("strikes-the-building", strikes.blowsLanded() > 0,
                        strikes.blowsLanded() + " blows on walls and windows"));
                verdicts.add(Verdict.of("breaks-in", breaches > 0,
                        breaches + " wall or window cells of the building opened"));
                verdicts.add(Verdict.of("goes-through-the-hole", breaches == 0
                                || trace.firstInsideTick >= 0,
                        breaches == 0 ? "no breach to go through"
                                : "inside from tick " + trace.firstInsideTick));
                verdicts.add(Verdict.of("strikes-the-occupants", trace.firstInsideTick < 0
                                || trace.marineDamage() > 0f,
                        trace.firstInsideTick < 0 ? "never inside"
                                : "occupants took " + trace.marineDamage()));
            }
            case "near-door" -> {
                verdicts.add(Verdict.of("uses-the-near-door", strikes.blowsLanded() == 0,
                        strikes.blowsLanded() + " blows; a door was in reach"));
                verdicts.add(Verdict.of("gets-in", trace.firstInsideTick >= 0,
                        "inside from tick " + trace.firstInsideTick));
            }
            case "fortified" -> {
                verdicts.add(Verdict.of("heavy-walls-untouched", strikes.blowsLanded() == 0
                                && trace.ringHpUnchanged(),
                        strikes.blowsLanded() + " blows; ring hp unchanged " + trace.ringHpUnchanged()));
            }
            case "emplacement" -> {
                verdicts.add(Verdict.of("emplacement-first", trace.firstTarget == turret,
                        "first target " + trace.firstTarget + ", emplacement " + turret));
                verdicts.add(Verdict.of("strikes-the-emplacement", !trace.reachedTurret
                                || trace.turretDamage > 0f,
                        trace.reachedTurret ? "emplacement took " + trace.turretDamage
                                : "never reached it"));
            }
            default -> throw new IllegalStateException(loop);
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("blows", strikes.blowsLanded());
        metrics.put("structuresBroken", strikes.structuresBroken());
        metrics.put("breachedRingCells", breaches);
        metrics.put("firstInsideTick", trace.firstInsideTick);
        metrics.put("marineDamage", trace.marineDamage());
        metrics.put("turretDamage", trace.turretDamage);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final long brute;
        private final long turret;
        private final long[] marines;
        private final float[] initialHp;
        private final SwarmStrainGround ground;
        private final boolean[] closedAtStart;
        private final int[] ringHp;
        private final float turretHp;
        private long firstTarget = -1L;
        private int firstInsideTick = -1;
        private boolean reachedTurret;
        private float turretDamage;
        private float[] lastHp;

        Trace(long brute, long turret, long[] marines, SwarmStrainGround ground,
              BattleSimulation sim) {
            this.brute = brute;
            this.turret = turret;
            this.marines = marines;
            this.ground = ground;
            initialHp = new float[marines.length];
            for (int i = 0; i < marines.length; i++) initialHp[i] = sim.world().hp(marines[i]);
            lastHp = initialHp.clone();
            int w = ground.grid.getWidth();
            closedAtStart = new boolean[w * ground.grid.getHeight()];
            ringHp = new int[closedAtStart.length];
            for (int y = 0; y < ground.grid.getHeight(); y++) {
                for (int x = 0; x < w; x++) {
                    closedAtStart[y * w + x] = !ground.grid.isWalkable(x, y);
                    ringHp[y * w + x] = ground.grid.getWallHp(x, y);
                }
            }
            turretHp = turret == 0L ? 0f : sim.world().hp(turret) + sim.world().armor(turret);
        }

        boolean wasClosed(int x, int y) {
            return closedAtStart[y * ground.grid.getWidth() + x];
        }

        boolean ringHpUnchanged() {
            int w = ground.grid.getWidth();
            for (int y = 0; y < ground.grid.getHeight(); y++) {
                for (int x = 0; x < w; x++) {
                    if (ground.onRing(x, y) && ground.grid.getWallHp(x, y) != ringHp[y * w + x]) {
                        return false;
                    }
                }
            }
            return true;
        }

        float marineDamage() {
            float total = 0f;
            for (int i = 0; i < marines.length; i++) total += initialHp[i] - lastHp[i];
            return total;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (!sim.world().isAlive(brute)) return;
            for (int i = 0; i < marines.length; i++) {
                if (sim.world().isAlive(marines[i])) lastHp[i] = sim.world().hp(marines[i]);
            }
            long target = sim.combat().targetId(brute);
            if (firstTarget < 0 && target != 0L) firstTarget = target;
            if (firstInsideTick < 0
                    && ground.inside(sim.world().cellX(brute), sim.world().cellY(brute))) {
                firstInsideTick = tick;
            }
            if (turret != 0L) {
                float dx = sim.world().x(turret) - sim.world().x(brute);
                float dy = sim.world().y(turret) - sim.world().y(brute);
                if (Math.hypot(dx, dy) <= sim.combat().attackRange(brute)
                        + sim.getRoster().radius(turret)) {
                    reachedTurret = true;
                }
                float now = sim.world().isAlive(turret)
                        ? sim.world().hp(turret) + sim.world().armor(turret) : 0f;
                turretDamage = turretHp - now;
            }
        }
    }
}
