package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckProfile;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.Hookup;
import com.dillon.starsectormarines.battle.world.gen.ship.HullContact;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.gen.ship.fit.RoomFitting;
import com.dillon.starsectormarines.battle.world.gen.ship.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Step 3 (ship) — pack the deck's room program into the hull, then cut each
 * room's access out of whatever that packing left behind.
 *
 * <p>This replaces an earlier model that ruled evenly spaced athwartships
 * corridors across the deck first and subdivided the bays between them. That
 * model could only produce what it was given: bay-length slabs, all the same
 * depth, every one opening onto the spine. Enlarging the deck enlarged the slabs
 * instead of fitting more rooms, and no room was ever the size its purpose
 * called for.
 *
 * <p>Here the rooms come first, and they are packed rather than partitioned.
 * Each {@link RoomRecipe} carries its own {@link RoomShape}, so a berth is a
 * berth-sized compartment wherever it lands and the mech bay is forty frames
 * long because that is what servicing a walker needs. Shapes are laid largest
 * first in any orientation, scored to sit in the {@link DeckZone} their purpose
 * belongs to and otherwise to wedge tight against the hull, the spine, and each
 * other.
 *
 * <p><b>Circulation is the negative space.</b> A packed deck leaves ragged gaps
 * — where the hull flares, where a shape did not divide the pocket it filled,
 * where two blocks of compartments meet at different depths — and passages are
 * cut through those. That is what gives the deck hallways of differing length
 * and width instead of a comb, and it is why the packing is allowed to be
 * uneven rather than tidied into a grid.
 *
 * <p>What survives all of that is offered to the small {@link RoomRecipe#UTILITY}
 * rooms, which take any pocket already touching a passage. A void that is left
 * over after even those have had their pick is not floor at all: it stays solid
 * as ship's structure.
 */
public final class RoomPlacementStage implements GenStage {

    /** Weight keeping a room in its own zone; large enough to outrank any packing score. */
    private static final int ZONE_BONUS = 1_000_000;
    /** How many of the best-scoring placements to try before giving a room up as unfittable. */
    private static final int PLACEMENT_ATTEMPTS = 8;
    /**
     * How many to try when the room states where it hooks up. Deeper than the
     * ordinary search because it is looking for something specific — a position
     * whose doors the surrounding deck can actually serve — and the best-packed
     * few are unlikely to be the ones a passage happens to run past.
     */
    private static final int HOOKUP_ATTEMPTS = 40;
    private static final int[][] STEPS = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };

    /** Cost of running a passage through deck nobody claimed. */
    private static final int OPEN_COST = 1;
    /**
     * Cost of taking a passage through a bulkhead. High enough that a route
     * prefers open deck and only crosses structure where it has to, which is
     * what makes a passage cross a bulkhead squarely — as a door — instead of
     * running along one and unzipping the compartment behind it.
     */
    private static final int WALL_COST = 8;
    /**
     * Surcharge on a cell no two-by-two square of open deck covers. Two abreast
     * is the width a squad can actually use, and it has to hold on both axes at
     * once: a corridor widened only across its direction of travel pinches back
     * to one cell at every corner, and a one-cell corner is a movement trap in
     * the worst possible place. So width is judged as a square, not as a pair.
     *
     * <p>Two is a floor, not a ceiling. Squares stamped along a route overlap
     * and merge with the ones already there, so halls open out wherever the deck
     * has room and only narrow where it genuinely does not.
     */
    private static final int NARROW_PENALTY = 3;

    /** Corners of the two-by-two square a route cell may be covered by. */
    private static final int[][] LANE_ANCHORS = { { 0, 0 }, { -1, 0 }, { 0, -1 }, { -1, -1 } };

    /** Padded by one cell each side, so a room's bulkhead ring never falls off the array. */
    private boolean[][] hull;
    private boolean[][] room;
    private boolean[][] claimed;
    private boolean[][] floor;
    private boolean[][] passage;
    private int[][] claimedSum;
    private int width;
    private int height;

    @Override
    public void run(GenContext ctx) {
        DeckProfile profile = ctx.get(ShipKeys.DECK_PROFILE);
        List<RoomRecipe> program = ctx.get(ShipKeys.ROOM_PROGRAM);
        if (profile == null || program == null) {
            throw new IllegalStateException(
                    "RoomPlacementStage requires a deck profile and a room program");
        }

        initMasks(ctx, profile);

        List<RoomRecipe> ordered = new ArrayList<>(program);
        ordered.sort(Comparator.comparingInt(RoomRecipe::area).reversed()
                .thenComparing(recipe -> recipe.purpose().name()));

        List<DeckGraph.Compartment> placed = new ArrayList<>();
        List<RoomRecipe> unplaced = new ArrayList<>();
        for (RoomRecipe recipe : ordered) {
            DeckGraph.Compartment compartment = place(ctx, profile, recipe, placed, true);
            if (compartment == null) {
                unplaced.add(recipe);
            } else {
                placed.add(compartment);
            }
        }
        fillPockets(ctx, profile, placed);
        ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(placed, unplaced));
    }

    /**
     * Everything inside the hull starts free; the spine and its two bulkhead
     * rows start taken, because the corridor was cut before this stage ran and
     * no room may eat into it.
     */
    private void initMasks(GenContext ctx, DeckProfile profile) {
        width = ctx.width;
        height = ctx.height;
        hull = new boolean[width + 2][height + 2];
        room = new boolean[width + 2][height + 2];
        claimed = new boolean[width + 2][height + 2];
        floor = new boolean[width + 2][height + 2];
        passage = new boolean[width + 2][height + 2];
        for (boolean[] column : claimed) {
            Arrays.fill(column, true);
        }
        for (int x = 0; x < profile.frames(); x++) {
            for (int y = profile.top(x); y <= profile.bottom(x); y++) {
                claimed[x + 1][y + 1] = false;
                hull[x + 1][y + 1] = true;
            }
            for (int y = profile.spineTop() - 1; y <= profile.spineBottom() + 1; y++) {
                claimed[x + 1][y + 1] = true;
            }
            for (int y = profile.spineTop(); y <= profile.spineBottom(); y++) {
                floor[x + 1][y + 1] = true;
                passage[x + 1][y + 1] = true;
            }
        }
        rebuildSums();
    }

    /**
     * Offer every pocket the authored rooms did not want to the utility set,
     * largest first, until a whole pass places nothing. There is no count to cap
     * here: the deck has a finite number of cells, every placement consumes
     * some, and how many there were to begin with is exactly what the density
     * dial decides. Utility rooms take a direct door only — a locker is worth a
     * pocket that already touches a passage, never worth tunnelling to.
     */
    private void fillPockets(GenContext ctx, DeckProfile profile,
                             List<DeckGraph.Compartment> placed) {
        List<RoomRecipe> utility = new ArrayList<>(RoomRecipe.UTILITY);
        utility.sort(Comparator.comparingInt(RoomRecipe::area).reversed());
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (RoomRecipe recipe : utility) {
                DeckGraph.Compartment compartment = place(ctx, profile, recipe, placed, false);
                if (compartment == null) continue;
                placed.add(compartment);
                progressed = true;
                break;
            }
        }
    }

    /**
     * Find this room the best position it can still have, cut it in, and open it
     * onto the deck's circulation. Returns null when nothing fits, or when
     * nothing that fits can be reached.
     *
     * @param mayTunnel whether the room is worth cutting a fresh passage to
     */
    private DeckGraph.Compartment place(GenContext ctx, DeckProfile profile, RoomRecipe recipe,
                                        List<DeckGraph.Compartment> placed, boolean mayTunnel) {
        RoomFitting fitting = RoomFittings.forPurpose(recipe.purpose());
        List<Hookup> hookups = fitting == null ? List.of() : fitting.hookups();
        List<Candidate> candidates =
                candidates(ctx, profile, recipe, posesFor(recipe.shape(), !hookups.isEmpty()));

        if (!hookups.isEmpty()) {
            DeckGraph.Compartment hooked =
                    placeHooked(ctx, profile, recipe, placed, hookups, candidates, mayTunnel);
            if (hooked != null) return hooked;
        }

        // A room that states its hookups still has to go somewhere. Falling back
        // to an ordinary door is a worse bay; refusing to place it is no bay at
        // all, and the deck would be short a facility over the position of a
        // hatch.
        int attempts = Math.min(PLACEMENT_ATTEMPTS, candidates.size());
        for (int i = 0; i < attempts; i++) {
            Candidate candidate = candidates.get(i);
            Access access = findAccess(candidate, mayTunnel, null);
            if (access == null) continue;
            List<DeckGraph.Compartment.Door> doors =
                    commit(ctx, candidate, recipe.purpose(), List.of(access));
            return describe(profile, candidate, recipe.purpose(), placed.size(), doors);
        }
        return null;
    }

    /**
     * Place a room where the deck can serve the doors it asked for, preferring
     * the position that serves the most of them.
     *
     * <p>Alternatives are tried in the order the fitting wrote them, so a bay
     * takes its drive-through arrangement wherever one is available and its
     * single-door arrangement only where one is not.
     */
    private DeckGraph.Compartment placeHooked(GenContext ctx, DeckProfile profile,
                                              RoomRecipe recipe,
                                              List<DeckGraph.Compartment> placed,
                                              List<Hookup> hookups, List<Candidate> candidates,
                                              boolean mayTunnel) {
        int attempts = Math.min(HOOKUP_ATTEMPTS, candidates.size());
        int wanted = 0;
        for (Hookup hookup : hookups) wanted = Math.max(wanted, hookup.slots().size());

        Candidate best = null;
        List<Access> bestAccesses = null;
        for (int i = 0; i < attempts; i++) {
            Candidate candidate = candidates.get(i);
            for (Hookup hookup : hookups) {
                List<Access> accesses = serve(candidate, recipe.shape(), hookup, mayTunnel);
                if (accesses == null) continue;
                if (bestAccesses == null || accesses.size() > bestAccesses.size()) {
                    best = candidate;
                    bestAccesses = accesses;
                }
                break;
            }
            if (bestAccesses != null && bestAccesses.size() >= wanted) break;
        }
        if (best == null) return null;
        List<DeckGraph.Compartment.Door> doors =
                commit(ctx, best, recipe.purpose(), bestAccesses);
        return describe(profile, best, recipe.purpose(), placed.size(), doors);
    }

    /**
     * The accesses this candidate can offer one hookup, or null if it cannot
     * serve the first doorway at all.
     *
     * <p>Only the first doorway is worth cutting a passage to. The rest are
     * taken where the deck already runs past them: a bay is better with two
     * doors than one, but not at the price of tunnelling a corridor around the
     * outside of it to reach its far side.
     */
    private List<Access> serve(Candidate candidate, RoomShape canonical,
                               Hookup hookup, boolean mayTunnel) {
        List<Access> accesses = new ArrayList<>();
        for (int slot = 0; slot < hookup.slots().size(); slot++) {
            Set<Long> allowed = allowedDoors(candidate, canonical, hookup.slots().get(slot));
            Access access = findAccess(candidate, slot == 0 && mayTunnel, allowed);
            if (access == null) {
                if (slot == 0) return null;
                continue;
            }
            accesses.add(access);
        }
        return accesses;
    }

    /** One doorway's authored cells, carried into this candidate's pose and position. */
    private Set<Long> allowedDoors(Candidate candidate, RoomShape canonical,
                                   Hookup.DoorSlot slot) {
        Set<Long> keys = new HashSet<>();
        for (int[] cell : slot.cells()) {
            int[] posed = candidate.pose()
                    .map(cell[0], cell[1], canonical.width(), canonical.height());
            keys.add(cellKey(candidate.x() + posed[0], candidate.y() + posed[1]));
        }
        return keys;
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private static boolean permits(Set<Long> allowed, int x, int y) {
        return allowed == null || allowed.contains(cellKey(x, y));
    }

    /** One way of laying a room down: a posed shape at an origin, and its score. */
    private record Candidate(RoomShape shape, RoomPose pose, int x, int y, int score) {}

    /**
     * The poses a room may be laid down in.
     *
     * <p>Rotations only, deduplicated by mask, unless the room states where it
     * hooks up — flips cost four times the candidates to consider and buy
     * nothing at all for an arrangement with no front and no back. A room that
     * does care gets all eight, which is what lets one authored bay serve a
     * deck whose circulation runs down either side of it.
     */
    private static List<RoomPose> posesFor(RoomShape shape, boolean flippable) {
        if (flippable) return RoomPose.all();
        List<RoomPose> distinct = new ArrayList<>();
        Set<RoomShape> seen = new HashSet<>();
        for (RoomPose pose : RoomPose.all()) {
            if (pose.mirrored()) continue;
            if (seen.add(shape.posed(pose))) distinct.add(pose);
        }
        return distinct;
    }

    /**
     * Every position and orientation this room could legally take, best first.
     *
     * <p>A candidate is legal when all of its floor is unclaimed and none of its
     * bulkhead ring is already walkable — a room may share a wall with the hull,
     * the spine, or another compartment, but it may never stand open along a
     * whole edge onto a corridor or a neighbour.
     *
     * <p>Score is zone first, then how much of the ring is already solid. That
     * second term is the packing: a position wedged into a corner outscores one
     * floating in open deck, so rooms gather into blocks and the space they
     * leave collects into passages instead of scattering as slivers.
     */
    private List<Candidate> candidates(GenContext ctx, DeckProfile profile, RoomRecipe recipe,
                                       List<RoomPose> poses) {
        List<Candidate> found = new ArrayList<>();
        for (RoomPose pose : poses) {
            RoomShape shape = recipe.shape().posed(pose);
            int w = shape.width();
            int h = shape.height();
            int slack = w * h - shape.area();
            for (int x = 0; x + w <= width; x++) {
                DeckZone zone = profile.zone(clampFrame(profile, x + w / 2));
                int zoneBonus = recipe.zone() == null || zone == recipe.zone() ? ZONE_BONUS : 0;
                for (int y = 0; y + h <= height; y++) {
                    // Necessary condition first: if the bounding box is more
                    // occupied than the shape's own holes could absorb, no
                    // per-cell test can save it.
                    if (sum(claimedSum, x, y, w, h) > slack) continue;
                    if (!floorFits(shape, x, y)) continue;
                    int contact = wallContact(shape, x, y);
                    if (contact < 0) continue;
                    if (!meetsHull(recipe.contact(), shape, x, y)) continue;
                    found.add(new Candidate(shape, pose, x, y,
                            zoneBonus + contact + ctx.rng.nextInt(3)));
                }
            }
        }
        found.sort(Comparator.comparingInt(Candidate::score).reversed()
                .thenComparingInt(Candidate::x)
                .thenComparingInt(Candidate::y));
        return found;
    }

    private static int clampFrame(DeckProfile profile, int frame) {
        return Math.max(0, Math.min(profile.frames() - 1, frame));
    }

    /** Whether every cell of the shape lands on unclaimed deck. */
    private boolean floorFits(RoomShape shape, int ox, int oy) {
        for (int[] cell : shape.filled()) {
            int x = ox + cell[0];
            int y = oy + cell[1];
            if (!inBounds(x, y) || claimed[x + 1][y + 1]) return false;
        }
        return true;
    }

    /**
     * Whether this placement puts the room against the part of the ship's
     * outside it needs.
     *
     * <p>A boat bay amidships opens onto the compartment next door, and an
     * engine room a third of the way up the hull is not driving anything.
     * Requiring part of the bulkhead to fall outside the hull — on any side for
     * a bay, on the after side for the drive — is what makes the placer find
     * those rooms somewhere they could do their job. It is also the test a
     * breach point will want when boarding entry is authored.
     */
    private boolean meetsHull(HullContact contact, RoomShape shape, int ox, int oy) {
        if (contact == HullContact.NONE) return true;
        for (int[] cell : shape.wall()) {
            if (contact == HullContact.STERN && cell[0] != shape.width()) continue;
            int x = ox + cell[0];
            int y = oy + cell[1];
            if (!inBounds(x, y) || !hull[x + 1][y + 1]) return true;
        }
        return false;
    }

    /**
     * How much of the room's bulkhead ring already backs onto something solid,
     * or -1 when the ring crosses a walkable cell and the placement is illegal.
     */
    private int wallContact(RoomShape shape, int ox, int oy) {
        int contact = 0;
        for (int[] cell : shape.wall()) {
            int x = ox + cell[0];
            int y = oy + cell[1];
            if (!inBounds(x, y)) {
                contact++;
                continue;
            }
            if (floor[x + 1][y + 1]) return -1;
            if (claimed[x + 1][y + 1]) contact++;
        }
        return contact;
    }

    /** A door through one bulkhead cell, and the passage cells cut to reach it. */
    /**
     * @param doorX door cell, in the bulkhead of the room being placed
     * @param doorY door cell, in the bulkhead of the room being placed
     * @param dirX outward step from the door to the cell it opens onto
     * @param dirY outward step from the door to the cell it opens onto
     * @param passage cells cut to reach circulation, nearest circulation first
     */
    private record Access(int doorX, int doorY, int dirX, int dirY, List<int[]> passage) {}

    /**
     * How this room joins the rest of the deck. A room whose bulkhead already
     * backs onto the spine or an existing passage needs only a door; otherwise a
     * passage is cut through unclaimed deck until it meets one.
     *
     * <p>A door has to open onto <b>circulation</b>, never merely onto walkable
     * space. Accepting any floor let rooms chain doorways through one another,
     * and a deck where the way outboard is through somebody's berth and out the
     * far side is an enfilade, not a ship: no hallways, no way past a held
     * compartment, and every room on the route a through-route.
     */
    private Access findAccess(Candidate candidate, boolean mayTunnel, Set<Long> allowed) {
        for (int[] doorway : candidate.shape().doorways()) {
            int doorX = candidate.x() + doorway[0];
            int doorY = candidate.y() + doorway[1];
            if (!permits(allowed, doorX, doorY)) continue;
            int outsideX = candidate.x() + doorway[2];
            int outsideY = candidate.y() + doorway[3];
            if (!inBounds(outsideX, outsideY) || !passage[outsideX + 1][outsideY + 1]) continue;
            // The room being placed is not yet carved on the first door and is
            // carved by the second, so this asks whether somebody *else* is
            // behind the bulkhead — which is the actual objection.
            if (backsOntoOtherRoom(candidate, doorX, doorY)) continue;
            return new Access(doorX, doorY,
                    doorway[2] - doorway[0], doorway[3] - doorway[1], List.of());
        }
        return mayTunnel ? cutPassage(candidate, allowed) : null;
    }

    /**
     * Cheapest route from any of this room's bulkheads to existing circulation,
     * preferring open deck and paying to cross structure. The result is the
     * least disruptive passage joining this room to the rest of the deck, which
     * is why passages thread between blocks already placed rather than wandering
     * or tearing through them.
     */
    private Access cutPassage(Candidate candidate, Set<Long> allowed) {
        int[][] routable = routableGrid(candidate);
        boolean[][] wide = wideGrid(routable);
        int[][] cost = new int[width][height];
        int[][] cameFrom = new int[width][height];
        for (int[] column : cost) {
            Arrays.fill(column, Integer.MAX_VALUE);
        }
        for (int[] column : cameFrom) {
            Arrays.fill(column, -1);
        }
        PriorityQueue<int[]> frontier =
                new PriorityQueue<>(Comparator.comparingInt(entry -> entry[2]));
        for (int[] doorway : candidate.shape().doorways()) {
            if (!permits(allowed, candidate.x() + doorway[0], candidate.y() + doorway[1])) continue;
            int outsideX = candidate.x() + doorway[2];
            int outsideY = candidate.y() + doorway[3];
            if (!inBounds(outsideX, outsideY)) continue;
            // A bulkhead a second compartment also stands behind is a shared
            // wall; a door there opens both rooms and joins them into one.
            if (backsOntoOtherRoom(candidate, candidate.x() + doorway[0],
                    candidate.y() + doorway[1])) {
                continue;
            }
            int step = routeCost(routable, wide, outsideX, outsideY);
            if (step < 0 || step >= cost[outsideX][outsideY]) continue;
            if (!crossesCleanly(routable, outsideX, outsideY,
                    doorway[2] - doorway[0], doorway[3] - doorway[1])) {
                continue;
            }
            cost[outsideX][outsideY] = step;
            cameFrom[outsideX][outsideY] =
                    doorMarker(candidate.x() + doorway[0], candidate.y() + doorway[1]);
            frontier.add(new int[]{ outsideX, outsideY, step });
        }
        while (!frontier.isEmpty()) {
            int[] cell = frontier.poll();
            if (cell[2] > cost[cell[0]][cell[1]]) continue;
            if (touchesPassage(cell[0], cell[1])) return trace(cameFrom, cell);
            for (int[] step : STEPS) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (!inBounds(nx, ny)) continue;
                int stepCost = routeCost(routable, wide, nx, ny);
                if (stepCost < 0) continue;
                if (!crossesCleanly(routable, nx, ny, step[0], step[1])) continue;
                int next = cell[2] + stepCost;
                if (next >= cost[nx][ny]) continue;
                cost[nx][ny] = next;
                cameFrom[nx][ny] = cell[0] * height + cell[1];
                frontier.add(new int[]{ nx, ny, next });
            }
        }
        return null;
    }

    /**
     * What it costs to route a passage through one cell, or -1 where it may not
     * go at all.
     *
     * <p>Walkable cells are never routed through — a passage that runs across
     * somebody's berth is not a passage — and neither is anything outside the
     * hull or belonging to the room currently being placed, which is not
     * committed yet and so is invisible to the masks.
     *
     * <p>The interesting exclusion is the last one: <b>a route never cuts a
     * cell that a room stands behind.</b> Only the deliberate door opens a
     * compartment. Letting a route take any structure it could pay for was not
     * enough of a limit — a room's outer wall is structure, so a passage could
     * chew through one bulkhead cell after another and leave the compartment
     * standing open for six cells at a stretch. Structure that no room backs
     * onto — the spine bulkhead away from any compartment, plating, the walls
     * of a pocket — is still crossable, which is all a passage actually needs.
     */
    private int stepCost(Candidate candidate, int x, int y) {
        if (!inBounds(x, y) || !hull[x + 1][y + 1] || floor[x + 1][y + 1]) return -1;
        int localX = x - candidate.x();
        int localY = y - candidate.y();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (candidate.shape().contains(localX + dx, localY + dy)) return -1;
            }
        }
        if (!claimed[x + 1][y + 1]) return OPEN_COST;
        return backsOntoRoom(x, y) ? -1 : WALL_COST;
    }

    /**
     * What every cell of the deck would cost this room's passage, worked out
     * once per attempt. The per-cell test walks the room being placed, so
     * calling it from inside the search — for the cell and for its neighbours,
     * on every relaxation — was the same work over and over.
     */
    private int[][] routableGrid(Candidate candidate) {
        int[][] costs = new int[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                costs[x][y] = stepCost(candidate, x, y);
            }
        }
        return costs;
    }

    /** Cells some two-by-two square of routable deck covers — where a hall can be full width. */
    private boolean[][] wideGrid(int[][] routable) {
        boolean[][] wide = new boolean[width][height];
        for (int x = 0; x + 1 < width; x++) {
            for (int y = 0; y + 1 < height; y++) {
                if (routable[x][y] < 0 || routable[x + 1][y] < 0
                        || routable[x][y + 1] < 0 || routable[x + 1][y + 1] < 0) {
                    continue;
                }
                wide[x][y] = true;
                wide[x + 1][y] = true;
                wide[x][y + 1] = true;
                wide[x + 1][y + 1] = true;
            }
        }
        return wide;
    }

    /**
     * Whether a passage entering this cell on this heading is crossing the
     * structure rather than running along it.
     *
     * <p>Paying a toll to cross a bulkhead is not enough on its own. A room's
     * outer wall has its own floor on one side and open deck on the other, so
     * nothing in the per-cell cost stops a route following that wall for its
     * whole length — and a route that does strips the compartment behind it of
     * a wall, which is worse than the enfilade a shared bulkhead would have
     * caused, because it happens along the whole side of the room.
     *
     * <p>So structure may only be entered when the cell straight ahead is open
     * deck or circulation already: in one side and out the other, which is a
     * door. Two structure cells in a row on the same heading is either a wall
     * being followed or a wall too thick to be a doorway, and neither is a
     * passage.
     */
    private boolean crossesCleanly(int[][] routable, int x, int y, int stepX, int stepY) {
        if (routable[x][y] != WALL_COST) return true;
        int aheadX = x + stepX;
        int aheadY = y + stepY;
        if (!inBounds(aheadX, aheadY)) return false;
        return !claimed[aheadX + 1][aheadY + 1] || passage[aheadX + 1][aheadY + 1];
    }

    private static int routeCost(int[][] routable, boolean[][] wide, int x, int y) {
        int base = routable[x][y];
        if (base < 0) return -1;
        return wide[x][y] ? base : base + NARROW_PENALTY;
    }

    /**
     * Whether one cell can carry part of a hall. Open deck can, and deck that is
     * already walkable is already a hall. Bulkhead is offered only while the
     * passage is crossing one: a wide hatch through a wall is a door, but a hall
     * that kept eating the bulkhead it runs alongside would unzip the
     * compartment behind it.
     */
    private boolean laneOk(Candidate candidate, int x, int y, boolean crossing) {
        if (!inBounds(x, y)) return false;
        if (floor[x + 1][y + 1]) return true;
        if (claimed[x + 1][y + 1] && !crossing) return false;
        return stepCost(candidate, x, y) >= 0;
    }

    /**
     * Whether a compartment other than the one being placed stands behind this
     * cell. The room being placed is already cut in by the time its door is
     * widened, so plain {@link #backsOntoRoom} would refuse every second door
     * cell on the grounds that a room is behind it — which is what a door is.
     */
    private boolean backsOntoOtherRoom(Candidate candidate, int x, int y) {
        for (int[] step : STEPS) {
            int nx = x + step[0];
            int ny = y + step[1];
            if (!isRoomFloor(nx, ny)) continue;
            if (!candidate.shape().contains(nx - candidate.x(), ny - candidate.y())) return true;
        }
        return false;
    }

    /** Whether any compartment stands directly behind this cell. */
    private boolean backsOntoRoom(int x, int y) {
        for (int[] step : STEPS) {
            if (isRoomFloor(x + step[0], y + step[1])) return true;
        }
        return false;
    }

    /**
     * Whether this cell is the floor of a compartment.
     *
     * <p>Recorded outright rather than inferred as walkable-but-not-passage. A
     * door is walkable and is deliberately kept out of the passage mask, so that
     * inference read every doorway as another compartment's floor, which
     * silently refused to widen any door on the deck.
     */
    private boolean isRoomFloor(int x, int y) {
        return inBounds(x, y) && room[x + 1][y + 1];
    }

    private boolean touchesPassage(int x, int y) {
        for (int[] step : STEPS) {
            int nx = x + step[0];
            int ny = y + step[1];
            if (inBounds(nx, ny) && passage[nx + 1][ny + 1]) return true;
        }
        return false;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** Search origins are tagged with the door they came from, below every real cell index. */
    private int doorMarker(int x, int y) {
        return -2 - (x * height + y);
    }

    /** Walk the search back to the bulkhead it started from, collecting the passage. */
    private Access trace(int[][] cameFrom, int[] end) {
        List<int[]> passage = new ArrayList<>();
        int x = end[0];
        int y = end[1];
        while (true) {
            passage.add(new int[]{ x, y });
            int from = cameFrom[x][y];
            if (from <= -2) {
                int door = -(from + 2);
                int doorX = door / height;
                int doorY = door % height;
                // The search started on the cell just outside the door, so the
                // outward direction is simply where that origin sits from it.
                return new Access(doorX, doorY, x - doorX, y - doorY, List.copyOf(passage));
            }
            x = from / height;
            y = from % height;
        }
    }

    /**
     * Cut the room, its bulkheads, its passage, and its door into the deck.
     *
     * @return the door cells, which the fill needs to know where people enter
     */
    private List<DeckGraph.Compartment.Door> commit(GenContext ctx, Candidate candidate,
                                                   RoomPurpose purpose, List<Access> accesses) {
        carveRoom(ctx, candidate, purpose);
        List<DeckGraph.Compartment.Door> doors = new ArrayList<>();
        for (Access access : accesses) {
            doors.addAll(cutDoor(ctx, candidate, access));
        }
        rebuildSums();
        return List.copyOf(doors);
    }

    /** Cut the room and its bulkheads into the deck, without its access. */
    private void carveRoom(GenContext ctx, Candidate candidate, RoomPurpose purpose) {
        RoomShape shape = candidate.shape();
        for (int[] cell : shape.filled()) {
            int x = candidate.x() + cell[0];
            int y = candidate.y() + cell[1];
            carve(ctx, x, y, purpose, GroundKind.INDOOR);
            room[x + 1][y + 1] = true;
        }
        for (int[] cell : shape.wall()) {
            int x = candidate.x() + cell[0];
            int y = candidate.y() + cell[1];
            if (inBounds(x, y)) claimed[x + 1][y + 1] = true;
        }
    }

    /** Cut one doorway and whatever passage was needed to reach it. */
    private List<DeckGraph.Compartment.Door> cutDoor(GenContext ctx, Candidate candidate,
                                                     Access access) {
        int[] anchor = null;
        for (int[] cell : access.passage()) {
            boolean crossing = claimed[cell[0] + 1][cell[1] + 1];
            carveLane(ctx, cell[0], cell[1]);
            anchor = stampLane(ctx, candidate, cell[0], cell[1], anchor, crossing);
        }
        // The door itself is a threshold, not circulation: leaving it out of the
        // passage mask is what stops the next room treating it as a hallway.
        carve(ctx, access.doorX(), access.doorY(), RoomPurpose.CORRIDOR, GroundKind.STRIPED);
        List<DeckGraph.Compartment.Door> doors = new ArrayList<>();
        doors.add(new DeckGraph.Compartment.Door(access.doorX(), access.doorY()));
        DeckGraph.Compartment.Door widened = widenDoorway(ctx, candidate, access);
        if (widened != null) doors.add(widened);
        return doors;
    }

    /**
     * Cover one cell of a route with a two-by-two square of hall, so the passage
     * is two wide on both axes there and not merely across the way it happened
     * to be heading. Squares laid down the route overlap, which is what turns a
     * corner into a proper elbow instead of the one-cell pinch a
     * travel-relative widening leaves.
     *
     * <p>The square that worked last time is tried first, so a straight run
     * keeps to one side and reads as a single ribbon. Where no square fits at
     * all the route stays single file rather than forcing its way through: a
     * squeeze somewhere the deck is genuinely tight is honest, and the routing
     * cost already steered around it if there was any alternative.
     *
     * @return the anchor used, to carry into the next cell
     */
    private int[] stampLane(GenContext ctx, Candidate candidate, int x, int y,
                            int[] preferred, boolean crossing) {
        for (int attempt = 0; attempt <= LANE_ANCHORS.length; attempt++) {
            int[] anchor = attempt == 0 ? preferred : LANE_ANCHORS[attempt - 1];
            if (anchor == null) continue;
            int ax = x + anchor[0];
            int ay = y + anchor[1];
            if (!laneOk(candidate, ax, ay, crossing)
                    || !laneOk(candidate, ax + 1, ay, crossing)
                    || !laneOk(candidate, ax, ay + 1, crossing)
                    || !laneOk(candidate, ax + 1, ay + 1, crossing)) {
                continue;
            }
            carveLane(ctx, ax, ay);
            carveLane(ctx, ax + 1, ay);
            carveLane(ctx, ax, ay + 1);
            carveLane(ctx, ax + 1, ay + 1);
            return anchor;
        }
        return preferred;
    }

    /** Cut one cell of hall, leaving deck that is already walkable alone. */
    private void carveLane(GenContext ctx, int x, int y) {
        if (!inBounds(x, y) || floor[x + 1][y + 1]) return;
        carve(ctx, x, y, RoomPurpose.CORRIDOR, GroundKind.INDOOR);
        passage[x + 1][y + 1] = true;
    }

    /**
     * Take the door to two cells where the wall and the space beyond allow it.
     * A compartment berthing a watch behind a one-cell threshold bottlenecks
     * everything that happens at it. The second cell has to be this room's own
     * bulkhead and has to open onto the same circulation, so this widens the
     * door rather than punching a second one somewhere else in the wall.
     */
    private DeckGraph.Compartment.Door widenDoorway(GenContext ctx, Candidate candidate,
                                                    Access access) {
        int perpX = access.dirY();
        int perpY = access.dirX();
        for (int side : new int[]{ 1, -1 }) {
            int nx = access.doorX() + perpX * side;
            int ny = access.doorY() + perpY * side;
            if (!inBounds(nx, ny) || floor[nx + 1][ny + 1]) continue;
            if (backsOntoOtherRoom(candidate, nx, ny)) continue;
            int localX = nx - candidate.x();
            int localY = ny - candidate.y();
            if (candidate.shape().contains(localX, localY)) continue;
            if (!candidate.shape().contains(localX - access.dirX(), localY - access.dirY())) continue;
            int outsideX = nx + access.dirX();
            int outsideY = ny + access.dirY();
            if (!inBounds(outsideX, outsideY) || !floor[outsideX + 1][outsideY + 1]) continue;
            carve(ctx, nx, ny, RoomPurpose.CORRIDOR, GroundKind.STRIPED);
            return new DeckGraph.Compartment.Door(nx, ny);
        }
        return null;
    }

    private void carve(GenContext ctx, int x, int y, RoomPurpose purpose, GroundKind kind) {
        if (!inBounds(x, y)) return;
        ctx.grid.setWalkableFloor(x, y);
        ctx.topology.setGroundKind(x, y, kind);
        ctx.topology.setRoomPurpose(x, y, purpose);
        claimed[x + 1][y + 1] = true;
        floor[x + 1][y + 1] = true;
    }

    private DeckGraph.Compartment describe(DeckProfile profile, Candidate candidate,
                                           RoomPurpose purpose, int id,
                                           List<DeckGraph.Compartment.Door> doors) {
        int left = candidate.x();
        int top = candidate.y();
        int right = left + candidate.shape().width() - 1;
        int bottom = top + candidate.shape().height() - 1;
        int spineCentre = (profile.spineTop() + profile.spineBottom()) / 2;
        DeckSide side = (top + bottom) / 2 < spineCentre ? DeckSide.PORT : DeckSide.STARBOARD;
        DeckZone zone = profile.zone(clampFrame(profile, (left + right) / 2));
        return new DeckGraph.Compartment(id, candidate.shape(), left, top,
                candidate.pose(), side, zone, purpose, doors);
    }

    private void rebuildSums() {
        claimedSum = prefix(claimed);
    }

    /** Summed-area table over the padded mask, so the bounding-box reject is four lookups. */
    private static int[][] prefix(boolean[][] mask) {
        int w = mask.length;
        int h = mask[0].length;
        int[][] sums = new int[w + 1][h + 1];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                sums[x + 1][y + 1] = (mask[x][y] ? 1 : 0)
                        + sums[x][y + 1] + sums[x + 1][y] - sums[x][y];
            }
        }
        return sums;
    }

    /** Count of set cells in the unpadded rect {@code (x, y, w, h)}, clipped to the padded mask. */
    private static int sum(int[][] sums, int x, int y, int w, int h) {
        int x0 = Math.max(0, Math.min(sums.length - 1, x + 1));
        int y0 = Math.max(0, Math.min(sums[0].length - 1, y + 1));
        int x1 = Math.max(x0, Math.min(sums.length - 1, x + 1 + w));
        int y1 = Math.max(y0, Math.min(sums[0].length - 1, y + 1 + h));
        return sums[x1][y1] - sums[x0][y1] - sums[x1][y0] + sums[x0][y0];
    }
}
