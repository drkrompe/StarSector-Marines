package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.drone.Flock;
import com.dillon.starsectormarines.battle.drone.FlockTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The security network's flocks on the shipped flock model (`derelict-drone-flocks.md`): each zone's flock, launched
 * by {@code FlockSystem.launch} at its node, reported through its roster and {@link Flock#contacts()}, and tasked by
 * {@link Flock}'s own calls. An order that repeats the flock's standing task, point and target is not given again,
 * so a probe is not restarted every pulse.
 *
 * <p>A contact is reported at the cell and on the scan tick its members saw it ({@code contactCells()},
 * {@code contactTick()}), never where the unit has gone since.
 */
final class FlockSystemPort implements FlockCommandPort {

    /** Within this many cells of home a flock that is going home counts as docked. */
    static final float DOCKED_CELLS = 2f;

    private final Map<Integer, Flock> flocks = new TreeMap<>();
    private final Map<Integer, Integer> zoneOf = new TreeMap<>();
    private final Map<Integer, float[]> station = new TreeMap<>();

    void add(Flock flock, int zoneId) {
        flocks.put(flock.id(), flock);
        zoneOf.put(flock.id(), zoneId);
    }

    @Override
    public List<Report> reports() {
        List<Report> out = new ArrayList<>();
        for (Flock flock : flocks.values()) {
            if (flock.size() == 0) continue;
            List<Contact> seen = new ArrayList<>();
            long[] units = flock.contacts();
            int[] cells = flock.contactCells();
            for (int i = 0; i < units.length && 2 * i + 1 < cells.length; i++) {
                seen.add(new Contact(units[i], cells[2 * i], cells[2 * i + 1], flock.contactTick()));
            }
            boolean home = Math.max(Math.abs(flock.anchorX() - flock.homeX()),
                    Math.abs(flock.anchorY() - flock.homeY())) <= DOCKED_CELLS;
            long pinned = flock.task() == FlockTask.STRIKE && flock.target() != 0L ? flock.target() : -1L;
            out.add(new Report(flock.id(), zoneOf.get(flock.id()), (int) Math.floor(flock.anchorX()),
                    (int) Math.floor(flock.anchorY()), flock.size() / (float) Math.max(1, flock.strength()),
                    home && flock.task() == FlockTask.RETURN, seen, pinned));
        }
        return out;
    }

    @Override
    public List<Long> cradles(int zoneId) {
        List<Long> out = new ArrayList<>();
        for (Map.Entry<Integer, Flock> entry : flocks.entrySet()) {
            if (zoneOf.get(entry.getKey()) == zoneId && entry.getValue().cradle() != 0L) {
                out.add(entry.getValue().cradle());
            }
        }
        return out;
    }

    @Override
    public void nodeStands(int zoneId, boolean stands) {
        for (Map.Entry<Integer, Flock> entry : flocks.entrySet()) {
            if (zoneOf.get(entry.getKey()) == zoneId) entry.getValue().nodeStands(stands);
        }
    }

    @Override
    public void order(int flockId, FlockTask task, int anchorCellX, int anchorCellY, long targetUnitId) {
        Flock flock = flocks.get(flockId);
        if (flock == null) return;
        float x = anchorCellX + 0.5f;
        float y = anchorCellY + 0.5f;
        long target = targetUnitId < 0 ? 0L : targetUnitId;
        float[] last = station.get(flockId);
        if (flock.task() == task && flock.target() == target && last != null
                && Math.max(Math.abs(last[0] - x), Math.abs(last[1] - y)) < 2f) {
            return;
        }
        station.put(flockId, new float[] {x, y});
        switch (task) {
            case SCREEN -> flock.screen(x, y);
            case PROBE -> flock.probe(x, y);
            case STRIKE -> {
                if (target != 0L) flock.strike(target);
            }
            case PURSUE -> {
                if (target != 0L) flock.pursue(target);
            }
            case RETURN -> flock.returnHome();
        }
    }
}
