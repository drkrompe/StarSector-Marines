package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.CreepVegetationSnapshotSuite;
import com.dillon.starsectormarines.battle.evacuation.BoreTier;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.evacuation.SwarmStrains;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A dense battle stood up directly, for render cost only: no balance or gameplay meaning.
 *
 * <p>{@link RenderBudgetEvidence} prices most of its maps after a playout, which is honest about what a frame
 * holds and costs minutes of simulation per map. This puts the same things on screen without one: the verdant
 * 400x240 colony at tick zero, bores and a mother bore fed to their tiers so their spore colonies stand, creep
 * matured over them at once, a mixed crowd of every body kind round one point with a share of it killed where it
 * stands, a fire lit across the middle of it, and only {@link #SETTLE_TICKS} of battle so the dead have fallen and
 * shots are in the air. Everything is placed from {@link #SEED}, so two stand-ups are the same world.
 *
 * <p>The fire is lit and then stepped on its own field, as the burning colony is: the field has no setter for a
 * cell's state, and heat pulses with field steps after them are the nearest thing to setting the cells. It is
 * built to The Burn's shape rather than a young disk's. The centre is chosen for trees and buildings, because
 * only a crown or a wall burns past intensity 200, where {@code FireRenderSystem} throws an ember (a solid rect
 * between flame quads). The main burn is stepped {@link #FIRE_STEPS} steps: its grass burns out and leaves
 * scorched ground, its brush is in its tail, and its crowns and walls are at full heat and smoking. A second
 * patch lit {@link #FRESH_STEPS} steps before the end adds young flames at the ramp.
 */
final class RenderStressScene {

    static final long SEED = 0x57E55L;

    /** Battle ticks played after the bodies are placed: enough for the killed to fall and the living to open fire. */
    static final int SETTLE_TICKS = 45;

    /**
     * Fire-field steps after the main pulse: past a grass load (30) so the grass is burnt out, past the ramp (20)
     * so crowns and walls burn at full heat, inside a crown's load (200) so they still do.
     */
    static final int FIRE_STEPS = 45;

    /** Steps the second, fresh patch burns before the frame: still on its ramp. */
    static final int FRESH_STEPS = 8;

    /** Radius, in cells, of the main burn: most of the mid framing's height, so the lane is mostly fire. */
    static final float FIRE_RADIUS = 20f;

    /** Radius, in cells, of the fresh patch. */
    static final float FRESH_RADIUS = 12f;

    /** Radius, in cells, of the ground the crowd stands on round the focus. */
    static final int CROWD_RADIUS = 24;

    private RenderStressScene() { }

    /** The stood-up world and the cell its framings look at. */
    record Stood(BattleSimulation sim, float[] focus) { }

    static Stood build() throws Exception {
        BattleSimulation sim = CreepVegetationSnapshotSuite.verdantColony();
        sim.setMissionCompletionEnabled(false);
        NavigationGrid grid = sim.getGrid();
        int[] centre = openCentre(grid, sim.fireField());
        int cx = centre[0], cy = centre[1];
        Random random = new Random(SEED);

        // Bores first: their colonies sprout only where no body stands yet.
        openBore(sim, 0, true, cx - 30, cy - 12, BoreTier.MAW);
        openBore(sim, 1, false, cx + 28, cy - 14, BoreTier.MAW);
        openBore(sim, 2, false, cx - 26, cy + 16, BoreTier.BORE);
        openBore(sim, 3, false, cx + 30, cy + 14, BoreTier.BORE);
        sim.creepField();
        sim.matureCreep();

        List<int[]> ground = openCells(grid, cx, cy, CROWD_RADIUS);
        List<Long> bodies = new ArrayList<>();
        infantry(sim, ground, random, bodies, Faction.MARINE, UnitType.MARINE, 3);
        infantry(sim, ground, random, bodies, Faction.ALLY, UnitType.MARINE_BLUE, 2);
        infantry(sim, ground, random, bodies, Faction.DEFENDER, UnitType.MARINE_RED, 2);
        infantry(sim, ground, random, bodies, Faction.DEFENDER, UnitType.MILITIA, 2);
        for (MechVariant variant : MechVariant.values()) {
            bodies.add(mech(sim, ground, random, Faction.MARINE, variant));
            bodies.add(mech(sim, ground, random, Faction.DEFENDER, variant));
        }
        swarm(sim, ground, random, bodies, UnitType.SWARM_RUNNER, 72);
        swarm(sim, ground, random, bodies, UnitType.SWARM_BRUTE, 14);
        swarm(sim, ground, random, bodies, UnitType.SWARM_SPITTER, 14);
        swarm(sim, ground, random, bodies, UnitType.SWARM_MATRIARCH, 1);

        // Every third body down where it stands: the dead of the fight so far.
        for (int i = 0; i < bodies.size(); i += 3) sim.applyDamage(bodies.get(i), 1_000_000f, 1000f);

        for (int t = 0; t < SETTLE_TICKS; t++) sim.advance(BattleSimulation.TICK_DT);

        FireField fire = sim.fireField();
        fire.setWind(1f, 0.2f, 0.5f);
        fire.addHeat(cx + 0.5f, cy + 0.5f, FIRE_RADIUS, 3000);
        int now = sim.getSimTickIndex();
        int first = now + Math.floorMod(-now, FireField.STEP_TICKS);
        for (int s = 0; s < FIRE_STEPS; s++) {
            if (s == FIRE_STEPS - FRESH_STEPS) fire.addHeat(cx + FIRE_RADIUS + 0.5f, cy + 0.5f, FRESH_RADIUS, 3000);
            fire.tick(first + s * FireField.STEP_TICKS);
        }

        return new Stood(sim, new float[]{cx + 0.5f, cy + 0.5f});
    }

    private static void openBore(BattleSimulation sim, int number, boolean mother, int x, int y, BoreTier tier) {
        SwarmBore bore = sim.swarmBores().open(sim, number, mother, x, y);
        if (bore == null) return;
        for (int guard = 0; bore.tier().ordinal() < tier.ordinal() && guard < 64; guard++) {
            sim.swarmBores().feed(sim, bore, 1f);
        }
    }

    private static void infantry(BattleSimulation sim, List<int[]> ground, Random random, List<Long> bodies,
                                 Faction faction, UnitType type, int squads) {
        for (int s = 0; s < squads; s++) {
            int size = 8;
            MarineLoadout[] kit = faction == Faction.MARINE || faction == Faction.ALLY
                    ? InfantryLoadoutRolls.playerSquad(size, random)
                    : InfantryLoadoutRolls.defenderSquad(size, type, RiskLevel.MEDIUM, random);
            int squad = sim.mintSquad(faction, type);
            int[] at = ground.get(random.nextInt(ground.size()));
            for (int i = 0; i < size; i++) {
                EntitySpec spec = new EntitySpec(type + " " + s + "-" + i, faction, type,
                        at[0] - 2 + i % 4, at[1] + i / 4);
                kit[i].seedInto(spec);
                spec.squad(squad);
                bodies.add(sim.spawn(spec));
            }
        }
    }

    private static long mech(BattleSimulation sim, List<int[]> ground, Random random, Faction faction,
                             MechVariant variant) {
        int[] at = ground.get(random.nextInt(ground.size()));
        EntitySpec spec = new EntitySpec(variant + " " + faction, faction, UnitType.HEAVY_MECH, at[0], at[1]);
        variant.applyTo(spec);
        spec.squad(sim.mintSquad(faction, UnitType.HEAVY_MECH));
        long id = sim.spawn(spec);
        sim.world().attachMechLoadout(id, variant.createLoadout(null));
        return id;
    }

    private static void swarm(BattleSimulation sim, List<int[]> ground, Random random, List<Long> bodies,
                              UnitType type, int count) {
        for (int i = 0; i < count; i++) {
            int[] at = ground.get(random.nextInt(ground.size()));
            bodies.add(SwarmStrains.spawn(sim, type + " " + i, type, at[0], at[1]));
        }
    }

    private static List<int[]> openCells(NavigationGrid grid, int cx, int cy, int radius) {
        List<int[]> out = new ArrayList<>();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = cx + dx, y = cy + dy;
                if (dx * dx + dy * dy > radius * radius || !grid.inBounds(x, y)) continue;
                if (grid.isWalkable(x, y)) out.add(new int[]{x, y});
            }
        }
        if (out.isEmpty()) throw new IllegalStateException("No open ground round " + cx + "," + cy);
        return out;
    }

    /**
     * The cell with the most open and burnable ground round it, standing fuel (a tree's crown, a building's wall)
     * counted thrice: a crowd needs room to stand, the fire needs fuel, which the colony's paved town does not
     * have, and only standing fuel burns hot enough to throw embers.
     */
    private static int[] openCentre(NavigationGrid grid, FireField fire) {
        // Far enough in from the edge that the bores, thirty cells out, stand on the map.
        int margin = 40;
        int[] best = {grid.getWidth() / 2, grid.getHeight() / 2};
        int bestOpen = -1;
        for (int y = margin; y < grid.getHeight() - margin; y += 4) {
            for (int x = margin; x < grid.getWidth() - margin; x += 4) {
                int open = 0;
                for (int dy = -CROWD_RADIUS; dy <= CROWD_RADIUS; dy += 3) {
                    for (int dx = -CROWD_RADIUS; dx <= CROWD_RADIUS; dx += 3) {
                        if (grid.inBounds(x + dx, y + dy) && grid.isWalkable(x + dx, y + dy)) open++;
                        if (fire.canBurn(x + dx, y + dy)) open += grid.isWalkable(x + dx, y + dy) ? 1 : 3;
                    }
                }
                if (open > bestOpen) {
                    bestOpen = open;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }
}
