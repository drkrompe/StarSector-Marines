package com.dillon.starsectormarines.battle.command;

/** Conquest-specific diagnostic seam shared by the two faction commanders. */
public interface ConquestFrontCommand extends CommandStrategy {

    ConquestFrontSnapshot frontSnapshot();

    static ConquestFrontSnapshot snapshotOf(CommandStrategy command) {
        return command instanceof ConquestFrontCommand conquest
                ? conquest.frontSnapshot() : null;
    }
}
