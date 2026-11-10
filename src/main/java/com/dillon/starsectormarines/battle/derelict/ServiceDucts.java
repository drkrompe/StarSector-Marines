package com.dillon.starsectormarines.battle.derelict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The plant's service ducts (`derelict-watch.md`, Movement): the ways behind its walls the drones still use. A duct
 * opens at a {@link Vent}, a hatch in open-sky paving, so a flock in a duct never passes under a roof. A flock may go
 * into a vent of its own zone and come up at another vent of its zone or a linked zone after the duct's transit; in a
 * duct it is off the map.
 *
 * <p>The ducts are shut until the facility's wake opens them ({@link #open()}, {@link FacilityWake}). A vent whose zone
 * is offline (its node dead or its branch cut) is shut. A company charge over a vent collapses it for good.
 */
public final class ServiceDucts {

    /** A hatch into the ducts, on an open-sky cell, belonging to a zone. */
    public record Vent(int id, int cellX, int cellY, int zoneId) { }

    /** One way through the ducts: in at {@code entry}, out at {@code exit}, {@code seconds} inside. */
    public record Transit(Vent entry, Vent exit, float seconds) { }

    /** How fast a flock goes through a duct, cells a second. UNTUNED. */
    public static final float DUCT_CELLS_PER_SECOND = 6f;
    /** The shortest time a flock spends in a duct: docking in and coming up. UNTUNED. */
    public static final float MIN_TRANSIT_SECONDS = 4f;
    /** How much shorter, in cells, the way through a duct must be than the open way before a flock takes it. */
    public static final float SAVING_CELLS = 10f;
    /** How near a vent, in cells, a charge must go off to collapse it. */
    public static final float COLLAPSE_CELLS = 2f;

    private final List<Vent> vents;
    private final Set<Integer> collapsed = new TreeSet<>();
    private boolean open;

    public ServiceDucts(List<Vent> vents) {
        this.vents = List.copyOf(vents);
    }

    public List<Vent> vents() {
        return vents;
    }

    /** The facility's wake has brought the ducts up. */
    public void open() {
        open = true;
    }

    public boolean opened() {
        return open;
    }

    /** A charge going off at a cell collapses every vent within {@link #COLLAPSE_CELLS}; returns those it closed. */
    public List<Vent> charge(float x, float y) {
        List<Vent> out = new ArrayList<>();
        for (Vent vent : vents) {
            if (collapsed.contains(vent.id())) continue;
            float dx = vent.cellX() + 0.5f - x;
            float dy = vent.cellY() + 0.5f - y;
            if (dx * dx + dy * dy <= COLLAPSE_CELLS * COLLAPSE_CELLS) {
                collapsed.add(vent.id());
                out.add(vent);
            }
        }
        return out;
    }

    public boolean collapsed(int ventId) {
        return collapsed.contains(ventId);
    }

    public Set<Integer> collapsedVents() {
        return Collections.unmodifiableSet(collapsed);
    }

    /** Whether a flock may use a vent now: the ducts are up, it stands, and its zone is online. */
    public boolean usable(Vent vent, SecurityNetwork network) {
        SecurityZone zone = network.zone(vent.zoneId());
        return open && !collapsed.contains(vent.id()) && zone != null && zone.online();
    }

    /** Seconds a flock spends in the duct between two vents. */
    public static float transitSeconds(Vent a, Vent b) {
        return Math.max(MIN_TRANSIT_SECONDS, distance(a.cellX(), a.cellY(), b.cellX() + 0.5f, b.cellY() + 0.5f)
                / DUCT_CELLS_PER_SECOND);
    }

    /**
     * Whether a flock of zone {@code zoneId} at {@code (fromX, fromY)} going to {@code (toX, toY)} should take a duct,
     * and which: in at one of its own zone's usable vents, out at a usable vent of its zone or a linked one, where the
     * whole way (to the entry, the transit at the flock's speed, from the exit) is at least {@link #SAVING_CELLS}
     * shorter than its open way. The shortest such way wins.
     *
     * @param openWayCells the flock's way over open ground, cells; negative when it has none
     * @param flockSpeed   the flock's cruise, cells a second, which turns the transit into cells
     * @return the duct to take, or null to fly the open way
     */
    public Transit plan(SecurityNetwork network, int zoneId, float fromX, float fromY, int toX, int toY,
                        float openWayCells, float flockSpeed) {
        Set<Integer> reach = new TreeSet<>(network.linked(zoneId));
        reach.add(zoneId);
        Transit best = null;
        float bestCells = Float.MAX_VALUE;
        for (Vent entry : vents) {
            if (entry.zoneId() != zoneId || !usable(entry, network)) continue;
            float in = distance(entry.cellX(), entry.cellY(), fromX, fromY);
            for (Vent exit : vents) {
                if (exit.id() == entry.id() || !reach.contains(exit.zoneId()) || !usable(exit, network)) continue;
                float seconds = transitSeconds(entry, exit);
                float cells = in + seconds * flockSpeed + distance(exit.cellX(), exit.cellY(), toX + 0.5f, toY + 0.5f);
                if (cells < bestCells) {
                    bestCells = cells;
                    best = new Transit(entry, exit, seconds);
                }
            }
        }
        if (best == null) return null;
        if (openWayCells >= 0f && bestCells + SAVING_CELLS > openWayCells) return null;
        return best;
    }

    private static float distance(int cellX, int cellY, float x, float y) {
        float dx = cellX + 0.5f - x;
        float dy = cellY + 0.5f - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
