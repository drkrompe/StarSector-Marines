package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
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
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.BoatGun;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A company Hermes with two guns on one hardpoint: does the pod fly as one turret and fire both?
 *
 * <p>One loop, {@code pods}: the Hermes on station over a garrison as a gunship, her yard's heavy MG on her
 * first hardpoint, two pulse lasers sharing her second and two SRM pods sharing her third. The owner's pick
 * (2026-10-27) is that a 2x2 hardpoint takes two 2x1 guns as a pod with a left and a right barrel. So each pod
 * should be two mounts on its hardpoint's one slot and one arc, both guns of each should fire on their own
 * cadence, and the pod should be laid on one line rather than its barrels pointing apart.
 *
 * <p>Judged from the craft's own mounts: a mount whose recoil clock reads zero after a tick fired that tick.
 */
public final class BoatPodScene implements BehaviorScene {

    static final int WIDTH = 76;
    static final int HEIGHT = 48;
    static final int TICKS = 1500;
    private static final int TARGET_X = 44;
    private static final int TARGET_Y = 24;
    private static final String CARBINE = "weapon.smg";
    private static final int GARRISON = 14;

    /** The Hermes under test: the yard's MG alone on hardpoint 1, twin lasers on 2, twin SRM pods on 3. */
    static FittedBoat podBoat() {
        String laser = BoatGun.PULSE_LASER.structureId();
        String srm = BoatGun.SRM_15.structureId();
        return new FittedBoat(ShuttleType.HERMES, Map.of(), "hermes-pod",
                new String[]{null, laser, srm}, new String[]{null, laser, srm});
    }

    @Override public String id() { return "boat-pod"; }

    @Override public String label() {
        return "Boat pod: a Hermes with two pulse lasers on one hardpoint and two SRM pods on another flies each pair "
                + "as one turret on its hardpoint's arc and fires both guns of each";
    }

    @Override public int frameDelayMillis() { return 300; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
        try {
            return List.of(playOne(frames));
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }

    private SceneReport playOne(FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.squad("company").faction(Faction.MARINE).size(4).at(18, 24).primary(CARBINE)
                .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, 18, 24)).done();
        builder.squad("garrison").faction(Faction.DEFENDER).size(GARRISON).at(TARGET_X, TARGET_Y).primary(CARBINE)
                .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, TARGET_X, TARGET_Y)).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261027L);
        long craft = sim.spawnGunship(podBoat(), Faction.MARINE, TARGET_X + 0.5f, TARGET_Y + 0.5f,
                -6f, TARGET_Y + 0.5f, -6f, TARGET_Y + 0.5f);
        Trace trace = new Trace(craft);
        SceneRun.of(world).observe(trace).frames(frames, "pods", 10, tick -> "pods / tick " + tick
                + " / rounds " + Arrays.toString(trace.rounds)).run(TICKS);
        return report(trace);
    }

    private SceneReport report(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("each-pod-is-two-guns-on-one-slot-and-arc", t.podsShareTheirSlot,
                t.mounts + " mounts; pods on one slot and arc " + t.podsShareTheirSlot));
        verdicts.add(Verdict.of("both-pulse-lasers-fire", t.rounds[1] > 0 && t.rounds[2] > 0,
                "laser rounds, left and right: " + t.rounds[1] + ", " + t.rounds[2]));
        verdicts.add(Verdict.of("both-srm-pods-fire", t.rounds[3] > 0 && t.rounds[4] > 0,
                "missile rounds, left and right: " + t.rounds[3] + ", " + t.rounds[4]));
        verdicts.add(Verdict.of("a-pod-is-laid-on-one-line", t.laidTicks > 0 && t.worstSplitWhileLaid <= 15f,
                String.format(Locale.ROOT, "%d ticks with both lasers on one target, their barrels at most %.1f° apart",
                        t.laidTicks, t.worstSplitWhileLaid)));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("mounts", t.mounts);
        for (int i = 0; i < t.rounds.length; i++) metrics.put("rounds" + i, t.rounds[i]);
        metrics.put("laidTicks", t.laidTicks);
        metrics.put("worstSplitWhileLaid", t.worstSplitWhileLaid);
        return new SceneReport(id(), "pods", TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final long craft;
        int mounts;
        boolean podsShareTheirSlot;
        final int[] rounds = new int[5];
        int laidTicks;
        float worstSplitWhileLaid;

        private Trace(long craft) {
            this.craft = craft;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            AirTurrets turrets = sim.world().airTurrets(craft);
            if (turrets == null) return;
            MountedTurret[] m = turrets.mounts;
            if (mounts == 0) {
                mounts = m.length;
                podsShareTheirSlot = m.length == 5 && pod(m[1], m[2]) && pod(m[3], m[4]) && m[0].podSide() == 0;
            }
            for (int i = 0; i < Math.min(m.length, rounds.length); i++) {
                if (m[i].recoilTimer == 0f) rounds[i]++;
            }
            if (m.length > 2 && m[1].targetId != 0L && m[1].targetId == m[2].targetId) {
                laidTicks++;
                worstSplitWhileLaid = Math.max(worstSplitWhileLaid,
                        Math.abs(TurretAim.shortestAngleDelta(m[1].facingDegrees, m[2].facingDegrees)));
            }
        }

        private static boolean pod(MountedTurret lead, MountedTurret second) {
            return lead.podPartner == second && second.podLead == lead
                    && lead.mount.localOffsetX == second.mount.localOffsetX
                    && lead.mount.localOffsetY == second.mount.localOffsetY
                    && lead.mount.arcDeg == second.mount.arcDeg && lead.mount.arcDeg < TurretMount.ALL_ROUND;
        }
    }
}
