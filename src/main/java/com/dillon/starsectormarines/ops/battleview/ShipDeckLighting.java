package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Baked, compartment-local light for a ship's deck. No simulation RNG,
 * navigation or combat lighting ownership.
 *
 * <p>Three kinds of light, after the deck look-dev ({@code art-source/ship-deck-lookdev}):
 * <ul>
 *   <li>Room fixtures on the long walls: cool white and broad on a live circuit,
 *       short warm emergency pools on a faulted one.</li>
 *   <li>Strung lamps in the passages: small caged amber lamps along both walls,
 *       each throwing a warm pool that runs inboard from its wall. Without passage
 *       power two in three are dead and half the rest flicker.</li>
 *   <li>Screens: a console lights the deck in front of it, teal while the ship's
 *       processing runs and a faint amber standby when it does not.</li>
 * </ul>
 * Each floor sample keeps its three strongest lights and a static shade:
 * contact shadow at the foot of a bulkhead, and in the passages the shadow of
 * the overhead beams. Faults and flicker stay per-lamp gains, so the bake never
 * changes after construction.
 */
final class ShipDeckLighting {
    static final int SAMPLES = 8;
    static final int PER_SAMPLE = 3;

    enum Style {
        /** A room's wall fixture. */
        STRIP,
        /** A caged lamp strung along a passage wall. */
        STRUNG,
        /** A screen's own glow; it has no fixture. */
        SCREEN
    }

    /**
     * One light. {@code nx, ny} point from its wall into the deck it lights;
     * {@code r, g, b} is its colour at full strength, and {@code reach} how far
     * its pool runs in cells.
     */
    record Lamp(float x, float y, boolean vertical, boolean emergency, boolean faulty, float phase,
                Style style, float nx, float ny, float r, float g, float b, float reach) {
        Lamp(float x, float y, boolean vertical, boolean emergency, boolean faulty, float phase) {
            this(x, y, vertical, emergency, faulty, phase, Style.STRIP, 0f, 0f, 1f, 1f, 1f, emergency ? 3f : 4.2f);
        }
    }

    /**
     * One floor cell's bake: per sample, up to {@link #PER_SAMPLE} lamps and
     * their weights, and a static shade multiplier. {@code tint} is how strongly
     * the light's own colour shows on this deck.
     */
    record Cell(int x, int y, boolean emergency, boolean passage, int[] lamps, float[] pools, float[] shadow, float tint) {}

    /** A strung lamp's sprite placement, drawn whatever the lamp's state. */
    record Fixture(int lamp, Doodad lit, Doodad dead) {}

    private record Mount(int x, int y, boolean wall) {}

    private static final float[] WARM = {1f, .64f, .30f};
    private static final float[] COOL = {.80f, .88f, 1f};
    private static final float[] EMERGENCY = {1f, .55f, .22f};
    private static final float[] TEAL = {.25f, .85f, .80f};
    private static final float[] STANDBY = {.85f, .52f, .16f};
    // Strung lamps sit four cells apart on a wall; the opposite wall is staggered by two.
    private static final int STRUNG_PITCH = 4;

    private final List<Mount> mounts = new ArrayList<>();
    /** Fixtures with no supply at all: drawn as dark housings, lighting nothing. */
    private final Set<Integer> deadLamps = new HashSet<>();
    private final CellTopology topology;
    final List<Lamp> lamps = new ArrayList<>();
    final List<Fixture> fixtures = new ArrayList<>();
    /** Wall-foot cable runs along the passages; slack while the passages are dark. */
    final List<Doodad> cables = new ArrayList<>();
    final Cell[] cells;
    final int width, height;

    ShipDeckLighting(CellTopology topology, DeckGraph graph, long seed) {
        this(topology, graph, seed, true);
    }

    ShipDeckLighting(CellTopology topology, DeckGraph graph, long seed, boolean passagePower) {
        this(topology, graph, seed, passagePower, List.of(), true, registry());
    }

    /**
     * @param passagePower whether the ship's distribution is live (the Power core
     *     restored). Without it two strung lamps in three are dead and half the
     *     rest flicker on what is left, and rooms the restoration record lists
     *     with faulty circuits run on emergency strips. With it only damaged
     *     rooms do, so restoring Power relights rooms without a new deck.
     * @param screens the deck's screens and consoles, which light the floor in front of them
     * @param processing whether the ship's processing runs (Compute restored): teal screens, else amber standby
     */
    ShipDeckLighting(CellTopology topology, DeckGraph graph, long seed, boolean passagePower,
                     List<Doodad> screens, boolean processing, Function<String, DoodadDef> art) {
        this.topology = topology;
        width = topology.getWidth(); height = topology.getHeight();
        cells = new Cell[width * height];
        int[] owners = new int[cells.length];
        Arrays.fill(owners, -1);
        Map<Integer, List<Integer>> circuits = new HashMap<>();
        Set<Integer> emergencyRooms = new HashSet<>();
        if (graph != null) for (var room : graph.compartments()) {
            for (int y = room.top(); y <= room.bottom(); y++) for (int x = room.left(); x <= room.right(); x++)
                if (room.contains(x, y)) owners[y * width + x] = room.id();
            // A damaged room is dark whatever the plant does; an intact room's faulty circuit
            // is the Power core's own symptom, so it follows the passages' supply.
            boolean emergency = graph.damaged(room.id()) || !passagePower && graph.emergencyLighting(room.id());
            if (emergency) emergencyRooms.add(room.id());
            int number = 0;
            for (int x = room.left() + 1; x <= room.right(); x += 6) {
                for (int y : new int[]{room.top(), room.bottom()}) {
                    if (!room.contains(x, y)) continue;
                    boolean top = y == room.top();
                    float[] colour = emergency ? EMERGENCY : COOL;
                    add(circuits, room.id(), new Lamp(x + .5f, top ? y + .15f : y + .85f, false, emergency,
                            emergency && number++ % 2 == 0, phase(seed, room.id(), lamps.size()), Style.STRIP,
                            0f, top ? 1f : -1f, colour[0], colour[1], colour[2], emergency ? 3f : 4.2f),
                            new Mount(x, y, false));
                }
            }
        }
        strung(topology, graph, owners, circuits, seed, passagePower, art);
        for (Doodad screen : screens) {
            int cx = screen.cellX + screen.footprintCellsX / 2, cy = screen.cellY + screen.footprintCellsY / 2;
            if (cx < 0 || cy < 0 || cx >= width || cy >= height) continue;
            float[] colour = processing ? TEAL : STANDBY;
            float strength = processing ? .55f : .22f;
            add(circuits, owners[cy * width + cx], new Lamp(screen.cellX + screen.footprintCellsX * .5f,
                    screen.cellY + screen.footprintCellsY * .5f, false, false, false, 0f, Style.SCREEN, 0f, 0f,
                    colour[0] * strength, colour[1] * strength, colour[2] * strength, 1.6f),
                    new Mount(cx, cy, false));
        }
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (!floor(topology, x, y)) continue;
            int owner = owners[y * width + x];
            boolean passage = owner < 0;
            var sources = circuits.getOrDefault(owner, List.of());
            int[] selected = new int[SAMPLES * SAMPLES * PER_SAMPLE];
            float[] pool = new float[selected.length];
            float[] shadow = new float[SAMPLES * SAMPLES];
            Arrays.fill(selected, -1);
            for (int sy = 0; sy < SAMPLES; sy++) for (int sx = 0; sx < SAMPLES; sx++) {
                int sample = sy * SAMPLES + sx;
                float px = x + (sx + .5f) / SAMPLES, py = y + (sy + .5f) / SAMPLES;
                int base = sample * PER_SAMPLE;
                for (int id : sources) {
                    float w = weight(lamps.get(id), px, py);
                    if (w <= .002f) continue;
                    // Keep the strongest few, weakest last.
                    for (int k = 0; k < PER_SAMPLE; k++) {
                        if (selected[base + k] >= 0 && pool[base + k] >= w) continue;
                        for (int m = PER_SAMPLE - 1; m > k; m--) {
                            selected[base + m] = selected[base + m - 1]; pool[base + m] = pool[base + m - 1];
                        }
                        selected[base + k] = id; pool[base + k] = w;
                        break;
                    }
                }
                shadow[sample] = contact(topology, x, y, px, py) * (passage ? beams(px, py) : 1f);
            }
            boolean emergency = passage ? !passagePower : emergencyRooms.contains(owner);
            float tint = passage ? .24f : emergency ? .12f : .05f;
            cells[y * width + x] = new Cell(x, y, emergency, passage, selected, pool, shadow, tint);
        }
    }

    static Function<String, DoodadDef> registry() {
        TileRegistry registry = TileRegistry.installed();
        return id -> registry == null ? null : registry.doodad(id);
    }

    /**
     * Caged lamps along every straight passage wall, and the cable that feeds
     * them along the wall foot. A lamp's sprite is turned onto its wall; its
     * art has the wall at the top of the frame, and a deck draws cell y upward.
     */
    private void strung(CellTopology topology, DeckGraph graph, int[] owners, Map<Integer, List<Integer>> circuits,
                        long seed, boolean passagePower, Function<String, DoodadDef> art) {
        DoodadDef lit = art.apply("ship-hold.lamp"), dead = art.apply("ship-hold.lamp-dead");
        DoodadDef cable = art.apply(passagePower ? "ship-hold.cable" : "ship-hold.cable-slack");
        // North, east, south, west: the wall's offset from the floor cell.
        int[][] walls = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        int number = 0;
        for (int y = 1; y < height - 1; y++) for (int x = 1; x < width - 1; x++) {
            if (owners[y * width + x] != -1 || !floor(topology, x, y)) continue;
            for (int side = 0; side < 4; side++) {
                int wx = walls[side][0], wy = walls[side][1];
                if (!topology.isWall(x + wx, y + wy)) continue;
                int ax = wy != 0 ? 1 : 0, ay = wx != 0 ? 1 : 0;
                // A straight run: the wall continues past both ends and the floor beside this cell is passage.
                boolean straight = topology.isWall(x + wx + ax, y + wy + ay) && topology.isWall(x + wx - ax, y + wy - ay)
                        && floor(topology, x + ax, y + ay) && floor(topology, x - ax, y - ay);
                if (!straight) continue;
                // A deck draws cell y upward, so art with its wall at the frame's top faces +y unturned.
                int turns = side == 0 ? 0 : side == 1 ? 3 : side == 2 ? 2 : 1;
                if (cable != null) cables.add(new Doodad(x, y, cable, turns));
                int along = ax != 0 ? x : y;
                if (Math.floorMod(along + (side >= 2 ? STRUNG_PITCH / 2 : 0), STRUNG_PITCH) != 0) continue;
                boolean flicker = !passagePower && number % 2 == 0;
                int id = lamps.size();
                add(circuits, -1, new Lamp(x + .5f + wx * .38f, y + .5f + wy * .38f, ax == 0, !passagePower,
                        flicker, phase(seed, -1, id), Style.STRUNG, -wx, -wy, WARM[0], WARM[1], WARM[2], 2.4f),
                        new Mount(x + wx, y + wy, true));
                if (!passagePower && number % 3 != 0) deadLamps.add(id);
                if (lit != null && dead != null)
                    fixtures.add(new Fixture(id, new Doodad(x, y, lit, turns), new Doodad(x, y, dead, turns)));
                number++;
            }
        }
    }

    /** How strongly a lamp lights a point, before its gain. */
    static float weight(Lamp lamp, float px, float py) {
        float dx = px - lamp.x(), dy = py - lamp.y();
        if (lamp.style() == Style.STRUNG) {
            // A pool that runs inboard from the wall: narrow along it, long across it.
            float across = dx * lamp.nx() + dy * lamp.ny();
            // The lamp hangs .12 of a cell off its wall; nothing past the wall line is lit.
            if (across < -.12f) return 0f;
            float along = dx * lamp.ny() - dy * lamp.nx();
            float centre = across - 1.1f;
            return 1.15f * (float) Math.exp(-(along * along) / 1.9f - (centre * centre) / (lamp.reach() * 2f));
        }
        if (lamp.style() == Style.STRIP && (lamp.nx() != 0f || lamp.ny() != 0f)
                && dx * lamp.nx() + dy * lamp.ny() < -.15f) return 0f;
        float spread = lamp.reach() * lamp.reach();
        return (float) Math.exp(-(dx * dx + dy * dy) / spread);
    }

    /** Contact shadow: the deck darkens into the foot of any bulkhead beside it. */
    static float contact(CellTopology topology, int x, int y, float px, float py) {
        float nearest = 1f;
        if (topology.isWall(x - 1, y)) nearest = Math.min(nearest, px - x);
        if (topology.isWall(x + 1, y)) nearest = Math.min(nearest, x + 1 - px);
        if (topology.isWall(x, y - 1)) nearest = Math.min(nearest, py - y);
        if (topology.isWall(x, y + 1)) nearest = Math.min(nearest, y + 1 - py);
        float t = Math.max(0f, 1f - nearest / .5f);
        return 1f - .42f * t * t;
    }

    /**
     * The overhead's shadow on a passage floor: beams crossing every few cells,
     * fixed to the deck so they never drift with the camera.
     */
    static float beams(float px, float py) {
        float bx = band(px, 5f, .45f), by = band(py, 5f, .45f);
        return 1f - .14f * Math.max(bx, by);
    }

    private static float band(float v, float pitch, float width) {
        float f = Math.floorMod((int) Math.floor(v * 100f), (int) (pitch * 100f)) / 100f;
        float d = Math.min(f, pitch - f);
        float t = Math.max(0f, 1f - d / width);
        return t * t * (3f - 2f * t);
    }

    private static float phase(long seed, int owner, int id) {
        // Stable circuit phase, independent of camera, frame rate, and the generation RNG stream.
        return Math.floorMod(seed + owner * 31L + id * 17L, 110) / 10f;
    }

    private void add(Map<Integer, List<Integer>> circuits, int owner, Lamp lamp, Mount mount) {
        int id = lamps.size();
        lamps.add(lamp);
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

    boolean dead(int index) {
        return deadLamps.contains(index);
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

    /** How much light reaches a sample, ignoring colour. */
    static float illumination(Cell cell, int sample, float[] gains) {
        int i = sample * PER_SAMPLE;
        float pool = 0f;
        for (int j = i; j < i + PER_SAMPLE; j++)
            if (cell.lamps()[j] >= 0) pool += cell.pools()[j] * gains[cell.lamps()[j]];
        return pool;
    }

    /**
     * The colour of the light reaching a sample, into {@code rgb}; returns its
     * strength (the brightest channel), before the static shade.
     */
    float colour(Cell cell, int sample, float[] gains, float[] rgb) {
        rgb[0] = rgb[1] = rgb[2] = 0f;
        int i = sample * PER_SAMPLE;
        for (int j = i; j < i + PER_SAMPLE; j++) {
            int id = cell.lamps()[j];
            if (id < 0) continue;
            Lamp lamp = lamps.get(id);
            float w = cell.pools()[j] * gains[id];
            rgb[0] += w * lamp.r(); rgb[1] += w * lamp.g(); rgb[2] += w * lamp.b();
        }
        return Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
    }

    /**
     * A passage's own curve: it is lit only by its strung lamps, so a live one
     * keeps the floor readable between pools, and a dark one falls to deep
     * shadow broken by the lamps that still burn.
     */
    /**
     * As {@link #colour(Cell, int, float[], float[])}, with each lamp's colour
     * read from {@code colours} (three per lamp) instead of the lamp itself:
     * how a frame mid-way through a dressing wave mixes two conditions' light.
     */
    static float colour(Cell cell, int sample, float[] gains, float[] colours, float[] rgb) {
        rgb[0] = rgb[1] = rgb[2] = 0f;
        int i = sample * PER_SAMPLE;
        for (int j = i; j < i + PER_SAMPLE; j++) {
            int id = cell.lamps()[j];
            if (id < 0) continue;
            float w = cell.pools()[j] * gains[id];
            rgb[0] += w * colours[id * 3]; rgb[1] += w * colours[id * 3 + 1]; rgb[2] += w * colours[id * 3 + 2];
        }
        return Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
    }

    static float shade(Cell cell, float pool) {
        if (!cell.passage()) return shade(cell.emergency(), pool);
        return cell.emergency() ? .62f - .58f * pool : .46f - .44f * pool;
    }

    static float shade(boolean emergency, float pool) {
        // Dark gaps retain a little shape; a live fixture can still illuminate near full brightness.
        return emergency ? .92f - .86f * pool : .62f - .57f * pool;
    }
}
