package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Frozen directive ledger included in a command frame. */
public final class CommandAssignmentSnapshot {

    private final Map<Integer, CommandDirective> directives;

    CommandAssignmentSnapshot(Map<Integer, CommandDirective> directives) {
        Map<Integer, CommandDirective> copy = new TreeMap<>();
        directives.forEach((id, directive) -> copy.put(id,
                CommandFrameCopies.directive(directive)));
        this.directives = Collections.unmodifiableMap(copy);
    }

    public CommandDirective directiveFor(int squadId) {
        return directives.get(squadId);
    }

    public Map<Integer, CommandDirective> directives() {
        return directives;
    }

    /** Returns the stable, faction-scoped ledger view legal for one command frame. */
    public CommandAssignmentSnapshot forPerspective(Faction perspective) {
        Map<Integer, CommandDirective> filtered = new TreeMap<>();
        directives.forEach((id, directive) -> {
            if (directive.perspective() == perspective) {
                filtered.put(id, directive);
            }
        });
        return new CommandAssignmentSnapshot(filtered);
    }
}
