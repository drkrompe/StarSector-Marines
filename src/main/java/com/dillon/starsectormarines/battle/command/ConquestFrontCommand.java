package com.dillon.starsectormarines.battle.command;

/** Conquest-specific diagnostic seam shared by the two faction commanders. */
public interface ConquestFrontCommand extends MissionCommand {

    ConquestFrontSnapshot frontSnapshot();

    static ConquestFrontSnapshot snapshotOf(MissionCommand command) {
        return command instanceof ConquestFrontCommand conquest
                ? conquest.frontSnapshot() : null;
    }
}
