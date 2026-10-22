package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bore left alone creeps toward a Marine gun with a wall behind it, and the creep grows a ground colony in reach
 * of the gun ({@link GroundColonies}): spines from below for the gun and the wall round it. One loop per case:
 * <ul>
 *   <li>{@code left-alone}: the bore stands the whole run.</li>
 *   <li>{@code collapsed}: the bore is destroyed once a ground colony stands; the colony withers with it.</li>
 * </ul>
 * Verdicts are mechanics: a ground colony roots on living creep within reach of a hostile gun, never over its bore's
 * cap, strikes when the gun stands in its reach, and dies with its bore. When the gun falls and how much wall is lost
 * are metrics.
 */
public final class GroundColonyScene implements BehaviorScene {

    static final int W = 96, H = 48;
    static final int BORE_X = 24, BORE_Y = 24;
    static final int GUN_X = 46, GUN_Y = 24;
    private static final String GUN = "structure.turret-vulcan";
    private static final int TICKS = 7200;
    private static final int FRAME_EVERY = 150;

    @Override public String id() { return "ground-colony"; }

    @Override public String label() {
        return "A bore's creep grows a ground colony in reach of a Marine gun, which spines it from below; it withers with the bore";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(playOne("left-alone", false, frames), playOne("collapsed", true, frames));
    }

    private SceneReport playOne(String loop, boolean kill, FrameSink frames) throws Exception {
        SceneWorld world = SceneBuilder.openGround(W, H).seed(20261021L).missionCompletion(false)
                .wall(GUN_X + 2, GUN_Y - 3, GUN_X + 2, GUN_Y + 3).build();
        try (BattleSimulation sim = world.sim()) {
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, BORE_X, BORE_Y);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            sim.swarmBores().feed(sim, bore, 9f);
            long gun = sim.spawn(MapTurret.create("Marine Vulcan", Faction.MARINE, GUN, GUN_X, GUN_Y));
            Watch watch = new Watch(bore, gun, kill);
            watch.wallAtStart = wallCells(sim);
            SceneRun.of(world).observe(watch)
                    .frames(frames, loop, FRAME_EVERY, tick -> loop + " / " + bore.tier() + " bore / "
                            + watch.living(sim) + " ground colonies / gun " + (sim.resolveUnit(gun) != 0L ? "standing" : "down")
                            + " / tick " + tick)
                    .run(TICKS);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("ground-colony-roots-on-living-creep", watch.offCreep == 0,
                    watch.offCreep + " of " + watch.rooted + " rooted off living creep"));
            verdicts.add(Verdict.of("ground-colony-roots-in-reach-of-a-gun", watch.outOfReach == 0,
                    watch.outOfReach + " of " + watch.rooted + " rooted with no hostile gun in reach"));
            verdicts.add(Verdict.of("never-over-the-cap", watch.mostLiving <= GroundColonies.capFor(bore.tier()),
                    watch.mostLiving + " living at most against a cap of " + GroundColonies.capFor(bore.tier())));
            verdicts.add(Verdict.of("strikes-the-gun-in-reach", watch.ticksGunInReach < 120 || watch.strikes > 0,
                    watch.ticksGunInReach < 120
                            ? "no colony stood with the gun in reach for four seconds; nothing to strike"
                            : watch.strikes + " spine strikes over " + watch.ticksGunInReach + " ticks with the gun in reach"));
            if (kill) {
                verdicts.add(Verdict.of("ground-colonies-wither-with-the-bore",
                        watch.livingAtKill == 0 || watch.livingAfterKill == 0,
                        watch.livingAtKill == 0 ? "no ground colony stood when the bore fell; nothing to wither"
                                : watch.livingAfterKill + " of " + watch.livingAtKill + " still standing two seconds after"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("groundColonies", watch.rooted);
            metrics.put("firstRootTick", watch.firstTick);
            metrics.put("strikes", watch.strikes);
            metrics.put("gunDownTick", watch.gunDownTick);
            metrics.put("wallCellsLost", watch.wallAtStart - wallCells(sim));
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static int wallCells(BattleSimulation sim) {
        int n = 0;
        for (int y = GUN_Y - 3; y <= GUN_Y + 3; y++) if (sim.getTopology().isWall(GUN_X + 2, y)) n++;
        return n;
    }

    private static final class Watch implements TickObserver {
        private final SwarmBore bore;
        private final long gun;
        private final boolean kill;
        int seen, rooted, offCreep, outOfReach, mostLiving, strikes, ticksGunInReach;
        int firstTick = -1, gunDownTick = -1, killTick = -1;
        int livingAtKill, livingAfterKill = -1;
        int wallAtStart;
        private final Map<Long, Float> lastRecoil = new LinkedHashMap<>();

        Watch(SwarmBore bore, long gun, boolean kill) {
            this.bore = bore;
            this.gun = gun;
            this.kill = kill;
        }

        int living(BattleSimulation sim) {
            int n = 0;
            for (long id : sim.groundColonies().of(bore.entityId())) if (sim.resolveUnit(id) != 0L) n++;
            return n;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            List<Long> grown = sim.groundColonies().of(bore.entityId());
            float reach = GroundColonies.reach();
            boolean gunUp = sim.resolveUnit(gun) != 0L;
            for (; seen < grown.size(); seen++) {
                long id = grown.get(seen);
                rooted++;
                if (firstTick < 0) firstTick = tick;
                CreepField field = sim.creepFieldIfPresent();
                if (field == null || !field.isCreeped(sim.world().cellX(id), sim.world().cellY(id))) offCreep++;
                if (!gunUp || Math.hypot(sim.world().x(id) - sim.world().x(gun), sim.world().y(id) - sim.world().y(gun)) > reach) {
                    outOfReach++;
                }
            }
            for (long id : grown) {
                if (sim.resolveUnit(id) == 0L) continue;
                float recoil = sim.turretState().recoilTimer(id);
                Float before = lastRecoil.put(id, recoil);
                if (before != null && recoil < before) strikes++;
                if (gunUp && Math.hypot(sim.world().x(id) - sim.world().x(gun), sim.world().y(id) - sim.world().y(gun)) <= reach) {
                    ticksGunInReach++;
                }
            }
            if (!gunUp && gunDownTick < 0) gunDownTick = tick;
            mostLiving = Math.max(mostLiving, living(sim));
            if (kill && killTick < 0 && rooted > 0 && tick >= firstTick + 600) {
                killTick = tick;
                livingAtKill = living(sim);
                sim.applyDamage(bore.entityId(), 1_000_000f, 100f);
            }
            if (kill && killTick >= 0 && tick == killTick + 60) livingAfterKill = living(sim);
        }
    }
}
