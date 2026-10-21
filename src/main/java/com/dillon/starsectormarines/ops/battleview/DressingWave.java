package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.CoreDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * One core's Deficit look coming or going across a running deck: the change
 * from one {@link CoreDressing} to another played over a few seconds instead of
 * at once.
 *
 * <p>Each core that changes spreads from its own source: Power and Life support
 * from the engine room's plant, Compute from the bridge, Structure from the
 * middle of the hull outward. A place's turn comes by walking distance through
 * the deck from that source, so the wave runs down the passages rather than
 * through bulkheads, with a little seeded jitter so it never reads as a ruler.
 * Every place then fades from the old look to the new over {@link #fade}.
 * Lamps and screens stutter as they come on.
 *
 * <p>Presentation only and a pure function of the deck, its seed and the
 * scene's own clock. Once {@link #done} it is exactly the target condition:
 * every weight is then 1 and every place shows {@code to}.
 */
final class DressingWave {

    final CoreDressing from, to;
    final float start, seconds, fade;
    /** The deck's light in the old condition; the scene already holds the new. */
    final ShipDeckLighting fromLighting;
    private final int width, height;
    /** Per changing core (by {@link ShipCore#ordinal}), each cell's arrival after {@link #start}; null if it holds still. */
    private final float[][] arrival = new float[ShipCore.values().length][];

    DressingWave(CoreDressing from, CoreDressing to, float start, float seconds, ShipDeckLighting fromLighting,
                 CellTopology topology, DeckGraph graph, long seed) {
        if (!(seconds > 0f)) throw new IllegalArgumentException("a wave needs a duration");
        this.from = from; this.to = to; this.start = start; this.seconds = seconds;
        this.fade = Math.min(.6f, seconds * .25f);
        this.fromLighting = fromLighting;
        width = topology.getWidth(); height = topology.getHeight();
        for (ShipCore core : CoreDressing.DRESSED) {
            if (from.restored(core) == to.restored(core)) continue;
            arrival[core.ordinal()] = arrivals(topology, graph, core, seed);
        }
    }

    /** How far the whole change has come, 0 at its start and 1 once it is finished. */
    float progress(float now) {
        return clamp((now - start) / seconds);
    }

    boolean done(float now) {
        return now - start >= seconds;
    }

    /** Whether this core changes in this wave. */
    boolean moves(ShipCore core) {
        return arrival[core.ordinal()] != null;
    }

    /** 0 where this place still shows the old look of this core, 1 where it shows the new. */
    float weight(ShipCore core, float x, float y, float now) {
        float[] times = arrival[core.ordinal()];
        if (times == null || done(now)) return 1f;
        float t = clamp((now - start - times[cell(x, y)]) / fade);
        return t * t * (3f - 2f * t);
    }

    /**
     * As {@link #weight}, but a fixture switching on stutters through its fade
     * the way a lamp or a screen catches: dark, a flash, dark, then steady.
     */
    float flicker(ShipCore core, float x, float y, float now, int salt) {
        float w = weight(core, x, y, now);
        if (w <= 0f || w >= 1f) return w;
        long beat = (long) Math.floor((now - start) / .07f);
        long roll = ShipDeckDressing.hash(salt, (int) x, (int) y, (int) beat);
        return Math.floorMod(roll, 3) == 0 ? 0f : w;
    }

    /** How strongly this core's Deficit look shows at this place: 1 full Deficit, 0 clear. */
    float deficit(ShipCore core, float x, float y, float now) {
        float a = from.restored(core) ? 0f : 1f, b = to.restored(core) ? 0f : 1f;
        if (a == b) return b;
        float w = weight(core, x, y, now);
        return a + (b - a) * w;
    }

    private int cell(float x, float y) {
        int cx = Math.max(0, Math.min(width - 1, (int) Math.floor(x)));
        int cy = Math.max(0, Math.min(height - 1, (int) Math.floor(y)));
        return cy * width + cx;
    }

    private float[] arrivals(CellTopology topology, DeckGraph graph, ShipCore core, long seed) {
        int[] distance = new int[width * height];
        Arrays.fill(distance, -1);
        ArrayDeque<Integer> open = new ArrayDeque<>();
        DeckGraph.Compartment source = source(graph, core);
        if (source != null) {
            for (int y = source.top(); y <= source.bottom(); y++) for (int x = source.left(); x <= source.right(); x++)
                if (inside(x, y) && source.contains(x, y) && floor(topology, x, y)) seed(distance, open, x, y);
        }
        if (open.isEmpty()) {
            // No such room: the middle of the hull, outward. The nearest floor to the centre.
            int best = -1;
            float bestD = Float.MAX_VALUE;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                if (!floor(topology, x, y)) continue;
                float dx = x - width * .5f, dy = y - height * .5f, d = dx * dx + dy * dy;
                if (d < bestD) { bestD = d; best = y * width + x; }
            }
            if (best >= 0) seed(distance, open, best % width, best / width);
        }
        int max = 0;
        while (!open.isEmpty()) {
            int i = open.poll(), x = i % width, y = i / width;
            max = Math.max(max, distance[i]);
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] s : steps) {
                int nx = x + s[0], ny = y + s[1];
                if (!inside(nx, ny) || !floor(topology, nx, ny)) continue;
                int n = ny * width + nx;
                if (distance[n] >= 0) continue;
                distance[n] = distance[i] + 1;
                open.add(n);
            }
        }
        float span = seconds - fade;
        float jitter = Math.min(.25f, seconds * .06f);
        float[] times = new float[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = y * width + x;
            // Sealed or cut-off deck the wave cannot walk to comes last.
            float along = distance[i] < 0 ? 1f : max == 0 ? 0f : distance[i] / (float) max;
            long roll = ShipDeckDressing.hash(seed, x, y, 7_000 + core.ordinal());
            float shake = (Math.floorMod(roll, 1000) / 999f - .5f) * jitter;
            times[i] = Math.max(0f, Math.min(span, along * span + shake));
        }
        return times;
    }

    /** The room a core's wave starts from; the largest of its kind. */
    static DeckGraph.Compartment source(DeckGraph graph, ShipCore core) {
        RoomPurpose purpose = switch (core) {
            case POWER, LIFE_SUPPORT -> RoomPurpose.ENGINE_ROOM;
            case COMPUTE -> RoomPurpose.BRIDGE;
            default -> null;
        };
        if (graph == null || purpose == null) return null;
        DeckGraph.Compartment best = null;
        int area = -1;
        for (var room : graph.compartments()) {
            if (room.purpose() != purpose) continue;
            int a = (room.right() - room.left() + 1) * (room.bottom() - room.top() + 1);
            if (a > area) { area = a; best = room; }
        }
        return best;
    }

    private void seed(int[] distance, ArrayDeque<Integer> open, int x, int y) {
        int i = y * width + x;
        if (distance[i] >= 0) return;
        distance[i] = 0;
        open.add(i);
    }

    private boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private static boolean floor(CellTopology topology, int x, int y) {
        return !topology.isWall(x, y) && topology.getGroundKind(x, y) != GroundKind.VOID;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }
}
