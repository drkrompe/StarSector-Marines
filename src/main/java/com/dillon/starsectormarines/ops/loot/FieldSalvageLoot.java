package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

/** Secured finds use the ordinary cargo settlement, but cannot be traded for claim budget. */
public final class FieldSalvageLoot {
    private FieldSalvageLoot() { }

    public static LootManifest append(LootManifest negotiated, List<SalvageCache.Recovered> finds,
                                       Function<SalvageResource, LootCandidate> catalog) {
        var stacks = new ArrayList<>(negotiated.stacks);
        var ids = new HashSet<String>();
        for (var find : finds) {
            if (!ids.add(find.id())) continue;
            var candidate = catalog.apply(find.resource());
            if (candidate != null) stacks.add(new LootStack(candidate, find.quantity(), true));
        }
        return new LootManifest(negotiated.seed, negotiated.entitlement, negotiated.selectionBudget, stacks);
    }
}
