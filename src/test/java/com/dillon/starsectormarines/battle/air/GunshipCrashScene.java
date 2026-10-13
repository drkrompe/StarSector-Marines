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
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
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
    @Override public int frameDelayMillis() { return 100; }

    @Override public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        try {
            for (String loop : List.of("live-flak", "cleared-flak", "impact", "momentum", "hover")) reports.add(playOne(loop, frames));
            reports.add(demolition(frames));
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
        return reports;
    }

    private SceneReport demolition(FrameSink frames) {
        var builder = SceneBuilder.openGround(96, 54).missionCompletion(false);
        builder.unit("roofed", Faction.MARINE, UnitType.MARINE, 40, 27);
        builder.unit("skid", Faction.DEFENDER, UnitType.MARINE, 57, 27);
        builder.unit("outside", Faction.DEFENDER, UnitType.MARINE, 75, 43);
        var world = builder.build();
        try (var sim = world.sim()) {
            sim.random().setSeed(20261013L);
            var grid = sim.getGrid();
            var topology = sim.getTopology();
            for (int y = 0; y < 54; y++) for (int x = 0; x < 96; x++) topology.setGroundKind(x, y, GroundKind.GRASS);
            // Roofed shed at first contact and a second wall well beyond the initial blast.
            for (int y = 23; y <= 31; y++) for (int x = 36; x <= 45; x++) {
                topology.setBuildingId(x, y, 1);
                topology.setGroundKind(x, y, GroundKind.INDOOR);
                if (x == 36 || x == 45 || y == 23 || y == 31) {
                    topology.setWall(x, y, true);
                    grid.setWalkable(x, y, false);
                    grid.setSeeThrough(x, y, false);
                    grid.setWallHp(x, y, 200);
                }
            }
            for (int y = 25; y <= 29; y++) {
                topology.setWall(54, y, true);
                grid.setWalkable(54, y, false);
                grid.setSeeThrough(54, y, false);
                grid.setWallHp(54, y, 200);
            }
            var rock = MineralFormation.BOULDERS;
            for (int y = 0; y < rock.height(); y++) for (int x = 0; x < rock.width(); x++) {
                if (!rock.blocks(x, y, 0)) continue;
                grid.setTag(48 + x, 26 + y, CellTag.NATURAL_SOLID, true);
                grid.setWalkable(48 + x, 26 + y, false);
                grid.setSeeThrough(48 + x, 26 + y, false);
            }
            sim.addDoodad(new Doodad(48, 26, TileRegistry.installed().doodad(rock.id())));
            // A permanent mountain cell shares the impact area, but has no destructible prop owner.
            grid.setTag(50, 31, CellTag.NATURAL_SOLID, true);
            grid.setWalkable(50, 31, false);
            grid.setSeeThrough(50, 31, false);
            long craft = sim.spawnGunship(ShuttleType.MUDSKIPPER, Faction.ALLY, 40, 27, -12, 27, -12, 27);
            for (var mount : sim.world().airTurrets(craft).mounts) mount.ammo = 0;
            var mission = sim.world().mission(craft);
            boolean[] killed = {false};
            var run = SceneRun.of(world).observe((s, tick) -> {
                if (!killed[0] && mission.state == ShuttleState.ORBIT) {
                    var body = s.world().kinematics(craft);
                    body.x = 40.5f - 18f * AirCrashMotion.FALL_SECONDS;
                    body.y = 27.5f; body.vx = 18f; body.vy = 0f;
                    s.applyAimedDamage(craft, 0L, 10000f, 100f);
                    killed[0] = true;
                }
                if (tick % 3 == 0 && tick < 360) frames.frame("demolition", s, tick, "Crash / roofed shed and downstream wall");
                frames.advanced("demolition", s, tick);
            });
            run.run(600);
            return new SceneReport(id(), "demolition", run.tick(), List.of(
                    Verdict.of("impact-demolishes-shed", !topology.isWall(36, 27) && !topology.isWall(45, 27)
                            && topology.isRoofDestroyed(40, 27), "walls breached and roof removed"),
                    Verdict.of("roof-does-not-spare-contact-occupant", !sim.world().isAlive(world.unit("roofed")), "roofed occupant"),
                    Verdict.of("skid-demolishes-downstream-wall", !topology.isWall(54, 27), "wall beyond initial blast"),
                    Verdict.of("skid-damages-downstream-occupant", !sim.world().isAlive(world.unit("skid")), "occupant beyond initial blast"),
                    Verdict.of("skid-shatters-natural-prop", sim.getDoodads().stream().anyMatch(d ->
                            "formation.boulders-destroyed".equals(d.definitionId())), "boulders in skid corridor"),
                    Verdict.of("mountain-stays-impassable", !grid.isWalkable(50, 31)
                            && grid.hasTag(50, 31, CellTag.NATURAL_SOLID), "permanent terrain survives contact area"),
                    Verdict.of("ground-retains-damage", topology.hasTag(50, 27, Tag.GROUND_DAMAGED), "scar in skid corridor"),
                    Verdict.of("outside-survives", sim.world().isAlive(world.unit("outside")), "outside crash corridor")), Map.of());
        }
    }

    private SceneReport playOne(String loop, FrameSink frames) {
        boolean impact = loop.equals("impact");
        boolean hover = loop.equals("hover");
        boolean momentum = loop.equals("momentum") || hover;
        var builder = SceneBuilder.openGround(80, 54).missionCompletion(false);
        builder.squad("company").faction(Faction.MARINE).size(1).at(impact ? 39 : 7, 27)
                .primary("weapon.smg").stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, impact ? 39 : 7, 27)).done();
        builder.squad("enemy").faction(Faction.DEFENDER).size(1).at(impact ? 42 : 72, 27)
                .primary("weapon.smg").stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, impact ? 42 : 72, 27)).done();
        var world = builder.build();
        try (var sim = world.sim()) {
            sim.random().setSeed(20261013L);
            List<Long> guns = new ArrayList<>();
            if (!impact && !momentum) for (int y : List.of(17, 37)) {
                long gun = sim.spawn(MapTurret.create("flak-" + y, Faction.DEFENDER,
                        TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID, 48, y));
                guns.add(gun);
                if (loop.equals("cleared-flak")) sim.applyDamage(gun, 10000f, 100f);
            }
            long craft = sim.spawnGunship(ShuttleType.MUDSKIPPER, Faction.ALLY, 40, 27, -12, 27, -12, 27);
            var mission = sim.world().mission(craft);
            if (impact || momentum) for (var mount : sim.world().airTurrets(craft).mounts) mount.ammo = 0;
            mission.orbitSecondsLeft = 100f;
            var craftBody = sim.world().kinematics(craft);
            float[] start = new float[3], contact = new float[2];
            boolean[] touched = {false};
            int[] impactBursts = {0};
            int[] firstFall = {-1}, fallTicks = {0}, settledTick = {-1}, shots = {0};
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
                        // Aim this collateral fixture at its named contact after instability drift.
                        body.x -= .5f * body.ax * AirCrashMotion.FALL_SECONDS * AirCrashMotion.FALL_SECONDS;
                        body.y -= .5f * body.ay * AirCrashMotion.FALL_SECONDS * AirCrashMotion.FALL_SECONDS;
                        killed[0] = true;
                    }
                }
                if (momentum && !killed[0] && mission.state == ShuttleState.ORBIT) {
                    if (hover) { craftBody.vx = 0f; craftBody.vy = 0f; }
                    s.applyAimedDamage(craft, 0L, 10000f, 100f);
                    killed[0] = true;
                }
                for (var shot : s.getShotsThisFrame()) if (guns.contains(shot.shooterId)) shots[0]++;
                if (momentum) impactBursts[0] += s.getRollingBlastsThisFrame().size();
                if (mission.state == ShuttleState.CRASHING) {
                    if (firstFall[0] < 0) {
                        firstFall[0] = tick;
                        start[0] = craftBody.x; start[1] = craftBody.y; start[2] = craftBody.speed();
                    }
                    if (mission.crashSeconds < AirCrashMotion.FALL_SECONDS) fallTicks[0]++;
                    else if (!touched[0]) {
                        touched[0] = true;
                        contact[0] = craftBody.x; contact[1] = craftBody.y;
                    }
                }
                if (!s.getAirfieldService().groundWrecks().isEmpty() && settledTick[0] < 0) {
                    settledTick[0] = tick;
                    hpAfter[0] = s.world().isAlive(friend) ? s.world().hp(friend) : 0f;
                    hpAfter[1] = s.world().isAlive(enemy) ? s.world().hp(enemy) : 0f;
                }
                if ((tick % (momentum ? 3 : impact ? 6 : 45) == 0 && tick < (momentum ? 330 : impact ? 540 : 3300)) || (impact && tick == 5999))
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
                verdicts.add(Verdict.of("falls-before-contact", fallTicks[0] >= 45 && fallTicks[0] <= 50,
                        "fall ticks=" + fallTicks[0] + ", settled=" + settledTick[0]));
                verdicts.add(Verdict.of("permanent-traversable-wreck", wrecks == 1 && sim.getGrid().isWalkable(
                        sim.getAirfieldService().groundWrecks().get(0).cellX(), sim.getAirfieldService().groundWrecks().get(0).cellY()),
                        "wrecks after " + (impact ? 200 : 140) + " seconds=" + wrecks));
                if (wrecks == 1) {
                    var wreck = sim.getAirfieldService().groundWrecks().get(0);
                    verdicts.add(Verdict.of("wreck-slows-ground-travel", sim.getGrid().groundSpeedMultiplier(
                            wreck.cellX() + .5f, wreck.cellY() + .5f) == .5f, "half-speed crash debris"));
                }
                if (impact) verdicts.add(Verdict.of("impact-hurts-both-sides", hpBefore[0] > hpAfter[0] && hpBefore[1] > hpAfter[1],
                        "friendly " + hpBefore[0] + " -> " + hpAfter[0] + "; enemy " + hpBefore[1] + " -> " + hpAfter[1]));
                else if (momentum) {
                    float airTravel = (float) Math.hypot(contact[0] - start[0], contact[1] - start[1]);
                    float skid = (float) Math.hypot(craftBody.x - contact[0], craftBody.y - contact[1]);
                    verdicts.add(Verdict.of(hover ? "hover-loss-builds-momentum" : "keeps-flight-momentum", touched[0] && (hover ? start[2] == 0f && airTravel > 5f : start[2] > 5f && airTravel >= start[2] * 1.5f),
                            "initial speed=" + start[2] + ", fall travel=" + airTravel));
                    verdicts.add(Verdict.of("skids-to-rest", skid > .5f && craftBody.speed() == 0f,
                            "ground travel=" + skid + ", final speed=" + craftBody.speed()));
                    verdicts.add(Verdict.of("impact-spreads-fireballs", impactBursts[0] >= 20,
                            "rolling fireballs throughout skid=" + impactBursts[0]));
                }
                else verdicts.add(Verdict.of("real-flak-shootdown", shots[0] > 0 && mission.destroyed,
                        "visible flak shots=" + shots[0] + ", destroyed=" + mission.destroyed));
            }
            return new SceneReport(id(), loop, run.tick(), verdicts,
                    Map.of("firstFall", firstFall[0], "settledTick", settledTick[0], "fallTicks", fallTicks[0], "flakShots", shots[0], "wrecks", wrecks));
        }
    }
}
