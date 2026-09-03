package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.sim.VisionService;
import com.dillon.starsectormarines.battle.sim.World;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Owns the per-cell fog-of-war bitmap, per-unit visibility state, and the
 * {@link Buildings} registry + {@link BuildingVisibilityPass} from the
 * pre-fog era. Ticked in the VISION phase at ~10 Hz (every 3rd sim tick).
 *
 * <h3>Fog bitmap</h3>
 * A ref-counted {@code short[]} ({@link #revealCount}) sized to the grid.
 * Each player-faction contributor's shadowcast increments cells it can see;
 * re-computation decrements the old footprint and increments the new one.
 * {@link #cellRevealed} is the derived boolean view ({@code revealCount > 0}).
 *
 * <h3>Cohort dispatch</h3>
 * Contributors are round-robin'd across {@link #COHORT_COUNT} cohorts. Each
 * vision tick processes one cohort, so each contributor refreshes at
 * {@code 10 Hz / COHORT_COUNT}. Contributors that haven't moved since their
 * last shadowcast are skipped (footprint unchanged).
 *
 * <h3>Entity visibility</h3>
 * {@link #unitVisibility} is a {@code byte[]} indexed by dense unit slot.
 * After the cohort update, every non-contributor alive unit is swept: if its
 * cell is revealed → VISIBLE, otherwise FADING (if previously visible) or
 * HIDDEN. The renderer reads this array + {@link #fadeAlpha} to gate drawing.
 */
public final class FogOfWarService {

    public static final byte VIS_HIDDEN  = 0;
    public static final byte VIS_VISIBLE = 1;
    public static final byte VIS_FADING  = 2;

    private static final int COHORT_COUNT = 6;
    private static final int MAX_VISION_RANGE = 60;

    private Buildings buildings = Buildings.EMPTY;
    private final PlayerVisionState visionState = new PlayerVisionState();

    private NavigationGrid grid;
    private int gridWidth;
    private int gridHeight;

    private short[] revealCount;
    private boolean[] cellRevealed;
    private boolean[] clearAirRevealed;
    private boolean[] clearAirNext;
    private boolean clearAirMaskActive;

    /**
     * Where the picture changed, for a consumer that keeps one derived from it.
     *
     * <p>Presentation reads this and nothing else does: fog remains the one
     * authority on what the player can see, and this only says where that answer
     * moved. It is written at every seam below that flips a cell's revealed
     * state, and where the counterfactual clear-air mask moves, because the fog
     * picture reads both.
     */
    private final RevealChangeLog changes = new RevealChangeLog();

    private byte[] unitVisibility;
    private float[] fadeAlpha;
    private int unitCapacity;

    private final FogCohort[] cohorts = new FogCohort[COHORT_COUNT];
    private int cohortCursor = 0;
    private long lastOpacityRevision = -1L;

    private boolean initialized = false;

    // Scratch buffer for shadowcast output — reused across all contributors
    // within a single tick. Sized to the largest possible footprint.
    private int[] shadowScratch;

    // Temporary vision sources — not part of the cohort system. Their combined
    // footprint is fully recomputed each vision tick: decrement old,
    // shadowcast new, increment.
    private int[] ephemeralPrevCells = new int[0];
    private int ephemeralPrevCount = 0;

    /**
     * Sources projected by a render host each frame: shuttles, strafing
     * fighters, and the player's active recon pings.
     */
    private final TemporarySources projected = new TemporarySources();

    /**
     * Sources the simulation itself replaces each tick: a suit whose integral
     * system is running a sensor sweep. A separate channel from
     * {@link #projected} because the two are cleared by different owners at
     * different cadences — the host wipes its own set between frames, and a
     * sim-owned source pushed into that set would be wiped with it. Both feed
     * the one footprint rebuild below, so law 5 ("replaced as a set, never
     * accumulated") holds for each of them independently.
     */
    private final TemporarySources carried = new TemporarySources();

    public Buildings getBuildings() { return buildings; }
    public PlayerVisionState getVisionState() { return visionState; }

    public void setBuildings(Buildings buildings) {
        this.buildings = buildings != null ? buildings : Buildings.EMPTY;
    }

    /**
     * One-time setup after the grid is known. Called from
     * {@link com.dillon.starsectormarines.battle.sim.BattleSimulation} once the
     * map is generated. Must be called before the first {@link #tick}.
     */
    public void init(NavigationGrid grid, int unitCapacity) {
        this.grid = grid;
        this.gridWidth = grid.getWidth();
        this.gridHeight = grid.getHeight();
        int cells = gridWidth * gridHeight;

        this.revealCount = new short[cells];
        this.cellRevealed = new boolean[cells];
        this.clearAirRevealed = new boolean[cells];
        this.clearAirNext = new boolean[cells];
        this.changes.init(gridWidth, gridHeight);

        this.unitCapacity = unitCapacity;
        this.unitVisibility = new byte[unitCapacity];
        this.fadeAlpha = new float[unitCapacity];

        this.shadowScratch = new int[Shadowcast.maxCells(MAX_VISION_RANGE)];
        this.lastOpacityRevision = grid.opacityRevision();

        for (int i = 0; i < COHORT_COUNT; i++) {
            cohorts[i] = new FogCohort();
        }
        this.initialized = true;
    }

    /** Returns true if the cell at {@code (x, y)} is currently revealed to the player. */
    public boolean isCellRevealed(int x, int y) {
        if (!initialized) return true;
        if (x < 0 || x >= gridWidth || y < 0 || y >= gridHeight) return false;
        return cellRevealed[y * gridWidth + x];
    }

    /**
     * Presentation-only counterfactual: whether the current observation
     * sources would reveal this cell if transient smoke opacity were absent.
     * Structural walls and every ordinary sight limit remain in force.
     */
    public boolean wouldBeRevealedWithoutTransientOpacity(int x, int y) {
        if (!initialized) return true;
        if (x < 0 || x >= gridWidth || y < 0 || y >= gridHeight) return false;
        if (!clearAirMaskActive) return isCellRevealed(x, y);
        return clearAirRevealed[y * gridWidth + x];
    }

    /**
     * How many sources currently hold this cell open. The boolean view above is
     * what the renderer wants; this is the reference count itself, exposed
     * because the standing "removing one footprint must not conceal a cell
     * still revealed by another" law is a statement about this number and
     * nothing else can observe it. A temporary source that released more or
     * less than it took is invisible in the boolean array until a second source
     * happens to overlap it.
     */
    public int revealCountAt(int x, int y) {
        if (!initialized || x < 0 || x >= gridWidth || y < 0 || y >= gridHeight) return 0;
        return revealCount[y * gridWidth + x];
    }

    /** Direct access to the revealed array for the renderer's per-cell fog pass. */
    public boolean[] cellRevealedArray() { return cellRevealed; }

    /**
     * Where the player's picture has changed, as a sequence of bounding
     * rectangles a presentation consumer remembers its own place in.
     *
     * <p>Presentation only. Nothing about what the player can see is decided
     * here; this reports where the answer moved, so a consumer holding a picture
     * derived from it can redo the part that is stale instead of all of it.
     */
    public RevealChangeLog revealChanges() { return changes; }

    /** Visibility state for unit at the given dense index. */
    public byte getUnitVisibility(int denseIdx) {
        if (!initialized || denseIdx < 0 || denseIdx >= unitCapacity) return VIS_VISIBLE;
        return unitVisibility[denseIdx];
    }

    /** Fade alpha for a FADING unit (1.0 = fully visible, 0.0 = gone). */
    public float getFadeAlpha(int denseIdx) {
        if (!initialized || denseIdx < 0 || denseIdx >= unitCapacity) return 1f;
        return fadeAlpha[denseIdx];
    }

    /**
     * Register a contributor unit (player-faction) so it begins casting vision
     * on the fog bitmap. Assigned to the smallest cohort. Runs an immediate
     * shadowcast so the unit's surroundings reveal on the spawn frame.
     */
    public void addContributor(long u, UnitRosterService roster) {
        if (!initialized) return;

        World world = roster.world();
        FogCohort smallest = cohorts[0];
        for (int i = 1; i < COHORT_COUNT; i++) {
            if (cohorts[i].contributors.size() < smallest.contributors.size()) {
                smallest = cohorts[i];
            }
        }

        VisionService vision = roster.vision();
        ContributorEntry entry = new ContributorEntry();
        entry.unitId = u;
        entry.lastCellX = world.cellX(u);
        entry.lastCellY = world.cellY(u);

        int range = Math.min(MAX_VISION_RANGE, (int) vision.visionRange(u));
        entry.lastRange = range;
        entry.lastAirLosRadius = vision.airLosRadius(u);
        int count = Shadowcast.castFrom(grid, entry.lastCellX, entry.lastCellY,
                range, entry.lastAirLosRadius, shadowScratch, 0);
        entry.previousCells = new int[count];
        System.arraycopy(shadowScratch, 0, entry.previousCells, 0, count);
        entry.previousCellCount = count;

        for (int i = 0; i < count; i++) {
            reveal(entry.previousCells[i]);
        }
        changes.seal();

        smallest.contributors.add(entry);
    }

    /**
     * Remove a contributor and decrement its vision footprint. Called when a
     * contributor unit dies or is otherwise removed from the battle.
     */
    public void removeContributor(long entityId) {
        if (!initialized) return;
        for (FogCohort cohort : cohorts) {
            for (int i = cohort.contributors.size() - 1; i >= 0; i--) {
                ContributorEntry e = cohort.contributors.get(i);
                if (e.unitId == entityId) {
                    decrementFootprint(e);
                    changes.seal();
                    cohort.contributors.remove(i);
                    return;
                }
            }
        }
    }

    /**
     * Grow backing arrays if the unit registry expanded beyond current capacity.
     * Called before the visibility sweep.
     */
    public void ensureUnitCapacity(int needed) {
        if (needed <= unitCapacity) return;
        int newCap = Math.max(needed, unitCapacity * 2);
        byte[] newVis = new byte[newCap];
        float[] newFade = new float[newCap];
        System.arraycopy(unitVisibility, 0, newVis, 0, unitCapacity);
        System.arraycopy(fadeAlpha, 0, newFade, 0, unitCapacity);
        unitVisibility = newVis;
        fadeAlpha = newFade;
        unitCapacity = newCap;
    }

    /**
     * VISION-phase tick. Processes one fog cohort, updates unit visibility,
     * and runs the building visibility pass. Uses the {@code grid} captured in
     * {@link #init} — callers needn't re-pass it.
     */
    public void tick(int simTickIndex, UnitRosterService roster) {
        if (simTickIndex % 3 != 0) return;

        if (initialized) {
            long revision = grid.opacityRevision();
            if (revision != lastOpacityRevision) {
                for (FogCohort cohort : cohorts) tickFogCohort(cohort, roster, true);
                lastOpacityRevision = revision;
            } else {
                FogCohort cohort = cohorts[cohortCursor % COHORT_COUNT];
                cohortCursor++;
                tickFogCohort(cohort, roster, false);
            }
            tickEphemeralSources();
            rebuildClearAirReveal(roster);
            sweepUnitVisibility(roster);
            // Building roofs reveal off the same per-cell fog bitmap (post-cohort/
            // ephemeral, so it reflects this tick's vision) — see BuildingVisibilityPass.
            if (!buildings.isEmpty()) {
                BuildingVisibilityPass.update(buildings, cellRevealed, gridWidth, gridHeight);
            }
            // One seal for the whole update, so a consumer never reads a picture
            // half way through a cohort's recast.
            changes.seal();
        }
    }

    /**
     * Advance fade timers for FADING units. Called from the render loop on
     * real-time dt (not sim-scaled) so fades stay smooth during pause/speedup.
     */
    public void advanceFade(float realDt) {
        if (!initialized) return;
        float decay = realDt * 3.0f;
        for (int i = 0; i < unitCapacity; i++) {
            if (unitVisibility[i] == VIS_FADING) {
                fadeAlpha[i] -= decay;
                if (fadeAlpha[i] <= 0f) {
                    fadeAlpha[i] = 0f;
                    unitVisibility[i] = VIS_HIDDEN;
                }
            }
        }
    }

    /**
     * Clears the host-projected source list. Call before re-pushing shuttle,
     * fighter, and recon-ping positions each frame.
     */
    public void clearEphemeralSources() {
        projected.clear();
    }

    /**
     * Registers a host-projected vision source (shuttle, strafing fighter,
     * recon ping) for the current vision tick. The footprint is fully
     * recomputed each tick — no caching, no cohort assignment.
     */
    public void addEphemeralSource(int cellX, int cellY, int range, float airLosRadius) {
        add(projected, cellX, cellY, range, airLosRadius);
    }

    /**
     * Clears the simulation-owned sweep source list. Called by the sweep that
     * owns those sources at the start of every tick, so a source survives
     * exactly as long as the system projecting it is still running.
     */
    public void clearCarriedSweepSources() {
        carried.clear();
    }

    /**
     * Registers a carried sensor sweep as a temporary observer for this tick.
     *
     * <p>Deliberately the same seam every other temporary source uses: the
     * caller supplies a cell, a range, and a wall-read radius, and gets the
     * ordinary shadowcast and the ordinary reference count. A sweep is a client
     * of player reveal composition, never a second reveal path
     * ({@code fog-of-war-nouns.md}).
     */
    public void addCarriedSweepSource(int cellX, int cellY, int range, float wallReadRadius) {
        add(carried, cellX, cellY, range, wallReadRadius);
    }

    private void add(TemporarySources sources, int cellX, int cellY,
                     int range, float airLosRadius) {
        if (!initialized) return;
        if (cellX < 0 || cellX >= gridWidth || cellY < 0 || cellY >= gridHeight) return;
        sources.add(cellX, cellY, Math.min(MAX_VISION_RANGE, range), airLosRadius);
    }

    public int gridWidth()  { return gridWidth; }
    public int gridHeight() { return gridHeight; }
    public boolean isInitialized() { return initialized; }

    // ---- internals ----

    private void tickEphemeralSources() {
        for (int i = 0; i < ephemeralPrevCount; i++) {
            conceal(ephemeralPrevCells[i]);
        }

        int total = castInto(projected, 0);
        total = castInto(carried, total);
        ephemeralPrevCount = total;

        for (int i = 0; i < total; i++) {
            reveal(ephemeralPrevCells[i]);
        }
    }

    /**
     * Takes one reference on a cell, recording the flip if it is the first.
     *
     * <p>Every increment goes through here rather than setting the boolean
     * directly, because the change log must see a cell become revealed exactly
     * when it becomes revealed. A source arriving on a cell another source
     * already holds open changes nothing the player can see, and a log that
     * recorded it would send its reader over ground that has not moved.
     */
    private void reveal(int idx) {
        if (revealCount[idx]++ == 0) {
            cellRevealed[idx] = true;
            changes.note(idx);
        }
    }

    /** Gives one reference back, recording the flip if it was the last. */
    private void conceal(int idx) {
        revealCount[idx]--;
        if (revealCount[idx] <= 0) {
            revealCount[idx] = 0;
            if (cellRevealed[idx]) {
                cellRevealed[idx] = false;
                changes.note(idx);
            }
        }
    }

    private void tickFogCohort(FogCohort cohort, UnitRosterService roster,
                               boolean forceRecast) {
        World world = roster.world();
        VisionService vision = roster.vision();

        for (int i = cohort.contributors.size() - 1; i >= 0; i--) {
            ContributorEntry e = cohort.contributors.get(i);

            // No Entity materialization: a dead/released contributor has no HEALTH
            // (isAliveById false), and an alive one's sight stats are read by id off
            // the VISION component — the field migration drops the handle-resolution hop here.
            if (!roster.isAliveById(e.unitId)) {
                decrementFootprint(e);
                cohort.contributors.remove(i);
                continue;
            }
            // Somebody inside a vehicle sees nothing out of it and has no cell
            // to see from. Its footprint comes down and it stops contributing
            // until it is set down again — the same shape as a contributor that
            // died, except it comes back.
            if (roster.isRiding(e.unitId)) {
                decrementFootprint(e);
                cohort.contributors.remove(i);
                continue;
            }

            int cx = world.cellX(e.unitId);
            int cy = world.cellY(e.unitId);
            if (!forceRecast && cx == e.lastCellX && cy == e.lastCellY) continue;

            decrementFootprint(e);

            int range = Math.min(MAX_VISION_RANGE, (int) vision.visionRange(e.unitId));
            float airLosRadius = vision.airLosRadius(e.unitId);
            int count = Shadowcast.castFrom(grid, cx, cy,
                    range, airLosRadius, shadowScratch, 0);

            if (count > e.previousCells.length) {
                e.previousCells = new int[count];
            }
            System.arraycopy(shadowScratch, 0, e.previousCells, 0, count);
            e.previousCellCount = count;
            e.lastCellX = cx;
            e.lastCellY = cy;
            e.lastRange = range;
            e.lastAirLosRadius = airLosRadius;

            for (int j = 0; j < count; j++) {
                reveal(e.previousCells[j]);
            }
        }
    }

    /** Shadowcasts every source in one channel onto the end of the combined footprint. */
    private int castInto(TemporarySources sources, int total) {
        for (int s = 0; s < sources.count; s++) {
            int count = Shadowcast.castFrom(grid,
                    sources.cellX[s], sources.cellY[s],
                    sources.range[s], sources.airLosRadius[s],
                    shadowScratch, 0);
            int needed = total + count;
            if (needed > ephemeralPrevCells.length) {
                int newCap = Math.max(needed, ephemeralPrevCells.length * 2);
                int[] grow = new int[newCap];
                System.arraycopy(ephemeralPrevCells, 0, grow, 0, total);
                ephemeralPrevCells = grow;
            }
            System.arraycopy(shadowScratch, 0, ephemeralPrevCells, total, count);
            total += count;
        }
        return total;
    }

    private void decrementFootprint(ContributorEntry e) {
        for (int j = 0; j < e.previousCellCount; j++) {
            conceal(e.previousCells[j]);
        }
    }

    /**
     * Rebuilds the counterfactual union only while smoke exists. This is not a
     * second observation authority: it never drives roofs, units, radio, or
     * gameplay, and it uses the same cached contributor cadence plus the same
     * temporary-source sets as the real reveal bitmap.
     */
    private void rebuildClearAirReveal(UnitRosterService roster) {
        if (!grid.hasTransientOpacity()) {
            if (clearAirMaskActive) {
                Arrays.fill(clearAirNext, false);
                adoptClearAir();
            }
            clearAirMaskActive = false;
            return;
        }

        Arrays.fill(clearAirNext, false);
        clearAirMaskActive = true;
        for (FogCohort cohort : cohorts) {
            for (ContributorEntry entry : cohort.contributors) {
                if (!roster.isAliveById(entry.unitId)) continue;
                addClearAirFootprint(entry.lastCellX, entry.lastCellY,
                        entry.lastRange, entry.lastAirLosRadius);
            }
        }
        addClearAirFootprints(projected);
        addClearAirFootprints(carried);
        adoptClearAir();
    }

    /**
     * Swaps the freshly cast counterfactual in, recording where it differs.
     *
     * <p>The clear-air union is rebuilt whole rather than reference counted, so
     * the only way to know which cells moved is to compare — one pass over the
     * grid, and only while a cloud exists at all. The half-strength shadow it
     * drives is part of the fog picture, so a consumer that missed this would
     * hold a cell at full darkness after the smoke around it cleared.
     */
    private void adoptClearAir() {
        for (int i = 0; i < clearAirNext.length; i++) {
            if (clearAirNext[i] != clearAirRevealed[i]) changes.note(i);
        }
        boolean[] swap = clearAirRevealed;
        clearAirRevealed = clearAirNext;
        clearAirNext = swap;
    }

    private void addClearAirFootprints(TemporarySources sources) {
        for (int i = 0; i < sources.count; i++) {
            addClearAirFootprint(sources.cellX[i], sources.cellY[i],
                    sources.range[i], sources.airLosRadius[i]);
        }
    }

    private void addClearAirFootprint(int cellX, int cellY,
                                      int range, float airLosRadius) {
        int count = Shadowcast.castFromIgnoringTransientOpacity(
                grid, cellX, cellY, range, airLosRadius, shadowScratch, 0);
        for (int i = 0; i < count; i++) clearAirNext[shadowScratch[i]] = true;
    }

    private void sweepUnitVisibility(UnitRosterService roster) {
        World world = roster.world();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = roster.get(i);
            // i IS the dense index (roster.get(i) == dense[i], dense[i].denseIdx==i),
            // and the visibility/fade arrays are keyed by dense index — so i indexes
            // them directly, no u.denseIdx field read.
            ensureUnitCapacity(i + 1);

            if (visionState.isContributor(roster.identity().faction(u))) {
                unitVisibility[i] = VIS_VISIBLE;
                fadeAlpha[i] = 1f;
                continue;
            }

            boolean revealed = isCellRevealed(world.cellX(u), world.cellY(u));
            byte prev = unitVisibility[i];

            if (revealed) {
                unitVisibility[i] = VIS_VISIBLE;
                fadeAlpha[i] = 1f;
            } else if (prev == VIS_VISIBLE) {
                unitVisibility[i] = VIS_FADING;
                // fadeAlpha stays at 1.0 — advanceFade will tick it down
            } else if (prev != VIS_FADING) {
                unitVisibility[i] = VIS_HIDDEN;
            }
        }
    }

    // ---- inner types ----

    private static final class FogCohort {
        final ArrayList<ContributorEntry> contributors = new ArrayList<>();
    }

    /** One replaceable set of temporary observers, owned by whoever pushes it. */
    private static final class TemporarySources {
        int count = 0;
        int[] cellX = new int[8];
        int[] cellY = new int[8];
        int[] range = new int[8];
        float[] airLosRadius = new float[8];

        void clear() {
            count = 0;
        }

        void add(int x, int y, int cells, float airRadius) {
            if (count >= cellX.length) {
                int newCap = cellX.length * 2;
                cellX = Arrays.copyOf(cellX, newCap);
                cellY = Arrays.copyOf(cellY, newCap);
                range = Arrays.copyOf(range, newCap);
                airLosRadius = Arrays.copyOf(airLosRadius, newCap);
            }
            cellX[count] = x;
            cellY[count] = y;
            range[count] = cells;
            airLosRadius[count] = airRadius;
            count++;
        }
    }

    private static final class ContributorEntry {
        long unitId;
        int lastCellX;
        int lastCellY;
        int lastRange;
        float lastAirLosRadius;
        int[] previousCells = new int[0];
        int previousCellCount = 0;
    }
}
