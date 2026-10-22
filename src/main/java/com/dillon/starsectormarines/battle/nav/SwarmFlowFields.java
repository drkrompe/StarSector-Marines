package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * One shared integration field per swarm goal: every walkable cell's step
 * distance to the goal's nearest source cell. A swarm body moves toward a goal
 * by walking downhill on its field, so strategic movement for the whole swarm
 * costs one map-sized integration per goal refresh plus a few neighbour reads
 * per body, instead of a path search per body.
 *
 * <p>Owned by the {@link NavigationGrid} it integrates over, for the reason
 * {@link LosCaches} is: a field is derived from this grid's topology and
 * nothing else's, so two simulations in one process can never read each
 * other's fields, and a grid's topology revision is what retires them.
 *
 * <h2>Threading</h2>
 * {@link #refresh} and {@link #retireUnused} are serial: the simulation calls
 * them before the parallel unit update. Workers only {@link #field read} the
 * published immutable fields and {@link #request} them. A request made during
 * tick {@code T} is answered by refreshes from tick {@code T + 1}, so what a
 * field holds never depends on worker order.
 *
 * <h2>Cadence</h2>
 * A rebuild starts when a goal's sources or the grid's topology have changed,
 * no sooner than {@link #REFRESH_TICKS} ticks after the last one started. It
 * is integrated a fixed number of cell expansions per tick
 * ({@link #EXPANSIONS_PER_TICK}) — a count, not a clock, so a replay builds
 * the same field on the same tick — and published whole when it completes.
 * Until then bodies descend the previous field against the current walls, so
 * a closed cell is never entered and an opened one is used once the field
 * catches up. A rebuild in progress is not restarted by a further change; the
 * field it publishes carries the revision it started from and is rebuilt
 * again on the next cadence.
 */
public final class SwarmFlowFields {

    /** {@code -Dbattle.swarm.flowField=false} restores per-body pathing for swarm strategic movement. */
    public static final String ENABLED_PROPERTY = "battle.swarm.flowField";
    /** Fewest ticks between the starts of two rebuilds of one goal's field. */
    public static final int REFRESH_TICKS = 10;
    /**
     * Cells a rebuild may settle per tick: a 560x336 map (188,160 cells)
     * completes in three ticks, a 280x168 one in one.
     */
    public static final int EXPANSIONS_PER_TICK = 1 << 16;
    /** A goal nobody has asked for in this many ticks releases its field. */
    public static final int RETIRE_AFTER_TICKS = 90;
    /** Goal slots; goal ids are small dense integers chosen by the caller. */
    public static final int MAX_GOALS = 8;

    /** Distance of a cell the goal cannot be reached from. */
    public static final int UNREACHABLE = Integer.MAX_VALUE;
    static final int CARDINAL_COST = 10;
    static final int DIAGONAL_COST = 14;
    /**
     * What crossing a militia barricade cell costs over an ordinary step: a
     * detour up to about this many cells is walked round, a longer one is torn
     * through ({@link NavigationGrid.CellTag#BARRICADE}).
     */
    static final int BARRICADE_COST = 12 * CARDINAL_COST;
    /** Dial's ring must be wider than the dearest single step. */
    private static final int BUCKETS = 256;
    /** {@code -Dbattle.swarm.barricadeAssault=false} is the control: a barricade is a wall to the swarm. */
    public static final String BARRICADE_ASSAULT_PROPERTY = "battle.swarm.barricadeAssault";
    private static final long BARRICADE = NavigationGrid.CellTag.BARRICADE.mask();

    public static boolean barricadeAssaultEnabled() {
        return Boolean.parseBoolean(System.getProperty(BARRICADE_ASSAULT_PROPERTY, "true"));
    }

    public static boolean defaultEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    private final NavigationGrid grid;
    private final int expansionsPerTick;
    private final AtomicReferenceArray<Field> published = new AtomicReferenceArray<>(MAX_GOALS);
    private final AtomicIntegerArray lastRequestTick = new AtomicIntegerArray(MAX_GOALS);
    private final long[] lastSourceHash = new long[MAX_GOALS];
    private final int[][] fixedSources = new int[MAX_GOALS][];
    private final Integration[] building = new Integration[MAX_GOALS];

    SwarmFlowFields(NavigationGrid grid) {
        this(grid, EXPANSIONS_PER_TICK);
    }

    SwarmFlowFields(NavigationGrid grid, int expansionsPerTick) {
        if (expansionsPerTick < 1) throw new IllegalArgumentException("expansionsPerTick must be positive");
        this.grid = grid;
        this.expansionsPerTick = expansionsPerTick;
        for (int i = 0; i < MAX_GOALS; i++) lastRequestTick.set(i, Integer.MIN_VALUE);
    }

    /** The published field for a goal, or {@code null} until its first rebuild completes. */
    public Field field(int goal) {
        return published.get(goal);
    }

    /**
     * Serial: declares a goal whose sources are fixed cells (a shelter's
     * doors, a lift's pads) rather than gathered each tick; {@code null}
     * withdraws them. The field follows on the next refresh.
     */
    public void setFixedSources(int goal, int[] cells) {
        fixedSources[goal] = cells == null ? null : cells.clone();
    }

    /** The fixed source cells declared for a goal, or {@code null}. */
    public int[] fixedSources(int goal) {
        return fixedSources[goal];
    }

    /** Worker-safe: marks a goal as wanted, so the serial refreshes keep it current. */
    public void request(int goal, int tick) {
        lastRequestTick.accumulateAndGet(goal, tick, Math::max);
    }

    /** Whether a goal has been requested within {@link #RETIRE_AFTER_TICKS} of {@code tick}. */
    public boolean isWanted(int goal, int tick) {
        int last = lastRequestTick.get(goal);
        return last != Integer.MIN_VALUE && tick - last <= RETIRE_AFTER_TICKS;
    }

    /**
     * Serial, once per tick per wanted goal: advances a rebuild in progress,
     * or starts one when the sources or topology moved and the cadence
     * allows. Returns whether a new field was published by this call.
     *
     * @param sources grid cell indices; order does not matter. Read only when
     *                a rebuild starts.
     */
    public boolean refresh(int goal, int[] sources, int sourceCount, int tick) {
        Integration pending = building[goal];
        if (pending == null || pending.done()) {
            long sourceHash = hashSources(sources, sourceCount);
            Field current = published.get(goal);
            if (current != null) {
                boolean stale = current.topologyRevision != grid.topologyRevision()
                        || lastSourceHash[goal] != sourceHash;
                if (!stale || tick - current.builtTick < REFRESH_TICKS) return false;
            }
            if (pending == null) {
                pending = new Integration(grid.getWidth() * grid.getHeight());
                building[goal] = pending;
            }
            pending.start(grid, sources, sourceCount, tick);
            lastSourceHash[goal] = sourceHash;
        }
        long started = System.nanoTime();
        boolean finished = pending.advance(grid, expansionsPerTick);
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (profile != null) {
            profile.record(TickInnerProfile.Bucket.SWARM_FLOW_FIELD_BUILD,
                    System.nanoTime() - started);
        }
        if (!finished) return false;
        published.set(goal, pending.publish(grid.getWidth(), grid.getHeight()));
        return true;
    }

    /** Serial: releases fields, and rebuilds in progress, that no body has asked for lately. */
    public void retireUnused(int tick) {
        for (int goal = 0; goal < MAX_GOALS; goal++) {
            if (isWanted(goal, tick)) continue;
            published.set(goal, null);
            building[goal] = null;
        }
    }

    /** Order-independent: the same set of source cells hashes the same in any order. */
    private static long hashSources(int[] sources, int count) {
        long sum = 0L;
        long xor = 0L;
        for (int i = 0; i < count; i++) {
            long mixed = mix(sources[i] + 0x9E3779B97F4A7C15L);
            sum += mixed;
            xor ^= Long.rotateLeft(mixed, 17);
        }
        return sum ^ xor ^ count;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    /**
     * An immutable integration: step distance (cardinal 10, diagonal 14) from
     * each cell to the nearest source under the same step rule as A*.
     */
    public static final class Field {
        private final int width;
        private final int height;
        private final int[] distance;
        final long topologyRevision;
        final int builtTick;

        Field(int width, int height, int[] distance, long topologyRevision, int builtTick) {
            this.width = width;
            this.height = height;
            this.distance = distance;
            this.topologyRevision = topologyRevision;
            this.builtTick = builtTick;
        }

        /** Field distance at a cell, {@link #UNREACHABLE} when the goal cannot be reached from it. */
        public int distanceAt(int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) return UNREACHABLE;
            return distance[y * width + x];
        }

        /** The tick whose sources and topology this field was integrated from. */
        public int builtTick() { return builtTick; }

        /**
         * Up to {@code maxSteps} cells downhill from {@code (x, y)}, as a path
         * in {@link Paths} layout starting at the start cell. Each step goes to
         * the nearest neighbour the grid lets this cell step into, ties to the
         * lower direction index, and only ever strictly nearer, so the walk
         * cannot loop. Empty when the start is unreachable or already a source.
         *
         * <p>There is deliberately no crowd term. The occupancy map marks where
         * paths end, not where bodies stand, and every weight given it in the
         * chokepoint scene only bent bodies onto less direct steps: arrival at
         * the goal slowed in step with the weight, and no body was freed that
         * the plain descent had left stuck.
         */
        public int[] descend(NavigationGrid grid, int x, int y, int maxSteps) {
            if (distanceAt(x, y) == UNREACHABLE || distanceAt(x, y) == 0 || maxSteps <= 0) {
                return GridPathfinder.EMPTY_PATH;
            }
            long[] cellFlags = grid.getCellFlagsArray();
            int[] steps = new int[(maxSteps + 1) * 2];
            steps[0] = x;
            steps[1] = y;
            int count = 1;
            int cx = x;
            int cy = y;
            while (count <= maxSteps) {
                int bestIdx = downhill(grid, cx, cy);
                // A barricade is not walked into: the body stops in front of it, and tears it down.
                if (bestIdx < 0 || (cellFlags[bestIdx] & BARRICADE) != 0L) break;
                cx = bestIdx % width;
                cy = bestIdx / width;
                steps[count * 2] = cx;
                steps[count * 2 + 1] = cy;
                count++;
            }
            if (count == 1) return GridPathfinder.EMPTY_PATH;
            return count * 2 == steps.length ? steps : Arrays.copyOf(steps, count * 2);
        }

        /**
         * The barricade cell the field's next step from {@code (x, y)} goes
         * through, as a grid index, or -1 when the way on is open ground: where
         * a body standing here has to tear its way on.
         */
        public int barricadeAhead(NavigationGrid grid, int x, int y) {
            if (distanceAt(x, y) == UNREACHABLE || distanceAt(x, y) == 0) return -1;
            int next = downhill(grid, x, y);
            return next >= 0 && (grid.getCellFlagsArray()[next] & BARRICADE) != 0L ? next : -1;
        }

        /**
         * Whether the field's way down from {@code (x, y)} runs through a
         * barricade within {@code steps} cells: the field has already weighed
         * going round against tearing through, and tearing through won.
         */
        public boolean leadsThroughBarricade(NavigationGrid grid, int x, int y, int steps) {
            if (distanceAt(x, y) == UNREACHABLE || distanceAt(x, y) == 0) return false;
            long[] cellFlags = grid.getCellFlagsArray();
            int cx = x, cy = y;
            for (int i = 0; i < steps; i++) {
                int next = downhill(grid, cx, cy);
                if (next < 0 || distance[next] == 0) return false;
                if ((cellFlags[next] & BARRICADE) != 0L) return true;
                cx = next % width;
                cy = next / width;
            }
            return false;
        }

        /** The nearest neighbour a body here can step toward, ties to the lower direction index; -1 at a minimum. */
        private int downhill(NavigationGrid grid, int cx, int cy) {
            long[] cellFlags = grid.getCellFlagsArray();
            byte[] edges = grid.getEdgePassabilityArray();
            int directions = GridPathfinder.directionCount(GridPathfinder.USE_CARDINAL_NAVIGATION);
            int here = cy * width + cx;
            int bestIdx = -1;
            int bestDistance = distance[here];
            for (int d = 0; d < directions; d++) {
                int nx = cx + GridPathfinder.directionX(d);
                int ny = cy + GridPathfinder.directionY(d);
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int next = ny * width + nx;
                int nextDistance = distance[next];
                if (nextDistance >= bestDistance) continue;
                if (!step(here, cx, cy, next, d, width, height, cellFlags, edges)) continue;
                bestDistance = nextDistance;
                bestIdx = next;
            }
            return bestIdx;
        }
    }

    /**
     * Whether a body may step from {@code from} into {@code to} on a field: the
     * pathfinder's own step, or a cardinal step into or out of a barricade
     * cell from walkable ground or another barricade cell.
     */
    static boolean step(int from, int fromX, int fromY, int to, int direction, int width, int height,
                        long[] cellFlags, byte[] edges) {
        boolean barricade = ((cellFlags[from] | cellFlags[to]) & BARRICADE) != 0L;
        if (!barricade) {
            return GridPathfinder.canStep(from, fromX, fromY, to, direction, width, height, cellFlags, edges, null);
        }
        if (direction >= GridPathfinder.FIRST_DIAGONAL_DIRECTION) return false;
        long walkable = NavigationGrid.CellTag.WALKABLE.mask();
        return (cellFlags[from] & (walkable | BARRICADE)) != 0L && (cellFlags[to] & (walkable | BARRICADE)) != 0L;
    }

    /**
     * One resumable integration by Dial's algorithm: integer step costs
     * bounded by {@link #DIAGONAL_COST} let a ring of buckets replace the
     * heap, so a build is linear in the cells it reaches and can stop after
     * any expansion and carry on next tick. The bucket scratch is reused; the
     * distance array is fresh per build, because the published field is read
     * by workers while the next one is integrated.
     */
    private static final class Integration {
        private final int[][] buckets = new int[BUCKETS][];
        private final int[] sizes = new int[BUCKETS];
        private int[] distance;
        private long pending;
        private int current;
        private long topologyRevision;
        private int startedTick;
        private boolean crossBarricades;
        private boolean finished = true;

        Integration(int cells) {
            int initial = Math.max(64, cells / BUCKETS);
            for (int i = 0; i < BUCKETS; i++) buckets[i] = new int[initial];
        }

        boolean done() { return finished; }

        void start(NavigationGrid grid, int[] sources, int sourceCount, int tick) {
            distance = new int[grid.getWidth() * grid.getHeight()];
            Arrays.fill(distance, UNREACHABLE);
            Arrays.fill(sizes, 0);
            long[] cellFlags = grid.getCellFlagsArray();
            pending = 0L;
            current = 0;
            topologyRevision = grid.topologyRevision();
            startedTick = tick;
            crossBarricades = barricadeAssaultEnabled();
            finished = false;
            for (int i = 0; i < sourceCount; i++) {
                int source = sources[i];
                if (source < 0 || source >= distance.length) continue;
                if ((cellFlags[source] & 1L) == 0L || distance[source] == 0) continue;
                distance[source] = 0;
                push(0, source);
                pending++;
            }
        }

        /** Settles up to {@code budget} cells; true once the whole reachable map is settled. */
        boolean advance(NavigationGrid grid, int budget) {
            int width = grid.getWidth();
            int height = grid.getHeight();
            long[] cellFlags = grid.getCellFlagsArray();
            byte[] edges = grid.getEdgePassabilityArray();
            int directions = GridPathfinder.directionCount(GridPathfinder.USE_CARDINAL_NAVIGATION);
            int settled = 0;
            while (pending > 0) {
                int slot = current & (BUCKETS - 1);
                while (sizes[slot] > 0) {
                    if (settled == budget) return false;
                    int cell = buckets[slot][--sizes[slot]];
                    pending--;
                    if (distance[cell] != current) continue;
                    settled++;
                    int cx = cell % width;
                    int cy = cell / width;
                    for (int d = 0; d < directions; d++) {
                        // Reverse search: the predecessor steps into this cell.
                        int px = cx - GridPathfinder.directionX(d);
                        int py = cy - GridPathfinder.directionY(d);
                        if (px < 0 || py < 0 || px >= width || py >= height) continue;
                        int predecessor = py * width + px;
                        int candidate = current + (d < GridPathfinder.FIRST_DIAGONAL_DIRECTION
                                ? CARDINAL_COST : DIAGONAL_COST)
                                + ((cellFlags[cell] & BARRICADE) != 0L ? BARRICADE_COST : 0);
                        if (candidate >= distance[predecessor]) continue;
                        if (crossBarricades) {
                            if (!step(predecessor, px, py, cell, d, width, height, cellFlags, edges)) continue;
                        } else if (!GridPathfinder.canStep(predecessor, px, py, cell, d, width, height,
                                cellFlags, edges, null)) continue;
                        distance[predecessor] = candidate;
                        push(candidate & (BUCKETS - 1), predecessor);
                        pending++;
                    }
                }
                current++;
            }
            finished = true;
            return true;
        }

        Field publish(int width, int height) {
            Field field = new Field(width, height, distance, topologyRevision, startedTick);
            distance = null;
            return field;
        }

        private void push(int slot, int cell) {
            if (sizes[slot] == buckets[slot].length) {
                buckets[slot] = Arrays.copyOf(buckets[slot], buckets[slot].length * 2);
            }
            buckets[slot][sizes[slot]++] = cell;
        }
    }
}
