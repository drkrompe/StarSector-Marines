package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.ambient.AmbientThreatPolicy;
import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.ambient.Shift;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every route a generated deck hands out can actually be walked.
 *
 * <p>An ambient route is derived from whatever the fill happened to put down,
 * and the fill is seeded — so the question "does this work" has no answer for a
 * single deck. A rack laid one cell further inboard, a hatch cut on the other
 * side of a mess, a range whose ready end came out against a bulkhead: any of
 * those produces a route that reads fine in the data and strands whoever is
 * given it. The failure is silent by nature. The route clock keeps advancing,
 * the actor keeps repathing, and what you see is somebody walking into a wall
 * for the whole watch.
 *
 * <p>So this sweeps hulls and seeds and asks the only two things that cannot be
 * checked by looking: that every stop stands on floor somebody can occupy, and
 * that consecutive stops are connected by a path the game's own pathfinder will
 * find. Both are checked against {@link GridPathfinder}, not against a private
 * flood fill, because a route is walked by the pathfinder and an agreement with
 * anything else is worth nothing.
 *
 * <p>Failures accumulate rather than throwing at the first one. A layout defect
 * usually shows up on several seeds at once, and the shape of the whole set is
 * what says whether it is a bad fitting or a bad deck.
 */
class AmbientRouteSweepTest {

    /** Seeds per hull. Enough to shake out a fill decision, few enough to stay quick. */
    private static final int SEEDS = 6;

    /** Members of each shift to check; beyond this the fixture assignment repeats. */
    private static final int MEMBERS = 6;

    /**
     * Hulls worth sweeping, which is to say hulls whose programs differ.
     *
     * <p>A transport is mostly barracks, a warship mostly crew quarters, a
     * carrier is dominated by its bays, and a freighter is mostly hold — and it
     * is the sparse programs that strand people, because a deck with one mess
     * makes every berth on the ship reach for the same room.
     */
    private record Hull(String name, HullClass hullClass, HullRole role,
                        int minCrew, int maxCrew, int cargo, float aspect) {}

    private static final List<Hull> FLEET = List.of(
            new Hull("light transport", HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                    10, 250, 50, 0.28f),
            new Hull("heavy transport", HullClass.CAPITAL, HullRole.TROOP_TRANSPORT,
                    60, 400, 250, 0.34f),
            new Hull("warship", HullClass.CRUISER, HullRole.WARSHIP,
                    200, 300, 100, 0.30f),
            new Hull("carrier", HullClass.CAPITAL, HullRole.CARRIER,
                    150, 400, 200, 0.40f),
            new Hull("freighter", HullClass.CRUISER, HullRole.FREIGHTER,
                    40, 120, 800, 0.32f));

    @Test
    void everyRouteAGeneratedDeckHandsOutCanBeWalked() {
        List<String> stranded = new ArrayList<>();
        int routes = 0;

        for (Hull hull : FLEET) {
            DeckSizing.DeckPlan plan = DeckSizing.planFor(hull.hullClass(), hull.role(),
                    hull.minCrew(), hull.maxCrew(), hull.cargo(), hull.aspect());
            for (long seed = 1; seed <= SEEDS; seed++) {
                ShipDeckGenerator generator = new ShipDeckGenerator();
                MapResult deck = generator.generateDeck(plan, seed, null);
                DeckGraph graph = generator.getLastDeckGraph();

                // Every berth full. A bay's servicing job only exists while a
                // machine is parked in it, and an empty ship would leave the
                // richest rotation on the deck untested.
                boolean[] berthed = new boolean[deck.gantries.size()];
                Arrays.fill(berthed, true);

                for (DeckGraph.Compartment room : graph.compartments()) {
                    for (CrewRole role : CrewRole.values()) {
                        Shift bill = Shift.postedAt(role, room, graph.compartments(),
                                deck.fixtureTasks, berthed,
                                AmbientThreatPolicy.HOSTILE_COMBATANT);
                        int hands = Math.min(MEMBERS, bill.capacity());
                        for (int index = 0; index < hands; index++) {
                            AmbientTaskRoute route = bill.member(index);
                            if (route == null) continue;
                            routes++;
                            walk(deck.grid, route,
                                    hull.name() + " seed " + seed + " " + room.purpose(),
                                    stranded);
                        }
                    }
                }
            }
        }

        assertTrue(routes > 200,
                "the sweep only built " + routes + " routes, so it is not testing much");
        assertTrue(stranded.isEmpty(),
                stranded.size() + " of " + routes + " routes cannot be walked:\n  "
                        + String.join("\n  ", stranded.subList(0, Math.min(12, stranded.size()))));
    }

    /**
     * Check one route the way the ambient service will drive it: from where the
     * actor is spawned, round the stops in order, and back to the first.
     */
    private static void walk(NavigationGrid grid, AmbientTaskRoute route,
                             String where, List<String> stranded) {
        List<AmbientTaskRoute.Stop> stops = route.stops();
        if (stops.isEmpty()) {
            stranded.add(where + " " + route.id() + ": no stops at all");
            return;
        }

        AmbientTaskRoute.Stop start = AmbientTaskService.standingPlace(route, 0f);
        int fromX = (int) Math.floor(start.worldX());
        int fromY = (int) Math.floor(start.worldY());
        if (!standable(grid, fromX, fromY)) {
            stranded.add(where + " " + route.id() + ": starts inside " + fromX + "," + fromY);
            return;
        }

        for (int step = 0; step <= stops.size(); step++) {
            AmbientTaskRoute.Stop stop = stops.get(step % stops.size());
            int toX = (int) Math.floor(stop.worldX());
            int toY = (int) Math.floor(stop.worldY());
            if (!standable(grid, toX, toY)) {
                stranded.add(where + " " + route.id() + " stop " + (step % stops.size())
                        + " (" + stop.activity() + "): stands on " + toX + "," + toY
                        + ", which is not floor");
                return;
            }
            if ((fromX != toX || fromY != toY)
                    && Paths.isEmpty(GridPathfinder.findPath(grid, fromX, fromY, toX, toY))) {
                stranded.add(where + " " + route.id() + ": no way from " + fromX + "," + fromY
                        + " to " + stop.activity() + " at " + toX + "," + toY);
                return;
            }
            fromX = toX;
            fromY = toY;
        }
    }

    private static boolean standable(NavigationGrid grid, int x, int y) {
        return x >= 0 && y >= 0 && x < grid.getWidth() && y < grid.getHeight()
                && grid.isWalkable(x, y);
    }
}
