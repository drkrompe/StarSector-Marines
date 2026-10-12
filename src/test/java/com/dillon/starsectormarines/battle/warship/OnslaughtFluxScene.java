package com.dillon.starsectormarines.battle.warship;

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
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An Onslaught shelling a thick block of defenders, three ways, to show what
 * flux does to its rhythm and nothing else.
 *
 * <p>The ship is overpowered by design and is not balanced by flux; flux decides
 * when the guns are talking and when they are not. {@code flux-off} is the
 * control: the battery never stops. {@code flux-on} is the shipped governor: the
 * guns surge until the pool nears capacity, rest while it drains, and surge
 * again, firing slower overall but not much less in total. {@code forced-overload}
 * disables the soft threshold, so the ship runs its pool dry the hard way: it
 * must overload, be silent for the whole overload, and resume.
 */
public final class OnslaughtFluxScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 80;
    private static final int TICKS = 3000;
    private static final float SHIP_X = 38f;
    private static final float SHIP_Y = 40f;
    private static final float START_HEADING = -120f;
    private static final float AREA_X = 68f;
    private static final float AREA_Y = 40f;
    private static final float AREA_RADIUS = 14f;
    private static final int GRID = 24;
    /** Consecutive ticks without a new area-fire round that make a rest rather than a gap between salvos. */
    private static final int REST_TICKS = 90;
    /**
     * The hull table's flux would never be reached by the ground cadence the guns
     * keep here (a stock Onslaught peaks under half its capacity), so the scene
     * scales the pool down to where the rhythm can be seen.
     */
    private static final float FLUX_SCALE = 0.2f;

    @Override public String id() { return "onslaught-flux"; }

    @Override public String label() {
        return "Onslaught flux: surge and rest set the rhythm, an overload silences the whole ship, damage is untouched";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        Stats control = null;
        for (String loop : List.of("flux-off", "flux-on", "forced-overload")) {
            Run run = playOne(loop, frames);
            if (loop.equals("flux-off")) control = run.stats;
            reports.add(report(loop, run, control));
        }
        return reports;
    }

    private record Run(Stats stats, Warship ship) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                builder.unit("d" + row + "-" + col, Faction.DEFENDER, UnitType.MARINE,
                        56 + col, 29 + row);
            }
        }
        builder.unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 150, 40);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE,
                SHIP_X, SHIP_Y, START_HEADING);
        ship.stationTo(SHIP_X, SHIP_Y);
        ship.order(new WarshipOrder.FireOnArea(AREA_X, AREA_Y, AREA_RADIUS, true));
        if (!loop.equals("flux-off")) {
            ship.limitFlux();
            ship.fluxScale(FLUX_SCALE);
        }
        if (loop.equals("forced-overload")) ship.fluxSoftLimit(false);
        sim.addWarship(ship);
        Stats stats = new Stats(ship);
        SceneRun.of(world).observe(stats)
                .frames(frames, loop, 6, tick -> loop + " / onslaught / " + ship.fluxState()
                        + " " + Math.round(ship.fluxFraction() * 100f) + "% / tick " + tick)
                .run(TICKS);
        return new Run(stats, ship);
    }

    private SceneReport report(String loop, Run run, Stats control) {
        Stats s = run.stats();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "flux-off" -> {
                verdicts.add(Verdict.of("fires-continuously", s.shots() > 0 && s.restGaps() == 0,
                        s.shots() + " rounds, " + s.restGaps() + " rests of " + REST_TICKS + "+ ticks"));
                verdicts.add(Verdict.of("never-governed", run.ship().overloadCount() == 0 && s.maxFraction == 0f,
                        "flux fraction peaked at " + s.maxFraction));
            }
            case "flux-on" -> {
                double rate = s.rate();
                double controlRate = control.rate();
                double damage = s.damage();
                double controlDamage = control.damage();
                verdicts.add(Verdict.of("rests-between-surges", s.restGaps() >= 2,
                        s.restGaps() + " rests of " + REST_TICKS + "+ ticks between salvos"));
                verdicts.add(Verdict.of("slower-than-ungoverned-but-not-silent",
                        rate > 0 && rate < controlRate,
                        String.format(Locale.ROOT, "%.1f rounds/s against the control's %.1f", rate, controlRate)));
                verdicts.add(Verdict.of("same-order-of-damage",
                        controlDamage > 0 && damage >= controlDamage * 0.1 && damage <= controlDamage * 1.05,
                        String.format(Locale.ROOT, "%.0f rated damage fired against the control's %.0f",
                                damage, controlDamage)));
                verdicts.add(Verdict.of("soft-limit-keeps-it-off-overload",
                        run.ship().overloadCount() == 0 && s.maxFraction < 1f,
                        run.ship().overloadCount() + " overloads, peak " + s.maxFraction));
            }
            default -> {
                verdicts.add(Verdict.of("overloads", run.ship().overloadCount() >= 1 && s.overloadStartTick > 0,
                        run.ship().overloadCount() + " overloads, the first at tick " + s.overloadStartTick));
                verdicts.add(Verdict.of("silent-for-the-whole-overload",
                        s.overloadEndTick > s.overloadStartTick && s.shotsInFirstOverload == 0,
                        s.shotsInFirstOverload + " rounds fired between tick " + s.overloadStartTick
                                + " and " + s.overloadEndTick));
                verdicts.add(Verdict.of("overload-lasts-its-length",
                        s.overloadEndTick - s.overloadStartTick >= WarshipFlux.OVERLOAD_MIN_SECONDS * 30f - 2f,
                        "silent for " + (s.overloadEndTick - s.overloadStartTick) + " ticks, at least "
                                + Math.round(WarshipFlux.OVERLOAD_MIN_SECONDS * 30f)));
                verdicts.add(Verdict.of("resumes-afterwards", s.shotsAfterFirstOverload > 0,
                        s.shotsAfterFirstOverload + " rounds fired after the first overload ended"));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("rounds", s.shots());
        metrics.put("roundsPerSecond", s.rate());
        metrics.put("restGaps", s.restGaps());
        metrics.put("peakFlux", s.maxFraction);
        metrics.put("overloads", run.ship().overloadCount());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Stats implements TickObserver {
        private final Warship ship;
        /** Cumulative area-fire rounds after each tick. */
        private final int[] cumulative = new int[TICKS + 2];
        private final double[] cumulativeDamage = new double[TICKS + 2];
        private int last;
        float maxFraction;
        int overloadStartTick = -1;
        int overloadEndTick = -1;
        int shotsInFirstOverload;
        int shotsAfterFirstOverload;
        private int shotsAtOverloadStart;
        private boolean wasOverloaded;
        private boolean firstOverloadDone;

        private Stats(Warship ship) {
            this.ship = ship;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            int shots = 0;
            double damage = 0;
            for (Warship.Mount m : ship.mounts()) {
                if (m.pointDefence()) continue;
                shots += m.shotsFired;
                damage += (double) m.shotsFired * m.weapon.damagePerShot();
            }
            int t = Math.min(tick, TICKS + 1);
            cumulative[t] = shots;
            cumulativeDamage[t] = damage;
            last = t;
            maxFraction = Math.max(maxFraction, ship.fluxFraction());
            boolean over = ship.overloaded();
            if (over && !wasOverloaded && !firstOverloadDone) {
                overloadStartTick = tick;
                shotsAtOverloadStart = shots;
            }
            if (!over && wasOverloaded && !firstOverloadDone) {
                overloadEndTick = tick;
                shotsInFirstOverload = shotsBefore(tick) - shotsAtOverloadStart;
                firstOverloadDone = true;
            }
            if (firstOverloadDone) shotsAfterFirstOverload = shots - shotsBefore(overloadEndTick);
            wasOverloaded = over;
        }

        private int shotsBefore(int tick) {
            return cumulative[Math.max(0, tick - 1)];
        }

        int shots() {
            return cumulative[last];
        }

        double damage() {
            return cumulativeDamage[last];
        }

        double rate() {
            return shots() / (last / 30.0);
        }

        /** Runs of {@link #REST_TICKS} or more ticks with no new round, between the first round and the last. */
        int restGaps() {
            int first = -1;
            int lastShot = -1;
            for (int t = 1; t <= last; t++) {
                if (cumulative[t] > cumulative[t - 1]) {
                    if (first < 0) first = t;
                    lastShot = t;
                }
            }
            if (first < 0) return 0;
            int gaps = 0;
            int quiet = 0;
            for (int t = first + 1; t <= lastShot; t++) {
                if (cumulative[t] > cumulative[t - 1]) {
                    quiet = 0;
                } else if (++quiet == REST_TICKS) {
                    gaps++;
                }
            }
            return gaps;
        }
    }
}
