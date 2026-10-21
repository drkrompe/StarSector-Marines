package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * The one way a swarm strain body enters a battle, so that whatever spawns the
 * swarm (the director's strain table, a scene, a debug mission) issues each
 * strain the same kit. Every strain runs {@link UnitRole#SWARM_PRESSURE};
 * {@link SwarmPressureBehavior} dispatches on the unit type.
 */
public final class SwarmStrains {

    /** The Brute's plate: a DMR or rocket goes straight through it, an SMG barely scratches it. */
    public static final float BRUTE_ARMOR = 20f;
    public static final float BRUTE_ARMOR_RATING = 10f;

    private SwarmStrains() {}

    /** A swarm body of {@code type} on the defender side at a cell, with its strain's kit. */
    public static EntitySpec spec(String name, UnitType type, int cellX, int cellY) {
        if (!type.isSwarm()) {
            throw new IllegalArgumentException(type + " is not a swarm strain");
        }
        EntitySpec spec = new EntitySpec(name, Faction.DEFENDER, type, cellX, cellY)
                .role(UnitRole.SWARM_PRESSURE);
        if (type == UnitType.SWARM_BRUTE) spec.armor(BRUTE_ARMOR, BRUTE_ARMOR_RATING);
        return spec;
    }

    /** Spawns {@link #spec} and returns the new body's id. */
    public static long spawn(BattleSimulation sim, String name, UnitType type,
                             int cellX, int cellY) {
        return sim.spawn(spec(name, type, cellX, cellY));
    }
}
