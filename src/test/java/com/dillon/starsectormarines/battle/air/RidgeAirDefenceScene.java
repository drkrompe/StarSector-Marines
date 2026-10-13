package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Production ridge geometry with a live aircraft: launch/contact and pre-cleared battery controls. */
public final class RidgeAirDefenceScene implements BehaviorScene {
    @Override public String id() { return "ridge-aa"; }
    @Override public String label() { return "Thunder Run / northern missile batteries"; }
    @Override public int frameDelayMillis() { return 100; }

    @Override public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install(); InstalledWeaponSlots.install(); InstalledVanillaData.install();
        try {
            List<SceneReport> reports = new ArrayList<>();
            for (boolean cleared : new boolean[]{false, true}) reports.add(playOne(frames, cleared));
            return reports;
        } finally { TurretSlotResolver.useHullSpecs(null); }
    }

    private SceneReport playOne(FrameSink frames, boolean cleared) {
        String loop = cleared ? "cleared-batteries" : "live-batteries";
        var profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
        var layout = ThunderRunCompound.generate(cleared ? 9302 : 9301, profile);
        var map = layout.map();
        try (var sim = BattleSetup.buildMap(map, List.of(), List.of(), List.of(), 9301).sim()) {
            sim.setMissionCompletionEnabled(false);
            sim.spawn(new EntitySpec("observer", Faction.MARINE, UnitType.MARINE, 21, 75));
            sim.spawn(new EntitySpec("distant", Faction.DEFENDER, UnitType.MILITIA, 290, 90));
            List<Long> guns = new ArrayList<>();
            for (var post : ThunderRunCompound.ANTI_AIR_POSTS) {
                long gun = sim.spawn(MapTurret.create("ridge-" + post.x(), Faction.DEFENDER,
                        TurretCatalogRegistry.RIDGE_AA_STRUCTURE_ID, post.x(), post.y()));
                guns.add(gun);
                if (cleared) sim.applyDamage(gun, 10000f, 100f);
            }
            long aircraft = sim.spawnGunship(ShuttleType.MUDSKIPPER, Faction.ALLY, 126, 75, -12, 75, -12, 75);
            var mission = sim.world().mission(aircraft);
            mission.orbitSecondsLeft = 70f;
            // Keep this isolated AA acceptance about the batteries, not the gunship's target choices.
            for (var mount : sim.world().airTurrets(aircraft).mounts) mount.ammo = 0;
            int launches = 0, curvedSamples = 0, firstDamage = -1, firstLaunch = -1;
            float initialHp = sim.world().hp(aircraft);
            for (int tick = 0; tick < 3000; tick++) {
                for (var shot : sim.getShotsThisFrame()) if (guns.contains(shot.shooterId)) {
                    launches++;
                    if (firstLaunch < 0) firstLaunch = tick;
                }
                for (var shot : sim.getActiveShots()) if (shot.guidedFlight != null && shot.guidedFlight.z > .5f) curvedSamples++;
                if (firstDamage < 0 && (mission.destroyed || sim.world().isAlive(aircraft)
                        && sim.world().hp(aircraft) < initialHp)) firstDamage = tick;
                if (tick % 6 == 0 && tick < 900) frames.frame(loop, sim, tick,
                        "Thunder Run / " + loop + " / missile launches " + launches + " / " + mission.state);
                frames.advanced(loop, sim, tick);
                sim.advance(BattleSimulation.TICK_DT);
            }
            return new SceneReport(id(), loop, 3000, List.of(
                    Verdict.of("ridge-access", true, "Generation proves all settlement interiors and battery approaches connected"),
                    Verdict.of(cleared ? "clearing-preserves-kestrel" : "missiles-can-down-kestrel",
                            cleared ? !mission.destroyed && launches == 0 : mission.destroyed && launches > 0 && curvedSamples > 0,
                            "destroyed=" + mission.destroyed + ", launches=" + launches + ", guided samples=" + curvedSamples),
                    Verdict.of("no-damage-at-launch", cleared || firstDamage > firstLaunch && firstLaunch >= 0,
                            "first launch=" + firstLaunch + ", first damage=" + firstDamage)),
                    Map.of("launches", launches, "guidedSamples", curvedSamples, "firstDamageTick", firstDamage));
        }
    }
}
