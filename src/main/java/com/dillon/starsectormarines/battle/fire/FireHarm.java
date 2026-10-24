package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.combat.DamageResolver;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.marine.ArmorClass;

import java.util.Arrays;

/**
 * What fire does to whoever is in it (`fire-nouns.md`, Damage): every fire
 * step a unit in a burning cell takes damage scaled by the cell's intensity,
 * and one beside a burning cell a tenth of the hottest neighbour's. The damage
 * carries morale, so a team standing in fire breaks as one under fire does.
 * What it does depends on what is burning, not on what stops bullets: a soft
 * body (infantry in ordinary armour, militia, civilians, the swarm) takes it
 * straight to health, its armour untouched, so a marine stood in full flame is
 * dead in about three seconds; the swarm, being flesh, takes half as much again.
 * A sealed power suit's armour soaks it as it soaks a round, and a mech's or
 * vehicle's hull takes a tenth of it. Static things (turrets, hubs, bores, the swarm's
 * growths) take nothing from fire: they are fought through their durability, and
 * growths die with the creep the fire burns.
 *
 * <p>A swarm body lying in a burning cell is consumed, as a digested one is.
 * The dead of every other side are left to be recovered.
 *
 * <p>Serial, on fire steps only; damage is gathered before it is applied, so a
 * death in the middle of the sweep cannot reshape the table being walked.
 */
public final class FireHarm {

    /** Hit points a second at full intensity, in the cell. */
    public static final float DPS_IN = 8f;
    /** The share of the hottest neighbour's damage a unit beside a fire takes. */
    public static final float BESIDE_SHARE = 0.1f;
    /** The share a mech's or vehicle's hull takes. */
    public static final float HULL_SHARE = 0.1f;
    /** Penetration of fire damage against a sealed suit or a hull: past armour that stops rifle rounds. */
    public static final float PENETRATION = 15f;
    /** How much more the swarm takes: flesh, with nothing over it. */
    public static final float SWARM_SHARE = 1.5f;

    private long[] ids = new long[64];
    private float[] amounts = new float[64];
    private int count;

    public void tick(BattleSimulation sim, FireField field) {
        if (field.burningCount() == 0 || Math.floorMod(sim.getSimTickIndex(), FireField.STEP_TICKS) != 0) return;
        BattleComponents c = sim.getBattleComponents();
        int width = field.width(), height = field.height();
        count = 0;
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.gridOccupants)) {
            if (!t.has(c.MOVEMENT) || !t.has(c.HEALTH)) continue;
            Object[] types = t.objects(c.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            float[] x = t.floats(c.POSITION, BattleComponents.POSITION_X).array();
            float[] y = t.floats(c.POSITION, BattleComponents.POSITION_Y).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                if (!(types[r] instanceof UnitType type) || type.isStatic()) continue;
                int cx = (int) Math.floor(x[r]), cy = (int) Math.floor(y[r]);
                if (cx < 0 || cy < 0 || cx >= width || cy >= height) continue;
                float share = exposure(field, cx, cy);
                if (share <= 0f) continue;
                add(t.entityAt(r), share * bodyShare(type) * DPS_IN * FireField.STEP_SECONDS);
            }
        }
        for (int i = 0; i < count; i++) {
            long id = ids[i];
            if (!sim.getRoster().isAliveById(id)) continue;
            boolean through = throughArmor(sim.identity().type(id), sim.identity().armorClass(id));
            sim.applyDamage(id, amounts[i], through ? DamageResolver.THROUGH_ARMOR : PENETRATION, 1f);
        }
        consumeSwarmDead(sim, field, c);
    }

    /** The share of fire damage a body of this kind takes: a tenth for a hull, half as much again for the swarm. */
    static float bodyShare(UnitType type) {
        if (isHull(type)) return HULL_SHARE;
        return type.isSwarm() || type.isFlier() ? SWARM_SHARE : 1f;
    }

    /** Whether fire goes past this body's armour to its health: everything soft, which is all but suits and hulls. */
    static boolean throughArmor(UnitType type, ArmorClass armor) {
        return !isHull(type) && armor != ArmorClass.POWER_SUIT;
    }

    private static boolean isHull(UnitType type) {
        return type.isMech() || type.isMachineFrame() || type.isGroundVehicle();
    }

    /** 0..1: the cell's own intensity, else a tenth of its hottest neighbour's. */
    static float exposure(FireField field, int x, int y) {
        int in = field.intensityAt(x, y);
        if (in > 0) return in / 255f;
        int beside = 0;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                if (dx != 0 || dy != 0) beside = Math.max(beside, field.intensityAt(x + dx, y + dy));
        return beside * BESIDE_SHARE / 255f;
    }

    private void consumeSwarmDead(BattleSimulation sim, FireField field, BattleComponents c) {
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.corpses)) {
            Object[] types = t.objects(c.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            int[] sprite = t.ints(c.SPRITE, BattleComponents.SPRITE_INDEX).array();
            float[] x = t.floats(c.POSITION, BattleComponents.POSITION_X).array();
            float[] y = t.floats(c.POSITION, BattleComponents.POSITION_Y).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                if (sprite[r] < 0 || !(types[r] instanceof UnitType type) || !type.isSwarm()) continue;
                if (!field.isBurning((int) Math.floor(x[r]), (int) Math.floor(y[r]))) continue;
                long body = t.entityAt(r);
                if (sim.swarmHarvest().isCarried(body)) continue;
                sim.getEntityWorld().setInt(body, c.SPRITE, BattleComponents.SPRITE_INDEX, -1);
            }
        }
    }

    private void add(long id, float amount) {
        if (count == ids.length) {
            ids = Arrays.copyOf(ids, count * 2);
            amounts = Arrays.copyOf(amounts, count * 2);
        }
        ids[count] = id;
        amounts[count++] = amount;
    }
}
