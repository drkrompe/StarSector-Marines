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
 *       whatever {@code put}s it; ordering is the owning recipe's job.</li>
 * </ul>
 *
 * <p>Not thread-safe and not meant to be — one generation run is single
 * threaded, and a generator is invoked with a fresh context for each run. See
 * {@code mapgen-nouns.md}.
 */
public final class GenContext {

    // --- spine: universal, always present ---

    public final NavigationGrid grid;
    public final CellTopology topology;
    public final Random rng;
    public final int width;
    public final int height;
    /**
     * The raw generation seed. {@code rng} is already derived from it; this is
     * kept separately for passes that seed their own deterministic sub-RNG off
     * the same value (e.g. the building flood-fill's per-building variation)
     * rather than drawing from the shared {@code rng} stream.
     */
    public final long seed;

    /** Landmark buildings emitted by fillers / stampers. */
    public final List<PointOfInterest> pois = new ArrayList<>();
    /** Decorative tiles placed by fillers / stampers. */
    public final List<Doodad> doodads = new ArrayList<>();
    /** Authored shuttle berths emitted by landing-zone / spaceport fillers. */
    public final List<LandingPad> landingPads = new ArrayList<>();
    /** Pair-capable mission arrival areas emitted by map-family stages. */
    public final List<LandingArea> landingAreas = new ArrayList<>();
    /** Authored airstrips, ordered deterministically by the lots that laid them. Empty on a map with no strip. */
    public final List<Runway> runways = new ArrayList<>();
    /**
     * Aircraft shelters — the bay inside a hangar where an aircraft is kept and
     * worked on.
     *
     * <p>A machine berth in the {@link Gantry} sense, kept apart from
     * {@link #gantries} because the thing standing in one taxis out under its
     * own power rather than being driven off by a crew. Empty on a map with no
     * airbase.
     */
    public final List<Gantry> shelters = new ArrayList<>();

    /** Authored machine berths inside vehicle bays, in the order fittings emit them. */
    public final List<Gantry> gantries = new ArrayList<>();
    /**
     * Authored work points inside fitted compartments, in the order fittings
     * emit them. What makes a generated room somewhere people have business
     * rather than somewhere they merely fit.
     */
    public final List<FixtureTask> fixtureTasks = new ArrayList<>();
    /** AI garrison anchors emitted by compound fillers + stampers; linked once at the end. */
    public final List<TacticalNode> tactical = new ArrayList<>();
    /**
     * Manned turret emplacements stamped by the defense-post pass. Always
     * allocated (empty for non-conquest runs) so it can flow straight into
     * {@link MapResult} — kept on the spine for parity with the other output
     * accumulators rather than behind a key.
     */
    public final List<DefensePost> defensePosts = new ArrayList<>();

    /**
     * Cells a facility has engineered, which terrain passes must not repaint.
     *
     * <p>A biome is the ground a place is built on, not the ground a place is
     * made of. The beach override repaints outdoor ground as sand so a shore
     * reads as one continuous strand — correct for a road, a park, a yard — and
     * it took an airbase apron with it, leaving berth markings eaten away in
     * ragged patches and a fenced lot floored in beach. The shoreline pass
     * already keeps a road reservation dry for the same reason; this is that
     * exemption generalised, so anything that lays a made surface can claim it.
     *
     * <p>Only the surface is claimed. What may still be built, walked, or
     * fought over on these cells is nobody's business here.
     */
    private final boolean[] madeGround;

    // --- blackboard: optional / domain overlays ---

    private final Map<GenKey<?>, Object> store = new HashMap<>();

    public GenContext(NavigationGrid grid, CellTopology topology, Random rng,
                      int width, int height, long seed) {
        this.grid = grid;
        this.topology = topology;
        this.rng = rng;
        this.width = width;
        this.height = height;
        this.seed = seed;
        this.madeGround = new boolean[Math.max(0, width * height)];
    }

    /** Claim {@code (x, y)} as an engineered surface no terrain pass may repaint. */
    public void markMadeGround(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        madeGround[y * width + x] = true;
    }

    /** Whether a facility has claimed {@code (x, y)} as an engineered surface. */
    public boolean isMadeGround(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return false;
        return madeGround[y * width + x];
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
