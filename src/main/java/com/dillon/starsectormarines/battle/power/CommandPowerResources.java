package com.dillon.starsectormarines.battle.power;

/** Campaign-resource window used by battle powers without coupling the sim to Starsector APIs. */
public interface CommandPowerResources {

    CommandPowerResources FREE = new CommandPowerResources() {
        @Override public int availableSupplies() { return Integer.MAX_VALUE; }
        @Override public boolean spendSupplies(int amount) { return true; }
    };

    int availableSupplies();

    /** Atomically verifies and spends {@code amount}; zero must always succeed. */
    boolean spendSupplies(int amount);
}
