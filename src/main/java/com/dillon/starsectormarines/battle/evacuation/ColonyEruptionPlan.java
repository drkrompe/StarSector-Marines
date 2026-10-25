package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where and when the colony breaks open: the eruption sites of one Colony
 * Breach battle, chosen at setup and fixed for the rest of it.
 *
 * <p>This is the seam bores plug into ({@link SwarmBores#open}, `colony-breach-nouns.md` Bore). The breach sequence
 * owns the clock and publishes each site as it erupts; whatever stands a bore
 * up on a site reads it from here. Nothing in this class spawns anything.
 *
 * <p><b>Sites are places, not cells picked off the map.</b> Each candidate is
 * a building of the colony, and the site is the open ground outside its door:
 * a bore comes up in the street a family lived on, not in a field or inside
 * the walls. A site is never inside the shelter's clearance, the lift's safe
 * ring or a marine landing pad's, and never where the swarm could not walk
 * from it to the shelter.
 *
 * <p><b>The bloom came in from the outskirts.</b> The first wave stands on the
 * town's near edge either side of the landing field (owner, 2026-10-25: bores
 * flank the company's landing zone and grow inward), already open when the
 * company comes down; the mother bore comes up later at the far end of one
 * flank, out on the deep outskirts. The rest of the eligible places are kept
 * ({@link #places}) for the bores the fed ones bud further in. Every choice is
 * a deterministic function of the seed and the geometry, so the same battle
 * breaks open in the same places every replay.
 */
public final class ColonyEruptionPlan {

    /** Which part of the colony a site threatens. */
    public enum Role {
        /** On the outskirts to one side of the landing field: the first wave, open at landfall. */
        FLANK,
        /** The one site whose bore is the mother bore. */
        MOTHER
    }

    /**
     * One eruption site.
     *
     * @param number             the bore's number in eruption order, from 1;
     *                           {@code 0} for the mother bore, which is named
     *                           rather than numbered in the beat table
     * @param x                  the site's cell
     * @param y                  the site's cell
     * @param role               what the site threatens
     * @param delayTicks         ticks after the tremor at which it erupts
     * @param placeKind          the kind of building the site stands outside
     */
    public record Site(int number, int x, int y, Role role, int delayTicks,
                       PointOfInterest.Kind placeKind) {
        public boolean mother() { return role == Role.MOTHER; }
    }

    /** First-wave sites a full colony plan carries. */
    public static final int FIRST_WAVE_SITES = 4;
    /** Seconds after the tremor, at landfall, that the mother bore opens. */
    public static final float MOTHER_ERUPTION_SECONDS = 300f;
    /** A flank site stands at least this far off the field's inward line, as a share of its distance from the field. */
    static final float FLANK_SHARE = 0.4f;
    /** Cells of open ground searched around a building's door for a site. */
    private static final int DOORSTEP_RADIUS = 3;

    private final List<Site> sites;
    private final int requestedFirstWave;
    private final List<int[]> places;
    private final int heartX, heartY, spacing;

    private ColonyEruptionPlan(List<Site> sites, int requestedFirstWave, List<int[]> places, int heartX, int heartY,
                               int spacing) {
        this.sites = List.copyOf(sites);
        this.requestedFirstWave = requestedFirstWave;
        this.places = List.copyOf(places);
        this.heartX = heartX;
        this.heartY = heartY;
        this.spacing = spacing;
    }

    /** The town's heart the bores bud toward: the shelter. */
    public int heartX() { return heartX; }

    public int heartY() { return heartY; }

    /** How close two sites, or two bores, may stand ({@link #siteSpacing}). */
    public int spacing() { return spacing; }

    /**
     * Every eligible place no site took, {@code {x, y, kindOrdinal}} each, in a seeded order: where a fed bore may
     * bud a new one further in ({@link SwarmBores}, Budding).
     */
    public List<int[]> places() { return places; }

    /** Sites in eruption order: the first wave by number, then the mother. */
    public List<Site> sites() { return sites; }

    /** The mother site, or {@code null} when the colony had no room for one. */
    public Site mother() {
        for (Site site : sites) if (site.mother()) return site;
        return null;
    }

    /** First-wave sites, without the mother. */
    public int firstWaveCount() {
        int count = 0;
        for (Site site : sites) if (!site.mother()) count++;
        return count;
    }

    /** Whether every requested first-wave site and the mother were seated. */
    public boolean isComplete() {
        return firstWaveCount() == requestedFirstWave && mother() != null;
    }

    /** The tick a site erupts, given the tick of the tremor. */
    public static int eruptionTick(Site site, int tremorTick) {
        return tremorTick + site.delayTicks();
    }

    /**
     * What the selection needs to know about the battle, with the map already
     * reduced to candidate cells. A pure value, so selection can be asked
     * directly.
     *
     * @param width          map width in cells
     * @param height         map height in cells
     * @param shelterX       the shelter's interior anchor
     * @param shelterY       the shelter's interior anchor
     * @param liftX          the lift's centre
     * @param liftY          the lift's centre
     * @param liftSafeRadius the lift's protected Chebyshev radius
     * @param candidates     doorstep cells, {@code {x, y, kindOrdinal}} each
     * @param landings       marine landing centres, {@code {x, y}} each
     */
    public record Geometry(int width, int height, int shelterX, int shelterY,
                           int liftX, int liftY, int liftSafeRadius,
                           List<int[]> candidates, List<int[]> landings) {
        public Geometry {
            candidates = List.copyOf(candidates);
            landings = List.copyOf(landings);
        }
    }

    /**
     * Builds the geometry off a placed rescue and selects its sites.
     *
     * @param pads the marine landing pads; a site keeps clear of each
     */
    public static ColonyEruptionPlan forColony(NavigationGrid grid, CellTopology topology,
                                               List<PointOfInterest> places,
                                               CivilianEvacuationPlacement placement,
                                               List<LandingPad> pads, long seed) {
        boolean[] reachable = SwarmDefenseRoster.reachableFromShelter(grid, placement);
        List<int[]> candidates = new ArrayList<>();
        for (PointOfInterest place : places) {
            if (place == null) continue;
            if (place.interiorAnchorX == placement.shelterX
                    && place.interiorAnchorY == placement.shelterY) continue;
            int[] doorstep = doorstep(grid, topology, reachable,
                    place.anchorCellX, place.anchorCellY);
            if (doorstep != null) {
                candidates.add(new int[]{doorstep[0], doorstep[1], place.kind.ordinal()});
            }
        }
        List<int[]> landings = new ArrayList<>();
        if (pads != null) for (LandingPad pad : pads) landings.add(new int[]{pad.centerX, pad.centerY});
        return select(new Geometry(grid.getWidth(), grid.getHeight(),
                placement.shelterX, placement.shelterY, placement.liftX, placement.liftY,
                placement.formationRadius() + 2, candidates, landings),
                FIRST_WAVE_SITES, seed);
    }

    /**
     * The nearest open, swarm-reachable, outdoor cell to a building's door,
     * ties broken by row then column; {@code null} when there is none close.
     */
    private static int[] doorstep(NavigationGrid grid, CellTopology topology,
                                  boolean[] reachable, int doorX, int doorY) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = doorY - DOORSTEP_RADIUS; y <= doorY + DOORSTEP_RADIUS; y++) {
            for (int x = doorX - DOORSTEP_RADIUS; x <= doorX + DOORSTEP_RADIUS; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) continue;
                if (topology != null && topology.getBuildingId(x, y) != 0) continue;
                if (!reachable[grid.index(x, y)]) continue;
                int distance = Math.abs(x - doorX) + Math.abs(y - doorY);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    /** How close two sites may be: an eighth of the map's short side, never under ten cells. */
    static int siteSpacing(int width, int height) {
        return Math.max(10, Math.min(width, height) / 8);
    }

    /** How far a site keeps from the shelter. */
    static int shelterClearance(int width, int height) {
        return Math.max(CivilianEvacuationPlacement.SHELTER_ZONE_RADIUS + 3,
                Math.min(width, height) / 16);
    }

    /** How far a site keeps from the lift, beyond its safe ring. */
    static int liftClearance(int width, int height) {
        return Math.max(4, Math.min(width, height) / 24);
    }

    /** How far a site keeps from a marine landing pad. */
    static int landingClearance(int width, int height) {
        return Math.max(6, Math.min(width, height) / 20);
    }

    /**
     * Selects up to {@code firstWave} first-wave sites and one mother site.
     *
     * <p>Order of seating: half the wave on each flank of the landing field
     * (a place off the line from the field in to the shelter by at least
     * {@link #FLANK_SHARE} of its distance), nearest the field first, the seed
     * shuffling places within a site's spacing; then whatever flank place is
     * left, then the outskirts straight ahead of the field. The mother last:
     * the place spaced from the rest standing furthest out along a flank, the
     * flank away from the lift when the lift stands off to one side. A map
     * that cannot seat them all returns a short plan rather than failing;
     * {@link #isComplete} says which.
     */
    public static ColonyEruptionPlan select(Geometry g, int firstWave, long seed) {
        int spacing = siteSpacing(g.width(), g.height());
        int shelterClear = shelterClearance(g.width(), g.height());
        int liftClear = g.liftSafeRadius() + liftClearance(g.width(), g.height());
        int landingClear = landingClearance(g.width(), g.height());

        List<int[]> eligible = new ArrayList<>();
        for (int[] c : g.candidates()) {
            if (Math.abs(c[0] - g.shelterX()) + Math.abs(c[1] - g.shelterY()) < shelterClear) continue;
            if (Math.max(Math.abs(c[0] - g.liftX()), Math.abs(c[1] - g.liftY())) <= liftClear) continue;
            boolean nearLanding = false;
            for (int[] pad : g.landings()) {
                if (Math.max(Math.abs(c[0] - pad[0]), Math.abs(c[1] - pad[1])) <= landingClear) {
                    nearLanding = true;
                    break;
                }
            }
            if (nearLanding) continue;
            eligible.add(c);
        }
        // Deterministic shuffle: seeded priority, cell as the tie-break.
        eligible.sort(Comparator
                .comparingLong((int[] c) -> priority(seed, c[1] * g.width() + c[0]))
                .thenComparingInt(c -> c[1] * g.width() + c[0]));

        // The landing field the company comes down on: its pads' centre, or the lift on it.
        float fx = g.liftX(), fy = g.liftY();
        if (!g.landings().isEmpty()) {
            fx = 0f;
            fy = 0f;
            for (int[] pad : g.landings()) {
                fx += pad[0];
                fy += pad[1];
            }
            fx /= g.landings().size();
            fy /= g.landings().size();
        }
        // Inward, from the field toward the shelter at the town's heart.
        float ux = g.shelterX() - fx, uy = g.shelterY() - fy;
        float ul = (float) Math.sqrt(ux * ux + uy * uy);
        if (ul < 1e-3f) {
            ux = 0f;
            uy = 1f;
        } else {
            ux /= ul;
            uy /= ul;
        }
        List<int[]> left = new ArrayList<>();
        List<int[]> right = new ArrayList<>();
        List<int[]> ahead = new ArrayList<>();
        for (int[] c : eligible) {
            float px = c[0] - fx, py = c[1] - fy;
            float across = ux * py - uy * px;
            float distance = (float) Math.sqrt(px * px + py * py);
            if (Math.abs(across) < distance * FLANK_SHARE) ahead.add(c);
            else if (across >= 0f) left.add(c);
            else right.add(c);
        }
        // Outskirts first: nearest the field, the seed shuffling places within a site's spacing of each other.
        final float ffx = fx, ffy = fy;
        Comparator<int[]> outskirts = Comparator.comparingDouble((int[] c) ->
                Math.hypot(c[0] - ffx, c[1] - ffy) + spacing * unitOf(priority(seed, c[1] * g.width() + c[0])))
                .thenComparingInt(c -> c[1] * g.width() + c[0]);
        left.sort(outskirts);
        right.sort(outskirts);
        ahead.sort(outskirts);

        List<int[]> chosen = new ArrayList<>();
        int perFlank = Math.max(1, firstWave / 2);
        takeSpaced(left, perFlank, chosen, spacing);
        takeSpaced(right, perFlank, chosen, spacing);
        // Whatever is still owed: the flanks' next, then the outskirts straight ahead of the field.
        if (chosen.size() < firstWave) {
            List<int[]> flanks = new ArrayList<>(left);
            flanks.addAll(right);
            flanks.sort(outskirts);
            takeSpaced(flanks, firstWave - chosen.size(), chosen, spacing);
        }
        if (chosen.size() < firstWave) takeSpaced(ahead, firstWave - chosen.size(), chosen, spacing);
        while (chosen.size() > firstWave) chosen.remove(chosen.size() - 1);

        // The mother: the deep outskirts, as far out along a flank as a place spaced from the wave stands, on the
        // flank away from the lift, so the hive objective is never at the cohort's boats.
        float liftAcross = ux * (g.liftY() - fy) - uy * (g.liftX() - fx);
        int[] mother = null;
        double motherReach = Double.NEGATIVE_INFINITY;
        for (int[] c : eligible) {
            if (!spacedFrom(c, chosen, spacing)) continue;
            float px = c[0] - fx, py = c[1] - fy;
            float across = ux * py - uy * px;
            boolean liftSide = Math.abs(liftAcross) >= spacing && Math.signum(across) == Math.signum(liftAcross);
            double reach = Math.abs(across) - 0.5 * (ux * px + uy * py) - (liftSide ? 100_000 : 0);
            if (reach > motherReach) {
                motherReach = reach;
                mother = c;
            }
        }

        // Numbered nearest the field first; all open at once, when the company lands.
        chosen.sort(Comparator.comparingDouble((int[] c) -> Math.hypot(c[0] - ffx, c[1] - ffy))
                .thenComparingInt(c -> c[1] * g.width() + c[0]));
        List<Site> sites = new ArrayList<>();
        int number = 1;
        for (int[] c : chosen) sites.add(new Site(number++, c[0], c[1], Role.FLANK, 0, kind(c[2])));
        if (mother != null) {
            sites.add(new Site(0, mother[0], mother[1], Role.MOTHER,
                    seconds(MOTHER_ERUPTION_SECONDS), kind(mother[2])));
        }
        List<int[]> rest = new ArrayList<>();
        for (int[] c : eligible) if (!chosen.contains(c) && c != mother) rest.add(c);
        return new ColonyEruptionPlan(sites, firstWave, rest, g.shelterX(), g.shelterY(), spacing);
    }

    private static void takeSpaced(List<int[]> from, int wanted, List<int[]> chosen, int spacing) {
        int taken = 0;
        for (int[] c : from) {
            if (taken >= wanted) return;
            if (chosen.contains(c) || !spacedFrom(c, chosen, spacing)) continue;
            chosen.add(c);
            taken++;
        }
    }

    private static boolean spacedFrom(int[] c, List<int[]> chosen, int spacing) {
        for (int[] other : chosen) {
            int dx = c[0] - other[0], dy = c[1] - other[1];
            if (dx * dx + dy * dy < spacing * spacing) return false;
        }
        return true;
    }

    private static PointOfInterest.Kind kind(int ordinal) {
        PointOfInterest.Kind[] kinds = PointOfInterest.Kind.values();
        return ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : null;
    }

    private static int seconds(float seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }

    /** A seeded priority as a share in {@code [0, 1)}. */
    private static double unitOf(long priority) {
        return (priority >>> 11) * 0x1.0p-53;
    }

    private static long priority(long seed, int cell) {
        long value = seed ^ (cell * 0x9E3779B97F4A7C15L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
