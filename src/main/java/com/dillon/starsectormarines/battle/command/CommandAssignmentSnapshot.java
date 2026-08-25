package com.dillon.starsectormarines.battle.command;

import java.util.Map;
import java.util.TreeMap;

/** Frozen directive ledger included in a command frame. */
public final class CommandAssignmentSnapshot {

    private final Map<Integer, CommandDirective> directives;

    CommandAssignmentSnapshot(Map<Integer, CommandDirective> directives) {
        Map<Integer, CommandDirective> copy = new TreeMap<>();
        directives.forEach((id, directive) -> copy.put(id,
                CommandFrameCopies.directive(directive)));
        this.directives = Map.copyOf(copy);
    }

    public CommandDirective directiveFor(int squadId) {
        return directives.get(squadId);
    }

    public Map<Integer, CommandDirective> directives() {
        return directives;
    }
}
