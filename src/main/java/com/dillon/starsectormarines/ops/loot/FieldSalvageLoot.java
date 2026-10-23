package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveResult;
import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * The materials succeeded optional objectives earned, settled as field finds are: into the
     * ordinary cargo settlement, claiming no salvage budget. An unknown commodity id is dropped.
     */
    public static LootManifest appendObjectiveMaterials(LootManifest manifest, List<ObjectiveResult> objectives,
                                                        Function<String, LootCandidate> catalog) {
        var stacks = new ArrayList<>(manifest.stacks);
        for (ObjectiveResult result : objectives) {
            for (Map.Entry<String, Integer> material : result.earned().materials().entrySet()) {
                if (material.getValue() == null || material.getValue() <= 0) continue;
                var candidate = catalog.apply(material.getKey());
                if (candidate != null) stacks.add(new LootStack(candidate, material.getValue(), true));
            }
        }
        if (stacks.size() == manifest.stacks.size()) return manifest;
        return new LootManifest(manifest.seed, manifest.entitlement, manifest.selectionBudget, stacks);
    }
}
