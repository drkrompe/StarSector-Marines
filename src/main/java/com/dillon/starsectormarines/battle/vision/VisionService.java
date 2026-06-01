package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;

import java.util.ArrayList;

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
 * <h3>Unit visibility</h3>
 * {@link #unitVisibility} is a {@code byte[]} indexed by dense unit slot.
 * After the cohort update, every non-contributor alive unit is swept: if its
 * cell is revealed → VISIBLE, otherwise FADING (if previously visible) or
 * HIDDEN. The renderer reads this array + {@link #fadeAlpha} to gate drawing.
 */
public final class VisionService {

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

    private byte[] unitVisibility;
    private float[] fadeAlpha;
    private int unitCapacity;

    private final FogCohort[] cohorts = new FogCohort[COHORT_COUNT];
    private int cohortCursor = 0;

    private boolean initialized = false;

    // Scratch buffer for shadowcast output — reused across all contributors
    // within a single tick. Sized to the largest possible footprint.
    private int[] shadowScratch;

    // Ephemeral vision sources (shuttles, strafing fighters) — not part of the
    // cohort system. Their footprint is fully recomputed each vision tick:
    // decrement old, shadowcast new, increment.
    private int[] ephemeralPrevCells = new int[0];
    private int ephemeralPrevCount = 0;
    private int ephemeralSourceCount = 0;
    private int[] ephSourceCellX = new int[8];
    private int[] ephSourceCellY = new int[8];
    private int[] ephSourceRange = new int[8];
    private float[] ephSourceAirR = new float[8];

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

        this.unitCapacity = unitCapacity;
        this.unitVisibility = new byte[unitCapacity];
        this.fadeAlpha = new float[unitCapacity];

        this.shadowScratch = new int[Shadowcast.maxCells(MAX_VISION_RANGE)];

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

    /** Direct access to the revealed array for the renderer's per-cell fog pass. */
    public boolean[] cellRevealedArray() { return cellRevealed; }

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
    public void addContributor(Unit u) {
        if (!initialized) return;

        FogCohort smallest = cohorts[0];
        for (int i = 1; i < COHORT_COUNT; i++) {
            if (cohorts[i].contributors.size() < smallest.contributors.size()) {
                smallest = cohorts[i];
            }
        }

        ContributorEntry entry = new ContributorEntry();
        entry.unitId = u.entityId;
        entry.lastCellX = u.getCellX();
        entry.lastCellY = u.getCellY();

        int range = Math.min(MAX_VISION_RANGE, (int) u.visionRange);
        int count = Shadowcast.castFrom(grid, entry.lastCellX, entry.lastCellY,
                range, u.airLosRadius, shadowScratch, 0);
        entry.previousCells = new int[count];
        System.arraycopy(shadowScratch, 0, entry.previousCells, 0, count);
        entry.previousCellCount = count;

        for (int i = 0; i < count; i++) {
            int idx = entry.previousCells[i];
            revealCount[idx]++;
            cellRevealed[idx] = true;
        }

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
     * and runs the building visibility pass.
     */
    public void tick(int simTickIndex, NavigationGrid grid, UnitRegistry registry) {
        if (simTickIndex % 3 != 0) return;

        if (initialized) {
            tickFogCohort(registry);
            tickEphemeralSources();
            sweepUnitVisibility(registry);
        }

        if (!buildings.isEmpty()) {
            BuildingVisibilityPass.update(buildings, registry, grid, visionState);
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
     * Clears the ephemeral source list. Call before re-pushing shuttle and
     * fighter positions each vision tick.
     */
    public void clearEphemeralSources() {
        ephemeralSourceCount = 0;
    }

    /**
     * Registers an ephemeral vision source (shuttle, strafing fighter) for the
     * current vision tick. The footprint is fully recomputed each tick — no
     * caching, no cohort assignment.
     */
    public void addEphemeralSource(int cellX, int cellY, int range, float airLosRadius) {
        if (!initialized) return;
        if (cellX < 0 || cellX >= gridWidth || cellY < 0 || cellY >= gridHeight) return;
        if (ephemeralSourceCount >= ephSourceCellX.length) {
            int newCap = ephSourceCellX.length * 2;
            ephSourceCellX = java.util.Arrays.copyOf(ephSourceCellX, newCap);
            ephSourceCellY = java.util.Arrays.copyOf(ephSourceCellY, newCap);
            ephSourceRange = java.util.Arrays.copyOf(ephSourceRange, newCap);
            ephSourceAirR  = java.util.Arrays.copyOf(ephSourceAirR, newCap);
        }
        ephSourceCellX[ephemeralSourceCount] = cellX;
        ephSourceCellY[ephemeralSourceCount] = cellY;
        ephSourceRange[ephemeralSourceCount] = Math.min(MAX_VISION_RANGE, range);
        ephSourceAirR[ephemeralSourceCount]  = airLosRadius;
        ephemeralSourceCount++;
    }

    public int gridWidth()  { return gridWidth; }
    public int gridHeight() { return gridHeight; }
    public boolean isInitialized() { return initialized; }

    // ---- internals ----

    private void tickEphemeralSources() {
        for (int i = 0; i < ephemeralPrevCount; i++) {
            int idx = ephemeralPrevCells[i];
            revealCount[idx]--;
            if (revealCount[idx] <= 0) {
                revealCount[idx] = 0;
                cellRevealed[idx] = false;
            }
        }

        int total = 0;
        for (int s = 0; s < ephemeralSourceCount; s++) {
            int count = Shadowcast.castFrom(grid,
                    ephSourceCellX[s], ephSourceCellY[s],
                    ephSourceRange[s], ephSourceAirR[s],
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
        ephemeralPrevCount = total;

        for (int i = 0; i < total; i++) {
            int idx = ephemeralPrevCells[i];
            revealCount[idx]++;
            cellRevealed[idx] = true;
        }
    }

    private void tickFogCohort(UnitRegistry registry) {
        FogCohort cohort = cohorts[cohortCursor % COHORT_COUNT];
        cohortCursor++;

        for (int i = cohort.contributors.size() - 1; i >= 0; i--) {
            ContributorEntry e = cohort.contributors.get(i);
            Unit u = registry.getOrNull(e.unitId);

            if (u == null || !u.isAlive()) {
                decrementFootprint(e);
                cohort.contributors.remove(i);
                continue;
            }

            int cx = u.getCellX();
            int cy = u.getCellY();
            if (cx == e.lastCellX && cy == e.lastCellY) continue;

            decrementFootprint(e);

            int range = Math.min(MAX_VISION_RANGE, (int) u.visionRange);
            int count = Shadowcast.castFrom(grid, cx, cy,
                    range, u.airLosRadius, shadowScratch, 0);

            if (count > e.previousCells.length) {
                e.previousCells = new int[count];
            }
            System.arraycopy(shadowScratch, 0, e.previousCells, 0, count);
            e.previousCellCount = count;
            e.lastCellX = cx;
            e.lastCellY = cy;

            for (int j = 0; j < count; j++) {
                int idx = e.previousCells[j];
                revealCount[idx]++;
                cellRevealed[idx] = true;
            }
        }
    }

    private void decrementFootprint(ContributorEntry e) {
        for (int j = 0; j < e.previousCellCount; j++) {
            int idx = e.previousCells[j];
            revealCount[idx]--;
            if (revealCount[idx] <= 0) {
                revealCount[idx] = 0;
                cellRevealed[idx] = false;
            }
        }
    }

    private void sweepUnitVisibility(UnitRegistry registry) {
        for (int i = 0, n = registry.liveCount(); i < n; i++) {
            Unit u = registry.get(i);
            ensureUnitCapacity(u.denseIdx + 1);

            if (visionState.isContributor(u.faction)) {
                unitVisibility[u.denseIdx] = VIS_VISIBLE;
                fadeAlpha[u.denseIdx] = 1f;
                continue;
            }

            boolean revealed = isCellRevealed(u.getCellX(), u.getCellY());
            byte prev = unitVisibility[u.denseIdx];

            if (revealed) {
                unitVisibility[u.denseIdx] = VIS_VISIBLE;
                fadeAlpha[u.denseIdx] = 1f;
            } else if (prev == VIS_VISIBLE) {
                unitVisibility[u.denseIdx] = VIS_FADING;
                // fadeAlpha stays at 1.0 — advanceFade will tick it down
            } else if (prev != VIS_FADING) {
                unitVisibility[u.denseIdx] = VIS_HIDDEN;
            }
        }
    }

    // ---- inner types ----

    private static final class FogCohort {
        final ArrayList<ContributorEntry> contributors = new ArrayList<>();
    }

    private static final class ContributorEntry {
        long unitId;
        int lastCellX;
        int lastCellY;
        int[] previousCells = new int[0];
        int previousCellCount = 0;
    }
}
