package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.CommandPower;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Constant-budget S8 command deck: selection math plus launch-boundary filtering. */
public final class CommandDeck {

    public static final int BUDGET = 5;

    private CommandDeck() {}

    public static int weight(CommandPower power) {
        return power == null ? 0 : power.slotWeight;
    }

    public static int used(List<CommandPower> available, Collection<String> selectedIds) {
        int used = 0;
        if (available == null || selectedIds == null) return used;
        for (CommandPower power : available) {
            if (selectedIds.contains(power.id)) used += weight(power);
        }
        return used;
    }

    public static boolean canAdd(List<CommandPower> available,
                                 Collection<String> selectedIds,
                                 CommandPower candidate) {
        return candidate != null && selectedIds != null
                && !selectedIds.contains(candidate.id)
                && used(available, selectedIds) + weight(candidate) <= BUDGET;
    }

    /** Stable catalog-order fill used once when a mission first opens. */
    public static Set<String> defaultSelection(List<CommandPower> available) {
        Set<String> selected = new LinkedHashSet<>();
        if (available == null) return selected;
        int used = 0;
        for (CommandPower power : available) {
            int weight = weight(power);
            if (used + weight > BUDGET) continue;
            selected.add(power.id);
            used += weight;
        }
        return selected;
    }

    /**
     * Filters in stable catalog order and enforces the budget again at launch.
     * Unknown ids and an over-budget/tampered tail are ignored.
     */
    public static List<CommandPower> filter(List<CommandPower> available,
                                            Collection<String> selectedIds) {
        List<CommandPower> out = new ArrayList<>();
        if (available == null || selectedIds == null) return out;
        int used = 0;
        for (CommandPower power : available) {
            if (!selectedIds.contains(power.id)) continue;
            int weight = weight(power);
            if (used + weight > BUDGET) continue;
            out.add(power);
            used += weight;
        }
        return out;
    }

    public static Detachment apply(Detachment detachment, Collection<String> selectedIds) {
        if (detachment == null) return Detachment.EMPTY;
        return new Detachment(detachment.shuttleManifest, detachment.marineWings,
                filter(detachment.powers, selectedIds));
    }
}
