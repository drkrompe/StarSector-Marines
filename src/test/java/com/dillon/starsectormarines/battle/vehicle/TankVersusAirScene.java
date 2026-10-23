package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.ShuttleType;
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
 * A tank under a gunship: <b>does a gun that reaches the air put its rounds into a hostile craft, and does it go back
 * to the ground when the sky is empty?</b> A hostile Mudskipper is put on station over a planted tank; the laser tank's
 * mount engages air ({@code TurretMountDef#engagesAir}) and the breakthrough tank's twin cannon does not, which makes
 * the cannon tank the control. A third loop adds a rifle section in the laser's reach, so the gun has both to choose
 * from.
 *
 * <p>Verdicts are mechanics only: the laser fires into the air at the craft, the cannon never does, and with men in
 * reach the laser still fires at the ground. Whether the craft comes down, how fast, and what the tank lost are readings.
 */
public final class TankVersusAirScene implements BehaviorScene {

    static final int FRAME_EVERY_TICKS = 6;

    private static final int WIDTH = 64;
    private static final int HEIGHT = 40;
    private static final int WINDOW_TICKS = 1800;
    private static final int SEEDS = 3;
    private static final int TANK_X = 30;
    private static final int TANK_Y = 20;
    private static final int SECTION_X = 50;
    private static final int SECTION_SIZE = 8;

    @Override public String id() { return "tank-versus-air"; }

    @Override
    public String label() {
        return "Tank versus air: the laser tank's gun takes a hostile gunship, the cannon tank's cannot";
    }

    @Override public int frameDelayMillis() { return 60; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(loop(frames, "laser-gunship", VehicleType.LASER_TANK, false));
        reports.add(loop(frames, "cannon-gunship", VehicleType.BREAKTHROUGH_TANK, false));
        reports.add(loop(frames, "laser-gunship-and-men", VehicleType.LASER_TANK, true));
        return reports;
    }

    private SceneReport loop(FrameSink frames, String loopId, VehicleType type, boolean men) {
        List<Trace> traces = new ArrayList<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            traces.add(playOnce(seed == 1 ? frames : FrameSink.NONE, loopId, type, men, seed));
        }
        return report(loopId, type, men, traces);
    }

    private Trace playOnce(FrameSink frames, String loopId, VehicleType type, boolean men, long seed) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(seed);
        // A spectator a long way off keeps the marine side on the field without touching the fight.
        builder.squad("spectator").faction(Faction.MARINE).size(1).at(2, 2).unarmed().stationary()
                .assigned(id -> ObjectiveAssignment.defendArea(id, 2, 2)).done();
        if (men) {
            builder.squad("rifles").faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(SECTION_SIZE)
                    .primary("weapon.field-rifle")
                    .inFile(SECTION_X, TANK_Y - SECTION_SIZE / 2, 0, 1)
                    .stationary()
                    .assigned(id -> ObjectiveAssignment.defendArea(id, SECTION_X, TANK_Y))
                    .done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        float tx = TANK_X + .5f;
        float ty = TANK_Y + .5f;
        long tank = sim.convoy().spawn(type, Faction.MARINE, VehicleMission.deployed(tx, ty));
        sim.convoy().body(tank).teleport(tx, ty, 0f);
        long craft = sim.spawnGunship(ShuttleType.MUDSKIPPER, Faction.DEFENDER,
                TANK_X, TANK_Y, -12, TANK_Y, WIDTH + 12, TANK_Y);
        Trace trace = new Trace(tank, craft, men ? world.members("rifles") : new long[0]);
        SceneRun run = SceneRun.of(world).observe(trace).frames(frames, loopId, FRAME_EVERY_TICKS, trace::caption);
        try {
            while (run.tick() < WINDOW_TICKS && !trace.over()) run.step();
        } finally {
            sim.close();
        }
        trace.ticks = run.tick();
        return trace;
    }

    // ---- instrument ----

    private static final class Trace implements TickObserver {
        final long tank;
        final long craft;
        final long[] men;
        int ticks;
        int craftDownTick = -1;
        int tankDeathTick = -1;
        int firstAirRoundTick = -1;
        int airRounds;
        int groundRounds;

        Trace(long tank, long craft, long[] men) {
            this.tank = tank;
            this.craft = craft;
            this.men = men;
        }

        boolean over() { return tankDeathTick >= 0 || craftDownTick >= 0 && menAlive == 0; }

        int menAlive;

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (tankDeathTick < 0 && !(sim.convoy().isVehicle(tank) && sim.convoy().isPresent(tank))) {
                tankDeathTick = tick;
            }
            var mission = sim.world().mission(craft);
            if (craftDownTick < 0 && (mission == null || mission.destroyed)) craftDownTick = tick;
            menAlive = 0;
            for (long man : men) if (sim.getRoster().isAliveById(man)) menAlive++;
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId != tank) continue;
                // A round into the air ends above the ground; a round at the ground ends on it.
                if (shot.toZ > 1f) {
                    airRounds++;
                    if (firstAirRoundTick < 0) firstAirRoundTick = tick;
                } else {
                    groundRounds++;
                }
            }
        }

        String caption(int tick) {
            return String.format(Locale.ROOT, "tank vs air  t=%.1fs  air rounds %d  ground rounds %d%s",
                    tick / 30f, airRounds, groundRounds, craftDownTick >= 0 ? "  CRAFT DOWN" : "");
        }
    }

    // ---- verdicts ----

    private SceneReport report(String loopId, VehicleType type, boolean men, List<Trace> traces) {
        int ticks = 0;
        int airRounds = 0;
        int groundRounds = 0;
        int craftDown = 0;
        int tankDeaths = 0;
        float downSeconds = 0f;
        for (Trace t : traces) {
            ticks += t.ticks;
            airRounds += t.airRounds;
            groundRounds += t.groundRounds;
            if (t.tankDeathTick >= 0) tankDeaths++;
            if (t.craftDownTick >= 0 && t.firstAirRoundTick >= 0) {
                craftDown++;
                downSeconds += (t.craftDownTick - t.firstAirRoundTick) / 30f;
            }
        }
        List<Verdict> verdicts = new ArrayList<>();
        boolean reachesAir = type.turretStructure().mount.engagesAir;
        if (reachesAir) {
            verdicts.add(Verdict.of("gun-fires-into-the-air", airRounds > 0,
                    airRounds + " rounds into the air over " + traces.size() + " plays"));
        } else {
            verdicts.add(Verdict.of("ground-gun-holds-at-the-air", airRounds == 0,
                    airRounds + " rounds into the air from a gun that does not reach it"));
        }
        if (men) {
            verdicts.add(Verdict.of("gun-still-fires-at-the-ground", groundRounds > 0,
                    groundRounds + " rounds at the ground with men in reach"));
        }
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("plays", traces.size());
        m.put("ticks", ticks);
        m.put("airRounds", airRounds);
        m.put("groundRounds", groundRounds);
        m.put("craftDown", craftDown);
        m.put("meanSecondsToDownFromFirstAirRound", craftDown > 0 ? downSeconds / craftDown : -1f);
        m.put("tankDeaths", tankDeaths);
        return new SceneReport(id(), loopId, ticks, verdicts, m);
    }
}
