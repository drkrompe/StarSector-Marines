package com.dillon.starsectormarines.battle.shield;

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
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An Onslaught shelling a knot of defenders that stand under a planetary shield
 * dome, and the same shelling with no dome, and with the dome's generator
 * destroyed part-way. It asks the question the dome exists to answer: does it
 * stop ship fire until it overloads, and does the gap it leaves let the ground
 * be hit?
 *
 * <p>{@code control} is the same world with no generator: the ship's first rounds
 * kill. {@code dome} has the generator: the rounds land as flux, nobody dies, the
 * pool fills, the dome drops and the next rounds land, and it comes back.
 * {@code destroyed} has the generator wrecked after the dome has been taking
 * fire: the dome goes with it and the rounds land from then on.
 */
public final class ShieldDomeScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private static final int TICKS = 1500;
    private static final float SHIP_X = 38f;
    private static final float SHIP_Y = 50f;
    private static final int GEN_X = 90;
    private static final int GEN_Y = 40;
    /** The knot of defenders stands under the dome, off the generator's own footprint. */
    private static final int KNOT_X = 90;
    private static final int KNOT_Y = 52;
    private static final int KNOT = 30;
    private static final int DESTROY_TICK = 240;

    @Override public String id() { return "shield-dome"; }

    @Override public String label() {
        return "Planetary shield dome: ship ordnance is taken as flux until the dome overloads, lands in the gap, "
                + "and stops again when it recovers; with no dome, or the generator destroyed, it lands";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        Run control = playOne("control", frames);
        Run dome = playOne("dome", frames);
        Run destroyed = playOne("destroyed", frames);
        List<SceneReport> reports = new ArrayList<>();
        reports.add(report("control", control, control));
        reports.add(report("dome", dome, control));
        reports.add(report("destroyed", destroyed, control));
        return reports;
    }

    private record Run(Trace trace) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        for (int i = 0; i < KNOT; i++) {
            builder.unit("k" + i, Faction.DEFENDER, UnitType.MARINE, KNOT_X + (i % 6) - 3, KNOT_Y + (i / 6) - 2);
        }
        // The company's own, standing off the dome so the player's fog reveals it and both sides stay on the map.
        builder.unit("spotter", Faction.MARINE, UnitType.MARINE, 68, 44);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261012L);
        long generator = loop.equals("control") ? 0L
                : sim.spawn(ShieldGenerator.create("generator", Faction.DEFENDER, GEN_X, GEN_Y));
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, SHIP_X, SHIP_Y, 0f);
        ship.stationTo(SHIP_X, SHIP_Y);
        ship.direct(8f, true, 1);
        sim.addWarship(ship);
        Trace trace = new Trace(world, generator);
        TickObserver script = (s, tick) -> {
            if (loop.equals("destroyed") && tick == DESTROY_TICK) {
                s.applyDamage(trace.generator, 1_000_000f, 3.5f, 0f);
            }
        };
        SceneRun.of(world).observe(script, trace)
                .frames(frames, loop, 12, tick -> loop + " / tick " + tick + trace.captionTail(sim))
                .run(TICKS);
        return new Run(trace);
    }

    private SceneReport report(String loop, Run run, Run control) {
        Trace t = run.trace();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "control" -> {
                verdicts.add(Verdict.of("ship-fire-lands", t.firstDeath >= 0,
                        "first defender died at tick " + t.firstDeath));
                verdicts.add(Verdict.of("clears-the-knot", t.deaths >= KNOT * 2 / 3,
                        t.deaths + " of " + KNOT + " died"));
            }
            case "dome" -> {
                verdicts.add(Verdict.of("dome-takes-the-first-rounds", t.firstHit >= 0,
                        "first round absorbed at tick " + t.firstHit));
                verdicts.add(Verdict.of("nobody-dies-before-the-overload",
                        t.firstOverload >= 0 && (t.firstDeath < 0 || t.firstDeath >= t.firstOverload),
                        "first overload tick " + t.firstOverload + ", first death tick " + t.firstDeath));
                verdicts.add(Verdict.of("dome-outlasts-the-undomed-knot",
                        control.trace().firstDeath >= 0 && t.firstDeath > control.trace().firstDeath,
                        "first death " + t.firstDeath + " under the dome, " + control.trace().firstDeath + " without"));
                verdicts.add(Verdict.of("overload-opens-a-gap", t.firstOverload >= 0 && t.deaths > 0,
                        t.deaths + " died, " + t.deathsInGap + " of them while the dome was down"));
                verdicts.add(Verdict.of("rounds-land-only-in-the-gap", t.deaths == t.deathsInGap,
                        t.deaths + " died, " + t.deathsInGap + " while down"));
                verdicts.add(Verdict.of("dome-recovers", t.firstRecovery > t.firstOverload && t.firstOverload >= 0,
                        "recovered at tick " + t.firstRecovery));
                verdicts.add(Verdict.of("recovered-dome-takes-rounds-again", t.hitsAfterRecovery > 0,
                        t.hitsAfterRecovery + " rounds absorbed after the first recovery"));
            }
            default -> {
                verdicts.add(Verdict.of("dome-took-fire-first", t.hitsBeforeDestroy > 0,
                        t.hitsBeforeDestroy + " rounds absorbed before the generator fell"));
                verdicts.add(Verdict.of("generator-destroyed", t.generatorGoneTick >= 0,
                        "generator gone at tick " + t.generatorGoneTick));
                verdicts.add(Verdict.of("dome-goes-with-it", t.domeGoneTick >= 0 && t.domeGoneTick <= t.generatorGoneTick + 2,
                        "dome gone at tick " + t.domeGoneTick));
                verdicts.add(Verdict.of("rounds-land-once-it-is-gone", t.deaths > 0 && t.firstDeath >= t.generatorGoneTick,
                        t.deaths + " died, the first at tick " + t.firstDeath));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("deaths", t.deaths);
        metrics.put("firstDeath", t.firstDeath);
        metrics.put("firstHit", t.firstHit);
        metrics.put("firstOverload", t.firstOverload);
        metrics.put("firstRecovery", t.firstRecovery);
        metrics.put("hits", t.hits);
        metrics.put("overloads", t.overloads);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        long generator;
        int deaths;
        int deathsInGap;
        int firstDeath = -1;
        int firstHit = -1;
        int firstOverload = -1;
        int firstRecovery = -1;
        int generatorGoneTick = -1;
        int domeGoneTick = -1;
        int hits;
        int overloads;
        int hitsAfterRecovery;
        int hitsBeforeDestroy;
        private final boolean[] dead = new boolean[KNOT];
        private boolean wasDown;
        private int lastHits;
        private boolean sawDome;

        Trace(SceneWorld world, long generator) {
            this.world = world;
            this.generator = generator;
        }

        String captionTail(BattleSimulation sim) {
            if (sim.shieldDomes().domes().isEmpty()) return "";
            ShieldDomeService.DomeView d = sim.shieldDomes().domes().get(0);
            return String.format(Locale.ROOT, " / dome %s %.0f%%", d.overloaded() ? "DOWN" : "up", d.level() * 100f);
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            List<ShieldDomeService.DomeView> domes = sim.shieldDomes().domes();
            boolean down = false;
            if (!domes.isEmpty()) {
                sawDome = true;
                ShieldDomeService.DomeView d = domes.get(0);
                down = d.overloaded();
                hits = d.hits();
                overloads = d.overloads();
                if (firstHit < 0 && d.hits() > 0) firstHit = tick;
                if (down && firstOverload < 0) firstOverload = tick;
                if (wasDown && !down && firstRecovery < 0) firstRecovery = tick;
                if (firstRecovery >= 0) hitsAfterRecovery += Math.max(0, d.hits() - lastHits);
                if (generatorGoneTick < 0) hitsBeforeDestroy = d.hits();
                lastHits = d.hits();
            } else if (sawDome && domeGoneTick < 0) {
                domeGoneTick = tick;
            }
            wasDown = down;
            if (generator != 0L && generatorGoneTick < 0 && !sim.getRoster().isAliveById(generator)) {
                generatorGoneTick = tick;
            }
            for (int i = 0; i < KNOT; i++) {
                if (dead[i] || sim.getRoster().isAliveById(world.unit("k" + i))) continue;
                dead[i] = true;
                deaths++;
                if (firstDeath < 0) firstDeath = tick;
                if (down) deathsInGap++;
            }
        }
    }
}
