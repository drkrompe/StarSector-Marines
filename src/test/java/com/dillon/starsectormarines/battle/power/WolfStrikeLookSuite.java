package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wolf Strike in the look preset (`command-powers-nouns.md`, Wolf Strike): two fly-bys along a 60-cell line over an
 * enemy column on open ground, two breakthrough tanks, two militia technicals, a heavy APC and two rifle squads strung
 * along it, with a flak position beside the line, a missile tower off it and, before the second run, a Rav flier pack
 * over it. The first run flies west to east; once it has climbed out a second comes back east to west. Play zoom over
 * the whole line, and a camera following the Wolf. Logs the column's state every second, so the damage rate reads as
 * numbers beside the picture.
 */
public class WolfStrikeLookSuite implements SnapshotSuite {

    private static final int W = 90, H = 50;
    private static final float LINE_Y = 25f, LINE_WEST = 15f, LINE_EAST = 75f;
    private static final int FIRST_RUN_TICK = 15, SECOND_RUN_TICK = 30 * 16;
    private static final int SECONDS = 32, EVERY_TICKS = 2;
    /** A Rav flier pack let loose over the line two seconds before the second run, for the Wolf's point defence. */
    private static final int PACK_TICK = SECOND_RUN_TICK - 60, PACK_SIZE = 8;

    @Override public String id() { return "wolf-strike-look"; }

    @Override public String label() { return "Look: two Wolf Strike fly-bys over an enemy armour and infantry column"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        // The run on the air now, for the camera that follows it; it holds where the last one was last seen.
        Warship[] flying = new Warship[1];
        float[] lastSeen = {Float.NaN, Float.NaN};
        LookReview.Shot shot = new LookReview.Shot("wolf-strike", LookGround.verdant(), W, H, placement -> {
            int x0 = placement.site().x0(), y0 = placement.site().y0();
            SceneBuilder builder = placement.holdouts(placement.builder());
            int[] squadA = cell(placement, 34f, 26f), squadB = cell(placement, 60f, 23f);
            builder.squad("militia-west").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6)
                    .at(squadA[0] - x0, squadA[1] - y0).primary("weapon.field-rifle").stationary().done();
            builder.squad("militia-east").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6)
                    .at(squadB[0] - x0, squadB[1] - y0).primary("weapon.field-rifle").stationary().done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            Map<String, Long> column = new LinkedHashMap<>();
            column.put("tank-1", vehicle(sim, placement, VehicleType.BREAKTHROUGH_TANK, 28f, 21f));
            column.put("tank-2", vehicle(sim, placement, VehicleType.BREAKTHROUGH_TANK, 54f, 29f));
            column.put("technical-1", vehicle(sim, placement, VehicleType.MILITIA_TECHNICAL, 42f, 29f));
            column.put("technical-2", vehicle(sim, placement, VehicleType.MILITIA_TECHNICAL, 67f, 21f));
            column.put("heavy-apc", vehicle(sim, placement, VehicleType.HEAVY_APC, 48f, 20f));
            // The column's anti-air: a flak position beside the line and a missile tower further off it.
            int[] flak = cell(placement, 45f, 31f), tower = cell(placement, 62f, 16f);
            sim.spawn(MapTurret.create("flak", Faction.DEFENDER, "structure.turret-anti-air-flak", flak[0], flak[1]));
            sim.spawn(MapTurret.create("tower", Faction.DEFENDER, "structure.aa-locust", tower[0], tower[1]));
            List<Long> infantry = new ArrayList<>();
            for (long id : world.members("militia-west")) infantry.add(id);
            for (long id : world.members("militia-east")) infantry.add(id);
            placement.subject("column", column.get("heavy-apc"));
            float wx = placement.site().x(LINE_WEST), ex = placement.site().x(LINE_EAST), ly = placement.site().y(LINE_Y);
            placement.beforeTick(s -> {
                long tick = s.getSimTickIndex();
                Warship wolf = null;
                if (tick == PACK_TICK) s.swarmFliers().release(s, HarrierLook.DEFAULT, wx + (ex - wx) * 0.6f, ly, PACK_SIZE, 0L);
                if (tick == FIRST_RUN_TICK) wolf = WolfStrike.fly(wx, ly, ex, ly, s);
                if (tick == SECOND_RUN_TICK) wolf = WolfStrike.fly(ex, ly - 1f, wx, ly - 1f, s);
                if ((tick == FIRST_RUN_TICK || tick == SECOND_RUN_TICK) && wolf == null) {
                    System.out.printf("    [wolf-strike] t=%.1fs the run was refused%n", tick / 30f);
                }
                if (wolf != null) {
                    flying[0] = wolf;
                    System.out.printf("    [wolf-strike] t=%.1fs run from %.0f,%.0f%n", tick / 30f, wolf.x, wolf.y);
                }
                if (tick % 30 == 0) log(s, tick, column, infantry, flying[0]);
            });
            return sim;
        }, placement -> List.of(
                LookReview.View.play(placement.site().x(45f), placement.site().y(LINE_Y)),
                new LookReview.View("wolf", s -> {
                    if (flying[0] != null && !flying[0].departed()) {
                        lastSeen[0] = flying[0].x;
                        lastSeen[1] = flying[0].y;
                    }
                    if (Float.isNaN(lastSeen[0])) {
                        return new float[]{placement.site().x(LINE_WEST), placement.site().y(LINE_Y), 40f};
                    }
                    return new float[]{lastSeen[0], lastSeen[1], 40f};
                })),
                30 * SECONDS, EVERY_TICKS, 4);
        return LookReview.record(context.modRoot(), shot);
    }

    private static long vehicle(BattleSimulation sim, LookReview.Placement placement, VehicleType type, float x, float y) {
        float[] at = placement.openNear(x, y);
        return sim.convoy().spawn(type, Faction.DEFENDER, VehicleMission.deployed(at[0], at[1]));
    }

    /** Each vehicle's hull share, how many riflemen still stand and the Wolf's own hull, once a second. */
    private static void log(BattleSimulation sim, long tick, Map<String, Long> column, List<Long> infantry,
                            Warship wolf) {
        StringBuilder line = new StringBuilder(String.format("    [wolf-strike] t=%4.1fs", tick / 30f));
        for (Map.Entry<String, Long> e : column.entrySet()) {
            long id = e.getValue();
            float share = sim.world().isAlive(id) ? sim.world().hp(id) / Math.max(1f, sim.world().maxHp(id)) : 0f;
            line.append(String.format("  %s %3.0f%%", e.getKey(), share * 100f));
        }
        int standing = 0;
        for (long id : infantry) if (sim.world().isAlive(id)) standing++;
        line.append("  riflemen ").append(standing).append('/').append(infantry.size());
        int fliers = 0;
        for (HarrierPack pack : sim.swarmFliers().packs()) fliers += pack.members.size();
        if (fliers > 0) line.append("  fliers ").append(fliers);
        if (wolf != null) {
            line.append(String.format("  wolf %3.0f%%%s", wolf.hullFraction() * 100f,
                    wolf.destroyed() ? " down" : wolf.inGroundAaReach() ? " low" : ""));
        }
        System.out.println(line);
    }

    private static int[] cell(LookReview.Placement p, float x, float y) {
        float[] at = p.openNear(x, y);
        return new int[]{(int) at[0], (int) at[1]};
    }
}
