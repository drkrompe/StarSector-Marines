package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
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
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;

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
 * question is the overwatch the owner asked for: does the craft come in, circle overhead of the
 * ground with the hull's one turret at the hull's own mount, fire on whatever is below from every
 * side of the ring, and then leave when its time is spent.
 *
 * <p>Judged from the craft's own pose and magazine rather than from the garrison alone, because a
 * gunship that killed the garrison from one corner of the map would answer nothing about circling.
 * The cover is read off which quarter of the ring the craft was in when each round left.
 */
public final class CloseAirSupportScene implements BehaviorScene {

    static final int WIDTH = 76;
    static final int HEIGHT = 48;
    private static final int TICKS = 1800;
    private static final int CALL_TICK = 30;
    private static final int TARGET_X = 44;
    private static final int TARGET_Y = 24;
    private static final String CARBINE = "weapon.smg";
    private static final int SUPPLIES = 10;
    private static final int GARRISON = 12;
    private static final int RESERVE = 12;

    @Override public String id() { return "close-air-support"; }

    @Override public String label() {
        return "Close Air Support: a called Mudskipper gunship enters from the nearest edge, circles overhead of the targeted "
                + "ground with its one turret on the hull's own mount, fires on it from every side of the ring, and leaves when its time is spent";
    }

    @Override public int frameDelayMillis() { return 400; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        try {
            for (String loop : List.of("control", "called")) reports.add(playOne(loop, frames));
        } finally {
            // Process-wide: leave no other scene's aircraft armed by this one's install.
            TurretSlotResolver.useHullSpecs(null);
        }
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
                .frames(frames, loop, loop.equals("called") ? 12 : 290, tick -> trace.caption(loop, tick))
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
                    t.orbitTicks >= 25 * tps && t.worstRingError <= 3f && t.laps >= 1.25f && t.laps <= 2f,
                    String.format(Locale.ROOT, "%.1f s on the ring, %.1f laps, never more than %.2f cells off a radius of %.0f",
                            t.orbitTicks / tps, t.laps, t.worstRingError, GunshipOrbit.RADIUS_CELLS)));
            verdicts.add(Verdict.of("one-turret-on-the-hulls-own-mount",
                    t.mounts == 1 && t.mountAtHullSlot,
                    t.mounts + " turret(s); at the hull's published weapon slot " + t.mountAtHullSlot));
            verdicts.add(Verdict.of("covers-the-ground-from-every-side-of-the-ring",
                    t.quartersFiredFrom >= 3 && t.rounds > 0,
                    t.rounds + " rounds, fired from " + t.quartersFiredFrom + " of 4 quarters of the ring ("
                            + java.util.Arrays.toString(t.roundsByQuarter) + ")"));
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
        metrics.put("rounds", t.rounds);
        metrics.put("quartersFiredFrom", t.quartersFiredFrom);
        metrics.put("garrisonDead", t.garrisonDead);
        metrics.put("goneTick", t.goneTick);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private static final int SETTLE_TICKS = 90;

        private final SceneWorld world;
        private final String loop;
        boolean targetSeen;
        ShuttleMission mission;
        AirTurrets turrets;
        long craft;
        int firstSeenTick = -1;
        int goneTick = -1;
        float entryX;
        float entryY;
        int orbitTicks;
        float worstRingError;
        float swept;
        float lastAngle = Float.NaN;
        float laps;
        int mounts;
        boolean mountAtHullSlot;
        int rounds;
        int lastAmmo;
        final int[] roundsByQuarter = new int[4];
        int quartersFiredFrom;
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
                mounts = turrets.mounts.length;
                float[][] slots = TurretSlotResolver.resolve(ShuttleType.MUDSKIPPER.renderHullId());
                mountAtHullSlot = mounts == 1 && slots.length >= 1
                        && turrets.mounts[0].mount.localOffsetX == slots[0][0]
                        && turrets.mounts[0].mount.localOffsetY == slots[0][1];
                lastAmmo = turrets.mounts[0].ammo;
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
            }
            // Which quarter of the ring the craft was in when each round left the turret.
            int ammo = turrets.mounts[0].ammo;
            int spent = lastAmmo - ammo;
            lastAmmo = ammo;
            if (spent > 0) {
                int quarter = Math.floorMod((int) Math.floor((angle + Math.PI) / (Math.PI / 2)), 4);
                roundsByQuarter[quarter] += spent;
                rounds += spent;
                quartersFiredFrom = 0;
                for (int n : roundsByQuarter) if (n > 0) quartersFiredFrom++;
            }
        }
    }
}
