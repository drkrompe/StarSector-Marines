package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable named-site layout for a generated Sabotage battle.
 *
 * <p>The selector fails closed unless the production map can provide exactly
 * {@link #REQUIRED_SITE_COUNT} distinct, reachable objective cells in the
 * defender half. Selection is deterministic and independent of incidental POI
 * list order: non-residential targets are preferred, then wider separation,
 * then a stable spatial ordering breaks ties.
 */
public final class SabotageSiteLayout {

    public static final int REQUIRED_SITE_COUNT = 3;

    /** Stable mission identity and immutable authored facts for one charge site. */
    public record Site(String id, PointOfInterest.Kind kind, int cellX, int cellY) {
        public Site {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("site id must not be blank");
            }
            Objects.requireNonNull(kind, "kind");
        }

        public String displayName() {
            return "Plant charge " + id
                    + ": " + kind.name().toLowerCase(Locale.ROOT);
        }
    }

    private record Candidate(PointOfInterest.Kind kind, int cellX, int cellY) {
        boolean highValue() {
            return kind != PointOfInterest.Kind.RESIDENTIAL;
        }
    }

    private final List<Site> sites;

    private SabotageSiteLayout(List<Site> sites) {
        this.sites = List.copyOf(sites);
    }

    public List<Site> sites() {
        return sites;
    }

    /**
     * Selects the canonical three-site layout for {@code map}.
     *
     * @throws IllegalStateException when the generated map cannot satisfy the
     *         exact-count, reachability, distinctness, and separation contract
     */
    public static SabotageSiteLayout select(MapResult map,
                                            List<LandingPad> landingPads,
                                            int minimumSeparation) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(landingPads, "landingPads");
        if (landingPads.isEmpty()) {
            throw new IllegalArgumentException("Sabotage requires at least one landing pad");
        }
        if (minimumSeparation < 0) {
            throw new IllegalArgumentException("minimumSeparation must be non-negative");
        }

        NavigationGrid grid = map.grid;
        int defenderHalfX = grid.getWidth() / 2;
        Set<Long> distinctCells = new HashSet<>();
        List<Candidate> candidates = new ArrayList<>();
        for (PointOfInterest poi : map.pointsOfInterest) {
            if (poi.centerX() < defenderHalfX) continue;
            int x = poi.interiorAnchorX;
            int y = poi.interiorAnchorY;
            if (x < defenderHalfX) continue;
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
            if (!reachableFromEveryPad(grid, landingPads, x, y)) continue;
            if (!distinctCells.add(cellKey(x, y))) continue;
            candidates.add(new Candidate(poi.kind, x, y));
        }
        candidates.sort(CANDIDATE_ORDER);

        Selection best = chooseBest(candidates, minimumSeparation);
        if (best == null) {
            throw new IllegalStateException("Sabotage map " + grid.getWidth() + "x"
                    + grid.getHeight() + " has " + candidates.size()
                    + " reachable distinct defender-half POIs but cannot place exactly "
                    + REQUIRED_SITE_COUNT + " charge sites with minimum separation "
                    + minimumSeparation);
        }

        List<Candidate> ordered = new ArrayList<>(best.candidates);
        ordered.sort(CANDIDATE_ORDER);
        List<Site> sites = new ArrayList<>(REQUIRED_SITE_COUNT);
        for (int i = 0; i < ordered.size(); i++) {
            Candidate candidate = ordered.get(i);
            sites.add(new Site(String.format(Locale.ROOT, "SAB-%02d", i + 1),
                    candidate.kind, candidate.cellX, candidate.cellY));
        }
        return new SabotageSiteLayout(sites);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SabotageSiteLayout layout
                && sites.equals(layout.sites);
    }

    @Override
    public int hashCode() {
        return sites.hashCode();
    }

    @Override
    public String toString() {
        return "SabotageSiteLayout" + sites;
    }

    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
            .comparingInt((Candidate candidate) -> candidate.highValue() ? 0 : 1)
            .thenComparingInt(Candidate::cellY)
            .thenComparingInt(Candidate::cellX)
            .thenComparing(candidate -> candidate.kind.name());

    private static final class Selection {
        private final List<Candidate> candidates;
        private final int highValueCount;
        private final int minimumDistanceSquared;
        private final long totalDistanceSquared;

        private Selection(List<Candidate> candidates) {
            this.candidates = List.copyOf(candidates);
            int highValue = 0;
            int minimumDistance = Integer.MAX_VALUE;
            long totalDistance = 0L;
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).highValue()) highValue++;
                for (int j = i + 1; j < candidates.size(); j++) {
                    int distance = distanceSquared(candidates.get(i), candidates.get(j));
                    minimumDistance = Math.min(minimumDistance, distance);
                    totalDistance += distance;
                }
            }
            this.highValueCount = highValue;
            this.minimumDistanceSquared = minimumDistance;
            this.totalDistanceSquared = totalDistance;
        }
    }

    private static Selection chooseBest(List<Candidate> candidates,
                                        int minimumSeparation) {
        Selection[] best = new Selection[1];
        choose(candidates, minimumSeparation * minimumSeparation, 0,
                new ArrayList<>(REQUIRED_SITE_COUNT), best);
        return best[0];
    }

    private static void choose(List<Candidate> candidates, int minimumSeparationSquared,
                               int from, List<Candidate> selected, Selection[] best) {
        if (selected.size() == REQUIRED_SITE_COUNT) {
            Selection candidate = new Selection(selected);
            if (best[0] == null || betterThan(candidate, best[0])) best[0] = candidate;
            return;
        }
        int remaining = REQUIRED_SITE_COUNT - selected.size();
        for (int i = from; i <= candidates.size() - remaining; i++) {
            Candidate candidate = candidates.get(i);
            if (!farEnough(candidate, selected, minimumSeparationSquared)) continue;
            selected.add(candidate);
            choose(candidates, minimumSeparationSquared, i + 1, selected, best);
            selected.remove(selected.size() - 1);
        }
    }

    private static boolean betterThan(Selection candidate, Selection incumbent) {
        if (candidate.highValueCount != incumbent.highValueCount) {
            return candidate.highValueCount > incumbent.highValueCount;
        }
        if (candidate.minimumDistanceSquared != incumbent.minimumDistanceSquared) {
            return candidate.minimumDistanceSquared > incumbent.minimumDistanceSquared;
        }
        if (candidate.totalDistanceSquared != incumbent.totalDistanceSquared) {
            return candidate.totalDistanceSquared > incumbent.totalDistanceSquared;
        }
        for (int i = 0; i < REQUIRED_SITE_COUNT; i++) {
            int compare = CANDIDATE_ORDER.compare(
                    candidate.candidates.get(i), incumbent.candidates.get(i));
            if (compare != 0) return compare < 0;
        }
        return false;
    }

    private static boolean farEnough(Candidate candidate, List<Candidate> selected,
                                     int minimumSeparationSquared) {
        for (Candidate previous : selected) {
            if (distanceSquared(candidate, previous) < minimumSeparationSquared) return false;
        }
        return true;
    }

    private static int distanceSquared(Candidate first, Candidate second) {
        int dx = first.cellX - second.cellX;
        int dy = first.cellY - second.cellY;
        return dx * dx + dy * dy;
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    private static boolean reachableFromEveryPad(NavigationGrid grid,
                                                 List<LandingPad> landingPads,
                                                 int targetX, int targetY) {
        for (LandingPad pad : landingPads) {
            if (GridPathfinder.findPath(grid, pad.centerX, pad.centerY,
                    targetX, targetY).length == 0) return false;
        }
        return true;
    }
}
