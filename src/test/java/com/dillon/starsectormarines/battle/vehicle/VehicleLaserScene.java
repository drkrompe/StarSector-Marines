package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The large vehicle lasers on the ground (owner, 2026-11-05: "its laser should be able to do decent damage to walls and
 * have an AoE on areas hit; its laser should be able to light fires with sustained damage to a flammable area"). A
 * driven laser tank on bare ground holds its fire on a patch of grass, then on a light wall, as a player would, once
 * with its PL-3 and once with a PL-3 (Sweep) fitted.
 *
 * <p>Verdicts are mechanics: the laser fires, sustained fire on the grass lights it, and its pulses wear the wall
 * they stop at. How long the grass took, how much wall it took and how many cells burnt are readings.
 */
public final class VehicleLaserScene implements BehaviorScene {

    private static final int WIDTH = 48;
    private static final int HEIGHT = 30;
    private static final int GRASS_TICKS = 30 * 12;
    private static final int WALL_TICKS = 30 * 12;
    private static final long SEED = 20261105L;

    private static final int TANK_X = 8;
    private static final int TANK_Y = 15;
    /** The grass patch aimed at, upfield of the tank and away from the wall. */
    private static final float GRASS_X = 24.5f, GRASS_Y = 24.5f;
    private static final int WALL_X = 26;
    private static final int WALL_Y0 = 12, WALL_Y1 = 18;
    private static final int WALL_HP = 100;

    @Override public String id() { return "vehicle-laser"; }

    @Override
    public String label() { return "Large vehicle lasers light grass under sustained fire and wear walls with a small burst"; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(
                loop(frames, "pl-3-fitted", MechWeaponComponent.PL_3),
                loop(frames, "pl-3-sweep-fitted", MechWeaponComponent.PL_3_SWEEP));
    }

    private SceneReport loop(FrameSink frames, String loopId, MechWeaponComponent gun) throws Exception {
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                // Grass only round the patch aimed at, so a fire there cannot reach the tank or the wall.
                boolean grass = Math.abs(x + .5f - GRASS_X) < 5f && Math.abs(y + .5f - GRASS_Y) < 4f;
                topology.setGroundKind(x, y, grass ? CellTopology.GroundKind.GRASS : CellTopology.GroundKind.DIRT);
            }
        }
        for (int y = WALL_Y0; y <= WALL_Y1; y++) {
            grid.setWalkable(WALL_X, y, false);
            grid.setWallHp(WALL_X, y, WALL_HP);
            topology.setWall(WALL_X, y, true);
        }
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false);
        // A side to fight, out of the laser's reach, so the battle stands up.
        builder.unit("far", Faction.DEFENDER, UnitType.MILITIA, WIDTH - 1, 0);
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            float tx = TANK_X + .5f, ty = TANK_Y + .5f;
            long tank = gun == null
                    ? sim.convoy().spawn(VehicleType.LASER_TANK, Faction.MARINE, VehicleMission.deployed(tx, ty))
                    : sim.convoy().spawn(VehicleType.LASER_TANK, VehiclePlating.FACTORY, VehicleEngine.STOCK, gun,
                            Faction.MARINE, VehicleMission.deployed(tx, ty));
            sim.convoy().body(tank).teleport(tx, ty, 0f);
            boolean entered = sim.directControl().enter(tank);
            float wallAimX = WALL_X, wallAimY = (WALL_Y0 + WALL_Y1) / 2 + .5f;
            int[] rounds = {0, 0};
            int[] firstLit = {-1};
            String[] weaponId = {""};
            SceneRun.of(world).observe((s, tick) -> {
                boolean onGrass = tick < GRASS_TICKS;
                s.directControl().submit(onGrass
                        ? new ManualIntent(0f, 0f, GRASS_X, GRASS_Y, true)
                        : new ManualIntent(0f, 0f, wallAimX, wallAimY, true));
                for (ShotEvent shot : s.getShotsThisFrame()) {
                    if (shot.shooterId != tank) continue;
                    rounds[onGrass ? 0 : 1]++;
                    if (shot.weaponDef() != null) weaponId[0] = shot.weaponDef().id;
                }
                FireField f = s.fireFieldIfPresent();
                if (firstLit[0] < 0 && f != null && f.burntCount() + f.burningCount() > 0) firstLit[0] = tick;
            }).frames(frames, loopId, 15, tick -> String.format(Locale.ROOT, "%s  t=%.1fs", loopId, tick / 30f))
                    .run(GRASS_TICKS + WALL_TICKS);

            int wallLost = 0, wallsBroken = 0;
            for (int y = WALL_Y0; y <= WALL_Y1; y++) {
                int hp = topology.isWall(WALL_X, y) ? grid.getWallHp(WALL_X, y) : 0;
                wallLost += WALL_HP - hp;
                if (hp <= 0) wallsBroken++;
            }
            FireField f = sim.fireFieldIfPresent();
            long cellsBurnt = f == null ? 0 : f.burntCount() + f.burningCount();

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("laser-fires", entered && rounds[0] > 0 && rounds[1] > 0,
                    "driven " + entered + ", " + rounds[0] + " pulses of " + weaponId[0] + " at the grass, "
                            + rounds[1] + " at the wall"));
            verdicts.add(Verdict.of("sustained-fire-lights-grass", cellsBurnt > 0,
                    cellsBurnt + " grass cells caught, first at tick " + firstLit[0]));
            verdicts.add(Verdict.of("pulses-wear-the-wall", wallLost > 0,
                    wallLost + " wall hit points taken, " + wallsBroken + " wall cells broken"));
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("pulsesAtGrass", rounds[0]);
            m.put("pulsesAtWall", rounds[1]);
            m.put("firstLitSeconds", firstLit[0] < 0 ? -1f : firstLit[0] / 30f);
            m.put("cellsBurnt", cellsBurnt);
            m.put("wallHpLost", wallLost);
            m.put("wallCellsBroken", wallsBroken);
            return new SceneReport(id(), loopId, GRASS_TICKS + WALL_TICKS, verdicts, m);
        }
    }
}
