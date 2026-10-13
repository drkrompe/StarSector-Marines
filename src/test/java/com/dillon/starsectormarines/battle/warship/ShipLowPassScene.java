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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An Onslaught on its director over the west of a map, and a line of defenders running east
 * along its axis beyond the director's reach: does a low pass put the ship's guns on the
 * whole line, charge the station time it says it does, and send the ship home afterwards?
 *
 * <p>Four loops. {@code pass} orders the pass from the ship's own berth down the line;
 * {@code control} is the same ship, director and ground with no pass, which is what the
 * director alone reaches (nothing on the line). {@code friendlies} is the pass again with marines
 * standing among the defenders (a show of force does not refuse friendly ground, and the rounds
 * hurt who they land on), and {@code friendlies-control} the same ground with no pass.
 *
 * <p>The units are scenery on purpose (no loadouts), so every casualty here is the ship's.
 * The line starts beyond the longest gun's reach from the berth, so the director cannot be
 * credited with any of it. Once the pass is over the director resumes and may take whatever is
 * then within its reach, so the corridor is judged at the instant the pass ends. Frames stop
 * once the run is over; the ship's slow hover back to its station is played out unrecorded so
 * the return can be judged.
 */
public final class ShipLowPassScene implements BehaviorScene {

    static final int WIDTH = 230;
    static final int HEIGHT = 100;
    private static final int TICKS_PASS = 4900;
    private static final int TICKS_CONTROL = 1800;
    /** Recorded ticks: long enough to show the run and the coast to a stop. */
    private static final int FRAME_TICKS = 900;
    /** A loop with a pass in it is recorded this often; one without shows a ship standing still and is recorded sparsely. */
    private static final int FRAME_EVERY_PASS = 10;
    private static final int FRAME_EVERY_CONTROL = 60;
    private static final int ORDER_AT = 20;
    private static final float STAY_SECONDS = 90f;
    private static final float STATION_X = 25f;
    private static final float STATION_Y = 50f;
    private static final float PASS_END_X = 165f;
    /** The line starts this far beyond the longest reach plus the director's area, in cells. */
    private static final float BEYOND_REACH_CELLS = 6f;
    private static final float LINE_END_X = 157f;
    private static final int LINE_COUNT = 12;
    private static final int[] LINE_JITTER = {-3, 3, -1, 2, -3, 3, -1, 2, -2, 3, -1, 1};
    /** Which defenders have a friendly standing beside them in the friendly loops. */
    private static final int[] FRIEND_AT = {1, 4, 7, 10};
    /** Defenders this far abeam the line's end: outside the corridor, inside the ship's reach once the run is over. */
    private static final int BYSTANDER_OFFSET = 36;

    /** Where everything stands, from the hull's reach. */
    private record Layout(int[][] line, int[][] bystanders, int[][] friendlies, float reach) {
        static Layout of(Warship ship) {
            float reach = 0f;
            for (Warship.Mount m : ship.mounts()) {
                if (m.pointDefence() || m.weapon.beam()) continue;
                reach = Math.max(reach, WarshipScale.cells(m.weapon.rangePx()));
            }
            float first = (float) Math.ceil(STATION_X + reach + WarshipPilot.AREA_RADIUS_CELLS + BEYOND_REACH_CELLS);
            int[][] line = new int[LINE_COUNT][];
            for (int i = 0; i < LINE_COUNT; i++) {
                float x = first + i * Math.max(2f, (LINE_END_X - first) / (LINE_COUNT - 1));
                line[i] = new int[]{Math.round(x), (int) STATION_Y + LINE_JITTER[i]};
            }
            int[][] bystanders = {
                    {line[8][0], (int) STATION_Y + BYSTANDER_OFFSET},
                    {line[10][0], (int) STATION_Y - BYSTANDER_OFFSET}};
            int[][] friendlies = new int[FRIEND_AT.length][];
            for (int i = 0; i < friendlies.length; i++) {
                friendlies[i] = new int[]{line[FRIEND_AT[i]][0] + 2, line[FRIEND_AT[i]][1]};
            }
            return new Layout(line, bystanders, friendlies, reach);
        }
    }

    @Override public String id() { return "ship-low-pass"; }

    @Override public String label() {
        return "Ship low pass: a capital flies a fast, low run down a line of defenders, guns on the corridor, and goes home";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        reports.add(playOne("pass", true, false, frames));
        reports.add(playOne("control", false, false, frames));
        reports.add(playOne("friendlies", true, true, frames));
        reports.add(playOne("friendlies-control", false, true, frames));
        return reports;
    }

    private SceneReport playOne(String loop, boolean pass, boolean friendlies, FrameSink frames) throws Exception {
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, STATION_X, STATION_Y, -90f);
        Layout layout = Layout.of(ship);
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        for (int i = 0; i < layout.line().length; i++) {
            builder.unit("d" + i, Faction.DEFENDER, UnitType.MARINE, layout.line()[i][0], layout.line()[i][1]);
        }
        for (int i = 0; i < layout.bystanders().length; i++) {
            builder.unit("b" + i, Faction.DEFENDER, UnitType.MARINE, layout.bystanders()[i][0], layout.bystanders()[i][1]);
        }
        // One distant defender nobody reaches, so the simulation never decides the battle.
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE, 226, 96);
        if (friendlies) {
            for (int i = 0; i < layout.friendlies().length; i++) {
                builder.unit("f" + i, Faction.MARINE, UnitType.MARINE, layout.friendlies()[i][0], layout.friendlies()[i][1]);
            }
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261014L);

        ship.stationTo(STATION_X, STATION_Y);
        ship.direct(WarshipPilot.AREA_RADIUS_CELLS, true);
        ship.stayFor(STAY_SECONDS);
        sim.addWarship(ship);

        Trace trace = new Trace(world, ship, layout, pass, friendlies);
        FrameSink window = new FrameSink() {
            @Override public void frame(String loopId, BattleSimulation s, int tick, String caption) {
                if (tick <= FRAME_TICKS) frames.frame(loopId, s, tick, caption);
            }
            @Override public void advanced(String loopId, BattleSimulation s, int tick) {
                if (tick <= FRAME_TICKS) frames.advanced(loopId, s, tick);
            }
        };
        int ticks = pass ? TICKS_PASS : TICKS_CONTROL;
        SceneRun.of(world).observe(trace)
                .frames(window, loop, pass ? FRAME_EVERY_PASS : FRAME_EVERY_CONTROL,
                        tick -> caption(loop, ship, trace, tick))
                .run(ticks);
        return report(loop, ticks, ship, trace, layout, pass, friendlies);
    }

    private static String caption(String loop, Warship ship, Trace t, int tick) {
        LowPass pass = ship.lowPass();
        String phase = pass == null ? "no pass" : pass.phase().name().toLowerCase(Locale.ROOT).replace('_', '-');
        return String.format(Locale.ROOT,
                "%s / tick %d / %s / speed %.1f cells/s / station left %.0f s / on the line down %d / friendlies down %d",
                loop, tick, phase, ship.speed(), ship.stationSecondsRemaining(), t.lineDown(), t.friendliesDown());
    }

    private SceneReport report(String loop, int ticks, Warship ship, Trace t, Layout layout,
                               boolean pass, boolean friendlies) {
        List<Verdict> verdicts = new ArrayList<>();
        float passSpeed = LowPass.passSpeed(ship.maxSpeed);
        if (pass) {
            verdicts.add(Verdict.of("pass-ran-the-line", t.completed && t.progressAtEnd >= t.length - 1.5f,
                    "pass finished at tick " + t.endedTick + ", " + fmt(t.progressAtEnd) + " of "
                            + fmt(t.length) + " cells flown in " + fmt(t.flown) + " s"));
            verdicts.add(Verdict.of("flies-fast", t.peakSpeed >= 0.9f * passSpeed && t.peakSpeed > 2f * ship.maxSpeed,
                    "peak " + fmt(t.peakSpeed) + " cells/s against a pass speed of " + fmt(passSpeed)
                            + " and a hover of " + fmt(ship.maxSpeed)));
            verdicts.add(Verdict.of("guns-on-the-corridor", t.lineDownAtEnd >= 10,
                    t.lineDownAtEnd + " of " + LINE_COUNT + " defenders on the line were down when the pass ended"));
            verdicts.add(Verdict.of("line-was-beyond-the-directors-reach",
                    layout.line()[0][0] > STATION_X + layout.reach() + WarshipPilot.AREA_RADIUS_CELLS,
                    "the line starts at x=" + layout.line()[0][0] + "; the longest gun reaches "
                            + fmt(layout.reach()) + " cells from x=" + fmt(STATION_X)));
            verdicts.add(Verdict.of("corridor-only", t.bystandersDownAtEnd == 0,
                    t.bystandersDownAtEnd + " of " + layout.bystanders().length
                            + " defenders abeam, outside the corridor, were down when the pass ended"));
            verdicts.add(Verdict.of("station-time-is-charged-double",
                    t.completed && Math.abs((t.stationAtOrder - t.stationAtEnd) - LowPass.STATION_COST_FACTOR * t.flown) < 0.25f,
                    "station fell " + fmt(t.stationAtOrder - t.stationAtEnd) + " s over " + fmt(t.flown)
                            + " s flown (cost factor " + fmt(LowPass.STATION_COST_FACTOR) + ")"));
            verdicts.add(Verdict.of("costs-part-of-the-station-time",
                    t.completed && t.stationAtEnd > 0f && t.stationAtOrder - t.stationAtEnd < 0.75f * STAY_SECONDS,
                    fmt(t.stationAtOrder - t.stationAtEnd) + " of " + fmt(STAY_SECONDS) + " s spent, "
                            + fmt(t.stationAtEnd) + " s left when the run ended"));
            verdicts.add(Verdict.of("gives-the-ground-back", ship.laidArea(Warship.FireGroup.GUNS) == null
                            && ship.laidArea(Warship.FireGroup.MISSILES) == null && ship.isDirected(),
                    "no laid area after the pass and the director is " + (ship.isDirected() ? "on" : "off")));
            verdicts.add(Verdict.of("returns-to-station",
                    Math.hypot(ship.x - STATION_X, ship.y - STATION_Y) < 1.0 && ship.speed() < 0.05f
                            && !ship.departing(),
                    "ended at (" + fmt(ship.x) + ", " + fmt(ship.y) + ") of station (" + fmt(STATION_X) + ", "
                            + fmt(STATION_Y) + "), back on it from tick " + t.backOnStationTick));
            verdicts.add(Verdict.of("heard-once", t.count(WarshipSoundCue.Kind.LOW_PASS_ROAR) == 1
                            && t.count(WarshipSoundCue.Kind.LOW_PASS_REPORT) == 1,
                    "roars " + t.count(WarshipSoundCue.Kind.LOW_PASS_ROAR) + ", reports "
                            + t.count(WarshipSoundCue.Kind.LOW_PASS_REPORT)));
        } else {
            verdicts.add(Verdict.of("no-pass-no-speed", t.peakSpeed <= ship.maxSpeed * 1.02f && ship.lowPass() == null,
                    "peak " + fmt(t.peakSpeed) + " cells/s against a hover of " + fmt(ship.maxSpeed)));
            verdicts.add(Verdict.of("director-alone-stops-short", t.lineDown() == 0 && t.bystandersDown() == 0,
                    t.lineDown() + " of " + LINE_COUNT + " defenders on the line are down, "
                            + t.bystandersDown() + " abeam"));
            verdicts.add(Verdict.of("no-pass-sounds", t.count(WarshipSoundCue.Kind.LOW_PASS_ROAR) == 0,
                    "pass roars " + t.count(WarshipSoundCue.Kind.LOW_PASS_ROAR)));
            verdicts.add(Verdict.of("stays-on-station", Math.hypot(ship.x - STATION_X, ship.y - STATION_Y) < 1.0
                            && !ship.departing(),
                    "ended at (" + fmt(ship.x) + ", " + fmt(ship.y) + "), " + fmt(ship.stationSecondsRemaining())
                            + " s of station left"));
        }
        if (friendlies && pass) {
            verdicts.add(Verdict.of("does-not-refuse-friendly-ground",
                    t.friendliesDownAtEnd >= 1 && t.lineDownAtEnd >= 8,
                    t.friendliesDownAtEnd + " of " + layout.friendlies().length + " friendlies down, with "
                            + t.lineDownAtEnd + " of the line, when the pass ended; the pass fires over them"));
        } else if (friendlies) {
            verdicts.add(Verdict.of("no-friendly-loss-without-the-pass", t.friendliesDown() == 0,
                    t.friendliesDown() + " friendlies down with nothing fired on the line"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("reachCells", layout.reach());
        metrics.put("lineDownAtPassEnd", t.lineDownAtEnd);
        metrics.put("lineDownFinal", t.lineDown());
        metrics.put("bystandersDownAtPassEnd", t.bystandersDownAtEnd);
        metrics.put("friendliesDownAtPassEnd", t.friendliesDownAtEnd);
        metrics.put("friendliesDownFinal", t.friendliesDown());
        metrics.put("peakSpeed", t.peakSpeed);
        metrics.put("passSeconds", t.flown);
        metrics.put("stationSpent", t.completed ? t.stationAtOrder - t.stationAtEnd : 0f);
        metrics.put("stationLeftAtEnd", ship.stationSecondsRemaining());
        metrics.put("endedTick", t.endedTick);
        metrics.put("backOnStationTick", t.backOnStationTick);
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** Orders the pass, then watches it. */
    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final Warship ship;
        private final Layout layout;
        private final boolean pass;
        private final boolean friendlies;
        private final Map<String, Integer> down = new LinkedHashMap<>();
        private final Map<WarshipSoundCue.Kind, Integer> cues = new EnumMap<>(WarshipSoundCue.Kind.class);
        float peakSpeed;
        float stationAtOrder = Float.NaN;
        float stationAtEnd = Float.NaN;
        float flown;
        float length;
        float progressAtEnd;
        int endedTick = -1;
        int backOnStationTick = -1;
        int lineDownAtEnd;
        int bystandersDownAtEnd;
        int friendliesDownAtEnd;
        boolean completed;
        private LowPass flying;

        Trace(SceneWorld world, Warship ship, Layout layout, boolean pass, boolean friendlies) {
            this.world = world;
            this.ship = ship;
            this.layout = layout;
            this.pass = pass;
            this.friendlies = friendlies;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (String key : keys()) {
                if (!down.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) down.put(key, tick);
            }
            for (WarshipSoundCue cue : sim.warships().cuesThisFrame()) cues.merge(cue.kind(), 1, Integer::sum);
            peakSpeed = Math.max(peakSpeed, ship.speed());
            if (pass && tick == ORDER_AT) {
                stationAtOrder = ship.stationSecondsRemaining();
                sim.warships().orderLowPass(sim, ship, STATION_X, STATION_Y, PASS_END_X, STATION_Y);
                flying = ship.lowPass();
                if (flying != null) length = flying.length();
            }
            if (flying != null && !completed && !flying.active()) {
                completed = true;
                endedTick = tick;
                stationAtEnd = ship.stationSecondsRemaining();
                progressAtEnd = flying.progress();
                flown = flying.seconds();
                lineDownAtEnd = lineDown();
                bystandersDownAtEnd = bystandersDown();
                friendliesDownAtEnd = friendliesDown();
            }
            if (completed && backOnStationTick < 0 && Math.hypot(ship.x - STATION_X, ship.y - STATION_Y) < 1.0
                    && ship.speed() < 0.05f) {
                backOnStationTick = tick;
            }
        }

        private List<String> keys() {
            List<String> all = new ArrayList<>();
            for (int i = 0; i < layout.line().length; i++) all.add("d" + i);
            for (int i = 0; i < layout.bystanders().length; i++) all.add("b" + i);
            if (friendlies) for (int i = 0; i < layout.friendlies().length; i++) all.add("f" + i);
            return all;
        }

        int lineDown() {
            int n = 0;
            for (int i = 0; i < layout.line().length; i++) if (down.containsKey("d" + i)) n++;
            return n;
        }

        int bystandersDown() {
            int n = 0;
            for (int i = 0; i < layout.bystanders().length; i++) if (down.containsKey("b" + i)) n++;
            return n;
        }

        int friendliesDown() {
            int n = 0;
            for (int i = 0; i < layout.friendlies().length; i++) if (down.containsKey("f" + i)) n++;
            return n;
        }

        int count(WarshipSoundCue.Kind kind) {
            return cues.getOrDefault(kind, 0);
        }
    }
}
