package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a fitted compartment is actually used, or merely furnished.
 *
 * <p>A room passes every structural check in the generator and still reads as
 * unfinished when the fill leaves a hole in the middle of it. That is the defect
 * this measures: a compartment whose fixtures hug its bulkheads and leave a
 * five-by-five patch of deck in the centre with nothing on it and no reason for
 * it. It is invisible to a connectivity test — the room is perfectly walkable —
 * and it is exactly what makes a generated interior look generated.
 *
 * <p><b>Measured from the finished deck, not from the fill's own bookkeeping.</b>
 * The fitting knows which cells it reserved as a lane, and asking it would let
 * a fill excuse its own emptiness by declaring the hole a corridor. What is
 * checked here is the outcome: rasterise the fixtures, the machine berths and
 * the deck the room shut, then ask how large a square of nothing is left. A
 * circulation lane is thin by construction and cannot swallow a square; a void
 * can.
 *
 * <p>The refit axis is swept alongside, because {@link RoomFit} claims the same
 * floor holds more when it is fitted better. If that claim is not measurably
 * true then an upgrade is a number going up somewhere, which is the thing the
 * whole spatial-capacity model exists to avoid.
 */
class CompartmentFillSweepTest {

    /** Seeds per hull. Enough to shake out one fitting's bad day. */
    private static final int SEEDS = 6;

    /**
     * The side of the largest square of unexplained deck a fitted compartment
     * may contain. Three is a lane crossing or a doorway apron; four is a room
     * with a hole in it.
     */
    private static final int MAX_VOID = 4;

    /**
     * Compartments smaller than this are exempt from the void check. A room
     * that is nearly all doorway apron has nowhere to put a fixture that is not
     * in somebody's way, and reporting it as under-filled would be reporting
     * that it is small.
     */
    private static final int MEASURABLE_AREA = 24;

    /** Share of a fitted compartment's floor that its fixtures must account for. */
    private static final float MIN_USED = 0.12f;

    /**
     * How many compartments hold a void today, and how many are barely
     * furnished. These are measurements of a known defect, not targets: the
     * sweep fails when they grow, and they come down as the fill improves.
     */
    private static final int VOIDS_TODAY = 344;
    private static final int SPARSE_TODAY = 480;

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

    /** One fitted compartment, as the finished deck presents it. */
    private record Fill(String hull, long seed, RoomPurpose purpose,
                        int area, int used, int voidSide) {
        float usedFraction() {
            return area <= 0 ? 0f : (float) used / area;
        }

        String where() {
            return hull + " seed " + seed + " " + purpose.name().toLowerCase(Locale.ROOT)
                    + " (" + area + " cells)";
        }
    }

    /**
     * <b>Ratchet, not a pass mark.</b> The fill leaves real holes today — a
     * hangar comes out with twelve cells square of nothing in the middle of it
     * — and the honest thing is to measure that rather than to pick a threshold
     * it happens to clear. So this fails when the count grows, and the number
     * comes down as the fill improves. A green run means the last change did
     * not make rooms emptier, not that the rooms are full.
     *
     * <p>Some of what is counted here is argued: {@link
     * com.dillon.starsectormarines.battle.world.gen.fit.AisleFitting} reserves
     * a working aisle scaled to the room, and a machinery space is mostly open
     * deck on purpose. Separating those from genuine voids means reading the
     * fill's own reservations, which is the next thing this instrument owes.
     */
    @Test
    void theFillLeavesNoMoreHolesThanItAlreadyDid() {
        List<Fill> fills = sweep(RoomFit.STANDARD);
        assertTrue(!fills.isEmpty(), "the sweep generated no fitted compartments at all");

        List<String> holes = new ArrayList<>();
        for (Fill fill : fills) {
            if (fill.area() < MEASURABLE_AREA || fill.voidSide() <= MAX_VOID) continue;
            holes.add(fill.where() + ": " + fill.voidSide() + "x" + fill.voidSide());
        }
        assertTrue(holes.size() <= VOIDS_TODAY,
                holes.size() + " of " + fills.size() + " fitted compartments hold a void "
                        + "larger than " + MAX_VOID + "x" + MAX_VOID + ", up from "
                        + VOIDS_TODAY + ". Worst offenders: "
                        + String.join("; ", holes.subList(0, Math.min(12, holes.size()))));
    }

    /** The same ratchet for rooms that are furnished but barely. */
    @Test
    void theFillFurnishesNoLessThanItAlreadyDid() {
        List<String> bare = new ArrayList<>();
        for (Fill fill : sweep(RoomFit.STANDARD)) {
            if (fill.area() < MEASURABLE_AREA) continue;
            if (fill.usedFraction() >= MIN_USED) continue;
            bare.add(fill.where() + ": " + percent(fill.usedFraction()) + " used");
        }
        assertTrue(bare.size() <= SPARSE_TODAY,
                bare.size() + " compartments are furnished below " + percent(MIN_USED)
                        + " of their floor, up from " + SPARSE_TODAY + ". Worst offenders: "
                        + String.join("; ", bare.subList(0, Math.min(12, bare.size()))));
    }

    /**
     * The refit is the capacity model, so it has to be visible in the deck.
     *
     * <p>Measured across the whole sweep rather than per room: an individual
     * compartment can come out the same at two levels when its shape decides
     * the answer before the pitch does, and a rule that forbade that would be
     * a rule about shapes.
     */
    @Test
    void aBetterFittedDeckHoldsMoreInTheSameFloor() {
        Map<RoomFit, Integer> occupied = new EnumMap<>(RoomFit.class);
        Map<RoomFit, Integer> floor = new EnumMap<>(RoomFit.class);
        for (RoomFit fit : RoomFit.values()) {
            for (Fill fill : sweep(fit)) {
                occupied.merge(fit, fill.used(), Integer::sum);
                floor.merge(fit, fill.area(), Integer::sum);
            }
        }
        float makeshift = share(occupied, floor, RoomFit.MAKESHIFT);
        float standard = share(occupied, floor, RoomFit.STANDARD);
        float optimised = share(occupied, floor, RoomFit.OPTIMISED);

        String ladder = "makeshift " + percent(makeshift)
                + ", standard " + percent(standard)
                + ", optimised " + percent(optimised);
        assertTrue(standard > makeshift,
                "a working arrangement should beat a makeshift one: " + ladder);
        assertTrue(optimised > standard,
                "fitting a room out properly should beat leaving it standard: " + ladder);
    }

    private static float share(Map<RoomFit, Integer> occupied,
                               Map<RoomFit, Integer> floor, RoomFit fit) {
        int total = floor.getOrDefault(fit, 0);
        return total <= 0 ? 0f : (float) occupied.getOrDefault(fit, 0) / total;
    }

    private static List<Fill> sweep(RoomFit fit) {
        List<Fill> fills = new ArrayList<>();
        for (Hull hull : FLEET) {
            for (long seed = 1; seed <= SEEDS; seed++) {
                DeckSizing.DeckPlan plan = DeckSizing.planFor(hull.hullClass(), hull.role(),
                        hull.minCrew(), hull.maxCrew(), hull.cargo(), hull.aspect());
                ShipDeckGenerator generator = new ShipDeckGenerator();
                MapResult deck = generator.generateDeck(plan, seed, null, fit);
                DeckGraph graph = generator.getLastDeckGraph();
                if (graph == null) continue;
                for (DeckGraph.Compartment room : graph.compartments()) {
                    if (RoomFittings.forPurpose(room.purpose()) == null) continue;
                    fills.add(measure(hull.name(), seed, room, deck));
                }
            }
        }
        return fills;
    }

    /**
     * Rasterise what is standing in one compartment and find the largest square
     * of deck that has nothing on it and no reason to be clear.
     *
     * <p>A machine berth counts as spoken for even though nothing is standing
     * in it. The bay is kept clear on purpose — a walker is put there by the
     * host, not by the fill — so counting it as a hole would report the one
     * empty space in the room that is doing its job.
     */
    private static Fill measure(String hull, long seed,
                                DeckGraph.Compartment room, MapResult deck) {
        int width = room.shape().width();
        int height = room.shape().height();
        boolean[][] spokenFor = new boolean[width][height];
        int area = 0;
        int used = 0;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!room.shape().contains(x, y)) {
                    // Not deck at all. Left unmarked it would read as the
                    // largest void in every room with a shaped corner.
                    spokenFor[x][y] = true;
                    continue;
                }
                area++;
                // Deck the room shut — a firing range's beaten zone — reads as
                // unwalkable floor rather than as a fixture.
                if (!deck.grid.isWalkable(room.originX() + x, room.originY() + y)) {
                    spokenFor[x][y] = true;
                    used++;
                }
            }
        }
        for (Doodad doodad : deck.doodads) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (spokenFor[x][y] || !room.shape().contains(x, y)) continue;
                    if (!doodad.occupiesCell(room.originX() + x, room.originY() + y)) continue;
                    spokenFor[x][y] = true;
                    used++;
                }
            }
        }
        for (Gantry gantry : deck.gantries) {
            for (int x = gantry.centerX - gantry.halfWidth;
                 x <= gantry.centerX + gantry.halfWidth; x++) {
                for (int y = gantry.centerY - gantry.halfHeight;
                     y <= gantry.centerY + gantry.halfHeight; y++) {
                    int lx = x - room.originX();
                    int ly = y - room.originY();
                    if (lx < 0 || ly < 0 || lx >= width || ly >= height) continue;
                    if (spokenFor[lx][ly] || !room.shape().contains(lx, ly)) continue;
                    spokenFor[lx][ly] = true;
                    used++;
                }
            }
        }
        return new Fill(hull, seed, room.purpose(), area, used, largestVoid(spokenFor));
    }

    /**
     * The side of the largest square containing no spoken-for cell.
     *
     * <p>A square rather than an area, because area cannot tell a lane from a
     * hole. Thirty cells of two-wide passage threading a room is circulation;
     * the same thirty cells in one block is a room nobody finished.
     */
    private static int largestVoid(boolean[][] spokenFor) {
        int width = spokenFor.length;
        int height = width == 0 ? 0 : spokenFor[0].length;
        int[][] side = new int[width][height];
        int largest = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (spokenFor[x][y]) continue;
                side[x][y] = x == 0 || y == 0 ? 1
                        : 1 + Math.min(side[x - 1][y],
                                Math.min(side[x][y - 1], side[x - 1][y - 1]));
                largest = Math.max(largest, side[x][y]);
            }
        }
        return largest;
    }

    private static String percent(float fraction) {
        return Math.round(fraction * 100f) + "%";
    }
}
