package com.dillon.starsectormarines.battle.decision.goap.world;

import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Arrays;

/**
 * How many units of each faction stood in each zone when the tick's unit update began.
 *
 * <p>Garrison squads ask "is this room still free of enemies?" for every member, every tick, and
 * nearly every answer is yes — which the live answer reaches only by walking the whole roster. This
 * table answers the common case from one pass: a zone with no hostile unit in it is clear, and
 * anything else is left to the live scan, so an <em>enemy here</em> answer is always exact.
 *
 * <p>The only inexactness is that a "clear" answer reflects where units stood when the table was
 * built, not where they stand at the moment of the call: an enemy who steps into a room during the
 * unit update is not seen by a unit processed later in that same update, only by the next tick.
 *
 * <p>The table is trusted only while it is true to its moment, so it can never answer for a state it
 * did not see. It answers for one sim tick and one revision of one zone graph, and
 * {@link #close()} ends it once units have moved. Past that, or when a wall breach has merged
 * zones, it declines and the caller scans.
 *
 * <p>Built and closed on the simulation thread, read by the unit-update workers while no one writes.
 */
public final class ZoneOccupancy {

    private static final Faction[] FACTIONS = Faction.values();

    private final int[][] counts = new int[FACTIONS.length][0];
    private ZoneGraph graph;
    private int graphRevision;
    /** The sim tick this table is valid for, or {@code -1} while it is being built or closed. */
    private volatile int validTick = -1;

    /** Starts a table for {@code graph} as it stands now, clearing the last one. Serial. */
    public void begin(ZoneGraph graph) {
        validTick = -1;
        int zoneSlots = graph.getZones().size();
        for (int f = 0; f < counts.length; f++) {
            if (counts[f].length < zoneSlots) {
                counts[f] = new int[Math.max(zoneSlots, counts[f].length * 2)];
            } else {
                Arrays.fill(counts[f], 0, zoneSlots, 0);
            }
        }
        this.graph = graph;
        this.graphRevision = graph.revision();
    }

    /** Counts one unit of {@code faction} in {@code zoneId}; a cell in no zone is ignored. */
    public void add(Faction faction, int zoneId) {
        int[] byZone = counts[faction.ordinal()];
        if (zoneId >= 0 && zoneId < byZone.length) byZone[zoneId]++;
    }

    /** Makes the table answer for {@code tick}. Serial, after every {@link #add}. */
    public void publish(int tick) {
        validTick = tick;
    }

    /** Stops the table answering, because units are about to move. Serial. */
    public void close() {
        validTick = -1;
    }

    /**
     * True only when this table is valid for {@code tick} and {@code currentGraph}, and holds no unit
     * of a faction hostile to {@code self} in {@code zoneId}. False means <em>not known to be clear</em>:
     * a hostile is counted there, or the table cannot speak to this moment. The caller must scan.
     */
    public boolean noHostileIn(ZoneGraph currentGraph, int tick, int zoneId, Faction self) {
        if (validTick != tick || currentGraph != graph || currentGraph.revision() != graphRevision) {
            return false;
        }
        for (Faction other : FACTIONS) {
            if (!self.hostileTo(other)) continue;
            int[] byZone = counts[other.ordinal()];
            if (zoneId < 0 || zoneId >= byZone.length) return false;
            if (byZone[zoneId] > 0) return false;
        }
        return true;
    }
}
