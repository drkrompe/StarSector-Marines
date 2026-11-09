package com.dillon.starsectormarines.battle.derelict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A derelict site's security zones and the relays between them (`derelict-lore-guide.md`, Tactics and command). A zone
 * that wakes relays its alarm one hop to its linked neighbours, which go alert where they stand; the relay needs a live
 * node at both ends. A vault breach is the {@linkplain #contingency contingency}: every zone with a live node wakes,
 * and stays in it for the rest of the battle.
 */
public final class SecurityNetwork {

    private final Map<Integer, SecurityZone> zones = new TreeMap<>();
    private final Map<Integer, Set<Integer>> links = new TreeMap<>();
    private boolean contingency;

    public SecurityNetwork add(SecurityZone zone) {
        if (zones.putIfAbsent(zone.id, zone) != null) throw new IllegalArgumentException("Duplicate zone " + zone.id);
        links.put(zone.id, new TreeSet<>());
        return this;
    }

    /** Links two zones by a relay, both ways. */
    public SecurityNetwork link(int a, int b) {
        if (a == b || !zones.containsKey(a) || !zones.containsKey(b)) throw new IllegalArgumentException("Bad link");
        links.get(a).add(b);
        links.get(b).add(a);
        return this;
    }

    public SecurityZone zone(int id) {
        return zones.get(id);
    }

    public List<SecurityZone> zones() {
        return Collections.unmodifiableList(new ArrayList<>(zones.values()));
    }

    public Set<Integer> linked(int id) {
        return Collections.unmodifiableSet(links.getOrDefault(id, Set.of()));
    }

    public boolean contingency() {
        return contingency;
    }

    /** The zone keeping a cell, the nearest node first where perimeters overlap; null outside every perimeter. */
    public SecurityZone zoneAt(int cellX, int cellY) {
        SecurityZone best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (SecurityZone zone : zones.values()) {
            if (!zone.inside(cellX, cellY)) continue;
            int distance = Math.max(Math.abs(cellX - zone.nodeCellX), Math.abs(cellY - zone.nodeCellY));
            if (distance < bestDistance) {
                best = zone;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A zone stirs (contact, gunfire, damage); if that woke it and its node stands, its linked zones hear the relay. */
    public void stir(int zoneId, int tick) {
        SecurityZone zone = zones.get(zoneId);
        if (zone == null) return;
        if (zone.stir(tick) && zone.online()) relayFrom(zone);
    }

    /** Gunfire at a cell: every zone whose perimeter holds it stirs. */
    public void gunfire(int cellX, int cellY, int tick) {
        for (SecurityZone zone : zones.values()) {
            if (zone.inside(cellX, cellY)) stir(zone.id, tick);
        }
    }

    /**
     * A loud work at a cell (a ruins site's power hall restarting, `ruins-works.md`): the zone keeping the cell stirs,
     * or, outside every perimeter, the zone whose node is nearest; its linked zones hear the relay as from any stir.
     */
    public void loud(int cellX, int cellY, int tick) {
        SecurityZone zone = zoneAt(cellX, cellY);
        if (zone == null) {
            int best = Integer.MAX_VALUE;
            for (SecurityZone z : zones.values()) {
                int distance = Math.max(Math.abs(cellX - z.nodeCellX), Math.abs(cellY - z.nodeCellY));
                if (distance < best) {
                    best = distance;
                    zone = z;
                }
            }
        }
        if (zone != null) stir(zone.id, tick);
    }

    /** The vault is breached: every zone with a live node wakes, now and from here on. */
    public void breachVault(int tick) {
        contingency = true;
        for (SecurityZone zone : zones.values()) zone.call(tick);
    }

    /** Settles quiet zones; zones in the contingency stay awake while their node stands. */
    public void settle(int tick) {
        for (SecurityZone zone : zones.values()) {
            if (contingency && zone.online()) zone.stir(tick);
            else zone.settle(tick);
        }
    }

    private void relayFrom(SecurityZone zone) {
        for (int neighbour : links.get(zone.id)) zones.get(neighbour).hearRelay();
    }
}
