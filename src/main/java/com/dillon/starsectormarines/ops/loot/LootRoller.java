package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Pure deterministic weighted roller for post-battle recovery manifests. */
public final class LootRoller {

    private static final int MIN_POOL_VALUE = 5_000;
    private static final int MAX_STACKS = 12;

    private LootRoller() {}

    public static LootManifest roll(LootRollRequest request, List<LootCandidate> catalog) {
        if (request == null || request.entitlement <= 0 || catalog == null || catalog.isEmpty()) {
            return LootManifest.EMPTY;
        }

        List<LootCandidate> eligible = new ArrayList<>();
        for (LootCandidate candidate : catalog) {
            if (candidate != null && candidate.weight > 0f) eligible.add(candidate);
        }
        if (eligible.isEmpty()) return LootManifest.EMPTY;

        // Catalog providers are free to use sets/maps. Canonical ordering makes
        // the same logical catalog replay identically regardless of iteration order.
        eligible.sort(Comparator
                .comparing((LootCandidate c) -> c.kind.ordinal())
                .thenComparing(c -> c.itemId));

        long seed = seedOf(request);
        Random random = new Random(seed);
        int targetValue = targetPoolValue(request);
        int drawLimit = Math.min(MAX_STACKS, eligible.size());
        List<LootStack> rolled = new ArrayList<>();
        long totalValue = 0L;

        while (!eligible.isEmpty() && rolled.size() < drawLimit && totalValue < targetValue) {
            LootCandidate candidate = removeWeighted(eligible, random);
            int quantity = rollQuantity(candidate, random, targetValue - totalValue);
            LootStack stack = new LootStack(candidate, quantity);
            rolled.add(stack);
            totalValue += stack.totalValue();
        }

        int boundedTotal = totalValue > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalValue;
        int budget = (int) Math.min(Integer.MAX_VALUE,
                (long) boundedTotal * Math.min(255, request.entitlement) / 100L);
        return new LootManifest(seed, request.entitlement, budget, rolled);
    }

    static long seedOf(LootRollRequest request) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, request.missionId);
        hash = mix(hash, request.missionType != null ? request.missionType.name() : null);
        hash = mix(hash, request.risk != null ? request.risk.name() : null);
        hash = mix(hash, request.targetFactionId);
        hash = mix(hash, request.targetIndustryId);
        hash ^= request.payout;
        hash *= 0x100000001b3L;
        hash ^= request.entitlement;
        hash *= 0x100000001b3L;
        return hash;
    }

    private static long mix(long hash, String value) {
        if (value == null) {
            hash ^= 0xff;
            return hash * 0x100000001b3L;
        }
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static int targetPoolValue(LootRollRequest request) {
        float typeMult = typeMultiplier(request.missionType);
        float riskMult = riskMultiplier(request.risk);
        long target = Math.round((double) request.payout * typeMult * riskMult);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(MIN_POOL_VALUE, target));
    }

    private static float typeMultiplier(MissionType type) {
        if (type == null) return 1f;
        switch (type) {
            case SABOTAGE:   return 0.75f;
            case EXTRACTION: return 1f;
            case ASSAULT:    return 2f;
            case RAID:       return 2.5f;
            case CONQUEST:   return 3f;
            default:         return 1f;
        }
    }

    private static float riskMultiplier(RiskLevel risk) {
        if (risk == RiskLevel.HIGH) return 1.5f;
        if (risk == RiskLevel.MEDIUM) return 1.2f;
        return 1f;
    }

    private static LootCandidate removeWeighted(List<LootCandidate> candidates, Random random) {
        double total = 0d;
        for (LootCandidate candidate : candidates) total += candidate.weight;
        double pick = random.nextDouble() * total;
        for (int i = 0; i < candidates.size(); i++) {
            LootCandidate candidate = candidates.get(i);
            pick -= candidate.weight;
            if (pick <= 0d) return candidates.remove(i);
        }
        return candidates.remove(candidates.size() - 1);
    }

    private static int rollQuantity(LootCandidate candidate, Random random, long remainingValue) {
        int range = candidate.maxQuantity - candidate.minQuantity + 1;
        int quantity = candidate.minQuantity + (range > 1 ? random.nextInt(range) : 0);
        if (candidate.maxQuantity > 1 && remainingValue > 0L) {
            int useful = (int) Math.min(candidate.maxQuantity,
                    Math.max(candidate.minQuantity,
                            (remainingValue + candidate.unitValue - 1L) / candidate.unitValue));
            quantity = Math.min(quantity, useful);
        }
        return quantity;
    }
}
