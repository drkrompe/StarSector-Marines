package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable battle-construction facts resolved from a mission's arrival policy. */
public record ShuttleArrivalPlan(
        MarineArrivalPolicy policy,
        int firstPlayerShuttle) {

    public ShuttleArrivalPlan {
        policy = Objects.requireNonNull(policy, "policy");
        firstPlayerShuttle = Math.max(0, firstPlayerShuttle);
    }

    public static ShuttleArrivalPlan legacy() {
        return new ShuttleArrivalPlan(MarineArrivalPolicy.INDEPENDENT_FULL_LOAD, 0);
    }

    public boolean paired() {
        return policy == MarineArrivalPolicy.PAIRED_HALF_SQUAD;
    }

    public ShuttleType deliveryCraft(ShuttleType committedLift) {
        return policy.deliveryCraft(committedLift);
    }

    /**
     * Resolves campaign lift into the bounded set of craft that actually make
     * the final descent. Paired Conquest arrivals reuse one Aeroshuttle pair
     * per ownership segment; the number of committed sorties becomes cycles
     * on that pair instead of consuming one permanent map berth per carrier.
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

        List<ShuttleAssignment> resolved = new ArrayList<>(4);
        collapseSegment(source, 0, employerEnd,
                seatCapacity(source, 0, employerEnd), resolved);
        int resolvedEmployer = resolved.size();

        int authoredPlayerSeats = seatCapacity(source, employerEnd, source.size());
        int playerSeats = selectedPlayerSeats >= 0
                ? Math.max(authoredPlayerSeats, selectedPlayerSeats)
                : authoredPlayerSeats;
        collapseSegment(source, employerEnd, source.size(), playerSeats, resolved);
        return new ResolvedManifest(resolved, resolvedEmployer);
    }

    public ResolvedManifest resolveManifest(List<ShuttleAssignment> manifest) {
        return resolveManifest(manifest, -1);
    }

    private void collapseSegment(List<ShuttleAssignment> source,
                                 int from, int to, int requiredSeats,
                                 List<ShuttleAssignment> resolved) {
        if (from >= to || requiredSeats <= 0) return;
        int seatsPerSortie = policy.seatsPerSortie(ShuttleType.AEROSHUTTLE);
        int sorties = (requiredSeats + seatsPerSortie - 1) / seatsPerSortie;
        int firstCycles = (sorties + 1) / 2;
        int secondCycles = sorties / 2;
        resolved.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE,
                firstCycles, seatsPerSortie));
        if (secondCycles > 0) {
            resolved.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE,
                    secondCycles, seatsPerSortie));
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
