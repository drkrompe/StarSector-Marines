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
 * query this graph (degree, neighbors, room centers) instead of re-deriving
 * topology from raw geometry. For this first slice the graph carries the
 * structure + the trivially-free {@link #degree(int)}; richer topological roles
 * (depth-from-entry, articulation points, on-spine vs on-loop) are a later layer
 * that sits on top of these vertices and edges.
 *
 * <p>Built incrementally by {@link
 * com.dillon.starsectormarines.battle.world.gen.bsp.stage.CorridorStage}
 * ({@link #addCorridor}) and then treated as read-only by consumers. Room id ==
 * index into {@link #rooms()}, assigned in BSP leaf order (deterministic).
 */
public final class StationGraph {

    /** One carved room. {@code id} indexes {@link #rooms()}; bounds are the inclusive leaf rect. */
    public static final class Room {
        public final int id;
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;
        public final int centerX;
        public final int centerY;

        public Room(int id, BlockLeaf leaf) {
            this.id = id;
            this.left = leaf.left;
            this.top = leaf.top;
            this.right = leaf.right;
            this.bottom = leaf.bottom;
            this.centerX = leaf.centerX();
            this.centerY = leaf.centerY();
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

    /** Neighbor room ids reachable by one corridor from {@code roomId}. */
    public List<Integer> neighbors(int roomId) {
        return adjacency.get(roomId);
    }

    /** Number of corridors meeting this room — 1 = dead-end, 2 = pass-through, ≥3 = hub. */
    public int degree(int roomId) {
        return adjacency.get(roomId).size();
    }
}
