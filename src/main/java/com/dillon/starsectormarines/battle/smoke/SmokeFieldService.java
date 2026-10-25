package com.dillon.starsectormarines.battle.smoke;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Owns deterministic grenade flight and faction-neutral temporary smoke opacity.
 *
 * <p>A cloud that declares a {@link SmokeGrenadeSpec#thinSeconds() thin} stands at its full radius until that many seconds
 * remain and then contracts toward a core ({@link #CORE_FRACTION} of the radius), clearing from the outside in and finally
 * going. Opacity is given back cell by cell as it contracts, so line of sight through the edge of an old screen returns before
 * the screen is gone: a longer-lasting screen is a screen that is eventually leaky, not one that is whole until it is not.
 * The standing cloud reports both what it is now ({@link SmokeFieldView#radius()}) and what it was thrown as
 * ({@link SmokeFieldView#fullRadius()}, held for {@link SmokeFieldView#coverSeconds()} more seconds), so a plan counts on the
 * whole screen only for the stretch it is whole.
 *
 * <p>It also stands <b>haze</b> ({@link #deployHaze}): a Bombard's see-through spore haze ({@code bombard.md}), which
 * thins the same way but holds the grid's haze layer instead of its opacity, so it costs a round accuracy and never
 * blocks sight. Hazes are published apart ({@link #activeHazes}); {@link #activeFields} and {@link #hasSmokeNear} are
 * smoke only, since a haze conceals nothing.
 */
public final class SmokeFieldService {

    /** Share of its radius a thinning cloud still holds when its last moment arrives. */
    public static final float CORE_FRACTION = 0.4f;

    private final NavigationGrid grid;
    private final ArrayList<SmokeThrow> throwsInFlight = new ArrayList<>();
    private final ArrayList<SmokeField> fields = new ArrayList<>();
    private volatile List<SmokeThrowView> throwSnapshot = List.of();
    private volatile List<SmokeFieldView> fieldSnapshot = List.of();
    private volatile List<SmokeFieldView> hazeSnapshot = List.of();
    private long nextId = 1L;

    public SmokeFieldService(NavigationGrid grid) {
        this.grid = grid;
    }

    public synchronized void launch(long carrierId, Faction faction, float fromX, float fromY,
                                    float toX, float toY, SmokeGrenadeSpec spec) {
        throwsInFlight.add(new SmokeThrow(nextId++, carrierId, faction,
                fromX, fromY, toX, toY, spec.flightSeconds(), spec.flightSeconds(),
                spec.arcHeight(), spec.cloudRadius(), spec.cloudDuration(), spec.thinSeconds()));
        publishSnapshots();
    }

    public synchronized void tick(float dt) {
        for (int i = fields.size() - 1; i >= 0; i--) {
            SmokeField field = fields.get(i);
            field.remaining -= dt;
            if (field.remaining <= 0f) {
                field.clearTo(-1f, grid);
                fields.remove(i);
                continue;
            }
            field.thin(grid);
        }
        for (int i = throwsInFlight.size() - 1; i >= 0; i--) {
            SmokeThrow grenade = throwsInFlight.get(i);
            grenade.remaining -= dt;
            if (grenade.remaining > 0f) continue;
            deploy(grenade);
            throwsInFlight.remove(i);
        }
        publishSnapshots();
    }

    public List<SmokeThrowView> throwsInFlight() { return throwSnapshot; }
    public List<SmokeFieldView> activeFields() { return fieldSnapshot; }
    /** Standing hazes, published apart from smoke. */
    public List<SmokeFieldView> activeHazes() { return hazeSnapshot; }

    /**
     * Stands a see-through haze at {@code (x, y)} at once: no flight, since whatever carried it there is the
     * carrier's own projectile. It thins as a smoke cloud does and holds the grid's haze layer, not its opacity.
     *
     * @return the haze's id
     */
    public synchronized long deployHaze(Faction faction, float x, float y, float radius, float duration,
                                        float thinSeconds) {
        long id = nextId++;
        stand(id, faction, x, y, radius, duration, thinSeconds, true);
        publishSnapshots();
        return id;
    }

    public boolean hasSmokeNear(float x, float y, float radius) {
        for (SmokeFieldView field : fieldSnapshot) {
            float dx = field.x() - x;
            float dy = field.y() - y;
            float reach = radius + field.radius();
            if (dx * dx + dy * dy <= reach * reach) return true;
        }
        return false;
    }

    public boolean hasThrowFrom(long carrierId) {
        for (SmokeThrowView grenade : throwSnapshot) {
            if (grenade.carrierId() == carrierId) return true;
        }
        return false;
    }

    private void deploy(SmokeThrow grenade) {
        stand(grenade.id, grenade.faction, grenade.toX, grenade.toY, grenade.cloudRadius, grenade.cloudDuration,
                grenade.thinSeconds, false);
    }

    private void stand(long id, Faction faction, float cx, float cy, float cloudRadius, float cloudDuration,
                       float thinSeconds, boolean haze) {
        int minX = Math.max(0, (int) Math.floor(cx - cloudRadius));
        int maxX = Math.min(grid.getWidth() - 1, (int) Math.floor(cx + cloudRadius));
        int minY = Math.max(0, (int) Math.floor(cy - cloudRadius));
        int maxY = Math.min(grid.getHeight() - 1, (int) Math.floor(cy + cloudRadius));
        float radiusSq = cloudRadius * cloudRadius;
        int capacity = (maxX - minX + 1) * (maxY - minY + 1);
        long[] keyed = new long[capacity];
        int count = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float dx = x + 0.5f - cx;
                float dy = y + 0.5f - cy;
                float distSq = dx * dx + dy * dy;
                if (distSq > radiusSq) continue;
                int idx = grid.index(x, y);
                if (haze) grid.addHazeAt(idx);
                else grid.addTransientOpacityAt(idx);
                // Outermost first, so a thinning cloud gives back its rim before its core; ties by index for repeatability.
                // A non-negative float's bits order as the float does, so the complement sorts the far cells first.
                long outward = ~(long) Float.floatToIntBits(distSq) & 0x7FFFFFFFL;
                keyed[count++] = (outward << 32) | (idx & 0xFFFFFFFFL);
            }
        }
        long[] sorted = Arrays.copyOf(keyed, count);
        Arrays.sort(sorted);
        int[] occupied = new int[count];
        float[] distanceSq = new float[count];
        int width = grid.getWidth();
        for (int i = 0; i < count; i++) {
            int idx = (int) (sorted[i] & 0xFFFFFFFFL);
            occupied[i] = idx;
            float dx = (idx % width) + 0.5f - cx;
            float dy = (idx / width) + 0.5f - cy;
            distanceSq[i] = dx * dx + dy * dy;
        }
        fields.add(new SmokeField(id, faction, cx, cy, cloudRadius, cloudDuration, cloudDuration, thinSeconds,
                occupied, distanceSq, haze));
    }

    private void publishSnapshots() {
        ArrayList<SmokeThrowView> visibleThrows = new ArrayList<>(throwsInFlight.size());
        for (SmokeThrow grenade : throwsInFlight) {
            float progress = 1f - Math.max(0f, grenade.remaining) / grenade.totalFlight;
            float x = grenade.fromX + (grenade.toX - grenade.fromX) * progress;
            float y = grenade.fromY + (grenade.toY - grenade.fromY) * progress;
            float z = 4f * grenade.arcHeight * progress * (1f - progress);
            visibleThrows.add(new SmokeThrowView(grenade.id, grenade.carrierId,
                    grenade.faction, x, y, z, progress));
        }
        ArrayList<SmokeFieldView> visibleFields = new ArrayList<>(fields.size());
        ArrayList<SmokeFieldView> visibleHazes = new ArrayList<>();
        for (SmokeField field : fields) {
            (field.haze ? visibleHazes : visibleFields).add(new SmokeFieldView(field.id, field.faction, field.x,
                    field.y, field.currentRadius(), field.remaining, field.totalDuration,
                    field.radius, Math.max(0f, field.remaining - field.thinSeconds)));
        }
        throwSnapshot = List.copyOf(visibleThrows);
        fieldSnapshot = List.copyOf(visibleFields);
        hazeSnapshot = List.copyOf(visibleHazes);
    }

    public record SmokeThrowView(long id, long carrierId, Faction sourceFaction,
                                 float x, float y, float z, float progress) {}

    /**
     * A standing cloud.
     *
     * @param radius       the radius it blocks to now (smaller than {@code fullRadius} once it has begun to thin)
     * @param fullRadius   the radius it was thrown at
     * @param coverSeconds how much longer it holds {@code fullRadius}; zero once it is thinning
     */
    public record SmokeFieldView(long id, Faction sourceFaction,
                                 float x, float y, float radius,
                                 float remaining, float totalDuration,
                                 float fullRadius, float coverSeconds) {}

    private static final class SmokeThrow {
        final long id;
        final long carrierId;
        final Faction faction;
        final float fromX;
        final float fromY;
        final float toX;
        final float toY;
        final float totalFlight;
        final float arcHeight;
        final float cloudRadius;
        final float cloudDuration;
        final float thinSeconds;
        float remaining;

        SmokeThrow(long id, long carrierId, Faction faction,
                   float fromX, float fromY, float toX, float toY,
                   float totalFlight, float remaining, float arcHeight,
                   float cloudRadius, float cloudDuration, float thinSeconds) {
            this.id = id;
            this.carrierId = carrierId;
            this.faction = faction;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.totalFlight = totalFlight;
            this.remaining = remaining;
            this.arcHeight = arcHeight;
            this.cloudRadius = cloudRadius;
            this.cloudDuration = cloudDuration;
            this.thinSeconds = thinSeconds;
        }
    }

    private static final class SmokeField {
        final long id;
        final Faction faction;
        final float x;
        final float y;
        final float radius;
        final float totalDuration;
        final float thinSeconds;
        /** Occupied cells, outermost first, with each one's squared distance from the centre in the same order. */
        final int[] occupiedCells;
        final float[] distanceSq;
        /** A see-through haze, holding the grid's haze layer rather than its opacity. */
        final boolean haze;
        /** Cells before this index have been given back. */
        int firstHeld;
        float remaining;

        SmokeField(long id, Faction faction, float x, float y, float radius,
                   float remaining, float totalDuration, float thinSeconds, int[] occupiedCells, float[] distanceSq,
                   boolean haze) {
            this.haze = haze;
            this.id = id;
            this.faction = faction;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.remaining = remaining;
            this.totalDuration = totalDuration;
            this.thinSeconds = thinSeconds;
            this.occupiedCells = occupiedCells;
            this.distanceSq = distanceSq;
        }

        /** The radius the cloud blocks to at this moment. */
        float currentRadius() {
            if (thinSeconds <= 0f || remaining >= thinSeconds) return radius;
            float held = remaining / thinSeconds;
            return radius * (CORE_FRACTION + (1f - CORE_FRACTION) * held);
        }

        void thin(NavigationGrid grid) {
            if (thinSeconds <= 0f || remaining >= thinSeconds) return;
            clearTo(currentRadius(), grid);
        }

        /** Gives back every held cell farther from the centre than {@code limit}; a negative limit gives back all. */
        void clearTo(float limit, NavigationGrid grid) {
            float limitSq = limit < 0f ? -1f : limit * limit;
            while (firstHeld < occupiedCells.length && distanceSq[firstHeld] > limitSq) {
                int idx = occupiedCells[firstHeld++];
                if (haze) grid.removeHazeAt(idx);
                else grid.removeTransientOpacityAt(idx);
            }
        }
    }
}
