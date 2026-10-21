package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Baked, compartment-local light pools. No simulation RNG, navigation or combat lighting ownership. */
final class ShipDeckLighting {
    static final int SAMPLES = 8;
    record Lamp(float x, float y, boolean vertical, boolean emergency, boolean faulty, float phase) {}
    record Cell(int x, int y, boolean emergency, int[] lamps, float[] pools) {}
    private record Mount(int x, int y, boolean wall) {}
    private final List<Mount> mounts = new ArrayList<>();
    /** Fixtures with no supply at all: drawn as dark housings, lighting nothing. */
    private final Set<Integer> deadLamps = new HashSet<>();
    private final CellTopology topology;
    final List<Lamp> lamps = new ArrayList<>();
    final Cell[] cells;
    final int width, height;

    ShipDeckLighting(CellTopology topology, DeckGraph graph, long seed) {
        this(topology, graph, seed, true);
    }

    /**
     * @param passagePower whether the passages' own circuit is live. Without it
     *     (the Power core at Deficit) passage fixtures fall back to the same short
     *     emergency pools as a faulty room, and one in three is dead outright.
     */
    ShipDeckLighting(CellTopology topology, DeckGraph graph, long seed, boolean passagePower) {
        this.topology = topology;
        width = topology.getWidth(); height = topology.getHeight();
        cells = new Cell[width * height];
        int[] owners = new int[cells.length];
        Arrays.fill(owners, -1);
        Map<Integer, List<Integer>> circuits = new HashMap<>();
        if (graph != null) for (var room : graph.compartments()) {
            for (int y = room.top(); y <= room.bottom(); y++) for (int x = room.left(); x <= room.right(); x++)
                if (room.contains(x, y)) owners[y * width + x] = room.id();
            boolean emergency = graph.emergencyLighting(room.id());
            int number = 0;
            for (int x = room.left() + 1; x <= room.right(); x += 6) {
                for (int y : new int[]{room.top(), room.bottom()}) {
                    if (!room.contains(x, y)) continue;
                    float ly = y == room.top() ? y + .15f : y + .85f;
                    add(circuits, room.id(), x + .5f, ly, false, emergency,
                            emergency && number++ % 2 == 0, seed, new Mount(x, y, false));
                }
            }
        }
        // Passage fixtures sit against actual walls; their circuit never illuminates through a room wall.
        boolean dark = !passagePower;
        int passageLamp = 0;
        for (int y = 1; y < height - 1; y++) for (int x = 1; x < width - 1; x++) {
            if (owners[y * width + x] != -1 || !floor(topology, x, y) || (x + y) % 7 != 0) continue;
            boolean faulty = dark && passageLamp % 2 == 0;
            if (topology.isWall(x, y + 1)) {
                add(circuits, -1, x + .5f, y + .85f, false, dark, faulty, seed, new Mount(x, y + 1, true));
                if (dark && passageLamp % 3 == 1) deadLamps.add(lamps.size() - 1);
                passageLamp++;
            } else if (topology.isWall(x - 1, y)) {
                add(circuits, -1, x + .15f, y + .5f, true, dark, faulty, seed, new Mount(x - 1, y, true));
                if (dark && passageLamp % 3 == 1) deadLamps.add(lamps.size() - 1);
                passageLamp++;
            }
        }
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (!floor(topology, x, y)) continue;
            int owner = owners[y * width + x];
            var sources = circuits.getOrDefault(owner, List.of());
            int[] selected = new int[SAMPLES * SAMPLES * 2];
            float[] pool = new float[selected.length];
            Arrays.fill(selected, -1);
            for (int sy = 0; sy < SAMPLES; sy++) for (int sx = 0; sx < SAMPLES; sx++) {
                int index = (sy * SAMPLES + sx) * 2;
                float px = x + (sx + .5f) / SAMPLES, py = y + (sy + .5f) / SAMPLES;
                float nearest = Float.MAX_VALUE, second = Float.MAX_VALUE;
                for (int id : sources) {
                    Lamp light = lamps.get(id);
                    float dx = px - light.x(), dy = py - light.y(), d2 = dx * dx + dy * dy;
                    if (d2 < nearest) {
                        second = nearest; selected[index + 1] = selected[index];
                        nearest = d2; selected[index] = id;
                    } else if (d2 < second) { second = d2; selected[index + 1] = id; }
                }
                // Emergency lamps throw short pools; healthy circuits spread usable light farther.
                float spread = owner < 0 ? (dark ? 9f : 18f)
                        : graph != null && graph.emergencyLighting(owner) ? 9f : 18f;
                float a = (float) Math.exp(-nearest / spread), b = (float) Math.exp(-second / spread);
                // Overlapping fixtures soften local faults instead of exposing a nearest-lamp boundary.
                if (a + b > 0f) { pool[index] = a * a / (a + b); pool[index + 1] = a * b / (a + b); }
            }
            boolean emergency = owner < 0 ? dark : graph != null && graph.emergencyLighting(owner);
            cells[y * width + x] = new Cell(x, y, emergency, selected, pool);
        }
    }

    private void add(Map<Integer, List<Integer>> circuits, int owner, float x, float y,
                     boolean vertical, boolean emergency, boolean faulty, long seed, Mount mount) {
        int id = lamps.size();
        // Stable circuit phase, independent of camera, frame rate, and the generation RNG stream.
        float phase = Math.floorMod(seed + owner * 31L + id * 17L, 110) / 10f;
        lamps.add(new Lamp(x, y, vertical, emergency, faulty, phase));
        mounts.add(mount);
        circuits.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(id);
    }

    private static boolean floor(CellTopology topology, int x, int y) {
        return !topology.isWall(x, y) && topology.getGroundKind(x, y) != GroundKind.VOID;
    }

    /** The circuit may survive, but a fixture cannot hang from demolished structure. */
    boolean present(int index) {
        Mount mount = mounts.get(index);
        return mount.wall() ? topology.isWall(mount.x(), mount.y())
                : !topology.isRoofDestroyed(mount.x(), mount.y())
                    && topology.getGroundKind(mount.x(), mount.y()) != GroundKind.VOID;
    }

    float gain(int index, float seconds) {
        return present(index) && !deadLamps.contains(index) ? gain(lamps.get(index), seconds) : 0f;
    }

    static float gain(Lamp lamp, float seconds) {
        if (!lamp.faulty()) return 1f;
        float phase = (Math.max(0f, seconds) + lamp.phase()) % 11f;
        if (phase < .18f) return .2f;
        if (phase < .36f) return .85f;
        if (phase < .58f) return .35f;
        if (phase < 1.4f) return .35f + .65f * (phase - .58f) / .82f;
        return 1f;
    }

    static float illumination(Cell cell, int sample, float[] gains) {
        int i = sample * 2;
        float pool = 0f;
        for (int j = i; j <= i + 1; j++)
            if (cell.lamps()[j] >= 0) pool += cell.pools()[j] * gains[cell.lamps()[j]];
        return pool;
    }

    static float shade(boolean emergency, float pool) {
        // Dark gaps retain a little shape; a live fixture can still illuminate near full brightness.
        return emergency ? .92f - .86f * pool : .62f - .57f * pool;
    }
}
