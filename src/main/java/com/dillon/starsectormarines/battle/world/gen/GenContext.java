package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Shared mutable state threaded through one map-generation run — the
 * blackboard every filler, partition strategy, and stamper reads and
 * mutates. Replaces the long per-pass argument lists (the old
 * {@code fill(grid, topology, roadCells, roadReservation, pois, doodads,
 * tactical, …, rng)} signatures) with a single {@code ctx} hand-off.
 *
 * <p>Two tiers of state:
 * <ul>
 *   <li><b>Spine</b> — direct final fields that are universal to every map
 *       type and always present: the {@link NavigationGrid}, the
 *       {@link CellTopology}, the {@link Random} source, and the
 *       output accumulators ({@code pois}, {@code doodads}, {@code tactical},
 *       {@code defensePosts}). A generator allocates these once up front; a
 *       pass that produces none simply leaves its list empty.</li>
 *   <li><b>Blackboard</b> — optional / domain-specific overlays addressed by
 *       {@link GenKey}: biome map, road graph, compound list, etc. A pass
 *       that needs one reads it by key and is responsible for running after
 *       whatever {@code put}s it (ordering is the recipe's job once stages
 *       exist; today it's the imperative call order in the orchestrator).</li>
 * </ul>
 *
 * <p>Not thread-safe and not meant to be — one generation run is single
 * threaded, and a generator is invoked once per battle with a fresh context.
 */
public final class GenContext {

    // --- spine: universal, always present ---

    public final NavigationGrid grid;
    public final CellTopology topology;
    public final Random rng;
    public final int width;
    public final int height;

    /** Landmark buildings emitted by fillers / stampers. */
    public final List<PointOfInterest> pois = new ArrayList<>();
    /** Decorative tiles placed by fillers / stampers. */
    public final List<Doodad> doodads = new ArrayList<>();
    /** AI garrison anchors emitted by compound fillers + stampers; linked once at the end. */
    public final List<TacticalNode> tactical = new ArrayList<>();
    /**
     * Manned turret emplacements stamped by the defense-post pass. Always
     * allocated (empty for non-conquest runs) so it can flow straight into
     * {@link MapResult} — kept on the spine for parity with the other output
     * accumulators rather than behind a key.
     */
    public final List<DefensePost> defensePosts = new ArrayList<>();

    // --- blackboard: optional / domain overlays ---

    private final Map<GenKey<?>, Object> store = new HashMap<>();

    public GenContext(NavigationGrid grid, CellTopology topology, Random rng,
                      int width, int height) {
        this.grid = grid;
        this.topology = topology;
        this.rng = rng;
        this.width = width;
        this.height = height;
    }

    /** Bind {@code value} under {@code key}. Last write wins. */
    public <T> void put(GenKey<T> key, T value) {
        store.put(key, value);
    }

    /** Read the value bound to {@code key}, or {@code null} if unset. Typed by the key. */
    @SuppressWarnings("unchecked")
    public <T> T get(GenKey<T> key) {
        return (T) store.get(key);
    }

    /** True when {@code key} has been bound this run. */
    public boolean has(GenKey<?> key) {
        return store.containsKey(key);
    }
}
