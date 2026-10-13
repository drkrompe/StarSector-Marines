package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Baked, compartment-local light pools. No simulation RNG, navigation or combat lighting ownership. */
final class ShipDeckLighting {
    static final int SAMPLES = 8;
    record Lamp(float x, float y, boolean vertical, boolean emergency, boolean faulty, float phase) {}
    record Cell(int x, int y, boolean emergency, int[] lamps, float[] pools) {}
    final List<Lamp> lamps = new ArrayList<>();
    final Cell[] cells;
    final int width, height;

    ShipDeckLighting(CellTopology topology, DeckGraph graph, long seed) {
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
                            emergency && number++ % 2 == 0, seed);
                }
            }
        }
        // Passage fixtures sit against actual walls; their circuit never illuminates through a room wall.
        for (int y = 1; y < height - 1; y++) for (int x = 1; x < width - 1; x++) {
            if (owners[y * width + x] != -1 || !floor(topology, x, y) || (x + y) % 7 != 0) continue;
            if (topology.isWall(x, y + 1)) add(circuits, -1, x + .5f, y + .85f, false, false, false, seed);
            else if (topology.isWall(x - 1, y)) add(circuits, -1, x + .15f, y + .5f, true, false, false, seed);
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
                float spread = graph != null && owner >= 0 && graph.emergencyLighting(owner) ? 9f : 18f;
                float a = (float) Math.exp(-nearest / spread), b = (float) Math.exp(-second / spread);
                // Overlapping fixtures soften local faults instead of exposing a nearest-lamp boundary.
                if (a + b > 0f) { pool[index] = a * a / (a + b); pool[index + 1] = a * b / (a + b); }
            }
            cells[y * width + x] = new Cell(x, y, graph != null && owner >= 0 && graph.emergencyLighting(owner), selected, pool);
        }
    }

    private void add(Map<Integer, List<Integer>> circuits, int owner, float x, float y,
                     boolean vertical, boolean emergency, boolean faulty, long seed) {
        int id = lamps.size();
        // Stable circuit phase, independent of camera, frame rate, and the generation RNG stream.
        float phase = Math.floorMod(seed + owner * 31L + id * 17L, 110) / 10f;
        lamps.add(new Lamp(x, y, vertical, emergency, faulty, phase));
        circuits.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(id);
    }

    private static boolean floor(CellTopology topology, int x, int y) {
        return !topology.isWall(x, y) && topology.getGroundKind(x, y) != GroundKind.VOID;
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
