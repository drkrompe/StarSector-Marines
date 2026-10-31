package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.power.SpawnInOnslaught;
import com.dillon.starsectormarines.battle.power.WolfStrike;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
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
 * A frigate's crash beside a capital's, in the look preset: a Wolf and then an Onslaught, each at the reduced body its
 * command power flies ({@link SpawnInOnslaught#PRESENCE}), hangs over an enemy column of tanks, technicals, an APC and
 * two rifle squads, is shot to nothing a second in while sliding east, and comes down on it. The Onslaught's secondaries
 * go off as it falls and ripple down its keel after it strikes, and it burns along its length at rest
 * ({@link WarshipWreck.Weight}). Logs the column's state every second, so the two crashes' damage reads as numbers
 * beside the pictures.
 */
public class WarshipCrashLookSuite implements SnapshotSuite {

    private static final int W = 80, H = 50;
    private static final float CRASH_X = 34f, CRASH_Y = 25f;
    private static final int KILL_TICK = 30, SECONDS = 14, EVERY_TICKS = 2;
    /** The way the hull is moving when it is lost, cells per second east, so it skids down the column. */
    private static final float SLIDE_SPEED = 5f;

    @Override public String id() { return "warship-crash-look"; }

    @Override public String label() { return "Look: a Wolf and an Onslaught shot down onto an enemy column"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SnapshotArtifact> out = new ArrayList<>();
        out.addAll(LookReview.record(context.modRoot(), shot("wolf-crash", WolfStrike.source(), Faction.ALLY)));
        out.addAll(LookReview.record(context.modRoot(),
                shot("onslaught-crash", ShipSupportSource.stockFor("onslaught"), Faction.MARINE)));
        return out;
    }

    private static LookReview.Shot shot(String name, ShipSupportSource source, Faction side) {
        return new LookReview.Shot(name, LookGround.verdant(), W, H, placement -> {
            int x0 = placement.site().x0(), y0 = placement.site().y0();
            SceneBuilder builder = placement.holdouts(placement.builder());
            int[] squadA = cell(placement, 30f, 28f), squadB = cell(placement, 46f, 23f);
            builder.squad("militia-west").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6)
                    .at(squadA[0] - x0, squadA[1] - y0).primary("weapon.field-rifle").stationary().done();
            builder.squad("militia-east").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6)
                    .at(squadB[0] - x0, squadB[1] - y0).primary("weapon.field-rifle").stationary().done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            Map<String, Long> column = new LinkedHashMap<>();
            column.put("tank-1", vehicle(sim, placement, VehicleType.BREAKTHROUGH_TANK, 36f, 21f));
            column.put("tank-2", vehicle(sim, placement, VehicleType.BREAKTHROUGH_TANK, 52f, 28f));
            column.put("technical-1", vehicle(sim, placement, VehicleType.MILITIA_TECHNICAL, 40f, 29f));
            column.put("technical-2", vehicle(sim, placement, VehicleType.MILITIA_TECHNICAL, 58f, 22f));
            column.put("heavy-apc", vehicle(sim, placement, VehicleType.HEAVY_APC, 46f, 26f));
            List<Long> infantry = new ArrayList<>();
            for (long id : world.members("militia-west")) infantry.add(id);
            for (long id : world.members("militia-east")) infantry.add(id);
            float sx = placement.site().x(CRASH_X), sy = placement.site().y(CRASH_Y);
            Warship ship;
            try {
                ship = Warship.fit(source, side, sx, sy, 0f, SpawnInOnslaught.PRESENCE);
            } catch (Exception e) {
                throw new IllegalStateException("the " + name + " hull could not be fitted", e);
            }
            ship.stationTo(sx, sy);
            sim.addWarship(ship);
            placement.subject("column", column.get("heavy-apc"));
            placement.beforeTick(s -> {
                long tick = s.getSimTickIndex();
                if (tick == KILL_TICK) {
                    ship.velocityX = SLIDE_SPEED;
                    ship.velocityY = 0f;
                    for (int i = 0; i < 1000 && !ship.destroyed(); i++) ship.takeDamage(100_000f, 100f);
                    System.out.printf("    [%s] t=%.1fs the %s is lost at %.0f,%.0f%n",
                            name, tick / 30f, ship.hullId, ship.x, ship.y);
                }
                if (tick % 30 == 0) log(name, s, tick, column, infantry);
            });
            return sim;
        }, placement -> List.of(
                LookReview.View.play(placement.site().x(CRASH_X + 8f), placement.site().y(CRASH_Y)),
                LookReview.View.close("close", placement.site().x(CRASH_X + 6f), placement.site().y(CRASH_Y), 40f)),
                30 * SECONDS, EVERY_TICKS, 4);
    }

    private static long vehicle(BattleSimulation sim, LookReview.Placement placement, VehicleType type, float x, float y) {
        float[] at = placement.openNear(x, y);
        return sim.convoy().spawn(type, Faction.DEFENDER, VehicleMission.deployed(at[0], at[1]));
    }

    /** Each vehicle's hull share and how many riflemen still stand, once a second. */
    private static void log(String name, BattleSimulation sim, long tick, Map<String, Long> column, List<Long> infantry) {
        StringBuilder line = new StringBuilder(String.format("    [%s] t=%4.1fs", name, tick / 30f));
        for (Map.Entry<String, Long> e : column.entrySet()) {
            long id = e.getValue();
            float share = sim.world().isAlive(id) ? sim.world().hp(id) / Math.max(1f, sim.world().maxHp(id)) : 0f;
            line.append(String.format("  %s %3.0f%%", e.getKey(), share * 100f));
        }
        int standing = 0;
        for (long id : infantry) if (sim.world().isAlive(id)) standing++;
        line.append("  riflemen ").append(standing).append('/').append(infantry.size());
        System.out.println(line);
    }

    private static int[] cell(LookReview.Placement p, float x, float y) {
        float[] at = p.openNear(x, y);
        return new int[]{(int) at[0], (int) at[1]};
    }
}
