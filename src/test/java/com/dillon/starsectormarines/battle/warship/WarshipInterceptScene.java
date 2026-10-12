package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
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
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
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
 * A defending Onslaught against a marine drop, three ways: no ship, a ship
 * whose stance is HOLD (the density director, which waits for people to stand
 * on the ground), and a ship defending (stance DEFEND), which denies the
 * landing zone with rounds timed to land as the boat touches down.
 *
 * <p>A boat of the defenders' own lands elsewhere in reach of the same ship, as
 * the standing check that interception fires only at a hostile drop. Whether a
 * detonation hurts a boat on its pad is not established by the simulation, so
 * the boat's fate is a recorded reading and not a verdict.
 */
public final class WarshipInterceptScene implements BehaviorScene {

    private static final int WIDTH = 260;
    private static final int HEIGHT = 80;
    private static final int TICKS = 2400;
    private static final float SHIP_X = 200f;
    private static final float SHIP_Y = 40f;
    private static final float LZ_X = 135.5f;
    private static final float LZ_Y = 40.5f;
    private static final float FRIENDLY_LZ_X = 207.5f;
    private static final float FRIENDLY_LZ_Y = 66.5f;
    private static final float DELAY_SECONDS = 20f;
    private static final float NEAR_LZ = 9f;
    private static final float NEAR_FRIENDLY = 8f;
    private static final int WATCH_TICKS = 150;

    @Override public String id() { return "warship-intercept"; }

    @Override public String label() {
        return "Warship intercept: a defending ship lands rounds on the landing zone as the boat touches down, "
                + "where a ship with no stance waits for people to stand on it, and never fires at its own side's boat";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        Run none = playOne("no-ship", frames);
        reports.add(report("no-ship", none, none, none));
        Run density = playOne("density-director", frames);
        reports.add(report("density-director", density, none, density));
        Run denial = playOne("landing-denial", frames);
        reports.add(report("landing-denial", denial, none, density));
        return reports;
    }

    private record Run(Trace trace, Warship ship) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 250, 8);
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 5, 75);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        long boat = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.MARINE, LZ_X, LZ_Y, 0.5f, LZ_Y, 0.5f, LZ_Y,
                DELAY_SECONDS, 6);
        long friendly = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER, FRIENDLY_LZ_X, FRIENDLY_LZ_Y,
                259.5f, FRIENDLY_LZ_Y, 259.5f, FRIENDLY_LZ_Y, DELAY_SECONDS, 6);
        Warship ship = null;
        if (!loop.equals("no-ship")) {
            ship = Warship.fit("onslaught", "onslaught_Standard", Faction.DEFENDER, SHIP_X, SHIP_Y, 90f);
            // A capital swings slowly: the defender is already lying on the heading that brings its guns onto the zone.
            float heading = WarshipFacing.bestHeading(ship, LZ_X, LZ_Y);
            ship.facingDegrees = heading;
            ship.desiredHeading = heading;
            ship.stationTo(SHIP_X, SHIP_Y);
            ship.direct(8f, true, 3);
            ship.useDoctrine();
            ship.stance(loop.equals("landing-denial") ? MissionStance.DEFEND : MissionStance.HOLD);
            // This scene measures the landing denial alone, for what cannot shoot the boat; `warship-vs-air` owns shooting it.
            ship.airFire(AirFireOrder.OFF);
            sim.addWarship(ship);
        }
        Trace trace = new Trace(boat, friendly, ship);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 6, tick -> loop + " / tick " + tick + " / marines " + trace.marinesAlive)
                .run(TICKS);
        return new Run(trace, ship);
    }

    private SceneReport report(String loop, Run run, Run none, Run density) {
        Trace t = run.trace();
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("the-boat-lands", t.touchdownTick >= 0,
                "touchdown at tick " + t.touchdownTick + ", boat " + t.boatEnd));
        int checkTick = t.touchdownTick + WATCH_TICKS;
        switch (loop) {
            case "no-ship" -> {
                verdicts.add(Verdict.of("everyone-gets-off", t.marinesAt(checkTick) >= 6,
                        t.marinesAt(checkTick) + " marines alive five seconds after touchdown"));
            }
            case "density-director" -> {
                verdicts.add(Verdict.of("waits-for-the-ground",
                        t.firstRoundNearLzLanding < 0 || t.firstRoundNearLzLanding > t.touchdownTick,
                        "first round on the zone lands at tick " + t.firstRoundNearLzLanding + ", touchdown " + t.touchdownTick));
                verdicts.add(Verdict.of("is-bombarding", run.ship().posture() == WarshipPosture.BOMBARD,
                        run.ship().posture() + ": " + run.ship().postureReason()));
            }
            default -> {
                verdicts.add(Verdict.of("is-intercepting", t.sawIntercept,
                        "posture at the end " + run.ship().posture() + ": " + run.ship().postureReason()));
                verdicts.add(Verdict.of("touchdown-estimate-within-a-second",
                        Math.abs(t.estimateBefore(6f) - 6f) <= 1f && Math.abs(t.estimateBefore(3f) - 3f) <= 1f,
                        String.format(Locale.ROOT, "estimated %.1f s with 6 s to go and %.1f s with 3 s to go",
                                t.estimateBefore(6f), t.estimateBefore(3f))));
                verdicts.add(Verdict.of("fires-before-touchdown", t.firstRoundNearLzFired >= 0 && t.firstRoundNearLzFired < t.touchdownTick,
                        "first zone round fired at tick " + t.firstRoundNearLzFired + ", touchdown " + t.touchdownTick));
                verdicts.add(Verdict.of("lands-with-the-boat",
                        t.firstRoundNearLzLanding >= 0 && t.firstRoundNearLzLanding - t.touchdownTick >= -45 && t.firstRoundNearLzLanding - t.touchdownTick <= 30,
                        "first zone round lands at tick " + t.firstRoundNearLzLanding + ", touchdown " + t.touchdownTick));
                verdicts.add(Verdict.of("fewer-marines-than-the-density-director",
                        t.marinesAt(checkTick) < density.trace().marinesAt(density.trace().touchdownTick + WATCH_TICKS),
                        t.marinesAt(checkTick) + " alive against " + density.trace().marinesAt(
                                density.trace().touchdownTick + WATCH_TICKS) + " five seconds after touchdown"));
                verdicts.add(Verdict.of("fewer-marines-than-no-ship", t.marinesAt(checkTick) < none.trace().marinesAt(
                                none.trace().touchdownTick + WATCH_TICKS),
                        t.marinesAt(checkTick) + " alive against " + none.trace().marinesAt(
                                none.trace().touchdownTick + WATCH_TICKS) + " with no ship"));
            }
        }
        if (run.ship() != null) {
            verdicts.add(Verdict.of("leaves-its-own-boat-alone", t.roundsNearFriendlyBoat == 0,
                    t.roundsNearFriendlyBoat + " rounds landed within " + NEAR_FRIENDLY + " cells of the defenders' boat"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("touchdownTick", t.touchdownTick);
        metrics.put("padDescentTicks", t.touchdownTick - t.padDescentTick);
        metrics.put("firstZoneRoundFired", t.firstRoundNearLzFired);
        metrics.put("firstZoneRoundLands", t.firstRoundNearLzLanding);
        metrics.put("marinesAfterFiveSeconds", t.marinesAt(checkTick));
        metrics.put("roundsNearFriendlyBoat", t.roundsNearFriendlyBoat);
        metrics.put("etaEstimate6sBefore", t.estimateBefore(6f));
        metrics.put("etaEstimate3sBefore", t.estimateBefore(3f));
        metrics.put("boatHpAtEnd", t.boatHp);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final long boat;
        private final long friendlyBoat;
        private final Warship ship;
        private final Set<WarshipShell> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        private final int[] marines = new int[TICKS + 2];
        private final float[] etaEstimate = new float[TICKS + 2];
        int marinesAlive;
        int touchdownTick = -1;
        int padDescentTick = -1;
        int firstRoundNearLzFired = -1;
        int firstRoundNearLzLanding = -1;
        int roundsNearFriendlyBoat;
        boolean sawIntercept;
        String boatEnd = "?";
        float boatHp = -1f;

        private Trace(long boat, long friendlyBoat, Warship ship) {
            this.boat = boat;
            this.friendlyBoat = friendlyBoat;
            this.ship = ship;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            ShuttleMission mission = sim.world().mission(boat);
            if (mission != null) {
                boatEnd = mission.state.toString();
                if (touchdownTick < 0 && mission.state == ShuttleState.LANDED) touchdownTick = tick;
                if (padDescentTick < 0 && mission.state == ShuttleState.PAD_DESCENT) padDescentTick = tick;
                boatHp = sim.world().hp(boat);
            } else {
                boatEnd = "removed";
            }
            UnitRosterService roster = sim.getRoster();
            int count = 0;
            for (int i = 0, n = roster.liveCount(); i < n; i++) {
                long u = roster.denseArray()[i];
                if (roster.identity().faction(u) != Faction.MARINE) continue;
                float ux = sim.world().x(u);
                if (ux > 95f && ux < 175f) count++;
            }
            marinesAlive = count;
            marines[Math.min(tick, TICKS + 1)] = count;
            if (ship != null && ship.posture() == WarshipPosture.INTERCEPT) sawIntercept = true;
            if (ship == null) return;
            AirThreatPicture picture = AirThreatPicture.take(sim, ship, sim.warships().ships());
            etaEstimate[Math.min(tick, TICKS + 1)] = picture.drops().isEmpty() ? -1f : picture.drops().get(0).etaSeconds();
            for (WarshipShell shell : sim.warships().shells()) {
                if (!seen.add(shell)) continue;
                int lands = tick + Math.round(shell.flightSeconds * 30f);
                if (Math.hypot(shell.toX - LZ_X, shell.toY - LZ_Y) <= NEAR_LZ) {
                    if (firstRoundNearLzFired < 0) firstRoundNearLzFired = tick;
                    if (firstRoundNearLzLanding < 0) firstRoundNearLzLanding = lands;
                }
                if (Math.hypot(shell.toX - FRIENDLY_LZ_X, shell.toY - FRIENDLY_LZ_Y) <= NEAR_FRIENDLY) {
                    roundsNearFriendlyBoat++;
                }
            }
        }

        /** The ship's estimate of the seconds to touchdown, read {@code secondsBefore} seconds before it happened. */
        float estimateBefore(float secondsBefore) {
            return touchdownTick < 0 ? -1f : etaEstimate[Math.max(0, touchdownTick - Math.round(secondsBefore * 30f))];
        }

        int marinesAt(int tick) {
            return marines[Math.max(0, Math.min(tick, TICKS + 1))];
        }
    }
}
