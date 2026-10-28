package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatFitting;
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

    public ShuttleArrivalPlan withArrivalConfig(ConquestArrivalConfig config) {
        return new ShuttleArrivalPlan(policy, firstPlayerShuttle, config);
    }

    /**
     * The arrival shape with whatever the mission left to the lift filled in.
     *
     * <p>Asked before the map is generated, because the count of pairs is also
     * the count of berthing areas the landing place has to seat. Where the
     * mission stated a shape this returns it unchanged.
     */
    public ConquestArrivalConfig sizedConfig() {
        if (!paired()) return arrivalConfig;
        return OrbitalLift.resolve(arrivalConfig);
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
        // Sized here as well as at the setup site, so a plan whose shape the
        // mission left derived cannot collapse to one pair merely because a
        // caller resolved its manifest without asking the lift first.
        collapseSegment(source, employerEnd, source.size(), playerSeats,
                OrbitalLift.resolve(arrivalConfig).playerShuttlePairCount(),
                resolved);
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
        int sorties = (int) (((long) requiredSeats + seatsPerSortie - 1L)
                / seatsPerSortie);
        int completePairCycles = sorties / 2;
        int activePairs = Math.min(Math.max(1, requestedPairs),
                Math.max(1, completePairCycles));
        int baseCycles = completePairCycles / activePairs;
        int extraCycles = completePairCycles % activePairs;
        boolean oddSortie = (sorties & 1) != 0;
        int taken = 0;
        for (int pair = 0; pair < activePairs; pair++) {
            int pairedCycles = baseCycles + (pair < extraCycles ? 1 : 0);
            int firstCycles = pairedCycles
                    + (oddSortie && pair == activePairs - 1 ? 1 : 0);
            if (firstCycles > 0) {
                Airframe first = frameFor(source, from, to, taken++);
                resolved.add(new ShuttleAssignment(ShuttleType.fromAirframe(first),
                        first, firstCycles,
                        seatsPerSortie, Math.max(1, firstCycles) * seatsPerSortie));
            }
            if (pairedCycles <= 0) continue;
            Airframe second = frameFor(source, from, to, taken++);
            resolved.add(new ShuttleAssignment(ShuttleType.fromAirframe(second),
                    second, pairedCycles,
                    seatsPerSortie, Math.max(1, pairedCycles) * seatsPerSortie));
        }
    }

    /**
     * Which of the segment's boats a collapsed craft flies as.
     *
     * <p>A collapse is fewer craft than boats on purpose — six committed
     * Aeroshuttles become two that fly more sorties each — so the fit cannot
     * survive one-for-one, and the boats past the collapsed count simply do not
     * make the descent. What is kept is the boats in berth order, which is the
     * same order the manifest was built in: the company's first boats are the
     * ones that go down, fit and all.
     *
     * <p>A boat that handles as an Aeroshuttle does (an Aeroshuttle, a Hermes) makes the descent
     * herself, fit and all. The pair count can be more than the boats, so a craft past the
     * boats repeats them in berth order as an anonymous copy: the same hull and fit, but not the
     * boat, whose loss must settle once. Any heavier hull's plating has nothing to do with the
     * Aeroshuttle that replaces her, and she is not repeated. A cradle does carry through: the
     * Aeroshuttle standing in for a cradled boat inherits its lift rating, so
     * the boat's machine sorties still fly ({@code air-nouns.md}, Lift).
     */
    static Airframe frameFor(List<ShuttleAssignment> source,
                             int from, int to, int index) {
        int span = Math.min(to, source.size()) - from;
        if (index < 0 || span <= 0) return ShuttleType.AEROSHUTTLE;
        ShuttleAssignment origin = source.get(from + index % span);
        if (origin == null) return ShuttleType.AEROSHUTTLE;
        boolean repeat = index >= span;
        if (origin.type.nimbleClass()) {
            return repeat && origin.airframe instanceof FittedBoat boat ? boat.anonymous() : origin.airframe;
        }
        if (repeat) return ShuttleType.AEROSHUTTLE;
        if (origin.airframe instanceof FittedBoat boat && boat.cradled()) {
            return new FittedBoat(ShuttleType.AEROSHUTTLE, BoatFitting.STANDARD_PLATING,
                    BoatFitting.STANDARD_DRIVE, boat.lift(), null);
        }
        return ShuttleType.AEROSHUTTLE;
    }

    private static int seatCapacity(List<ShuttleAssignment> manifest,
                                    int from, int to) {
        long seats = 0L;
        for (int i = Math.max(0, from); i < Math.min(to, manifest.size()); i++) {
            ShuttleAssignment assignment = manifest.get(i);
            if (assignment != null) {
                seats += assignment.embarkedPersonnel;
                if (seats >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
            }
        }
        return (int) seats;
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
