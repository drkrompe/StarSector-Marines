package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * The battle's held {@link VehicleClearance} masks, one {@link VehicleClearanceCache} per grid and footprint radius,
 * so a route question asked on every move order or pull-back reads the mask rather than eroding the whole map again.
 *
 * <p>Eroding the map is a footprint test at every cell, and asking it per question made it half of all simulation
 * time in The Burn, where allied armour is ordered and pulled back many times a minute. The mask is a pure
 * derivation of the grid's topology, which {@link VehicleClearanceCache} tracks exactly, so a held one answers the
 * same as a fresh one.
 *
 * <p>A grid is held by identity. The live grid keeps its identity for the whole battle; a planning view (a dozer's
 * breach view, a hover hull's water view) is a new grid each time the live map moves, so the slot that held the
 * previous view is reused for it. A handful of slots covers every grid a battle asks about at once.
 *
 * <p>The masks it hands out are never changed afterwards (a catch-up patches a copy), so a caller may keep one.
 * The store itself is the convoy tick's, on the serial game thread; it is synchronized only so a stray caller
 * elsewhere cannot corrupt it.
 */
public final class VehicleClearances {

    /** Slots kept per radius: the live grid, a blade view and a hover view, with one spare. */
    private static final int SLOTS_PER_RADIUS = 4;

    private final List<Slot> slots = new ArrayList<>();
    private long useClock;

    /** The mask a chassis of footprint {@code radiusCells} erodes on {@code grid} as it stands now. */
    public synchronized VehicleClearance of(NavigationGrid grid, int radiusCells) {
        int radius = Math.max(0, radiusCells);
        Slot held = null, oldest = null;
        int sameRadius = 0;
        for (Slot slot : slots) {
            if (slot.radius != radius) continue;
            sameRadius++;
            if (slot.grid == grid) {
                held = slot;
                break;
            }
            if (oldest == null || slot.lastUse < oldest.lastUse) oldest = slot;
        }
        if (held == null) {
            if (sameRadius < SLOTS_PER_RADIUS) {
                held = new Slot(radius);
                slots.add(held);
            } else {
                held = oldest;
            }
            held.grid = grid;
        }
        held.lastUse = ++useClock;
        return held.cache.clearance(grid, grid.topologyRevision());
    }

    /** The mask {@code type}'s footprint erodes on {@code grid}. */
    public VehicleClearance of(NavigationGrid grid, VehicleType type) {
        return of(grid, VehicleClearance.radiusForWidth(type.visualWidthCells));
    }

    private static final class Slot {
        final int radius;
        final VehicleClearanceCache cache;
        NavigationGrid grid;
        long lastUse;

        Slot(int radius) {
            this.radius = radius;
            this.cache = new VehicleClearanceCache(radius);
        }
    }
}
