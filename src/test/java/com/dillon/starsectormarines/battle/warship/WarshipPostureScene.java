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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An Onslaught shelling a block of defenders while the threat to it comes and
 * goes, to show what a posture changes and that it changes nothing else.
 *
 * <p>{@code control} runs the same script of declared threats against a ship
 * with no doctrine: it must not care. {@code declared-threat} raises a threat
 * and clears it; the ship must turn to the air fight within a pass, spend its
 * flux on opportunity shots only, and come back to its ordinary rate after the
 * quiet dwell, changing posture exactly twice. {@code hostile-ship} does the
 * same with a hostile hull on the map that is later destroyed.
 * {@code standoff} adds a declared capital hazard over the ship's station: the
 * ship must keep clear for the fight and return afterwards. {@code withdraw}
 * knocks the hull below the limit: the ship must leave and stop firing.
 */
public final class WarshipPostureScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 80;
    private static final int TICKS = 3600;
    private static final float SHIP_X = 38f;
    private static final float SHIP_Y = 40f;
    private static final float AREA_RADIUS = 8f;
    private static final int GRID = 24;
    private static final int RAISE_TICK = 240;
    private static final int CLEAR_TICK = 600;
    private static final float THREAT = 2f;
    /** Cells of lateral spread a round may land off the aimed unit. */
    private static final float SPREAD_ALLOWANCE = 3f;
    private static final float HAZARD_X = 50f;
    private static final float HAZARD_RADIUS = 12f;
    private static final float FLUX_SCALE = 0.2f;

    @Override public String id() { return "warship-posture"; }

    @Override public String label() {
        return "Warship posture: a declared threat turns the ship to the air fight (opportunity fire, flux held back, standoff), "
                + "it recovers its rate when the threat has gone, and a ship below the hull limit leaves";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        Run control = playOne("control", frames);
        reports.add(report("control", control, control));
        for (String loop : List.of("declared-threat", "hostile-ship", "standoff", "withdraw")) {
            Run run = playOne(loop, frames);
            SceneReport report = report(loop, run, control);
            if (loop.equals("declared-threat")) report = withDeterminism(report, run, playOne(loop, FrameSink.NONE));
            reports.add(report);
        }
        return reports;
    }

    private record Run(Stats stats, Warship ship, Warship enemy) {}

    /** Adds the verdict that a second play of the same loop changes posture on the very same ticks. */
    private SceneReport withDeterminism(SceneReport report, Run first, Run second) {
        int a1 = first.stats().firstTick(WarshipPosture.AIR_SUPERIORITY);
        int a2 = second.stats().firstTick(WarshipPosture.AIR_SUPERIORITY);
        int b1 = first.stats().firstTickAfter(WarshipPosture.BOMBARD, a1);
        int b2 = second.stats().firstTickAfter(WarshipPosture.BOMBARD, a2);
        boolean same = a1 == a2 && b1 == b2 && first.stats().rounds(TICKS) == second.stats().rounds(TICKS);
        List<Verdict> verdicts = new ArrayList<>(report.verdicts());
        verdicts.add(Verdict.of("deterministic", same, "transitions at " + a1 + "/" + b1 + " then " + a2 + "/" + b2
                + "; rounds " + first.stats().rounds(TICKS) + " then " + second.stats().rounds(TICKS)));
        return new SceneReport(report.sceneId(), report.loopId(), report.ticks(), verdicts, report.metrics());
    }

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                builder.unit("d" + row + "-" + col, Faction.DEFENDER, UnitType.MARINE, 64 + col, 28 + row);
            }
        }
        // A forward knot close to the ship: the only ground a ship held to opportunity fire may shoot.
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 8; col++) {
                builder.unit("k" + row + "-" + col, Faction.DEFENDER, UnitType.MARINE, 46 + col, 38 + row);
            }
        }
        builder.unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 150, 40);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, SHIP_X, SHIP_Y, -90f);
        ship.stationTo(SHIP_X, SHIP_Y);
        ship.direct(AREA_RADIUS, true);
        ship.limitFlux();
        ship.fluxScale(FLUX_SCALE);
        if (!loop.equals("control")) ship.useDoctrine();
        sim.addWarship(ship);
        Warship enemy = null;
        if (loop.equals("hostile-ship")) {
            enemy = Warship.fit("onslaught", "onslaught_Standard", Faction.DEFENDER, 140f, 70f, 90f);
            sim.addWarship(enemy);
        }
        if (loop.equals("standoff")) {
            sim.warships().declareCapitalHazard(Faction.DEFENDER, HAZARD_X, SHIP_Y, HAZARD_RADIUS);
        }
        Stats stats = new Stats(ship);
        Warship hostile = enemy;
        TickObserver script = (s, tick) -> {
            switch (loop) {
                case "control", "declared-threat", "standoff" -> {
                    if (tick == RAISE_TICK) ship.declareAirThreat(THREAT, 1000f);
                    if (tick == CLEAR_TICK) ship.clearAirThreat();
                }
                case "hostile-ship" -> {
                    if (tick == CLEAR_TICK) hostile.takeDamage(hostile.maxHitPoints * 3f, 100f);
                }
                default -> {
                    if (tick == RAISE_TICK) ship.takeDamage(ship.maxHitPoints * 0.7f, 100f);
                }
            }
        };
        SceneRun.of(world).observe(script, stats)
                .frames(frames, loop, 6, tick -> loop + " / " + ship.posture().label() + " / flux "
                        + Math.round(ship.fluxFraction() * 100f) + "% / tick " + tick)
                .run(TICKS);
        return new Run(stats, ship, enemy);
    }

    private SceneReport report(String loop, Run run, Run control) {
        Stats s = run.stats();
        Stats c = control.stats();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "control" -> {
                verdicts.add(Verdict.of("never-changes-posture", run.ship().postureTransitions() == 0
                                && run.ship().posture() == WarshipPosture.BOMBARD,
                        run.ship().postureTransitions() + " transitions, posture " + run.ship().posture()));
                verdicts.add(Verdict.of("fires", s.rounds(TICKS) > 0, s.rounds(TICKS) + " rounds"));
            }
            case "declared-threat", "hostile-ship", "standoff" -> {
                int raised = loop.equals("hostile-ship") ? 0 : RAISE_TICK;
                int cleared = CLEAR_TICK;
                int enter = s.firstTick(WarshipPosture.AIR_SUPERIORITY);
                verdicts.add(Verdict.of("turns-to-the-air-fight-within-a-pass",
                        enter >= raised && enter <= raised + 31,
                        "air superiority at tick " + enter + ", threat at " + raised));
                int back = s.firstTickAfter(WarshipPosture.BOMBARD, enter);
                int dwellTicks = (int) (WarshipDoctrine.AIR_QUIET_SECONDS * 30f);
                verdicts.add(Verdict.of("returns-after-the-quiet-dwell",
                        back >= cleared + dwellTicks - 31 && back <= cleared + dwellTicks + 62,
                        "bombard again at tick " + back + ", threat gone at " + cleared + ", dwell " + dwellTicks));
                verdicts.add(Verdict.of("does-not-flap", run.ship().postureTransitions() == 2,
                        run.ship().postureTransitions() + " transitions"));
                verdicts.add(Verdict.of("every-posture-has-a-reason", s.reasonsAllPresent(),
                        "reasons: " + s.reasons));
                int from = Math.max(enter, 1);
                int to = Math.max(back, from + 1);
                double mean = s.meanFlux(from, to);
                double controlMean = c.meanFlux(from, to);
                verdicts.add(Verdict.of("flux-held-below-the-control", mean < controlMean,
                        String.format(Locale.ROOT, "mean flux %.2f against the control's %.2f over ticks %d-%d",
                                mean, controlMean, from, to)));
                double mine = s.fluxSpent(to) - s.fluxSpent(from);
                double theirs = c.fluxSpent(to) - c.fluxSpent(from);
                verdicts.add(Verdict.of("spends-less-flux-than-the-control", mine < theirs * 0.8,
                        String.format(Locale.ROOT, "%.0f flux of area fire against the control's %.0f over the same ticks",
                                mine, theirs)));
                verdicts.add(Verdict.of("opportunity-shots-only", s.shotsOutsideRange == 0 && (loop.equals("standoff") || s.shotsInAir > 0),
                        s.shotsInAir + " rounds in the fight, " + s.shotsOutsideRange + " beyond the opportunity range "
                                + String.format(Locale.ROOT, "%.1f", s.opportunityInFight) + (s.firstOutside == null ? "" : "; first " + s.firstOutside)));
                int after = back + 120;
                double rate = (s.rounds(TICKS) - s.rounds(after)) / ((TICKS - after) / 30.0);
                double controlRate = (c.rounds(TICKS) - c.rounds(after)) / ((TICKS - after) / 30.0);
                verdicts.add(Verdict.of("recovers-its-rate",
                        controlRate > 0 && rate >= controlRate * 0.6,
                        String.format(Locale.ROOT, "%.2f rounds/s afterwards against the control's %.2f", rate, controlRate)));
                if (loop.equals("hostile-ship")) {
                    verdicts.add(Verdict.of("names-the-hostile-ship", s.reasons.stream().anyMatch(r -> r.contains("hostile ship")),
                            "reasons: " + s.reasons));
                }
                if (loop.equals("standoff")) {
                    float gap = s.hazardGapAtAirEnd;
                    verdicts.add(Verdict.of("keeps-clear-of-the-hazard", gap >= HAZARD_RADIUS,
                            String.format(Locale.ROOT, "%.1f cells from the hazard's centre at the end of the fight, radius %.0f",
                                    gap, HAZARD_RADIUS)));
                    float home = (float) Math.hypot(run.ship().x - SHIP_X, run.ship().y - SHIP_Y);
                    verdicts.add(Verdict.of("returns-to-its-station", home < 1.5f,
                            String.format(Locale.ROOT, "%.1f cells from its original station at the end", home)));
                }
            }
            default -> {
                int withdraw = s.firstTick(WarshipPosture.WITHDRAW);
                verdicts.add(Verdict.of("withdraws-within-a-pass", withdraw >= RAISE_TICK && withdraw <= RAISE_TICK + 31,
                        "withdraw at tick " + withdraw + ", hull knocked at " + RAISE_TICK));
                verdicts.add(Verdict.of("departs", run.ship().departing() || run.ship().departed(),
                        "departing " + run.ship().departing() + ", departed " + run.ship().departed()));
                int quietFrom = withdraw + 150;
                verdicts.add(Verdict.of("stops-firing", s.rounds(TICKS) == s.rounds(quietFrom),
                        s.rounds(quietFrom) + " rounds by tick " + quietFrom + ", " + s.rounds(TICKS) + " at the end"));
                verdicts.add(Verdict.of("reason-names-the-hull", s.reasons.stream().anyMatch(r -> r.contains(": hull")),
                        "reasons: " + s.reasons));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("rounds", s.rounds(TICKS));
        metrics.put("transitions", run.ship().postureTransitions());
        metrics.put("peakFlux", s.peakFlux(0, TICKS));
        metrics.put("airSuperiorityTick", s.firstTick(WarshipPosture.AIR_SUPERIORITY));
        metrics.put("shotsInAir", s.shotsInAir);
        metrics.put("reach", WarshipDirector.reach(run.ship()));
        metrics.put("meanFluxInWindow", s.meanFlux(RAISE_TICK, CLEAR_TICK + 240));
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Stats implements TickObserver {
        private final Warship ship;
        private final int[] cumulative = new int[TICKS + 2];
        private final double[] spent = new double[TICKS + 2];
        float opportunityInFight = Float.POSITIVE_INFINITY;
        private final float[] flux = new float[TICKS + 2];
        private final WarshipPosture[] posture = new WarshipPosture[TICKS + 2];
        private final Set<WarshipShell> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<String> reasons = new ArrayList<>();
        private String lastReason = "";
        private WarshipPosture lastPosture = WarshipPosture.BOMBARD;
        int shotsInAir;
        int shotsOutsideRange;
        String firstOutside;
        float hazardGapAtAirEnd = -1f;
        private int last;

        private Stats(Warship ship) {
            this.ship = ship;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            int shots = 0;
            double fluxSpent = 0;
            for (Warship.Mount m : ship.mounts()) {
                if (m.pointDefence()) continue;
                shots += m.shotsFired;
                fluxSpent += (double) m.shotsFired * m.weapon.fluxPerShot();
            }
            int t = Math.min(tick, TICKS + 1);
            cumulative[t] = shots;
            spent[t] = fluxSpent;
            flux[t] = ship.fluxFraction();
            posture[t] = ship.posture();
            last = t;
            if (ship.posture() != lastPosture) {
                reasons.add(ship.posture().label() + ": " + ship.postureReason());
                if (lastPosture == WarshipPosture.AIR_SUPERIORITY) {
                    hazardGapAtAirEnd = (float) Math.hypot(ship.x - HAZARD_X, ship.y - SHIP_Y);
                }
                lastPosture = ship.posture();
            }
            lastReason = ship.postureReason();
            for (WarshipShell shell : sim.warships().shells()) {
                if (!seen.add(shell) || shell.faction != ship.faction) continue;
                if (ship.posture() == WarshipPosture.AIR_SUPERIORITY) {
                    shotsInAir++;
                    opportunityInFight = ship.opportunityRange();
                    float range = (float) Math.hypot(shell.toX - shell.fromX, shell.toY - shell.fromY);
                    if (range > opportunityInFight + SPREAD_ALLOWANCE) {
                        shotsOutsideRange++;
                        if (firstOutside == null) {
                            firstOutside = shell.weapon.id() + " at tick " + tick + " range " + range + " spread " + shell.weapon.spreadDegrees();
                        }
                    }
                }
            }
        }

        double fluxSpent(int tick) {
            return spent[Math.min(tick, last)];
        }

        int rounds(int tick) {
            return cumulative[Math.min(tick, last)];
        }

        double peakFlux(int from, int to) {
            float peak = 0f;
            for (int t = Math.max(0, from); t <= Math.min(to, last); t++) peak = Math.max(peak, flux[t]);
            return peak;
        }

        double meanFlux(int from, int to) {
            double sum = 0;
            int n = 0;
            for (int t = Math.max(0, from); t <= Math.min(to, last); t++) {
                sum += flux[t];
                n++;
            }
            return n == 0 ? 0 : sum / n;
        }

        int firstTick(WarshipPosture p) {
            return firstTickAfter(p, -1);
        }

        /** The first tick after {@code after} at which the ship held posture {@code p}, or -1. */
        int firstTickAfter(WarshipPosture p, int after) {
            for (int t = after + 1; t <= last; t++) if (posture[t] == p) return t;
            return -1;
        }

        boolean reasonsAllPresent() {
            return !reasons.isEmpty() && reasons.stream().noneMatch(r -> r.endsWith(": ")) && !lastReason.isBlank();
        }
    }
}
