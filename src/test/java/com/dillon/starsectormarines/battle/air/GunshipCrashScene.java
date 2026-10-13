package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real flak versus cleared guns, plus a controlled impact to isolate blast and wreck lifetime. */
public final class GunshipCrashScene implements BehaviorScene {
    @Override public String id() { return "gunship-crash"; }
    @Override public String label() { return "Gunship: flak, falling hull, damaging impact and permanent wreck"; }
    @Override public int frameDelayMillis() { return 80; }

    @Override public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        try {
            for (String loop : List.of("live-flak", "cleared-flak", "impact")) reports.add(playOne(loop, frames));
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) {
        boolean impact = loop.equals("impact");
        var builder = SceneBuilder.openGround(80, 54).missionCompletion(false);
        builder.squad("company").faction(Faction.MARINE).size(1).at(impact ? 39 : 7, 27)
                .primary("weapon.smg").stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, impact ? 39 : 7, 27)).done();
        builder.squad("enemy").faction(Faction.DEFENDER).size(1).at(impact ? 42 : 72, 27)
                .primary("weapon.smg").stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, impact ? 42 : 72, 27)).done();
        var world = builder.build();
        try (var sim = world.sim()) {
            sim.random().setSeed(20261013L);
            List<Long> guns = new ArrayList<>();
            if (!impact) for (int y : List.of(17, 37)) {
                long gun = sim.spawn(MapTurret.create("flak-" + y, Faction.DEFENDER,
                        TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID, 48, y));
                guns.add(gun);
                if (loop.equals("cleared-flak")) sim.applyDamage(gun, 10000f, 100f);
            }
            long craft = sim.spawnGunship(ShuttleType.MUDSKIPPER, Faction.ALLY, 40, 27, -12, 27, -12, 27);
            var mission = sim.world().mission(craft);
            if (impact) for (var mount : sim.world().airTurrets(craft).mounts) mount.ammo = 0;
            mission.orbitSecondsLeft = 100f;
            int[] firstFall = {-1}, fallTicks = {0}, impactTick = {-1}, shots = {0};
            boolean[] killed = {false};
            float[] hpBefore = {0f, 0f}, hpAfter = {0f, 0f};
            long friend = world.members("company")[0], enemy = world.members("enemy")[0];
            var run = SceneRun.of(world).observe((s, tick) -> {
                // This diagnostic holds only the impact fixture's infantry still and harmless.
                // The flak pair gets no damage or flight-state intervention.
                if (impact && !killed[0]) {
                    s.world().setCooldownTimer(friend, 100f);
                    s.world().setCooldownTimer(enemy, 100f);
                    if (mission.state == ShuttleState.ORBIT) {
                        var body = s.world().kinematics(craft);
                        body.x = 40.5f; body.y = 27.5f; body.vx = 0f; body.vy = 0f;
                        hpBefore[0] = s.world().hp(friend); hpBefore[1] = s.world().hp(enemy);
                        s.applyAimedDamage(craft, 0L, 10000f, 100f);
                        killed[0] = true;
                    }
                }
                for (var shot : s.getShotsThisFrame()) if (guns.contains(shot.shooterId)) shots[0]++;
                if (mission.state == ShuttleState.CRASHING) {
                    if (firstFall[0] < 0) firstFall[0] = tick;
                    fallTicks[0]++;
                }
                if (!s.getAirfieldService().groundWrecks().isEmpty() && impactTick[0] < 0) {
                    impactTick[0] = tick;
                    hpAfter[0] = s.world().isAlive(friend) ? s.world().hp(friend) : 0f;
                    hpAfter[1] = s.world().isAlive(enemy) ? s.world().hp(enemy) : 0f;
                }
                if ((tick % (impact ? 6 : 45) == 0 && tick < (impact ? 540 : 3300)) || (impact && tick == 5999))
                    frames.frame(loop, s, tick, loop + " / " + mission.state + " / tick " + tick);
                frames.advanced(loop, s, tick);
            });
            run.run(impact ? 6000 : 4200);
            int wrecks = sim.getAirfieldService().groundWrecks().size();
            List<Verdict> verdicts = new ArrayList<>();
            if (loop.equals("cleared-flak")) {
                verdicts.add(Verdict.of("clearing-guns-preserves-aircraft", !mission.destroyed && mission.state == ShuttleState.GONE,
                        "destroyed=" + mission.destroyed + ", state=" + mission.state + ", flak shots=" + shots[0]));
            } else {
                verdicts.add(Verdict.of("falls-before-impact", fallTicks[0] >= 45 && fallTicks[0] <= 50,
                        "fall ticks=" + fallTicks[0] + ", impact=" + impactTick[0]));
                verdicts.add(Verdict.of("permanent-blocking-wreck", wrecks == 1 && !sim.getGrid().isWalkable(
                        sim.getAirfieldService().groundWrecks().get(0).cellX(), sim.getAirfieldService().groundWrecks().get(0).cellY()),
                        "wrecks after " + (impact ? 200 : 140) + " seconds=" + wrecks));
                if (impact) verdicts.add(Verdict.of("impact-hurts-both-sides", hpBefore[0] > hpAfter[0] && hpBefore[1] > hpAfter[1],
                        "friendly " + hpBefore[0] + " -> " + hpAfter[0] + "; enemy " + hpBefore[1] + " -> " + hpAfter[1]));
                else verdicts.add(Verdict.of("real-flak-shootdown", shots[0] > 0 && mission.destroyed,
                        "visible flak shots=" + shots[0] + ", destroyed=" + mission.destroyed));
            }
            return new SceneReport(id(), loop, run.tick(), verdicts,
                    Map.of("firstFall", firstFall[0], "impactTick", impactTick[0], "fallTicks", fallTicks[0], "flakShots", shots[0], "wrecks", wrecks));
        }
    }
}
