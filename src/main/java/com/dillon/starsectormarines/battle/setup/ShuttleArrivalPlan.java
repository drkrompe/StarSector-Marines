package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable battle-construction facts resolved from a mission's arrival policy. */
public record ShuttleArrivalPlan(
        MarineArrivalPolicy policy,
        int firstPlayerShuttle,
        ConquestArrivalConfig arrivalConfig) {

    public ShuttleArrivalPlan {
        policy = Objects.requireNonNull(policy, "policy");
        firstPlayerShuttle = Math.max(0, firstPlayerShuttle);
        arrivalConfig = arrivalConfig != null
                ? arrivalConfig
                : policy == MarineArrivalPolicy.PAIRED_HALF_SQUAD
                        ? ConquestArrivalConfig.DEFAULT
                        : ConquestArrivalConfig.LEGACY;
    }

    public ShuttleArrivalPlan(MarineArrivalPolicy policy, int firstPlayerShuttle) {
        this(policy, firstPlayerShuttle,
                policy == MarineArrivalPolicy.PAIRED_HALF_SQUAD
                        ? ConquestArrivalConfig.DEFAULT
                        : ConquestArrivalConfig.LEGACY);
    }

    public static ShuttleArrivalPlan legacy() {
        return new ShuttleArrivalPlan(MarineArrivalPolicy.INDEPENDENT_FULL_LOAD,
                0, ConquestArrivalConfig.LEGACY);
    }

    public boolean paired() {
        return policy == MarineArrivalPolicy.PAIRED_HALF_SQUAD;
    }

    public ShuttleType deliveryCraft(ShuttleType committedLift) {
        return policy.deliveryCraft(committedLift);
    }

    /**
     * Resolves campaign lift into the bounded set of craft that actually make
     * the final descent. Paired Conquest arrivals use one employer pair and a
     * mission-configured number of player pairs; committed sorties become
     * cycles distributed across that reusable fleet instead of consuming one
     * permanent map berth per carrier.
     *
     * @param selectedPlayerSeats selected named personnel that must fit; a
     *                            negative value preserves authored capacity
     */
    public ResolvedManifest resolveManifest(List<ShuttleAssignment> manifest,
                                            int selectedPlayerSeats) {
        List<ShuttleAssignment> source = manifest != null
                ? List.copyOf(manifest) : List.of();
        int employerEnd = Math.min(firstPlayerShuttle, source.size());
        if (!paired()) return new ResolvedManifest(source, employerEnd);

        List<ShuttleAssignment> resolved = new ArrayList<>(
                2 + 2 * arrivalConfig.playerShuttlePairCount());
        collapseSegment(source, 0, employerEnd,
                seatCapacity(source, 0, employerEnd), 1, resolved);
        int resolvedEmployer = resolved.size();

        int authoredPlayerSeats = seatCapacity(source, employerEnd, source.size());
        int playerSeats = selectedPlayerSeats >= 0
                ? Math.max(authoredPlayerSeats, selectedPlayerSeats)
                : authoredPlayerSeats;
        collapseSegment(source, employerEnd, source.size(), playerSeats,
                arrivalConfig.playerShuttlePairCount(), resolved);
        return new ResolvedManifest(resolved, resolvedEmployer);
    }

    public ResolvedManifest resolveManifest(List<ShuttleAssignment> manifest) {
        return resolveManifest(manifest, -1);
    }

    private void collapseSegment(List<ShuttleAssignment> source,
                                 int from, int to, int requiredSeats,
                                 int requestedPairs,
                                 List<ShuttleAssignment> resolved) {
        if (from >= to || requiredSeats <= 0) return;
        int seatsPerSortie = policy.seatsPerSortie(ShuttleType.AEROSHUTTLE);
        int sorties = (requiredSeats + seatsPerSortie - 1) / seatsPerSortie;
        int completePairCycles = sorties / 2;
        int activePairs = Math.min(Math.max(1, requestedPairs),
                Math.max(1, completePairCycles));
        int baseCycles = completePairCycles / activePairs;
        int extraCycles = completePairCycles % activePairs;
        boolean oddSortie = (sorties & 1) != 0;
        for (int pair = 0; pair < activePairs; pair++) {
            int pairedCycles = baseCycles + (pair < extraCycles ? 1 : 0);
            int firstCycles = pairedCycles
                    + (oddSortie && pair == activePairs - 1 ? 1 : 0);
            if (firstCycles > 0) {
                resolved.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE,
                        firstCycles, seatsPerSortie));
            }
            if (pairedCycles <= 0) continue;
            resolved.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE,
                    pairedCycles, seatsPerSortie));
        }
    }

    private static int seatCapacity(List<ShuttleAssignment> manifest,
                                    int from, int to) {
        int seats = 0;
        for (int i = Math.max(0, from); i < Math.min(to, manifest.size()); i++) {
            ShuttleAssignment assignment = manifest.get(i);
            if (assignment != null) {
                seats += assignment.seatsPerSortie * assignment.cycles;
            }
        }
        return seats;
    }

    /** The reusable descent manifest and its remapped ownership boundary. */
    public record ResolvedManifest(
            List<ShuttleAssignment> assignments,
            int firstPlayerShuttle) {

        public ResolvedManifest {
            assignments = List.copyOf(assignments);
            firstPlayerShuttle = Math.max(0,
                    Math.min(firstPlayerShuttle, assignments.size()));
        }
    }
}
