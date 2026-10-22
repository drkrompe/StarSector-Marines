package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * The one way a swarm strain body enters a battle, so that whatever spawns the
 * swarm (the director's strain table, a scene, a debug mission) issues each
 * strain the same kit. Every strain runs {@link UnitRole#SWARM_PRESSURE};
 * {@link SwarmPressureBehavior} dispatches on the unit type.
 *
 * <p>Every body is issued a growth stage here too, so a director, a scene and
 * a debug mission spawn the same elder: the stage goes on the body and its
 * strain's kit at that stage ({@link SwarmStageStats}) goes in its stat
 * columns. A body spawned without one is an adult.
 */
public final class SwarmStrains {

    /** The Brute's plate: a DMR or rocket goes straight through it, an SMG barely scratches it. */
    public static final float BRUTE_ARMOR = 20f;
    public static final float BRUTE_ARMOR_RATING = 10f;
    /**
     * The Matriarch's plate, a mech's weight of it: rifle fire skips off, a rocket, a mech's guns or a
     * satchel go in.
     */
    public static final float MATRIARCH_ARMOR = 700f;
    public static final float MATRIARCH_ARMOR_RATING = 14f;

    private SwarmStrains() {}

    /** An adult swarm body of {@code type} on the defender side at a cell, with its strain's kit. */
    public static EntitySpec spec(String name, UnitType type, int cellX, int cellY) {
        return spec(name, type, cellX, cellY, SwarmStage.ADULT);
    }

    /** A swarm body of {@code type} at {@code stage} on the defender side at a cell, with its strain's kit. */
    public static EntitySpec spec(String name, UnitType type, int cellX, int cellY, SwarmStage stage) {
        if (!type.isSwarm()) {
            throw new IllegalArgumentException(type + " is not a swarm strain");
        }
        SwarmStage issued = SwarmStage.of(stage);
        SwarmStageStats.Stats stats = SwarmStageStats.of(type, issued);
        EntitySpec spec = new EntitySpec(name, Faction.DEFENDER, type, cellX, cellY)
                .role(UnitRole.SWARM_PRESSURE)
                .swarmStage(issued)
                .hp(stats.hp())
                .maxHp(stats.hp())
                .attackDamage(stats.damage())
                .attackRange(stats.reach())
                .moveSpeed(stats.speed());
        if (stats.armor() > 0f) spec.armor(stats.armor(), stats.armorRating());
        return spec;
    }

    /** Spawns an adult {@link #spec} and returns the new body's id. */
    public static long spawn(BattleSimulation sim, String name, UnitType type,
                             int cellX, int cellY) {
        return sim.spawn(spec(name, type, cellX, cellY));
    }

    /** Spawns {@link #spec} at {@code stage} and returns the new body's id. */
    public static long spawn(BattleSimulation sim, String name, UnitType type,
                             int cellX, int cellY, SwarmStage stage) {
        return sim.spawn(spec(name, type, cellX, cellY, stage));
    }
}
