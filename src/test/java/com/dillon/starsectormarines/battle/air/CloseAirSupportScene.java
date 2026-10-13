package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.power.CloseAirSupport;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
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
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Close Air Support: a Mudskipper gunship called over a garrison.
 *
 * <p>{@code control} is a garrison in the open with a company watching from the west and nobody
 * calling anything. {@code called} is the same world with the power activated on the garrison. The
 * question is the one the owner asked for: does the craft come in, circle the ground with its
 * <em>flank</em> to it, and fire out of that side, and then leave when its time is spent.
 *
 * <p>Judged from the craft's own pose and magazines rather than from the garrison alone, because
 * a gunship that sprayed the ground with its nose guns would kill the garrison just as dead and
 * answer nothing. The flank is measured as the angle between the craft's inboard beam and the
 * bearing to the centre of the ring; the side that fires is read off which mounts spent rounds.
 */
public final class CloseAirSupportScene implements BehaviorScene {

    static final int WIDTH = 76;
    static final int HEIGHT = 48;
    private static final int TICKS = 1450;
    private static final int CALL_TICK = 30;
    private static final int TARGET_X = 44;
    private static final int TARGET_Y = 24;
    private static final String CARBINE = "weapon.smg";
    private static final int SUPPLIES = 10;
    private static final int GARRISON = 12;
    private static final int RESERVE = 12;

    @Override public String id() { return "close-air-support"; }

    @Override public String label() {
        return "Close Air Support: a called Mudskipper gunship enters from the nearest edge, circles the targeted ground "
                + "with its flank to it, fires out of that side, and leaves when its time is spent";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("control", "called")) reports.add(playOne(loop, frames));
        return reports;
    }

    private static final class Supplies implements CommandPowerResources {
        int supplies = SUPPLIES;

        @Override public int availableSupplies() { return supplies; }

        @Override public boolean spendSupplies(int amount) {
            if (amount < 0 || supplies < amount) return false;
            supplies -= amount;
            return true;
        }
    }

    private SceneReport playOne(String loop, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.squad("company").faction(Faction.MARINE).size(4).at(22, 24).primary(CARBINE)
                .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, 22, 24)).done();
        builder.squad("garrison").faction(Faction.DEFENDER).size(GARRISON).at(TARGET_X, TARGET_Y).primary(CARBINE)
                .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, TARGET_X, TARGET_Y)).done();
        // A second squad a little way off, so the recording has ground to keep working after the first is down.
        builder.squad("reserve").faction(Faction.DEFENDER).size(RESERVE).at(TARGET_X + 7, TARGET_Y - 3).primary(CARBINE)
                .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, TARGET_X + 7, TARGET_Y - 3)).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261014L);
        Supplies resources = new Supplies();
        if (loop.equals("called")) {
            sim.setCommandPowers(List.of(new CloseAirSupport()));
            sim.setCommandPowerResources(resources);
        }
        Trace trace = new Trace(world, loop);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, loop.equals("called") ? 18 : 290, tick -> trace.caption(loop, tick))
                .run(TICKS);
        return report(loop, trace, resources, sim);
    }

    private SceneReport report(String loop, Trace t, Supplies resources, BattleSimulation sim) {
        List<Verdict> verdicts = new ArrayList<>();
        float tps = 1f / BattleSimulation.TICK_DT;
        if (loop.equals("control")) {
            verdicts.add(Verdict.of("no-craft-without-a-call", t.firstSeenTick < 0 && t.garrisonDead == 0,
                    "craft seen " + (t.firstSeenTick < 0 ? "never" : "at tick " + t.firstSeenTick)
                            + ", garrison dead " + t.garrisonDead + " of " + GARRISON));
        } else {
            int charges = sim.getCommandPowerService().getChargesRemaining(CloseAirSupport.ID);
            verdicts.add(Verdict.of("the-call-was-seen-and-paid-for",
                    t.targetSeen && charges == 1 && resources.supplies == SUPPLIES - 2,
                    "target seen " + t.targetSeen + ", charges left " + charges + ", supplies " + resources.supplies
                            + " of " + SUPPLIES));
            verdicts.add(Verdict.of("enters-from-off-the-map",
                    t.firstSeenTick >= 0 && (t.entryX < 0 || t.entryY < 0 || t.entryX > WIDTH || t.entryY > HEIGHT),
                    String.format(Locale.ROOT, "first seen at tick %d at (%.1f, %.1f), map %dx%d",
                            t.firstSeenTick, t.entryX, t.entryY, WIDTH, HEIGHT)));
            verdicts.add(Verdict.of("circles-the-ground-it-was-sent-to",
                    t.orbitTicks >= 25 * tps && t.worstRingError <= 3f && t.laps >= 3f,
                    String.format(Locale.ROOT, "%.1f s on the ring, %.1f laps, never more than %.2f cells off a radius of %.0f",
                            t.orbitTicks / tps, t.laps, t.worstRingError, GunshipOrbit.RADIUS_CELLS)));
            verdicts.add(Verdict.of("keeps-a-flank-to-the-ground",
                    t.orbitTicks > 0 && t.worstFlankErrorDeg <= 25f,
                    String.format(Locale.ROOT, "the inboard beam was never more than %.1f degrees off the bearing to the centre "
                            + "(%s ring)", t.worstFlankErrorDeg, t.clockwise ? "clockwise" : "counter-clockwise")));
            verdicts.add(Verdict.of("fires-out-of-the-inboard-side-only",
                    t.inboardRounds > 0 && t.outboardRounds == 0,
                    t.inboardRounds + " rounds from the guns facing the ring's centre, "
                            + t.outboardRounds + " from the far beam"));
            verdicts.add(Verdict.of("works-the-garrison", t.garrisonDead >= 8,
                    "garrison dead " + t.garrisonDead + " of " + (GARRISON + RESERVE) + ", first death at tick " + t.firstDeath));
            verdicts.add(Verdict.of("leaves-when-its-time-is-spent",
                    t.goneTick >= 0 && Math.abs(t.orbitTicks - GunshipOrbit.STATION_SECONDS * tps) <= 10,
                    String.format(Locale.ROOT, "on the ring %.1f s of %.0f s, gone at tick %d",
                            t.orbitTicks / tps, GunshipOrbit.STATION_SECONDS, t.goneTick)));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("firstSeenTick", t.firstSeenTick);
        metrics.put("orbitTicks", t.orbitTicks);
        metrics.put("laps", t.laps);
        metrics.put("worstRingError", t.worstRingError);
        metrics.put("worstFlankErrorDeg", t.worstFlankErrorDeg);
        metrics.put("inboardRounds", t.inboardRounds);
        metrics.put("outboardRounds", t.outboardRounds);
        metrics.put("garrisonDead", t.garrisonDead);
        metrics.put("goneTick", t.goneTick);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private static final int SETTLE_TICKS = 90;

        private final SceneWorld world;
        private final String loop;
        boolean targetSeen;
        boolean clockwise;
        ShuttleMission mission;
        AirTurrets turrets;
        int[] startAmmo;
        long craft;
        int firstSeenTick = -1;
        int goneTick = -1;
        float entryX;
        float entryY;
        int orbitTicks;
        float worstRingError;
        float worstFlankErrorDeg;
        float swept;
        float lastAngle = Float.NaN;
        float laps;
        int inboardRounds;
        int outboardRounds;
        int garrisonDead;
        int firstDeath = -1;

        private Trace(SceneWorld world, String loop) {
            this.world = world;
            this.loop = loop;
        }

        String caption(String loop, int tick) {
            if (mission == null) return loop + " / tick " + tick;
            return loop + " / tick " + tick + " / " + mission.state.name().toLowerCase(Locale.ROOT)
                    + (mission.state == ShuttleState.ORBIT
                            ? String.format(Locale.ROOT, " / %.0f s left", mission.orbitSecondsLeft) : "")
                    + " / garrison " + (GARRISON + RESERVE - garrisonDead) + " of " + (GARRISON + RESERVE);
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (tick == CALL_TICK && loop.equals("called")) {
                targetSeen = sim.playerSeesCell(TARGET_X, TARGET_Y);
                sim.getCommandPowerService().requestActivation(CloseAirSupport.ID, TARGET_X, TARGET_Y);
            }
            if (mission == null && sim.getAirEntityIds().length > 0) {
                craft = sim.getAirEntityIds()[0];
                mission = sim.world().mission(craft);
                turrets = sim.world().airTurrets(craft);
                firstSeenTick = tick;
                AirBody body = sim.world().kinematics(craft);
                entryX = body.x;
                entryY = body.y;
                clockwise = mission.orbitClockwise;
                startAmmo = new int[turrets.mounts.length];
                for (int i = 0; i < startAmmo.length; i++) startAmmo[i] = turrets.mounts[i].ammo;
            }
            int dead = 0;
            for (String key : List.of("garrison", "reserve")) {
                for (long u : world.members(key)) if (!sim.getRoster().isAliveById(u)) dead++;
            }
            if (dead > 0 && firstDeath < 0) firstDeath = tick;
            garrisonDead = dead;
            if (mission == null) return;
            if (goneTick < 0 && mission.state == ShuttleState.GONE) goneTick = tick;
            if (mission.state != ShuttleState.ORBIT) return;

            AirBody body = sim.world().kinematics(craft);
            float cx = TARGET_X + 0.5f;
            float cy = TARGET_Y + 0.5f;
            orbitTicks++;
            float angle = (float) Math.atan2(body.y - cy, body.x - cx);
            if (!Float.isNaN(lastAngle)) {
                float step = angle - lastAngle;
                while (step > Math.PI) step -= (float) (2 * Math.PI);
                while (step < -Math.PI) step += (float) (2 * Math.PI);
                swept += Math.abs(step);
                laps = swept / (float) (2 * Math.PI);
            }
            lastAngle = angle;
            if (orbitTicks > SETTLE_TICKS) {
                worstRingError = Math.max(worstRingError,
                        Math.abs((float) Math.hypot(body.x - cx, body.y - cy) - GunshipOrbit.RADIUS_CELLS));
                // The inboard beam: the right-hand side of the heading for a clockwise ring, the left for the other.
                double heading = Math.toRadians(body.facingDegrees);
                double rightX = Math.cos(heading);
                double rightY = Math.sin(heading);
                double side = clockwise ? 1.0 : -1.0;
                double toCx = cx - body.x;
                double toCy = cy - body.y;
                double len = Math.hypot(toCx, toCy);
                double dot = (side * rightX * toCx + side * rightY * toCy) / Math.max(1e-6, len);
                worstFlankErrorDeg = Math.max(worstFlankErrorDeg,
                        (float) Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot)))));
            }
            inboardRounds = 0;
            outboardRounds = 0;
            for (int i = 0; i < turrets.mounts.length; i++) {
                MountedTurret mount = turrets.mounts[i];
                int spent = startAmmo[i] - mount.ammo;
                boolean inboard = Math.signum(mount.mount.localOffsetX) == GunshipOrbit.inboardSide(clockwise);
                if (inboard) inboardRounds += spent;
                else outboardRounds += spent;
            }
        }
    }
}
