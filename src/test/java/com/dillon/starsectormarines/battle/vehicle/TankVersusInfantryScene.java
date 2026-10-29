package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A tank against men: <b>does the laser tank's turret do what it is fitted for?</b> One planted tank and a
 * twelve-strong rifle section eighteen cells east of it, inside both guns' reach, played once with the breakthrough
 * tank's twin cannon and once with the laser tank's burst laser, against a line section and against power suits.
 * The cannon tank is the control every laser row is read against.
 *
 * <p>Verdicts are mechanics only: the laser fires at men, and none of its rounds carries wall damage or blast, so it
 * cannot open the ground it sweeps. Who wins, how fast the section breaks and how many rounds it took are readings.
 */
public final class TankVersusInfantryScene implements BehaviorScene {

    static final int FRAME_EVERY_TICKS = 10;

    private static final int WIDTH = 56;
    private static final int HEIGHT = 25;
    private static final int WINDOW_TICKS = 1800;
    private static final int SEEDS = 4;

    private static final int TANK_X = 10;
    private static final int TANK_Y = 12;
    private static final int SECTION_X = 28;
    private static final int SECTION_SIZE = 12;
    private static final String RIFLE = "weapon.field-rifle";
    private static final String SECTION = "rifles";

    /** Line infantry's plate, and a heavy power suit's ({@code marine-armor-catalog.armor.json}). */
    private enum Plate {
        LINE(10f, 8f), SUITS(60f, 14f);
        final float capacity;
        final float rating;
        Plate(float capacity, float rating) { this.capacity = capacity; this.rating = rating; }
    }

    @Override public String id() { return "tank-versus-infantry"; }

    @Override
    public String label() {
        return "Tank versus infantry: the laser tank's turret against a section, read against the cannon tank";
    }

    @Override public int frameDelayMillis() { return 70; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(loop(frames, "cannon-line", VehicleType.BREAKTHROUGH_TANK, Plate.LINE));
        reports.add(loop(frames, "laser-line", VehicleType.LASER_TANK, Plate.LINE));
        reports.add(loop(frames, "cannon-suits", VehicleType.BREAKTHROUGH_TANK, Plate.SUITS));
        reports.add(loop(frames, "laser-suits", VehicleType.LASER_TANK, Plate.SUITS));
        return reports;
    }

    private SceneReport loop(FrameSink frames, String loopId, VehicleType type, Plate plate) {
        List<Trace> traces = new ArrayList<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            traces.add(playOnce(seed == 1 ? frames : FrameSink.NONE, loopId, type, plate, seed));
        }
        return report(loopId, type, traces);
    }

    private Trace playOnce(FrameSink frames, String loopId, VehicleType type, Plate plate, long seed) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(seed);
        builder.squad(SECTION).faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(SECTION_SIZE)
                .primary(RIFLE)
                .armor(plate.capacity, plate.rating)
                .inFile(SECTION_X, TANK_Y - SECTION_SIZE / 2, 0, 1)
                .stationary()
                .assigned(id -> ObjectiveAssignment.defendArea(id, SECTION_X, TANK_Y))
                .done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        float tx = TANK_X + .5f;
        float ty = TANK_Y + .5f;
        long tank = sim.convoy().spawn(type, Faction.MARINE, VehicleMission.deployed(tx, ty));
        sim.convoy().body(tank).teleport(tx, ty, 0f);
        Trace trace = new Trace(tank, world.members(SECTION));
        SceneRun run = SceneRun.of(world).observe(trace).frames(frames, loopId, FRAME_EVERY_TICKS, trace::caption);
        try {
            while (run.tick() < WINDOW_TICKS && !trace.over()) run.step();
            trace.menKilled = trace.men.length - trace.menAlive(sim);
        } finally {
            sim.close();
        }
        trace.ticks = run.tick();
        return trace;
    }

    // ---- instrument ----

    private static final class Trace implements TickObserver {
        final long tank;
        final long[] men;
        int ticks;
        int tankDeathTick = -1;
        int sectionBrokenTick = -1;
        int firstShotTick = -1;
        int rounds;
        /** Rounds that carried blast or wall damage. */
        int roundsThatBreak;
        int menKilled;

        Trace(long tank, long[] men) {
            this.tank = tank;
            this.men = men;
        }

        boolean over() { return tankDeathTick >= 0 || sectionBrokenTick >= 0; }

        int menAlive(BattleSimulation sim) {
            int alive = 0;
            for (long man : men) if (sim.getRoster().isAliveById(man)) alive++;
            return alive;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (tankDeathTick < 0 && !(sim.convoy().isVehicle(tank) && sim.convoy().isPresent(tank))) {
                tankDeathTick = tick;
            }
            if (sectionBrokenTick < 0 && menAlive(sim) == 0) sectionBrokenTick = tick;
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId != tank || shot.weaponDef() == null) continue;
                if (firstShotTick < 0) firstShotTick = tick;
                rounds++;
                if (shot.weaponDef().wallDamage > 0 || shot.weaponDef().aoeRadius > 0f) roundsThatBreak++;
            }
        }

        String caption(int tick) {
            return String.format(Locale.ROOT, "tank vs infantry  t=%.1fs  rounds %d", tick / 30f, rounds);
        }
    }

    // ---- verdicts ----

    private SceneReport report(String loopId, VehicleType type, List<Trace> traces) {
        int ticks = 0;
        int rounds = 0;
        int roundsThatBreak = 0;
        int menKilled = 0;
        int sectionsBroken = 0;
        int tankDeaths = 0;
        float breakSeconds = 0f;
        for (Trace t : traces) {
            ticks += t.ticks;
            rounds += t.rounds;
            roundsThatBreak += t.roundsThatBreak;
            menKilled += t.menKilled;
            if (t.tankDeathTick >= 0) tankDeaths++;
            if (t.sectionBrokenTick >= 0) {
                sectionsBroken++;
                breakSeconds += (t.sectionBrokenTick - Math.max(0, t.firstShotTick)) / 30f;
            }
        }
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("turret-fires-at-men", rounds > 0,
                rounds + " rounds fired over " + traces.size() + " plays"));
        if (type == VehicleType.LASER_TANK) {
            verdicts.add(Verdict.of("laser-carries-no-blast", roundsThatBreak == 0,
                    roundsThatBreak + " laser rounds carried blast or wall damage"));
        }
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("plays", traces.size());
        m.put("ticks", ticks);
        m.put("menPerPlay", SECTION_SIZE);
        m.put("menKilled", menKilled);
        m.put("sectionsBroken", sectionsBroken);
        m.put("meanSecondsToBreakFromFirstShot", sectionsBroken > 0 ? breakSeconds / sectionsBroken : -1f);
        m.put("tankDeaths", tankDeaths);
        m.put("rounds", rounds);
        return new SceneReport(id(), loopId, ticks, verdicts, m);
    }
}
