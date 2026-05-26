package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.compound.CompoundService;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Per-faction resource pools that gate orchestration requests. Each pool
 * accumulates fractionally over time (driven by alive compounds) and is
 * debited in whole units by dispatch layers.
 *
 * <p>Production is compound-driven: each alive ARMORY ticks
 * {@link ResourceType#REINFORCEMENT} for its owning faction; each alive
 * COMMAND_POST ticks {@link ResourceType#AIRSTRIKE}. Capturing a compound
 * permanently removes its contribution — the resource rate degrades
 * proportionally as compounds fall.
 *
 * <p>Follows the {@code *Service} convention — state owner, ticked by
 * {@link BattleSimulation}. Dispatch layers ({@link
 * com.dillon.starsectormarines.battle.reinforcement.ReinforcementService})
 * call {@link #tryConsume} before committing a spawn.
 */
public final class BattleResources {

    private static final float REINFORCEMENT_PER_ARMORY_PER_SEC = 0.05f;
    private static final float AIRSTRIKE_PER_CP_PER_SEC = 0.0f;
    private static final float REINFORCEMENT_COST = 1.0f;

    private final float[][] pools;

    public BattleResources() {
        pools = new float[Faction.values().length][ResourceType.values().length];
    }

    public float getBalance(Faction faction, ResourceType type) {
        return pools[faction.ordinal()][type.ordinal()];
    }

    public void produce(Faction faction, ResourceType type, float amount) {
        pools[faction.ordinal()][type.ordinal()] += amount;
    }

    public boolean tryConsume(Faction faction, ResourceType type, float cost) {
        int fi = faction.ordinal();
        int ti = type.ordinal();
        if (pools[fi][ti] < cost) return false;
        pools[fi][ti] -= cost;
        return true;
    }

    /**
     * Accumulate production from alive compounds. Called on the sim's
     * slow-tick cadence, after compound capture so the state is fresh.
     */
    public void tick(float dt, CompoundService compounds) {
        for (CompoundService.Record r : compounds.getRecords()) {
            Faction supplier = supplyFaction(r);
            if (supplier == null) continue;
            ResourceType type = resourceForKind(r.node.kind);
            if (type == null) continue;
            float rate = productionRate(type);
            if (rate > 0f) {
                produce(supplier, type, rate * dt);
            }
        }
    }

    public float reinforcementCost() { return REINFORCEMENT_COST; }

    private static Faction supplyFaction(CompoundService.Record r) {
        switch (r.state) {
            case DEFENDER_HELD:
            case CONTESTED:
                return Faction.DEFENDER;
            case MARINE_HELD:
                return Faction.MARINE;
            default:
                return null;
        }
    }

    private static ResourceType resourceForKind(TacticalNode.Kind kind) {
        switch (kind) {
            case ARMORY:       return ResourceType.REINFORCEMENT;
            case COMMAND_POST: return ResourceType.AIRSTRIKE;
            default:           return null;
        }
    }

    private static float productionRate(ResourceType type) {
        switch (type) {
            case REINFORCEMENT: return REINFORCEMENT_PER_ARMORY_PER_SEC;
            case AIRSTRIKE:     return AIRSTRIKE_PER_CP_PER_SEC;
            default:            return 0f;
        }
    }
}
