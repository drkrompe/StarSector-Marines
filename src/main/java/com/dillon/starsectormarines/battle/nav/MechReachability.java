package com.dillon.starsectormarines.battle.nav;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The battle's long memory of where its mechs can go: the {@link ReachableRegion}s its refused route proofs flooded,
 * held for {@link #MAX_AGE_TICKS} so that a mech choosing a destination can prefer ground it can reach.
 *
 * <p>A region is learned, never computed for its own sake: it is what a proof that found its goal unreachable had
 * already paid for. It is kept across the map's changes, since a burning or crumbling map moves its topology every
 * few ticks and almost only opens ground, and is dropped after its age so that a breach it does not know about is
 * learned by the next refusal. A newer region of the same radius that overlaps it is the same component proved again,
 * and replaces it.
 *
 * <p>Regions found during a tick, possibly on several unit workers at once, are published at the start of the next
 * one in owner order, so what every mech reads within a tick does not depend on which worker finished first.
 */
public final class MechReachability {

    /** Ticks a region is preferred from after it was learned. */
    static final int MAX_AGE_TICKS = 300;
    /** Regions held at once; the oldest goes first. */
    static final int CAPACITY = 8;

    private record Held(ReachableRegion region, int learnedTick) {}
    private record Learned(long owner, ReachableRegion region) {}

    private final List<Held> held = new ArrayList<>();
    private final List<Learned> learned = new ArrayList<>();
    private int tick;

    /** Records a region one owner's proof flooded this tick; it is read from the next. */
    public synchronized void learn(long owner, ReachableRegion region) {
        if (region != null && region.size() > 0) learned.add(new Learned(owner, region));
    }

    /** Publishes last tick's regions and retires the expired. */
    public synchronized void beginTick(int nextTick) {
        tick = nextTick;
        held.removeIf(row -> tick - row.learnedTick > MAX_AGE_TICKS);
        learned.sort(Comparator.comparingLong(Learned::owner));
        for (Learned row : learned) {
            // A refusal served again from the route cache is the same proof, not a newer one.
            if (held.stream().anyMatch(old -> old.region == row.region)) continue;
            held.removeIf(old -> old.region.radius() == row.region.radius() && old.region.overlaps(row.region));
            held.add(new Held(row.region, tick));
            if (held.size() > CAPACITY) held.remove(0);
        }
        learned.clear();
    }

    /**
     * The newest held region of {@code radius} a body at ({@code x}, {@code y}) stands inside, or {@code null} when
     * nothing is known about its ground.
     */
    public synchronized ReachableRegion regionFor(float x, float y, float radius) {
        for (int i = held.size() - 1; i >= 0; i--) {
            ReachableRegion region = held.get(i).region;
            if (region.radius() == radius && region.holds(x, y)) return region;
        }
        return null;
    }
}
