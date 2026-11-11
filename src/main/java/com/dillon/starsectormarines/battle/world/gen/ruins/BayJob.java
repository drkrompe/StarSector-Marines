package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Set;

/**
 * What a bay of a plant's building did for the works ({@code ruins-expedition.md}, The bays have jobs): the secondary
 * work a real plant ran round its spaces, taken by where the bay stands along the material flow from intake through
 * raw stock, the works hall and line two to finished goods and dispatch. A bay beside a store or the dock was that
 * space's marshalling floor and opens onto it whole; a bay against the outer hull carried the plant's pipe and cable
 * galleries; the rest serve the space they stand nearest.
 *
 * <p>Neighbouring bays of one job were one space ({@link #fuse}): a gallery runs on along the hull, a tank farm or a
 * crating floor takes up to six of the grid's cells, so the building reads as rooms a plant worked rather than a
 * grid of cells.
 */
enum BayJob {
    /** Beside a store or the dock: the floor its loads were marshalled on, open onto it. */
    MARSHALLING("marshalling", 4),
    /** Against the outer hull: the pipe and cable galleries that fed the plant. */
    GALLERY("gallery", 6),
    /** Beside line two: more of its line, the hall run on through the bay. */
    LINE("line", 6),
    /** Beside the works hall or the maintenance shop: a line workshop. */
    WORKSHOP("workshop", 3),
    /** By the line's stores and the intake: spares and overflow stock. */
    SPARES("spares", 3),
    /** Round the habitat: the shift lockers and crew stores. */
    LOCKERS("lockers", 4),
    /** Beside plant services: the tank farm and its pumps. */
    TANK_FARM("tank-farm", 6),
    /** By finished goods: packing and crating for dispatch. */
    CRATING("crating", 6);

    /** The bay's published name, before its number. */
    final String id;
    /** How many cells of the bay grid one bay of this job may take. */
    final int fuse;

    BayJob(String id, int fuse) {
        this.id = id;
        this.fuse = fuse;
    }

    /** The brief's spaces a bay beside them marshals for: the stores and the dock. */
    static final Set<String> MARSHALLED = Set.of("dock", "raw-stock", "finished-goods");

    /**
     * The job of a bay that stands nearest the brief's space {@code nearest} ({@code works-hall} for the works hall,
     * null when none), {@code beside} names the store or dock it shares a wall with (else null), {@code onHull}
     * whether it lies against the outer hull.
     */
    static BayJob of(String nearest, String beside, boolean onHull) {
        if (beside != null && MARSHALLED.contains(beside)) return MARSHALLING;
        if (onHull) return GALLERY;
        if (nearest == null) return SPARES;
        return switch (nearest) {
            case "habitat" -> LOCKERS;
            case "services" -> TANK_FARM;
            case "finished-goods" -> CRATING;
            case "line-two", "line-two-bay" -> LINE;
            case "works-hall", "maintenance", "control", "lab" -> WORKSHOP;
            default -> SPARES;
        };
    }

    /**
     * Groups a grid of bay cells, {@code cols} by {@code rows}, into bays: each cell's job ordinal in {@code jobs}
     * (negative where the grid cell holds no bay); neighbouring cells of one job join one group, grown from the
     * lowest cell first, until it holds that job's {@link #fuse} cells. The group of each cell, or -1.
     */
    static int[] fuse(int[] jobs, int cols, int rows) {
        int[] group = new int[jobs.length];
        Arrays.fill(group, -1);
        int next = 0;
        BayJob[] all = values();
        for (int start = 0; start < jobs.length; start++) {
            if (jobs[start] < 0 || group[start] >= 0) continue;
            int cap = all[jobs[start]].fuse, size = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            group[start] = next;
            while (!queue.isEmpty() && size < cap) {
                int k = queue.poll();
                size++;
                int x = k % cols, y = k / cols;
                int[][] steps = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
                for (int[] s : steps) {
                    int nx = x + s[0], ny = y + s[1];
                    if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
                    int n = ny * cols + nx;
                    if (group[n] >= 0 || jobs[n] != jobs[start] || size + queue.size() >= cap) continue;
                    group[n] = next;
                    queue.add(n);
                }
            }
            next++;
        }
        return group;
    }
}
