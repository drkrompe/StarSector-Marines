package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Published common command envelope, created only after arbiter commit. */
public record CommanderSnapshot<D>(
        Faction perspective,
        String strategy,
        String phase,
        int tick,
        int influenceTick,
        int commandPoolSize,
        int reserveCount,
        List<String> objectiveSummaries,
        List<CommandDirective> directives,
        D detail) {

    public CommanderSnapshot {
        objectiveSummaries = List.copyOf(objectiveSummaries);
        directives = List.copyOf(directives);
    }

    public CommandDirective directiveFor(int squadId) {
        for (CommandDirective directive : directives) {
            if (directive.squadId() == squadId) return directive;
        }
        return null;
    }
}
