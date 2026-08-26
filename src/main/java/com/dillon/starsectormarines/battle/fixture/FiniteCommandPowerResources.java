package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.power.CommandPowerResources;

/** Replay-local command-power supply account initialized from launch-time cargo. */
public final class FiniteCommandPowerResources implements CommandPowerResources {

    private int supplies;

    public FiniteCommandPowerResources(int supplies) {
        if (supplies < 0) throw new IllegalArgumentException("supplies must be non-negative");
        this.supplies = supplies;
    }

    @Override
    public int availableSupplies() {
        return supplies;
    }

    @Override
    public boolean spendSupplies(int amount) {
        if (amount <= 0) return true;
        if (supplies < amount) return false;
        supplies -= amount;
        return true;
    }
}
