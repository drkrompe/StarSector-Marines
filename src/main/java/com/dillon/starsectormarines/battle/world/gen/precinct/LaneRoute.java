package com.dillon.starsectormarines.battle.world.gen.precinct;

import java.util.List;

/**
 * Where one lane actually runs on the finished map: its places in path order
 * and the road between them.
 *
 * <p>A {@link LanePath} is a statement made before the map exists — waypoints
 * as fractions, seeds jittered inside a lane, claims not yet grown. This is
 * what came of it. The links are the places that stood up, ordered from the
 * beachhead to the keep, and the route is the cheapest walkable way along the
 * road network between each pair of them, read back once every wall and gate on
 * the map is where it will be.
 *
 * <p><b>The generator owes the route; the commander owes what is done along
 * it.</b> A lane's chain of compounds is what a Conquest is measured in, and
 * staging on a road the ground actually follows is only possible if somebody
 * wrote the road down. Re-deriving it at battle time would be a second answer
 * to a question the generator had already answered, and the two would disagree
 * the first time either moved.
 *
 * @param lane  which lane this is, counting from zero the way the command
 *              tracks do
 * @param links the lane's places from the beachhead to the objective
 * @param route every cell of the way through them, in order
 */
public record LaneRoute(int lane, List<Link> links, List<Cell> route) {

    public LaneRoute {
        if (lane < 0) throw new IllegalArgumentException("no lane numbered " + lane);
        links = links == null ? List.of() : List.copyOf(links);
        route = route == null ? List.of() : List.copyOf(route);
    }

    /** One cell of a route. */
    public record Cell(int x, int y) { }

    /**
     * One place on a lane.
     *
     * <p>The cell is a <em>walkable</em> one of the place, never its seed: a
     * precinct's seed is where its roads grew from and a garrison's is usually
     * inside its own wall by the time the packer has finished. What a route has
     * to reach is somewhere a marine can stand.
     *
     * <p><b>The ground is recorded as well as the cell, because a link is a
     * place and a commander has to know what stands on it.</b> A lane's chain
     * is a chain of compounds, and the only party that knows which compound
     * belongs to which place is the generator that put them both there — a
     * commander pairing them by distance would be guessing at a fact somebody
     * already had. The bounds are the extent of the precinct's claim, which is
     * what the packer stamped its buildings inside of.
     *
     * @param place      the precinct's own name
     * @param band       which rung of the ladder, {@link #OBJECTIVE_BAND} for
     *                   the thing the lane leads to
     * @param routeIndex where in {@link LaneRoute#route} this link stands
     * @param claimLeft  west edge of the ground this place claims
     * @param claimTop   north edge of the ground this place claims
     * @param claimRight east edge, inclusive
     * @param claimBottom south edge, inclusive
     */
    public record Link(String place, int band, int x, int y, int routeIndex,
                       int claimLeft, int claimTop, int claimRight,
                       int claimBottom) {

        /** A place whose claim nobody measured: its own cell and nothing else. */
        public Link(String place, int band, int x, int y, int routeIndex) {
            this(place, band, x, y, routeIndex, x, y, x, y);
        }

        /** Whether this place's claimed ground holds the given cell. */
        public boolean claims(int cellX, int cellY) {
            return cellX >= claimLeft && cellX <= claimRight
                    && cellY >= claimTop && cellY <= claimBottom;
        }
    }

    /** The band the objective itself stands in: the end of every lane. */
    public static final int OBJECTIVE_BAND = 0;

    /** Whether the route was walked end to end, which every lane owes. */
    public boolean isWalked() {
        return links.size() >= 2 && !route.isEmpty();
    }
}
