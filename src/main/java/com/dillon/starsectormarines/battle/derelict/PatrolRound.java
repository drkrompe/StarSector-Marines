package com.dillon.starsectormarines.battle.derelict;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A zone's round (`derelict-watch.md`, Movement): the loop its rounds walker walks while the zone sleeps, through the
 * places the zone keeps, in order round its node. The walker goes to a place, waits there {@link #PAUSE_SECONDS}, and
 * goes on to the next; the loop never ends. A pure rule: the director walks the walker to the cell it answers.
 */
public final class PatrolRound {

    /** How long the walker stands at each place before it goes on. UNTUNED. */
    public static final float PAUSE_SECONDS = 6f;
    /** How near a place, in cells, counts as there. */
    public static final float ARRIVE_CELLS = 1.5f;

    private final List<int[]> places;
    private int index;
    private float waited;

    /**
     * @param places the places of the round, in the order walked (see {@link #order}); at least one
     */
    public PatrolRound(List<int[]> places) {
        if (places.isEmpty()) throw new IllegalArgumentException("A round has a place");
        this.places = List.copyOf(places);
    }

    /**
     * The places a zone keeps, as a round: those inside its perimeter, one per cell, ordered by bearing round its node
     * (from east, counter-clockwise) so the loop walks round the node rather than across it. Its node cell stands in
     * when none is inside.
     */
    public static List<int[]> order(SecurityZone zone, List<int[]> candidates) {
        List<int[]> inside = new ArrayList<>();
        for (int[] cell : candidates) {
            if (!zone.inside(cell[0], cell[1])) continue;
            boolean dup = false;
            for (int[] taken : inside) dup |= taken[0] == cell[0] && taken[1] == cell[1];
            if (!dup) inside.add(cell);
        }
        if (inside.isEmpty()) inside.add(new int[] {zone.nodeCellX, zone.nodeCellY});
        inside.sort(Comparator.comparingDouble((int[] c) -> bearing(zone, c)).thenComparingInt(c -> c[0])
                .thenComparingInt(c -> c[1]));
        return inside;
    }

    private static double bearing(SecurityZone zone, int[] cell) {
        double angle = Math.atan2(cell[1] - zone.nodeCellY, cell[0] - zone.nodeCellX);
        return angle < 0 ? angle + Math.PI * 2 : angle;
    }

    /** The places of the round, in order. */
    public List<int[]> places() {
        return places;
    }

    /** The place the walker is walking to or standing at. */
    public int[] current() {
        return places.get(index);
    }

    /**
     * The walker stands at {@code (x, y)} after {@code dt} seconds: at its place, it waits there; once it has waited
     * its pause, the round moves on to the next place.
     *
     * @return the place to walk to now
     */
    public int[] advance(float x, float y, float dt) {
        int[] at = places.get(index);
        float dx = at[0] + 0.5f - x;
        float dy = at[1] + 0.5f - y;
        if (Math.max(Math.abs(dx), Math.abs(dy)) <= ARRIVE_CELLS) {
            waited += dt;
            if (waited >= PAUSE_SECONDS) {
                waited = 0f;
                index = (index + 1) % places.size();
            }
        } else {
            waited = 0f;
        }
        return places.get(index);
    }
}
