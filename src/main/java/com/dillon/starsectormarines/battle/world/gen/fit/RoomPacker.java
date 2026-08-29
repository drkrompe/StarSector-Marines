package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
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
 * Packs authored rooms into a buildable envelope, then cuts each room's access
 * out of whatever that packing left behind.
 *
 * <p>This replaces an earlier model that ruled evenly spaced corridors across
 * the space first and subdivided the bays between them. That model could only
 * produce what it was given: bay-length slabs, all the same depth, every one
 * opening onto the main corridor. Enlarging the space enlarged the slabs
 * instead of fitting more rooms, and no room was ever the size its purpose
 * called for.
 *
 * <p>Here the rooms come first, and they are packed rather than partitioned.
 * Each {@link Request} carries its own {@link RoomShape}, so a berth is a
 * berth-sized room wherever it lands and a mech bay is forty cells long because
 * that is what servicing a walker needs. Shapes are laid largest first in any
 * orientation, scored to sit where the caller says they belong and otherwise to
 * wedge tight against the envelope, the existing circulation, and each other.
 *
 * <p><b>Circulation is the negative space.</b> A packed envelope leaves ragged
 * gaps — where the outline flares, where a shape did not divide the pocket it
 * filled, where two blocks of rooms meet at different depths — and passages are
 * cut through those. That is what gives hallways of differing length and width
 * instead of a comb, and it is why the packing is allowed to be uneven rather
 * than tidied into a grid.
 *
 * <p>Nothing here knows what kind of place it is filling. A hull and a walled
 * compound differ in what makes a position good, which is the caller's
 * {@link Affinity}; in how freely rooms may be glued to each other, which is
 * its {@link Massing}; and in what counts as the outside, which is the buildable
 * mask — not in how rooms are packed or how a passage is cut to reach one.
 * Ordering the program, filling leftover pockets, and deciding what an unplaced
 * room means are likewise the caller's, because they are policy about a place
 * rather than mechanism about packing.
 */
public final class RoomPacker {

    /**
     * Whether a position sits where a room belongs, as opposed to merely
     * fitting. On a deck this is the longitudinal zone a purpose is at home in;
     * in a compound it is the ward.
     *
     * <p>Deliberately a predicate rather than a score. How strongly belonging
     * should outrank packing is a property of the packer — it has to outrank it
     * outright or a room settles wherever it wedges tightest — while what
     * belonging means is a property of the place.
     */
    @FunctionalInterface
    public interface Affinity {
        boolean prefers(int centreX, int centreY);

        /** A room with no opinion about where it goes. */
        Affinity ANYWHERE = (centreX, centreY) -> true;
    }

    /**
     * Where a room has to meet the edge of the buildable envelope, if at all.
     *
     * <p>Most rooms only need to fit. A few are defined by what they open onto,
     * and for those a placement that fits is still wrong: a boat bay buried
     * amidships opens onto the compartment next door, and a hangar that does not
     * reach the compound wall has nowhere to drive out to.
     */
    public enum EdgeContact {
        /** Anywhere the packing allows. Almost every room. */
        NONE,
        /** Must reach the outside on some side. */
        ANY,
        /** Must sit against the far end of its own local frame — a transom room. */
        TRAILING
    }

    /** The ground a packed room, its halls, and its thresholds are laid on. */
    public record Palette(GroundKind roomFloor, GroundKind hall, GroundKind threshold) {}

    /**
     * How freely rooms may be glued to one another.
     *
     * <p>Wedging is what makes the packing tight, and inside a hull it is simply
     * correct: compartments share bulkheads, and a void between two of them is
     * wasted displacement. On open ground it is not. Every shared seam extends
     * one unbroken run of impassable structure, and a place whose buildings all
     * chain together is a place with one long wall through it — crossed only by
     * walking to the end and back, which is a detour the packing never sees
     * because every individual room is reachable.
     *
     * <p>So the allowance is a length: how much of its wall a room may share
     * with the rooms already placed before the packing starts preferring
     * somewhere else. Short seams stay free, which is what lets buildings sit
     * in a block and corner into each other; long ones are pushed apart. It is
     * a preference rather than a veto because a room that cannot be placed at
     * all is worse than a room placed against its neighbour.
     *
     * <p>It also carries how many ways into a room the place wants, for the
     * same reason: how a building sits among its neighbours and how it is
     * entered are one question about the place, not two.
     *
     * @param sharedSeamAllowance ring cells a room may share with earlier rooms
     *                            for free
     * @param seamPenalty score charged per shared cell beyond the allowance
     * @param floorPerWayIn cells of floor that earn a room another way in, on a
     *                      face it does not already have one; {@code 0} for a
     *                      single way in however large the room
     */
    public record Massing(int sharedSeamAllowance, int seamPenalty, int floorPerWayIn) {

        /**
         * A hull: share every bulkhead you can, because open space is waste,
         * and one hatch per compartment as decks have always been cut.
         */
        public static final Massing WEDGED = new Massing(Integer.MAX_VALUE, 0, 0);

        public Massing {
            if (sharedSeamAllowance < 0) {
                throw new IllegalArgumentException("a seam allowance cannot be negative");
            }
            if (floorPerWayIn < 0) {
                throw new IllegalArgumentException("floor per way in cannot be negative");
            }
        }
    }

    /** One room to place: what it is, the floor it needs, and where it belongs. */
    public record Request(RoomPurpose purpose, RoomShape shape,
                          Affinity affinity, EdgeContact contact) {

        public Request(RoomPurpose purpose, RoomShape shape) {
            this(purpose, shape, Affinity.ANYWHERE, EdgeContact.NONE);
        }

        public int area() {
            return shape.area();
        }
    }

    /**
     * Where a room ended up, and the doors that were cut for it.
     *
     * <p>This is already everything a {@link FurnishableRoom} is, so it says so
     * rather than being copied into one. A packed room and a placed compartment
     * are the same thing to a fitting — a floor, a pose, its doors, and what it
     * is for — and the whole point of drawing that seam was that neither family
     * has to know about the other.
     */
    public record Placed(RoomShape shape, RoomPose pose, int originX, int originY,
                         RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom {

        public Placed {
            doors = List.copyOf(doors);
        }
    }

    /** Weight keeping a room where it belongs; large enough to outrank any packing score. */
    private static final int AFFINITY_BONUS = 1_000_000;
    /** How many of the best-scoring placements to try before giving a room up as unfittable. */
    private static final int PLACEMENT_ATTEMPTS = 8;
    /**
     * How many to try when the room states where it hooks up. Deeper than the
     * ordinary search because it is looking for something specific — a position
     * whose doors the surrounding space can actually serve — and the best-packed
     * few are unlikely to be the ones a passage happens to run past.
     */
    private static final int HOOKUP_ATTEMPTS = 40;
    /** How many placements the scan keeps; the deepest search reads that many. */
    private static final int SHORTLIST = Math.max(PLACEMENT_ATTEMPTS, HOOKUP_ATTEMPTS);
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

    /**
     * Doors are worth having on each face and not worth having twice on one.
     * Four faces is the ceiling, and a room with fewer open faces gets fewer.
     */
    private static final int MAX_WAYS_IN = 4;

    /** Corners of the two-by-two square a route cell may be covered by. */
    private static final int[][] LANE_ANCHORS = { { 0, 0 }, { -1, 0 }, { 0, -1 }, { -1, -1 } };

    /** Padded by one cell each side, so a room's wall ring never falls off the array. */
    private final boolean[][] outside;
    private final boolean[][] room;
    private final boolean[][] claimed;
    private final boolean[][] floor;
    private final boolean[][] passage;
    /** Ring cells of rooms already placed — the seams a later room could glue itself to. */
    private final boolean[][] structure;
    private final GenContext ctx;
    private final Palette palette;
    private final Massing massing;
    private final int width;
    private final int height;
    private int[] claimedSum;
    /**
     * Walkable floor, summed the same way, so a solid room's whole bulkhead
     * ring can be judged without walking it. @see #ringContact
     */
    private int[] floorSum;
    /** Row stride of the summed tables, which are one larger than the padded masks. */
    private final int sumStride;
    private final int sumColumns;

    /**
     * @param buildable cells rooms may occupy; everything else is outside
     * @param circulation walkable space that already exists and must survive —
     *                    a ship's spine, a compound's approach — taken as
     *                    floor and claimed along with the ring around it, so no
     *                    room eats into it
     */
    public RoomPacker(GenContext ctx, boolean[][] buildable, boolean[][] circulation,
                      Palette palette) {
        this(ctx, buildable, circulation, palette, Massing.WEDGED);
    }

    /**
     * @param buildable cells rooms may occupy; everything else is outside
     * @param circulation walkable space that already exists and must survive
     * @param massing how freely this place lets its rooms glue together
     */
    public RoomPacker(GenContext ctx, boolean[][] buildable, boolean[][] circulation,
                      Palette palette, Massing massing) {
        this.ctx = ctx;
        this.palette = palette;
        this.massing = massing;
        this.structure = new boolean[ctx.width + 2][ctx.height + 2];
        this.width = ctx.width;
        this.height = ctx.height;
        this.sumStride = ctx.height + 3;
        this.sumColumns = ctx.width + 3;
        this.outside = new boolean[width + 2][height + 2];
        this.room = new boolean[width + 2][height + 2];
        this.claimed = new boolean[width + 2][height + 2];
        this.floor = new boolean[width + 2][height + 2];
        this.passage = new boolean[width + 2][height + 2];
        for (boolean[] column : claimed) {
            Arrays.fill(column, true);
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!buildable[x][y]) continue;
                claimed[x + 1][y + 1] = false;
                outside[x + 1][y + 1] = true;
            }
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!circulation[x][y]) continue;
                floor[x + 1][y + 1] = true;
                passage[x + 1][y + 1] = true;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx != 0 && dy != 0) continue;
                        int cx = x + dx;
                        int cy = y + dy;
                        if (cx < 0 || cy < 0 || cx >= width || cy >= height) continue;
                        claimed[cx + 1][cy + 1] = true;
                    }
                }
            }
        }
        rebuildSums();
    }

    /**
     * Find this room the best position it can still have, cut it in, and open it
     * onto the deck's circulation. Returns null when nothing fits, or when
     * nothing that fits can be reached.
     *
     * @param mayTunnel whether the room is worth cutting a fresh passage to
     */
    public Placed place(Request request, boolean mayTunnel) {
        RoomFitting fitting = RoomFittings.forPurpose(request.purpose());
        List<Hookup> hookups = fitting == null ? List.of() : fitting.hookups(request.shape());
        boolean handed = fitting != null && fitting.handed();
        List<Candidate> candidates =
                candidates(request, posesFor(request.shape(), handed));

        if (!hookups.isEmpty()) {
            Placed hooked = placeHooked(request, hookups, candidates, mayTunnel);
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
            List<Doorway> doors = commit(candidate, request.purpose(),
                    withFurtherWaysIn(candidate, access, mayTunnel));
            return describe(candidate, request.purpose(), doors);
        }
        return null;
    }

    /**
     * Join circulation that runs close together but is walked between the long
     * way round, and keep doing it until no cut left earns itself.
     *
     * <p>Run after everything is placed, because the defect is not in any one
     * placement. Each passage took the cheapest honest route to whatever was
     * already connected when it was cut, which makes the finished network a
     * tree: every branch a dead end, and two rooms a few cells apart on
     * different branches walked between by going back to the spine and out
     * again. Only the finished network can be asked where that hurts.
     *
     * <p>What the cut may cross is the same law as any other passage — never a
     * cell a compartment stands behind, never along a bulkhead, two abreast
     * where the space allows. A loop is a hallway that happens to have ends at
     * both ends; it is not a licence to open rooms.
     *
     * @return how many links were cut
     */
    public int openLoops(CirculationLoops.Policy policy) {
        int cuts = 0;
        while (true) {
            CirculationLoops.Link link = CirculationLoops.best(
                    routableGrid(null), circulationGrid(), throughableGrid(), policy);
            if (link == null) return cuts;
            carveLink(link);
            cuts++;
        }
    }

    /** Connective walkable space as it stands. Thresholds are deliberately not in it. */
    private boolean[][] circulationGrid() {
        boolean[][] grid = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = passage[x + 1][y + 1];
            }
        }
        return grid;
    }

    /** Cells a passage crossing structure may emerge into. */
    private boolean[][] throughableGrid() {
        boolean[][] grid = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = !claimed[x + 1][y + 1] || passage[x + 1][y + 1];
            }
        }
        return grid;
    }

    /**
     * Cut one link in. The same lane stamping the room passages use, so a loop
     * is the same width as the halls it joins rather than a foot-track between
     * them.
     */
    private void carveLink(CirculationLoops.Link link) {
        int[] anchor = null;
        for (int[] cell : link.route()) {
            boolean crossing = claimed[cell[0] + 1][cell[1] + 1];
            carveLane(cell[0], cell[1]);
            anchor = stampLane(null, cell[0], cell[1], anchor, crossing);
        }
        rebuildSums();
    }

    /**
     * Place a room where the deck can serve the doors it asked for, preferring
     * the position that serves the most of them.
     *
     * <p>Alternatives are tried in the order the fitting wrote them, so a bay
     * takes its drive-through arrangement wherever one is available and its
     * single-door arrangement only where one is not.
     */
    private Placed placeHooked(Request request, List<Hookup> hookups,
                               List<Candidate> candidates, boolean mayTunnel) {
        int attempts = Math.min(HOOKUP_ATTEMPTS, candidates.size());
        int wanted = 0;
        for (Hookup hookup : hookups) wanted = Math.max(wanted, hookup.slots().size());

        Candidate best = null;
        List<Access> bestAccesses = null;
        for (int i = 0; i < attempts; i++) {
            Candidate candidate = candidates.get(i);
            for (Hookup hookup : hookups) {
                List<Access> accesses = serve(candidate, request.shape(), hookup, mayTunnel);
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
        List<Doorway> doors = commit(best, request.purpose(), bestAccesses);
        return describe(best, request.purpose(), doors);
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
            accesses.add(access.within(allowed));
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
     * The best few placements the scan has seen, kept as they arrive.
     *
     * <p>Only the best {@link #HOOKUP_ATTEMPTS} are ever tried, and open deck
     * offers tens of thousands of legal positions for a single compartment.
     * Collecting all of them to sort them and read the top allocated a
     * candidate for every one.
     *
     * <p>Ordered exactly as the sort it replaces: by score, and otherwise by
     * the order the scan reached them. A placement equal to one already held
     * goes behind it, which is what a stable sort of the whole scan did.
     */
    private static final class Shortlist {

        private final Candidate[] held;
        private int size;

        Shortlist(int capacity) {
            held = new Candidate[capacity];
        }

        void offer(Candidate candidate) {
            if (size == held.length && !precedes(candidate, held[size - 1])) return;
            int at = size < held.length ? size : held.length - 1;
            while (at > 0 && precedes(candidate, held[at - 1])) at--;
            System.arraycopy(held, at, held, at + 1, Math.min(size, held.length - 1) - at);
            held[at] = candidate;
            if (size < held.length) size++;
        }

        List<Candidate> best() {
            return List.of(Arrays.copyOf(held, size));
        }

        private static boolean precedes(Candidate candidate, Candidate other) {
            if (candidate.score() != other.score()) {
                return candidate.score() > other.score();
            }
            if (candidate.x() != other.x()) return candidate.x() < other.x();
            return candidate.y() < other.y();
        }
    }

    /**
     * The poses a room may be laid down in.
     *
     * <p>Rotations only, deduplicated by mask, unless the arrangement is handed
     * — flips cost four times the candidates to consider and buy nothing at all
     * for a room with no front and no back. A room that does have one gets all
     * eight, which is what lets a single authored bay serve a deck whose
     * circulation runs down either side of it.
     */
    private static List<RoomPose> posesFor(RoomShape shape, boolean handed) {
        if (handed) return RoomPose.all();
        List<RoomPose> distinct = new ArrayList<>();
        Set<RoomShape> seen = new HashSet<>();
        for (RoomPose pose : RoomPose.all()) {
            if (pose.mirrored()) continue;
            if (seen.add(shape.posed(pose))) distinct.add(pose);
        }
        return distinct;
    }

    /**
     * The ways into this room: the one that was found, plus another on a
     * different face for every {@link #FLOOR_PER_WAY_IN} cells of floor.
     *
     * <p>One door is enough to make a room reachable, and reachability is all
     * the packing was ever checking. It is not enough to make a building worth
     * fighting over. A vehicle shed five hundred cells across with a single
     * entrance — widened to two cells, which reads as two doors on the same
     * wall — is cleared by holding one doorway, so an attacker never has to
     * choose an approach and a defender never has to cover more than one. The
     * interior might as well not be there.
     *
     * <p>Faces rather than count is the whole point. Another door beside the
     * first changes nothing; a door on the far wall means the building can be
     * entered from two sides at once, flanked, or given up from one end and
     * held at the other. So each further way in is searched with the faces
     * already used excluded outright.
     *
     * <p>Best-effort: a room wedged against its neighbours may have only one
     * face on the open, and one way in is better than refusing to place it.
     */
    private List<Access> withFurtherWaysIn(Candidate candidate, Access first,
                                           boolean mayTunnel) {
        List<Access> accesses = new ArrayList<>();
        accesses.add(first);
        if (massing.floorPerWayIn() <= 0) return accesses;
        int wanted = Math.min(MAX_WAYS_IN,
                1 + candidate.shape().area() / massing.floorPerWayIn());
        while (accesses.size() < wanted) {
            Access next = findAccess(candidate, mayTunnel,
                    doorwaysFacingAwayFrom(candidate, accesses));
            if (next == null) break;
            accesses.add(next);
        }
        return accesses;
    }

    /**
     * This room's authored doorway cells on faces none of {@code taken} uses.
     *
     * <p>Empty when every face is spoken for, which {@link #findAccess} reads as
     * "nothing is permitted" and answers with null — the caller stops there.
     */
    private Set<Long> doorwaysFacingAwayFrom(Candidate candidate, List<Access> taken) {
        Set<Long> free = new HashSet<>();
        for (int[] doorway : candidate.shape().doorways()) {
            int dirX = doorway[2] - doorway[0];
            int dirY = doorway[3] - doorway[1];
            boolean used = false;
            for (Access access : taken) {
                if (access.dirX() == dirX && access.dirY() == dirY) used = true;
            }
            if (used) continue;
            free.add(cellKey(candidate.x() + doorway[0], candidate.y() + doorway[1]));
        }
        return free;
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
    private List<Candidate> candidates(Request request, List<RoomPose> poses) {
        Shortlist found = new Shortlist(SHORTLIST);
        for (RoomPose pose : poses) {
            RoomShape shape = request.shape().posed(pose);
            int w = shape.width();
            int h = shape.height();
            int slack = w * h - shape.area();
            boolean solid = slack == 0;
            for (int x = 0; x + w <= width; x++) {
                for (int y = 0; y + h <= height; y++) {
                    // Necessary condition first: if the bounding box is more
                    // occupied than the shape's own holes could absorb, no
                    // per-cell test can save it.
                    int occupied = sum(claimedSum, x, y, w, h);
                    if (occupied > slack) continue;
                    // A footprint with no holes cannot disagree with its own
                    // bounding box, so nothing claimed inside it is the whole
                    // of the floor test.
                    if (!solid && occupied > 0 && !floorFits(shape, x, y)) continue;
                    int contact = solid ? ringContact(x, y, w, h)
                            : wallContact(shape, x, y);
                    if (contact < 0) continue;
                    if (!meetsEdge(request.contact(), shape, x, y)) continue;
                    int belongs = request.affinity().prefers(x + w / 2, y + h / 2)
                            ? AFFINITY_BONUS : 0;
                    int excess = massing.sharedSeamAllowance() == Integer.MAX_VALUE ? 0
                            : Math.max(0, glue(shape, x, y)
                                    - massing.sharedSeamAllowance());
                    found.offer(new Candidate(shape, pose, x, y,
                            belongs + contact + ctx.rng.nextInt(3)
                                    - excess * massing.seamPenalty()));
                }
            }
        }
        return found.best();
    }

    /**
     * The bulkhead ring of a solid footprint, judged from the summed tables
     * rather than walked.
     *
     * <p>A footprint with no holes reserves exactly the ring one cell outside
     * its own box, corners included, so what {@link #wallContact} counts a cell
     * at a time is the difference between two rectangles. That matters because
     * this is the innermost thing the packer does: every position of every pose
     * of every room asks it, which on a capital's deck is a few hundred
     * thousand questions per compartment. Walking the ring is what made laying
     * out a large hull a matter of tens of seconds.
     *
     * <p>The tables are over the padded masks, whose border is claimed and is
     * never floor — which is exactly how the walked form treats a ring cell
     * lying off the deck.
     *
     * <p>Asked only of a position whose own box holds nothing claimed, which
     * is what the bounding-box test already established for a footprint with
     * no holes. So the box contributes nothing to either count — floor is
     * always claimed — and the ring is the outer rectangle alone.
     *
     * @return how much of the ring backs onto something solid, or -1 where it
     *     crosses walkable floor and the placement is illegal
     */
    private int ringContact(int x, int y, int w, int h) {
        if (sum(floorSum, x - 1, y - 1, w + 2, h + 2) > 0) return -1;
        return sum(claimedSum, x - 1, y - 1, w + 2, h + 2);
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
    private boolean meetsEdge(EdgeContact contact, RoomShape shape, int ox, int oy) {
        if (contact == EdgeContact.NONE) return true;
        for (int[] cell : shape.wall()) {
            if (contact == EdgeContact.TRAILING && cell[0] != shape.width()) continue;
            int x = ox + cell[0];
            int y = oy + cell[1];
            if (!inBounds(x, y) || !outside[x + 1][y + 1]) return true;
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

    /**
     * How much of this ring would join a run of structure already standing.
     *
     * <p>Shared cells and abutting ones alike. A wall two cells thick is every
     * bit as impassable as one the two rooms share, so counting only the
     * overlap measures the wrong thing: rooms would simply stop sharing and go
     * on standing back to back, and the unbroken run would be exactly as long.
     * What has to be bought is a cell of yard between them.
     *
     * <p>Told apart from contact with the envelope and the circulation, which
     * are what the wedging is for: a room tucked into a corner of the ground it
     * was given, or laid along the road, costs nobody a detour. A room laid
     * against its neighbour's flank does.
     */
    private int glue(RoomShape shape, int ox, int oy) {
        int glued = 0;
        for (int[] cell : shape.wall()) {
            int x = ox + cell[0];
            int y = oy + cell[1];
            if (!inBounds(x, y)) continue;
            if (structure[x + 1][y + 1]) {
                glued++;
                continue;
            }
            for (int[] step : STEPS) {
                int nx = x + step[0];
                int ny = y + step[1];
                if (inBounds(nx, ny) && structure[nx + 1][ny + 1]) {
                    glued++;
                    break;
                }
            }
        }
        return glued;
    }

    /** A door through one bulkhead cell, and the passage cells cut to reach it. */
    /**
     * @param doorX door cell, in the bulkhead of the room being placed
     * @param doorY door cell, in the bulkhead of the room being placed
     * @param dirX outward step from the door to the cell it opens onto
     * @param dirY outward step from the door to the cell it opens onto
     * @param passage cells cut to reach circulation, nearest circulation first
     */
    /**
     * One way into a candidate: the bulkhead cell to cut, which way it faces,
     * and the passage that had to be opened to reach it.
     *
     * @param doorway the cells this doorway may occupy, or null where the room
     *     did not author one. A doorway is widened to two cells wherever it can
     *     be, and widening it out of the slot the room named would undo the
     *     authoring: a berth budgets a rack for each hatch, and a hatch that
     *     spread into the neighbouring slot took a second rack the fitting had
     *     already laid a bunk in.
     */
    private record Access(int doorX, int doorY, int dirX, int dirY, List<int[]> passage,
                          Set<Long> doorway) {

        Access(int doorX, int doorY, int dirX, int dirY, List<int[]> passage) {
            this(doorX, doorY, dirX, dirY, passage, null);
        }

        Access within(Set<Long> doorway) {
            return new Access(doorX, doorY, dirX, dirY, passage, doorway);
        }
    }

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
     *
     * @param candidate the room being placed, or null when the route belongs to
     *     no room. A loop cut between two finished passages has nothing
     *     uncommitted to hide from the masks, so there is nothing to exclude.
     */
    private int stepCost(Candidate candidate, int x, int y) {
        if (!inBounds(x, y) || !outside[x + 1][y + 1] || floor[x + 1][y + 1]) return -1;
        if (candidate != null) {
            int localX = x - candidate.x();
            int localY = y - candidate.y();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (candidate.shape().contains(localX + dx, localY + dy)) return -1;
                }
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
    private List<Doorway> commit(Candidate candidate,
                                                   RoomPurpose purpose, List<Access> accesses) {
        carveRoom(candidate, purpose);
        List<Doorway> doors = new ArrayList<>();
        for (Access access : accesses) {
            doors.addAll(cutDoor(candidate, access));
        }
        rebuildSums();
        return List.copyOf(doors);
    }

    /** Cut the room and its bulkheads into the deck, without its access. */
    private void carveRoom(Candidate candidate, RoomPurpose purpose) {
        RoomShape shape = candidate.shape();
        for (int[] cell : shape.filled()) {
            int x = candidate.x() + cell[0];
            int y = candidate.y() + cell[1];
            carve(x, y, purpose, palette.roomFloor());
            room[x + 1][y + 1] = true;
        }
        for (int[] cell : shape.wall()) {
            int x = candidate.x() + cell[0];
            int y = candidate.y() + cell[1];
            if (!inBounds(x, y)) continue;
            claimed[x + 1][y + 1] = true;
            structure[x + 1][y + 1] = true;
            // A claimed bulkhead is real navigation structure, not merely a
            // promise that another packed room will stay away. Fortress rooms
            // can inherit live city floor under this ring; leaving it walkable
            // joins the room to the yard even when its threshold is a doorway.
            ctx.grid.setWalkable(x, y, false);
            ctx.grid.setDoorway(x, y, false);
        }
    }

    /** Cut one doorway and whatever passage was needed to reach it. */
    private List<Doorway> cutDoor(Candidate candidate,
                                                     Access access) {
        int[] anchor = null;
        for (int[] cell : access.passage()) {
            boolean crossing = claimed[cell[0] + 1][cell[1] + 1];
            carveLane(cell[0], cell[1]);
            anchor = stampLane(candidate, cell[0], cell[1], anchor, crossing);
        }
        // The door itself is a threshold, not circulation: leaving it out of the
        // passage mask is what stops the next room treating it as a hallway.
        carveDoorway(access.doorX(), access.doorY());
        List<Doorway> doors = new ArrayList<>();
        doors.add(new Doorway(access.doorX(), access.doorY()));
        Doorway widened = widenDoorway(candidate, access);
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
    private int[] stampLane(Candidate candidate, int x, int y,
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
            carveLane(ax, ay);
            carveLane(ax + 1, ay);
            carveLane(ax, ay + 1);
            carveLane(ax + 1, ay + 1);
            return anchor;
        }
        return preferred;
    }

    /** Cut one cell of hall, leaving deck that is already walkable alone. */
    private void carveLane(int x, int y) {
        if (!inBounds(x, y) || floor[x + 1][y + 1]) return;
        carve(x, y, RoomPurpose.CORRIDOR, palette.hall());
        passage[x + 1][y + 1] = true;
    }

    /**
     * Take the door to two cells where the wall and the space beyond allow it.
     * A compartment berthing a watch behind a one-cell threshold bottlenecks
     * everything that happens at it. The second cell has to be this room's own
     * bulkhead and has to open onto the same circulation, so this widens the
     * door rather than punching a second one somewhere else in the wall — and
     * where the room authored its doorway, the second cell has to be one the
     * room named.
     */
    private Doorway widenDoorway(Candidate candidate,
                                                    Access access) {
        int perpX = access.dirY();
        int perpY = access.dirX();
        for (int side : new int[]{ 1, -1 }) {
            int nx = access.doorX() + perpX * side;
            int ny = access.doorY() + perpY * side;
            if (!inBounds(nx, ny) || floor[nx + 1][ny + 1]) continue;
            if (!permits(access.doorway(), nx, ny)) continue;
            if (backsOntoOtherRoom(candidate, nx, ny)) continue;
            int localX = nx - candidate.x();
            int localY = ny - candidate.y();
            if (candidate.shape().contains(localX, localY)) continue;
            if (!candidate.shape().contains(localX - access.dirX(), localY - access.dirY())) continue;
            int outsideX = nx + access.dirX();
            int outsideY = ny + access.dirY();
            if (!inBounds(outsideX, outsideY) || !floor[outsideX + 1][outsideY + 1]) continue;
            carveDoorway(nx, ny);
            return new Doorway(nx, ny);
        }
        return null;
    }

    /**
     * Cut a threshold and publish it to the navigation-zone layer.
     *
     * <p>A walkable wall opening without the doorway tag is ordinary floor to
     * {@code ZoneDetector}: it flood-fills the room and the surrounding deck or
     * yard into one zone. That made every packed fortress strongpoint share the
     * outdoor zone, so one unit anywhere outside contested every compound at
     * once. The room packer already owns the exact doorway cells; marking them
     * here keeps each packed room a distinct tactical/capture zone while the
     * portal graph still connects it to circulation.
     */
    private void carveDoorway(int x, int y) {
        carve(x, y, RoomPurpose.CORRIDOR, palette.threshold());
        ctx.grid.setDoorway(x, y, true);
    }

    private void carve(int x, int y, RoomPurpose purpose, GroundKind kind) {
        if (!inBounds(x, y)) return;
        ctx.grid.setWalkableFloor(x, y);
        ctx.topology.setGroundKind(x, y, kind);
        ctx.topology.setRoomPurpose(x, y, purpose);
        claimed[x + 1][y + 1] = true;
        floor[x + 1][y + 1] = true;
    }

    private static Placed describe(Candidate candidate, RoomPurpose purpose,
                                   List<Doorway> doors) {
        return new Placed(candidate.shape(), candidate.pose(),
                candidate.x(), candidate.y(), purpose, doors);
    }

    private void rebuildSums() {
        claimedSum = prefix(claimed, claimedSum);
        floorSum = prefix(floor, floorSum);
    }

    /**
     * Summed-area table over the padded mask, so the bounding-box reject is
     * four lookups.
     *
     * <p>Flat rather than a table of rows. The scan reads these tables a dozen
     * times for every position of every pose of every room, which on a large
     * deck is tens of millions of reads through a pointer to a row that is
     * rarely the one read last.
     */
    private int[] prefix(boolean[][] mask, int[] into) {
        int w = mask.length;
        int h = mask[0].length;
        int[] sums = into != null ? into : new int[(w + 1) * (h + 1)];
        for (int x = 0; x < w; x++) {
            int row = (x + 1) * sumStride;
            int previous = x * sumStride;
            for (int y = 0; y < h; y++) {
                sums[row + y + 1] = (mask[x][y] ? 1 : 0)
                        + sums[previous + y + 1] + sums[row + y] - sums[previous + y];
            }
        }
        return sums;
    }

    /** Count of set cells in the unpadded rect {@code (x, y, w, h)}, clipped to the padded mask. */
    private int sum(int[] sums, int x, int y, int w, int h) {
        int x0 = Math.max(0, Math.min(sumColumns - 1, x + 1));
        int y0 = Math.max(0, Math.min(sumStride - 1, y + 1));
        int x1 = Math.max(x0, Math.min(sumColumns - 1, x + 1 + w));
        int y1 = Math.max(y0, Math.min(sumStride - 1, y + 1 + h));
        int low = x0 * sumStride;
        int high = x1 * sumStride;
        return sums[high + y1] - sums[low + y1] - sums[high + y0] + sums[low + y0];
    }
}
