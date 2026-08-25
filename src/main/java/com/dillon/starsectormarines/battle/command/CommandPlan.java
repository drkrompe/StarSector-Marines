package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;
import java.util.Objects;

/** Complete side proposal produced without mutating the battle. */
public record CommandPlan<D>(
        Faction perspective,
        String strategy,
        String phase,
        int tick,
        int influenceTick,
        int commandPoolSize,
        int reserveCount,
        List<String> objectiveSummaries,
        List<CommandProposal> proposals,
        D detail) {

    public CommandPlan {
        Objects.requireNonNull(perspective, "perspective");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(phase, "phase");
        objectiveSummaries = List.copyOf(objectiveSummaries);
        proposals = List.copyOf(proposals);
    }
}
