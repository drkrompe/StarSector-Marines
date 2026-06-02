package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;

import java.util.ArrayList;
import java.util.List;

/**
 * The station's room/corridor connectivity graph — first-class generator
 * output, not just carved cells. Rooms are vertices (one per BSP leaf),
 * corridors are the carved subset of leaf-adjacency edges that
 * {@link com.dillon.starsectormarines.battle.world.gen.bsp.stage.CorridorStage}
 * connected.
 *
 * <p>This is the "generator publishes structure, passes consume it" seam the
 * corridors-first-class design centers on: downstream placement/spawn passes
 * query this graph instead of re-deriving topology from raw geometry. Two tiers
 * of signal:
 * <ul>
 *   <li><b>Structure</b> — rooms, corridors, {@link #neighbors neighbors},
 *       {@link #degree degree} (1 = dead-end, 2 = pass-through, ≥3 = hub). Built
 *       at carve time by {@link
 *       com.dillon.starsectormarines.battle.world.gen.bsp.stage.CorridorStage}
 *       ({@link #addCorridor}).</li>
 *   <li><b>Topological roles</b> — {@link #depthFromEntry depth-from-entry} (the
 *       indoor assault gradient), {@link #isArticulation articulation rooms} +
 *       {@link #isBridge bridge corridors} (must-pass fortify points), and
 *       {@link #isOnLoop on-spine vs on-loop} (main line vs flank). Derived
 *       <em>after</em> spawns are known by {@link
 *       com.dillon.starsectormarines.battle.world.gen.bsp.stage.StationTopologyStage}
 *       via {@link #applyRoles}; {@link #hasRoles} reports whether they're
 *       populated. These are the foundation later placement rules query.</li>
 * </ul>
 *
 * <p>Treated as read-only by consumers once both tiers are filled. Room id ==
 * index into {@link #rooms()}, assigned in BSP leaf order (deterministic);
 * corridor index == position in {@link #corridors()} (addition order).
 */
public final class StationGraph {

    /** One carved room. {@code id} indexes {@link #rooms()}; bounds are the inclusive cell rect. */
    public static final class Room {
        public final int id;
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;
        public final int centerX;
        public final int centerY;

        public Room(int id, BlockLeaf leaf) {
            this(id, leaf.left, leaf.top, leaf.right, leaf.bottom);
        }

        /** Explicit-bounds room — used by layouts that don't come from a {@link BlockLeaf} (e.g. concentric rings). */
        public Room(int id, int left, int top, int right, int bottom) {
            this.id = id;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.centerX = (left + right) / 2;
            this.centerY = (top + bottom) / 2;
        }
    }

    /** One carved corridor connecting two rooms (undirected; stored once). */
    public static final class Corridor {
        public final int roomA;
        public final int roomB;

        public Corridor(int roomA, int roomB) {
            this.roomA = roomA;
            this.roomB = roomB;
        }
    }

    private final List<Room> rooms;
    private final List<Corridor> corridors = new ArrayList<>();
    private final List<List<Integer>> adjacency;

    // --- structural layout metadata: set by layouts that have a ring/core notion (concentric); -1 otherwise ---
    private int[] ringOf;   // 0 = core, rising outward; null for layouts without rings (BSP station)
    private int coreRoom = -1;
    private int[] ports = new int[0];   // entry-port room ids (the diamond layout's cardinal landing zones); empty otherwise

    // --- derived topological roles: null until StationTopologyStage calls applyRoles ---
    private int entryRoom = -1;
    private int[] depthFromEntry;     // BFS hops from entryRoom; -1 if unreachable
    private boolean[] articulation;   // must-pass room: its removal disconnects the graph
    private boolean[] onLoop;         // room touches a cycle (has ≥1 non-bridge incident corridor)
    private boolean[] bridgeCorridor; // per corridors() index: the sole link to a subtree

    public StationGraph(List<Room> rooms) {
        this.rooms = List.copyOf(rooms);
        this.adjacency = new ArrayList<>(rooms.size());
        for (int i = 0; i < rooms.size(); i++) {
            adjacency.add(new ArrayList<>());
        }
    }

    /**
     * Record a corridor between two rooms and update both adjacency lists.
     * Callers are expected to dedupe edges first (each undirected pair added
     * once) so {@link #degree(int)} counts distinct neighbors.
     */
    public void addCorridor(int roomA, int roomB) {
        corridors.add(new Corridor(roomA, roomB));
        adjacency.get(roomA).add(roomB);
        adjacency.get(roomB).add(roomA);
    }

    public List<Room> rooms() {
        return rooms;
    }

    public int roomCount() {
        return rooms.size();
    }

    public Room room(int id) {
        return rooms.get(id);
    }

    public List<Corridor> corridors() {
        return List.copyOf(corridors);
    }

    public int corridorCount() {
        return corridors.size();
    }

    /** Neighbor room ids reachable by one corridor from {@code roomId}. */
    public List<Integer> neighbors(int roomId) {
        return adjacency.get(roomId);
    }

    /** Number of corridors meeting this room — 1 = dead-end, 2 = pass-through, ≥3 = hub. */
    public int degree(int roomId) {
        return adjacency.get(roomId).size();
    }

    // ----- Ring/core layout metadata (concentric stations) -----

    /**
     * Record the concentric-ring structure. {@code ringOf} is indexed by room id
     * (0 = core, rising outward); {@code coreRoom} is the central control room.
     * Set by {@code ConcentricLayoutStage}; layouts without rings leave it unset.
     */
    public void setRings(int[] ringOf, int coreRoom) {
        this.ringOf = ringOf;
        this.coreRoom = coreRoom;
    }

    /** True once {@link #setRings} has run — this is a concentric-ring station. */
    public boolean hasRings() {
        return ringOf != null;
    }

    /** Concentric ring index for a room — 0 = core, rising outward. -1 if this layout has no rings. */
    public int ringOf(int roomId) {
        return ringOf == null ? -1 : ringOf[roomId];
    }

    /** The central control-core room id, or -1 if this layout has no core. */
    public int coreRoom() {
        return coreRoom;
    }

    /**
     * Record the entry-port room ids — the diamond layout's cardinal landing
     * zones (the isolated outer-ring galleries). Published for a future
     * multi-spawn insertion that lands forces at several ports at once; a single
     * spawn uses just one today.
     */
    public void setPorts(int[] ports) {
        this.ports = ports;
    }

    /** Entry-port room ids (cardinal landing zones), or an empty array for layouts without ports. */
    public int[] ports() {
        return ports;
    }

    // ----- Topological roles (populated by StationTopologyStage) -----

    /**
     * Install the derived topological roles. Called once by
     * {@link com.dillon.starsectormarines.battle.world.gen.bsp.stage.StationTopologyStage}
     * after spawns are known. Arrays are stored by reference (the stage hands off
     * ownership); {@code depthFromEntry}/{@code articulation}/{@code onLoop} are
     * indexed by room id, {@code bridgeCorridor} by {@link #corridors()} index.
     */
    public void applyRoles(int entryRoom, int[] depthFromEntry, boolean[] articulation,
                           boolean[] onLoop, boolean[] bridgeCorridor) {
        this.entryRoom = entryRoom;
        this.depthFromEntry = depthFromEntry;
        this.articulation = articulation;
        this.onLoop = onLoop;
        this.bridgeCorridor = bridgeCorridor;
    }

    /** True once {@link #applyRoles} has run — guards the role accessors below. */
    public boolean hasRoles() {
        return depthFromEntry != null;
    }

    /** The attacker-entry room (marine spawn) BFS depth is measured from. -1 until roles are applied. */
    public int entryRoom() {
        return entryRoom;
    }

    /** Corridor hops from the entry room — the indoor assault gradient. -1 if unreachable (or roles unapplied). */
    public int depthFromEntry(int roomId) {
        return depthFromEntry[roomId];
    }

    /** Must-pass room: removing it (and its corridors) disconnects the graph. The defender's natural fortify point. */
    public boolean isArticulation(int roomId) {
        return articulation[roomId];
    }

    /** True iff the room touches a cycle (has ≥1 non-bridge corridor) — on a flank/loop rather than purely on the spine. */
    public boolean isOnLoop(int roomId) {
        return onLoop[roomId];
    }

    /** Bridge corridor (by {@link #corridors()} index): the sole link to a subtree — on-spine, no alternate route. */
    public boolean isBridge(int corridorIndex) {
        return bridgeCorridor[corridorIndex];
    }
}
