package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * A Conquest lane read as a chain of places to take, in order.
 *
 * <p>The tracks are a fence across the map's width; this is the ladder up one
 * of them. A lane is its ordered places from the beachhead to the keep — the
 * outposts and strongpoints {@code conquest-lanes.md} seeds, then the objective
 * itself — and each place holds the compounds the packer stamped inside it. The
 * lane's <b>front</b> is the first place the marines do not hold, which is the
 * only thing on the lane worth assaulting, and everything behind it is ground
 * already won.
 *
 * <p><b>Progress is a chain index, not a coordinate.</b> The forward fraction
 * this replaces measured how far up the map a squad had walked, which on a
 * grown map says nothing about what the marines hold: a track reads 0.8
 * advanced while its strongpoint is still the defenders'. Ownership along the
 * chain is the reading that moves when the battle moves, and moves
 * <em>backwards</em> when a place is retaken, which a fraction of the map
 * cannot do.
 *
 * <p><b>A compound belongs to the place whose ground it stands on.</b> The
 * generator claimed that ground and stamped the compound inside it, and records
 * both on {@link LaneRoute.Link} for exactly this pairing — a commander
 * matching compounds to places by distance would be re-deriving a fact somebody
 * already had, and the two would disagree the first time either moved. A
 * compound on nobody's claimed ground is off the chain: a settlement's supply
 * hub, or the beachhead, which are territory but not rungs.
 *
 * <p>Immutable and faction-neutral, like {@link ConquestTrackLayout}. Ownership
 * is not held here — it changes every few seconds and belongs to
 * {@code CompoundService} — so the questions that depend on it take a predicate
 * over capture-zone ids and are answered against the caller's own frozen frame.
 */
public final class ConquestLaneChain {

    /** A chain with no lanes on it: every question answers "not on a lane". */
    public static final ConquestLaneChain NONE =
            new ConquestLaneChain(List.of(), List.of());

    /**
     * One place on a lane, with the compounds standing on it.
     *
     * @param lane          which lane, counting from zero as the tracks do
     * @param index         which rung, counting from the beachhead
     * @param place         the precinct's own name, for evidence
     * @param band          the ladder band, {@link LaneRoute#OBJECTIVE_BAND}
     *                      for the thing the lane leads to
     * @param routeIndex    where this place stands along the lane's route
     * @param captureZoneIds every compound stamped on this place's ground
     */
    public record Link(int lane, int index, String place, int band,
                       int cellX, int cellY, int routeIndex,
                       int[] captureZoneIds) {

        public Link {
            captureZoneIds = captureZoneIds.clone();
        }

        @Override public int[] captureZoneIds() {
            return captureZoneIds.clone();
        }

        /** Whether every compound on this place is held by the marines. */
        public boolean isHeld(IntPredicate marineHeld) {
            for (int zone : captureZoneIds) {
                if (!marineHeld.test(zone)) return false;
            }
            return captureZoneIds.length > 0;
        }

        /**
         * Whether anything at all stands here. A rung the map found no room
         * for, or one whose buildings never registered as compounds, is a place
         * with nothing to take: it is stepped over rather than blocking the
         * lane behind a capture that can never happen.
         */
        public boolean hasCompounds() {
            return captureZoneIds.length > 0;
        }
    }

    /** What the caller knows about one compound: which it is and where. */
    public record Compound(int captureZoneId, int anchorX, int anchorY) { }

    private final List<List<Link>> lanes;
    private final List<List<LaneRoute.Cell>> routes;
    /** capture zone id → {lane, link index}; sparse, so a plain map of arrays. */
    private final Map<Integer, int[]> placeOfCompound;

    private ConquestLaneChain(List<List<Link>> lanes,
                              List<List<LaneRoute.Cell>> routes) {
        this.lanes = lanes;
        this.routes = routes;
        Map<Integer, int[]> places = new HashMap<>();
        for (List<Link> lane : lanes) {
            for (Link link : lane) {
                for (int zone : link.captureZoneIds()) {
                    // First lane wins, because the objective stands on all of
                    // them and "which lane is the keep on" has no true answer.
                    places.putIfAbsent(zone, new int[]{link.lane(), link.index()});
                }
            }
        }
        this.placeOfCompound = places;
    }

    /**
     * The chains a map's recorded lanes and a battle's compounds make together.
     *
     * <p>A compound is placed on the link whose claimed ground holds its
     * anchor; where two claims overlap — bounds are rectangles and claims are
     * not — the nearer place wins. A compound on no lane's ground is left off
     * every chain rather than attached to the nearest one: being near a lane is
     * not standing on it.
     */
    public static ConquestLaneChain of(List<LaneRoute> routes,
                                       List<Compound> compounds) {
        if (routes == null || routes.isEmpty()) return NONE;
        List<List<LaneRoute.Cell>> cells = new ArrayList<>();
        List<List<int[]>> claimed = new ArrayList<>();
        for (LaneRoute route : routes) {
            cells.add(route.route());
            List<int[]> mine = new ArrayList<>();
            for (int i = 0; i < route.links().size(); i++) mine.add(new int[0]);
            claimed.add(mine);
        }
        for (Compound compound : compounds == null ? List.<Compound>of() : compounds) {
            for (int lane = 0; lane < routes.size(); lane++) {
                int index = linkFor(routes.get(lane), compound);
                if (index < 0) continue;
                List<int[]> links = claimed.get(lane);
                links.set(index, append(links.get(index), compound.captureZoneId()));
            }
        }
        List<List<Link>> lanes = new ArrayList<>();
        for (int lane = 0; lane < routes.size(); lane++) {
            LaneRoute route = routes.get(lane);
            List<Link> links = new ArrayList<>();
            for (int index = 0; index < route.links().size(); index++) {
                LaneRoute.Link at = route.links().get(index);
                int[] zones = claimed.get(lane).get(index);
                Arrays.sort(zones);
                links.add(new Link(route.lane(), index, at.place(), at.band(),
                        at.x(), at.y(), at.routeIndex(), zones));
            }
            lanes.add(List.copyOf(links));
        }
        return new ConquestLaneChain(List.copyOf(lanes), List.copyOf(cells));
    }

    /**
     * Which rung of this lane the compound stands on, or {@code -1} for one
     * standing on none of its places.
     *
     * <p>Asked once per lane rather than once for the whole map, because
     * <b>every lane ends at the same objective</b>: the keep is the last link
     * of all three chains, and a compound awarded to one lane alone would leave
     * the others reading as finished with the fortress still standing. Within a
     * lane the nearer place wins where two claim boxes overlap — bounds are
     * rectangles and claims are not — and ties go to the outer rung, so the
     * reading is the same on every replay.
     */
    private static int linkFor(LaneRoute route, Compound compound) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        List<LaneRoute.Link> links = route.links();
        for (int index = 0; index < links.size(); index++) {
            LaneRoute.Link link = links.get(index);
            if (!link.claims(compound.anchorX(), compound.anchorY())) continue;
            long dx = link.x() - (long) compound.anchorX();
            long dy = link.y() - (long) compound.anchorY();
            long distance = dx * dx + dy * dy;
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            best = index;
        }
        return best;
    }

    private static int[] append(int[] zones, int zone) {
        for (int held : zones) if (held == zone) return zones;
        int[] out = Arrays.copyOf(zones, zones.length + 1);
        out[zones.length] = zone;
        return out;
    }

    /** How many lanes this map laid; zero for a map that laid none. */
    public int laneCount() {
        return lanes.size();
    }

    /** Whether any lane at all carries a place with something on it to take. */
    public boolean hasChains() {
        for (List<Link> lane : lanes) {
            for (Link link : lane) if (link.hasCompounds()) return true;
        }
        return false;
    }

    /** One lane's places, from the beachhead to the objective. */
    public List<Link> links(int lane) {
        if (lane < 0 || lane >= lanes.size()) return List.of();
        return lanes.get(lane);
    }

    /** One lane's recorded route, cell by cell. */
    public List<LaneRoute.Cell> route(int lane) {
        if (lane < 0 || lane >= routes.size()) return List.of();
        return routes.get(lane);
    }

    /** The lane this compound stands on, or {@code -1} for one off every chain. */
    public int laneOfCompound(int captureZoneId) {
        int[] at = placeOfCompound.get(captureZoneId);
        return at == null ? -1 : at[0];
    }

    /** Which rung this compound stands on, or {@code -1} for one off every chain. */
    public int linkOfCompound(int captureZoneId) {
        int[] at = placeOfCompound.get(captureZoneId);
        return at == null ? -1 : at[1];
    }

    /** Whether this compound stands on any lane's ground at all. */
    public boolean isOnChain(int captureZoneId) {
        return placeOfCompound.containsKey(captureZoneId);
    }

    /**
     * Which rung of the named lane this compound stands on, or {@code -1} when
     * it stands on none of that lane's places.
     *
     * <p>Asked of a lane rather than of the map because the objective is on
     * every chain: a squad on lane 2 approaching the keep is approaching its
     * own lane's last link, not lane 0's.
     */
    public int linkIndexOn(int lane, int captureZoneId) {
        for (Link link : links(lane)) {
            for (int zone : link.captureZoneIds()) {
                if (zone == captureZoneId) return link.index();
            }
        }
        return -1;
    }

    /**
     * The first place on this lane the marines do not hold — the lane's front,
     * and the only place on it worth assaulting.
     *
     * <p>Places with nothing on them are stepped over: a rung the map found no
     * room for cannot be taken and must not stop the lane behind it. A lane
     * held to its end answers {@link #links(int)}{@code .size()}, which is what
     * convergence reads as "this lane is finished".
     */
    public int frontLink(int lane, IntPredicate marineHeld) {
        List<Link> links = links(lane);
        for (int i = 0; i < links.size(); i++) {
            Link link = links.get(i);
            if (!link.hasCompounds()) continue;
            if (!link.isHeld(marineHeld)) return i;
        }
        return links.size();
    }

    /**
     * How much of this lane the marines hold, as places rather than ground:
     * zero at the beachhead and one when the whole chain is theirs.
     *
     * <p>{@code -1} for a lane with nothing on it, which is a lane the report
     * should decline to score rather than call finished.
     */
    public float progress(int lane, IntPredicate marineHeld) {
        List<Link> links = links(lane);
        int taken = 0;
        int total = 0;
        for (Link link : links) {
            if (!link.hasCompounds()) continue;
            total++;
            if (link.isHeld(marineHeld)) taken++;
        }
        if (total == 0) return -1f;
        return (float) taken / total;
    }

    /**
     * Where along a lane's route a cell stands, by the nearest route cell.
     *
     * <p>This is what replaces the forward coordinate for a lane that bends: a
     * squad's progress toward the next place is how far along the recorded road
     * it has come, not how far up the map. {@code -1} when the lane recorded no
     * route.
     */
    public int routeIndexNear(int lane, float x, float y) {
        List<LaneRoute.Cell> cells = route(lane);
        if (cells.isEmpty()) return -1;
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < cells.size(); i++) {
            LaneRoute.Cell cell = cells.get(i);
            double dx = cell.x() + 0.5 - x;
            double dy = cell.y() + 0.5 - y;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** How many cells this lane's route runs for. */
    public int routeLength(int lane) {
        return route(lane).size();
    }

    /** The route cell at {@code index}, or {@code null} when there is none. */
    public LaneRoute.Cell routeCell(int lane, int index) {
        List<LaneRoute.Cell> cells = route(lane);
        if (index < 0 || index >= cells.size()) return null;
        return cells.get(index);
    }
}
